// Package shopping gère « Les courses » : une liste par tribu, la même sur
// tous les appareils de ses membres.
//
// Le serveur garde ce que la tribu décide : les recettes au menu et leurs
// parts, les articles ajoutés à la main, les lignes cochées, les quantités
// corrigées, le rayon choisi pour un produit, l'ordre des rayons et le
// placard. Les clients en tirent la liste (addition des ingrédients, rayons)
// avec la même logique des deux côtés (lib/shopping.ts ↔ ShoppingList.kt) :
// le serveur ne connaît que des clés de lignes et de produits.
//
// Chaque changement est une opération ; elles s'appliquent dans l'ordre, et
// sur une même ligne la dernière l'emporte. Android rejoue ainsi celles faites
// hors ligne. Un numéro de version permet aux clients de ne relire la liste
// que si elle a changé.
package shopping

import (
	"context"
	"crypto/rand"
	"encoding/hex"
	"errors"
	"regexp"
	"slices"
	"strings"
	"time"
	_ "time/tzdata" // fuseau de Paris (l'image alpine n'a pas de zoneinfo)
	"unicode/utf8"

	"go.mongodb.org/mongo-driver/v2/bson"
	"go.mongodb.org/mongo-driver/v2/mongo"
	"go.mongodb.org/mongo-driver/v2/mongo/options"
)

var (
	ErrInvalidInput = errors.New("invalid input")
	ErrNotFound     = errors.New("not found")
	// ErrConflict : la liste a changé trop souvent pendant l'écriture
	ErrConflict = errors.New("shopping list changed, try again")
)

func invalid(msg string) error { return errors.Join(ErrInvalidInput, errors.New(msg)) }

const (
	Collection = "shopping_lists"

	recipeCategory  = "LINK_CATEGORY_RECETTE"
	DefaultServings = 4
	maxServings     = 50
	maxOps          = 100
	maxMenu         = 20
	maxItems        = 200
	maxChecked      = 500
	maxValues       = 500
	maxPantry       = 200
	maxRemoved      = 300
	maxLabel        = 80
	maxQuantity     = 30
	maxKey          = 200
	// « X est en magasin » s'efface tout seul
	shoppingTTL = 2 * time.Hour
	retries     = 5
)

// Aisles : les rayons, dans l'ordre par défaut d'un supermarché. Les clients
// ont la même liste (lib/aisles.ts ↔ data/Aisles.kt).
var Aisles = []string{
	"fruits-legumes", "boulangerie", "boucherie", "cremerie", "epicerie",
	"surgeles", "boissons", "bebe-hygiene", "maison", "autres",
}

var (
	paris  = mustParis()
	itemID = regexp.MustCompile(`^[A-Za-z0-9_-]{1,40}$`)
)

func mustParis() *time.Location {
	loc, err := time.LoadLocation("Europe/Paris")
	if err != nil {
		panic(err)
	}
	return loc
}

type entry struct {
	LinkID   string    `bson:"link_id"`
	Servings int32     `bson:"servings"`
	AddedBy  string    `bson:"added_by"`
	AddedAt  time.Time `bson:"added_at"`
	// Marquée « C'est fait » depuis son ajout : enregistré pour que la version
	// change et que les autres appareils estompent la carte
	CookedAt *time.Time `bson:"cooked_at,omitempty"`
}

type item struct {
	ID       string    `bson:"id"`
	Label    string    `bson:"label"`
	Quantity string    `bson:"quantity"`
	Aisle    string    `bson:"aisle"`
	AddedBy  string    `bson:"added_by"`
	AddedAt  time.Time `bson:"added_at"`
}

type check struct {
	Key string    `bson:"key"`
	By  string    `bson:"by"`
	At  time.Time `bson:"at"`
}

type value struct {
	Key   string `bson:"key"`
	Value string `bson:"value"`
}

// list : le document d'une tribu (ou d'un compte seul), clé unique.
type list struct {
	ID            bson.ObjectID `bson:"_id,omitempty"`
	Key           string        `bson:"key"`
	Menu          []entry       `bson:"menu"`
	Items         []item        `bson:"items"`
	Checked       []check       `bson:"checked"`
	Quantities    []value       `bson:"quantities"`
	Aisles        []value       `bson:"aisles"`
	AisleOrder    []string      `bson:"aisle_order"`
	Pantry        []string      `bson:"pantry"`
	Removed       []string      `bson:"removed"`
	ShopperID     string        `bson:"shopper_id,omitempty"`
	ShoppingSince *time.Time    `bson:"shopping_since,omitempty"`
	Version       int64         `bson:"version"`
	UpdatedAt     time.Time     `bson:"updated_at"`
}

