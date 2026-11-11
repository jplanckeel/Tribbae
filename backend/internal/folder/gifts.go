package folder

import (
	"context"
	"errors"
	"strings"
	"sync"
	"time"
	"unicode/utf8"

	tmail "github.com/tribbae/backend/internal/mail"
	"go.mongodb.org/mongo-driver/v2/bson"
	"go.mongodb.org/mongo-driver/v2/mongo"
	"go.mongodb.org/mongo-driver/v2/mongo/options"
)

// Liste de cadeaux (« Offrir une idée ») : sur un dossier en liste de cadeaux,
// un proche dit « Je l'offre » sur une idée Cadeau pour éviter les doublons.
// Personne ne voit qui offre quoi : ni les autres offreurs, ni la créatrice de
// la liste, ni sa tribu. Tout le monde voit seulement « Réservé » ou « Offert ».
//
// Avec un compte, la réservation est immédiate et se gère dans l'app. Sans
// compte (lien de partage), on donne un prénom et un email : la réservation
// attend la confirmation par le lien reçu (2 h), lien qui sert ensuite à
// marquer le cadeau acheté ou à le libérer. Le prénom ne sert qu'aux emails.

var (
	// ErrGiftTaken : quelqu'un a déjà réservé ce cadeau
	ErrGiftTaken = errors.New("gift already reserved")
	// ErrNotAGiftList : le dossier n'accepte pas de réservations
	ErrNotAGiftList = errors.New("gift reservations are off for this folder")
	// ErrGiftQuota : trop de réservations pour cet email sur cette liste en 24 h
	ErrGiftQuota = errors.New("too many gift reservations today")
)

const (
	giftCategory = "LINK_CATEGORY_CADEAU"

	GiftPending  = "pending"  // attend la confirmation par email
	GiftReserved = "reserved" // réservé
	GiftBought   = "bought"   // acheté

	giftPendingTTL   = 2 * time.Hour
	giftDailyByEmail = 5
	giftNameMax      = 40
)

// GiftNotifier : les emails de la liste de cadeaux.
type GiftNotifier interface {
	GiftConfirm(tmail.GiftConfirm)
	GiftTaken(tmail.GiftTaken)
	GiftReleased(tmail.GiftReleased)
}

// SetGifts branche les emails et la visibilité des idées (réserver depuis l'app).
func (s *Service) SetGifts(n GiftNotifier, canView func(ctx context.Context, linkID, userID string) bool) {
	s.giftNotify, s.giftCanView = n, canView
}

type giftReservation struct {
	ID          bson.ObjectID `bson:"_id,omitempty"`
	LinkID      string        `bson:"link_id"`
	FolderID    string        `bson:"folder_id"`
	OwnerID     string        `bson:"owner_id"` // propriétaire du dossier
	Status      string        `bson:"status"`
	UserID      string        `bson:"user_id,omitempty"` // réservé avec un compte
	Name        string        `bson:"name,omitempty"`    // sans compte : prénom (emails seulement)
	Email       string        `bson:"email,omitempty"`   // sans compte
	TokenHash   string        `bson:"token_hash,omitempty"`
	ExpiresAt   *time.Time    `bson:"expires_at,omitempty"` // en attente : effacée après (index TTL)
	CreatedAt   time.Time     `bson:"created_at"`
	ConfirmedAt *time.Time    `bson:"confirmed_at,omitempty"`
}

func (s *Service) gifts() *mongo.Collection { return s.col.Database().Collection("gift_reservations") }

type giftLink struct {
	ID       bson.ObjectID `bson:"_id"`
	Title    string        `bson:"title"`
	FolderID string        `bson:"folder_id"`
	ImageURL string        `bson:"image_url"`
	Price    string        `bson:"price"`
}

// sharedGift : une idée Cadeau visible par le lien de partage.
func (s *Service) sharedGift(ctx context.Context, shareToken, linkID string) (*Folder, *giftLink, error) {
	if len(shareToken) != 32 {
		return nil, nil, ErrNotFound
	}
	var f Folder
	if err := s.col.FindOne(ctx, bson.M{"share_token": shareToken}).Decode(&f); err != nil {
		return nil, nil, ErrNotFound
	}
	l, err := s.giftIn(ctx, &f, linkID, bson.M{"$or": bson.A{bson.M{"owner_id": f.OwnerID}, bson.M{"visibility": "public"}}})
	return &f, l, err
}

