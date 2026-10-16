package link

import (
	"context"
	"errors"
	"testing"

	"go.mongodb.org/mongo-driver/bson"
	"go.mongodb.org/mongo-driver/bson/primitive"
)

// Confidentialité par défaut (RGPD) : une idée n'est publiée que par la
// décision explicite de son auteur.

func insertFolder(t *testing.T, ctx context.Context, svc *Service, owner, visibility string, collaborators ...string) string {
	t.Helper()
	id := primitive.NewObjectID()
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
