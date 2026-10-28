// Package oauth est le serveur d'autorisation OAuth 2.1 des applications
// connectées (le connecteur Claude, qui parle au serveur MCP de /mcp).
//
// Choix de sécurité : ces applications sont des agents qui lisent des pages
// web non fiables, on limite donc au maximum ce qu'un jeton permet.
//   - Clients publics enregistrés dynamiquement (RFC 7591), seulement avec une
//     adresse de retour de la liste blanche (Config.RedirectURIs) ; un client
//     jamais autorisé est effacé après un jour.
//   - PKCE S256 obligatoire, code à usage unique valable 60 s ; un code
//     présenté deux fois révoque l'accès qu'il a ouvert.
//   - Jetons opaques (jamais des JWT de l'app), stockés hachés, liés à la
//     ressource /mcp (RFC 8707) : un jeton de l'app ne marche pas sur /mcp,
//     et un jeton MCP ne marche pas sur l'API de l'app.
//   - Access token de 15 min ; refresh token remplacé à chaque usage, et
//     réutiliser un ancien révoque tout l'accès (vol probable).
//   - Accès (« grant ») limité à 90 jours, inactif au bout de 30 ; fermé par
//     un changement de mot de passe ou la suppression du compte.
package oauth

import (
	"context"
	"crypto/rand"
	"crypto/sha256"
	"crypto/subtle"
	"encoding/base64"
	"encoding/hex"
	"errors"
	"net/url"
	"slices"
	"strings"
	"time"
	"unicode"

	"go.mongodb.org/mongo-driver/v2/bson"
	"go.mongodb.org/mongo-driver/v2/mongo"
	"go.mongodb.org/mongo-driver/v2/mongo/options"
)

// Droits qu'une application peut demander. Aucun droit d'administration,
// de modification ou de suppression.
const (
	ScopeLinksRead   = "links:read"
	ScopeLinksWrite  = "links:write"
	ScopeFoldersRead = "folders:read"
	// scopeOffline est ajouté par Claude pour obtenir un refresh token, que
	// l'on délivre de toute façon : il est accepté puis ignoré.
	scopeOffline = "offline_access"
)

// Scopes sont tous les droits, accordés quand l'application n'en précise pas.
var Scopes = []string{ScopeLinksRead, ScopeLinksWrite, ScopeFoldersRead}

const (
	// Préfixes des jetons : ils ne ressemblent jamais à un JWT de l'app
	accessPrefix  = "tbm_at_"
	refreshPrefix = "tbm_rt_"
	clientPrefix  = "tbm_client_"

	AccessTTL  = 15 * time.Minute
	RefreshTTL = 30 * 24 * time.Hour
	GrantTTL   = 90 * 24 * time.Hour
	// requestTTL : temps laissé pour se connecter et donner son accord
	requestTTL = 10 * time.Minute
	codeTTL    = 60 * time.Second
	// unusedClientTTL : un client enregistré mais jamais autorisé est effacé
	unusedClientTTL = 24 * time.Hour
	// maxUnusedClients borne les enregistrements en attente (chacun est
	// public et gratuit) : au-delà, on refuse jusqu'à ce qu'ils expirent
	maxUnusedClients = 500
	maxClientNameLen = 80
	maxStateLen      = 512
)

const (
	kindAccess  = "access"
	kindRefresh = "refresh"
)

// Config décrit le serveur d'autorisation.
type Config struct {
	// Issuer : adresse publique du site (https://tribbae.fr)
	Issuer string
	// Resource : adresse exacte du serveur MCP (https://tribbae.fr/mcp),
	// seule audience des jetons
	Resource string
	// RedirectURIs : seules adresses de retour acceptées (comparaison exacte)
	RedirectURIs []string
}

// Error est une erreur OAuth (RFC 6749 §5.2, RFC 7591 §3.2.2) : un code
// standard et une description en anglais.
type Error struct {
	Code        string
	Description string
	Status      int
}

func (e *Error) Error() string { return e.Code + ": " + e.Description }

func oauthErr(code, desc string) *Error { return &Error{Code: code, Description: desc, Status: 400} }

func invalidGrant(desc string) *Error { return oauthErr("invalid_grant", desc) }

