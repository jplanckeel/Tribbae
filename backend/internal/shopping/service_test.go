package shopping

import (
	"context"
	"errors"
	"strings"
	"testing"
	"time"

	"go.mongodb.org/mongo-driver/v2/bson"
	"go.mongodb.org/mongo-driver/v2/mongo"
	"go.mongodb.org/mongo-driver/v2/mongo/options"
)

// Alice et Bob forment une tribu ; Carol est seule, Eve est une inconnue.
type fixture struct {
	t     *testing.T
	ctx   context.Context
	db    *mongo.Database
	svc   *Service
	clock time.Time
	// tribe : compte → clé de son foyer (absent : compte seul)
	tribe map[string]string
}

func setup(t *testing.T) *fixture {
	t.Helper()
	ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
	t.Cleanup(cancel)
	client, err := mongo.Connect(options.Client().ApplyURI("mongodb://localhost:27017").SetServerSelectionTimeout(3 * time.Second))
	if err != nil || client.Ping(ctx, nil) != nil {
		t.Skip("Skipping test: MongoDB unavailable")
	}
	db := client.Database("tribbae_test_" + bson.NewObjectID().Hex())
	t.Cleanup(func() {
		_ = db.Drop(context.Background())
		_ = client.Disconnect(context.Background())
	})
	if _, err := db.Collection(Collection).Indexes().CreateOne(ctx, mongo.IndexModel{
		Keys: bson.D{{Key: "key", Value: 1}}, Options: options.Index().SetUnique(true),
	}); err != nil {
		t.Fatal(err)
	}
	f := &fixture{t: t, ctx: ctx, db: db, clock: time.Date(2026, 11, 13, 10, 0, 0, 0, paris),
		tribe: map[string]string{"alice": "tribu-ab", "bob": "tribu-ab"}}
	household := func(_ context.Context, userID string) (string, bool, error) {
		if k, ok := f.tribe[userID]; ok {
			return k, false, nil
		}
		return userID, true, nil
	}
	peers := func(_ context.Context, userID string) []string {
		if k, ok := f.tribe[userID]; ok {
			var out []string
			for u, kk := range f.tribe {
				if kk == k {
					out = append(out, u)
				}
			}
			return out
		}
		return []string{userID}
	}
	// Visible : ses idées, celles de sa tribu, et les publiques
	canView := func(ctx context.Context, linkID, userID string) bool {
		oid, _ := bson.ObjectIDFromHex(linkID)
		var l struct {
			OwnerID    string `bson:"owner_id"`
			Visibility string `bson:"visibility"`
		}
		if db.Collection("links").FindOne(ctx, bson.M{"_id": oid}).Decode(&l) != nil {
			return false
		}
		for _, p := range peers(ctx, userID) {
			if p == l.OwnerID {
				return true
			}
		}
		return l.Visibility == "public"
	}
	f.svc = NewService(db, household, peers, canView)
	f.svc.now = func() time.Time { return f.clock }
	return f
}

func (f *fixture) link(doc bson.M) string {
	f.t.Helper()
	id := bson.NewObjectID()
	doc["_id"] = id
	if _, err := f.db.Collection("links").InsertOne(f.ctx, doc); err != nil {
		f.t.Fatal(err)
	}
	return id.Hex()
}

func (f *fixture) apply(user string, ops ...Op) *View {
	f.t.Helper()
	v, err := f.svc.Apply(f.ctx, user, ops)
	if err != nil {
		f.t.Fatalf("apply %v: %v", ops, err)
	}
	return v
}

func (f *fixture) get(user string) *View {
	f.t.Helper()
	v, _, err := f.svc.Get(f.ctx, user, 0)
	if err != nil {
		f.t.Fatal(err)
	}
	return v
}

