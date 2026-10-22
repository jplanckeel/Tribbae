package link

import (
	"context"
	"testing"
	"time"

	"go.mongodb.org/mongo-driver/bson"
	"go.mongodb.org/mongo-driver/bson/primitive"
)

func TestImageBackfill(t *testing.T) {
	_, db, cleanup := setupTestDB(t)
	defer cleanup()
	ctx := context.Background()
	svc := NewService(db.Collection("links"), db.Collection("folders"))

	pages := map[string]string{"https://ok.example/page": "https://ok.example/image.jpg"}
	var asked []string
	prev := fetchImage
	fetchImage = func(_ context.Context, url string) string {
		asked = append(asked, url)
		return pages[url]
	}
	defer func() { fetchImage = prev }()

	insert := func(url, img string, checked *time.Time) primitive.ObjectID {
		doc := bson.M{"_id": primitive.NewObjectID(), "owner_id": "u", "title": url, "url": url, "image_url": img, "created_at": time.Now()}
		if checked != nil {
			doc["image_checked_at"] = *checked
		}
		if _, err := svc.col.InsertOne(ctx, doc); err != nil {
			t.Fatal(err)
		}
		return doc["_id"].(primitive.ObjectID)
	}
	recent := time.Now().Add(-time.Hour)
	old := time.Now().Add(-8 * 24 * time.Hour)
	okID := insert("https://ok.example/page", "", nil)
	blockedID := insert("https://bloque.example/page", "", nil)
	insert("https://deja.example/page", "https://deja.example/i.jpg", nil) // a déjà une image
	insert("", "", nil)                                                    // sans lien
	insert("https://recent.example/page", "", &recent)                     // tenté il y a 1 h
	insert("https://ancien.example/page", "", &old)                        // tenté il y a 8 jours

	filled, tried := svc.backfillBatch(ctx, 10)
	if filled != 1 || tried != 3 {
		t.Errorf("filled=%d tried=%d, want 1/3 (asked %v)", filled, tried, asked)
	}
	var got bson.M
	_ = svc.col.FindOne(ctx, bson.M{"_id": okID}).Decode(&got)
	if got["image_url"] != "https://ok.example/image.jpg" {
		t.Errorf("image non enregistrée : %v", got["image_url"])
	}
	_ = svc.col.FindOne(ctx, bson.M{"_id": blockedID}).Decode(&got)
	if _, ok := got["image_checked_at"]; !ok {
		t.Error("une tentative infructueuse doit être datée")
	}

	// Second passage : rien à refaire avant une semaine
	asked = nil
	if _, tried := svc.backfillBatch(ctx, 10); tried != 0 {
		t.Errorf("second passage : %d tentatives (%v), want 0", tried, asked)
	}
}
