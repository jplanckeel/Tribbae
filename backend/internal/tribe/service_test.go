package tribe

import (
	"context"
	"errors"
	"slices"
	"testing"
	"time"

	"go.mongodb.org/mongo-driver/bson"
	"go.mongodb.org/mongo-driver/bson/primitive"
	"go.mongodb.org/mongo-driver/mongo"
	"go.mongodb.org/mongo-driver/mongo/options"
)

func setup(t *testing.T) (*Service, *mongo.Database, func()) {
	t.Helper()
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	client, err := mongo.Connect(ctx, options.Client().ApplyURI("mongodb://localhost:27017").SetServerSelectionTimeout(3*time.Second))
	if err != nil {
		t.Skipf("MongoDB indisponible : %v", err)
	}
	if err := client.Ping(ctx, nil); err != nil {
		t.Skipf("MongoDB indisponible : %v", err)
	}
	db := client.Database("tribbae_test_" + primitive.NewObjectID().Hex())
	_, _ = db.Collection("tribes").Indexes().CreateOne(ctx, mongo.IndexModel{
		Keys: bson.D{{Key: "members.user_id", Value: 1}}, Options: options.Index().SetUnique(true),
	})
	return NewService(db.Collection("tribes"), db.Collection("users")), db, func() {
		_ = db.Drop(context.Background())
		_ = client.Disconnect(context.Background())
	}
}

func addUser(t *testing.T, db *mongo.Database, email, name string) string {
	t.Helper()
	id := primitive.NewObjectID()
	if _, err := db.Collection("users").InsertOne(context.Background(), bson.M{"_id": id, "email": email, "display_name": name}); err != nil {
		t.Fatal(err)
	}
	return id.Hex()
}

func TestSoloTribeIsVirtualAndNamedAfterAccount(t *testing.T) {
	svc, db, cleanup := setup(t)
	defer cleanup()
	ctx := context.Background()
	alice := addUser(t, db, "alice@ex.fr", "Alice")

	tr, err := svc.Get(ctx, alice)
	if err != nil || tr.ID != primitive.NilObjectID || tr.Name != "Alice" || len(tr.Members) != 1 {
		t.Fatalf("tribu virtuelle attendue : %+v, %v", tr, err)
	}
	if got := svc.Peers(ctx, alice); !slices.Equal(got, []string{alice}) {
		t.Errorf("peers = %v", got)
	}
	tr, err = svc.Rename(ctx, alice, "  Dupont  ")
	if err != nil || tr.Name != "Dupont" {
		t.Fatalf("rename : %+v, %v", tr, err)
	}
	if _, err := svc.Rename(ctx, alice, ""); !errors.Is(err, ErrInvalidInput) {
		t.Errorf("nom vide accepté : %v", err)
	}
}

func TestInviteeSeesTribeButSharesOnlyAfterConfirming(t *testing.T) {
	svc, db, cleanup := setup(t)
	defer cleanup()
	ctx := context.Background()
	alice := addUser(t, db, "alice@ex.fr", "Alice")
	bob := addUser(t, db, "bob@ex.fr", "Bob")

	tr, err := svc.AddMember(ctx, alice, " BOB@ex.fr ")
	if err != nil || len(tr.Members) != 2 || tr.Member(bob) == nil || tr.Member(bob).Sharing {
		t.Fatalf("ajout : %+v, %v", tr, err)
	}
	// Bob voit le contenu d'Alice tout de suite ; Alice ne voit pas encore celui de Bob
	if got := svc.Peers(ctx, bob); !slices.Contains(got, alice) {
		t.Errorf("Bob doit voir la tribu d'Alice : %v", got)
	}
	if got := svc.Peers(ctx, alice); slices.Contains(got, bob) {
		t.Errorf("le contenu de Bob ne doit pas être partagé avant sa confirmation : %v", got)
	}
	if _, err := svc.SetSharing(ctx, bob, true); err != nil {
		t.Fatal(err)
	}
	if got := svc.Peers(ctx, alice); !slices.Contains(got, bob) {
		t.Errorf("après confirmation, Alice voit le contenu de Bob : %v", got)
	}

	if _, err := svc.AddMember(ctx, alice, "bob@ex.fr"); !errors.Is(err, ErrAlreadyMember) {
		t.Errorf("double ajout : %v", err)
	}
	if _, err := svc.AddMember(ctx, alice, "inconnu@ex.fr"); !errors.Is(err, ErrUserNotFound) {
		t.Errorf("email inconnu : %v", err)
	}
}

func TestCannotPullSomeoneOutOfAnotherSharedTribe(t *testing.T) {
	svc, db, cleanup := setup(t)
	defer cleanup()
	ctx := context.Background()
	alice := addUser(t, db, "alice@ex.fr", "Alice")
	bob := addUser(t, db, "bob@ex.fr", "Bob")
	carol := addUser(t, db, "carol@ex.fr", "Carol")
	if _, err := svc.AddMember(ctx, alice, "bob@ex.fr"); err != nil {
		t.Fatal(err)
	}
	if _, err := svc.AddMember(ctx, carol, "bob@ex.fr"); !errors.Is(err, ErrInOtherTribe) {
		t.Errorf("Bob est déjà dans la tribu d'Alice : %v", err)
	}
	// Une tribu où l'on est seul (créée en la renommant) est simplement remplacée
	if _, err := svc.Rename(ctx, carol, "Martin"); err != nil {
		t.Fatal(err)
	}
	if _, err := svc.AddMember(ctx, alice, "carol@ex.fr"); err != nil {
		t.Errorf("Carol, seule dans sa tribu, peut rejoindre Alice : %v", err)
	}
	_ = bob
}

func TestLeaveAndOwnerTransfer(t *testing.T) {
	svc, db, cleanup := setup(t)
	defer cleanup()
	ctx := context.Background()
	alice := addUser(t, db, "alice@ex.fr", "Alice")
	bob := addUser(t, db, "bob@ex.fr", "Bob")
	carol := addUser(t, db, "carol@ex.fr", "Carol")
	_, _ = svc.AddMember(ctx, alice, "bob@ex.fr")
	time.Sleep(5 * time.Millisecond)
	_, _ = svc.AddMember(ctx, alice, "carol@ex.fr")

	if _, err := svc.RemoveMember(ctx, bob, carol); !errors.Is(err, ErrForbidden) {
		t.Errorf("un membre ne retire pas un autre membre : %v", err)
	}
	// Alice (créatrice) s'en va : Bob, le plus ancien, reprend la tribu
	tr, err := svc.RemoveMember(ctx, alice, alice)
	if err != nil || len(tr.Members) != 1 || tr.Member(alice).Role != RoleOwner {
		t.Fatalf("après départ, Alice retrouve une tribu à elle seule : %+v, %v", tr, err)
	}
	if got := svc.Peers(ctx, bob); slices.Contains(got, alice) {
		t.Errorf("Alice partie, Bob ne voit plus son contenu : %v", got)
	}
	tr, _ = svc.Get(ctx, bob)
	if tr.Member(bob) == nil || tr.Member(bob).Role != RoleOwner || tr.Member(carol) == nil {
		t.Errorf("Bob doit reprendre la tribu : %+v", tr)
	}
	// Le nouveau créateur peut retirer Carol
	if _, err := svc.RemoveMember(ctx, bob, carol); err != nil {
		t.Errorf("retrait par le créateur : %v", err)
	}
}