// Op : un changement envoyé par un client (voir ShoppingOp dans shopping.proto).
type Op struct {
	Type, LinkID, ID, Label, Quantity, Aisle, Key, Value string
	Servings                                             int32
	Order                                                []string
}

// MenuEntry : une recette au menu, avec ses ingrédients.
type MenuEntry struct {
	LinkID, Title, ImageURL, AddedBy string
	Ingredients                      []string
	BaseServings, Servings           int32
	AddedAt                          time.Time
	Cooked                           bool
}

// View : la liste telle que les clients la reçoivent.
type View struct {
	Version       int64
	Menu          []MenuEntry
	Items         []Item
	Checked       []Check
	Quantities    []Value
	Aisles        []Value
	AisleOrder    []string
	Pantry        []string
	Removed       []string
	ShopperID     string
	ShoppingSince *time.Time
}

type (
	Item  = item
	Check = check
	Value = value
)

// Service : la liste de courses des tribus.
type Service struct {
	col, links, dones *mongo.Collection
	// household : clé de la liste de userID (tribu ou compte seul)
	household func(ctx context.Context, userID string) (key string, personal bool, err error)
	peers     func(ctx context.Context, userID string) []string
	canView   func(ctx context.Context, linkID, userID string) bool
	now       func() time.Time
}

func NewService(db *mongo.Database,
	household func(ctx context.Context, userID string) (string, bool, error),
	peers func(ctx context.Context, userID string) []string,
	canView func(ctx context.Context, linkID, userID string) bool) *Service {
	return &Service{
		col: db.Collection(Collection), links: db.Collection("links"), dones: db.Collection("link_dones"),
		household: household, peers: peers, canView: canView, now: time.Now,
	}
}

// Get retourne la liste de userID. Si since est la version courante, elle
// n'est pas relue (unchanged).
func (s *Service) Get(ctx context.Context, userID string, since int64) (*View, bool, error) {
	key, personal, err := s.household(ctx, userID)
	if err != nil {
		return nil, false, err
	}
	if !personal {
		if err := s.adoptPersonal(ctx, key, userID); err != nil {
			return nil, false, err
		}
	}
	l, err := s.load(ctx, key)
	if err != nil {
		return nil, false, err
	}
	if l, err = s.tidy(ctx, l); err != nil {
		return nil, false, err
	}
	if since > 0 && since == l.Version {
		return nil, true, nil
	}
	v, err := s.view(ctx, l, userID)
	return v, false, err
}

// Apply applique les opérations de userID dans l'ordre et retourne la liste.
func (s *Service) Apply(ctx context.Context, userID string, ops []Op) (*View, error) {
	if len(ops) == 0 || len(ops) > maxOps {
		return nil, invalid("between 1 and 100 operations")
	}
	key, personal, err := s.household(ctx, userID)
	if err != nil {
		return nil, err
	}
	if !personal {
		if err := s.adoptPersonal(ctx, key, userID); err != nil {
			return nil, err
		}
	}
	// Les recettes ajoutées au menu doivent être des recettes visibles du compte
	allowed := map[string]bool{}
	for _, op := range ops {
		if op.Type == "menu_add" && !allowed[op.LinkID] {
			if err := s.checkRecipe(ctx, op.LinkID, userID); err != nil {
				return nil, err
			}
			allowed[op.LinkID] = true
		}
	}
	for range retries {
		l, err := s.load(ctx, key)
		if err != nil {
			return nil, err
		}
		before := l.Version
		now := s.now()
		for _, op := range ops {
			if err := apply(l, op, userID, now); err != nil {
				return nil, err
			}
		}
		saved, err := s.save(ctx, l, before, now)
		if err != nil {
			return nil, err
		}
		if !saved {
			continue // modifiée entre-temps : on rejoue sur la nouvelle version
		}
		if l, err = s.tidy(ctx, l); err != nil {
			return nil, err
		}
		return s.view(ctx, l, userID)
	}
	return nil, ErrConflict
}

func (s *Service) load(ctx context.Context, key string) (*list, error) {
	var l list
	err := s.col.FindOne(ctx, bson.M{"key": key}).Decode(&l)
	if errors.Is(err, mongo.ErrNoDocuments) {
		return &list{Key: key}, nil
	}
	if err != nil {
		return nil, err
	}
	return &l, nil
}

