// Package tribe gère la tribu : les comptes d'une même famille, qui voient et
// modifient ensemble idées, dossiers, enfants et liste de courses.
//
// Un compte appartient à au plus une tribu. Ajouté par email, un membre voit
// tout de suite le contenu partagé de la tribu, mais le sien n'y entre qu'après
// qu'il l'a confirmé (Sharing) : connaître un email ne donne pas accès aux
// données d'un compte. Sans tribu enregistrée, un compte forme une tribu
// « virtuelle » d'un seul membre, nommée d'après lui.
package tribe

import (
	"context"
	"errors"
	"strings"
	"time"
	"unicode/utf8"

	"go.mongodb.org/mongo-driver/v2/bson"
	"go.mongodb.org/mongo-driver/v2/mongo"

	"github.com/tribbae/backend/internal/mail"
)

const (
	RoleOwner  = "owner"
	RoleMember = "member"

	maxNameRunes = 60
	maxMembers   = 12
)

var (
	ErrInvalidInput = errors.New("invalid input")
	// Lien d'invitation inconnu, expiré, révoqué ou tribu disparue
	ErrInvalidInvitation = errors.New("invalid or expired invitation")
	// Le compte connecté n'est pas celui de l'adresse invitée
	ErrWrongAccount  = errors.New("invitation is for another email")
	ErrAlreadyMember = errors.New("already a member of this tribe")
	ErrInOtherTribe  = errors.New("this account already belongs to another tribe")
	ErrForbidden     = errors.New("only the tribe owner can remove another member")
	ErrNotMember     = errors.New("not a member of this tribe")
	ErrTribeFull     = errors.New("tribe is full")
)

func invalid(msg string) error { return errors.Join(ErrInvalidInput, errors.New(msg)) }

type Member struct {
	UserID  string    `bson:"user_id"`
	Role    string    `bson:"role"`
	Sharing bool      `bson:"sharing"`
	AddedAt time.Time `bson:"added_at"`
	AddedBy string    `bson:"added_by,omitempty"`
}

type Tribe struct {
	ID        bson.ObjectID `bson:"_id,omitempty"`
	Name      string        `bson:"name"`
	Members   []Member      `bson:"members"`
	CreatedAt time.Time     `bson:"created_at"`
	UpdatedAt time.Time     `bson:"updated_at"`
}

// Member retourne le membre userID, ou nil.
func (t *Tribe) Member(userID string) *Member {
	for i := range t.Members {
		if t.Members[i].UserID == userID {
			return &t.Members[i]
		}
	}
	return nil
}

type userDoc struct {
	ID          bson.ObjectID `bson:"_id"`
	Email       string        `bson:"email"`
	DisplayName string        `bson:"display_name"`
}

// Notifier envoie les emails d'invitation (mail.Mailer).
type Notifier interface {
	TribeInvitation(inv mail.TribeInvitation)
}

type Service struct {
	col     *mongo.Collection
	userCol *mongo.Collection
	// Invitations en attente (invitations.go)
	invCol    *mongo.Collection
	notify    Notifier
	inviteURL string
}

func NewService(col, userCol, invCol *mongo.Collection) *Service {
	return &Service{col: col, userCol: userCol, invCol: invCol}
}

// SetNotifier active l'email d'invitation ; inviteURL est l'adresse de l'écran
// « Rejoindre la tribu » du site (le jeton y est ajouté après un #).
func (s *Service) SetNotifier(n Notifier, inviteURL string) { s.notify, s.inviteURL = n, inviteURL }

// find retourne la tribu enregistrée de userID, ou nil.
func (s *Service) find(ctx context.Context, userID string) (*Tribe, error) {
	var t Tribe
	err := s.col.FindOne(ctx, bson.M{"members.user_id": userID}).Decode(&t)
	if errors.Is(err, mongo.ErrNoDocuments) {
		return nil, nil
	}
	if err != nil {
		return nil, err
	}
	return &t, nil
}

