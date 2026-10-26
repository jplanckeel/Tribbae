package folder

import (
	"context"
	"crypto/rand"
	"crypto/sha256"
	"encoding/base64"
	"encoding/hex"
	"errors"
	"log"
	"net/mail"
	"slices"
	"strings"
	"time"

	"go.mongodb.org/mongo-driver/v2/bson"
	"go.mongodb.org/mongo-driver/v2/mongo"
	"go.mongodb.org/mongo-driver/v2/mongo/options"

	tmail "github.com/tribbae/backend/internal/mail"
)

const (
	// InvitationValidity : durée de validité d'une invitation dans le cercle.
	InvitationValidity = 7 * 24 * time.Hour
	// resendCooldown : délai avant un nouvel email à la même adresse. Le lien
	// déjà envoyé reste valable (et ouvre aussi les dossiers ajoutés depuis).
	resendCooldown = 10 * time.Minute
	// maxPendingInvitations : invitations en attente par compte, au plus.
	maxPendingInvitations = 30
	// maxInvitationFolders : dossiers ouverts par une même invitation, au plus.
	maxInvitationFolders = 50
)

var (
	// ErrInvalidInvitation : lien inconnu, expiré, annulé ou déjà utilisé.
	ErrInvalidInvitation = errors.New("invalid or expired invitation")
	// ErrWrongAccount : le compte connecté n'utilise pas l'adresse invitée.
	ErrWrongAccount = errors.New("invitation is for another email")
	// ErrTooManyInvitations : trop d'invitations en attente.
	ErrTooManyInvitations = errors.New("too many pending invitations")
)

// CircleNotifier envoie l'email d'invitation dans le cercle (mail.Mailer).
type CircleNotifier interface {
	CircleInvitation(inv tmail.CircleInvitation)
}

// Invitation : une adresse invitée par owner à des dossiers choisis (son
// cercle). On n'y entre qu'en acceptant le lien reçu par email, avec le compte
// de cette adresse : taper une adresse ne fait entrer personne et ne dit pas
// si elle a un compte. Seule l'empreinte SHA-256 du jeton est stockée ; l'index
// TTL sur expires_at efface les invitations expirées. Une seule invitation par
// propriétaire et par adresse : en refaire une y ajoute les dossiers choisis.
type Invitation struct {
	ID        bson.ObjectID `bson:"_id"`
	OwnerID   string        `bson:"owner_id"`
	Email     string        `bson:"email"`
	FolderIDs []string      `bson:"folder_ids"`
	Role      string        `bson:"role"` // "viewer" | "editor"
	TokenHash string        `bson:"token_hash"`
	CreatedAt time.Time     `bson:"created_at"` // dernier envoi
	ExpiresAt time.Time     `bson:"expires_at"`
}

// InvitationInfo : ce qu'affiche le lien d'invitation avant de l'accepter.
type InvitationInfo struct {
	Email         string
	InvitedByName string
	FolderNames   []string
	Role          string
	ExpiresAt     time.Time
}

