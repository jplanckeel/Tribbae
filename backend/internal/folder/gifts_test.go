package folder

import (
	"context"
	"errors"
	"strings"
	"testing"
	"time"

	tmail "github.com/tribbae/backend/internal/mail"
	"go.mongodb.org/mongo-driver/v2/bson"
	"go.mongodb.org/mongo-driver/v2/mongo"
	"go.mongodb.org/mongo-driver/v2/mongo/options"
)

type giftMails struct {
	confirm  []tmail.GiftConfirm
	taken    []tmail.GiftTaken
	released []tmail.GiftReleased
}

func (g *giftMails) GiftConfirm(m tmail.GiftConfirm)   { g.confirm = append(g.confirm, m) }
func (g *giftMails) GiftTaken(m tmail.GiftTaken)       { g.taken = append(g.taken, m) }
func (g *giftMails) GiftReleased(m tmail.GiftReleased) { g.released = append(g.released, m) }

// « Offrir une idée » : réserver sans compte (prénom + email confirmé) ou avec
// un compte, acheter, libérer ; personne ne voit qui offre quoi.
func TestGiftReservations(t *testing.T) {
	ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
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
	if _, err := db.Collection("gift_reservations").Indexes().CreateOne(ctx, mongo.IndexModel{
		Keys: bson.D{{Key: "link_id", Value: 1}}, Options: options.Index().SetUnique(true),
	}); err != nil {
		t.Fatal(err)
	}
	svc := NewService(db.Collection("folders"), db.Collection("links"), db.Collection("users"), "https://tribbae.example")
	svc.SetTribePeers(func(_ context.Context, userID string) []string {
		if userID == "dan" {
			return []string{"dan", "alice"}
		}
		return []string{userID}
	})
	mails := &giftMails{}
	svc.SetGifts(mails, func(_ context.Context, _, userID string) bool { return userID != "eve" })
	giftMail.Lock()
	giftMail.last = map[string]time.Time{}
	giftMail.Unlock()

	alice := bson.NewObjectID()
	bob := bson.NewObjectID()
	if _, err := db.Collection("users").InsertMany(ctx, []any{
		bson.M{"_id": alice, "email": "alice@exemple.fr", "display_name": "Alice"},
		bson.M{"_id": bob, "email": "bob@exemple.fr", "display_name": "Bob"},
	}); err != nil {
		t.Fatal(err)
	}
	fid := bson.NewObjectID()
	if _, err := db.Collection("folders").InsertOne(ctx, bson.M{"_id": fid, "owner_id": alice.Hex(), "name": "Les 7 ans de Léo", "visibility": "private"}); err != nil {
		t.Fatal(err)
	}
	share, _, err := svc.GenerateShareToken(ctx, fid.Hex(), alice.Hex())
	if err != nil {
		t.Fatal(err)
	}
	insert := func(doc bson.M) string {
		id := bson.NewObjectID()
		doc["_id"], doc["folder_id"] = id, fid.Hex()
		if _, err := db.Collection("links").InsertOne(ctx, doc); err != nil {
			t.Fatal(err)
		}
		return id.Hex()
	}
	kit := insert(bson.M{"owner_id": alice.Hex(), "title": "Kit d'astronomie", "category": giftCategory})
	lego := insert(bson.M{"owner_id": alice.Hex(), "title": "Lego", "category": giftCategory})
	book := insert(bson.M{"owner_id": alice.Hex(), "title": "Livre", "category": "LINK_CATEGORY_LIVRE"})

	if _, err := svc.ReserveSharedGift(ctx, share, kit, "", "Marc", "marc@exemple.fr"); !errors.Is(err, ErrNotAGiftList) {
		t.Fatalf("option off: %v", err)
	}
	if _, err := db.Collection("folders").UpdateByID(ctx, fid, bson.M{"$set": bson.M{"gift_reservations": true}}); err != nil {
		t.Fatal(err)
	}
	if _, err := svc.ReserveSharedGift(ctx, share, book, "", "Marc", "marc@exemple.fr"); !errors.Is(err, ErrNotFound) {
		t.Errorf("not a gift: %v", err)
	}
	for _, bad := range [][2]string{{"", "marc@exemple.fr"}, {"Marc", "pas-un-email"}} {
		if _, err := svc.ReserveSharedGift(ctx, share, kit, "", bad[0], bad[1]); !errors.Is(err, ErrInvalidInput) {
			t.Errorf("%v: %v", bad, err)
		}
	}

	// Sans compte : en attente jusqu'au lien reçu par email
	st, err := svc.ReserveSharedGift(ctx, share, kit, "", "Marc", "Marc@Exemple.fr")
	if err != nil || st != GiftPending || len(mails.confirm) != 1 {
		t.Fatalf("reserve by email: %q %v %d", st, err, len(mails.confirm))
	}
	link := mails.confirm[0].URL
	if !strings.HasPrefix(link, "https://tribbae.example/offrir#") || mails.confirm[0].Email != "marc@exemple.fr" {
		t.Fatalf("confirm email: %+v", mails.confirm[0])
	}
	token := strings.TrimPrefix(link, "https://tribbae.example/offrir#")
	if _, err := svc.ReserveSharedGift(ctx, share, kit, bob.Hex(), "", ""); !errors.Is(err, ErrGiftTaken) {
		t.Errorf("a pending reservation holds the gift: %v", err)
	}
	if got := svc.GiftStatuses(ctx, fid.Hex(), alice.Hex())[kit]; got != "reserved" {
		t.Errorf("creator sees %q", got)
	}
	if len(mails.taken) != 0 {
		t.Error("the creator hears only once it is confirmed")
	}
	v, err := svc.ManageGiftByToken(ctx, token, "confirm")
	if err != nil || v.Status != GiftReserved || v.Title != "Kit d'astronomie" || v.FolderName != "Les 7 ans de Léo" {
		t.Fatalf("confirm: %+v %v", v, err)
	}
	if len(mails.taken) != 1 || mails.taken[0].Email != "alice@exemple.fr" || strings.Contains(mails.taken[0].URL, kit) {
		t.Errorf("creator notified without detail: %+v", mails.taken)
	}
	if v, _ := svc.ManageGiftByToken(ctx, token, "bought"); v.Status != GiftBought {
		t.Errorf("bought: %+v", v)
	}
	if got := svc.GiftStatuses(ctx, fid.Hex(), bob.Hex())[kit]; got != "bought" {
		t.Errorf("others see %q", got)
	}
	if _, err := svc.ManageGiftByToken(ctx, "x"+token[1:], "release"); !errors.Is(err, ErrNotFound) {
		t.Errorf("wrong token: %v", err)
	}

	// Avec un compte : réservé tout de suite, depuis le lien ou l'app
	if st, err := svc.ReserveSharedGift(ctx, share, lego, bob.Hex(), "", ""); err != nil || st != GiftReserved {
		t.Fatalf("reserve as user: %q %v", st, err)
	}
	if got := svc.GiftStatuses(ctx, fid.Hex(), bob.Hex())[lego]; got != "mine" {
		t.Errorf("giver sees %q", got)
	}
	if _, err := svc.ManageMyGift(ctx, lego, "eve", "release"); !errors.Is(err, ErrNotFound) {
		t.Errorf("someone else's reservation: %v", err)
	}
	if v, err := svc.ManageMyGift(ctx, lego, bob.Hex(), "release"); err != nil || v.Status != "" {
		t.Fatalf("release: %+v %v", v, err)
	}
	if len(mails.released) != 1 || mails.released[0].Email != "marc@exemple.fr" || mails.released[0].Gift != "Lego" {
		t.Errorf("other givers told the gift is free: %+v", mails.released)
	}
	if _, err := svc.ManageMyGift(ctx, lego, "eve", "reserve"); !errors.Is(err, ErrNotFound) {
		t.Errorf("reserving an idea one cannot see: %v", err)
	}
	if v, err := svc.ManageMyGift(ctx, lego, bob.Hex(), "reserve"); err != nil || v.Status != GiftReserved {
		t.Fatalf("reserve from the app: %+v %v", v, err)
	}

	// Quota : 5 réservations par email et par liste en 24 h
	for i := 0; i < 4; i++ {
		id := insert(bson.M{"owner_id": alice.Hex(), "title": "Cadeau", "category": giftCategory})
		if _, err := svc.ReserveSharedGift(ctx, share, id, "", "Marc", "marc@exemple.fr"); err != nil {
			t.Fatal(err)
		}
	}
	extra := insert(bson.M{"owner_id": alice.Hex(), "title": "Encore", "category": giftCategory})
	if _, err := svc.ReserveSharedGift(ctx, share, extra, "", "Marc", "marc@exemple.fr"); !errors.Is(err, ErrGiftQuota) {
		t.Errorf("quota: %v", err)
	}

	if err := svc.ResetGifts(ctx, fid.Hex(), "eve"); !errors.Is(err, ErrNotFound) {
		t.Errorf("reset by a stranger: %v", err)
	}
	if err := svc.ResetGifts(ctx, fid.Hex(), alice.Hex()); err != nil {
		t.Fatal(err)
	}
	if n, _ := db.Collection("gift_reservations").CountDocuments(ctx, bson.M{}); n != 0 {
		t.Errorf("%d reservations left after reset", n)
	}
}