var (
	// ErrRequestNotFound : demande d'autorisation inconnue, expirée ou déjà traitée
	ErrRequestNotFound = errors.New("authorization request not found or expired")
	// ErrInvalidToken : jeton inconnu, expiré, révoqué ou d'une autre audience
	ErrInvalidToken = errors.New("invalid token")
)

// Client est une application enregistrée (RFC 7591), toujours publique.
type Client struct {
	ID           string    `bson:"_id"`
	Name         string    `bson:"name"`
	RedirectURIs []string  `bson:"redirect_uris"`
	CreatedAt    time.Time `bson:"created_at"`
	// UnusedExpiresAt : date d'effacement (index TTL) tant que personne ne
	// l'a autorisé ; retiré à la première autorisation
	UnusedExpiresAt *time.Time `bson:"unused_expires_at,omitempty"`
}

// authRequest est une demande d'autorisation en attente de l'accord de
// l'utilisateur, puis le code délivré une fois l'accord donné.
type authRequest struct {
	ID            string         `bson:"_id"` // hash de l'identifiant passé à la page de consentement
	ClientID      string         `bson:"client_id"`
	ClientName    string         `bson:"client_name"`
	RedirectURI   string         `bson:"redirect_uri"`
	State         string         `bson:"state"`
	CodeChallenge string         `bson:"code_challenge"`
	Scopes        []string       `bson:"scopes"`
	Resource      string         `bson:"resource"`
	CreatedAt     time.Time      `bson:"created_at"`
	ExpiresAt     time.Time      `bson:"expires_at"`
	CodeHash      string         `bson:"code_hash,omitempty"`
	UserID        string         `bson:"user_id,omitempty"`
	ApprovedAt    *time.Time     `bson:"approved_at,omitempty"`
	UsedAt        *time.Time     `bson:"used_at,omitempty"`
	GrantID       *bson.ObjectID `bson:"grant_id,omitempty"`
}

// Grant est l'accès qu'un utilisateur a donné à une application : c'est ce
// que l'écran « Applications connectées » montre et révoque.
type Grant struct {
	ID         bson.ObjectID `bson:"_id"`
	UserID     string        `bson:"user_id"`
	ClientID   string        `bson:"client_id"`
	ClientName string        `bson:"client_name"`
	// RedirectHost : le site de l'application (claude.ai)
	RedirectHost string    `bson:"redirect_host"`
	Scopes       []string  `bson:"scopes"`
	Resource     string    `bson:"resource"`
	CreatedAt    time.Time `bson:"created_at"`
	LastUsedAt   time.Time `bson:"last_used_at"`
	ExpiresAt    time.Time `bson:"expires_at"`
}

// token est un access ou refresh token, stocké haché.
type token struct {
	Hash     string        `bson:"_id"`
	Kind     string        `bson:"kind"`
	GrantID  bson.ObjectID `bson:"grant_id"`
	UserID   string        `bson:"user_id"`
	ClientID string        `bson:"client_id"`
	Scopes   []string      `bson:"scopes"`
	Resource string        `bson:"resource"`
	// AuthTime : date de l'autorisation, comparée au dernier changement de
	// mot de passe
	AuthTime  time.Time  `bson:"auth_time"`
	CreatedAt time.Time  `bson:"created_at"`
	ExpiresAt time.Time  `bson:"expires_at"`
	UsedAt    *time.Time `bson:"used_at,omitempty"`
}

// AccessToken décrit un access token valide.
type AccessToken struct {
	UserID    string
	ClientID  string
	GrantID   string
	Scopes    []string
	ExpiresAt time.Time
}

// TokenResponse est la réponse du point /oauth/token (RFC 6749 §5.1).
type TokenResponse struct {
	AccessToken  string `json:"access_token"`
	TokenType    string `json:"token_type"`
	ExpiresIn    int64  `json:"expires_in"`
	RefreshToken string `json:"refresh_token"`
	Scope        string `json:"scope"`
}

type Service struct {
	cfg      Config
	clients  *mongo.Collection
	requests *mongo.Collection
	grants   *mongo.Collection
	tokens   *mongo.Collection
	// session vérifie que le compte existe encore et que son mot de passe
	// n'a pas changé depuis l'autorisation (auth.Service.SessionValid)
	session func(userID string, since time.Time) error
	now     func() time.Time
}

