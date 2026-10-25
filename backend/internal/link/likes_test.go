package link

import (
	"context"
	"testing"

	"go.mongodb.org/mongo-driver/v2/bson"
	"go.mongodb.org/mongo-driver/v2/mongo"
	"go.mongodb.org/mongo-driver/v2/mongo/options"
)

// Un double appui sur « j'aime » ne compte qu'une fois, même en parallèle.
func TestLikeIsIdempotent(t *testing.T) {
	_, db, cleanup := setupTestDB(t)
	defer cleanup()
	ctx := context.Background()
	svc := NewService(db.Collection("links"), db.Collection("folders"))
	if _, err := db.Collection("link_likes").Indexes().CreateOne(ctx, mongo.IndexModel{
		Keys: bson.D{{Key: "link_id", Value: 1}, {Key: "user_id", Value: 1}}, Options: options.Index().SetUnique(true),
	}); err != nil {
		t.Fatal(err)
	}
	l, err := svc.Create(ctx, "alice", &Link{Title: "Crêpes", Category: "LINK_CATEGORY_RECETTE", Visibility: "public"})
	if err != nil {
		t.Fatal(err)
	}
	id := l.ID.Hex()

	errs := make(chan error, 4)
	for range 4 {
		go func() {
			_, err := svc.LikeLink(ctx, id, "bob")
			errs <- err
		}()
	}
	for range 4 {
		if err := <-errs; err != nil {
			t.Fatalf("like: %v", err)
		}
	}
	if n, _ := svc.GetLikeCount(ctx, id); n != 1 {
		t.Fatalf("likes = %d, want 1", n)
	}
	if n, err := svc.LikeLink(ctx, id, "carol"); err != nil || n != 2 {
		t.Fatalf("second user: %d %v", n, err)
	}
	if n, err := svc.UnlikeLink(ctx, id, "bob"); err != nil || n != 1 {
		t.Fatalf("unlike: %d %v", n, err)
	}
}
