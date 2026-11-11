package folder

import (
	"context"
	"crypto/rand"
	"crypto/sha256"
	"crypto/subtle"
	"encoding/hex"
	"errors"
	"time"

	"go.mongodb.org/mongo-driver/v2/bson"
	"go.mongodb.org/mongo-driver/v2/mongo"
	"go.mongodb.org/mongo-driver/v2/mongo/options"
)

// Liste de cadeaux : sur un dossier en mode « liste de cadeaux », les visiteurs
// du lien de partage (sans compte) disent « Je l'offre » sur une idée Cadeau.
// La réservation est anonyme : on garde seulement l'empreinte d'un secret remis
// au navigateur qui a réservé, le seul à pouvoir l'annuler. Tout le monde voit
// que le cadeau est pris, personne ne sait par qui.

var (
	// ErrGiftTaken : quelqu'un a déjà réservé ce cadeau
	ErrGiftTaken = errors.New("gift already reserved")
	// ErrNotAGiftList : le dossier n'accepte pas de réservations
	ErrNotAGiftList = errors.New("gift reservations are off for this folder")
)

const giftCategory = "LINK_CATEGORY_CADEAU"

type giftReservation struct {
	LinkID    string    `bson:"link_id"`
	FolderID  string    `bson:"folder_id"`
	OwnerID   string    `bson:"owner_id"` // propriétaire du dossier (suppression du compte)
	TokenHash string    `bson:"token_hash"`
	CreatedAt time.Time `bson:"created_at"`
}

func (s *Service) gifts() *mongo.Collection { return s.col.Database().Collection("gift_reservations") }

func hashGiftToken(token string) string {
	sum := sha256.Sum256([]byte(token))
	return hex.EncodeToString(sum[:])
}

// sharedGift retrouve, par le lien de partage, une idée Cadeau visible par ce lien.
func (s *Service) sharedGift(ctx context.Context, shareToken, linkID string) (*Folder, error) {
	if len(shareToken) != 32 {
		return nil, ErrNotFound
	}
	lid, err := bson.ObjectIDFromHex(linkID)
	if err != nil {
		return nil, ErrNotFound
	}
	var f Folder
	if err := s.col.FindOne(ctx, bson.M{"share_token": shareToken}).Decode(&f); err != nil {
		return nil, ErrNotFound
	}
	// Mêmes idées que GetByShareToken : celles du propriétaire et les publiques
	n, err := s.linkCol.CountDocuments(ctx, bson.M{
		"_id": lid, "folder_id": f.ID.Hex(), "category": giftCategory, "hidden": bson.M{"$ne": true},
		"$or": bson.A{bson.M{"owner_id": f.OwnerID}, bson.M{"visibility": "public"}},
	})
	if err != nil {
		return nil, err
	}
	if n == 0 {
		return nil, ErrNotFound
	}
	return &f, nil
}

// ReserveGift réserve un cadeau et renvoie le secret qui permettra de l'annuler.
func (s *Service) ReserveGift(ctx context.Context, shareToken, linkID string) (string, error) {
	f, err := s.sharedGift(ctx, shareToken, linkID)
	if err != nil {
		return "", err
	}
	if !f.GiftReservations {
		return "", ErrNotAGiftList
	}
	raw := make([]byte, 16)
	if _, err := rand.Read(raw); err != nil {
		return "", err
	}
	token := hex.EncodeToString(raw)
	_, err = s.gifts().InsertOne(ctx, giftReservation{
		LinkID: linkID, FolderID: f.ID.Hex(), OwnerID: f.OwnerID, TokenHash: hashGiftToken(token), CreatedAt: time.Now(),
	})
	if mongo.IsDuplicateKeyError(err) {
		return "", ErrGiftTaken
	}
	if err != nil {
		return "", err
	}
	return token, nil
}

// CancelGift annule une réservation : seul le secret remis à la réservation le permet.
func (s *Service) CancelGift(ctx context.Context, shareToken, linkID, cancelToken string) error {
	if _, err := s.sharedGift(ctx, shareToken, linkID); err != nil {
		return err
	}
	var r giftReservation
	if err := s.gifts().FindOne(ctx, bson.M{"link_id": linkID}).Decode(&r); err != nil {
		if errors.Is(err, mongo.ErrNoDocuments) {
			return ErrNotFound
		}
		return err
	}
	if subtle.ConstantTimeCompare([]byte(r.TokenHash), []byte(hashGiftToken(cancelToken))) != 1 {
		return ErrNotFound
	}
	_, err := s.gifts().DeleteOne(ctx, bson.M{"link_id": linkID, "token_hash": r.TokenHash})
	return err
}

// ResetGifts efface toutes les réservations d'un dossier (son propriétaire ou sa tribu).
func (s *Service) ResetGifts(ctx context.Context, folderID, userID string) error {
	id, err := bson.ObjectIDFromHex(folderID)
	if err != nil {
		return ErrNotFound
	}
	filter := s.ownedByTribe(ctx, userID)
	filter["_id"] = id
	if n, err := s.col.CountDocuments(ctx, filter); err != nil {
		return err
	} else if n == 0 {
		return ErrNotFound
	}
	_, err = s.gifts().DeleteMany(ctx, bson.M{"folder_id": folderID})
	return err
}

// ReservedGifts : les idées réservées d'un dossier.
func (s *Service) ReservedGifts(ctx context.Context, folderID string) map[string]bool {
	out := map[string]bool{}
	cur, err := s.gifts().Find(ctx, bson.M{"folder_id": folderID}, options.Find().SetProjection(bson.M{"link_id": 1}))
	if err != nil {
		return out
	}
	var rows []struct {
		LinkID string `bson:"link_id"`
	}
	if cur.All(ctx, &rows) == nil {
		for _, r := range rows {
			out[r.LinkID] = true
		}
	}
	return out
}
