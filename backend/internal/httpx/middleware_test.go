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
