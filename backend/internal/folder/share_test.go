package folder

import (
	"context"
	"testing"
	"time"

	"go.mongodb.org/mongo-driver/v2/bson"
	"go.mongodb.org/mongo-driver/v2/mongo"
	"go.mongodb.org/mongo-driver/v2/mongo/options"
)

// Un lien de partage montre les idées du propriétaire avec leurs champs, date comprise.
func TestSharedFolderShowsOwnerIdeas(t *testing.T) {
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

	fid := bson.NewObjectID()
	if _, err := db.Collection("folders").InsertOne(ctx, bson.M{"_id": fid, "owner_id": "alice", "name": "Noël", "visibility": "private"}); err != nil {
		t.Fatal(err)
	}
	token, _, err := svc.GenerateShareToken(ctx, fid.Hex(), "alice")
	if err != nil {
		t.Fatal(err)
	}
	created := time.Date(2026, 10, 1, 12, 0, 0, 0, time.UTC)
	ideas := []any{
		bson.M{"folder_id": fid.Hex(), "owner_id": "alice", "title": "Sapin", "category": "LINK_CATEGORY_DECORATION", "tags": bson.A{"noël"}, "price": "20 €", "created_at": created},
		bson.M{"folder_id": fid.Hex(), "owner_id": "bob", "title": "Privée de Bob", "visibility": "private"},
	}
	if _, err := db.Collection("links").InsertMany(ctx, ideas); err != nil {
		t.Fatal(err)
	}

	f, links, err := svc.GetByShareToken(ctx, token)
	if err != nil || f.Name != "Noël" {
		t.Fatalf("folder: %v %v", f, err)
	}
	if len(links) != 1 {
		t.Fatalf("%d ideas, want only the owner's", len(links))
	}
	l := links[0]
	if l.Title != "Sapin" || l.Category != "LINK_CATEGORY_DECORATION" || len(l.Tags) != 1 || l.Price != "20 €" || !l.CreatedAt.Equal(created) || l.ID.IsZero() {
		t.Errorf("idea = %+v", l)
	}
}
