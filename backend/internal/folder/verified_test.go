package folder

import (
	"context"
	"errors"
	"slices"
	"testing"
	"time"

	"github.com/tribbae/backend/internal/auth"
	"go.mongodb.org/mongo-driver/v2/bson"
	"go.mongodb.org/mongo-driver/v2/mongo"
	"go.mongodb.org/mongo-driver/v2/mongo/options"
)

// Publier un dossier et y ajouter un collaborateur exigent une adresse confirmée.
func TestPublishingAndSharingRequireVerifiedEmail(t *testing.T) {
	ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
	defer cancel()
	client, err := mongo.Connect(options.Client().ApplyURI("mongodb://localhost:27017").SetServerSelectionTimeout(3 * time.Second))
	if err != nil || client.Ping(ctx, nil) != nil {
		t.Skip("Skipping test: MongoDB unavailable")
	}
	db := client.Database("tribbae_test_" + bson.NewObjectID().Hex())
	t.Cleanup(func() {
		_ = db.Drop(context.Background())
		_ = client.Disconnect(context.Background())
	})
	svc := NewService(db.Collection("folders"), db.Collection("links"), db.Collection("users"), "https://tribbae.example")
	svc.SetEmailCheck(func(_ context.Context, userID string) error {
		if slices.Contains([]string{"bob"}, userID) {
			return nil
		}
		return auth.ErrEmailNotVerified
	})
	if _, err := db.Collection("users").InsertOne(ctx, bson.M{"email": "carol@ex.fr", "display_name": "Carol"}); err != nil {
		t.Fatal(err)
	}

	if _, err := svc.Create(ctx, "alice", "Sorties", "", "", "public", "", nil, Style{}); !errors.Is(err, auth.ErrEmailNotVerified) {
		t.Errorf("dossier public sans adresse confirmée : %v", err)
	}
	f, err := svc.Create(ctx, "alice", "Sorties", "", "", "private", "", nil, Style{})
	if err != nil {
		t.Fatalf("dossier privé refusé : %v", err)
	}
	if _, err := svc.Update(ctx, f.ID.Hex(), "alice", "Sorties", "", "", "public", "", nil, StylePatch{}); !errors.Is(err, auth.ErrEmailNotVerified) {
		t.Errorf("publication sans adresse confirmée : %v", err)
	}
	if _, err := svc.Update(ctx, f.ID.Hex(), "alice", "Sorties d'automne", "", "", "private", "", nil, StylePatch{}); err != nil {
		t.Errorf("modification d'un dossier privé refusée : %v", err)
	}
	if _, err := svc.AddCollaborator(ctx, f.ID.Hex(), "alice", "carol@ex.fr", "editor"); !errors.Is(err, auth.ErrEmailNotVerified) {
		t.Errorf("ajout d'un collaborateur sans adresse confirmée : %v", err)
	}

	// Un dossier déjà publié reste modifiable
	old := bson.NewObjectID()
	if _, err := db.Collection("folders").InsertOne(ctx, bson.M{"_id": old, "owner_id": "alice", "name": "Ancien", "visibility": "public"}); err != nil {
		t.Fatal(err)
	}
	if _, err := svc.Update(ctx, old.Hex(), "alice", "Ancien, renommé", "", "", "public", "", nil, StylePatch{}); err != nil {
		t.Errorf("modification d'un dossier déjà public refusée : %v", err)
	}

	g, err := svc.Create(ctx, "bob", "Recettes", "", "", "public", "", nil, Style{})
	if err != nil {
		t.Fatalf("publication avec adresse confirmée refusée : %v", err)
	}
	if _, err := svc.AddCollaborator(ctx, g.ID.Hex(), "bob", "carol@ex.fr", "editor"); err != nil {
		t.Errorf("ajout d'un collaborateur avec adresse confirmée refusé : %v", err)
	}
}