func TestMenuIsSharedByTheTribe(t *testing.T) {
	f := setup(t)
	crepes := f.link(bson.M{"owner_id": "alice", "title": "Crêpes", "category": recipeCategory, "ingredients": bson.A{"250 g de farine"}, "servings": 6})
	gratin := f.link(bson.M{"owner_id": "bob", "title": "Gratin", "category": recipeCategory, "ingredients": bson.A{"1 kg de pommes de terre"}})
	velo := f.link(bson.M{"owner_id": "alice", "title": "Vélo", "category": "LINK_CATEGORY_CADEAU"})
	secret := f.link(bson.M{"owner_id": "eve", "title": "Secret", "category": recipeCategory, "visibility": "private"})
	public := f.link(bson.M{"owner_id": "eve", "title": "Soupe", "category": recipeCategory, "visibility": "public"})

	for _, id := range []string{velo, secret, "pas-un-id"} {
		if _, err := f.svc.Apply(f.ctx, "alice", []Op{{Type: "menu_add", LinkID: id}}); !errors.Is(err, ErrNotFound) {
			t.Errorf("menu_add %s: %v, want ErrNotFound", id, err)
		}
	}
	f.apply("alice", Op{Type: "menu_add", LinkID: crepes}, Op{Type: "menu_add", LinkID: public, Servings: 2})
	v := f.apply("bob", Op{Type: "menu_add", LinkID: gratin}, Op{Type: "menu_add", LinkID: crepes})
	if len(v.Menu) != 3 {
		t.Fatalf("menu partagé : %d recettes, want 3 (une recette n'y entre qu'une fois)", len(v.Menu))
	}
	byID := map[string]MenuEntry{}
	for _, e := range v.Menu {
		byID[e.LinkID] = e
	}
	if e := byID[crepes]; e.Title != "Crêpes" || e.BaseServings != 6 || e.Servings != 6 || e.AddedBy != "alice" || len(e.Ingredients) != 1 {
		t.Errorf("crêpes : %+v", e)
	}
	if e := byID[gratin]; e.BaseServings != DefaultServings || e.Servings != DefaultServings {
		t.Errorf("recette sans nombre de personnes : %+v, want 4", e)
	}
	if e := byID[public]; e.Servings != 2 {
		t.Errorf("parts choisies à l'ajout : %+v", e)
	}
	v = f.apply("alice", Op{Type: "menu_servings", LinkID: crepes, Servings: 9})
	for _, e := range v.Menu {
		if e.LinkID == crepes && e.Servings != 9 {
			t.Errorf("parts : %d, want 9", e.Servings)
		}
	}
	if got := f.get("carol"); len(got.Menu) != 0 {
		t.Errorf("Carol, hors tribu, voit %d recettes", len(got.Menu))
	}

	// Une recette rendue privée par son auteur hors tribu disparaît du menu affiché
	oid, _ := bson.ObjectIDFromHex(public)
	if _, err := f.db.Collection("links").UpdateByID(f.ctx, oid, bson.M{"$set": bson.M{"visibility": "private"}}); err != nil {
		t.Fatal(err)
	}
	if v := f.get("bob"); len(v.Menu) != 2 {
		t.Errorf("recette devenue privée encore affichée : %d recettes", len(v.Menu))
	}
	// Une recette supprimée quitte le menu
	oid, _ = bson.ObjectIDFromHex(gratin)
	if _, err := f.db.Collection("links").DeleteOne(f.ctx, bson.M{"_id": oid}); err != nil {
		t.Fatal(err)
	}
	f.apply("alice", Op{Type: "menu_remove", LinkID: public})
	if v := f.get("alice"); len(v.Menu) != 1 || v.Menu[0].LinkID != crepes {
		t.Errorf("après suppression et retrait : %+v", v.Menu)
	}
}