// Collections utilisées, aussi effacées avec le compte (account.Service).
const (
	ClientsCollection  = "oauth_clients"
	RequestsCollection = "oauth_requests"
	GrantsCollection   = "oauth_grants"
	TokensCollection   = "oauth_tokens"
)

func NewService(db *mongo.Database, cfg Config) *Service {
	return &Service{
		cfg:      cfg,
		clients:  db.Collection(ClientsCollection),
		requests: db.Collection(RequestsCollection),
		grants:   db.Collection(GrantsCollection),
		tokens:   db.Collection(TokensCollection),
		now:      time.Now,
	}
}

// SetSessionCheck branche la vérification du compte (auth.Service.SessionValid).
func (s *Service) SetSessionCheck(check func(userID string, since time.Time) error) {
	s.session = check
}

func (s *Service) sessionValid(userID string, since time.Time) bool {
	return s.session == nil || s.session(userID, since) == nil
}

// Config retourne la configuration (métadonnées publiées).
func (s *Service) Config() Config { return s.cfg }

func hashToken(t string) string {
	sum := sha256.Sum256([]byte(t))
	return hex.EncodeToString(sum[:])
}

func newSecret() string {
	b := make([]byte, 32)
	_, _ = rand.Read(b) // ne retourne jamais d'erreur (Go ≥ 1.24)
	return base64.RawURLEncoding.EncodeToString(b)
}

// cleanName garde un nom affichable sur une ligne, sans caractères de contrôle.
func cleanName(name string) string {
	name = strings.Map(func(r rune) rune {
		if unicode.IsControl(r) || unicode.Is(unicode.Cf, r) {
			return ' '
		}
		return r
	}, name)
	name = strings.Join(strings.Fields(name), " ")
	if r := []rune(name); len(r) > maxClientNameLen {
		name = string(r[:maxClientNameLen])
	}
	return name
}

// RegistrationRequest est le corps d'un enregistrement dynamique (RFC 7591).
type RegistrationRequest struct {
	RedirectURIs            []string `json:"redirect_uris"`
	ClientName              string   `json:"client_name"`
	GrantTypes              []string `json:"grant_types"`
	ResponseTypes           []string `json:"response_types"`
	TokenEndpointAuthMethod string   `json:"token_endpoint_auth_method"`
}

// Register enregistre un client public dont toutes les adresses de retour
// sont dans la liste blanche.
func (s *Service) Register(ctx context.Context, req RegistrationRequest) (*Client, error) {
	if len(req.RedirectURIs) == 0 || len(req.RedirectURIs) > len(s.cfg.RedirectURIs) {
		return nil, oauthErr("invalid_redirect_uri", "redirect_uris must list allowed callback URLs")
	}
	for _, u := range req.RedirectURIs {
		if !slices.Contains(s.cfg.RedirectURIs, u) {
			return nil, oauthErr("invalid_redirect_uri", "redirect_uri is not allowed")
		}
	}
	if m := req.TokenEndpointAuthMethod; m != "" && m != "none" {
		return nil, oauthErr("invalid_client_metadata", "only public clients (token_endpoint_auth_method none) are supported")
	}
	for _, g := range req.GrantTypes {
		if g != "authorization_code" && g != "refresh_token" {
			return nil, oauthErr("invalid_client_metadata", "unsupported grant type")
		}
	}
	for _, r := range req.ResponseTypes {
		if r != "code" {
			return nil, oauthErr("invalid_client_metadata", "unsupported response type")
		}
	}
	pending, err := s.clients.CountDocuments(ctx, bson.M{"unused_expires_at": bson.M{"$exists": true}})
	if err != nil {
		return nil, err
	}
	if pending >= maxUnusedClients {
		return nil, &Error{Code: "temporarily_unavailable", Description: "too many pending registrations, please retry later", Status: 429}
	}
	now := s.now()
	expires := now.Add(unusedClientTTL)
	c := &Client{
		ID:              clientPrefix + newSecret()[:22],
		Name:            cleanName(req.ClientName),
		RedirectURIs:    slices.Compact(slices.Sorted(slices.Values(req.RedirectURIs))),
		CreatedAt:       now,
		UnusedExpiresAt: &expires,
	}
	if _, err := s.clients.InsertOne(ctx, c); err != nil {
		return nil, err
	}
	return c, nil
}

