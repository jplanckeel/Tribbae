package link

import (
	"context"
	"errors"
	"testing"

	"go.mongodb.org/mongo-driver/v2/bson"
)

// Confidentialité par défaut (RGPD) : une idée n'est publiée que par la
// décision explicite de son auteur.

func insertFolder(t *testing.T, ctx context.Context, svc *Service, owner, visibility string, collaborators ...string) string {
	t.Helper()
	id := bson.NewObjectID()
	var collabs bson.A
	for _, c := range collaborators {
		collabs = append(collabs, bson.M{"user_id": c, "role": "editor"})
	}
	if _, err := svc.folderCol.InsertOne(ctx, bson.M{
		"_id": id, "owner_id": owner, "visibility": visibility, "collaborators": collabs,
	}); err != nil {
		t.Fatalf("insert folder: %v", err)
	}
	return id.Hex()
}

func TestLinkIsPrivateByDefault(t *testing.T) {
	_, db, cleanup := setupTestDB(t)
	defer cleanup()
	ctx := context.Background()
	svc := NewService(db.Collection("links"), db.Collection("folders"))

	l, err := svc.Create(ctx, "alice", &Link{Title: "Idée", Category: "LINK_CATEGORY_IDEE"})
	if err != nil {
		t.Fatalf("create: %v", err)
	}
	if l.Visibility != "private" {
		t.Errorf("visibility = %q, want private", l.Visibility)
	}
	if svc.CanView(ctx, l.ID.Hex(), "") || svc.CanView(ctx, l.ID.Hex(), "bob") {
		t.Error("une idée privée hors dossier ne doit être visible que de son auteur")
	}
	community, _ := svc.ListCommunity(ctx, "", 50)
	if len(community) != 0 {
		t.Errorf("community = %d liens, want 0", len(community))
	}
}

func TestPublicFolderDoesNotPublishCollaboratorsIdeas(t *testing.T) {
	_, db, cleanup := setupTestDB(t)
	defer cleanup()
	ctx := context.Background()
	svc := NewService(db.Collection("links"), db.Collection("folders"))

	folderID := insertFolder(t, ctx, svc, "alice", "public", "bob")
	own, _ := svc.Create(ctx, "alice", &Link{Title: "D'Alice", Category: "LINK_CATEGORY_IDEE", FolderID: folderID})
	collab, _ := svc.Create(ctx, "bob", &Link{Title: "De Bob", Category: "LINK_CATEGORY_IDEE", FolderID: folderID})
	published, _ := svc.Create(ctx, "bob", &Link{Title: "De Bob, publiée", Category: "LINK_CATEGORY_IDEE", FolderID: folderID, Visibility: "public"})

	// Visiteur anonyme : idées de la propriétaire + celles que Bob a publiées lui-même
	if !svc.CanView(ctx, own.ID.Hex(), "") {
		t.Error("l'idée de la propriétaire d'un dossier public doit être visible")
	}
	if svc.CanView(ctx, collab.ID.Hex(), "") {
		t.Error("l'idée privée d'un collaborateur ne doit pas être publiée par le propriétaire du dossier")
	}
	if !svc.CanView(ctx, published.ID.Hex(), "") {
		t.Error("une idée publiée par son auteur doit être visible")
	}
	visitor, err := svc.List(ctx, "", folderID)
	if err != nil {
		t.Fatalf("list as visitor: %v", err)
	}
	if len(visitor) != 2 {
		t.Errorf("visiteur : %d idées, want 2", len(visitor))
	}

	// Les membres voient tout
	if !svc.CanView(ctx, collab.ID.Hex(), "alice") {
		t.Error("la propriétaire doit voir les idées de ses collaborateurs")
	}
	member, _ := svc.List(ctx, "alice", folderID)
	if len(member) != 3 {
		t.Errorf("membre : %d idées, want 3", len(member))
	}
}

// Le client d'un autre compte ne connaît pas le dossier : la réponse dit si
// l'idée est visible de tous, selon la même règle que PublicFolderFilter.
func TestStatsTellWhichIdeasAPublicFolderExposes(t *testing.T) {
	_, db, cleanup := setupTestDB(t)
	defer cleanup()
	ctx := context.Background()
	svc := NewService(db.Collection("links"), db.Collection("folders"))

	public := insertFolder(t, ctx, svc, "alice", "public", "bob")
	private := insertFolder(t, ctx, svc, "alice", "private")
	own, _ := svc.Create(ctx, "alice", &Link{Title: "D'Alice", Category: "LINK_CATEGORY_IDEE", FolderID: public})
	collab, _ := svc.Create(ctx, "bob", &Link{Title: "De Bob", Category: "LINK_CATEGORY_IDEE", FolderID: public})
	hidden, _ := svc.Create(ctx, "alice", &Link{Title: "Privée", Category: "LINK_CATEGORY_IDEE", FolderID: private})

	h := &Handler{svc: svc}
	st := svc.LoadStats(ctx, []*Link{own, collab, hidden}, "")
	if !h.toProto(own, st).PublicViaFolder {
		t.Error("l'idée de la propriétaire est visible de tous")
	}
	if h.toProto(collab, st).PublicViaFolder {
		t.Error("l'idée d'un collaborateur reste réservée aux membres")
	}
	if h.toProto(hidden, st).PublicViaFolder {
		t.Error("un dossier privé n'expose rien")
	}
}

