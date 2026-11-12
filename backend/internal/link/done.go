package link

import (
	"context"
	"errors"
	"net/url"
	"sort"
	"strings"
	"time"
	_ "time/tzdata" // heure de Paris même sans base de fuseaux dans l'image Docker
	"unicode/utf8"

	"github.com/tribbae/backend/internal/photo"
	"go.mongodb.org/mongo-driver/v2/bson"
	"go.mongodb.org/mongo-driver/v2/mongo"
	"go.mongodb.org/mongo-driver/v2/mongo/options"
)

// « C'est fait » : une ligne de link_dones par fois où une idée a été faite
// (sortie faite, recette cuisinée, livre lu, cadeau acheté), avec un souvenir
// facultatif. L'état appartient à la tribu de l'idée et aux membres de son
// dossier ; il n'est jamais visible d'un compte extérieur, d'Explorer ni du
// lien de partage. Les idées Cadeau d'une liste de cadeaux n'y passent pas :
// leur achat reste une réservation (internal/folder/gifts.go).

// LinkDone : une fois où l'idée a été faite.
type LinkDone struct {
	ID     bson.ObjectID `bson:"_id,omitempty"`
	LinkID string        `bson:"link_id"`
	// Qui l'a marquée
	UserID string `bson:"user_id"`
	// TribeKey : tribu de celui qui a marqué (plus petit identifiant de ses
	// pairs au moment du marquage), pour compter les tribus sans les nommer
	TribeKey string `bson:"tribe_key"`
	// Jour, sans heure (minuit UTC)
	DoneOn       time.Time `bson:"done_on"`
	WithUserIDs  []string  `bson:"with_user_ids"`
	WithChildIDs []string  `bson:"with_child_ids"`
	Rating       int32     `bson:"rating"`
	Note         string    `bson:"note"`
	PhotoURL     string    `bson:"photo_url"`
	CreatedAt    time.Time `bson:"created_at"`
}

// DoneInput : ce que le client envoie pour marquer une idée ou corriger un souvenir.
type DoneInput struct {
	// AAAA-MM-JJ ; aujourd'hui si vide
	DoneOn       string
	WithUserIDs  []string
	WithChildIDs []string
	Rating       int32
	Note         string
	PhotoURL     string
	// « À refaire », porté par l'idée ; nil = inchangé
	Redo *bool
}

// DoneSummary : « Notre année en idées ».
type DoneSummary struct {
	Year                      int32
	Outings, Recipes, Books   int32
	Total                     int32
	BestMonth, BestMonthCount int32
}

const (
	doneCollection = "link_dones"
	maxDoneNote    = 280
	maxDoneWith    = 30
	// DoneDailyLimit borne les marquages d'un compte sur 24 h.
	DoneDailyLimit = 50
	// DoneFamiliesMin : en dessous, le compteur public reste à 0 (anonymat).
	DoneFamiliesMin = 5
	doneDayLayout   = "2006-01-02"
	giftCategory    = "LINK_CATEGORY_CADEAU"
)

var (
	// ErrGiftList : une idée Cadeau d'une liste de cadeaux se marque par sa réservation.
	ErrGiftList = errors.New("gift list ideas are marked through their reservation")
	// ErrDoneQuota : trop de marquages aujourd'hui.
	ErrDoneQuota = errors.New("too many ideas marked done today")

	doneParis = func() *time.Location {
		loc, err := time.LoadLocation("Europe/Paris")
		if err != nil {
			return time.UTC
		}
		return loc
	}()
	doneEpoch = time.Date(2000, 1, 1, 0, 0, 0, 0, time.UTC)
)

func (s *Service) dones() *mongo.Collection { return s.col.Database().Collection(doneCollection) }

// DoneDay formate le jour d'un souvenir (AAAA-MM-JJ).
func DoneDay(t time.Time) string { return t.UTC().Format(doneDayLayout) }

// parseDoneDay lit le jour envoyé : aujourd'hui (heure de Paris) s'il est vide,
// jamais dans le futur — avec la marge des fuseaux en avance sur l'UTC — ni
// avant 2000.
func parseDoneDay(raw string, now time.Time) (time.Time, error) {
	raw = strings.TrimSpace(raw)
	if raw == "" {
		y, m, d := now.In(doneParis).Date()
		return time.Date(y, m, d, 0, 0, 0, 0, time.UTC), nil
	}
	day, err := time.Parse(doneDayLayout, raw)
	if err != nil {
		return time.Time{}, invalid("doneOn must be a day (YYYY-MM-DD)")
	}
	y, m, d := now.UTC().Add(14 * time.Hour).Date()
	if day.After(time.Date(y, m, d, 0, 0, 0, 0, time.UTC)) {
		return time.Time{}, invalid("doneOn cannot be in the future")
	}
	if day.Before(doneEpoch) {
		return time.Time{}, invalid("doneOn is too far in the past")
	}
	return day, nil
}