func (s *Service) giftIn(ctx context.Context, f *Folder, linkID string, extra bson.M) (*giftLink, error) {
	lid, err := bson.ObjectIDFromHex(linkID)
	if err != nil {
		return nil, ErrNotFound
	}
	filter := bson.M{"_id": lid, "folder_id": f.ID.Hex(), "category": giftCategory, "hidden": bson.M{"$ne": true}}
	for k, v := range extra {
		filter[k] = v
	}
	var l giftLink
	if err := s.linkCol.FindOne(ctx, filter).Decode(&l); err != nil {
		return nil, ErrNotFound
	}
	return &l, nil
}

// clearExpired retire une réservation en attente expirée (l'index TTL passe
// toutes les minutes environ : on n'attend pas pour libérer le cadeau).
func (s *Service) clearExpired(ctx context.Context, linkID string) {
	_, _ = s.gifts().DeleteOne(ctx, bson.M{"link_id": linkID, "status": GiftPending, "expires_at": bson.M{"$lt": time.Now()}})
}

// ReserveSharedGift : « Je l'offre » depuis le lien de partage. Avec un compte
// (userID non vide), c'est réservé ; sinon la réservation attend la
// confirmation envoyée à email. Renvoie le statut obtenu.
func (s *Service) ReserveSharedGift(ctx context.Context, shareToken, linkID, userID, name, email string) (string, error) {
	f, l, err := s.sharedGift(ctx, shareToken, linkID)
	if err != nil {
		return "", err
	}
	if userID != "" {
		return GiftReserved, s.reserveAsUser(ctx, f, l, userID)
	}
	return s.reserveByEmail(ctx, f, l, name, email)
}

func (s *Service) reserveByEmail(ctx context.Context, f *Folder, l *giftLink, name, email string) (string, error) {
	if !f.GiftReservations {
		return "", ErrNotAGiftList
	}
	name = strings.TrimSpace(name)
	email = strings.ToLower(strings.TrimSpace(email))
	if name == "" || utf8.RuneCountInString(name) > giftNameMax || !validEmail(email) {
		return "", ErrInvalidInput
	}
	n, err := s.gifts().CountDocuments(ctx, bson.M{"folder_id": f.ID.Hex(), "email": email, "created_at": bson.M{"$gte": time.Now().Add(-24 * time.Hour)}})
	if err != nil {
		return "", err
	}
	if n >= giftDailyByEmail {
		return "", ErrGiftQuota
	}
	token, err := newToken()
	if err != nil {
		return "", err
	}
	now := time.Now()
	exp := now.Add(giftPendingTTL)
	s.clearExpired(ctx, l.ID.Hex())
	_, err = s.gifts().InsertOne(ctx, giftReservation{
		LinkID: l.ID.Hex(), FolderID: f.ID.Hex(), OwnerID: f.OwnerID, Status: GiftPending,
		Name: name, Email: email, TokenHash: hashToken(token), ExpiresAt: &exp, CreatedAt: now,
	})
	if mongo.IsDuplicateKeyError(err) {
		return "", ErrGiftTaken
	}
	if err != nil {
		return "", err
	}
	if s.giftNotify != nil {
		s.giftNotify.GiftConfirm(tmail.GiftConfirm{
			Email: email, Name: name, Gift: l.Title, List: f.Name,
			URL: strings.TrimRight(s.baseURL, "/") + "/offrir#" + token, Validity: giftPendingTTL,
		})
	}
	return GiftPending, nil
}

func (s *Service) reserveAsUser(ctx context.Context, f *Folder, l *giftLink, userID string) error {
	if !f.GiftReservations {
		return ErrNotAGiftList
	}
	s.clearExpired(ctx, l.ID.Hex())
	now := time.Now()
	_, err := s.gifts().InsertOne(ctx, giftReservation{
		LinkID: l.ID.Hex(), FolderID: f.ID.Hex(), OwnerID: f.OwnerID, Status: GiftReserved,
		UserID: userID, CreatedAt: now, ConfirmedAt: &now,
	})
	if mongo.IsDuplicateKeyError(err) {
		return ErrGiftTaken
	}
	if err != nil {
		return err
	}
	s.giftTaken(ctx, f)
	return nil
}

// GiftView : une réservation vue par son offreur (page /offrir ou app).
type GiftView struct {
	LinkID, Title, ImageURL, Price string
	FolderName, ShareToken         string
	KeyDate                        int64
	Status                         string
}

// ManageGiftByToken : l'offreur sans compte, depuis le lien reçu par email.
// action : "confirm" (ouvrir le lien confirme), "bought", "unbought", "release".
func (s *Service) ManageGiftByToken(ctx context.Context, token, action string) (*GiftView, error) {
	if len(token) < 32 {
		return nil, ErrNotFound
	}
	var r giftReservation
	if err := s.gifts().FindOne(ctx, bson.M{"token_hash": hashToken(token)}).Decode(&r); err != nil {
		return nil, ErrNotFound
	}
	if r.Status == GiftPending && r.ExpiresAt != nil && r.ExpiresAt.Before(time.Now()) {
		return nil, ErrNotFound
	}
	return s.applyGift(ctx, r, action)
}

