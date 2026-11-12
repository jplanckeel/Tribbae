package link

import (
	"context"
	"errors"
	"fmt"
	"testing"
	"time"

	pb "github.com/tribbae/backend/gen/tribbae/v1"
	"github.com/tribbae/backend/internal/interceptor"
	"go.mongodb.org/mongo-driver/v2/bson"
	"go.mongodb.org/mongo-driver/v2/mongo"
)

// « C'est fait » : l'état appartient à la tribu de l'idée et aux membres de
// son dossier, jamais à un compte extérieur.

// doneService : Alice et Bob forment une tribu ; les autres comptes sont seuls.
func doneService(db *mongo.Database) *Service {
	svc := NewService(db.Collection("links"), db.Collection("folders"))
	svc.SetTribePeers(func(_ context.Context, userID string) []string {
		if userID == "alice" || userID == "bob" {
			return []string{"alice", "bob"}
		}
		return []string{userID}
	})
	return svc
}

func day(offset int) string { return time.Now().UTC().AddDate(0, 0, offset).Format(doneDayLayout) }

func mustMark(t *testing.T, svc *Service, linkID, userID string, in DoneInput) *LinkDone {
	t.Helper()
	d, _, err := svc.MarkDone(context.Background(), linkID, userID, in)
	if err != nil {
		t.Fatalf("mark done as %s: %v", userID, err)
	}
	return d
}

func TestDoneCountsEveryTimeAndKeepsTheLatestDay(t *testing.T) {
	_, db, cleanup := setupTestDB(t)
	defer cleanup()
	ctx := context.Background()
	svc := doneService(db)

	l, _ := svc.Create(ctx, "alice", &Link{Title: "Soupe de potimarron", Category: "LINK_CATEGORY_RECETTE"})
	id := l.ID.Hex()
	mustMark(t, svc, id, "alice", DoneInput{DoneOn: day(-10), Rating: 4, Note: "  Avec des croûtons  "})
	mustMark(t, svc, id, "bob", DoneInput{DoneOn: day(-2)})
	mustMark(t, svc, id, "alice", DoneInput{DoneOn: day(-5)})

	st := svc.LoadStats(ctx, []*Link{l}, "alice")
	if got := st.Done[id].Count; got != 3 {
		t.Errorf("done count = %d, want 3", got)
	}
	if got := DoneDay(st.Done[id].Last); got != day(-2) {
		t.Errorf("last done = %s, want %s", got, day(-2))
	}
	dones, err := svc.ListDones(ctx, id, "bob")
	if err != nil || len(dones) != 3 {
		t.Fatalf("list dones = %d, %v", len(dones), err)
	}
	if DoneDay(dones[0].DoneOn) != day(-2) || DoneDay(dones[2].DoneOn) != day(-10) {
		t.Errorf("les souvenirs doivent aller du plus récent au plus ancien")
	}
	if dones[2].Note != "Avec des croûtons" || dones[2].Rating != 4 {
		t.Errorf("souvenir = %+v", dones[2])
	}
}

func TestDoneIsOnlySeenByTheTribeAndFolderMembers(t *testing.T) {
	_, db, cleanup := setupTestDB(t)
	defer cleanup()
	ctx := context.Background()
	svc := doneService(db)

	folderID := insertFolder(t, ctx, svc, "alice", "public", "carol")
	l, _ := svc.Create(ctx, "alice", &Link{Title: "Balade", Category: "LINK_CATEGORY_ACTIVITE", FolderID: folderID})
	id := l.ID.Hex()
	redo := true
	mustMark(t, svc, id, "alice", DoneInput{Redo: &redo})
	l, _ = svc.Get(ctx, id, "alice")
	h := NewHandler(svc)

	for _, member := range []string{"alice", "bob", "carol"} {
		p := h.one(ctx, l, member)
		if p.DoneCount != 1 || p.LastDoneAt == nil || !p.Redo {
			t.Errorf("%s doit voir l'état fait : %+v", member, p)
		}
	}
	// Dossier public : un compte extérieur et un visiteur voient l'idée, pas son état
	for _, outsider := range []string{"dave", ""} {
		if !svc.CanView(ctx, id, outsider) {
			t.Fatalf("%q doit voir l'idée du dossier public", outsider)
		}
		p := h.one(ctx, l, outsider)
		if p.DoneCount != 0 || p.LastDoneAt != nil || p.Redo || p.DoneFamilies != 0 {
			t.Errorf("%q ne doit rien voir de l'état fait : %+v", outsider, p)
		}
		if _, err := svc.ListDones(ctx, id, outsider); !errors.Is(err, ErrNotFound) {
			t.Errorf("list dones as %q = %v, want ErrNotFound", outsider, err)
		}
	}
	if _, _, err := svc.MarkDone(ctx, id, "dave", DoneInput{}); !errors.Is(err, ErrForbidden) {
		t.Errorf("mark as outsider = %v, want ErrForbidden", err)
	}
}

