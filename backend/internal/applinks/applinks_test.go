package applinks

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"
)

const (
	upload = "14:6D:E9:83:C5:73:06:50:D8:EE:B9:95:2F:34:FC:64:16:A0:83:42:E6:1D:BE:A8:8A:04:96:B2:3F:CF:44:E5"
	play   = "fa:c6:17:45:dc:09:03:78:6f:b9:ed:e6:2a:96:2b:39:9f:73:48:f0:bb:6f:89:9b:83:32:66:75:91:03:3b:9c"
)

func serve(t *testing.T, h http.Handler, method string) *httptest.ResponseRecorder {
	t.Helper()
	rec := httptest.NewRecorder()
	h.ServeHTTP(rec, httptest.NewRequest(method, Path, nil))
	return rec
}

func TestHandlerPublishesFingerprints(t *testing.T) {
	h, err := Handler([]string{upload, " " + play + " "})
	if err != nil {
		t.Fatal(err)
	}
	rec := serve(t, h, http.MethodGet)
	if rec.Code != http.StatusOK {
		t.Fatalf("status = %d", rec.Code)
	}
	if ct := rec.Header().Get("Content-Type"); ct != "application/json" {
		t.Fatalf("Content-Type = %q", ct)
	}
	var got []statement
	if err := json.Unmarshal(rec.Body.Bytes(), &got); err != nil {
		t.Fatal(err)
	}
	if len(got) != 1 || got[0].Relation[0] != "delegate_permission/common.handle_all_urls" {
		t.Fatalf("statement = %+v", got)
	}
	tg := got[0].Target
	if tg.Namespace != "android_app" || tg.PackageName != "fr.tribbae.app" {
		t.Fatalf("target = %+v", tg)
	}
	// Minuscules et espaces normalisés : Android compare en majuscules
	want := []string{upload, "FA:C6:17:45:DC:09:03:78:6F:B9:ED:E6:2A:96:2B:39:9F:73:48:F0:BB:6F:89:9B:83:32:66:75:91:03:3B:9C"}
	if len(tg.Fingerprints) != 2 || tg.Fingerprints[0] != want[0] || tg.Fingerprints[1] != want[1] {
		t.Fatalf("fingerprints = %v, want %v", tg.Fingerprints, want)
	}
	if rec := serve(t, h, http.MethodHead); rec.Code != http.StatusOK {
		t.Fatalf("HEAD status = %d", rec.Code)
	}
	if rec := serve(t, h, http.MethodPost); rec.Code != http.StatusMethodNotAllowed {
		t.Fatalf("POST status = %d", rec.Code)
	}
}

func TestHandlerWithoutFingerprintIsNotFound(t *testing.T) {
	h, err := Handler(nil)
	if err != nil {
		t.Fatal(err)
	}
	if rec := serve(t, h, http.MethodGet); rec.Code != http.StatusNotFound {
		t.Fatalf("status = %d, want 404", rec.Code)
	}
}

func TestHandlerRejectsMalformedFingerprints(t *testing.T) {
	for _, f := range []string{
		"14:6D:E9", // trop courte
		"146DE983C5730650D8EEB9952F34FC6416A08342E61DBEA88A0496B23FCF44E5", // sans « : »
		upload + ":AA",      // trop longue
		"ZZ" + upload[2:],   // pas de l'hexadécimal
		"SHA256: " + upload, // préfixe recopié de keytool
	} {
		if _, err := Handler([]string{f}); err == nil {
			t.Errorf("Handler(%q) accepted a malformed fingerprint", f)
		}
	}
}