// Peers retourne les comptes dont userID voit et modifie le contenu : lui-même
// et les membres de sa tribu qui ont confirmé le partage. En cas d'erreur, on
// se replie sur userID seul (jamais plus d'accès que nécessaire).
func (s *Service) Peers(ctx context.Context, userID string) []string {
	if userID == "" {
		return nil
	}
	t, err := s.find(ctx, userID)
	if err != nil || t == nil {
		return []string{userID}
	}
	peers := []string{userID}
	for _, m := range t.Members {
		if m.UserID != userID && m.Sharing {
			peers = append(peers, m.UserID)
		}
	}
	return peers
}

// Get retourne la tribu de userID (virtuelle s'il n'en a pas encore).
func (s *Service) Get(ctx context.Context, userID string) (*Tribe, error) {
	t, err := s.find(ctx, userID)
	if err != nil || t != nil {
		return t, err
	}
	u, err := s.user(ctx, userID)
	if err != nil {
		return nil, err
	}
	return &Tribe{Name: u.DisplayName, Members: []Member{{UserID: userID, Role: RoleOwner, Sharing: true}}}, nil
}

// ensure retourne la tribu enregistrée de userID, en la créant au besoin.
func (s *Service) ensure(ctx context.Context, userID string) (*Tribe, error) {
	t, err := s.find(ctx, userID)
	if err != nil || t != nil {
		return t, err
	}
	u, err := s.user(ctx, userID)
	if err != nil {
		return nil, err
	}
	now := time.Now()
	t = &Tribe{
		ID:        bson.NewObjectID(),
		Name:      u.DisplayName,
		Members:   []Member{{UserID: userID, Role: RoleOwner, Sharing: true, AddedAt: now}},
		CreatedAt: now,
		UpdatedAt: now,
	}
	if _, err := s.col.InsertOne(ctx, t); err != nil {
		if mongo.IsDuplicateKeyError(err) { // créée en parallèle
			return s.find(ctx, userID)
		}
		return nil, err
	}
	return t, nil
}

// Rename change le nom de la tribu (« La tribu … »). Tout membre peut le faire.
func (s *Service) Rename(ctx context.Context, userID, name string) (*Tribe, error) {
	name = strings.Join(strings.Fields(name), " ")
	if name == "" || utf8.RuneCountInString(name) > maxNameRunes {
		return nil, invalid("name must be 1 to 60 characters")
	}
	t, err := s.ensure(ctx, userID)
	if err != nil {
		return nil, err
	}
	if _, err := s.col.UpdateOne(ctx, bson.M{"_id": t.ID}, bson.M{"$set": bson.M{"name": name, "updated_at": time.Now()}}); err != nil {
		return nil, err
	}
	t.Name = name
	return t, nil
}

// join fait entrer inviteeID dans la tribu t (invitation acceptée). Il voit
// aussitôt la tribu ; son propre contenu n'est partagé qu'après sa confirmation.
func (s *Service) join(ctx context.Context, t *Tribe, inviteeID, addedBy string) error {
	if t.Member(inviteeID) != nil {
		return ErrAlreadyMember
	}
	if len(t.Members) >= maxMembers {
		return ErrTribeFull
	}
	// L'invité ne peut pas être retiré d'une tribu qu'il partage avec d'autres ;
	// une tribu où il est seul (créée pour la renommer) est simplement remplacée.
	if other, err := s.find(ctx, inviteeID); err != nil {
		return err
	} else if other != nil {
		if len(other.Members) > 1 {
			return ErrInOtherTribe
		}
		if _, err := s.col.DeleteOne(ctx, bson.M{"_id": other.ID, "members": bson.M{"$size": 1}}); err != nil {
			return err
		}
		s.dropInvitations(ctx, other.ID)
	}
	m := Member{UserID: inviteeID, Role: RoleMember, Sharing: false, AddedAt: time.Now(), AddedBy: addedBy}
	res, err := s.col.UpdateOne(ctx,
		bson.M{"_id": t.ID, "members.user_id": bson.M{"$ne": inviteeID}},
		bson.M{"$push": bson.M{"members": m}, "$set": bson.M{"updated_at": time.Now()}})
	if err != nil {
		if mongo.IsDuplicateKeyError(err) {
			return ErrInOtherTribe
		}
		return err
	}
	if res.ModifiedCount == 0 {
		return ErrAlreadyMember
	}
	return nil
}

