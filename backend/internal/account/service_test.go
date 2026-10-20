package account

import (
	"context"
	"testing"
	"time"

	"go.mongodb.org/mongo-driver/bson"
	"go.mongodb.org/mongo-driver/bson/primitive"
	"go.mongodb.org/mongo-driver/mongo"
	"go.mongodb.org/mongo-driver/mongo/options"

	"github.com/tribbae/backend/internal/tribe"
)

func setupTestDB(t *testing.T) *mongo.Database {
	t.Helper()
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	client, err := mongo.Connect(ctx, options.Client().ApplyURI("mongodb://localhost:27017").SetServerSelectionTimeout(3*time.Second))
	if err != nil {
		t.Skipf("Skipping test: MongoDB unavailable: %v", err)
	}
	if err := client.Ping(ctx, nil); err != nil {
		t.Skipf("Skipping test: MongoDB unavailable: %v", err)
	}
	db := client.Database("tribbae_test_" + primitive.NewObjectID().Hex())
	t.Cleanup(func() {
		_ = db.Drop(context.Background())
		_ = client.Disconnect(context.Background())
	})
	return db
}

func insert(t *testing.T, db *mongo.Database, col string, doc bson.M) primitive.ObjectID {
	t.Helper()
	id := primitive.NewObjectID()
	doc["_id"] = id
	if _, err := db.Collection(col).InsertOne(context.Background(), doc); err != nil {
		t.Fatal(err)
	}
	return id
}

func count(t *testing.T, db *mongo.Database, col string, filter bson.M) int64 {
	t.Helper()
	n, err := db.Collection(col).CountDocuments(context.Background(), filter)
	if err != nil {
		t.Fatal(err)
	}
	return n
}

// Alice supprime son compte ; Bob, membre de sa tribu, doit garder tout ce qui
// est à lui.
func TestDeleteErasesEverythingOfTheAccountOnly(t *testing.T) {
	db := setupTestDB(t)
	ctx := context.Background()
	aliceID := insert(t, db, "users", bson.M{"email": "alice@example.com", "display_name": "Alice", "password": "x"})
	bobID := insert(t, db, "users", bson.M{"email": "bob@example.com", "display_name": "Bob", "password": "x"})
	alice, bob := aliceID.Hex(), bobID.Hex()

	tribes := tribe.NewService(db.Collection("tribes"), db.Collection("users"))
	if _, err := tribes.AddMember(ctx, alice, "bob@example.com"); err != nil {
		t.Fatal(err)
	}

	aliceFolder := insert(t, db, "folders", bson.M{"owner_id": alice, "name": "Vacances"}).Hex()
	bobFolder := insert(t, db, "folders", bson.M{"owner_id": bob, "name": "Noël", "liked_by": bson.A{alice}, "like_count": 1,
		"collaborators": bson.A{bson.M{"user_id": alice, "email": "alice@example.com", "display_name": "Alice", "role": "editor"}}})
	aliceIdea := insert(t, db, "links", bson.M{"owner_id": alice, "title": "Crêpes", "folder_id": aliceFolder}).Hex()
	bobInAliceFolder := insert(t, db, "links", bson.M{"owner_id": bob, "title": "Plage", "folder_id": aliceFolder})
	bobIdea := insert(t, db, "links", bson.M{"owner_id": bob, "title": "Sapin", "like_count": 1})
	insert(t, db, "link_likes", bson.M{"link_id": bobIdea.Hex(), "user_id": alice})
	insert(t, db, "link_likes", bson.M{"link_id": aliceIdea, "user_id": bob})
	insert(t, db, "comments", bson.M{"link_id": aliceIdea, "user_id": bob, "text": "Miam"})
	insert(t, db, "comments", bson.M{"link_id": bobIdea.Hex(), "user_id": alice, "text": "Joli"})
	insert(t, db, "follows", bson.M{"follower_id": alice, "following_id": bob})
	insert(t, db, "follows", bson.M{"follower_id": bob, "following_id": alice})
	insert(t, db, "children", bson.M{"ownerId": aliceID, "name": "Léa"})
	insert(t, db, "children", bson.M{"ownerId": bobID, "name": "Tom"})

	svc := NewService(db, tribes)
	export, err := svc.Export(ctx, alice)
	if err != nil {
		t.Fatalf("export: %v", err)
	}
	if acc := export["account"].(bson.M); acc["password"] != nil || acc["email"] != "alice@example.com" {
		t.Errorf("export du compte : %v (jamais le mot de passe)", acc)
	}
	if len(export["ideas"].([]bson.M)) != 1 || len(export["children"].([]bson.M)) != 1 || len(export["folderLikes"].([]bson.M)) != 1 {
		t.Errorf("export incomplet : %v", export)
	}

	if err := svc.Delete(ctx, alice); err != nil {
		t.Fatalf("delete: %v", err)
	}
	checks := []struct {
		what string
		col  string
		f    bson.M
		want int64
	}{
		{"compte", "users", bson.M{"_id": aliceID}, 0},
		{"idées d'Alice", "links", bson.M{"owner_id": alice}, 0},
		{"dossiers d'Alice", "folders", bson.M{"owner_id": alice}, 0},
		{"commentaires sur ses idées", "comments", bson.M{"link_id": aliceIdea}, 0},
		{"ses commentaires", "comments", bson.M{"user_id": alice}, 0},
		{"« j'aime » sur ses idées", "link_likes", bson.M{"link_id": aliceIdea}, 0},
		{"ses « j'aime »", "link_likes", bson.M{"user_id": alice}, 0},
		{"abonnements", "follows", bson.M{}, 0},
		{"enfants d'Alice", "children", bson.M{"ownerId": aliceID}, 0},
		{"place dans les dossiers des autres", "folders", bson.M{"collaborators.user_id": alice}, 0},
		{"« j'aime » de dossiers", "folders", bson.M{"liked_by": alice}, 0},
		{"tribu", "tribes", bson.M{"members.user_id": alice}, 0},
		// Ce qui est à Bob reste à Bob
		{"compte de Bob", "users", bson.M{"_id": bobID}, 1},
		{"idée de Bob, sortie du dossier supprimé", "links", bson.M{"_id": bobInAliceFolder, "folder_id": ""}, 1},
		{"« j'aime » retiré du compteur", "links", bson.M{"_id": bobIdea, "like_count": 0}, 1},
		{"dossier de Bob", "folders", bson.M{"_id": bobFolder, "like_count": 0}, 1},
		{"enfant de Bob", "children", bson.M{"ownerId": bobID}, 1},
		{"Bob reprend la tribu", "tribes", bson.M{"members": bson.M{"$size": 1}, "members.user_id": bob, "members.role": "owner"}, 1},
	}
	for _, c := range checks {
		if got := count(t, db, c.col, c.f); got != c.want {
			t.Errorf("%s : %d document(s), want %d", c.what, got, c.want)
		}
	}
	// Une nouvelle demande (compte déjà supprimé) ne casse rien
	if err := svc.Delete(ctx, alice); err != ErrNotFound {
		t.Errorf("seconde suppression : %v, want ErrNotFound", err)
	}
}