func TestEditorsCanMarkDoneViewersCannot(t *testing.T) {
	_, db, cleanup := setupTestDB(t)
	defer cleanup()
	ctx := context.Background()
	svc := doneService(db)

	fid := bson.NewObjectID()
	if _, err := svc.folderCol.InsertOne(ctx, bson.M{"_id": fid, "owner_id": "alice", "visibility": "private", "collaborators": bson.A{
		bson.M{"user_id": "carol", "role": "editor"},
		bson.M{"user_id": "victor", "role": "viewer"},
	}}); err != nil {
		t.Fatal(err)
	}
	l, _ := svc.Create(ctx, "alice", &Link{Title: "Musée", Category: "LINK_CATEGORY_EVENEMENT", FolderID: fid.Hex()})
	id := l.ID.Hex()

	mustMark(t, svc, id, "carol", DoneInput{})
	if _, _, err := svc.MarkDone(ctx, id, "victor", DoneInput{}); !errors.Is(err, ErrForbidden) {
		t.Errorf("mark as viewer = %v, want ErrForbidden", err)
	}
	if _, err := svc.DismissDonePrompt(ctx, id, "victor"); !errors.Is(err, ErrForbidden) {
		t.Errorf("dismiss as viewer = %v, want ErrForbidden", err)
	}
	// Le lecteur voit quand même que c'est fait
	if dones, err := svc.ListDones(ctx, id, "victor"); err != nil || len(dones) != 1 {
		t.Errorf("list dones as viewer = %d, %v", len(dones), err)
	}
}

func TestOnlyTheTribeRemovesItsMemories(t *testing.T) {
	_, db, cleanup := setupTestDB(t)
	defer cleanup()
	ctx := context.Background()
	svc := doneService(db)

	folderID := insertFolder(t, ctx, svc, "alice", "private", "carol")
	l, _ := svc.Create(ctx, "alice", &Link{Title: "Cabane", Category: "LINK_CATEGORY_ACTIVITE", FolderID: folderID})
	id := l.ID.Hex()
	mine := mustMark(t, svc, id, "alice", DoneInput{})
	theirs := mustMark(t, svc, id, "carol", DoneInput{})

	if _, err := svc.UnmarkDone(ctx, id, mine.ID.Hex(), "carol"); !errors.Is(err, ErrForbidden) {
		t.Errorf("unmark another tribe's memory = %v, want ErrForbidden", err)
	}
	if _, _, err := svc.UpdateDone(ctx, id, mine.ID.Hex(), "carol", DoneInput{Note: "à moi"}); !errors.Is(err, ErrForbidden) {
		t.Errorf("update another tribe's memory = %v, want ErrForbidden", err)
	}
	// Bob est de la tribu d'Alice : il corrige et retire son souvenir
	d, _, err := svc.UpdateDone(ctx, id, mine.ID.Hex(), "bob", DoneInput{Note: "Sous la pluie", Rating: 5})
	if err != nil || d.Note != "Sous la pluie" || d.Rating != 5 || DoneDay(d.DoneOn) != DoneDay(mine.DoneOn) {
		t.Fatalf("update as peer = %+v, %v", d, err)
	}
	if _, err := svc.UnmarkDone(ctx, id, mine.ID.Hex(), "bob"); err != nil {
		t.Fatalf("unmark as peer: %v", err)
	}
	if _, err := svc.UnmarkDone(ctx, id, theirs.ID.Hex(), "carol"); err != nil {
		t.Fatalf("unmark own memory: %v", err)
	}
	if n, _ := svc.dones().CountDocuments(ctx, bson.M{"link_id": id}); n != 0 {
		t.Errorf("%d souvenirs restants, want 0", n)
	}
}

