package auth

import (
	"context"
	"errors"
	"time"

	"go.mongodb.org/mongo-driver/v2/bson"
	"go.mongodb.org/mongo-driver/v2/mongo"
	"go.mongodb.org/mongo-driver/v2/mongo/options"
)

const (
	// VerifyValidity : durée de validité d'un lien de confirmation d'adresse.
	VerifyValidity = 24 * time.Hour
	// verifyCooldown : délai minimal entre deux emails de confirmation pour un
	// même compte. Le lien précédent reste valable.
	verifyCooldown = time.Minute
)

var (
	// ErrInvalidVerifyToken : lien inconnu, déjà utilisé ou expiré.
	ErrInvalidVerifyToken = errors.New("invalid or expired verification link")
	// ErrEmailNotVerified : action réservée aux comptes dont l'adresse est
	// confirmée (publier, écrire à d'autres personnes).
	ErrEmailNotVerified = errors.New("email address not verified")
)

// VerifyNotifier envoie le lien de confirmation de l'adresse (mail.Mailer).
type VerifyNotifier interface {
	EmailVerification(email, name, verifyURL string, validity time.Duration)
}

// emailVerification est une confirmation d'adresse en attente : une par
// compte au plus, jeton stocké haché comme pour le mot de passe oublié. Email
// est l'adresse à confirmer : si celle du compte change entre-temps, le lien
// ne confirme plus rien. L'index TTL sur expires_at efface les demandes expirées.
type emailVerification struct {
	UserID    bson.ObjectID `bson:"_id"`
	Email     string        `bson:"email"`
	TokenHash string        `bson:"token_hash"`
	ExpiresAt time.Time     `bson:"expires_at"`
	CreatedAt time.Time     `bson:"created_at"`
}

// SetEmailVerification active la confirmation d'adresse : verifications
// stocke les demandes en cours, verifyURL est l'adresse de l'écran
// « Confirmer mon adresse » du site (le jeton y est ajouté après un #).
func (s *Service) SetEmailVerification(verifications *mongo.Collection, verifyURL string, n VerifyNotifier) {
	s.verifications, s.verifyURL, s.verifyNotify = verifications, verifyURL, n
}

// newVerification enregistre un nouveau lien de confirmation pour user (il
// remplace le précédent) et retourne son adresse, "" si la confirmation
// d'adresse n'est pas activée.
func (s *Service) newVerification(ctx context.Context, user *User, now time.Time) (string, error) {
	if s.verifications == nil {
		return "", nil
	}
	token, err := newResetToken()
	if err != nil {
		return "", err
	}
	v := emailVerification{UserID: user.ID, Email: user.Email, TokenHash: hashToken(token), ExpiresAt: now.Add(VerifyValidity), CreatedAt: now}
	if _, err := s.verifications.ReplaceOne(ctx, bson.M{"_id": user.ID}, v, options.Replace().SetUpsert(true)); err != nil {
		return "", err
	}
	return s.verifyURL + "#" + token, nil
}

// ResendEmailVerification renvoie le lien de confirmation au compte userID.
// Retourne true si l'adresse est déjà confirmée (rien n'est envoyé). Les
// demandes rapprochées n'envoient rien de plus : le dernier lien reste bon.
func (s *Service) ResendEmailVerification(ctx context.Context, userID string) (bool, error) {
	if s.verifications == nil {
		return false, errors.New("email verification is not configured")
	}
	user, err := s.GetUser(ctx, userID)
	if err != nil {
		return false, err
	}
	if user.EmailVerifiedAt != nil {
		return true, nil
	}
	now := time.Now()
	recent, err := s.verifications.CountDocuments(ctx, bson.M{"_id": user.ID, "created_at": bson.M{"$gt": now.Add(-verifyCooldown)}})
	if err != nil {
		return false, err
	}
	if recent > 0 {
		return false, nil
	}
	url, err := s.newVerification(ctx, user, now)
	if err != nil {
		return false, err
	}
	if s.verifyNotify != nil {
		s.verifyNotify.EmailVerification(user.Email, user.DisplayName, url, VerifyValidity)
	}
	return false, nil
}

// VerifyEmail confirme l'adresse du compte du lien, qui ne sert qu'une fois.
// Aucune session n'est demandée : le lien prouve à lui seul que l'on reçoit
// les emails de l'adresse.
func (s *Service) VerifyEmail(ctx context.Context, token string) error {
	if s.verifications == nil {
		return errors.New("email verification is not configured")
	}
	if token == "" || len(token) > 128 {
		return ErrInvalidVerifyToken
	}
	var v emailVerification
	err := s.verifications.FindOneAndDelete(ctx, bson.M{"token_hash": hashToken(token), "expires_at": bson.M{"$gt": time.Now()}}).Decode(&v)
	if errors.Is(err, mongo.ErrNoDocuments) {
		return ErrInvalidVerifyToken
	}
	if err != nil {
		return err
	}
	// Seulement si le compte a toujours cette adresse ; déjà confirmée : rien à faire
	res, err := s.col.UpdateOne(ctx,
		bson.M{"_id": v.UserID, "email": v.Email},
		bson.M{"$min": bson.M{"email_verified_at": time.Now()}})
	if err != nil {
		return err
	}
	if res.MatchedCount == 0 {
		return ErrInvalidVerifyToken // compte supprimé ou adresse changée entre-temps
	}
	return nil
}

// MarkEmailVerified confirme l'adresse de userID quand un autre lien reçu par
// email l'a prouvée (mot de passe oublié, invitation dans une tribu).
func (s *Service) MarkEmailVerified(ctx context.Context, userID string) error {
	id, err := bson.ObjectIDFromHex(userID)
	if err != nil {
		return errors.New("invalid user id")
	}
	if _, err := s.col.UpdateOne(ctx, bson.M{"_id": id}, bson.M{"$min": bson.M{"email_verified_at": time.Now()}}); err != nil {
		return err
	}
	if s.verifications != nil {
		_, err = s.verifications.DeleteOne(ctx, bson.M{"_id": id})
	}
	return err
}

// RequireVerifiedEmail retourne ErrEmailNotVerified si l'adresse de userID
// n'est pas confirmée. Branchée dans les services qui publient ou écrivent à
// d'autres personnes (idées, dossiers, tribu).
func (s *Service) RequireVerifiedEmail(ctx context.Context, userID string) error {
	id, err := bson.ObjectIDFromHex(userID)
	if err != nil {
		return ErrEmailNotVerified
	}
	var u struct {
		EmailVerifiedAt *time.Time `bson:"email_verified_at"`
	}
	err = s.col.FindOne(ctx, bson.M{"_id": id}, options.FindOne().SetProjection(bson.M{"email_verified_at": 1})).Decode(&u)
	if errors.Is(err, mongo.ErrNoDocuments) {
		return ErrEmailNotVerified
	}
	if err != nil {
		return err
	}
	if u.EmailVerifiedAt == nil {
		return ErrEmailNotVerified
	}
	return nil
}