func uniqueIDs(in []string) []string {
	out := make([]string, 0, len(in))
	seen := map[string]bool{}
	for _, v := range in {
		v = strings.TrimSpace(v)
		if v != "" && !seen[v] {
			seen[v] = true
			out = append(out, v)
		}
	}
	return out
}

// tribeKey identifie la tribu d'un compte sans la nommer.
func (s *Service) tribeKey(ctx context.Context, userID string) string {
	key := userID
	for _, p := range s.peerIDs(ctx, userID) {
		if p < key {
			key = p
		}
	}
	return key
}

// doneLink charge l'idée et vérifie que l'utilisateur la voit.
func (s *Service) doneLink(ctx context.Context, linkID, userID string) (*Link, error) {
	id, err := bson.ObjectIDFromHex(linkID)
	if err != nil {
		return nil, ErrNotFound
	}
	var l Link
	if err := s.col.FindOne(ctx, bson.M{"_id": id}).Decode(&l); err != nil {
		if errors.Is(err, mongo.ErrNoDocuments) {
			return nil, ErrNotFound
		}
		return nil, err
	}
	if userID == "" || !s.canAccessLink(ctx, &l, userID) {
		return nil, ErrNotFound
	}
	return &l, nil
}

// canSeeDone : la tribu de l'idée et les membres de son dossier voient l'état « fait ».
func (s *Service) canSeeDone(ctx context.Context, l *Link, userID string) bool {
	if userID == "" {
		return false
	}
	if s.isPeer(ctx, userID, l.OwnerID) {
		return true
	}
	member, _ := s.folderAccess(ctx, l.FolderID, userID)
	return member
}

// canMarkDone : la tribu de l'idée et les collaborateurs éditeurs de son dossier.
func (s *Service) canMarkDone(ctx context.Context, l *Link, userID string) bool {
	return s.isPeer(ctx, userID, l.OwnerID) || s.canEditFolder(ctx, l.FolderID, userID)
}

// inGiftList : idée Cadeau rangée dans une liste de cadeaux.
func (s *Service) inGiftList(ctx context.Context, l *Link) bool {
	if l.Category != giftCategory {
		return false
	}
	fid, err := bson.ObjectIDFromHex(l.FolderID)
	if err != nil {
		return false
	}
	n, _ := s.folderCol.CountDocuments(ctx, bson.M{"_id": fid, "gift_reservations": true})
	return n > 0
}

// sanitizeDone valide un souvenir. previousPhoto est la photo déjà enregistrée
// (elle n'est pas revérifiée : un pair peut corriger le souvenir d'un autre).
func (s *Service) sanitizeDone(ctx context.Context, userID string, in DoneInput, previousPhoto string) (*LinkDone, error) {
	day, err := parseDoneDay(in.DoneOn, time.Now())
	if err != nil {
		return nil, err
	}
	if in.Rating < 0 || in.Rating > 5 {
		return nil, invalid("rating must be between 0 and 5")
	}
	note := strings.TrimSpace(in.Note)
	if utf8.RuneCountInString(note) > maxDoneNote {
		return nil, invalid("note is too long")
	}
	users, children := uniqueIDs(in.WithUserIDs), uniqueIDs(in.WithChildIDs)
	if len(users) > maxDoneWith || len(children) > maxDoneWith {
		return nil, invalid("too many people")
	}
	peers := s.peerIDs(ctx, userID)
	inTribe := make(map[string]bool, len(peers))
	for _, p := range peers {
		inTribe[p] = true
	}
	for _, u := range users {
		if !inTribe[u] {
			return nil, invalid("withUserIds must be members of the tribe")
		}
	}
	if len(children) > 0 {
		ids, owners := make(bson.A, 0, len(children)), make(bson.A, 0, len(peers))
		for _, c := range children {
			oid, err := bson.ObjectIDFromHex(c)
			if err != nil {
				return nil, invalid("withChildIds must be children of the tribe")
			}
			ids = append(ids, oid)
		}
		for _, p := range peers {
			if oid, err := bson.ObjectIDFromHex(p); err == nil {
				owners = append(owners, oid)
			}
		}
		n, err := s.col.Database().Collection("children").CountDocuments(ctx, bson.M{"_id": bson.M{"$in": ids}, "ownerId": bson.M{"$in": owners}})
		if err != nil {
			return nil, err
		}
		if int(n) != len(children) {
			return nil, invalid("withChildIds must be children of the tribe")
		}
	}
	photoURL := strings.TrimSpace(in.PhotoURL)
	if photoURL != "" && photoURL != previousPhoto {
		if err := s.ownPhoto(ctx, photoURL, peers); err != nil {
			return nil, err
		}
	}
	return &LinkDone{DoneOn: day, WithUserIDs: users, WithChildIDs: children, Rating: in.Rating, Note: note, PhotoURL: photoURL}, nil
}