func TestCookedRecipeLeavesTheMenuTheNextDay(t *testing.T) {
	f := setup(t)
	soupe := f.link(bson.M{"owner_id": "alice", "title": "Soupe", "category": recipeCategory})
	cake := f.link(bson.M{"owner_id": "alice", "title": "Cake", "category": recipeCategory})
	// Cuisinée avant d'être remise au menu : ça ne compte pas
	if _, err := f.db.Collection("link_dones").InsertOne(f.ctx, bson.M{"link_id": cake, "user_id": "bob", "created_at": f.clock.Add(-time.Hour)}); err != nil {
		t.Fatal(err)
	}
	f.apply("alice", Op{Type: "menu_add", LinkID: soupe}, Op{Type: "menu_add", LinkID: cake})
	f.clock = f.clock.Add(2 * time.Hour)
	before := f.get("bob").Version
	f.clock = f.clock.Add(time.Minute)
	if _, err := f.db.Collection("link_dones").InsertOne(f.ctx, bson.M{"link_id": soupe, "user_id": "bob", "created_at": f.clock}); err != nil {
		t.Fatal(err)
	}
	// Un appareil à jour apprend quand même qu'elle est cuisinée : la version change
	v, unchanged, err := f.svc.Get(f.ctx, "alice", before)
	if err != nil || unchanged || v.Version == before {
		t.Fatalf("recette cuisinée sans nouvelle version : unchanged=%v %v", unchanged, err)
	}
	if len(v.Menu) != 2 || !v.Menu[0].Cooked || v.Menu[1].Cooked {
		t.Fatalf("le jour même, la soupe reste estompée : %+v", v.Menu)
	}
	if _, unchanged, _ := f.svc.Get(f.ctx, "alice", v.Version); !unchanged {
		t.Error("relue sans changement : la version ne doit plus bouger")
	}
	f.clock = time.Date(2026, 11, 14, 0, 30, 0, 0, paris)
	v = f.get("bob")
	if len(v.Menu) != 1 || v.Menu[0].LinkID != cake {
		t.Errorf("le lendemain, la soupe quitte le menu : %+v", v.Menu)
	}
}

func TestItemsChecksAndFinish(t *testing.T) {
	f := setup(t)
	crepes := f.link(bson.M{"owner_id": "alice", "title": "Crêpes", "category": recipeCategory})
	f.apply("alice", Op{Type: "menu_add", LinkID: crepes},
		Op{Type: "item_add", ID: "lait-1", Label: "  Lait   demi-écrémé ", Quantity: "1 l", Aisle: "cremerie"},
		Op{Type: "item_add", ID: "lait-1", Label: "Lait"}, // rejouée : pas de doublon
		Op{Type: "item_add", Label: "Couches taille 4", Quantity: "1 paquet"},
		Op{Type: "pantry_have", Key: "sel"}, Op{Type: "aisle", Key: "creme de coco", Value: "epicerie"},
		Op{Type: "remove", Key: "muscade"})
	v := f.apply("bob", Op{Type: "check", Key: ItemKey("lait-1")}, Op{Type: "check", Key: "farine|250 g"},
		Op{Type: "quantity", Key: "beurre", Value: "250 g"}, Op{Type: "shopping_start"})
	if len(v.Items) != 2 || v.Items[0].Label != "Lait demi-écrémé" || v.Items[0].AddedBy != "alice" || v.Items[1].ID == "" {
		t.Fatalf("articles : %+v", v.Items)
	}
	if len(v.Checked) != 2 || v.Checked[0].By != "bob" || v.ShopperID != "bob" {
		t.Fatalf("coches : %+v, en magasin %q", v.Checked, v.ShopperID)
	}
	// Deux personnes sur la même ligne : la dernière l'emporte
	v = f.apply("alice", Op{Type: "check", Key: "farine|250 g"}, Op{Type: "uncheck", Key: ItemKey("lait-1")})
	if len(v.Checked) != 1 || v.Checked[0].By != "alice" {
		t.Errorf("dernière action : %+v", v.Checked)
	}
	v = f.apply("alice", Op{Type: "check", Key: ItemKey("lait-1")}, Op{Type: "finish"})
	if len(v.Checked) != 0 || len(v.Items) != 1 || v.Items[0].Label != "Couches taille 4" || v.ShopperID != "" {
		t.Errorf("terminer vide le panier seulement : %+v", v)
	}
	if len(v.Menu) != 1 || len(v.Pantry) != 1 || len(v.Aisles) != 1 || len(v.Quantities) != 1 || len(v.Removed) != 1 {
		t.Errorf("le menu, le placard et les réglages restent : %+v", v)
	}
	v = f.apply("bob", Op{Type: "clear"})
	if len(v.Menu)+len(v.Items)+len(v.Quantities)+len(v.Removed) != 0 || len(v.Pantry) != 1 || len(v.Aisles) != 1 {
		t.Errorf("nouvelle liste : garde le placard et les rayons seulement : %+v", v)
	}
}

