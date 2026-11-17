package link

import (
	"context"
	"fmt"
	"strconv"
	"sync"
	"testing"
	"time"

	"go.mongodb.org/mongo-driver/v2/bson"
)

func TestExpiringImage(t *testing.T) {
	cases := map[string]bool{
		"https://scontent-cdg4-1.cdninstagram.com/v/t51.2885-15/1.jpg?oh=a&oe=6740A1B2": true,
		"https://instagram.fcdg1-1.fna.fbcdn.net/v/t51/1.jpg":                            true,
		"https://p16-sign-va.tiktokcdn.com/obj/1.jpeg?x-expires=1700000000":              true,
		"https://p16-pu-sign-no.tiktokcdn-eu.com/1.jpeg":                                 true,
		"https://static.cdninstagram.com/rsrc.php/yj/r/f01UdNyJjJj.webp":                 false,
		"https://tribbae.fr/v1/photos/0123456789abcdef0123456789abcdef":                  false,
		"https://www.marmiton.org/image.jpg":                                             false,
		"https://evilcdninstagram.com/1.jpg":                                             false,
		"":                                                                               false,
	}
	for raw, want := range cases {
		if got := ExpiringImage(raw); got != want {
			t.Errorf("ExpiringImage(%q) = %v, want %v", raw, got, want)
		}
	}
}

func TestImageExpired(t *testing.T) {
	now := time.Unix(1_700_000_000, 0)
	past, future := strconv.FormatInt(now.Unix()-60, 16), strconv.FormatInt(now.Unix()+60, 16)
	cases := map[string]bool{
		"https://scontent.cdninstagram.com/1.jpg?oe=" + past:                     true,
		"https://scontent.cdninstagram.com/1.jpg?oe=" + future:                   false,
		fmt.Sprintf("https://p16.tiktokcdn.com/1.jpeg?x-expires=%d", now.Unix()): true,
		"https://scontent.cdninstagram.com/1.jpg":                                false,
		"https://scontent.cdninstagram.com/1.jpg?oe=zz":                          false,
	}
	for raw, want := range cases {
		if got := imageExpired(raw, now); got != want {
			t.Errorf("imageExpired(%q) = %v, want %v", raw, got, want)
		}
	}
}

// Copie des images qui expirent : à la création, au rattrapage des idées
// existantes, et quand l'image a déjà disparu (la page est relue).
func TestImageMirror(t *testing.T) {
	_, db, cleanup := setupTestDB(t)
	defer cleanup()
	ctx := context.Background()
	svc := NewService(db.Collection("links"), db.Collection("folders"))

	var mu sync.Mutex
	var copied []string
	gone := map[string]bool{}
	svc.SetImageMirror(func(_ context.Context, owner, img string) (string, error) {
		mu.Lock()
		defer mu.Unlock()
		if gone[img] {
			return "", ErrImageGone
		}
		copied = append(copied, owner+" "+img)
		return fmt.Sprintf("https://tribbae.fr/v1/photos/%032d", len(copied)), nil
	})
	prev := fetchImage
	fetchImage = func(_ context.Context, url string) string {
		return "https://scontent.cdninstagram.com/fresh.jpg?oe=" + strconv.FormatInt(time.Now().Add(time.Hour).Unix(), 16)
	}
	defer func() { fetchImage = prev }()
	get := func(id bson.ObjectID) bson.M {
		var got bson.M
		_ = svc.col.FindOne(ctx, bson.M{"_id": id}).Decode(&got)
		return got
	}
	future := strconv.FormatInt(time.Now().Add(time.Hour).Unix(), 16)

	// À la création : copiée en arrière-plan, l'adresse d'origine retenue
	insta := "https://scontent.cdninstagram.com/a.jpg?oe=" + future
	l, err := svc.Create(ctx, "u1", &Link{Title: "Post", URL: "https://www.instagram.com/p/abc/", Category: "LINK_CATEGORY_IDEE", ImageURL: insta})
	if err != nil {
		t.Fatal(err)
	}
	deadline := time.Now().Add(5 * time.Second)
	for get(l.ID)["image_source"] == nil && time.Now().Before(deadline) {
		time.Sleep(20 * time.Millisecond)
	}
	got := get(l.ID)
	if got["image_url"] != "https://tribbae.fr/v1/photos/00000000000000000000000000000001" || got["image_source"] != insta || got["image_expiring"] != nil {
		t.Fatalf("copie à la création : %v", got)
	}

	// Un client qui renvoie l'adresse d'origine garde la copie
	if _, err := svc.Update(ctx, l.ID.Hex(), "u1", &Link{Title: "Post modifié", URL: "https://www.instagram.com/p/abc/", Category: "LINK_CATEGORY_IDEE", ImageURL: insta}); err != nil {
		t.Fatal(err)
	}
	if got := get(l.ID); got["image_url"] != "https://tribbae.fr/v1/photos/00000000000000000000000000000001" {
		t.Errorf("la copie doit rester : %v", got["image_url"])
	}

	// Idées existantes : marquées au démarrage, puis copiées par lot
	old := "https://scontent.cdninstagram.com/old.jpg?oe=" + future
	expired := "https://scontent.cdninstagram.com/expired.jpg?oe=" + strconv.FormatInt(time.Now().Add(-time.Hour).Unix(), 16)
	deleted := "https://scontent.cdninstagram.com/deleted.jpg?oe=" + future
	gone[deleted] = true
	insert := func(img, url string) bson.ObjectID {
		id := bson.NewObjectID()
		if _, err := svc.col.InsertOne(ctx, bson.M{"_id": id, "owner_id": "u2", "title": "x", "url": url, "image_url": img, "created_at": time.Now()}); err != nil {
			t.Fatal(err)
		}
		return id
	}
	oldID := insert(old, "https://www.instagram.com/p/old/")
	expiredID := insert(expired, "https://www.instagram.com/p/expired/")
	deletedID := insert(deleted, "")
	otherID := insert("https://www.marmiton.org/i.jpg", "https://www.marmiton.org/r")
	svc.markExpiringImages(ctx)
	if get(otherID)["image_expiring"] != nil {
		t.Error("une image ordinaire ne doit pas être marquée")
	}
	if n, tried := svc.mirrorBatch(ctx, 10); tried != 3 || n != 1 {
		t.Errorf("copiées %d sur %d, want 1 sur 3", n, tried)
	}
	if got := get(oldID); got["image_source"] != old {
		t.Errorf("ancienne idée non copiée : %v", got)
	}
	// Expirée : retirée, la page relue donne une image fraîche, copiée à son tour
	if got := get(expiredID); got["image_source"] == nil || got["image_source"] == expired {
		t.Errorf("image expirée non remplacée : %v", got)
	}
	// Disparue, sans page à relire : retirée, datée pour ne pas recommencer
	if got := get(deletedID); got["image_url"] != "" || got["image_checked_at"] == nil || got["image_expiring"] != nil {
		t.Errorf("image disparue : %v", got)
	}
	if n, tried := svc.mirrorBatch(ctx, 10); tried != 0 || n != 0 {
		t.Errorf("rien ne doit rester à copier : %d/%d", n, tried)
	}
	mu.Lock()
	defer mu.Unlock()
	if copied[0] != "u1 "+insta || copied[1] != "u2 "+old {
		t.Errorf("copie au nom de l'auteur : %v", copied)
	}
}