// Client retrouve un client et vérifie l'adresse de retour demandée. Une
// erreur ici ne doit jamais être renvoyée vers cette adresse (elle n'est pas
// prouvée), mais affichée à l'utilisateur.
func (s *Service) Client(ctx context.Context, clientID, redirectURI string) (*Client, string, error) {
	var c Client
	if clientID == "" || s.clients.FindOne(ctx, bson.M{"_id": clientID}).Decode(&c) != nil {
		return nil, "", oauthErr("invalid_client", "unknown client")
	}
	if redirectURI == "" && len(c.RedirectURIs) == 1 {
		redirectURI = c.RedirectURIs[0]
	}
	// L'adresse doit toujours être dans la liste blanche actuelle : la retirer
	// de la configuration coupe aussi les clients déjà enregistrés
	if !slices.Contains(c.RedirectURIs, redirectURI) || !slices.Contains(s.cfg.RedirectURIs, redirectURI) {
		return nil, "", oauthErr("invalid_request", "redirect_uri does not match the registered ones")
	}
	return &c, redirectURI, nil
}

// AuthorizeParams sont les paramètres de /oauth/authorize.
type AuthorizeParams struct {
	ResponseType        string
	State               string
	CodeChallenge       string
	CodeChallengeMethod string
	Scope               string
	Resource            string
}

// parseScopes vérifie les droits demandés ; aucun = tous.
func parseScopes(scope string) ([]string, bool) {
	var out []string
	for _, sc := range strings.Fields(scope) {
		switch {
		case sc == scopeOffline:
		case slices.Contains(Scopes, sc):
			if !slices.Contains(out, sc) {
				out = append(out, sc)
			}
		default:
			return nil, false
		}
	}
	if len(out) == 0 {
		return slices.Clone(Scopes), true
	}
	slices.Sort(out)
	return out, true
}

// validPKCE : un code_challenge ou code_verifier S256 fait 43 à 128
// caractères non réservés (RFC 7636 §4.1).
func validPKCE(v string) bool {
	if len(v) < 43 || len(v) > 128 {
		return false
	}
	for _, r := range v {
		if !(r >= 'A' && r <= 'Z' || r >= 'a' && r <= 'z' || r >= '0' && r <= '9' || strings.ContainsRune("-._~", r)) {
			return false
		}
	}
	return true
}

// checkResource : la seule audience possible est le serveur MCP (RFC 8707).
func (s *Service) checkResource(resource string) bool {
	return resource == "" || resource == s.cfg.Resource
}

// Authorize enregistre une demande d'autorisation et retourne l'identifiant
// à passer à la page de consentement. Une *Error est à renvoyer vers
// l'adresse de retour (déjà vérifiée par Client).
func (s *Service) Authorize(ctx context.Context, c *Client, redirectURI string, p AuthorizeParams) (string, error) {
	if p.ResponseType != "code" {
		return "", oauthErr("unsupported_response_type", "only response_type=code is supported")
	}
	if p.CodeChallengeMethod != "S256" || !validPKCE(p.CodeChallenge) {
		return "", oauthErr("invalid_request", "PKCE with code_challenge_method=S256 is required")
	}
	if len(p.State) > maxStateLen {
		return "", oauthErr("invalid_request", "state is too long")
	}
	scopes, ok := parseScopes(p.Scope)
	if !ok {
		return "", oauthErr("invalid_scope", "unknown scope")
	}
	if !s.checkResource(p.Resource) {
		return "", oauthErr("invalid_target", "resource must be "+s.cfg.Resource)
	}
	id := newSecret()
	now := s.now()
	req := authRequest{
		ID:            hashToken(id),
		ClientID:      c.ID,
		ClientName:    c.Name,
		RedirectURI:   redirectURI,
		State:         p.State,
		CodeChallenge: p.CodeChallenge,
		Scopes:        scopes,
		Resource:      s.cfg.Resource,
		CreatedAt:     now,
		ExpiresAt:     now.Add(requestTTL),
	}
	if _, err := s.requests.InsertOne(ctx, req); err != nil {
		return "", err
	}
	return id, nil
}

// Consent est ce que la page de consentement affiche.
type Consent struct {
	ClientName string `json:"clientName"`
	// RedirectHost : site qui recevra l'accès (claude.ai), affiché en clair
	RedirectHost string   `json:"redirectHost"`
	Scopes       []string `json:"scopes"`
}

