package oauth

import (
	"encoding/json"
	"errors"
	"net/http"
	"net/http/httptest"
	"net/url"
	"strings"
	"testing"
)

// newTestHandler sert le serveur d'autorisation ; la session de l'app est
// simulée par l'en-tête « Authorization: Bearer app-<userID> ».
func newTestHandler(t *testing.T) (*Handler, *Service) {
	svc, _ := newTestService(t)
	appUser := func(r *http.Request) (string, error) {
		if id, ok := strings.CutPrefix(r.Header.Get("Authorization"), "Bearer app-"); ok {
			return id, nil
		}
		return "", errors.New("no session")
	}
	return NewHandler(svc, appUser, true), svc
}

func do(h http.Handler, method, target, contentType, body string, header ...string) *httptest.ResponseRecorder {
	r := httptest.NewRequest(method, target, strings.NewReader(body))
	r.Header.Set("X-Forwarded-Proto", "https")
	if contentType != "" {
		r.Header.Set("Content-Type", contentType)
	}
	for i := 0; i+1 < len(header); i += 2 {
		r.Header.Set(header[i], header[i+1])
	}
	w := httptest.NewRecorder()
	h.ServeHTTP(w, r)
	return w
}

func decode(t *testing.T, w *httptest.ResponseRecorder) map[string]any {
	t.Helper()
	var m map[string]any
	if err := json.Unmarshal(w.Body.Bytes(), &m); err != nil {
		t.Fatalf("réponse non JSON (%d) : %s", w.Code, w.Body)
	}
	return m
}

const form = "application/x-www-form-urlencoded"

func TestMetadata(t *testing.T) {
	h, _ := newTestHandler(t)
	as := decode(t, do(h, "GET", PathASMetadata, "", ""))
	if as["issuer"] != testIssuer || as["token_endpoint"] != testIssuer+PathToken || as["registration_endpoint"] != testIssuer+PathRegister {
		t.Errorf("métadonnées : %v", as)
	}
	if m, _ := as["code_challenge_methods_supported"].([]any); len(m) != 1 || m[0] != "S256" {
		t.Errorf("PKCE annoncé : %v", as["code_challenge_methods_supported"])
	}
	if _, ok := as["client_id_metadata_document_supported"]; ok {
		t.Error("CIMD annoncé")
	}
	for _, path := range []string{PathPRMetadata, PathPRMetadata + "/mcp"} {
		prm := decode(t, do(h, "GET", path, "", ""))
		if prm["resource"] != testResource {
			t.Errorf("%s : resource = %v", path, prm["resource"])
		}
		if as, _ := prm["authorization_servers"].([]any); len(as) != 1 || as[0] != testIssuer {
			t.Errorf("%s : authorization_servers = %v", path, prm["authorization_servers"])
		}
	}
	if !Handles(PathPRMetadata+"/mcp") || Handles("/v1/links") || Handles("/oauth/other") {
		t.Error("Handles")
	}
}

func TestPlainHTTPIsRefused(t *testing.T) {
	h, _ := newTestHandler(t)
	r := httptest.NewRequest("GET", PathASMetadata, nil)
	w := httptest.NewRecorder()
	h.ServeHTTP(w, r)
	if w.Code != http.StatusBadRequest {
		t.Errorf("HTTP en clair : %d", w.Code)
	}
}

