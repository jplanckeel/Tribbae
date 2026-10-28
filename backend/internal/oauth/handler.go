package oauth

import (
	"encoding/json"
	"errors"
	"log"
	"mime"
	"net/http"
	"net/url"
	"strings"
)

// Adresses servies par le serveur d'autorisation.
const (
	PathASMetadata = "/.well-known/oauth-authorization-server"
	PathPRMetadata = "/.well-known/oauth-protected-resource"
	PathRegister   = "/oauth/register"
	PathAuthorize  = "/oauth/authorize"
	PathToken      = "/oauth/token"
	PathRevoke     = "/oauth/revoke"
	// API de la page de consentement (session de l'app exigée)
	PathConsentInfo = "/v1/oauth/consent/info"
	PathConsent     = "/v1/oauth/consent"
	// ConsentPage : page web « Connecter une application »
	ConsentPage = "/connecter-application"
)

// Handles indique si une adresse relève du serveur d'autorisation (toutes
// répondent 404 quand MCP_ENABLED est désactivé).
func Handles(path string) bool {
	switch path {
	case PathASMetadata, PathRegister, PathAuthorize, PathToken, PathRevoke, PathConsentInfo, PathConsent:
		return true
	}
	return path == PathPRMetadata || strings.HasPrefix(path, PathPRMetadata+"/")
}

// Handler sert le serveur d'autorisation en HTTP.
type Handler struct {
	svc *Service
	// appUser extrait le compte de la session de l'app (JWT), pour la page
	// de consentement seulement
	appUser func(r *http.Request) (string, error)
	// requireHTTPS refuse les requêtes arrivées en clair (hors local)
	requireHTTPS bool
}

func NewHandler(svc *Service, appUser func(r *http.Request) (string, error), requireHTTPS bool) *Handler {
	return &Handler{svc: svc, appUser: appUser, requireHTTPS: requireHTTPS}
}

// IsHTTPS indique si la requête est arrivée en HTTPS (directement, ou
// derrière Traefik qui le signale par X-Forwarded-Proto).
func IsHTTPS(r *http.Request) bool {
	return r.TLS != nil || r.Header.Get("X-Forwarded-Proto") == "https"
}

const maxFormBody = 16 << 10

func writeJSON(w http.ResponseWriter, status int, v any) {
	w.Header().Set("Content-Type", "application/json")
	w.Header().Set("Cache-Control", "no-store")
	w.Header().Set("Pragma", "no-cache")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(v)
}

// writeError répond une erreur OAuth ({error, error_description}).
func writeError(w http.ResponseWriter, err error) {
	var oe *Error
	if !errors.As(err, &oe) {
		log.Printf("oauth: %v", err)
		oe = &Error{Code: "server_error", Description: "internal error", Status: http.StatusInternalServerError}
	}
	writeJSON(w, oe.Status, map[string]string{"error": oe.Code, "error_description": oe.Description})
}

func (h *Handler) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	if h.requireHTTPS && !IsHTTPS(r) {
		writeJSON(w, http.StatusBadRequest, map[string]string{"error": "invalid_request", "error_description": "HTTPS is required"})
		return
	}
	switch {
	case r.URL.Path == PathASMetadata:
		h.only(w, r, http.MethodGet, h.authorizationServerMetadata)
	case r.URL.Path == PathPRMetadata || strings.HasPrefix(r.URL.Path, PathPRMetadata+"/"):
		h.only(w, r, http.MethodGet, h.protectedResourceMetadata)
	case r.URL.Path == PathRegister:
		h.only(w, r, http.MethodPost, h.register)
	case r.URL.Path == PathAuthorize:
		h.only(w, r, http.MethodGet, h.authorize)
	case r.URL.Path == PathToken:
		h.only(w, r, http.MethodPost, h.token)
	case r.URL.Path == PathRevoke:
		h.only(w, r, http.MethodPost, h.revoke)
	case r.URL.Path == PathConsentInfo:
		h.only(w, r, http.MethodPost, h.consentInfo)
	case r.URL.Path == PathConsent:
		h.only(w, r, http.MethodPost, h.consent)
	default:
		http.NotFound(w, r)
	}
}

