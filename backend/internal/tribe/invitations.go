package tribe

import (
	"context"
	"crypto/rand"
	"crypto/sha256"
	"encoding/base64"
	"encoding/hex"
	"errors"
	"net/mail"
	"strings"
	"time"

	"go.mongodb.org/mongo-driver/v2/bson"
	"go.mongodb.org/mongo-driver/v2/mongo"
	"go.mongodb.org/mongo-driver/v2/mongo/options"

	tmail "github.com/tribbae/backend/internal/mail"
)

const (
	// InvitationValidity : durée de validité d'une invitation.
	InvitationValidity = 7 * 24 * time.Hour
	// resendCooldown : délai avant de pouvoir renvoyer une invitation à la même
	// adresse. Le lien déjà envoyé reste valable ; inviter en boucle n'inonde
	// personne d'emails.
	resendCooldown = 10 * time.Minute
)

// Invitation : une adresse invitée à rejoindre une tribu. On ne rejoint une
// tribu qu'en acceptant le lien reçu par email : taper une adresse ne fait
// entrer personne et ne dit pas si elle a un compte. Seule l'empreinte SHA-256
// du jeton est stockée ; l'index TTL sur expires_at efface les invitations
// expirées. Une seule invitation par tribu et par adresse (une nouvelle
// remplace la précédente).
type Invitation struct {
	ID        bson.ObjectID `bson:"_id"`
	TribeID   bson.ObjectID `bson:"tribe_id"`
	Email     string        `bson:"email"`
	InvitedBy string        `bson:"invited_by"`
	TokenHash string        `bson:"token_hash"`
	CreatedAt time.Time     `bson:"created_at"`
	ExpiresAt time.Time     `bson:"expires_at"`
}

// InvitationInfo : ce qu'affiche le lien d'invitation avant de l'accepter.
type InvitationInfo struct {
	Email         string
	TribeName     string
	InvitedByName string
	MemberNames   []string
	ExpiresAt     time.Time
}

func hashToken(token string) string {
	sum := sha256.Sum256([]byte(token))
	return hex.EncodeToString(sum[:])
}

func newToken() (string, error) {
	b := make([]byte, 32)
	if _, err := rand.Read(b); err != nil {
		return "", err
	}
	return base64.RawURLEncoding.EncodeToString(b), nil
}

func validEmail(email string) bool {
	a, err := mail.ParseAddress(email)
	return err == nil && a.Address == email && len(email) <= 254
}

// Invite invite l'adresse donnée dans la tribu de userID et lui envoie le lien
// par email. La réponse est la même que l'adresse ait un compte ou non.
func (s *Service) Invite(ctx context.Context, userID, email string) (*Tribe, error) {
	email = strings.ToLower(strings.TrimSpace(email))
	if !validEmail(email) {
		return nil, invalid("a valid email is required")
	}
	t, err := s.ensure(ctx, userID)
	if err != nil {
		return nil, err
	}
	// Déjà membre (y compris soi-même) : rien à envoyer. Les membres se
	// connaissent déjà, cela ne révèle rien.
	ids := make([]string, 0, len(t.Members))
	for _, m := range t.Members {
		ids = append(ids, m.UserID)
	}
	users := s.Users(ctx, ids)
	for _, u := range users {
		if strings.EqualFold(u.Email, email) {
			return nil, ErrAlreadyMember
		}
	}
	pending, err := s.invCol.CountDocuments(ctx, bson.M{"tribe_id": t.ID, "email": bson.M{"$ne": email}, "expires_at": bson.M{"$gt": time.Now()}})
	if err != nil {
		return nil, err
	}
	if len(t.Members)+int(pending) >= maxMembers {
		return nil, ErrTribeFull
	}
	now := time.Now()
	recent, err := s.invCol.CountDocuments(ctx, bson.M{"tribe_id": t.ID, "email": email, "created_at": bson.M{"$gt": now.Add(-resendCooldown)}})
	if err != nil {
		return nil, err
	}
	if recent > 0 {
		return t, nil
	}
	token, err := newToken()
	if err != nil {
		return nil, err
	}
	inv := Invitation{
		ID:        bson.NewObjectID(),
		TribeID:   t.ID,
		Email:     email,
		InvitedBy: userID,
		TokenHash: hashToken(token),
		CreatedAt: now,
		ExpiresAt: now.Add(InvitationValidity),
	}
	if _, err := s.invCol.ReplaceOne(ctx, bson.M{"tribe_id": t.ID, "email": email}, inv, options.Replace().SetUpsert(true)); err != nil {
		return nil, err
	}
	if s.notify != nil {
		names := make([]string, 0, len(ids))
		for _, id := range ids {
			names = append(names, users[id].DisplayName)
		}
		s.notify.TribeInvitation(tmail.TribeInvitation{
			Email:       email,
			InviterName: users[userID].DisplayName,
			TribeName:   t.Name,
			Members:     names,
			URL:         s.inviteURL + "#" + token,
			Validity:    InvitationValidity,
		})
	}
	return t, nil
}

