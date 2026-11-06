package link

import (
	"context"
	"testing"
	"time"

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

// La liste J'aime suit l'ordre des J'aime et oublie une idée redevenue invisible.
func TestListLikedHidesIdeasNoLongerVisible(t *testing.T) {
	_, db, cleanup := setupTestDB(t)
	defer cleanup()
	ctx := context.Background()
	svc := NewService(db.Collection("links"), db.Collection("folders"))

	first, _ := svc.Create(ctx, "alice", &Link{Title: "Première", Category: "LINK_CATEGORY_IDEE", Visibility: "public"})
	second, _ := svc.Create(ctx, "alice", &Link{Title: "Seconde", Category: "LINK_CATEGORY_IDEE", Visibility: "public"})
	for _, l := range []*Link{first, second} {
		if _, err := svc.LikeLink(ctx, l.ID.Hex(), "bob"); err != nil {
			t.Fatal(err)
		}
		time.Sleep(5 * time.Millisecond)
	}
	got, err := svc.ListLiked(ctx, "bob")
	if err != nil {
		t.Fatal(err)
	}
	if len(got) != 2 || got[0].ID != second.ID {
		t.Fatalf("liked = %v, want the latest like first", got)
	}

	if _, err := db.Collection("links").UpdateByID(ctx, second.ID, bson.M{"$set": bson.M{"visibility": "private"}}); err != nil {
		t.Fatal(err)
	}
	got, err = svc.ListLiked(ctx, "bob")
	if err != nil {
		t.Fatal(err)
	}
	if len(got) != 1 || got[0].ID != first.ID {
		t.Errorf("liked = %v, want only the visible idea", got)
	}
}

// Un favori devient un J'aime, sans revenir si on retire ce J'aime ensuite.
func TestMigrateFavoritesRunsOncePerIdea(t *testing.T) {
	_, db, cleanup := setupTestDB(t)
	defer cleanup()
	ctx := context.Background()
	svc := NewService(db.Collection("links"), db.Collection("folders"))

	idea, _ := svc.Create(ctx, "alice", &Link{Title: "Favorite", Category: "LINK_CATEGORY_IDEE"})
	if _, err := svc.ToggleFavorite(ctx, idea.ID.Hex(), "alice"); err != nil {
		t.Fatal(err)
	}
	if n, err := svc.MigrateFavorites(ctx); err != nil || n != 1 {
		t.Fatalf("migrated %d: %v", n, err)
	}
	if count, _ := svc.GetLikeCount(ctx, idea.ID.Hex()); count != 1 {
		t.Fatalf("likes = %d, want 1", count)
	}
	if _, err := svc.UnlikeLink(ctx, idea.ID.Hex(), "alice"); err != nil {
		t.Fatal(err)
	}
	if n, err := svc.MigrateFavorites(ctx); err != nil || n != 0 {
		t.Fatalf("second run migrated %d: %v", n, err)
	}
	if count, _ := svc.GetLikeCount(ctx, idea.ID.Hex()); count != 0 {
		t.Errorf("likes = %d, an unliked idea must stay unliked", count)
	}
}

// Supprimer une idée retire ses J'aime, et ceux restés d'avant disparaissent à la lecture.
func TestDeletedIdeasLeaveNoLikes(t *testing.T) {
	_, db, cleanup := setupTestDB(t)
	defer cleanup()
	ctx := context.Background()
	svc := NewService(db.Collection("links"), db.Collection("folders"))

	idea, _ := svc.Create(ctx, "alice", &Link{Title: "Idée", Category: "LINK_CATEGORY_IDEE", Visibility: "public"})
	if _, err := svc.LikeLink(ctx, idea.ID.Hex(), "bob"); err != nil {
		t.Fatal(err)
	}
	if err := svc.Delete(ctx, idea.ID.Hex(), "alice"); err != nil {
		t.Fatal(err)
	}
	if n, _ := db.Collection("link_likes").CountDocuments(ctx, bson.M{"user_id": "bob"}); n != 0 {
		t.Errorf("%d likes left after deleting the idea", n)
	}

	orphan := bson.NewObjectID().Hex()
	if _, err := db.Collection("link_likes").InsertOne(ctx, bson.M{"link_id": orphan, "user_id": "bob", "created_at": time.Now()}); err != nil {
		t.Fatal(err)
	}
	if _, err := svc.ListLiked(ctx, "bob"); err != nil {
		t.Fatal(err)
	}
	if n, _ := db.Collection("link_likes").CountDocuments(ctx, bson.M{"user_id": "bob"}); n != 0 {
		t.Errorf("%d orphan likes left after reading the list", n)
	}
}
