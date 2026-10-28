package folder

import (
	"context"
	"errors"
	"slices"
	"strconv"
	"strings"
	"testing"
	"time"

	"github.com/tribbae/backend/internal/auth"
	tmail "github.com/tribbae/backend/internal/mail"
	"go.mongodb.org/mongo-driver/v2/bson"
	"go.mongodb.org/mongo-driver/v2/mongo"
	"go.mongodb.org/mongo-driver/v2/mongo/options"
)

type fakeCircleMailer struct {
	sent   []tmail.CircleInvitation
	collab []tmail.CollaboratorAdded
}

func (f *fakeCircleMailer) CircleInvitation(inv tmail.CircleInvitation) { f.sent = append(f.sent, inv) }
func (f *fakeCircleMailer) CollaboratorAdded(c tmail.CollaboratorAdded) {
	f.collab = append(f.collab, c)
}

type circleEnv struct {
	svc      *Service
	db       *mongo.Database
	mailer   *fakeCircleMailer
	verified map[string]bool
}

func setupCircle(t *testing.T) *circleEnv {
	t.Helper()
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
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
	env := &circleEnv{
		svc:      NewService(db.Collection("folders"), db.Collection("links"), db.Collection("users"), "https://tribbae.example"),
		db:       db,
		mailer:   &fakeCircleMailer{},
		verified: map[string]bool{},
	}
	env.svc.SetEmailCheck(func(_ context.Context, userID string) error {
		if !env.verified[userID] {
			return auth.ErrEmailNotVerified
		}
		return nil
	})
	env.svc.SetInvitations(db.Collection("folder_invitations"), "https://tribbae.example/invitation-cercle", env.mailer,
		func(_ context.Context, userID string) error { env.verified[userID] = true; return nil })
	return env
}

func (e *circleEnv) user(t *testing.T, email, name string) string {
	t.Helper()
	id := bson.NewObjectID()
	if _, err := e.db.Collection("users").InsertOne(context.Background(), bson.M{"_id": id, "email": email, "display_name": name}); err != nil {
		t.Fatal(err)
	}
	e.verified[id.Hex()] = true
	return id.Hex()
}

func (e *circleEnv) folder(t *testing.T, owner, name string) string {
	t.Helper()
	f, err := e.svc.Create(context.Background(), owner, name, "", "", "private", "", nil, Style{})
	if err != nil {
		t.Fatal(err)
	}
	return f.ID.Hex()
}

// lastToken lit le jeton du dernier email envoyé (après le #).
func (e *circleEnv) lastToken(t *testing.T) string {
	t.Helper()
	if len(e.mailer.sent) == 0 {
		t.Fatal("aucun email envoyé")
	}
	_, token, ok := strings.Cut(e.mailer.sent[len(e.mailer.sent)-1].URL, "#")
	if !ok || token == "" {
		t.Fatal("lien sans jeton")
	}
	return token
}

// Recule le dernier envoi : le délai avant un nouvel email est écoulé.
func (e *circleEnv) age(t *testing.T, field string, by time.Duration) {
	t.Helper()
	if _, err := e.db.Collection("folder_invitations").UpdateMany(context.Background(), bson.M{}, bson.M{"$set": bson.M{field: time.Now().Add(-by)}}); err != nil {
		t.Fatal(err)
	}
}