// ownPhoto : l'adresse est celle d'une photo envoyée par le compte (ou sa tribu).
func (s *Service) ownPhoto(ctx context.Context, raw string, peers []string) error {
	u, err := url.Parse(raw)
	if err != nil || len(raw) > maxURLLen || (u.Scheme != "https" && u.Scheme != "http") || !strings.HasPrefix(u.Path, photo.Path) {
		return invalid("photoUrl must be a photo sent to Tribbae")
	}
	token := strings.TrimPrefix(u.Path, photo.Path)
	if !photo.ValidToken(token) {
		return invalid("photoUrl must be a photo sent to Tribbae")
	}
	n, err := s.col.Database().Collection(photo.Bucket+".files").CountDocuments(ctx, bson.M{"filename": token, "metadata.owner_id": bson.M{"$in": peers}})
	if err != nil {
		return err
	}
	if n == 0 {
		return invalid("photoUrl must be a photo of the account")
	}
	return nil
}

func (s *Service) setRedo(ctx context.Context, l *Link, redo *bool) error {
	if redo == nil || *redo == l.Redo {
		return nil
	}
	l.Redo = *redo
	// updated_at reste tel quel : il ordonne les idées publiques
	_, err := s.col.UpdateOne(ctx, bson.M{"_id": l.ID}, bson.M{"$set": bson.M{"redo": *redo}})
	return err
}

// MarkDone enregistre une fois où l'idée a été faite, avec son souvenir.
func (s *Service) MarkDone(ctx context.Context, linkID, userID string, in DoneInput) (*LinkDone, *Link, error) {
	l, err := s.doneLink(ctx, linkID, userID)
	if err != nil {
		return nil, nil, err
	}
	if !s.canMarkDone(ctx, l, userID) {
		return nil, nil, ErrForbidden
	}
	if s.inGiftList(ctx, l) {
		return nil, nil, ErrGiftList
	}
	d, err := s.sanitizeDone(ctx, userID, in, "")
	if err != nil {
		return nil, nil, err
	}
	n, err := s.dones().CountDocuments(ctx, bson.M{"user_id": userID, "created_at": bson.M{"$gte": time.Now().Add(-24 * time.Hour)}})
	if err != nil {
		return nil, nil, err
	}
	if n >= DoneDailyLimit {
		return nil, nil, ErrDoneQuota
	}
	d.ID = bson.NewObjectID()
	d.LinkID = linkID
	d.UserID = userID
	d.TribeKey = s.tribeKey(ctx, userID)
	d.CreatedAt = time.Now()
	if _, err := s.dones().InsertOne(ctx, d); err != nil {
		return nil, nil, err
	}
	if err := s.setRedo(ctx, l, in.Redo); err != nil {
		return nil, nil, err
	}
	return d, l, nil
}

// ownDone charge un souvenir que l'utilisateur peut corriger ou retirer : celui
// de sa tribu, sur une idée qu'il peut marquer.
func (s *Service) ownDone(ctx context.Context, linkID, doneID, userID string) (*LinkDone, *Link, error) {
	l, err := s.doneLink(ctx, linkID, userID)
	if err != nil {
		return nil, nil, err
	}
	id, err := bson.ObjectIDFromHex(doneID)
	if err != nil {
		return nil, nil, ErrNotFound
	}
	var d LinkDone
	if err := s.dones().FindOne(ctx, bson.M{"_id": id, "link_id": linkID}).Decode(&d); err != nil {
		if errors.Is(err, mongo.ErrNoDocuments) {
			return nil, nil, ErrNotFound
		}
		return nil, nil, err
	}
	if !s.canSeeDone(ctx, l, userID) {
		return nil, nil, ErrNotFound
	}
	if !s.isPeer(ctx, userID, d.UserID) || !s.canMarkDone(ctx, l, userID) {
		return nil, nil, ErrForbidden
	}
	return &d, l, nil
}