// SetInvitations active les invitations dans le cercle : invCol les stocke,
// inviteURL est l'adresse de l'écran « Rejoindre le cercle » du site (le jeton
// y est ajouté après un #) et markVerified confirme l'adresse de qui accepte
// (le lien prouve qu'il reçoit ses emails).
func (s *Service) SetInvitations(invCol *mongo.Collection, inviteURL string, n CircleNotifier, markVerified func(ctx context.Context, userID string) error) {
	s.invCol, s.inviteURL, s.notify, s.markVerified = invCol, inviteURL, n, markVerified
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

// ownedFolders retourne, dans l'ordre demandé, les dossiers de ids dont
// ownerID est le propriétaire (les autres sont ignorés).
func (s *Service) ownedFolders(ctx context.Context, ownerID string, ids []string) ([]Folder, error) {
	oids := make([]bson.ObjectID, 0, len(ids))
	for _, id := range ids {
		if oid, err := bson.ObjectIDFromHex(id); err == nil {
			oids = append(oids, oid)
		}
	}
	if len(oids) == 0 {
		return nil, nil
	}
	cur, err := s.col.Find(ctx, bson.M{"_id": bson.M{"$in": oids}, "owner_id": ownerID},
		options.Find().SetProjection(bson.M{"name": 1, "owner_id": 1, "visibility": 1, "collaborators": 1}))
	if err != nil {
		return nil, err
	}
	var found []Folder
	if err := cur.All(ctx, &found); err != nil {
		return nil, err
	}
	byID := make(map[string]Folder, len(found))
	for _, f := range found {
		byID[f.ID.Hex()] = f
	}
	out := make([]Folder, 0, len(found))
	for _, id := range ids {
		if f, ok := byID[id]; ok {
			out = append(out, f)
			delete(byID, id) // un identifiant en double ne compte qu'une fois
		}
	}
	return out, nil
}

func folderNames(folders []Folder) []string {
	names := make([]string, 0, len(folders))
	for _, f := range folders {
		names = append(names, f.Name)
	}
	return names
}

func (s *Service) displayName(ctx context.Context, userID string) string {
	id, err := bson.ObjectIDFromHex(userID)
	if err != nil {
		return ""
	}
	var u struct {
		DisplayName string `bson:"display_name"`
	}
	_ = s.userCol.FindOne(ctx, bson.M{"_id": id}, options.FindOne().SetProjection(bson.M{"display_name": 1})).Decode(&u)
	return u.DisplayName
}

// Invite invite l'adresse donnée aux dossiers folderIDs de ownerID (ceux qu'il
// possède) et lui envoie le lien par email. La réponse est la même que
// l'adresse ait un compte ou non. Une invitation déjà en attente pour cette
// adresse reçoit les dossiers en plus et le rôle choisi ; l'email n'est renvoyé
// qu'après resendCooldown.
func (s *Service) Invite(ctx context.Context, ownerID, email string, folderIDs []string, role string) (*Invitation, error) {
	if s.invCol == nil {
		return nil, errors.New("circle invitations are not configured")
	}
	email = strings.ToLower(strings.TrimSpace(email))
	if !validEmail(email) {
		return nil, invalid("a valid email is required")
	}
	if role != "editor" {
		role = "viewer"
	}
	if len(folderIDs) == 0 || len(folderIDs) > maxInvitationFolders {
		return nil, invalid("choose the folders to open")
	}
	if err := s.requireVerified(ctx, ownerID); err != nil {
		return nil, err
	}
	folders, err := s.ownedFolders(ctx, ownerID, folderIDs)
	if err != nil {
		return nil, err
	}
	if len(folders) == 0 {
		return nil, ErrNotFound
	}
	if s.isOwnerEmail(ctx, ownerID, email) {
		return nil, invalid("you cannot invite yourself")
	}

	now := time.Now()
	var existing Invitation
	err = s.invCol.FindOne(ctx, bson.M{"owner_id": ownerID, "email": email, "expires_at": bson.M{"$gt": now}}).Decode(&existing)
	switch {
	case errors.Is(err, mongo.ErrNoDocuments):
		pending, err := s.invCol.CountDocuments(ctx, bson.M{"owner_id": ownerID, "expires_at": bson.M{"$gt": now}})
		if err != nil {
			return nil, err
		}
		if pending >= maxPendingInvitations {
			return nil, ErrTooManyInvitations
		}
	case err != nil:
		return nil, err
	}

	ids := existing.FolderIDs
	for _, f := range folders {
		if !slices.Contains(ids, f.ID.Hex()) {
			ids = append(ids, f.ID.Hex())
		}
	}
	if len(ids) > maxInvitationFolders {
		return nil, invalid("choose the folders to open")
	}
	// Envoi récent : le lien déjà reçu ouvre aussi les dossiers ajoutés
	if !existing.ID.IsZero() && existing.CreatedAt.After(now.Add(-resendCooldown)) {
		existing.FolderIDs, existing.Role = ids, role
		if _, err := s.invCol.UpdateOne(ctx, bson.M{"_id": existing.ID}, bson.M{"$set": bson.M{"folder_ids": ids, "role": role}}); err != nil {
			return nil, err
		}
		return &existing, nil
	}

	token, err := newToken()
	if err != nil {
		return nil, err
	}
	inv := Invitation{
		ID:        existing.ID,
		OwnerID:   ownerID,
		Email:     email,
		FolderIDs: ids,
		Role:      role,
		TokenHash: hashToken(token),
		CreatedAt: now,
		ExpiresAt: now.Add(InvitationValidity),
	}
	if inv.ID.IsZero() {
		inv.ID = bson.NewObjectID()
	}
	// Une invitation expirée pas encore effacée par le TTL laisse la place
	// (son _id ne peut pas être repris)
	if _, err := s.invCol.DeleteMany(ctx, bson.M{"owner_id": ownerID, "email": email, "expires_at": bson.M{"$lte": now}}); err != nil {
		return nil, err
	}
	if _, err := s.invCol.ReplaceOne(ctx, bson.M{"_id": inv.ID}, inv, options.Replace().SetUpsert(true)); err != nil {
		return nil, err
	}
	if s.notify != nil {
		all, err := s.ownedFolders(ctx, ownerID, ids)
		if err != nil {
			return nil, err
		}
		s.notify.CircleInvitation(tmail.CircleInvitation{
			Email:       email,
			InviterName: s.displayName(ctx, ownerID),
			Folders:     folderNames(all),
			Editor:      role == "editor",
			URL:         s.inviteURL + "#" + token,
			Validity:    InvitationValidity,
		})
	}
	return &inv, nil
}

// isOwnerEmail : l'adresse est celle du compte ownerID.
func (s *Service) isOwnerEmail(ctx context.Context, ownerID, email string) bool {
	id, err := bson.ObjectIDFromHex(ownerID)
	if err != nil {
		return false
	}
	var u struct {
		Email string `bson:"email"`
	}
	if err := s.userCol.FindOne(ctx, bson.M{"_id": id}, options.FindOne().SetProjection(bson.M{"email": 1})).Decode(&u); err != nil {
		return false
	}
	return strings.EqualFold(strings.TrimSpace(u.Email), email)
}

// Invitations retourne les invitations en attente de ownerID, les plus
// anciennes d'abord, avec le nom des dossiers encore existants.
func (s *Service) Invitations(ctx context.Context, ownerID string) ([]Invitation, map[string]string, error) {
	if s.invCol == nil {
		return nil, nil, nil
	}
	cur, err := s.invCol.Find(ctx, bson.M{"owner_id": ownerID, "expires_at": bson.M{"$gt": time.Now()}},
		options.Find().SetSort(bson.D{{Key: "created_at", Value: 1}}))
	if err != nil {
		return nil, nil, err
	}
	var invs []Invitation
	if err := cur.All(ctx, &invs); err != nil {
		return nil, nil, err
	}
	var ids []string
	for _, inv := range invs {
		ids = append(ids, inv.FolderIDs...)
	}
	folders, err := s.ownedFolders(ctx, ownerID, ids)
	if err != nil {
		return nil, nil, err
	}
	names := make(map[string]string, len(folders))
	for _, f := range folders {
		names[f.ID.Hex()] = f.Name
	}
	return invs, names, nil
}

// Revoke annule une invitation envoyée par ownerID : son lien ne marche plus.
func (s *Service) Revoke(ctx context.Context, ownerID, invitationID string) error {
	id, err := bson.ObjectIDFromHex(invitationID)
	if err != nil || s.invCol == nil {
		return ErrInvalidInvitation
	}
	res, err := s.invCol.DeleteOne(ctx, bson.M{"_id": id, "owner_id": ownerID})
	if err != nil {
		return err
	}
	if res.DeletedCount == 0 {
		return ErrInvalidInvitation
	}
	return nil
}

// pending retrouve l'invitation encore valable d'un jeton et ses dossiers
// existants. Une invitation dont tous les dossiers ont disparu n'est plus valable.
func (s *Service) pending(ctx context.Context, token string) (*Invitation, []Folder, error) {
	if s.invCol == nil || token == "" || len(token) > 128 {
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
	folders, err := s.ownedFolders(ctx, inv.OwnerID, inv.FolderIDs)
	if err != nil {
		return nil, nil, err
	}
	if len(folders) == 0 {
		return nil, nil, ErrInvalidInvitation
	}
	return &inv, folders, nil
}

// Lookup décrit l'invitation d'un lien, pour l'afficher avant de l'accepter.
func (s *Service) Lookup(ctx context.Context, token string) (*InvitationInfo, error) {
	inv, folders, err := s.pending(ctx, token)
	if err != nil {
		return nil, err
	}
	return &InvitationInfo{
		Email:         inv.Email,
		InvitedByName: s.displayName(ctx, inv.OwnerID),
		FolderNames:   folderNames(folders),
		Role:          inv.Role,
		ExpiresAt:     inv.ExpiresAt,
	}, nil
}

// Accept ouvre à userID les dossiers de l'invitation. Le compte doit utiliser
// l'adresse invitée : le lien prouve qu'on la reçoit, un lien transféré n'ouvre
// rien à quelqu'un d'autre. Déjà collaborateur d'un dossier : son rôle devient
// celui de l'invitation.
func (s *Service) Accept(ctx context.Context, userID, token string) ([]*Folder, error) {
	inv, folders, err := s.pending(ctx, token)
	if err != nil {
		return nil, err
	}
	uid, err := bson.ObjectIDFromHex(userID)
	if err != nil {
		return nil, ErrWrongAccount
	}
	var user struct {
		Email       string `bson:"email"`
		DisplayName string `bson:"display_name"`
	}
	if err := s.userCol.FindOne(ctx, bson.M{"_id": uid}, options.FindOne().SetProjection(bson.M{"email": 1, "display_name": 1})).Decode(&user); err != nil {
		return nil, ErrWrongAccount
	}
	if !strings.EqualFold(strings.TrimSpace(user.Email), inv.Email) {
		return nil, ErrWrongAccount
	}
	if userID == inv.OwnerID {
		return nil, ErrWrongAccount
	}
	now := time.Now()
	for _, f := range folders {
		// Retiré puis ajouté : une seule entrée, au rôle de l'invitation
		if _, err := s.col.UpdateOne(ctx, bson.M{"_id": f.ID}, bson.M{"$pull": bson.M{"collaborators": bson.M{"user_id": userID}}}); err != nil {
			return nil, err
		}
		set := bson.M{"updated_at": now}
		if f.Visibility == "private" {
			set["visibility"] = "shared"
		}
		entry := CollaboratorEntry{UserID: userID, Email: user.Email, DisplayName: user.DisplayName, Role: inv.Role, AddedAt: now}
		if _, err := s.col.UpdateOne(ctx, bson.M{"_id": f.ID}, bson.M{"$push": bson.M{"collaborators": entry}, "$set": set}); err != nil {
			return nil, err
		}
	}
	if _, err := s.invCol.DeleteOne(ctx, bson.M{"_id": inv.ID}); err != nil {
		return nil, err
	}
	if s.markVerified != nil {
		if err := s.markVerified(ctx, userID); err != nil {
			log.Printf("folder: mark email verified: %v", err)
		}
	}
	out := make([]*Folder, 0, len(folders))
	for _, f := range folders {
		got, err := s.Get(ctx, f.ID.Hex(), userID)
		if err == nil {
			out = append(out, got)
		}
	}
	return out, nil
}

// forgetFolder retire un dossier supprimé des invitations qui l'ouvraient ;
// celles qui n'ouvrent plus rien sont effacées.
func (s *Service) forgetFolder(ctx context.Context, folderID string) {
	if s.invCol == nil {
		return
	}
	if _, err := s.invCol.UpdateMany(ctx, bson.M{"folder_ids": folderID}, bson.M{"$pull": bson.M{"folder_ids": folderID}}); err != nil {
		log.Printf("folder: forget folder in invitations: %v", err)
		return
	}
	if _, err := s.invCol.DeleteMany(ctx, bson.M{"folder_ids": bson.M{"$size": 0}}); err != nil {
		log.Printf("folder: drop empty invitations: %v", err)
	}
}