// Ajouté directement comme collaborateur (anciennes applications), on est
// prévenu par email, une fois par dossier et au plus 5 fois par jour.
func TestCollaboratorAddedEmail(t *testing.T) {
	e := setupCircle(t)
	ctx := context.Background()
	alice := e.user(t, "alice@ex.fr", "Alice")
	e.user(t, "mamie@ex.fr", "Mamie")
	noel := e.folder(t, alice, "Noël")

	if _, err := e.svc.AddCollaborator(ctx, noel, alice, "Mamie@ex.fr", "editor"); err != nil {
		t.Fatal(err)
	}
	want := tmail.CollaboratorAdded{Email: "mamie@ex.fr", Name: "Mamie", OwnerName: "Alice", FolderID: noel, FolderName: "Noël", Editor: true}
	if len(e.mailer.collab) != 1 || e.mailer.collab[0] != want {
		t.Fatalf("email d'ajout : %+v", e.mailer.collab)
	}
	// Déjà collaboratrice : refusé, pas d'email
	if _, err := e.svc.AddCollaborator(ctx, noel, alice, "mamie@ex.fr", "viewer"); err == nil {
		t.Error("déjà collaboratrice : erreur attendue")
	}
	// Retirée puis rajoutée le même jour : pas de second email
	mamie := e.mailer.collab[0]
	if _, err := e.svc.RemoveCollaborator(ctx, noel, alice, e.collabID(t, noel)); err != nil {
		t.Fatal(err)
	}
	if _, err := e.svc.AddCollaborator(ctx, noel, alice, mamie.Email, "viewer"); err != nil {
		t.Fatal(err)
	}
	if len(e.mailer.collab) != 1 {
		t.Errorf("un seul email par dossier et par jour : %d", len(e.mailer.collab))
	}
	// D'autres dossiers : un email chacun, 5 au plus par jour
	for i := range 6 {
		f := e.folder(t, alice, "Dossier "+strconv.Itoa(i))
		if _, err := e.svc.AddCollaborator(ctx, f, alice, mamie.Email, "viewer"); err != nil {
			t.Fatal(err)
		}
	}
	if len(e.mailer.collab) != 5 {
		t.Errorf("au plus 5 emails par jour et par destinataire : %d", len(e.mailer.collab))
	}
}

// collabID : identifiant du premier collaborateur du dossier.
func (e *circleEnv) collabID(t *testing.T, folderID string) string {
	t.Helper()
	id, _ := bson.ObjectIDFromHex(folderID)
	var f Folder
	if err := e.db.Collection("folders").FindOne(context.Background(), bson.M{"_id": id}).Decode(&f); err != nil || len(f.Collaborators) == 0 {
		t.Fatalf("collaborateur introuvable : %v", err)
	}
	return f.Collaborators[0].UserID
}