// save écrit l si personne ne l'a changée depuis la version before (false sinon).
func (s *Service) save(ctx context.Context, l *list, before int64, now time.Time) (bool, error) {
	l.Version = before + 1
	l.UpdatedAt = now
	if before == 0 && l.ID.IsZero() {
		l.ID = bson.NewObjectID()
		_, err := s.col.InsertOne(ctx, l)
		if mongo.IsDuplicateKeyError(err) {
			return false, nil
		}
		return err == nil, err
	}
	res, err := s.col.ReplaceOne(ctx, bson.M{"_id": l.ID, "version": before}, l)
	if err != nil {
		return false, err
	}
	return res.MatchedCount == 1, nil
}

// adoptPersonal : un compte qui rejoint une tribu y apporte sa liste d'avant
// (recettes, articles, placard, rayons choisis), puis elle disparaît.
func (s *Service) adoptPersonal(ctx context.Context, key, userID string) error {
	var mine list
	err := s.col.FindOne(ctx, bson.M{"key": userID}).Decode(&mine)
	if errors.Is(err, mongo.ErrNoDocuments) {
		return nil
	}
	if err != nil {
		return err
	}
	for range retries {
		l, err := s.load(ctx, key)
		if err != nil {
			return err
		}
		before := l.Version
		merge(l, &mine)
		saved, err := s.save(ctx, l, before, s.now())
		if err != nil {
			return err
		}
		if saved {
			_, err = s.col.DeleteOne(ctx, bson.M{"_id": mine.ID})
			return err
		}
	}
	return ErrConflict
}

func merge(dst, src *list) {
	for _, e := range src.Menu {
		if len(dst.Menu) < maxMenu && !slices.ContainsFunc(dst.Menu, func(x entry) bool { return x.LinkID == e.LinkID }) {
			dst.Menu = append(dst.Menu, e)
		}
	}
	for _, it := range src.Items {
		if len(dst.Items) < maxItems && !slices.ContainsFunc(dst.Items, func(x item) bool { return x.ID == it.ID }) {
			dst.Items = append(dst.Items, it)
		}
	}
	for _, c := range src.Checked {
		dst.Checked = setCheck(dst.Checked, c)
	}
	for _, v := range src.Quantities {
		dst.Quantities = setValue(dst.Quantities, v.Key, v.Value)
	}
	for _, v := range src.Aisles {
		if !slices.ContainsFunc(dst.Aisles, func(x value) bool { return x.Key == v.Key }) {
			dst.Aisles = setValue(dst.Aisles, v.Key, v.Value)
		}
	}
	if len(dst.AisleOrder) == 0 {
		dst.AisleOrder = src.AisleOrder
	}
	for _, p := range src.Pantry {
		dst.Pantry = addKey(dst.Pantry, p, maxPantry)
	}
	for _, r := range src.Removed {
		dst.Removed = addKey(dst.Removed, r, maxRemoved)
	}
}

func (s *Service) checkRecipe(ctx context.Context, linkID, userID string) error {
	oid, err := bson.ObjectIDFromHex(linkID)
	if err != nil {
		return ErrNotFound
	}
	n, err := s.links.CountDocuments(ctx, bson.M{"_id": oid, "category": recipeCategory})
	if err != nil {
		return err
	}
	if n == 0 || s.canView == nil || !s.canView(ctx, linkID, userID) {
		return ErrNotFound
	}
	return nil
}