// UpdateDone corrige un souvenir de sa tribu.
func (s *Service) UpdateDone(ctx context.Context, linkID, doneID, userID string, in DoneInput) (*LinkDone, *Link, error) {
	d, l, err := s.ownDone(ctx, linkID, doneID, userID)
	if err != nil {
		return nil, nil, err
	}
	if in.DoneOn == "" {
		in.DoneOn = DoneDay(d.DoneOn)
	}
	next, err := s.sanitizeDone(ctx, userID, in, d.PhotoURL)
	if err != nil {
		return nil, nil, err
	}
	d.DoneOn, d.WithUserIDs, d.WithChildIDs = next.DoneOn, next.WithUserIDs, next.WithChildIDs
	d.Rating, d.Note, d.PhotoURL = next.Rating, next.Note, next.PhotoURL
	if _, err := s.dones().UpdateOne(ctx, bson.M{"_id": d.ID}, bson.M{"$set": bson.M{
		"done_on": d.DoneOn, "with_user_ids": d.WithUserIDs, "with_child_ids": d.WithChildIDs,
		"rating": d.Rating, "note": d.Note, "photo_url": d.PhotoURL,
	}}); err != nil {
		return nil, nil, err
	}
	if err := s.setRedo(ctx, l, in.Redo); err != nil {
		return nil, nil, err
	}
	return d, l, nil
}

// UnmarkDone retire un souvenir de sa tribu (« Plus fait », ou « Annuler »).
func (s *Service) UnmarkDone(ctx context.Context, linkID, doneID, userID string) (*Link, error) {
	d, l, err := s.ownDone(ctx, linkID, doneID, userID)
	if err != nil {
		return nil, err
	}
	if _, err := s.dones().DeleteOne(ctx, bson.M{"_id": d.ID}); err != nil {
		return nil, err
	}
	return l, nil
}

// ListDones retourne les souvenirs d'une idée, du plus récent au plus ancien.
func (s *Service) ListDones(ctx context.Context, linkID, userID string) ([]*LinkDone, error) {
	l, err := s.doneLink(ctx, linkID, userID)
	if err != nil {
		return nil, err
	}
	if !s.canSeeDone(ctx, l, userID) {
		return nil, ErrNotFound
	}
	cur, err := s.dones().Find(ctx, bson.M{"link_id": linkID},
		options.Find().SetSort(bson.D{{Key: "done_on", Value: -1}, {Key: "created_at", Value: -1}}))
	if err != nil {
		return nil, err
	}
	out := []*LinkDone{}
	return out, cur.All(ctx, &out)
}

// DismissDonePrompt : « Pas cette fois », on ne redemande plus si la sortie a été faite.
func (s *Service) DismissDonePrompt(ctx context.Context, linkID, userID string) (*Link, error) {
	l, err := s.doneLink(ctx, linkID, userID)
	if err != nil {
		return nil, err
	}
	if !s.canMarkDone(ctx, l, userID) {
		return nil, ErrForbidden
	}
	if !l.DonePromptDismissed {
		if _, err := s.col.UpdateOne(ctx, bson.M{"_id": l.ID}, bson.M{"$set": bson.M{"done_prompt_dismissed": true}}); err != nil {
			return nil, err
		}
		l.DonePromptDismissed = true
	}
	return l, nil
}

// DoneInfo : état « fait » d'une idée pour un lecteur qui a le droit de le voir.
type DoneInfo struct {
	Count int32
	Last  time.Time
}

