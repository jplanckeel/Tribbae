package comment

import (
	"context"
	"testing"
	"time"

	"go.mongodb.org/mongo-driver/v2/bson"
)

// Les auteurs des commentaires sont chargés en une fois : nom et statut admin,
// nom vide pour un compte supprimé, commentaire écarté si l'auteur est invalide.
func TestCommentsCarryTheirAuthors(t *testing.T) {
	_, db, cleanup := setupTestDB(t)
	defer cleanup()
	ctx := context.Background()
	svc := NewService(db.Collection("comments"), db.Collection("links"), db.Collection("users"))

	alice, gone := bson.NewObjectID(), bson.NewObjectID()
	if _, err := db.Collection("users").InsertOne(ctx, bson.M{"_id": alice, "display_name": "Alice", "is_admin": true, "password": "hash"}); err != nil {
		t.Fatal(err)
	}
	now := time.Now()
	for i, uid := range []string{alice.Hex(), gone.Hex(), "not-an-id", alice.Hex()} {
		if _, err := db.Collection("comments").InsertOne(ctx, bson.M{
			"_id": bson.NewObjectID(), "link_id": "L1", "user_id": uid, "text": "c", "created_at": now.Add(time.Duration(i) * time.Minute),
		}); err != nil {
			t.Fatal(err)
		}
	}

	got, err := svc.GetComments(ctx, "L1")
	if err != nil {
		t.Fatal(err)
	}
	if len(got) != 3 {
		t.Fatalf("%d comments, want 3 (invalid author skipped)", len(got))
	}
	// Les plus récents d'abord
	want := []struct {
		name  string
		admin bool
	}{{"Alice", true}, {"", false}, {"Alice", true}}
	for i, w := range want {
		if got[i].UserDisplayName != w.name || got[i].UserIsAdmin != w.admin {
			t.Errorf("comment %d: %q admin=%v, want %q admin=%v", i, got[i].UserDisplayName, got[i].UserIsAdmin, w.name, w.admin)
		}
	}
}