// Parcours complet, tel que Claude le fait.
func TestFullFlowOverHTTP(t *testing.T) {
	h, svc := newTestHandler(t)

	w := do(h, "POST", PathRegister, "application/json", `{"redirect_uris":["`+testCallback+`"],"client_name":"Claude","token_endpoint_auth_method":"none"}`)
	if w.Code != http.StatusCreated {
		t.Fatalf("register : %d %s", w.Code, w.Body)
	}
	clientID, _ := decode(t, w)["client_id"].(string)
	if w := do(h, "POST", PathRegister, "application/json", `{"redirect_uris":["https://evil.example/cb"]}`); w.Code != http.StatusBadRequest || decode(t, w)["error"] != "invalid_redirect_uri" {
		t.Errorf("adresse hors liste : %d %s", w.Code, w.Body)
	}

	// Adresse de retour inconnue : page d'erreur, jamais de redirection
	bad := url.Values{"client_id": {clientID}, "redirect_uri": {"https://evil.example/cb"}, "response_type": {"code"}}
	if w := do(h, "GET", PathAuthorize+"?"+bad.Encode(), "", ""); w.Code != http.StatusBadRequest || w.Header().Get("Location") != "" {
		t.Errorf("redirection vers une adresse inconnue : %d %q", w.Code, w.Header().Get("Location"))
	}
	// Sans PKCE : renvoyé vers Claude avec l'erreur
	noPKCE := url.Values{"client_id": {clientID}, "redirect_uri": {testCallback}, "response_type": {"code"}, "state": {"st"}}
	if loc := do(h, "GET", PathAuthorize+"?"+noPKCE.Encode(), "", "").Header().Get("Location"); !strings.HasPrefix(loc, testCallback+"?") || !strings.Contains(loc, "error=invalid_request") {
		t.Errorf("sans PKCE : %q", loc)
	}

	q := url.Values{
		"client_id": {clientID}, "redirect_uri": {testCallback}, "response_type": {"code"}, "state": {"st"},
		"code_challenge": {challenge(testVerifier)}, "code_challenge_method": {"S256"},
		"scope": {"links:read links:write folders:read offline_access"}, "resource": {testResource},
	}
	w = do(h, "GET", PathAuthorize+"?"+q.Encode(), "", "")
	loc := w.Header().Get("Location")
	if w.Code != http.StatusFound || !strings.HasPrefix(loc, ConsentPage+"#") {
		t.Fatalf("authorize : %d %q", w.Code, loc)
	}
	requestID, _ := url.PathUnescape(strings.TrimPrefix(loc, ConsentPage+"#"))
	body := `{"request":"` + requestID + `","approve":true}`

	// La page de consentement exige la session de l'app
	if w := do(h, "POST", PathConsentInfo, "application/json", body); w.Code != http.StatusUnauthorized {
		t.Errorf("consentement sans session : %d", w.Code)
	}
	info := decode(t, do(h, "POST", PathConsentInfo, "application/json", body, "Authorization", "Bearer app-user1"))
	if info["redirectHost"] != "claude.ai" {
		t.Errorf("info = %v", info)
	}
	w = do(h, "POST", PathConsent, "application/json", body, "Authorization", "Bearer app-user1")
	back, _ := url.Parse(decode(t, w)["redirect"].(string))
	if back.Host != "claude.ai" || back.Query().Get("state") != "st" || back.Query().Get("code") == "" {
		t.Fatalf("retour = %v", back)
	}

	// Jetons : corps form-urlencoded uniquement
	tokenForm := url.Values{
		"grant_type": {"authorization_code"}, "code": {back.Query().Get("code")}, "redirect_uri": {testCallback},
		"client_id": {clientID}, "code_verifier": {testVerifier}, "resource": {testResource},
	}
	if w := do(h, "POST", PathToken, "application/json", `{}`); w.Code != http.StatusBadRequest {
		t.Errorf("token en JSON : %d", w.Code)
	}
	if w := do(h, "POST", PathToken+"?"+tokenForm.Encode(), form, ""); w.Code == http.StatusOK {
		t.Errorf("paramètres dans l'adresse acceptés : %d", w.Code)
	}
	w = do(h, "POST", PathToken, form, tokenForm.Encode())
	tok := decode(t, w)
	if w.Code != http.StatusOK || w.Header().Get("Cache-Control") != "no-store" {
		t.Fatalf("token : %d %v", w.Code, tok)
	}
	at, _ := tok["access_token"].(string)
	if _, err := svc.VerifyAccessToken(t.Context(), at); err != nil {
		t.Fatalf("access token : %v", err)
	}

	// Refresh, puis réutilisation de l'ancien : invalid_grant
	rf := url.Values{"grant_type": {"refresh_token"}, "refresh_token": {tok["refresh_token"].(string)}, "client_id": {clientID}}
	if w := do(h, "POST", PathToken, form, rf.Encode()); w.Code != http.StatusOK {
		t.Fatalf("refresh : %d %s", w.Code, w.Body)
	}
	if w := do(h, "POST", PathToken, form, rf.Encode()); w.Code != http.StatusBadRequest || decode(t, w)["error"] != "invalid_grant" {
		t.Errorf("refresh token rejoué : %d %s", w.Code, w.Body)
	}

	// Révocation : toujours 200
	if w := do(h, "POST", PathRevoke, form, url.Values{"token": {"inconnu"}, "client_id": {clientID}}.Encode()); w.Code != http.StatusOK {
		t.Errorf("revoke : %d", w.Code)
	}
}