// loadDones remplit l'état « fait » des idées dont le lecteur est membre
// (sa tribu, ou un dossier où il collabore) et, pour les idées publiques, le
// nombre de tribus qui les ont faites — en deux requêtes pour tout le lot.
func (s *Service) loadDones(ctx context.Context, links []*Link, ids []string, userID string, st *Stats) {
	visible := map[string]bool{}
	if userID != "" {
		peers := map[string]bool{}
		for _, p := range s.peerIDs(ctx, userID) {
			peers[p] = true
		}
		folders := bson.A{}
		seen := map[string]bool{}
		for _, l := range links {
			if peers[l.OwnerID] {
				visible[l.ID.Hex()] = true
			} else if fid, err := bson.ObjectIDFromHex(l.FolderID); err == nil && !seen[l.FolderID] {
				seen[l.FolderID] = true
				folders = append(folders, fid)
			}
		}
		if len(folders) > 0 {
			owners := make(bson.A, 0, len(peers))
			for p := range peers {
				owners = append(owners, p)
			}
			filter := bson.M{"_id": bson.M{"$in": folders}, "$or": bson.A{
				bson.M{"owner_id": bson.M{"$in": owners}},
				bson.M{"collaborators.user_id": userID},
			}}
			if cur, err := s.folderCol.Find(ctx, filter, options.Find().SetProjection(bson.M{"_id": 1})); err == nil {
				var rows []struct {
					ID bson.ObjectID `bson:"_id"`
				}
				if cur.All(ctx, &rows) == nil {
					member := map[string]bool{}
					for _, r := range rows {
						member[r.ID.Hex()] = true
					}
					for _, l := range links {
						if member[l.FolderID] {
							visible[l.ID.Hex()] = true
						}
					}
				}
			}
		}
	}
	st.DoneVisible = visible

	pipeline := mongo.Pipeline{
		{{Key: "$match", Value: bson.M{"link_id": bson.M{"$in": ids}}}},
		{{Key: "$group", Value: bson.M{
			"_id":    "$link_id",
			"n":      bson.M{"$sum": 1},
			"last":   bson.M{"$max": "$done_on"},
			"tribes": bson.M{"$addToSet": "$tribe_key"},
		}}},
		{{Key: "$project", Value: bson.M{"n": 1, "last": 1, "tribes": bson.M{"$size": "$tribes"}}}},
	}
	cur, err := s.dones().Aggregate(ctx, pipeline)
	if err != nil {
		return
	}
	var rows []struct {
		ID     string    `bson:"_id"`
		N      int32     `bson:"n"`
		Last   time.Time `bson:"last"`
		Tribes int32     `bson:"tribes"`
	}
	if cur.All(ctx, &rows) != nil {
		return
	}
	public := map[string]bool{}
	for _, l := range links {
		if !l.Hidden && (l.Visibility == "public" || (l.FolderID != "" && st.PublicFolders[l.FolderID] == l.OwnerID)) {
			public[l.ID.Hex()] = true
		}
	}
	for _, r := range rows {
		if visible[r.ID] {
			st.Done[r.ID] = DoneInfo{Count: r.N, Last: r.Last}
		}
		if public[r.ID] && r.Tribes >= DoneFamiliesMin {
			st.DoneFamilies[r.ID] = r.Tribes
		}
	}
}

// DoneSummary compte ce que la tribu a fait dans l'année : chaque fois compte.
func (s *Service) DoneSummary(ctx context.Context, userID string, year int) (*DoneSummary, error) {
	if year < 2000 || year > 2200 {
		return nil, invalid("year is out of range")
	}
	out := &DoneSummary{Year: int32(year)}
	from := time.Date(year, 1, 1, 0, 0, 0, 0, time.UTC)
	cur, err := s.dones().Find(ctx,
		bson.M{"user_id": bson.M{"$in": s.peerIDs(ctx, userID)}, "done_on": bson.M{"$gte": from, "$lt": from.AddDate(1, 0, 0)}},
		options.Find().SetProjection(bson.M{"link_id": 1, "done_on": 1}))
	if err != nil {
		return nil, err
	}
	var rows []struct {
		LinkID string    `bson:"link_id"`
		DoneOn time.Time `bson:"done_on"`
	}
	if err := cur.All(ctx, &rows); err != nil {
		return nil, err
	}
	if len(rows) == 0 {
		return out, nil
	}
	ids := bson.A{}
	seen := map[string]bool{}
	for _, r := range rows {
		if oid, err := bson.ObjectIDFromHex(r.LinkID); err == nil && !seen[r.LinkID] {
			seen[r.LinkID] = true
			ids = append(ids, oid)
		}
	}
	lc, err := s.col.Find(ctx, bson.M{"_id": bson.M{"$in": ids}}, options.Find().SetProjection(bson.M{"category": 1}))
	if err != nil {
		return nil, err
	}
	var cats []struct {
		ID       bson.ObjectID `bson:"_id"`
		Category string        `bson:"category"`
	}
	if err := lc.All(ctx, &cats); err != nil {
		return nil, err
	}
	category := make(map[string]string, len(cats))
	for _, c := range cats {
		category[c.ID.Hex()] = c.Category
	}
	months := map[int]int32{}
	for _, r := range rows {
		cat, ok := category[r.LinkID]
		if !ok {
			continue
		}
		switch cat {
		case "LINK_CATEGORY_ACTIVITE", "LINK_CATEGORY_EVENEMENT":
			out.Outings++
		case "LINK_CATEGORY_RECETTE":
			out.Recipes++
		case "LINK_CATEGORY_LIVRE":
			out.Books++
		}
		out.Total++
		months[int(r.DoneOn.UTC().Month())]++
	}
	order := make([]int, 0, len(months))
	for m := range months {
		order = append(order, m)
	}
	sort.Ints(order)
	for _, m := range order {
		if months[m] > out.BestMonthCount {
			out.BestMonth, out.BestMonthCount = int32(m), months[m]
		}
	}
	return out, nil
}