// RemoveMember retire targetID de la tribu de userID : quitter la tribu
// (targetID == userID) ou, pour le créateur, en retirer un membre. Le contenu
// du membre parti cesse aussitôt d'être partagé.
func (s *Service) RemoveMember(ctx context.Context, userID, targetID string) (*Tribe, error) {
	t, err := s.find(ctx, userID)
	if err != nil {
		return nil, err
	}
	if t == nil || t.Member(targetID) == nil {
		return nil, ErrNotMember
	}
	if targetID != userID && t.Member(userID).Role != RoleOwner {
		return nil, ErrForbidden
	}
	var rest []Member
	for _, m := range t.Members {
		if m.UserID != targetID {
			rest = append(rest, m)
		}
	}
	if len(rest) == 0 {
		_, err := s.col.DeleteOne(ctx, bson.M{"_id": t.ID})
		s.dropInvitations(ctx, t.ID)
		return nil, err
	}
	// Le créateur s'en va : la tribu revient au membre le plus ancien
	if t.Member(targetID).Role == RoleOwner {
		oldest := 0
		for i := range rest {
			if rest[i].AddedAt.Before(rest[oldest].AddedAt) {
				oldest = i
			}
		}
		rest[oldest].Role = RoleOwner
	}
	if _, err := s.col.UpdateOne(ctx, bson.M{"_id": t.ID}, bson.M{"$set": bson.M{"members": rest, "updated_at": time.Now()}}); err != nil {
		return nil, err
	}
	if targetID == userID {
		return s.Get(ctx, userID)
	}
	return s.find(ctx, userID)
}

// SetSharing : le membre confirme (ou retire) le partage de son propre contenu.
func (s *Service) SetSharing(ctx context.Context, userID string, sharing bool) (*Tribe, error) {
	t, err := s.find(ctx, userID)
	if err != nil {
		return nil, err
	}
	if t == nil {
		return s.Get(ctx, userID) // seul : rien à partager
	}
	if _, err := s.col.UpdateOne(ctx,
		bson.M{"_id": t.ID, "members.user_id": userID},
		bson.M{"$set": bson.M{"members.$.sharing": sharing, "updated_at": time.Now()}}); err != nil {
		return nil, err
	}
	return s.find(ctx, userID)
}

func (s *Service) user(ctx context.Context, userID string) (*userDoc, error) {
	id, err := bson.ObjectIDFromHex(userID)
	if err != nil {
		return nil, ErrNotMember
	}
	var u userDoc
	if err := s.userCol.FindOne(ctx, bson.M{"_id": id}).Decode(&u); err != nil {
		return nil, err
	}
	return &u, nil
}

// Users retourne nom et email des comptes donnés (affichage des membres).
func (s *Service) Users(ctx context.Context, ids []string) map[string]userDoc {
	oids := make([]bson.ObjectID, 0, len(ids))
	for _, id := range ids {
		if oid, err := bson.ObjectIDFromHex(id); err == nil {
			oids = append(oids, oid)
		}
	}
	out := map[string]userDoc{}
	cur, err := s.userCol.Find(ctx, bson.M{"_id": bson.M{"$in": oids}})
	if err != nil {
		return out
	}
	var users []userDoc
	if cur.All(ctx, &users) == nil {
		for _, u := range users {
			out[u.ID.Hex()] = u
		}
	}
	return out
}

// Leave retire userID de sa tribu (suppression du compte). Sans tribu
// enregistrée, il n'y a rien à faire.
func (s *Service) Leave(ctx context.Context, userID string) error {
	_, err := s.RemoveMember(ctx, userID, userID)
	if errors.Is(err, ErrNotMember) {
		return nil
	}
	return err
}