func TestOnlyAuthorCanMoveIdeaToAnotherFolder(t *testing.T) {
	_, db, cleanup := setupTestDB(t)
	defer cleanup()
	ctx := context.Background()
	svc := NewService(db.Collection("links"), db.Collection("folders"))

	shared := insertFolder(t, ctx, svc, "alice", "private", "bob")
	bobsPublic := insertFolder(t, ctx, svc, "bob", "public")
	l, _ := svc.Create(ctx, "alice", &Link{Title: "Privée", Category: "LINK_CATEGORY_IDEE", FolderID: shared})

	// Bob, éditeur du dossier partagé, ne peut pas déplacer l'idée d'Alice dans son dossier public
	_, err := svc.Update(ctx, l.ID.Hex(), "bob", &Link{Title: "Privée", Category: "LINK_CATEGORY_IDEE", FolderID: bobsPublic})
	if !errors.Is(err, ErrForbidden) {
		t.Fatalf("update = %v, want ErrForbidden", err)
	}
	// Il peut toujours la modifier sur place, sans pouvoir la publier
	updated, err := svc.Update(ctx, l.ID.Hex(), "bob", &Link{Title: "Renommée", Category: "LINK_CATEGORY_IDEE", FolderID: shared, Visibility: "public"})
	if err != nil {
		t.Fatalf("update in place: %v", err)
	}
	if updated.Visibility != "private" {
		t.Errorf("visibility = %q : seul l'auteur peut publier", updated.Visibility)
	}
}

func TestTribeSharesIdeasButNotPublication(t *testing.T) {
	_, db, cleanup := setupTestDB(t)
	defer cleanup()
	ctx := context.Background()
	svc := NewService(db.Collection("links"), db.Collection("folders"))
	// Alice et Bob forment une tribu ; Eve n'en fait pas partie
	svc.SetTribePeers(func(_ context.Context, u string) []string {
		if u == "alice" || u == "bob" {
			return []string{u, map[string]string{"alice": "bob", "bob": "alice"}[u]}
		}
		return []string{u}
	})
	alicesPublic := insertFolder(t, ctx, svc, "alice", "public")
	l, _ := svc.Create(ctx, "bob", &Link{Title: "Idée de Bob", Category: "LINK_CATEGORY_IDEE"})

	mine, _ := svc.List(ctx, "alice", "")
	if len(mine) != 1 || !svc.CanView(ctx, l.ID.Hex(), "alice") {
		t.Fatalf("Alice doit voir l'idée de Bob (tribu) : %d idées", len(mine))
	}
	if svc.CanView(ctx, l.ID.Hex(), "eve") {
		t.Error("Eve, hors tribu, ne doit pas voir l'idée privée de Bob")
	}
	// Alice modifie l'idée de Bob, sans pouvoir la publier
	updated, err := svc.Update(ctx, l.ID.Hex(), "alice", &Link{Title: "Renommée par Alice", Category: "LINK_CATEGORY_IDEE", Visibility: "public"})
	if err != nil || updated.Title != "Renommée par Alice" || updated.Visibility != "private" {
		t.Fatalf("update par la tribu : %+v, %v", updated, err)
	}
	// …ni la ranger dans son dossier public (ce serait la publier à la place de Bob)
	if _, err := svc.Update(ctx, l.ID.Hex(), "alice", &Link{Title: "x", Category: "LINK_CATEGORY_IDEE", FolderID: alicesPublic}); !errors.Is(err, ErrForbidden) {
		t.Errorf("déplacement vers un dossier public : err = %v, want ErrForbidden", err)
	}
	if _, err := svc.ToggleFavorite(ctx, l.ID.Hex(), "alice"); err != nil {
		t.Errorf("favori par la tribu : %v", err)
	}
	if err := svc.Delete(ctx, l.ID.Hex(), "eve"); !errors.Is(err, ErrForbidden) {
		t.Errorf("suppression hors tribu : err = %v", err)
	}
	if err := svc.Delete(ctx, l.ID.Hex(), "alice"); err != nil {
		t.Errorf("suppression par la tribu : %v", err)
	}
}