// apply applique une opération à l en mémoire.
func apply(l *list, op Op, userID string, now time.Time) error {
	switch op.Type {
	case "menu_add":
		if slices.ContainsFunc(l.Menu, func(e entry) bool { return e.LinkID == op.LinkID }) {
			if op.Servings != 0 {
				return apply(l, Op{Type: "menu_servings", LinkID: op.LinkID, Servings: op.Servings}, userID, now)
			}
			return nil
		}
		if len(l.Menu) >= maxMenu {
			return invalid("too many recipes on the menu")
		}
		if op.Servings < 0 || op.Servings > maxServings {
			return invalid("servings must be between 1 and 50")
		}
		l.Menu = append(l.Menu, entry{LinkID: op.LinkID, Servings: op.Servings, AddedBy: userID, AddedAt: now})
	case "menu_servings":
		if op.Servings < 1 || op.Servings > maxServings {
			return invalid("servings must be between 1 and 50")
		}
		for i := range l.Menu {
			if l.Menu[i].LinkID == op.LinkID {
				l.Menu[i].Servings = op.Servings
			}
		}
	case "menu_remove":
		l.Menu = slices.DeleteFunc(l.Menu, func(e entry) bool { return e.LinkID == op.LinkID })
	case "item_add":
		label, qty, aisle, err := cleanItem(op)
		if err != nil {
			return err
		}
		id := op.ID
		if id == "" {
			id = newID()
		} else if !itemID.MatchString(id) {
			return invalid("invalid item id")
		}
		if slices.ContainsFunc(l.Items, func(it item) bool { return it.ID == id }) {
			return nil // déjà ajouté (opération rejouée)
		}
		if len(l.Items) >= maxItems {
			return invalid("too many items")
		}
		l.Items = append(l.Items, item{ID: id, Label: label, Quantity: qty, Aisle: aisle, AddedBy: userID, AddedAt: now})
	case "item_update":
		label, qty, aisle, err := cleanItem(op)
		if err != nil {
			return err
		}
		for i := range l.Items {
			if l.Items[i].ID == op.ID {
				l.Items[i].Label, l.Items[i].Quantity, l.Items[i].Aisle = label, qty, aisle
			}
		}
	case "item_remove":
		l.Items = slices.DeleteFunc(l.Items, func(it item) bool { return it.ID == op.ID })
		l.Checked = slices.DeleteFunc(l.Checked, func(c check) bool { return c.Key == ItemKey(op.ID) })
	case "check":
		k, err := cleanKey(op.Key)
		if err != nil {
			return err
		}
		if len(l.Checked) >= maxChecked && !slices.ContainsFunc(l.Checked, func(c check) bool { return c.Key == k }) {
			return invalid("too many checked lines")
		}
		l.Checked = setCheck(l.Checked, check{Key: k, By: userID, At: now})
	case "uncheck":
		l.Checked = slices.DeleteFunc(l.Checked, func(c check) bool { return c.Key == op.Key })
	case "quantity", "aisle":
		k, err := cleanKey(op.Key)
		if err != nil {
			return err
		}
		v := strings.TrimSpace(op.Value)
		target := &l.Quantities
		if op.Type == "aisle" {
			target = &l.Aisles
			if v != "" && !slices.Contains(Aisles, v) {
				return invalid("unknown aisle")
			}
		} else if utf8.RuneCountInString(v) > maxQuantity {
			return invalid("quantity is too long")
		}
		if v == "" {
			*target = slices.DeleteFunc(*target, func(x value) bool { return x.Key == k })
			return nil
		}
		if len(*target) >= maxValues && !slices.ContainsFunc(*target, func(x value) bool { return x.Key == k }) {
			return invalid("too many values")
		}
		*target = setValue(*target, k, v)
	case "aisle_order":
		seen := map[string]bool{}
		for _, a := range op.Order {
			if !slices.Contains(Aisles, a) || seen[a] {
				return invalid("aisle order must list known aisles once")
			}
			seen[a] = true
		}
		l.AisleOrder = op.Order
	case "pantry_have", "remove":
		k, err := cleanKey(op.Key)
		if err != nil {
			return err
		}
		if op.Type == "remove" {
			if l.Removed = addKey(l.Removed, k, maxRemoved); !slices.Contains(l.Removed, k) {
				return invalid("too many removed lines")
			}
			return nil
		}
		if l.Pantry = addKey(l.Pantry, k, maxPantry); !slices.Contains(l.Pantry, k) {
			return invalid("too many pantry items")
		}
	case "pantry_out":
		l.Pantry = slices.DeleteFunc(l.Pantry, func(p string) bool { return p == op.Key })
	case "restore":
		l.Removed = slices.DeleteFunc(l.Removed, func(p string) bool { return p == op.Key })
	case "shopping_start":
		l.ShopperID, l.ShoppingSince = userID, &now
	case "shopping_stop":
		if l.ShopperID == userID {
			l.ShopperID, l.ShoppingSince = "", nil
		}
	case "finish":
		// Le panier se vide : lignes cochées et articles libres achetés
		// disparaissent ; le menu, le placard et les rayons restent
		bought := map[string]bool{}
		for _, c := range l.Checked {
			bought[c.Key] = true
		}
		l.Items = slices.DeleteFunc(l.Items, func(it item) bool { return bought[ItemKey(it.ID)] })
		l.Checked = nil
		l.ShopperID, l.ShoppingSince = "", nil
	case "clear":
		// Nouvelle liste : on garde le placard, les rayons et leur ordre
		l.Menu, l.Items, l.Checked, l.Quantities, l.Removed = nil, nil, nil, nil, nil
		l.ShopperID, l.ShoppingSince = "", nil
	default:
		return invalid("unknown operation " + op.Type)
	}
	return nil
}