func (s *Service) pending(requestID string) bson.M {
	return bson.M{"_id": hashToken(requestID), "code_hash": bson.M{"$exists": false}, "expires_at": bson.M{"$gt": s.now()}}
}

// ConsentInfo décrit une demande en attente.
func (s *Service) ConsentInfo(ctx context.Context, requestID string) (*Consent, error) {
	var req authRequest
	if requestID == "" || s.requests.FindOne(ctx, s.pending(requestID)).Decode(&req) != nil {
		return nil, ErrRequestNotFound
	}
	return &Consent{ClientName: req.ClientName, RedirectHost: hostOf(req.RedirectURI), Scopes: req.Scopes}, nil
}

func hostOf(raw string) string {
	u, err := url.Parse(raw)
	if err != nil {
		return ""
	}
	return u.Host
}

// Decide enregistre la réponse de l'utilisateur connecté et retourne
// l'adresse vers laquelle renvoyer son navigateur : avec un code s'il a
// accepté, avec access_denied sinon.
func (s *Service) Decide(ctx context.Context, requestID, userID string, approve bool) (string, error) {
	if requestID == "" || userID == "" {
		return "", ErrRequestNotFound
	}
	var req authRequest
	params := url.Values{}
	if approve {
		code := newSecret()
		now := s.now()
		err := s.requests.FindOneAndUpdate(ctx, s.pending(requestID), bson.M{"$set": bson.M{
			"code_hash": hashToken(code), "user_id": userID, "approved_at": now, "expires_at": now.Add(codeTTL),
		}}, options.FindOneAndUpdate().SetReturnDocument(options.After)).Decode(&req)
		if err != nil {
			return "", ErrRequestNotFound
		}
		params.Set("code", code)
	} else {
		if s.requests.FindOneAndDelete(ctx, s.pending(requestID)).Decode(&req) != nil {
			return "", ErrRequestNotFound
		}
		params.Set("error", "access_denied")
		params.Set("error_description", "the user denied access")
	}
	return s.redirect(req.RedirectURI, req.State, params), nil
}

// redirect construit l'adresse de retour, avec state et iss (RFC 9207).
func (s *Service) redirect(redirectURI, state string, params url.Values) string {
	u, err := url.Parse(redirectURI)
	if err != nil {
		return redirectURI
	}
	q := u.Query()
	for k, v := range params {
		q[k] = v
	}
	if state != "" {
		q.Set("state", state)
	}
	q.Set("iss", s.cfg.Issuer)
	u.RawQuery = q.Encode()
	return u.String()
}

// ErrorRedirect construit l'adresse de retour d'une erreur d'Authorize.
func (s *Service) ErrorRedirect(redirectURI, state string, e *Error) string {
	return s.redirect(redirectURI, state, url.Values{"error": {e.Code}, "error_description": {e.Description}})
}

// TokenRequest regroupe les champs de /oauth/token.
type TokenRequest struct {
	GrantType    string
	Code         string
	RedirectURI  string
	ClientID     string
	CodeVerifier string
	RefreshToken string
	Scope        string
	Resource     string
}

// Token traite /oauth/token : échange d'un code ou d'un refresh token.
func (s *Service) Token(ctx context.Context, r TokenRequest) (*TokenResponse, error) {
	if r.ClientID == "" {
		return nil, &Error{Code: "invalid_client", Description: "client_id is required", Status: 401}
	}
	if !s.checkResource(r.Resource) {
		return nil, oauthErr("invalid_target", "resource must be "+s.cfg.Resource)
	}
	switch r.GrantType {
	case "authorization_code":
		return s.exchangeCode(ctx, r)
	case "refresh_token":
		return s.refresh(ctx, r)
	default:
		return nil, oauthErr("unsupported_grant_type", "unsupported grant_type")
	}
}

func pkceMatches(verifier, challenge string) bool {
	if !validPKCE(verifier) {
		return false
	}
	sum := sha256.Sum256([]byte(verifier))
	return subtle.ConstantTimeCompare([]byte(base64.RawURLEncoding.EncodeToString(sum[:])), []byte(challenge)) == 1
}