// ManageMyGift : l'offreur avec un compte, depuis l'app. action "reserve"
// réserve une idée Cadeau qu'il peut voir dans un dossier en liste de cadeaux.
func (s *Service) ManageMyGift(ctx context.Context, linkID, userID, action string) (*GiftView, error) {
	if action == "reserve" {
		lid, err := bson.ObjectIDFromHex(linkID)
		if err != nil {
			return nil, ErrNotFound
		}
		var l giftLink
		if err := s.linkCol.FindOne(ctx, bson.M{"_id": lid, "category": giftCategory, "hidden": bson.M{"$ne": true}}).Decode(&l); err != nil {
			return nil, ErrNotFound
		}
		if s.giftCanView == nil || !s.giftCanView(ctx, linkID, userID) {
			return nil, ErrNotFound
		}
		fid, err := bson.ObjectIDFromHex(l.FolderID)
		if err != nil {
			return nil, ErrNotAGiftList
		}
		var f Folder
		if err := s.col.FindOne(ctx, bson.M{"_id": fid}).Decode(&f); err != nil {
			return nil, ErrNotAGiftList
		}
		if err := s.reserveAsUser(ctx, &f, &l, userID); err != nil {
			return nil, err
		}
		return s.view(ctx, giftReservation{LinkID: linkID, FolderID: f.ID.Hex(), Status: GiftReserved})
	}
	var r giftReservation
	if err := s.gifts().FindOne(ctx, bson.M{"link_id": linkID, "user_id": userID}).Decode(&r); err != nil {
		return nil, ErrNotFound
	}
	return s.applyGift(ctx, r, action)
}

func (s *Service) applyGift(ctx context.Context, r giftReservation, action string) (*GiftView, error) {
	switch action {
	case "", "view":
	case "confirm":
		if r.Status == GiftPending {
			now := time.Now()
			if _, err := s.gifts().UpdateOne(ctx, bson.M{"_id": r.ID, "status": GiftPending},
				bson.M{"$set": bson.M{"status": GiftReserved, "confirmed_at": now}, "$unset": bson.M{"expires_at": ""}}); err != nil {
				return nil, err
			}
			r.Status = GiftReserved
			if f, err := s.folderByID(ctx, r.FolderID); err == nil {
				s.giftTaken(ctx, f)
			}
		}
	case "bought", "unbought":
		if r.Status == GiftPending {
			return nil, ErrInvalidInput
		}
		st := GiftBought
		if action == "unbought" {
			st = GiftReserved
		}
		if _, err := s.gifts().UpdateOne(ctx, bson.M{"_id": r.ID}, bson.M{"$set": bson.M{"status": st}}); err != nil {
			return nil, err
		}
		r.Status = st
	case "release":
		if _, err := s.gifts().DeleteOne(ctx, bson.M{"_id": r.ID}); err != nil {
			return nil, err
		}
		if r.Status != GiftPending {
			s.giftReleased(ctx, r)
		}
		r.Status = ""
	default:
		return nil, ErrInvalidInput
	}
	return s.view(ctx, r)
}

func (s *Service) folderByID(ctx context.Context, id string) (*Folder, error) {
	oid, err := bson.ObjectIDFromHex(id)
	if err != nil {
		return nil, ErrNotFound
	}
	var f Folder
	if err := s.col.FindOne(ctx, bson.M{"_id": oid}).Decode(&f); err != nil {
		return nil, ErrNotFound
	}
	return &f, nil
}

func (s *Service) view(ctx context.Context, r giftReservation) (*GiftView, error) {
	v := &GiftView{LinkID: r.LinkID, Status: r.Status}
	if lid, err := bson.ObjectIDFromHex(r.LinkID); err == nil {
		var l giftLink
		if s.linkCol.FindOne(ctx, bson.M{"_id": lid}).Decode(&l) == nil {
			v.Title, v.ImageURL, v.Price = l.Title, l.ImageURL, l.Price
		}
	}
	if f, err := s.folderByID(ctx, r.FolderID); err == nil {
		v.FolderName, v.ShareToken, v.KeyDate = f.Name, f.ShareToken, f.KeyDate
	}
	return v, nil
}

// --- Emails : « un cadeau a trouvé preneur » (créatrice) et « un cadeau s'est
// libéré » (autres offreurs). Aucun ne dit qui offre quoi. ---

