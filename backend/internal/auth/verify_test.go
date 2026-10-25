package auth

import (
	"context"
	"errors"
	"strings"
	"testing"
	"time"

	"go.mongodb.org/mongo-driver/v2/bson"
)

type fakeMailer struct {
	welcome []sentLink // url = lien de confirmation de l'email de bienvenue
	verify  []sentLink // emails « Confirmez votre adresse »
}

func (f *fakeMailer) Welcome(email, _, verifyURL string, _ time.Duration) {
	f.welcome = append(f.welcome, sentLink{email: email, url: verifyURL})
}

func (f *fakeMailer) EmailVerification(email, _, verifyURL string, _ time.Duration) {
	f.verify = append(f.verify, sentLink{email: email, url: verifyURL})
}

func setupVerify(t *testing.T) (*Service, *fakeMailer, func(string) error) {
	t.Helper()
	svc, db, _ := setupReset(t)
	m := &fakeMailer{}
	svc.SetNotifier(m)
	svc.SetEmailVerification(db.Collection("email_verifications"), "https://tribbae.example/verifier-email", m)
	// Recule la date des demandes : le délai entre deux envois est écoulé
	age := func(field string) error {
		_, err := db.Collection("email_verifications").UpdateMany(context.Background(), bson.M{},
			bson.M{"$set": bson.M{field: time.Now().Add(-48 * time.Hour)}})
		return err
	}
	return svc, m, age
}

func TestEmailVerificationFlow(t *testing.T) {
	svc, m, age := setupVerify(t)
	ctx := context.Background()
	alice, _, err := svc.Register(ctx, "alice@example.com", "mot-de-passe", "Alice")
	if err != nil {
		t.Fatal(err)
	}
	// Le lien part dans l'email de bienvenue
	if len(m.welcome) != 1 || !strings.HasPrefix(m.welcome[0].url, "https://tribbae.example/verifier-email#") {
		t.Fatalf("email de bienvenue : %+v", m.welcome)
	}
	if err := svc.RequireVerifiedEmail(ctx, alice.ID.Hex()); !errors.Is(err, ErrEmailNotVerified) {
		t.Fatalf("adresse non confirmée acceptée : %v", err)
	}

	// Renvoyer : un nouvel email, mais pas deux dans la minute
	if verified, err := svc.ResendEmailVerification(ctx, alice.ID.Hex()); err != nil || verified {
		t.Fatalf("renvoi : verified=%v err=%v", verified, err)
	}
	if len(m.verify) != 0 {
		t.Fatalf("renvoi dans la minute de l'inscription : %+v", m.verify)
	}
	if err := age("created_at"); err != nil {
		t.Fatal(err)
	}
	if _, err := svc.ResendEmailVerification(ctx, alice.ID.Hex()); err != nil || len(m.verify) != 1 {
		t.Fatalf("renvoi : err=%v emails=%+v", err, m.verify)
	}
	// Le nouveau lien remplace celui de l'email de bienvenue
	if err := svc.VerifyEmail(ctx, tokenOf(t, m.welcome[0].url)); !errors.Is(err, ErrInvalidVerifyToken) {
		t.Errorf("ancien lien accepté : %v", err)
	}
	token := tokenOf(t, m.verify[0].url)
	if err := svc.VerifyEmail(ctx, token); err != nil {
		t.Fatalf("confirmation : %v", err)
	}
	if err := svc.RequireVerifiedEmail(ctx, alice.ID.Hex()); err != nil {
		t.Errorf("adresse confirmée refusée : %v", err)
	}
	// Le lien ne sert qu'une fois ; une fois confirmée, rien n'est renvoyé
	if err := svc.VerifyEmail(ctx, token); !errors.Is(err, ErrInvalidVerifyToken) {
		t.Errorf("lien réutilisé : %v", err)
	}
	if verified, err := svc.ResendEmailVerification(ctx, alice.ID.Hex()); err != nil || !verified || len(m.verify) != 1 {
		t.Errorf("renvoi après confirmation : verified=%v err=%v emails=%d", verified, err, len(m.verify))
	}
	user, _, err := svc.Login(ctx, "alice@example.com", "mot-de-passe")
	if err != nil || user.EmailVerifiedAt == nil {
		t.Errorf("connexion : user=%+v err=%v", user, err)
	}
}

func TestEmailVerificationLinkExpires(t *testing.T) {
	svc, m, age := setupVerify(t)
	ctx := context.Background()
	bob, _, err := svc.Register(ctx, "bob@example.com", "mot-de-passe", "Bob")
	if err != nil {
		t.Fatal(err)
	}
	if err := age("expires_at"); err != nil {
		t.Fatal(err)
	}
	if err := svc.VerifyEmail(ctx, tokenOf(t, m.welcome[0].url)); !errors.Is(err, ErrInvalidVerifyToken) {
		t.Errorf("lien expiré accepté : %v", err)
	}
	if err := svc.VerifyEmail(ctx, "jeton-invente"); !errors.Is(err, ErrInvalidVerifyToken) {
		t.Errorf("jeton inventé accepté : %v", err)
	}
	if err := svc.RequireVerifiedEmail(ctx, bob.ID.Hex()); !errors.Is(err, ErrEmailNotVerified) {
		t.Errorf("adresse confirmée par un lien expiré : %v", err)
	}
}

// Le lien confirme l'adresse à laquelle il a été envoyé, pas celle que le
// compte aurait prise depuis.
func TestEmailVerificationFollowsTheAddress(t *testing.T) {
	svc, m, _ := setupVerify(t)
	ctx := context.Background()
	carol, _, err := svc.Register(ctx, "carol@example.com", "mot-de-passe", "Carol")
	if err != nil {
		t.Fatal(err)
	}
	if _, err := svc.col.UpdateOne(ctx, bson.M{"_id": carol.ID}, bson.M{"$set": bson.M{"email": "autre@example.com"}}); err != nil {
		t.Fatal(err)
	}
	if err := svc.VerifyEmail(ctx, tokenOf(t, m.welcome[0].url)); !errors.Is(err, ErrInvalidVerifyToken) {
		t.Errorf("lien envoyé à l'ancienne adresse accepté : %v", err)
	}
}

func TestMarkEmailVerified(t *testing.T) {
	svc, m, _ := setupVerify(t)
	ctx := context.Background()
	dan, _, err := svc.Register(ctx, "dan@example.com", "mot-de-passe", "Dan")
	if err != nil {
		t.Fatal(err)
	}
	if err := svc.MarkEmailVerified(ctx, dan.ID.Hex()); err != nil {
		t.Fatal(err)
	}
	if err := svc.RequireVerifiedEmail(ctx, dan.ID.Hex()); err != nil {
		t.Errorf("adresse non confirmée : %v", err)
	}
	// Le lien en attente ne sert plus
	if err := svc.VerifyEmail(ctx, tokenOf(t, m.welcome[0].url)); !errors.Is(err, ErrInvalidVerifyToken) {
		t.Errorf("lien en attente encore accepté : %v", err)
	}
}
