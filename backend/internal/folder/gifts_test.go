package folder

import (
	"context"
	"errors"
	"testing"
	"time"

	"go.mongodb.org/mongo-driver/v2/bson"
	"go.mongodb.org/mongo-driver/v2/mongo"
	"go.mongodb.org/mongo-driver/v2/mongo/options"
)

// Liste de cadeaux : réserver depuis le lien, anonymement ; seul le navigateur
// qui a réservé annule ; la tribu remet à zéro.
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
		if userID == "alice" || userID == "dan" {
			return []string{"alice", "dan"}
		}
		return []string{userID}
	})

	fid := bson.NewObjectID()
	if _, err := db.Collection("folders").InsertOne(ctx, bson.M{"_id": fid, "owner_id": "alice", "name": "Noël de Léo", "visibility": "private"}); err != nil {
		t.Fatal(err)
	}
	token, _, err := svc.GenerateShareToken(ctx, fid.Hex(), "alice")
	if err != nil {
		t.Fatal(err)
	}
	insert := func(doc bson.M) string {
		id := bson.NewObjectID()
		doc["_id"] = id
		doc["folder_id"] = fid.Hex()
		if _, err := db.Collection("links").InsertOne(ctx, doc); err != nil {
			t.Fatal(err)
		}
		return id.Hex()
	}
	lego := insert(bson.M{"owner_id": "alice", "title": "Lego", "category": giftCategory})
	book := insert(bson.M{"owner_id": "alice", "title": "Livre", "category": "LINK_CATEGORY_LIVRE"})
	bobs := insert(bson.M{"owner_id": "bob", "title": "Privé de Bob", "category": giftCategory, "visibility": "private"})

	if _, err := svc.ReserveGift(ctx, token, lego); !errors.Is(err, ErrNotAGiftList) {
		t.Fatalf("option off: %v", err)
	}
	if _, err := db.Collection("folders").UpdateByID(ctx, fid, bson.M{"$set": bson.M{"gift_reservations": true}}); err != nil {
		t.Fatal(err)
	}
	for name, id := range map[string]string{"not a gift": book, "not shared by the link": bobs, "unknown": bson.NewObjectID().Hex()} {
		if _, err := svc.ReserveGift(ctx, token, id); !errors.Is(err, ErrNotFound) {
			t.Errorf("%s: %v", name, err)
		}
	}
	if _, err := svc.ReserveGift(ctx, "x"+token[1:], lego); !errors.Is(err, ErrNotFound) {
		t.Errorf("wrong share token: %v", err)
	}

	secret, err := svc.ReserveGift(ctx, token, lego)
	if err != nil || len(secret) != 32 {
		t.Fatalf("reserve: %q %v", secret, err)
	}
	if _, err := svc.ReserveGift(ctx, token, lego); !errors.Is(err, ErrGiftTaken) {
		t.Errorf("second reservation: %v", err)
	}
	if !svc.ReservedGifts(ctx, fid.Hex())[lego] {
		t.Error("the gift should show as reserved")
	}
	var stored bson.M
	_ = db.Collection("gift_reservations").FindOne(ctx, bson.M{"link_id": lego}).Decode(&stored)
	if stored["token_hash"] == secret {
		t.Error("the cancel secret must be stored hashed")
	}

	if err := svc.CancelGift(ctx, token, lego, "nope"); !errors.Is(err, ErrNotFound) {
		t.Errorf("cancel with a wrong secret: %v", err)
	}
	if err := svc.CancelGift(ctx, token, lego, secret); err != nil {
		t.Fatal(err)
	}
	if svc.ReservedGifts(ctx, fid.Hex())[lego] {
		t.Error("cancelled gift still reserved")
	}

	if _, err := svc.ReserveGift(ctx, token, lego); err != nil {
		t.Fatal(err)
	}
	if err := svc.ResetGifts(ctx, fid.Hex(), "eve"); !errors.Is(err, ErrNotFound) {
		t.Errorf("reset by a stranger: %v", err)
	}
	if err := svc.ResetGifts(ctx, fid.Hex(), "dan"); err != nil {
		t.Fatalf("reset by the tribe: %v", err)
	}
	if len(svc.ReservedGifts(ctx, fid.Hex())) != 0 {
		t.Error("reset left reservations")
	}

	if _, err := svc.ReserveGift(ctx, token, lego); err != nil {
		t.Fatal(err)
	}
	if err := svc.Delete(ctx, fid.Hex(), "alice"); err != nil {
		t.Fatal(err)
	}
	if n, _ := db.Collection("gift_reservations").CountDocuments(ctx, bson.M{}); n != 0 {
		t.Errorf("%d reservations left after deleting the folder", n)
	}
}
