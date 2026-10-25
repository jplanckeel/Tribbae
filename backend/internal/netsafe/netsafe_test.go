package netsafe

import (
	"context"
	"errors"
	"net"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"
)

func TestIsPublicIP(t *testing.T) {
	cases := map[string]bool{
		"8.8.8.8":         true,
		"2606:4700::1111": true,
		"127.0.0.1":       false,
		"10.1.2.3":        false,
		"172.16.0.1":      false,
		"192.168.1.1":     false,
		"169.254.169.254": false,
		"100.64.0.1":      false,
		"0.0.0.0":         false,
		"::1":             false,
		"fd00::1":         false,
		"fe80::1":         false,
	}
	for in, want := range cases {
		if got := IsPublicIP(net.ParseIP(in)); got != want {
			t.Errorf("IsPublicIP(%s) = %v, want %v", in, got, want)
		}
	}
}

func TestValidateURL(t *testing.T) {
	ok := []string{"https://example.com", "http://example.com/a?b=c"}
	ko := []string{"", "example.com", "javascript:alert(1)", "file:///etc/passwd", "ftp://example.com", "gopher://x"}
	for _, u := range ok {
		if _, err := ValidateURL(u); err != nil {
			t.Errorf("ValidateURL(%q) unexpected error: %v", u, err)
		}
	}
	for _, u := range ko {
		if _, err := ValidateURL(u); err == nil {
			t.Errorf("ValidateURL(%q) expected error", u)
		}
	}
}

func TestClientRefusesLoopback(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		w.WriteHeader(http.StatusOK)
	}))
	defer srv.Close()

	_, err := GetWithHeaders(context.Background(), NewClient(2*time.Second), srv.URL, nil)
	if !errors.Is(err, ErrForbiddenAddress) {
		t.Fatalf("expected ErrForbiddenAddress, got %v", err)
	}
}
