package auth

import (
	"context"
	"crypto/rand"
	"crypto/sha256"
	"encoding/base64"
	"encoding/hex"
	"errors"
	"strings"
	"time"
	"unicode/utf8"

	"go.mongodb.org/mongo-driver/bson"
	"go.mongodb.org/mongo-driver/bson/primitive"
	"go.mongodb.org/mongo-driver/mongo"
	"go.mongodb.org/mongo-driver/mongo/options"
	"golang.org/x/crypto/bcrypt"
)

const (
	// ResetValidity : durée de validité d'un lien de réinitialisation.
	ResetValidity = time.Hour
	// resetCooldown : délai minimal entre deux emails pour un même compte. Le
	// lien précédent reste valable : demander en boucle n'inonde personne.
	resetCooldown = time.Minute
)

// ErrInvalidResetToken : lien inconnu, déjà utilisé ou expiré.
var ErrInvalidResetToken = errors.New("invalid or expired reset link")

// ResetNotifier envoie les emails du mot de passe oublié (mail.Mailer).
type ResetNotifier interface {
	PasswordResetLink(email, name, resetURL string, validity time.Duration, requestedAt time.Time, device string)
	PasswordChanged(email, name string, changedAt time.Time, device string)
}

// passwordReset est une demande de réinitialisation en cours : une par compte
// au plus (une nouvelle demande remplace la précédente). Seule l'empreinte
// SHA-256 du jeton est stockée : une fuite de la base ne donne aucun lien
// utilisable. L'index TTL sur expires_at efface les demandes expirées.
type passwordReset struct {
	UserID    primitive.ObjectID `bson:"_id"`
	TokenHash string             `bson:"token_hash"`
	ExpiresAt time.Time          `bson:"expires_at"`
	CreatedAt time.Time          `bson:"created_at"`
}

// SetPasswordReset active le mot de passe oublié : resets stocke les demandes
// en cours, resetURL est l'adresse de l'écran « Nouveau mot de passe » du site
// (le jeton y est ajouté après un #).
func (s *Service) SetPasswordReset(resets *mongo.Collection, resetURL string, n ResetNotifier) {
	s.resets, s.resetURL, s.resetNotify = resets, resetURL, n
}

func hashToken(token string) string {
	sum := sha256.Sum256([]byte(token))
	return hex.EncodeToString(sum[:])
}

func newResetToken() (string, error) {
	b := make([]byte, 32)
	if _, err := rand.Read(b); err != nil {
		return "", err
	}
	return base64.RawURLEncoding.EncodeToString(b), nil
}

// RequestPasswordReset envoie un lien de réinitialisation si l'adresse a un
// compte. Rien ne distingue la réponse d'une adresse inconnue : l'appelant
// répond toujours la même chose, et l'email part en arrière-plan.
func (s *Service) RequestPasswordReset(ctx context.Context, email, device string) error {
	if s.resets == nil {
		return errors.New("password reset is not configured")
	}
	raw := strings.TrimSpace(email)
	if raw == "" || len(raw) > 254 {
		return nil
	}
	var user User
	// Comme à la connexion : les comptes historiques peuvent avoir des majuscules
	filter := bson.M{"email": bson.M{"$in": bson.A{NormalizeEmail(raw), raw}}}
	if err := s.col.FindOne(ctx, filter).Decode(&user); err != nil {
		if errors.Is(err, mongo.ErrNoDocuments) {
			return nil
		}
		return err
	}
	now := time.Now()
	recent, err := s.resets.CountDocuments(ctx, bson.M{"_id": user.ID, "created_at": bson.M{"$gt": now.Add(-resetCooldown)}})
	if err != nil {
		return err
	}
	if recent > 0 {
		return nil
	}
	token, err := newResetToken()
	if err != nil {
		return err
	}
	reset := passwordReset{UserID: user.ID, TokenHash: hashToken(token), ExpiresAt: now.Add(ResetValidity), CreatedAt: now}
	if _, err := s.resets.ReplaceOne(ctx, bson.M{"_id": user.ID}, reset, options.Replace().SetUpsert(true)); err != nil {
		return err
	}
	if s.resetNotify != nil {
		s.resetNotify.PasswordResetLink(user.Email, user.DisplayName, s.resetURL+"#"+token, ResetValidity, now, device)
	}
	return nil
}

// ResetPassword remplace le mot de passe du compte du lien, qui ne sert
// qu'une fois. Les sessions ouvertes avant sont fermées (voir ValidateToken)
// et une nouvelle session est ouverte.
func (s *Service) ResetPassword(ctx context.Context, token, password, device string) (userID, jwt, displayName string, isAdmin bool, err error) {
	if s.resets == nil {
		return "", "", "", false, errors.New("password reset is not configured")
	}
	if utf8.RuneCountInString(password) < 8 || len(password) > 72 {
		return "", "", "", false, errors.Join(ErrInvalidInput, errors.New("password must be between 8 and 72 characters"))
	}
	if token == "" || len(token) > 128 {
		return "", "", "", false, ErrInvalidResetToken
	}
	// Retirée en même temps qu'elle est lue : deux envois du même lien ne
	// peuvent pas réussir tous les deux
	var reset passwordReset
	err = s.resets.FindOneAndDelete(ctx, bson.M{"token_hash": hashToken(token), "expires_at": bson.M{"$gt": time.Now()}}).Decode(&reset)
	if errors.Is(err, mongo.ErrNoDocuments) {
		return "", "", "", false, ErrInvalidResetToken
	}
	if err != nil {
		return "", "", "", false, err
	}
	hash, err := bcrypt.GenerateFromPassword([]byte(password), bcrypt.DefaultCost)
	if err != nil {
		return "", "", "", false, err
	}
	// Seconde entière : comparée au champ iat (en secondes) des jetons
	changedAt := time.Now().Truncate(time.Second)
	var user User
	err = s.col.FindOneAndUpdate(ctx, bson.M{"_id": reset.UserID},
		bson.M{"$set": bson.M{"password": string(hash), "password_changed_at": changedAt}},
		options.FindOneAndUpdate().SetReturnDocument(options.After)).Decode(&user)
	if errors.Is(err, mongo.ErrNoDocuments) {
		return "", "", "", false, ErrInvalidResetToken // compte supprimé entre-temps
	}
	if err != nil {
		return "", "", "", false, err
	}
	if s.resetNotify != nil {
		s.resetNotify.PasswordChanged(user.Email, user.DisplayName, changedAt, device)
	}
	jwt, err = s.generateToken(user.ID.Hex())
	return user.ID.Hex(), jwt, user.DisplayName, user.IsAdmin, err
}