func TestCircleInvitationFlow(t *testing.T) {
	e := setupCircle(t)
	ctx := context.Background()
	alice := e.user(t, "alice@ex.fr", "Alice")
	noel := e.folder(t, alice, "Noël")
	sorties := e.folder(t, alice, "Sorties")
	other := e.folder(t, e.user(t, "bob@ex.fr", "Bob"), "De Bob")

	if _, err := e.svc.Invite(ctx, alice, "pas-un-email", []string{noel}, "viewer"); !errors.Is(err, ErrInvalidInput) {
		t.Errorf("adresse invalide : %v", err)
	}
	if _, err := e.svc.Invite(ctx, alice, "mamie@ex.fr", nil, "viewer"); !errors.Is(err, ErrInvalidInput) {
		t.Errorf("aucun dossier : %v", err)
	}
	if _, err := e.svc.Invite(ctx, alice, "mamie@ex.fr", []string{other}, "viewer"); !errors.Is(err, ErrNotFound) {
		t.Errorf("dossier d'un autre : %v", err)
	}
	if _, err := e.svc.Invite(ctx, alice, "Alice@ex.fr", []string{noel}, "viewer"); !errors.Is(err, ErrInvalidInput) {
		t.Errorf("s'inviter soi-même : %v", err)
	}
	// Adresse sans compte : invitation et email comme pour une adresse connue
	inv, err := e.svc.Invite(ctx, alice, " Mamie@Ex.fr ", []string{noel, other}, "editor")
	if err != nil {
		t.Fatal(err)
	}
	if inv.Email != "mamie@ex.fr" || !slices.Equal(inv.FolderIDs, []string{noel}) || inv.Role != "editor" {
		t.Errorf("invitation = %+v (le dossier de Bob est ignoré)", inv)
	}
	sent := e.mailer.sent
	if len(sent) != 1 || sent[0].Email != "mamie@ex.fr" || sent[0].InviterName != "Alice" || !slices.Equal(sent[0].Folders, []string{"Noël"}) ||
		!sent[0].Editor || !strings.HasPrefix(sent[0].URL, "https://tribbae.example/invitation-cercle#") {
		t.Fatalf("email = %+v", sent)
	}
	token := e.lastToken(t)
	if n, _ := e.db.Collection("folder_invitations").CountDocuments(ctx, bson.M{"token_hash": token}); n != 0 {
		t.Error("le jeton est stocké en clair")
	}

	// Réinviter dans les 10 minutes : les dossiers s'ajoutent, pas de nouvel email
	if _, err := e.svc.Invite(ctx, alice, "mamie@ex.fr", []string{sorties}, "viewer"); err != nil || len(e.mailer.sent) != 1 {
		t.Fatalf("réinvitation immédiate : %v, %d emails", err, len(e.mailer.sent))
	}
	invs, names, err := e.svc.Invitations(ctx, alice)
	if err != nil || len(invs) != 1 || !slices.Equal(invs[0].FolderIDs, []string{noel, sorties}) || invs[0].Role != "viewer" || names[sorties] != "Sorties" {
		t.Fatalf("invitations en attente : %+v %v %v", invs, names, err)
	}
	info, err := e.svc.Lookup(ctx, token)
	if err != nil || info.Email != "mamie@ex.fr" || info.InvitedByName != "Alice" || !slices.Equal(info.FolderNames, []string{"Noël", "Sorties"}) {
		t.Fatalf("lookup : %+v %v", info, err)
	}
	// Plus tard : « Relancer » renvoie un nouveau lien, l'ancien ne marche plus
	e.age(t, "created_at", time.Hour)
	if _, err := e.svc.Invite(ctx, alice, "mamie@ex.fr", []string{noel}, "viewer"); err != nil || len(e.mailer.sent) != 2 {
		t.Fatalf("relance : %v, %d emails", err, len(e.mailer.sent))
	}
	if !slices.Equal(e.mailer.sent[1].Folders, []string{"Noël", "Sorties"}) {
		t.Errorf("la relance doit citer tous les dossiers : %v", e.mailer.sent[1].Folders)
	}
	if _, err := e.svc.Lookup(ctx, token); !errors.Is(err, ErrInvalidInvitation) {
		t.Errorf("ancien lien encore valable : %v", err)
	}
	token = e.lastToken(t)

	// Mamie crée son compte : un autre compte ne peut pas accepter à sa place
	eve := e.user(t, "eve@ex.fr", "Eve")
	if _, err := e.svc.Accept(ctx, eve, token); !errors.Is(err, ErrWrongAccount) {
		t.Errorf("Eve accepte l'invitation de Mamie : %v", err)
	}
	mamie := e.user(t, "mamie@ex.fr", "Mamie")
	e.verified[mamie] = false
	folders, err := e.svc.Accept(ctx, mamie, token)
	if err != nil || len(folders) != 2 {
		t.Fatalf("accepter : %d dossiers, %v", len(folders), err)
	}
	for _, f := range folders {
		if len(f.Collaborators) != 1 || f.Collaborators[0].UserID != mamie || f.Collaborators[0].Role != "viewer" || f.Visibility != "shared" {
			t.Errorf("dossier %q : %+v, visibilité %q", f.Name, f.Collaborators, f.Visibility)
		}
	}
	if !e.verified[mamie] {
		t.Error("accepter doit confirmer l'adresse de Mamie")
	}
	if _, err := e.svc.Accept(ctx, mamie, token); !errors.Is(err, ErrInvalidInvitation) {
		t.Errorf("lien réutilisé : %v", err)
	}
	if invs, _, _ := e.svc.Invitations(ctx, alice); len(invs) != 0 {
		t.Errorf("invitation encore en attente : %+v", invs)
	}

	// Réinviter quelqu'un déjà dans le dossier change son rôle, sans doublon
	if _, err := e.svc.Invite(ctx, alice, "mamie@ex.fr", []string{noel}, "editor"); err != nil {
		t.Fatal(err)
	}
	folders, err = e.svc.Accept(ctx, mamie, e.lastToken(t))
	if err != nil || len(folders) != 1 || len(folders[0].Collaborators) != 1 || folders[0].Collaborators[0].Role != "editor" {
		t.Fatalf("nouveau rôle : %+v %v", folders, err)
	}
}

