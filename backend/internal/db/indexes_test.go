package db

import (
	"context"
	"testing"
	"time"

	"go.mongodb.org/mongo-driver/v2/bson"
	"go.mongodb.org/mongo-driver/v2/mongo"
	"go.mongodb.org/mongo-driver/v2/mongo/options"
)

func indexNames(t *testing.T, col *mongo.Collection) map[string]bool {
	t.Helper()
	cur, err := col.Indexes().List(context.Background())
	if err != nil {
		t.Fatal(err)
	}
	var rows []bson.M
	if err := cur.All(context.Background(), &rows); err != nil {
		t.Fatal(err)
	}
	out := map[string]bool{}
	for _, r := range rows {
		out[r["name"].(string)] = true
	}
	return out
}

// EnsureIndexes crée les index (deux fois sans erreur) et retire ceux qu'il remplace.
func TestEnsureIndexesDropsReplacedOnes(t *testing.T) {
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

	// Base d'avant : les anciens index existent
	old := map[string]bson.D{
		"idx_links_folder_id": {{Key: "folder_id", Value: 1}},
		"idx_links_category":  {{Key: "category", Value: 1}},
	}
	for name, keys := range old {
		if _, err := db.Collection("links").Indexes().CreateOne(ctx, mongo.IndexModel{Keys: keys, Options: options.Index().SetName(name)}); err != nil {
			t.Fatal(err)
		}
	}
	for range 2 {
		if err := EnsureIndexes(ctx, db); err != nil {
			t.Fatalf("EnsureIndexes: %v", err)
		}
	}
	links := indexNames(t, db.Collection("links"))
	for name := range old {
		if links[name] {
			t.Errorf("%s still exists", name)
		}
	}
	for _, name := range []string{"idx_links_visibility_updated", "idx_links_visibility_created", "idx_links_missing_image", "idx_links_folder_id_created_at"} {
		if !links[name] {
			t.Errorf("%s missing", name)
		}
	}
	if likes := indexNames(t, db.Collection("link_likes")); !likes["idx_link_likes_user_id"] || likes["idx_link_likes_link_id"] {
		t.Errorf("link_likes indexes: %v", likes)
	}
}