func TestDoneFamiliesStartsAtFiveTribes(t *testing.T) {
	_, db, cleanup := setupTestDB(t)
	defer cleanup()
	ctx := context.Background()
	svc := doneService(db)

	folderID := insertFolder(t, ctx, svc, "alice", "public", "c1", "c2", "c3", "c4")
	l, _ := svc.Create(ctx, "alice", &Link{Title: "Fête de la courge", Category: "LINK_CATEGORY_EVENEMENT", FolderID: folderID, Visibility: "public"})
	id := l.ID.Hex()
	h := NewHandler(svc)

	// Alice et Bob : une seule tribu, même en marquant deux fois
	for _, u := range []string{"alice", "bob", "c1", "c2", "c3"} {
		mustMark(t, svc, id, u, DoneInput{})
	}
	if got := h.one(ctx, l, "").DoneFamilies; got != 0 {
		t.Errorf("4 tribus : done families = %d, want 0", got)
	}
	mustMark(t, svc, id, "c4", DoneInput{})
	for _, reader := range []string{"", "dave", "alice"} {
		if got := h.one(ctx, l, reader).DoneFamilies; got != 5 {
			t.Errorf("5 tribus vues par %q : done families = %d, want 5", reader, got)
		}
	}
	// Une idée privée ne publie aucun compteur
	private, _ := svc.Create(ctx, "alice", &Link{Title: "Privée", Category: "LINK_CATEGORY_ACTIVITE"})
	for i := range 5 {
		if _, err := svc.dones().InsertOne(ctx, LinkDone{ID: bson.NewObjectID(), LinkID: private.ID.Hex(), UserID: "x", TribeKey: fmt.Sprint("t", i), DoneOn: time.Now()}); err != nil {
			t.Fatal(err)
		}
	}
	if got := h.one(ctx, private, "alice").DoneFamilies; got != 0 {
		t.Errorf("idée privée : done families = %d, want 0", got)
	}
}

func TestGiftListIdeasAreNotMarkedDone(t *testing.T) {
	_, db, cleanup := setupTestDB(t)
	defer cleanup()
	ctx := context.Background()
	svc := doneService(db)

	fid := bson.NewObjectID()
	if _, err := svc.folderCol.InsertOne(ctx, bson.M{"_id": fid, "owner_id": "alice", "visibility": "private", "gift_reservations": true}); err != nil {
		t.Fatal(err)
	}
	gift, _ := svc.Create(ctx, "alice", &Link{Title: "Puzzle", Category: giftCategory, FolderID: fid.Hex()})
	if _, _, err := svc.MarkDone(ctx, gift.ID.Hex(), "alice", DoneInput{}); !errors.Is(err, ErrGiftList) {
		t.Errorf("mark a gift of a gift list = %v, want ErrGiftList", err)
	}
	// Les autres idées de la liste, et un cadeau hors liste, se marquent
	book, _ := svc.Create(ctx, "alice", &Link{Title: "Livre", Category: "LINK_CATEGORY_LIVRE", FolderID: fid.Hex()})
	mustMark(t, svc, book.ID.Hex(), "alice", DoneInput{})
	loose, _ := svc.Create(ctx, "alice", &Link{Title: "Vélo", Category: giftCategory})
	mustMark(t, svc, loose.ID.Hex(), "alice", DoneInput{})
}