func TestCircleInvitationRevokeAndDeletedFolders(t *testing.T) {
	e := setupCircle(t)
	ctx := context.Background()
	alice := e.user(t, "alice@ex.fr", "Alice")
	bob := e.user(t, "bob@ex.fr", "Bob")
	noel := e.folder(t, alice, "Noël")
	sorties := e.folder(t, alice, "Sorties")

	inv, err := e.svc.Invite(ctx, alice, "mamie@ex.fr", []string{noel}, "viewer")
	if err != nil {
		t.Fatal(err)
	}
	token := e.lastToken(t)
	// Seul celui qui a invité peut annuler
	if err := e.svc.Revoke(ctx, bob, inv.ID.Hex()); !errors.Is(err, ErrInvalidInvitation) {
		t.Errorf("Bob annule l'invitation d'Alice : %v", err)
	}
	if err := e.svc.Revoke(ctx, alice, inv.ID.Hex()); err != nil {
		t.Fatal(err)
	}
	if _, err := e.svc.Lookup(ctx, token); !errors.Is(err, ErrInvalidInvitation) {
		t.Errorf("lien d'une invitation annulée : %v", err)
	}
	if err := e.svc.Revoke(ctx, alice, inv.ID.Hex()); !errors.Is(err, ErrInvalidInvitation) {
		t.Errorf("annuler deux fois : %v", err)
	}

	// Dossier supprimé : retiré de l'invitation ; plus aucun dossier : invitation effacée
	if _, err := e.svc.Invite(ctx, alice, "papi@ex.fr", []string{noel, sorties}, "viewer"); err != nil {
		t.Fatal(err)
	}
	token = e.lastToken(t)
	if err := e.svc.Delete(ctx, noel, alice); err != nil {
		t.Fatal(err)
	}
	if info, err := e.svc.Lookup(ctx, token); err != nil || !slices.Equal(info.FolderNames, []string{"Sorties"}) {
		t.Errorf("après suppression d'un dossier : %+v %v", info, err)
	}
	if err := e.svc.Delete(ctx, sorties, alice); err != nil {
		t.Fatal(err)
	}
	if n, _ := e.db.Collection("folder_invitations").CountDocuments(ctx, bson.M{}); n != 0 {
		t.Errorf("%d invitation(s) sans dossier restante(s)", n)
	}
}

func TestCircleInvitationLimits(t *testing.T) {
	e := setupCircle(t)
	ctx := context.Background()
	alice := e.user(t, "alice@ex.fr", "Alice")
	noel := e.folder(t, alice, "Noël")

	// Inviter exige une adresse confirmée
	e.verified[alice] = false
	if _, err := e.svc.Invite(ctx, alice, "mamie@ex.fr", []string{noel}, "viewer"); !errors.Is(err, auth.ErrEmailNotVerified) {
		t.Fatalf("invitation sans adresse confirmée : %v", err)
	}
	e.verified[alice] = true

	for i := range maxPendingInvitations {
		if _, err := e.svc.Invite(ctx, alice, "proche"+strconv.Itoa(i)+"@ex.fr", []string{noel}, "viewer"); err != nil {
			t.Fatal(err)
		}
	}
	if _, err := e.svc.Invite(ctx, alice, "encore@ex.fr", []string{noel}, "viewer"); !errors.Is(err, ErrTooManyInvitations) {
		t.Errorf("au-delà de %d invitations : %v", maxPendingInvitations, err)
	}
	// Réinviter une adresse déjà en attente reste possible
	if _, err := e.svc.Invite(ctx, alice, "proche0@ex.fr", []string{noel}, "editor"); err != nil {
		t.Errorf("réinviter une adresse en attente : %v", err)
	}
	// Une invitation expirée laisse la place à une nouvelle, sous un autre lien
	e.age(t, "expires_at", time.Second)
	if _, err := e.svc.Invite(ctx, alice, "proche0@ex.fr", []string{noel}, "viewer"); err != nil {
		t.Errorf("réinviter après expiration : %v", err)
	}
}