// Invitations retourne les invitations en attente de la tribu.
func (s *Service) Invitations(ctx context.Context, tribeID bson.ObjectID) ([]Invitation, error) {
	if tribeID.IsZero() {
		return nil, nil
	}
	cur, err := s.invCol.Find(ctx, bson.M{"tribe_id": tribeID, "expires_at": bson.M{"$gt": time.Now()}},
		options.Find().SetSort(bson.D{{Key: "created_at", Value: 1}}))
	if err != nil {
		return nil, err
	}
	var out []Invitation
	return out, cur.All(ctx, &out)
}

// Revoke annule une invitation de la tribu de userID (tout membre peut le faire).
func (s *Service) Revoke(ctx context.Context, userID, invitationID string) (*Tribe, error) {
	t, err := s.find(ctx, userID)
	if err != nil {
		return nil, err
	}
	id, idErr := bson.ObjectIDFromHex(invitationID)
	if t == nil || idErr != nil {
		return nil, ErrInvalidInvitation
	}
	res, err := s.invCol.DeleteOne(ctx, bson.M{"_id": id, "tribe_id": t.ID})
	if err != nil {
		return nil, err
	}
	if res.DeletedCount == 0 {
		return nil, ErrInvalidInvitation
	}
	return t, nil
}

// pending retrouve l'invitation encore valable d'un jeton et sa tribu.
func (s *Service) pending(ctx context.Context, token string) (*Invitation, *Tribe, error) {
	if token == "" || len(token) > 128 {
		return nil, nil, ErrInvalidInvitation
	}
	var inv Invitation
	err := s.invCol.FindOne(ctx, bson.M{"token_hash": hashToken(token), "expires_at": bson.M{"$gt": time.Now()}}).Decode(&inv)
	if errors.Is(err, mongo.ErrNoDocuments) {
		return nil, nil, ErrInvalidInvitation
	}
	if err != nil {
		return nil, nil, err
	}
	var t Tribe
	if err := s.col.FindOne(ctx, bson.M{"_id": inv.TribeID}).Decode(&t); err != nil {
		if errors.Is(err, mongo.ErrNoDocuments) {
			return nil, nil, ErrInvalidInvitation
		}
		return nil, nil, err
	}
	return &inv, &t, nil
}

// Lookup décrit l'invitation d'un lien, pour l'afficher avant de l'accepter.
func (s *Service) Lookup(ctx context.Context, token string) (*InvitationInfo, error) {
	inv, t, err := s.pending(ctx, token)
	if err != nil {
		return nil, err
	}
	ids := make([]string, 0, len(t.Members)+1)
	for _, m := range t.Members {
		ids = append(ids, m.UserID)
	}
	users := s.Users(ctx, append(ids, inv.InvitedBy))
	names := make([]string, 0, len(ids))
	for _, id := range ids {
		names = append(names, users[id].DisplayName)
	}
	return &InvitationInfo{
		Email:         inv.Email,
		TribeName:     t.Name,
		InvitedByName: users[inv.InvitedBy].DisplayName,
		MemberNames:   names,
		ExpiresAt:     inv.ExpiresAt,
	}, nil
}

// Accept fait entrer userID dans la tribu de l'invitation. Le compte doit
// utiliser l'adresse invitée : le lien prouve qu'on la reçoit, un lien
// transféré ne fait pas entrer quelqu'un d'autre.
func (s *Service) Accept(ctx context.Context, userID, token string) (*Tribe, error) {
	inv, t, err := s.pending(ctx, token)
	if err != nil {
		return nil, err
	}
	u, err := s.user(ctx, userID)
	if err != nil {
		return nil, err
	}
	if !strings.EqualFold(strings.TrimSpace(u.Email), inv.Email) {
		return nil, ErrWrongAccount
	}
	switch err := s.join(ctx, t, userID, inv.InvitedBy); {
	case errors.Is(err, ErrAlreadyMember):
		// Déjà dedans (lien ouvert deux fois) : l'invitation a servi
	case err != nil:
		return nil, err
	}
	if _, err := s.invCol.DeleteOne(ctx, bson.M{"_id": inv.ID}); err != nil {
		return nil, err
	}
	return s.find(ctx, userID)
}

// dropInvitations efface les invitations d'une tribu qui disparaît.
func (s *Service) dropInvitations(ctx context.Context, tribeID bson.ObjectID) {
	_, _ = s.invCol.DeleteMany(ctx, bson.M{"tribe_id": tribeID})
}