func TestDeletingAnIdeaDeletesItsMemories(t *testing.T) {
	_, db, cleanup := setupTestDB(t)
	defer cleanup()
	ctx := context.Background()
	svc := doneService(db)

	l, _ := svc.Create(ctx, "alice", &Link{Title: "Atelier", Category: "LINK_CATEGORY_ACTIVITE"})
	other, _ := svc.Create(ctx, "alice", &Link{Title: "Autre", Category: "LINK_CATEGORY_ACTIVITE"})
	mustMark(t, svc, l.ID.Hex(), "alice", DoneInput{})
	mustMark(t, svc, l.ID.Hex(), "bob", DoneInput{})
	mustMark(t, svc, other.ID.Hex(), "alice", DoneInput{})

	if err := svc.Delete(ctx, l.ID.Hex(), "alice"); err != nil {
		t.Fatalf("delete: %v", err)
	}
	if n, _ := svc.dones().CountDocuments(ctx, bson.M{"link_id": l.ID.Hex()}); n != 0 {
		t.Errorf("%d souvenirs de l'idée supprimée, want 0", n)
	}
	if n, _ := svc.dones().CountDocuments(ctx, bson.M{}); n != 1 {
		t.Errorf("%d souvenirs en tout, want 1", n)
	}
}

func TestListLinksFiltersByDoneStatus(t *testing.T) {
	_, db, cleanup := setupTestDB(t)
	defer cleanup()
	svc := doneService(db)
	ctx := interceptor.WithUserID(context.Background(), "alice")

	done, _ := svc.Create(ctx, "alice", &Link{Title: "Faite", Category: "LINK_CATEGORY_ACTIVITE"})
	if _, err := svc.Create(ctx, "bob", &Link{Title: "À faire", Category: "LINK_CATEGORY_ACTIVITE"}); err != nil {
		t.Fatal(err)
	}
	mustMark(t, svc, done.ID.Hex(), "bob", DoneInput{})
	h := NewHandler(svc)

	titles := func(status string) []string {
		res, err := h.ListLinks(ctx, &pb.ListLinksRequest{Status: status})
		if err != nil {
			t.Fatalf("list %q: %v", status, err)
		}
		out := []string{}
		for _, l := range res.Links {
			out = append(out, l.Title)
		}
		return out
	}
	if got := titles("done"); len(got) != 1 || got[0] != "Faite" {
		t.Errorf("status=done → %v", got)
	}
	if got := titles("todo"); len(got) != 1 || got[0] != "À faire" {
		t.Errorf("status=todo → %v", got)
	}
	if got := titles(""); len(got) != 2 {
		t.Errorf("sans statut → %v", got)
	}
	if got := titles("all"); len(got) != 2 {
		t.Errorf("status=all → %v", got)
	}
	if _, err := h.ListLinks(ctx, &pb.ListLinksRequest{Status: "later"}); err == nil {
		t.Error("un statut inconnu doit être refusé")
	}
}

func TestDoneSummaryCountsEveryTime(t *testing.T) {
	_, db, cleanup := setupTestDB(t)
	defer cleanup()
	ctx := context.Background()
	svc := doneService(db)

	recipe, _ := svc.Create(ctx, "alice", &Link{Title: "Crêpes", Category: "LINK_CATEGORY_RECETTE"})
	outing, _ := svc.Create(ctx, "bob", &Link{Title: "Musée", Category: "LINK_CATEGORY_EVENEMENT"})
	activity, _ := svc.Create(ctx, "alice", &Link{Title: "Cabane", Category: "LINK_CATEGORY_ACTIVITE"})
	book, _ := svc.Create(ctx, "alice", &Link{Title: "BD", Category: "LINK_CATEGORY_LIVRE"})
	idea, _ := svc.Create(ctx, "alice", &Link{Title: "Ranger le garage", Category: "LINK_CATEGORY_IDEE"})
	theirs, _ := svc.Create(ctx, "dave", &Link{Title: "Chez Dave", Category: "LINK_CATEGORY_RECETTE"})

	for _, d := range []string{"2025-03-02", "2025-03-09", "2025-03-30"} {
		mustMark(t, svc, recipe.ID.Hex(), "alice", DoneInput{DoneOn: d})
	}
	mustMark(t, svc, outing.ID.Hex(), "bob", DoneInput{DoneOn: "2025-03-15"})
	mustMark(t, svc, activity.ID.Hex(), "alice", DoneInput{DoneOn: "2025-07-01"})
	mustMark(t, svc, book.ID.Hex(), "bob", DoneInput{DoneOn: "2025-12-31"})
	mustMark(t, svc, idea.ID.Hex(), "alice", DoneInput{DoneOn: "2025-01-01"})
	// Hors de l'année, et une autre tribu : pas comptés
	mustMark(t, svc, recipe.ID.Hex(), "alice", DoneInput{DoneOn: "2024-12-31"})
	mustMark(t, svc, theirs.ID.Hex(), "dave", DoneInput{DoneOn: "2025-03-03"})

	sum, err := svc.DoneSummary(ctx, "bob", 2025)
	if err != nil {
		t.Fatalf("summary: %v", err)
	}
	want := DoneSummary{Year: 2025, Outings: 2, Recipes: 3, Books: 1, Total: 7, BestMonth: 3, BestMonthCount: 4}
	if *sum != want {
		t.Errorf("summary = %+v, want %+v", *sum, want)
	}
	if empty, err := svc.DoneSummary(ctx, "carol", 2025); err != nil || empty.Total != 0 || empty.BestMonth != 0 {
		t.Errorf("summary of an empty year = %+v, %v", empty, err)
	}
}

