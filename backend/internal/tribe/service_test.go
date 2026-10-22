package tribe

import (
	"context"
	"errors"
	"slices"
	"strconv"
	"strings"
	"testing"
	"time"

	"go.mongodb.org/mongo-driver/bson"
	"go.mongodb.org/mongo-driver/bson/primitive"
	"go.mongodb.org/mongo-driver/mongo"
	"go.mongodb.org/mongo-driver/mongo/options"

	"github.com/tribbae/backend/internal/mail"
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
	svc := NewService(db.Collection("tribes"), db.Collection("users"), db.Collection("tribe_invitations"))
	svc.SetNotifier(&fakeNotifier{}, "https://tribbae.example/invitation")
	return svc, db, func() {
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

	tr, err := join(t, svc, alice, bob, " BOB@ex.fr ")
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

	if _, err := svc.Invite(ctx, alice, "bob@ex.fr"); !errors.Is(err, ErrAlreadyMember) {
		t.Errorf("double ajout : %v", err)
	}
	// Une adresse sans compte est invitée comme les autres : rien ne dit qu'elle n'a pas de compte
	if _, err := svc.Invite(ctx, alice, "inconnu@ex.fr"); err != nil {
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
	if _, err := join(t, svc, alice, bob, "bob@ex.fr"); err != nil {
		t.Fatal(err)
	}
	if _, err := join(t, svc, carol, bob, "bob@ex.fr"); !errors.Is(err, ErrInOtherTribe) {
		t.Errorf("Bob est déjà dans la tribu d'Alice : %v", err)
	}
	// Une tribu où l'on est seul (créée en la renommant) est simplement remplacée
	if _, err := svc.Rename(ctx, carol, "Martin"); err != nil {
		t.Fatal(err)
	}
	if _, err := join(t, svc, alice, carol, "carol@ex.fr"); err != nil {
		t.Errorf("Carol, seule dans sa tribu, peut rejoindre Alice : %v", err)
	}
}

func TestLeaveAndOwnerTransfer(t *testing.T) {
	svc, db, cleanup := setup(t)
	defer cleanup()
	ctx := context.Background()
	alice := addUser(t, db, "alice@ex.fr", "Alice")
	bob := addUser(t, db, "bob@ex.fr", "Bob")
	carol := addUser(t, db, "carol@ex.fr", "Carol")
	_, _ = join(t, svc, alice, bob, "bob@ex.fr")
	time.Sleep(5 * time.Millisecond)
	_, _ = join(t, svc, alice, carol, "carol@ex.fr")

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

type fakeNotifier struct{ sent []mail.TribeInvitation }

func (f *fakeNotifier) TribeInvitation(inv mail.TribeInvitation) { f.sent = append(f.sent, inv) }

func notifier(svc *Service) *fakeNotifier { return svc.notify.(*fakeNotifier) }

// lastToken : jeton du dernier lien d'invitation envoyé (après le #).
func lastToken(t *testing.T, svc *Service) string {
	t.Helper()
	sent := notifier(svc).sent
	if len(sent) == 0 {
		t.Fatal("aucune invitation envoyée")
	}
	_, token, _ := strings.Cut(sent[len(sent)-1].URL, "#")
	return token
}

// join : from invite l'adresse email, puis le compte to accepte le lien.
func join(t *testing.T, svc *Service, from, to, email string) (*Tribe, error) {
	t.Helper()
	ctx := context.Background()
	before := len(notifier(svc).sent)
	if _, err := svc.Invite(ctx, from, email); err != nil {
		return nil, err
	}
	if len(notifier(svc).sent) == before {
		t.Fatal("invitation sans email")
	}
	return svc.Accept(ctx, to, lastToken(t, svc))
}

func TestInvitationEmailAndLookup(t *testing.T) {
	svc, db, cleanup := setup(t)
	defer cleanup()
	ctx := context.Background()
	alice := addUser(t, db, "alice@ex.fr", "Alice")
	if _, err := svc.Rename(ctx, alice, "Les Martin"); err != nil {
		t.Fatal(err)
	}
	if _, err := svc.Invite(ctx, alice, "pas-un-email"); !errors.Is(err, ErrInvalidInput) {
		t.Errorf("adresse invalide : %v", err)
	}
	if _, err := svc.Invite(ctx, alice, "alice@ex.fr"); !errors.Is(err, ErrAlreadyMember) {
		t.Errorf("s'inviter soi-même : %v", err)
	}
	tr, err := svc.Invite(ctx, alice, " Bob@Ex.fr ")
	if err != nil {
		t.Fatal(err)
	}
	sent := notifier(svc).sent
	if len(sent) != 1 || sent[0].Email != "bob@ex.fr" || sent[0].InviterName != "Alice" || sent[0].TribeName != "Les Martin" ||
		!slices.Equal(sent[0].Members, []string{"Alice"}) || !strings.HasPrefix(sent[0].URL, "https://tribbae.example/invitation#") {
		t.Fatalf("email = %+v", sent)
	}
	token := lastToken(t, svc)
	if n, _ := db.Collection("tribe_invitations").CountDocuments(ctx, bson.M{"token_hash": token}); n != 0 {
		t.Error("le jeton est stocké en clair")
	}
	invs, err := svc.Invitations(ctx, tr.ID)
	if err != nil || len(invs) != 1 || invs[0].Email != "bob@ex.fr" {
		t.Fatalf("invitations en attente : %+v, %v", invs, err)
	}
	info, err := svc.Lookup(ctx, token)
	if err != nil || info.Email != "bob@ex.fr" || info.TribeName != "Les Martin" || info.InvitedByName != "Alice" {
		t.Fatalf("lookup : %+v, %v", info, err)
	}
	if _, err := svc.Lookup(ctx, "jeton-invente"); !errors.Is(err, ErrInvalidInvitation) {
		t.Errorf("jeton inventé : %v", err)
	}
	// Renvoyer dans les 10 minutes : pas de nouvel email, le premier lien reste valable
	if _, err := svc.Invite(ctx, alice, "bob@ex.fr"); err != nil || len(notifier(svc).sent) != 1 {
		t.Errorf("renvoi immédiat : %v, %d emails", err, len(notifier(svc).sent))
	}
	if _, err := svc.Lookup(ctx, token); err != nil {
		t.Errorf("le premier lien doit rester valable : %v", err)
	}
}

func TestAcceptRequiresTheInvitedAccount(t *testing.T) {
	svc, db, cleanup := setup(t)
	defer cleanup()
	ctx := context.Background()
	alice := addUser(t, db, "alice@ex.fr", "Alice")
	bob := addUser(t, db, "bob@ex.fr", "Bob")
	eve := addUser(t, db, "eve@ex.fr", "Eve")
	if _, err := svc.Invite(ctx, alice, "bob@ex.fr"); err != nil {
		t.Fatal(err)
	}
	token := lastToken(t, svc)
	// Un lien transféré ne fait pas entrer quelqu'un d'autre
	if _, err := svc.Accept(ctx, eve, token); !errors.Is(err, ErrWrongAccount) {
		t.Errorf("Eve accepte l'invitation de Bob : %v", err)
	}
	tr, err := svc.Accept(ctx, bob, token)
	if err != nil || tr.Member(bob) == nil || tr.Member(bob).Sharing || tr.Member(bob).AddedBy != alice {
		t.Fatalf("Bob accepte : %+v, %v", tr, err)
	}
	// L'invitation a servi
	if _, err := svc.Accept(ctx, bob, token); !errors.Is(err, ErrInvalidInvitation) {
		t.Errorf("lien réutilisé : %v", err)
	}
	if invs, _ := svc.Invitations(ctx, tr.ID); len(invs) != 0 {
		t.Errorf("invitation encore en attente : %+v", invs)
	}
}

func TestRevokeExpireAndFull(t *testing.T) {
	svc, db, cleanup := setup(t)
	defer cleanup()
	ctx := context.Background()
	alice := addUser(t, db, "alice@ex.fr", "Alice")
	bob := addUser(t, db, "bob@ex.fr", "Bob")
	tr, err := svc.Invite(ctx, alice, "bob@ex.fr")
	if err != nil {
		t.Fatal(err)
	}
	token := lastToken(t, svc)
	invs, _ := svc.Invitations(ctx, tr.ID)
	if _, err := svc.Revoke(ctx, bob, invs[0].ID.Hex()); !errors.Is(err, ErrInvalidInvitation) {
		t.Errorf("Bob, hors de la tribu, annule l'invitation : %v", err)
	}
	if _, err := svc.Revoke(ctx, alice, invs[0].ID.Hex()); err != nil {
		t.Fatal(err)
	}
	if _, err := svc.Accept(ctx, bob, token); !errors.Is(err, ErrInvalidInvitation) {
		t.Errorf("invitation annulée acceptée : %v", err)
	}

	// Expirée
	if _, err := svc.Invite(ctx, alice, "bob@ex.fr"); err != nil {
		t.Fatal(err)
	}
	if _, err := db.Collection("tribe_invitations").UpdateMany(ctx, bson.M{}, bson.M{"$set": bson.M{"expires_at": time.Now().Add(-time.Second)}}); err != nil {
		t.Fatal(err)
	}
	if _, err := svc.Accept(ctx, bob, lastToken(t, svc)); !errors.Is(err, ErrInvalidInvitation) {
		t.Errorf("invitation expirée acceptée : %v", err)
	}
	_, _ = db.Collection("tribe_invitations").DeleteMany(ctx, bson.M{})

	// Membres + invitations en attente : 12 au plus
	for i := 1; i < maxMembers; i++ {
		if _, err := svc.Invite(ctx, alice, "invite"+strconv.Itoa(i)+"@ex.fr"); err != nil {
			t.Fatalf("invitation %d : %v", i, err)
		}
	}
	if _, err := svc.Invite(ctx, alice, "une-de-trop@ex.fr"); !errors.Is(err, ErrTribeFull) {
		t.Errorf("tribu complète : %v", err)
	}
	// La tribu disparaît avec son dernier membre : ses invitations aussi
	if _, err := svc.RemoveMember(ctx, alice, alice); err != nil {
		t.Fatal(err)
	}
	if n, _ := db.Collection("tribe_invitations").CountDocuments(ctx, bson.M{}); n != 0 {
		t.Errorf("%d invitation(s) d'une tribu disparue", n)
	}
}
