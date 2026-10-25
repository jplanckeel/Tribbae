package auth

import (
	"context"
	"errors"
	"strings"
	"testing"
	"time"

	"go.mongodb.org/mongo-driver/v2/bson"
	"go.mongodb.org/mongo-driver/v2/mongo"
	"go.mongodb.org/mongo-driver/v2/mongo/options"
)

type sentLink struct{ email, url, device string }

type fakeResetNotifier struct {
	links   []sentLink
	changed []string
}

func (f *fakeResetNotifier) PasswordResetLink(email, _, resetURL string, _ time.Duration, _ time.Time, device string) {
	f.links = append(f.links, sentLink{email, resetURL, device})
}

func (f *fakeResetNotifier) PasswordChanged(email, _ string, _ time.Time, _ string) {
	f.changed = append(f.changed, email)
}

func setupReset(t *testing.T) (*Service, *mongo.Database, *fakeResetNotifier) {
	t.Helper()
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	client, err := mongo.Connect(options.Client().ApplyURI("mongodb://localhost:27017").SetServerSelectionTimeout(3 * time.Second))
	if err != nil {
		t.Skipf("MongoDB indisponible : %v", err)
	}
	if err := client.Ping(ctx, nil); err != nil {
		t.Skipf("MongoDB indisponible : %v", err)
	}
	db := client.Database("tribbae_test_" + bson.NewObjectID().Hex())
	t.Cleanup(func() {
		_ = db.Drop(context.Background())
		_ = client.Disconnect(context.Background())
	})
	svc := NewService(db.Collection("users"), "test-secret-test-secret-test-secret")
	n := &fakeResetNotifier{}
	svc.SetPasswordReset(db.Collection("password_resets"), "https://tribbae.example/mot-de-passe/nouveau", n)
	return svc, db, n
}

// tokenOf extrait le jeton du lien envoyé (après le #).
func tokenOf(t *testing.T, link string) string {
	t.Helper()
	_, token, ok := strings.Cut(link, "#")
	if !ok || token == "" {
		t.Fatalf("lien sans jeton : %q", link)
	}
	return token
}

func TestPasswordResetFlow(t *testing.T) {
	svc, db, n := setupReset(t)
	ctx := context.Background()
	userID, oldSession, _, err := svc.Register(ctx, "alice@example.com", "ancien-mdp", "Alice")
	if err != nil {
		t.Fatal(err)
	}

	// Adresse inconnue : aucune erreur, aucun email
	if err := svc.RequestPasswordReset(ctx, "inconnu@example.com", ""); err != nil || len(n.links) != 0 {
		t.Fatalf("adresse inconnue : err=%v, emails=%v", err, n.links)
	}
	if err := svc.RequestPasswordReset(ctx, " Alice@Example.com ", "Firefox sur Linux"); err != nil {
		t.Fatal(err)
	}
	if len(n.links) != 1 || n.links[0].email != "alice@example.com" || n.links[0].device != "Firefox sur Linux" ||
		!strings.HasPrefix(n.links[0].url, "https://tribbae.example/mot-de-passe/nouveau#") {
		t.Fatalf("emails = %+v", n.links)
	}
	token := tokenOf(t, n.links[0].url)
	// Seule l'empreinte du jeton est stockée
	if c, _ := db.Collection("password_resets").CountDocuments(ctx, bson.M{"token_hash": token}); c != 0 {
		t.Error("le jeton est stocké en clair")
	}

	// Une nouvelle demande dans la minute ne renvoie rien : le premier lien reste valable
	if err := svc.RequestPasswordReset(ctx, "alice@example.com", ""); err != nil || len(n.links) != 1 {
		t.Fatalf("demande répétée : err=%v, emails=%d", err, len(n.links))
	}

	if _, _, _, _, err := svc.ResetPassword(ctx, token, "court", ""); !errors.Is(err, ErrInvalidInput) {
		t.Errorf("mot de passe trop court accepté : %v", err)
	}
	gotID, newSession, name, _, err := svc.ResetPassword(ctx, token, "nouveau-mdp", "")
	if err != nil || gotID != userID || name != "Alice" || newSession == "" {
		t.Fatalf("reset : id=%q name=%q err=%v", gotID, name, err)
	}
	if len(n.changed) != 1 {
		t.Errorf("email « mot de passe modifié » : %v", n.changed)
	}
	// Le lien ne sert qu'une fois
	if _, _, _, _, err := svc.ResetPassword(ctx, token, "encore-un-mdp", ""); !errors.Is(err, ErrInvalidResetToken) {
		t.Errorf("lien réutilisé : %v", err)
	}
	// Nouveau mot de passe actif, ancien refusé
	if _, _, _, _, err := svc.Login(ctx, "alice@example.com", "nouveau-mdp"); err != nil {
		t.Errorf("connexion avec le nouveau mot de passe : %v", err)
	}
	if _, _, _, _, err := svc.Login(ctx, "alice@example.com", "ancien-mdp"); err == nil {
		t.Error("l'ancien mot de passe marche encore")
	}
	// La session ouverte avant le changement est fermée, la nouvelle marche.
	// Le jeton d'avant date de la même seconde au plus : on recule son iat.
	if _, err := svc.ValidateToken(newSession); err != nil {
		t.Errorf("nouvelle session refusée : %v", err)
	}
	if _, err := db.Collection("users").UpdateOne(ctx, bson.M{"email": "alice@example.com"},
		bson.M{"$set": bson.M{"password_changed_at": time.Now().Add(time.Hour).Truncate(time.Second)}}); err != nil {
		t.Fatal(err)
	}
	if _, err := svc.ValidateToken(oldSession); err == nil {
		t.Error("une session ouverte avant le changement de mot de passe est encore acceptée")
	}
}

func TestPasswordResetLinkExpires(t *testing.T) {
	svc, db, n := setupReset(t)
	ctx := context.Background()
	if _, _, _, err := svc.Register(ctx, "bob@example.com", "mot-de-passe", "Bob"); err != nil {
		t.Fatal(err)
	}
	if err := svc.RequestPasswordReset(ctx, "bob@example.com", ""); err != nil || len(n.links) != 1 {
		t.Fatalf("demande : %v", err)
	}
	if _, err := db.Collection("password_resets").UpdateMany(ctx, bson.M{}, bson.M{"$set": bson.M{"expires_at": time.Now().Add(-time.Second)}}); err != nil {
		t.Fatal(err)
	}
	if _, _, _, _, err := svc.ResetPassword(ctx, tokenOf(t, n.links[0].url), "nouveau-mdp", ""); !errors.Is(err, ErrInvalidResetToken) {
		t.Errorf("lien expiré accepté : %v", err)
	}
	if _, _, _, _, err := svc.ResetPassword(ctx, "jeton-invente", "nouveau-mdp", ""); !errors.Is(err, ErrInvalidResetToken) {
		t.Errorf("jeton inventé accepté : %v", err)
	}
}