// ItemKey : clé de ligne d'un article libre (les lignes de recette ont la leur,
// calculée par les clients).
func ItemKey(id string) string { return "item:" + id }

func cleanItem(op Op) (label, qty, aisle string, err error) {
	label = strings.Join(strings.Fields(op.Label), " ")
	qty = strings.Join(strings.Fields(op.Quantity), " ")
	aisle = strings.TrimSpace(op.Aisle)
	switch {
	case label == "" || utf8.RuneCountInString(label) > maxLabel:
		return "", "", "", invalid("label must be 1 to 80 characters")
	case utf8.RuneCountInString(qty) > maxQuantity:
		return "", "", "", invalid("quantity is too long")
	case aisle != "" && !slices.Contains(Aisles, aisle):
		return "", "", "", invalid("unknown aisle")
	}
	return label, qty, aisle, nil
}

func cleanKey(k string) (string, error) {
	k = strings.TrimSpace(k)
	if k == "" || len(k) > maxKey || !utf8.ValidString(k) {
		return "", invalid("invalid key")
	}
	return k, nil
}

func setCheck(cs []check, c check) []check {
	for i := range cs {
		if cs[i].Key == c.Key {
			cs[i] = c
			return cs
		}
	}
	return append(cs, c)
}

func setValue(vs []value, k, v string) []value {
	for i := range vs {
		if vs[i].Key == k {
			vs[i].Value = v
			return vs
		}
	}
	return append(vs, value{Key: k, Value: v})
}

func addKey(ks []string, k string, limit int) []string {
	if slices.Contains(ks, k) || len(ks) >= limit {
		return ks
	}
	return append(ks, k)
}

func newID() string {
	b := make([]byte, 8)
	_, _ = rand.Read(b)
	return hex.EncodeToString(b)
}

// tidy range la liste avant lecture : une recette cuisinée depuis son ajout
// est notée cuisinée (la version change, les autres appareils estompent sa
// carte) puis quitte le menu le lendemain (heure de Paris) ; une recette
// supprimée quitte le menu ; « en magasin » s'efface après 2 h. Enregistré si
// quelque chose a changé.
func (s *Service) tidy(ctx context.Context, l *list) (*list, error) {
	if l.Version == 0 {
		return l, nil
	}
	for range retries {
		now := s.now()
		cooked, alive, err := s.menuState(ctx, l)
		if err != nil {
			return nil, err
		}
		today := day(now)
		gone := func(e entry) bool {
			c, ok := cooked[e.LinkID]
			return !alive[e.LinkID] || ok && day(c).Before(today)
		}
		// Cuisinée, ou plus cuisinée (souvenir retiré) depuis la dernière lecture
		stale := func(e entry) bool {
			c, ok := cooked[e.LinkID]
			return ok != (e.CookedAt != nil) || ok && !c.Equal(*e.CookedAt)
		}
		expired := l.ShoppingSince != nil && now.Sub(*l.ShoppingSince) > shoppingTTL
		if !slices.ContainsFunc(l.Menu, gone) && !slices.ContainsFunc(l.Menu, stale) && !expired {
			return l, nil
		}
		before := l.Version
		l.Menu = slices.DeleteFunc(l.Menu, gone)
		for i := range l.Menu {
			if c, ok := cooked[l.Menu[i].LinkID]; ok {
				l.Menu[i].CookedAt = &c
			} else {
				l.Menu[i].CookedAt = nil
			}
		}
		if expired {
			l.ShopperID, l.ShoppingSince = "", nil
		}
		saved, err := s.save(ctx, l, before, now)
		if err != nil {
			return nil, err
		}
		if saved {
			return l, nil
		}
		if l, err = s.load(ctx, l.Key); err != nil {
			return nil, err
		}
	}
	return nil, ErrConflict
}

func day(t time.Time) time.Time {
	y, m, d := t.In(paris).Date()
	return time.Date(y, m, d, 0, 0, 0, 0, paris)
}