func (s *Service) exchangeCode(ctx context.Context, r TokenRequest) (*TokenResponse, error) {
	if r.Code == "" {
		return nil, oauthErr("invalid_request", "code is required")
	}
	now := s.now()
	codeHash := hashToken(r.Code)
	// Le code ne sert qu'une fois : marqué utilisé atomiquement
	var req authRequest
	err := s.requests.FindOneAndUpdate(ctx,
		bson.M{"code_hash": codeHash, "used_at": bson.M{"$exists": false}},
		bson.M{"$set": bson.M{"used_at": now}}).Decode(&req)
	if err != nil {
		// Code déjà utilisé : il a fuité, on ferme l'accès qu'il avait ouvert
		var used authRequest
		if s.requests.FindOne(ctx, bson.M{"code_hash": codeHash}).Decode(&used) == nil && used.GrantID != nil {
			_ = s.revokeGrant(ctx, *used.GrantID)
		}
		return nil, invalidGrant("invalid or already used authorization code")
	}
	if !req.ExpiresAt.After(now) || req.ApprovedAt == nil {
		return nil, invalidGrant("authorization code expired")
	}
	if r.ClientID != req.ClientID {
		return nil, invalidGrant("authorization code was issued to another client")
	}
	if r.RedirectURI != req.RedirectURI {
		return nil, invalidGrant("redirect_uri does not match the authorization request")
	}
	if !pkceMatches(r.CodeVerifier, req.CodeChallenge) {
		return nil, invalidGrant("PKCE verification failed")
	}
	if !s.sessionValid(req.UserID, *req.ApprovedAt) {
		return nil, invalidGrant("account is no longer valid")
	}
	// Le client a servi : il n'est plus effacé automatiquement
	res, err := s.clients.UpdateOne(ctx, bson.M{"_id": req.ClientID}, bson.M{"$unset": bson.M{"unused_expires_at": ""}})
	if err != nil {
		return nil, err
	}
	if res.MatchedCount == 0 {
		return nil, invalidGrant("client no longer exists")
	}
	g := Grant{
		ID:           bson.NewObjectID(),
		UserID:       req.UserID,
		ClientID:     req.ClientID,
		ClientName:   req.ClientName,
		RedirectHost: hostOf(req.RedirectURI),
		Scopes:       req.Scopes,
		Resource:     req.Resource,
		CreatedAt:    *req.ApprovedAt,
		LastUsedAt:   now,
		ExpiresAt:    req.ApprovedAt.Add(GrantTTL),
	}
	if _, err := s.grants.InsertOne(ctx, g); err != nil {
		return nil, err
	}
	if _, err := s.requests.UpdateOne(ctx, bson.M{"_id": req.ID}, bson.M{"$set": bson.M{"grant_id": g.ID}}); err != nil {
		return nil, err
	}
	return s.issue(ctx, &g, g.Scopes)
}

func (s *Service) refresh(ctx context.Context, r TokenRequest) (*TokenResponse, error) {
	if !strings.HasPrefix(r.RefreshToken, refreshPrefix) {
		return nil, invalidGrant("invalid refresh token")
	}
	now := s.now()
	h := hashToken(r.RefreshToken)
	// Un refresh token ne sert qu'une fois : marqué utilisé atomiquement
	var t token
	err := s.tokens.FindOneAndUpdate(ctx,
		bson.M{"_id": h, "kind": kindRefresh, "used_at": bson.M{"$exists": false}},
		bson.M{"$set": bson.M{"used_at": now}}).Decode(&t)
	if err != nil {
		// Un refresh token déjà remplacé qui revient : l'un des deux
		// détenteurs l'a volé, on révoque tout l'accès
		var used token
		if s.tokens.FindOne(ctx, bson.M{"_id": h, "kind": kindRefresh}).Decode(&used) == nil {
			_ = s.revokeGrant(ctx, used.GrantID)
			return nil, invalidGrant("refresh token reused, access revoked")
		}
		return nil, invalidGrant("invalid refresh token")
	}
	if !t.ExpiresAt.After(now) {
		return nil, invalidGrant("refresh token expired")
	}
	if r.ClientID != t.ClientID {
		return nil, invalidGrant("refresh token was issued to another client")
	}
	var g Grant
	if s.grants.FindOne(ctx, bson.M{"_id": t.GrantID}).Decode(&g) != nil || !g.ExpiresAt.After(now) {
		return nil, invalidGrant("access was revoked or has expired")
	}
	if !s.sessionValid(g.UserID, g.CreatedAt) {
		_ = s.revokeGrant(ctx, g.ID)
		return nil, invalidGrant("account is no longer valid")
	}
	scopes := g.Scopes
	if r.Scope != "" {
		asked, ok := parseScopes(r.Scope)
		if !ok {
			return nil, oauthErr("invalid_scope", "unknown scope")
		}
		for _, sc := range asked {
			if !slices.Contains(g.Scopes, sc) {
				return nil, oauthErr("invalid_scope", "scope exceeds the granted ones")
			}
		}
		scopes = asked
	}
	if _, err := s.grants.UpdateOne(ctx, bson.M{"_id": g.ID}, bson.M{"$set": bson.M{"last_used_at": now}}); err != nil {
		return nil, err
	}
	return s.issue(ctx, &g, scopes)
}