func TestDoneInputIsValidated(t *testing.T) {
	_, db, cleanup := setupTestDB(t)
	defer cleanup()
	ctx := context.Background()
	svc := doneService(db)

	l, _ := svc.Create(ctx, "alice", &Link{Title: "Balade", Category: "LINK_CATEGORY_ACTIVITE"})
	id := l.ID.Hex()
	long := make([]rune, maxDoneNote+1)
	for i := range long {
		long[i] = 'é'
	}
	for name, in := range map[string]DoneInput{
		"jour futur":              {DoneOn: day(3)},
		"jour avant 2000":         {DoneOn: "1999-12-31"},
		"jour mal écrit":          {DoneOn: "26/10/2025"},
		"note hors bornes":        {Rating: 6},
		"mot trop long":           {Note: string(long)},
		"présent hors tribu":      {WithUserIDs: []string{"dave"}},
		"enfant d'une autre":      {WithChildIDs: []string{bson.NewObjectID().Hex()}},
		"photo d'un autre site":   {PhotoURL: "https://example.com/photo.jpg"},
		"photo d'un autre compte": {PhotoURL: "https://tribbae.fr/v1/photos/0123456789abcdef0123456789abcdef"},
	} {
		if _, _, err := svc.MarkDone(ctx, id, "alice", in); !errors.Is(err, ErrInvalidInput) {
			t.Errorf("%s : %v, want ErrInvalidInput", name, err)
		}
	}
	// Qui était là : un pair de la tribu, sans doublon
	d := mustMark(t, svc, id, "alice", DoneInput{WithUserIDs: []string{"bob", "alice", "bob"}})
	if len(d.WithUserIDs) != 2 {
		t.Errorf("with = %v, want bob et alice", d.WithUserIDs)
	}
	// Une photo envoyée par la tribu est acceptée
	if _, err := db.Collection("photos.files").InsertOne(ctx, bson.M{"filename": "0123456789abcdef0123456789abcdef", "metadata": bson.M{"owner_id": "bob"}}); err != nil {
		t.Fatal(err)
	}
	mustMark(t, svc, id, "alice", DoneInput{PhotoURL: "https://tribbae.fr/v1/photos/0123456789abcdef0123456789abcdef"})
}

func TestDoneDailyLimit(t *testing.T) {
	_, db, cleanup := setupTestDB(t)
	defer cleanup()
	ctx := context.Background()
	svc := doneService(db)

	l, _ := svc.Create(ctx, "alice", &Link{Title: "Crêpes", Category: "LINK_CATEGORY_RECETTE"})
	rows := make([]any, 0, DoneDailyLimit)
	for range DoneDailyLimit {
		rows = append(rows, LinkDone{ID: bson.NewObjectID(), LinkID: l.ID.Hex(), UserID: "alice", TribeKey: "alice", DoneOn: time.Now(), CreatedAt: time.Now()})
	}
	if _, err := svc.dones().InsertMany(ctx, rows); err != nil {
		t.Fatal(err)
	}
	if _, _, err := svc.MarkDone(ctx, l.ID.Hex(), "alice", DoneInput{}); !errors.Is(err, ErrDoneQuota) {
		t.Errorf("51e marquage du jour = %v, want ErrDoneQuota", err)
	}
	// Le plafond est par compte
	mustMark(t, svc, l.ID.Hex(), "bob", DoneInput{})
}
