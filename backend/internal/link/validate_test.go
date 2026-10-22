package link

import (
	"errors"
	"strings"
	"testing"
)

func TestNormalizeWebURL(t *testing.T) {
	cases := []struct {
		in, want string
		ok       bool
	}{
		{"", "", true},
		{"https://example.com/a", "https://example.com/a", true},
		{"example.com/recette", "https://example.com/recette", true},
		{"  http://example.com ", "http://example.com", true},
		{"javascript:alert(1)", "", false},
		{"JavaScript:alert(1)", "", false},
		{"data:text/html,<script>", "", false},
		{"file:///etc/passwd", "", false},
		{"https://" + strings.Repeat("a", maxURLLen), "", false},
	}
	for _, c := range cases {
		got, err := normalizeWebURL(c.in)
		if c.ok && (err != nil || got != c.want) {
			t.Errorf("normalizeWebURL(%q) = %q, %v; want %q", c.in, got, err, c.want)
		}
		if !c.ok && !errors.Is(err, ErrInvalidInput) {
			t.Errorf("normalizeWebURL(%q) expected ErrInvalidInput, got %q, %v", c.in, got, err)
		}
	}
}

func TestNormalizeImageURL(t *testing.T) {
	for _, ok := range []string{"", "https://cdn.example.com/a.jpg", "file:///data/user/0/img.jpg"} {
		if _, err := normalizeImageURL(ok); err != nil {
			t.Errorf("normalizeImageURL(%q) unexpected error %v", ok, err)
		}
	}
	for _, ko := range []string{"javascript:alert(1)", "data:image/svg+xml,<svg onload=alert(1)>", "https://"} {
		if _, err := normalizeImageURL(ko); err == nil {
			t.Errorf("normalizeImageURL(%q) expected error", ko)
		}
	}
}

func TestSanitize(t *testing.T) {
	l := &Link{Title: "  Crêpes  ", Tags: []string{" a ", "", "b"}, Rating: 3}
	if err := l.sanitize(); err != nil {
		t.Fatal(err)
	}
	if l.Title != "Crêpes" || len(l.Tags) != 2 {
		t.Fatalf("unexpected sanitize result: %+v", l)
	}
	if err := (&Link{Rating: 9}).sanitize(); !errors.Is(err, ErrInvalidInput) {
		t.Fatalf("expected invalid rating, got %v", err)
	}
	if err := (&Link{Visibility: "everyone"}).sanitize(); !errors.Is(err, ErrInvalidInput) {
		t.Fatalf("expected invalid visibility, got %v", err)
	}
}