// menuState : pour chaque recette du menu, la première fois qu'elle a été
// marquée faite depuis son ajout, et si elle existe encore.
func (s *Service) menuState(ctx context.Context, l *list) (cooked map[string]time.Time, alive map[string]bool, err error) {
	cooked, alive = map[string]time.Time{}, map[string]bool{}
	if len(l.Menu) == 0 {
		return cooked, alive, nil
	}
	ids, oids := make([]string, 0, len(l.Menu)), bson.A{}
	addedAt := map[string]time.Time{}
	for _, e := range l.Menu {
		ids = append(ids, e.LinkID)
		addedAt[e.LinkID] = e.AddedAt
		if oid, err := bson.ObjectIDFromHex(e.LinkID); err == nil {
			oids = append(oids, oid)
		}
	}
	cur, err := s.links.Find(ctx, bson.M{"_id": bson.M{"$in": oids}}, options.Find().SetProjection(bson.M{"_id": 1}))
	if err != nil {
		return nil, nil, err
	}
	var found []struct {
		ID bson.ObjectID `bson:"_id"`
	}
	if err := cur.All(ctx, &found); err != nil {
		return nil, nil, err
	}
	for _, f := range found {
		alive[f.ID.Hex()] = true
	}
	cur, err = s.dones.Find(ctx, bson.M{"link_id": bson.M{"$in": ids}}, options.Find().SetProjection(bson.M{"link_id": 1, "created_at": 1}))
	if err != nil {
		return nil, nil, err
	}
	var dones []struct {
		LinkID    string    `bson:"link_id"`
		CreatedAt time.Time `bson:"created_at"`
	}
	if err := cur.All(ctx, &dones); err != nil {
		return nil, nil, err
	}
	for _, d := range dones {
		if !d.CreatedAt.After(addedAt[d.LinkID]) {
			continue
		}
		if c, ok := cooked[d.LinkID]; !ok || d.CreatedAt.Before(c) {
			cooked[d.LinkID] = d.CreatedAt
		}
	}
	return cooked, alive, nil
}

type recipe struct {
	ID          bson.ObjectID `bson:"_id"`
	OwnerID     string        `bson:"owner_id"`
	Title       string        `bson:"title"`
	ImageURL    string        `bson:"image_url"`
	Ingredients []string      `bson:"ingredients"`
	Servings    int32         `bson:"servings"`
	Visibility  string        `bson:"visibility"`
	Hidden      bool          `bson:"hidden"`
}

// view : la liste pour userID, recettes du menu comprises. Une recette que le
// lecteur ne peut plus voir (rendue privée par un auteur hors de la tribu) est
// cachée de sa liste.
func (s *Service) view(ctx context.Context, l *list, userID string) (*View, error) {
	v := &View{
		Version: l.Version, Items: l.Items, Checked: l.Checked, Quantities: l.Quantities, Aisles: l.Aisles,
		AisleOrder: l.AisleOrder, Pantry: l.Pantry, Removed: l.Removed, ShopperID: l.ShopperID, ShoppingSince: l.ShoppingSince,
	}
	if len(l.Menu) == 0 {
		return v, nil
	}
	oids := bson.A{}
	for _, e := range l.Menu {
		if oid, err := bson.ObjectIDFromHex(e.LinkID); err == nil {
			oids = append(oids, oid)
		}
	}
	cur, err := s.links.Find(ctx, bson.M{"_id": bson.M{"$in": oids}}, options.Find().SetProjection(bson.M{
		"owner_id": 1, "title": 1, "image_url": 1, "ingredients": 1, "servings": 1, "visibility": 1, "hidden": 1,
	}))
	if err != nil {
		return nil, err
	}
	var rs []recipe
	if err := cur.All(ctx, &rs); err != nil {
		return nil, err
	}
	byID := map[string]recipe{}
	for _, r := range rs {
		byID[r.ID.Hex()] = r
	}
	var peers []string
	if s.peers != nil {
		peers = s.peers(ctx, userID)
	}
	for _, e := range l.Menu {
		r, ok := byID[e.LinkID]
		if !ok {
			continue
		}
		visible := slices.Contains(peers, r.OwnerID) || (r.Visibility == "public" && !r.Hidden)
		if !visible && (s.canView == nil || !s.canView(ctx, e.LinkID, userID)) {
			continue
		}
		base := r.Servings
		if base <= 0 {
			base = DefaultServings
		}
		servings := e.Servings
		if servings <= 0 {
			servings = base
		}
		v.Menu = append(v.Menu, MenuEntry{
			LinkID: e.LinkID, Title: r.Title, ImageURL: r.ImageURL, Ingredients: r.Ingredients,
			BaseServings: base, Servings: servings, AddedBy: e.AddedBy, AddedAt: e.AddedAt, Cooked: e.CookedAt != nil,
		})
	}
	return v, nil
}
