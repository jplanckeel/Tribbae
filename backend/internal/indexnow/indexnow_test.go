package indexnow

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"
)

func TestDisabledWithoutValidKey(t *testing.T) {
	for _, k := range []string{"", "short", "avec des espaces!"} {
		if c := New("https://tribbae.fr", k); c != nil {
			t.Errorf("clé %q acceptée", k)
		}
	}
	var c *Client
	c.Notify("/links/a") // sans clé : rien, sans erreur
}

func TestBatchesChangedPages(t *testing.T) {
	var got map[string]any
	calls := 0
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		calls++
		_ = json.NewDecoder(r.Body).Decode(&got)
		w.WriteHeader(http.StatusAccepted)
	}))
	defer srv.Close()

	c := New("https://tribbae.fr/", "0123456789abcdef")
	c.endpoint = srv.URL
	if c.KeyPath() != "/0123456789abcdef.txt" {
		t.Errorf("KeyPath = %q", c.KeyPath())
	}
	c.Notify("/links/a", "/dossiers/b", "/links/a", "pas-un-chemin")
	if err := c.Flush(context.Background()); err != nil {
		t.Fatal(err)
	}
	if calls != 1 || got["host"] != "tribbae.fr" || got["keyLocation"] != "https://tribbae.fr/0123456789abcdef.txt" {
		t.Errorf("envoi : %d appels, %v", calls, got)
	}
	urls, _ := got["urlList"].([]any)
	if len(urls) != 2 || urls[0] != "https://tribbae.fr/dossiers/b" || urls[1] != "https://tribbae.fr/links/a" {
		t.Errorf("urlList = %v", urls)
	}
	// Rien en attente : pas d'appel
	if err := c.Flush(context.Background()); err != nil || calls != 1 {
		t.Errorf("flush vide : %d appels, %v", calls, err)
	}
}