func TestVersionAndShopperExpiry(t *testing.T) {
	f := setup(t)
	v := f.apply("alice", Op{Type: "shopping_start"})
	if _, unchanged, err := f.svc.Get(f.ctx, "bob", v.Version); err != nil || !unchanged {
		t.Errorf("rien n'a changé : unchanged=%v %v", unchanged, err)
	}
	f.apply("bob", Op{Type: "item_add", Label: "Pain"})
	if got, unchanged, _ := f.svc.Get(f.ctx, "alice", v.Version); unchanged || got.Version != v.Version+1 {
		t.Errorf("après un ajout : unchanged=%v", unchanged)
	}
	f.apply("bob", Op{Type: "shopping_stop"}) // ce n'est pas lui qui fait les courses
	f.clock = f.clock.Add(3 * time.Hour)
	if got := f.get("bob"); got.ShopperID != "" {
		t.Errorf("« en magasin » oublié après 2 h : %q", got.ShopperID)
	}
}

func TestPersonalListJoinsTheTribe(t *testing.T) {
	f := setup(t)
	recette := f.link(bson.M{"owner_id": "carol", "title": "Tarte", "category": recipeCategory})
	f.apply("carol", Op{Type: "menu_add", LinkID: recette}, Op{Type: "item_add", ID: "cafe", Label: "Café"}, Op{Type: "pantry_have", Key: "huile"})
	f.apply("alice", Op{Type: "item_add", ID: "pain", Label: "Pain"}, Op{Type: "pantry_have", Key: "sel"})
	f.tribe["carol"] = "tribu-ab" // Carol rejoint la tribu
	v := f.get("carol")
	if len(v.Menu) != 1 || len(v.Items) != 2 || len(v.Pantry) != 2 {
		t.Errorf("sa liste rejoint celle de la tribu : %+v", v)
	}
	if n, _ := f.db.Collection(Collection).CountDocuments(f.ctx, bson.M{"key": "carol"}); n != 0 {
		t.Errorf("sa liste d'avant reste (%d)", n)
	}
}

func TestOpsAreValidated(t *testing.T) {
	f := setup(t)
	crepes := f.link(bson.M{"owner_id": "alice", "title": "Crêpes", "category": recipeCategory})
	many := make([]Op, maxOps+1)
	for i := range many {
		many[i] = Op{Type: "shopping_start"}
	}
	bad := [][]Op{
		nil,
		many,
		{{Type: "inconnue"}},
		{{Type: "item_add", Label: " "}},
		{{Type: "item_add", Label: strings.Repeat("a", maxLabel+1)}},
		{{Type: "item_add", Label: "Lait", Aisle: "rayon-inconnu"}},
		{{Type: "item_add", ID: "a/b", Label: "Lait"}},
		{{Type: "menu_add", LinkID: crepes, Servings: 51}},
		{{Type: "menu_add", LinkID: crepes}, {Type: "menu_servings", LinkID: crepes, Servings: 0}},
		{{Type: "check", Key: ""}},
		{{Type: "aisle", Key: "lait", Value: "nulle-part"}},
		{{Type: "aisle_order", Order: []string{"boulangerie", "boulangerie"}}},
		{{Type: "quantity", Key: "lait", Value: strings.Repeat("1", maxQuantity+1)}},
	}
	for _, ops := range bad {
		if _, err := f.svc.Apply(f.ctx, "alice", ops); !errors.Is(err, ErrInvalidInput) {
			t.Errorf("%+v: %v, want ErrInvalidInput", ops, err)
		}
	}
	// Une opération refusée n'enregistre rien de celles qui la précèdent
	if v := f.get("alice"); len(v.Menu) != 0 {
		t.Errorf("le lot refusé a été en partie enregistré : %+v", v.Menu)
	}
	v := f.apply("alice", Op{Type: "aisle_order", Order: []string{"boulangerie", "fruits-legumes"}})
	if len(v.AisleOrder) != 2 || v.AisleOrder[0] != "boulangerie" {
		t.Errorf("ordre des rayons : %v", v.AisleOrder)
	}
}