func (h *Handler) only(w http.ResponseWriter, r *http.Request, method string, next http.HandlerFunc) {
	if r.Method != method {
		w.Header().Set("Allow", method)
		writeJSON(w, http.StatusMethodNotAllowed, map[string]string{"error": "invalid_request", "error_description": "method not allowed"})
		return
	}
	next(w, r)
}

// authorizationServerMetadata : RFC 8414. Pas de CIMD : il obligerait à aller
// chercher une adresse fournie par le client ; Claude passe alors par DCR.
func (h *Handler) authorizationServerMetadata(w http.ResponseWriter, _ *http.Request) {
	iss := h.svc.cfg.Issuer
	writeJSON(w, http.StatusOK, map[string]any{
		"issuer":                                         iss,
		"authorization_endpoint":                         iss + PathAuthorize,
		"token_endpoint":                                 iss + PathToken,
		"registration_endpoint":                          iss + PathRegister,
		"revocation_endpoint":                            iss + PathRevoke,
		"response_types_supported":                       []string{"code"},
		"response_modes_supported":                       []string{"query"},
		"grant_types_supported":                          []string{"authorization_code", "refresh_token"},
		"code_challenge_methods_supported":               []string{"S256"},
		"token_endpoint_auth_methods_supported":          []string{"none"},
		"revocation_endpoint_auth_methods_supported":     []string{"none"},
		"scopes_supported":                               append(append([]string{}, Scopes...), scopeOffline),
		"authorization_response_iss_parameter_supported": true,
	})
}

// protectedResourceMetadata : RFC 9728, pour le serveur MCP.
func (h *Handler) protectedResourceMetadata(w http.ResponseWriter, _ *http.Request) {
	writeJSON(w, http.StatusOK, map[string]any{
		"resource":                 h.svc.cfg.Resource,
		"resource_name":            "Tribbae",
		"authorization_servers":    []string{h.svc.cfg.Issuer},
		"scopes_supported":         Scopes,
		"bearer_methods_supported": []string{"header"},
	})
}

func (h *Handler) register(w http.ResponseWriter, r *http.Request) {
	if mt, _, _ := mime.ParseMediaType(r.Header.Get("Content-Type")); mt != "application/json" {
		writeError(w, oauthErr("invalid_client_metadata", "body must be application/json"))
		return
	}
	var req RegistrationRequest
	if err := json.NewDecoder(http.MaxBytesReader(w, r.Body, maxFormBody)).Decode(&req); err != nil {
		writeError(w, oauthErr("invalid_client_metadata", "malformed JSON body"))
		return
	}
	c, err := h.svc.Register(r.Context(), req)
	if err != nil {
		writeError(w, err)
		return
	}
	writeJSON(w, http.StatusCreated, map[string]any{
		"client_id":                  c.ID,
		"client_id_issued_at":        c.CreatedAt.Unix(),
		"client_name":                c.Name,
		"redirect_uris":              c.RedirectURIs,
		"grant_types":                []string{"authorization_code", "refresh_token"},
		"response_types":             []string{"code"},
		"token_endpoint_auth_method": "none",
	})
}

// authorize vérifie la demande puis envoie le navigateur sur la page de
// consentement. L'identifiant de la demande passe après le # : il n'apparaît
// ni dans les journaux ni dans l'en-tête Referer.
func (h *Handler) authorize(w http.ResponseWriter, r *http.Request) {
	q := r.URL.Query()
	c, redirectURI, err := h.svc.Client(r.Context(), q.Get("client_id"), q.Get("redirect_uri"))
	if err != nil {
		// Adresse de retour non prouvée : on n'y renvoie pas, on l'affiche
		w.Header().Set("Content-Type", "text/plain; charset=utf-8")
		w.Header().Set("Cache-Control", "no-store")
		w.WriteHeader(http.StatusBadRequest)
		_, _ = w.Write([]byte("Ce lien de connexion n'est pas valide. Relancez la connexion depuis l'application qui vous l'a proposé.\n"))
		return
	}
	id, err := h.svc.Authorize(r.Context(), c, redirectURI, AuthorizeParams{
		ResponseType:        q.Get("response_type"),
		State:               q.Get("state"),
		CodeChallenge:       q.Get("code_challenge"),
		CodeChallengeMethod: q.Get("code_challenge_method"),
		Scope:               q.Get("scope"),
		Resource:            q.Get("resource"),
	})
	var oe *Error
	switch {
	case errors.As(err, &oe):
		http.Redirect(w, r, h.svc.ErrorRedirect(redirectURI, q.Get("state"), oe), http.StatusFound)
	case err != nil:
		log.Printf("oauth: authorize: %v", err)
		http.Redirect(w, r, h.svc.ErrorRedirect(redirectURI, q.Get("state"), &Error{Code: "server_error", Description: "internal error"}), http.StatusFound)
	default:
		w.Header().Set("Cache-Control", "no-store")
		http.Redirect(w, r, ConsentPage+"#"+url.PathEscape(id), http.StatusFound)
	}
}