var giftMail = struct {
	sync.Mutex
	last map[string]time.Time
}{last: map[string]time.Time{}}

// throttled : au plus un email par clé et par fenêtre.
func throttled(key string, window time.Duration) bool {
	giftMail.Lock()
	defer giftMail.Unlock()
	now := time.Now()
	if t, ok := giftMail.last[key]; ok && now.Sub(t) < window {
		return true
	}
	giftMail.last[key] = now
	return false
}

func (s *Service) giftTaken(ctx context.Context, f *Folder) {
	if s.giftNotify == nil || throttled("taken|"+f.ID.Hex(), time.Hour) {
		return
	}
	email, name := s.userEmail(ctx, f.OwnerID)
	if email == "" {
		return
	}
	s.giftNotify.GiftTaken(tmail.GiftTaken{
		Email: email, Name: name, List: f.Name, URL: strings.TrimRight(s.baseURL, "/") + "/dossiers/" + f.ID.Hex(),
	})
}

func (s *Service) giftReleased(ctx context.Context, released giftReservation) {
	if s.giftNotify == nil {
		return
	}
	f, err := s.folderByID(ctx, released.FolderID)
	if err != nil {
		return
	}
	var title string
	if lid, err := bson.ObjectIDFromHex(released.LinkID); err == nil {
		var l giftLink
		if s.linkCol.FindOne(ctx, bson.M{"_id": lid}).Decode(&l) == nil {
			title = l.Title
		}
	}
	cur, err := s.gifts().Find(ctx, bson.M{"folder_id": released.FolderID, "status": bson.M{"$in": bson.A{GiftReserved, GiftBought}}})
	if err != nil {
		return
	}
	var others []giftReservation
	if cur.All(ctx, &others) != nil {
		return
	}
	url := strings.TrimRight(s.baseURL, "/") + "/dossiers/" + f.ID.Hex()
	if f.ShareToken != "" {
		url = strings.TrimRight(s.baseURL, "/") + "/share/" + f.ShareToken
	}
	seen := map[string]bool{}
	for _, o := range others {
		email, name := o.Email, o.Name
		if o.UserID != "" {
			email, name = s.userEmail(ctx, o.UserID)
		}
		same := (o.UserID != "" && o.UserID == released.UserID) || (o.Email != "" && o.Email == released.Email)
		if email == "" || same || seen[email] || throttled("released|"+email+"|"+released.LinkID, 24*time.Hour) {
			continue
		}
		seen[email] = true
		s.giftNotify.GiftReleased(tmail.GiftReleased{Email: email, Name: name, Gift: title, List: f.Name, URL: url})
	}
}

func (s *Service) userEmail(ctx context.Context, userID string) (string, string) {
	oid, err := bson.ObjectIDFromHex(userID)
	if err != nil {
		return "", ""
	}
	var u struct {
		Email       string `bson:"email"`
		DisplayName string `bson:"display_name"`
	}
	if s.userCol.FindOne(ctx, bson.M{"_id": oid}, options.FindOne().SetProjection(bson.M{"email": 1, "display_name": 1})).Decode(&u) != nil {
		return "", ""
	}
	return u.Email, u.DisplayName
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

// GiftStatuses : statut de chaque cadeau réservé d'un dossier, vu par viewerID
// ("mine" / "mine_bought" pour ses propres réservations, sinon "reserved" /
// "bought" ; une réservation en attente compte comme réservée).
func (s *Service) GiftStatuses(ctx context.Context, folderID, viewerID string) map[string]string {
	out := map[string]string{}
	cur, err := s.gifts().Find(ctx, bson.M{"folder_id": folderID}, options.Find().SetProjection(bson.M{"link_id": 1, "status": 1, "user_id": 1, "expires_at": 1}))
	if err != nil {
		return out
	}
	var rows []giftReservation
	if cur.All(ctx, &rows) == nil {
		for _, r := range rows {
			if st := GiftStatusFor(r.Status, r.UserID, viewerID, r.ExpiresAt); st != "" {
				out[r.LinkID] = st
			}
		}
	}
	return out
}

// GiftStatusFor : statut affiché d'une réservation pour un lecteur.
func GiftStatusFor(status, ownerUserID, viewerID string, expiresAt *time.Time) string {
	if status == GiftPending && expiresAt != nil && expiresAt.Before(time.Now()) {
		return ""
	}
	mine := viewerID != "" && ownerUserID == viewerID
	switch {
	case status == GiftBought && mine:
		return "mine_bought"
	case status == GiftBought:
		return "bought"
	case mine:
		return "mine"
	default:
		return "reserved"
	}
}