// issue délivre une paire de jetons pour un accès. Le refresh token vit 30
// jours, sans dépasser la fin de l'accès.
func (s *Service) issue(ctx context.Context, g *Grant, scopes []string) (*TokenResponse, error) {
	now := s.now()
	access := accessPrefix + newSecret()
	refresh := refreshPrefix + newSecret()
	refreshExp := now.Add(RefreshTTL)
	if g.ExpiresAt.Before(refreshExp) {
		refreshExp = g.ExpiresAt
	}
	base := token{GrantID: g.ID, UserID: g.UserID, ClientID: g.ClientID, Scopes: scopes, Resource: g.Resource, AuthTime: g.CreatedAt, CreatedAt: now}
	at, rt := base, base
	at.Hash, at.Kind, at.ExpiresAt = hashToken(access), kindAccess, now.Add(AccessTTL)
	rt.Hash, rt.Kind, rt.ExpiresAt = hashToken(refresh), kindRefresh, refreshExp
	if _, err := s.tokens.InsertMany(ctx, []token{at, rt}); err != nil {
		return nil, err
	}
	return &TokenResponse{
		AccessToken:  access,
		TokenType:    "Bearer",
		ExpiresIn:    int64(AccessTTL / time.Second),
		RefreshToken: refresh,
		Scope:        strings.Join(scopes, " "),
	}, nil
}

// VerifyAccessToken vérifie un access token présenté au serveur MCP : connu,
// non expiré, émis pour cette ressource, compte toujours valide.
func (s *Service) VerifyAccessToken(ctx context.Context, raw string) (*AccessToken, error) {
	if !strings.HasPrefix(raw, accessPrefix) {
		return nil, ErrInvalidToken
	}
	var t token
	if s.tokens.FindOne(ctx, bson.M{"_id": hashToken(raw), "kind": kindAccess}).Decode(&t) != nil {
		return nil, ErrInvalidToken
	}
	if !t.ExpiresAt.After(s.now()) || t.Resource != s.cfg.Resource || !s.sessionValid(t.UserID, t.AuthTime) {
		return nil, ErrInvalidToken
	}
	return &AccessToken{UserID: t.UserID, ClientID: t.ClientID, GrantID: t.GrantID.Hex(), Scopes: t.Scopes, ExpiresAt: t.ExpiresAt}, nil
}

// Revoke traite /oauth/revoke (RFC 7009) : un refresh token ferme tout
// l'accès, un access token seulement lui-même. Un jeton inconnu ou d'un
// autre client est ignoré sans erreur.
func (s *Service) Revoke(ctx context.Context, raw, clientID string) error {
	var t token
	if s.tokens.FindOne(ctx, bson.M{"_id": hashToken(raw)}).Decode(&t) != nil || t.ClientID != clientID {
		return nil
	}
	if t.Kind == kindRefresh {
		return s.revokeGrant(ctx, t.GrantID)
	}
	_, err := s.tokens.DeleteOne(ctx, bson.M{"_id": t.Hash})
	return err
}

func (s *Service) revokeGrant(ctx context.Context, id bson.ObjectID) error {
	if _, err := s.grants.DeleteOne(ctx, bson.M{"_id": id}); err != nil {
		return err
	}
	_, err := s.tokens.DeleteMany(ctx, bson.M{"grant_id": id})
	return err
}