// parseForm lit un corps application/x-www-form-urlencoded (jamais la query :
// un jeton ne doit pas passer dans une adresse).
func parseForm(w http.ResponseWriter, r *http.Request) (url.Values, error) {
	if mt, _, _ := mime.ParseMediaType(r.Header.Get("Content-Type")); mt != "application/x-www-form-urlencoded" {
		return nil, oauthErr("invalid_request", "body must be application/x-www-form-urlencoded")
	}
	r.Body = http.MaxBytesReader(w, r.Body, maxFormBody)
	if err := r.ParseForm(); err != nil {
		return nil, oauthErr("invalid_request", "malformed body")
	}
	return r.PostForm, nil
}

func (h *Handler) token(w http.ResponseWriter, r *http.Request) {
	f, err := parseForm(w, r)
	if err != nil {
		writeError(w, err)
		return
	}
	resp, err := h.svc.Token(r.Context(), TokenRequest{
		GrantType:    f.Get("grant_type"),
		Code:         f.Get("code"),
		RedirectURI:  f.Get("redirect_uri"),
		ClientID:     f.Get("client_id"),
		CodeVerifier: f.Get("code_verifier"),
		RefreshToken: f.Get("refresh_token"),
		Scope:        f.Get("scope"),
		Resource:     f.Get("resource"),
	})
	if err != nil {
		writeError(w, err)
		return
	}
	writeJSON(w, http.StatusOK, resp)
}

func (h *Handler) revoke(w http.ResponseWriter, r *http.Request) {
	f, err := parseForm(w, r)
	if err != nil {
		writeError(w, err)
		return
	}
	if err := h.svc.Revoke(r.Context(), f.Get("token"), f.Get("client_id")); err != nil {
		writeError(w, err)
		return
	}
	w.Header().Set("Cache-Control", "no-store")
	w.WriteHeader(http.StatusOK)
}

// consentRequest est le corps des deux points de la page de consentement.
type consentRequest struct {
	Request string `json:"request"`
	Approve bool   `json:"approve"`
}

// readConsent exige la session de l'app : le JWT voyage dans l'en-tête
// Authorization, que le navigateur n'envoie jamais seul (pas de CSRF).
func (h *Handler) readConsent(w http.ResponseWriter, r *http.Request) (string, *consentRequest, bool) {
	userID, err := h.appUser(r)
	if err != nil || userID == "" {
		writeJSON(w, http.StatusUnauthorized, map[string]string{"message": "authentication required"})
		return "", nil, false
	}
	var req consentRequest
	if err := json.NewDecoder(http.MaxBytesReader(w, r.Body, maxFormBody)).Decode(&req); err != nil || req.Request == "" {
		writeJSON(w, http.StatusBadRequest, map[string]string{"message": "request is required"})
		return "", nil, false
	}
	return userID, &req, true
}

func (h *Handler) consentInfo(w http.ResponseWriter, r *http.Request) {
	_, req, ok := h.readConsent(w, r)
	if !ok {
		return
	}
	info, err := h.svc.ConsentInfo(r.Context(), req.Request)
	if err != nil {
		writeJSON(w, http.StatusNotFound, map[string]string{"message": err.Error()})
		return
	}
	writeJSON(w, http.StatusOK, info)
}

func (h *Handler) consent(w http.ResponseWriter, r *http.Request) {
	userID, req, ok := h.readConsent(w, r)
	if !ok {
		return
	}
	redirect, err := h.svc.Decide(r.Context(), req.Request, userID, req.Approve)
	if err != nil {
		writeJSON(w, http.StatusNotFound, map[string]string{"message": err.Error()})
		return
	}
	writeJSON(w, http.StatusOK, map[string]string{"redirect": redirect})
}
