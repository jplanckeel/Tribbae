package httpx

import (
	"net/http"
	"net/http/httptest"
	"testing"
	"time"
)

func TestClientIP(t *testing.T) {
	r := httptest.NewRequest(http.MethodGet, "/", nil)
	r.RemoteAddr = "10.0.0.2:1234"
	if got := ClientIP(r); got != "10.0.0.2" {
		t.Fatalf("ClientIP without XFF = %q", got)
	}
	r.Header.Set("X-Forwarded-For", "1.1.1.1, 203.0.113.7")
	if got := ClientIP(r); got != "203.0.113.7" {
		t.Fatalf("ClientIP with XFF = %q, want proxy-appended address", got)
	}
}

func TestRateLimiter(t *testing.T) {
	rl := NewRateLimiter(2, time.Minute)
	if !rl.Allow("a") || !rl.Allow("a") {
		t.Fatal("first two requests should pass")
	}
	if rl.Allow("a") {
		t.Fatal("third request should be limited")
	}
	if !rl.Allow("b") {
		t.Fatal("other keys are independent")
	}
}

func TestCORS(t *testing.T) {
	h := CORS([]string{"https://ok.example"}, http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {}))

	r := httptest.NewRequest(http.MethodGet, "/v1/links", nil)
	r.Header.Set("Origin", "https://evil.example")
	w := httptest.NewRecorder()
	h.ServeHTTP(w, r)
	if w.Header().Get("Access-Control-Allow-Origin") != "" {
		t.Fatal("unknown origin must not be allowed")
	}

	r.Header.Set("Origin", "https://ok.example")
	w = httptest.NewRecorder()
	h.ServeHTTP(w, r)
	if w.Header().Get("Access-Control-Allow-Origin") != "https://ok.example" {
		t.Fatal("allowed origin must be reflected")
	}
}

func TestCanonicalHost(t *testing.T) {
	ok := http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) { w.WriteHeader(http.StatusTeapot) })
	h := CanonicalHost("https://tribbae.fr/", []string{"tribbae.bananaops.cloud", " WWW.tribbae.fr "}, ok)
	cases := []struct {
		method, host, target string
		code                 int
		location             string
	}{
		{"GET", "tribbae.bananaops.cloud", "/dossiers/42?vue=liste", http.StatusMovedPermanently, "https://tribbae.fr/dossiers/42?vue=liste"},
		{"HEAD", "www.tribbae.fr:443", "/", http.StatusMovedPermanently, "https://tribbae.fr/"},
		{"GET", "tribbae.bananaops.cloud", "/sitemap.xml", http.StatusMovedPermanently, "https://tribbae.fr/sitemap.xml"},
		// L'API reste servie sur l'ancien domaine (applications déjà installées)
		{"GET", "tribbae.bananaops.cloud", "/v1/links", http.StatusTeapot, ""},
		{"POST", "tribbae.bananaops.cloud", "/v1/auth/login", http.StatusTeapot, ""},
		{"GET", "tribbae.fr", "/explorer", http.StatusTeapot, ""},
		// Noms non listés (réseau interne, local) : jamais redirigés
		{"GET", "backend:8080", "/", http.StatusTeapot, ""},
		{"GET", "localhost:8080", "/", http.StatusTeapot, ""},
	}
	for _, c := range cases {
		req := httptest.NewRequest(c.method, c.target, nil)
		req.Host = c.host
		rec := httptest.NewRecorder()
		h.ServeHTTP(rec, req)
		if rec.Code != c.code || rec.Header().Get("Location") != c.location {
			t.Errorf("%s %s%s : %d %q, want %d %q", c.method, c.host, c.target, rec.Code, rec.Header().Get("Location"), c.code, c.location)
		}
	}
}
