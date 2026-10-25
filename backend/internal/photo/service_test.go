package photo

import (
	"context"
	"io"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"

	"go.mongodb.org/mongo-driver/v2/bson"
	"go.mongodb.org/mongo-driver/v2/mongo"
	"go.mongodb.org/mongo-driver/v2/mongo/options"
)

func setupTestDB(t *testing.T) *mongo.Database {
	t.Helper()
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	client, err := mongo.Connect(options.Client().ApplyURI("mongodb://localhost:27017").SetServerSelectionTimeout(3 * time.Second))
	if err != nil {
		t.Skipf("Skipping test: MongoDB unavailable: %v", err)
	}
	if err := client.Ping(ctx, nil); err != nil {
		t.Skipf("Skipping test: MongoDB unavailable: %v", err)
	}
	db := client.Database("tribbae_test_" + bson.NewObjectID().Hex())
	t.Cleanup(func() {
		_ = db.Drop(context.Background())
		_ = client.Disconnect(context.Background())
	})
	return db
}

func TestUploadServeAndDelete(t *testing.T) {
	db := setupTestDB(t)
	svc := NewService(db)
	h := NewHandler(svc, func(r *http.Request) (string, error) { return "user-1", nil })

	req := httptest.NewRequest(http.MethodPost, UploadPath, strings.NewReader(string(encodeJPEG(t, halves(64, 48)))))
	req.Host = "tribbae.fr"
	req.Header.Set("X-Forwarded-Proto", "https")
	rec := httptest.NewRecorder()
	h.ServeHTTP(rec, req)
	if rec.Code != http.StatusCreated {
		t.Fatalf("upload: %d %s", rec.Code, rec.Body)
	}
	body := rec.Body.String()
	const prefix = `{"url":"https://tribbae.fr/v1/photos/`
	if !strings.HasPrefix(body, prefix) {
		t.Fatalf("url: %s", body)
	}
	token := body[len(prefix) : len(prefix)+32]
	if !ValidToken(token) {
		t.Fatalf("token %q", token)
	}

	rec = httptest.NewRecorder()
	h.ServeHTTP(rec, httptest.NewRequest(http.MethodGet, Path+token, nil))
	if rec.Code != http.StatusOK || rec.Header().Get("Content-Type") != "image/jpeg" {
		t.Fatalf("serve: %d %v", rec.Code, rec.Header())
	}
	if b := decode(t, rec.Body.Bytes()).Bounds(); b.Dx() != 64 {
		t.Fatalf("served size %v", b.Size())
	}

	for _, p := range []string{Path + "0123456789abcdef0123456789abcdef", Path + "../x", Path + ""} {
		rec = httptest.NewRecorder()
		h.ServeHTTP(rec, httptest.NewRequest(http.MethodGet, p, nil))
		if rec.Code != http.StatusNotFound {
			t.Errorf("%s: %d, want 404", p, rec.Code)
		}
	}

	if err := DeleteOwnedBy(context.Background(), db, "user-1"); err != nil {
		t.Fatal(err)
	}
	if _, err := svc.Open(context.Background(), token); err != ErrNotFound {
		t.Fatalf("after delete: %v", err)
	}
	if n, _ := db.Collection(Bucket+".chunks").CountDocuments(context.Background(), bson.M{}); n != 0 {
		t.Fatalf("%d chunks left", n)
	}
}

func TestUploadRefusesAnonymousAndNonImages(t *testing.T) {
	db := setupTestDB(t)
	svc := NewService(db)
	anon := NewHandler(svc, func(r *http.Request) (string, error) { return "", io.EOF })
	rec := httptest.NewRecorder()
	anon.ServeHTTP(rec, httptest.NewRequest(http.MethodPost, UploadPath, strings.NewReader("x")))
	if rec.Code != http.StatusUnauthorized {
		t.Fatalf("anonymous: %d", rec.Code)
	}
	h := NewHandler(svc, func(r *http.Request) (string, error) { return "user-1", nil })
	rec = httptest.NewRecorder()
	h.ServeHTTP(rec, httptest.NewRequest(http.MethodPost, UploadPath, strings.NewReader("<html>")))
	if rec.Code != http.StatusBadRequest {
		t.Fatalf("not an image: %d", rec.Code)
	}
}

func TestCleanupKeepsUsedAndRecentPhotos(t *testing.T) {
	db := setupTestDB(t)
	ctx := context.Background()
	svc := NewService(db)
	jpg := encodeJPEG(t, halves(16, 16))
	save := func() string {
		tok, err := svc.Save(ctx, "user-1", jpg)
		if err != nil {
			t.Fatal(err)
		}
		return tok
	}
	inIdea, inFolder, unused := save(), save(), save()
	_, _ = db.Collection("links").InsertOne(ctx, bson.M{"image_url": "https://tribbae.fr/v1/photos/" + inIdea})
	_, _ = db.Collection("folders").InsertOne(ctx, bson.M{"banner_url": "http://10.0.2.2:8080/v1/photos/" + inFolder})

	// Toutes récentes : rien n'est effacé
	if n, err := svc.cleanup(ctx, time.Now().Add(-time.Hour)); err != nil || n != 0 {
		t.Fatalf("recent: %d %v", n, err)
	}
	n, err := svc.cleanup(ctx, time.Now().Add(time.Minute))
	if err != nil || n != 1 {
		t.Fatalf("cleanup: %d %v, want 1", n, err)
	}
	for tok, want := range map[string]bool{inIdea: true, inFolder: true, unused: false} {
		_, err := svc.Open(ctx, tok)
		if (err == nil) != want {
			t.Errorf("%s: open err %v, want kept=%v", tok, err, want)
		}
	}
}