// Une idée retirée par la modération ne se republie pas et sort des dossiers publics.
func TestHiddenIdeaStaysUnpublished(t *testing.T) {
	_, db, cleanup := setupTestDB(t)
	defer cleanup()
	ctx := context.Background()
	svc := NewService(db.Collection("links"), db.Collection("folders"))

	public := insertFolder(t, ctx, svc, "alice", "public")
	idea, _ := svc.Create(ctx, "alice", &Link{Title: "Retirée", Category: "LINK_CATEGORY_IDEE", FolderID: public})
	if !svc.CanView(ctx, idea.ID.Hex(), "") {
		t.Fatal("before removal, the owner's idea in a public folder is visible")
	}
	if _, err := db.Collection("links").UpdateByID(ctx, idea.ID, bson.M{"$set": bson.M{"hidden": true}}); err != nil {
		t.Fatal(err)
	}
	if svc.CanView(ctx, idea.ID.Hex(), "") {
		t.Error("a removed idea must not be exposed by its public folder")
	}
	if visitor, _ := svc.List(ctx, "", public); len(visitor) != 0 {
		t.Errorf("visitor still sees %d ideas", len(visitor))
	}
	if !svc.CanView(ctx, idea.ID.Hex(), "alice") {
		t.Error("the owner keeps their removed idea")
	}
	again := *idea
	again.Visibility = "public"
	if _, err := svc.Update(ctx, idea.ID.Hex(), "alice", &again); !errors.Is(err, ErrHidden) {
		t.Errorf("republishing a removed idea: %v", err)
	}
}

// Supprimer une idée efface aussi ses commentaires.
func TestDeletingAnIdeaDeletesItsComments(t *testing.T) {
	_, db, cleanup := setupTestDB(t)
	defer cleanup()
	ctx := context.Background()
	svc := NewService(db.Collection("links"), db.Collection("folders"))
	idea, _ := svc.Create(ctx, "alice", &Link{Title: "Idée", Category: "LINK_CATEGORY_IDEE"})
	other, _ := svc.Create(ctx, "alice", &Link{Title: "Autre", Category: "LINK_CATEGORY_IDEE"})
	for _, l := range []*Link{idea, other} {
		if _, err := db.Collection("comments").InsertOne(ctx, bson.M{"link_id": l.ID.Hex(), "user_id": "bob", "text": "Super"}); err != nil {
			t.Fatal(err)
		}
	}
	if err := svc.Delete(ctx, idea.ID.Hex(), "alice"); err != nil {
		t.Fatal(err)
	}
	if n, _ := db.Collection("comments").CountDocuments(ctx, bson.M{"link_id": idea.ID.Hex()}); n != 0 {
		t.Errorf("%d comments left on the deleted idea", n)
	}
	if n, _ := db.Collection("comments").CountDocuments(ctx, bson.M{"link_id": other.ID.Hex()}); n != 1 {
		t.Error("comments of other ideas must stay")
	}
}

// Une idée réservée sur une liste de cadeaux s'affiche « réservée » à la tribu,
// tant qu'elle est dans ce dossier et que le dossier reste en liste de cadeaux.
func TestReservedGiftShowsInTheFolder(t *testing.T) {
	_, db, cleanup := setupTestDB(t)
	defer cleanup()
	ctx := context.Background()
	svc := NewService(db.Collection("links"), db.Collection("folders"))
	fid := bson.NewObjectID()
	if _, err := db.Collection("folders").InsertOne(ctx, bson.M{"_id": fid, "owner_id": "alice", "name": "Noël", "gift_reservations": true}); err != nil {
		t.Fatal(err)
	}
	gift, _ := svc.Create(ctx, "alice", &Link{Title: "Lego", Category: "LINK_CATEGORY_CADEAU", FolderID: fid.Hex()})
	if _, err := db.Collection("gift_reservations").InsertOne(ctx, bson.M{"link_id": gift.ID.Hex(), "folder_id": fid.Hex(), "owner_id": "alice"}); err != nil {
		t.Fatal(err)
	}
	if !svc.LoadStats(ctx, []*Link{gift}, "alice").Reserved[gift.ID.Hex()] {
		t.Error("reserved gift not marked")
	}
	moved := *gift
	moved.FolderID = ""
	if svc.LoadStats(ctx, []*Link{&moved}, "alice").Reserved[gift.ID.Hex()] {
		t.Error("a gift moved out of the list is no longer reserved")
	}
	if _, err := db.Collection("folders").UpdateByID(ctx, fid, bson.M{"$set": bson.M{"gift_reservations": false}}); err != nil {
		t.Fatal(err)
	}
	if svc.LoadStats(ctx, []*Link{gift}, "alice").Reserved[gift.ID.Hex()] {
		t.Error("reservations are hidden once the folder is no longer a gift list")
	}
}
