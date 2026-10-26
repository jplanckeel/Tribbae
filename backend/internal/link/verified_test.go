package link

import (
	"context"
	"errors"
	"slices"
	"testing"

	"github.com/tribbae/backend/internal/auth"
	"go.mongodb.org/mongo-driver/v2/bson"
)

// onlyVerified simule la confirmation d'adresse : seuls ids sont confirmés.
func onlyVerified(ids ...string) func(context.Context, string) error {
	return func(_ context.Context, userID string) error {
		if slices.Contains(ids, userID) {
			return nil
		}
		return auth.ErrEmailNotVerified
	}
}

// Publier une idée (la rendre publique ou la ranger dans un dossier public)
// exige une adresse confirmée ; le reste marche sans.
func TestPublishingRequiresVerifiedEmail(t *testing.T) {
	_, db, cleanup := setupTestDB(t)
	defer cleanup()
	ctx := context.Background()
	svc := NewService(db.Collection("links"), db.Collection("folders"))
	svc.SetEmailCheck(onlyVerified("bob"))
	public := insertFolder(t, ctx, svc, "alice", "public")
	private := insertFolder(t, ctx, svc, "alice", "private")

	idea, err := svc.Create(ctx, "alice", &Link{Title: "Privée", Category: "LINK_CATEGORY_IDEE", FolderID: private})
	if err != nil {
		t.Fatalf("idée privée refusée : %v", err)
	}
	if _, err := svc.Create(ctx, "alice", &Link{Title: "Publique", Category: "LINK_CATEGORY_IDEE", Visibility: "public"}); !errors.Is(err, auth.ErrEmailNotVerified) {
		t.Errorf("idée publique sans adresse confirmée : %v", err)
	}
	if _, err := svc.Create(ctx, "alice", &Link{Title: "Dossier public", Category: "LINK_CATEGORY_IDEE", FolderID: public}); !errors.Is(err, auth.ErrEmailNotVerified) {
		t.Errorf("idée dans un dossier public sans adresse confirmée : %v", err)
	}
	idea.Visibility = "public"
	if _, err := svc.Update(ctx, idea.ID.Hex(), "alice", idea); !errors.Is(err, auth.ErrEmailNotVerified) {
		t.Errorf("publication sans adresse confirmée : %v", err)
	}
	idea.Visibility, idea.FolderID = "private", public
	if _, err := svc.Update(ctx, idea.ID.Hex(), "alice", idea); !errors.Is(err, auth.ErrEmailNotVerified) {
		t.Errorf("déplacement dans un dossier public sans adresse confirmée : %v", err)
	}
	idea.FolderID, idea.Title = private, "Renommée"
	if _, err := svc.Update(ctx, idea.ID.Hex(), "alice", idea); err != nil {
		t.Errorf("modification d'une idée privée refusée : %v", err)
	}

	// Une idée déjà publiée (avant la confirmation d'adresse) reste modifiable
	old := bson.NewObjectID()
	if _, err := svc.col.InsertOne(ctx, bson.M{"_id": old, "owner_id": "alice", "title": "Ancienne", "category": "LINK_CATEGORY_IDEE", "visibility": "public"}); err != nil {
		t.Fatal(err)
	}
	if _, err := svc.Update(ctx, old.Hex(), "alice", &Link{Title: "Ancienne, corrigée", Category: "LINK_CATEGORY_IDEE", Visibility: "public"}); err != nil {
		t.Errorf("modification d'une idée déjà publique refusée : %v", err)
	}

	if _, err := svc.Create(ctx, "bob", &Link{Title: "De Bob", Category: "LINK_CATEGORY_IDEE", Visibility: "public"}); err != nil {
		t.Errorf("publication avec adresse confirmée refusée : %v", err)
	}
}
