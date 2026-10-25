package account

import (
	"bytes"
	"context"
	"encoding/json"
	"image"
	"image/jpeg"
	"strings"
	"testing"
	"time"

	"go.mongodb.org/mongo-driver/v2/bson"
	"go.mongodb.org/mongo-driver/v2/mongo"
	"go.mongodb.org/mongo-driver/v2/mongo/options"

	"github.com/tribbae/backend/internal/mail"
	"github.com/tribbae/backend/internal/photo"
	"github.com/tribbae/backend/internal/tribe"
)

func setupTestDB(t *testing.T) *mongo.Database {
	t.Helper()
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	client, err := mongo.Connect(options.Client().ApplyURI("mongodb://localhost:27017").SetServerSelectionTimeout(3 * time.Second))
	if err != nil {
		t.Skipf("Skipping test: MongoDB unavailable: %v", err)
	}
	if err := client.Ping(ctx, nil); err != nil {
		t.Skipf("Skipping test: MongoDB unavailable: %v", err)
	}
	db := client.Database("tribbae_test_" + bson.NewObjectID().Hex())
	t.Cleanup(func() {
		_ = db.Drop(context.Background())
		_ = client.Disconnect(context.Background())
	})
	return db
}

func insert(t *testing.T, db *mongo.Database, col string, doc bson.M) bson.ObjectID {
	t.Helper()
	id := bson.NewObjectID()
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

	tribes := tribe.NewService(db.Collection("tribes"), db.Collection("users"), db.Collection("tribe_invitations"))
	inv := &invitations{}
	tribes.SetNotifier(inv, "https://tribbae.example/invitation")
	if _, err := tribes.Invite(ctx, alice, "bob@example.com"); err != nil {
		t.Fatal(err)
	}
	if _, err := tribes.Accept(ctx, bob, inv.token()); err != nil {
		t.Fatal(err)
	}
	// Invitations en attente envoyées par Alice et reçues par elle : effacées aussi
	if _, err := tribes.Invite(ctx, alice, "carol@example.com"); err != nil {
		t.Fatal(err)
	}
	insert(t, db, "tribe_invitations", bson.M{"email": "alice@example.com", "invited_by": "someone", "token_hash": "x"})

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
	var jpg bytes.Buffer
	if err := jpeg.Encode(&jpg, image.NewRGBA(image.Rect(0, 0, 8, 8)), nil); err != nil {
		t.Fatal(err)
	}
	photos := photo.NewService(db)
	for _, owner := range []string{alice, alice, bob} {
		if _, err := photos.Save(ctx, owner, jpg.Bytes()); err != nil {
			t.Fatal(err)
		}
	}

	svc := NewService(db, tribes)
	export, err := svc.Export(ctx, alice)
	if err != nil {
		t.Fatalf("export: %v", err)
	}
	if acc := export["account"].(bson.M); acc["password"] != nil || acc["email"] != "alice@example.com" {
		t.Errorf("export du compte : %v (jamais le mot de passe)", acc)
	}
	if len(export["ideas"].([]bson.M)) != 1 || len(export["children"].([]bson.M)) != 1 || len(export["folderLikes"].([]bson.M)) != 1 || len(export["photos"].([]bson.M)) != 2 {
		t.Errorf("export incomplet : %v", export)
	}

	// Sous-documents en objets JSON, pas en listes {Key, Value}
	members, _ := export["tribe"].([]bson.M)[0]["members"].(bson.A)
	if len(members) == 0 {
		t.Fatalf("export de la tribu sans membres : %v", export["tribe"])
	}
	if _, ok := members[0].(bson.M); !ok {
		t.Errorf("membre exporté en %T, want bson.M", members[0])
	}
	if raw, _ := json.Marshal(export["tribe"]); strings.Contains(string(raw), `"Key"`) {
		t.Errorf("export JSON en paires clé/valeur : %s", raw)
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
		{"invitations envoyées ou reçues", "tribe_invitations", bson.M{}, 0},
		{"photos d'Alice", "photos.files", bson.M{"metadata.owner_id": alice}, 0},
		// Ce qui est à Bob reste à Bob
		{"compte de Bob", "users", bson.M{"_id": bobID}, 1},
		{"idée de Bob, sortie du dossier supprimé", "links", bson.M{"_id": bobInAliceFolder, "folder_id": ""}, 1},
		{"« j'aime » d'Alice sur l'idée de Bob", "link_likes", bson.M{"link_id": bobIdea.Hex()}, 0},
		{"dossier de Bob", "folders", bson.M{"_id": bobFolder, "like_count": 0}, 1},
		{"enfant de Bob", "children", bson.M{"ownerId": bobID}, 1},
		{"photo de Bob", "photos.files", bson.M{"metadata.owner_id": bob}, 1},
		{"morceaux de la photo de Bob seulement", "photos.chunks", bson.M{}, 1},
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

type fakeNotifier struct{ emails, names []string }

func (f *fakeNotifier) AccountDeleted(email, name string) {
	f.emails = append(f.emails, email)
	f.names = append(f.names, name)
}

// La suppression est confirmée par email, une seule fois.
func TestDeleteSendsConfirmationEmail(t *testing.T) {
	db := setupTestDB(t)
	ctx := context.Background()
	aliceID := insert(t, db, "users", bson.M{"email": "alice@example.com", "display_name": "Alice", "password": "x"})
	svc := NewService(db, nil)
	n := &fakeNotifier{}
	svc.SetNotifier(n)

	if err := svc.Delete(ctx, aliceID.Hex()); err != nil {
		t.Fatal(err)
	}
	if err := svc.Delete(ctx, aliceID.Hex()); err != ErrNotFound {
		t.Fatalf("seconde suppression : %v, want ErrNotFound", err)
	}
	if len(n.emails) != 1 || n.emails[0] != "alice@example.com" || n.names[0] != "Alice" {
		t.Errorf("emails = %v %v, want une confirmation à alice@example.com", n.emails, n.names)
	}
}

// invitations retient le dernier lien d'invitation envoyé.
type invitations struct{ last string }

func (i *invitations) TribeInvitation(inv mail.TribeInvitation) { i.last = inv.URL }

func (i *invitations) token() string {
	_, token, _ := strings.Cut(i.last, "#")
	return token
}
