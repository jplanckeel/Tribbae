package oauth

import (
	"context"
	"crypto/sha256"
	"encoding/base64"
	"errors"
	"net/url"
	"strings"
	"testing"
	"time"

	"go.mongodb.org/mongo-driver/v2/bson"
	"go.mongodb.org/mongo-driver/v2/mongo"
	"go.mongodb.org/mongo-driver/v2/mongo/options"

	"github.com/tribbae/backend/internal/auth"
)

const (
	testIssuer   = "https://tribbae.example"
	testResource = "https://tribbae.example/mcp"
	testCallback = "https://claude.ai/api/mcp/auth_callback"
	testVerifier = "dBjftJeZ4CVP-mJ92K-6-zDx8n6pXhUoq7o2Yc1dBK1oBx3ppU1zr"
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

func newTestService(t *testing.T) (*Service, *mongo.Database) {
	db := setupTestDB(t)
	svc := NewService(db, Config{Issuer: testIssuer, Resource: testResource, RedirectURIs: []string{testCallback}})
	return svc, db
}

func challenge(verifier string) string {
	sum := sha256.Sum256([]byte(verifier))
	return base64.RawURLEncoding.EncodeToString(sum[:])
}

func register(t *testing.T, svc *Service) *Client {
	t.Helper()
	c, err := svc.Register(context.Background(), RegistrationRequest{RedirectURIs: []string{testCallback}, ClientName: "Claude"})
	if err != nil {
		t.Fatalf("register: %v", err)
	}
	return c
}

// approve fait le parcours jusqu'au code : demande, accord de l'utilisateur.
func approve(t *testing.T, svc *Service, c *Client, userID string, p AuthorizeParams) string {
	t.Helper()
	ctx := context.Background()
	client, redirect, err := svc.Client(ctx, c.ID, testCallback)
	if err != nil {
		t.Fatalf("client: %v", err)
	}
	if p.ResponseType == "" {
		p.ResponseType = "code"
	}
	if p.CodeChallengeMethod == "" {
		p.CodeChallengeMethod = "S256"
	}
	if p.CodeChallenge == "" {
		p.CodeChallenge = challenge(testVerifier)
	}
	id, err := svc.Authorize(ctx, client, redirect, p)
	if err != nil {
		t.Fatalf("authorize: %v", err)
	}
	back, err := svc.Decide(ctx, id, userID, true)
	if err != nil {
		t.Fatalf("decide: %v", err)
	}
	u, _ := url.Parse(back)
	if u.Query().Get("iss") != testIssuer || u.Query().Get("state") != p.State {
		t.Fatalf("retour sans iss ni state : %s", back)
	}
	return u.Query().Get("code")
}

func exchange(svc *Service, c *Client, code, verifier string) (*TokenResponse, error) {
	return svc.Token(context.Background(), TokenRequest{
		GrantType: "authorization_code", Code: code, RedirectURI: testCallback, ClientID: c.ID, CodeVerifier: verifier, Resource: testResource,
	})
}

func refresh(svc *Service, c *Client, rt string) (*TokenResponse, error) {
	return svc.Token(context.Background(), TokenRequest{GrantType: "refresh_token", RefreshToken: rt, ClientID: c.ID})
}

func oauthCode(err error) string {
	var oe *Error
	if errors.As(err, &oe) {
		return oe.Code
	}
	return ""
}

func TestRegisterOnlyAllowsWhitelistedPublicClients(t *testing.T) {
	svc, _ := newTestService(t)
	ctx := context.Background()
	cases := []struct {
		name string
		req  RegistrationRequest
		want string
	}{
		{"sans adresse de retour", RegistrationRequest{}, "invalid_redirect_uri"},
		{"adresse hors liste", RegistrationRequest{RedirectURIs: []string{"https://evil.example/cb"}}, "invalid_redirect_uri"},
		{"même hôte, autre chemin", RegistrationRequest{RedirectURIs: []string{"https://claude.ai/api/mcp/auth_callback/x"}}, "invalid_redirect_uri"},
		{"boucle locale", RegistrationRequest{RedirectURIs: []string{"http://localhost:3118/callback"}}, "invalid_redirect_uri"},
		{"client confidentiel", RegistrationRequest{RedirectURIs: []string{testCallback}, TokenEndpointAuthMethod: "client_secret_basic"}, "invalid_client_metadata"},
		{"client_credentials", RegistrationRequest{RedirectURIs: []string{testCallback}, GrantTypes: []string{"client_credentials"}}, "invalid_client_metadata"},
		{"implicit", RegistrationRequest{RedirectURIs: []string{testCallback}, ResponseTypes: []string{"token"}}, "invalid_client_metadata"},
	}
	for _, tc := range cases {
		if _, err := svc.Register(ctx, tc.req); oauthCode(err) != tc.want {
			t.Errorf("%s : %v, want %s", tc.name, err, tc.want)
		}
	}
	c, err := svc.Register(ctx, RegistrationRequest{RedirectURIs: []string{testCallback}, ClientName: "Claude\n‮evil", TokenEndpointAuthMethod: "none"})
	if err != nil {
		t.Fatal(err)
	}
	if !strings.HasPrefix(c.ID, clientPrefix) || c.Name != "Claude evil" || c.UnusedExpiresAt == nil {
		t.Errorf("client = %+v", c)
	}
}

func TestAuthorizeRequiresPKCEAndTheMCPResource(t *testing.T) {
	svc, _ := newTestService(t)
	ctx := context.Background()
	c := register(t, svc)
	if _, _, err := svc.Client(ctx, c.ID, "https://evil.example/cb"); err == nil {
		t.Error("adresse de retour non enregistrée acceptée")
	}
	if _, _, err := svc.Client(ctx, "tbm_client_unknown", testCallback); err == nil {
		t.Error("client inconnu accepté")
	}
	client, redirect, err := svc.Client(ctx, c.ID, "")
	if err != nil || redirect != testCallback {
		t.Fatalf("adresse unique non déduite : %v %q", err, redirect)
	}
	ok := AuthorizeParams{ResponseType: "code", CodeChallenge: challenge(testVerifier), CodeChallengeMethod: "S256"}
	cases := []struct {
		name string
		edit func(p *AuthorizeParams)
		want string
	}{
		{"sans PKCE", func(p *AuthorizeParams) { p.CodeChallenge, p.CodeChallengeMethod = "", "" }, "invalid_request"},
		{"PKCE plain", func(p *AuthorizeParams) { p.CodeChallengeMethod = "plain" }, "invalid_request"},
		{"challenge trop court", func(p *AuthorizeParams) { p.CodeChallenge = "abc" }, "invalid_request"},
		{"response_type token", func(p *AuthorizeParams) { p.ResponseType = "token" }, "unsupported_response_type"},
		{"scope admin", func(p *AuthorizeParams) { p.Scope = "links:read admin" }, "invalid_scope"},
		{"autre ressource", func(p *AuthorizeParams) { p.Resource = "https://tribbae.example/v1" }, "invalid_target"},
	}
	for _, tc := range cases {
		p := ok
		tc.edit(&p)
		if _, err := svc.Authorize(ctx, client, redirect, p); oauthCode(err) != tc.want {
			t.Errorf("%s : %v, want %s", tc.name, err, tc.want)
		}
	}
}

func TestConsentShowsTheRequestAndDenyRedirectsWithAnError(t *testing.T) {
	svc, _ := newTestService(t)
	ctx := context.Background()
	c := register(t, svc)
	client, redirect, _ := svc.Client(ctx, c.ID, testCallback)
	id, err := svc.Authorize(ctx, client, redirect, AuthorizeParams{ResponseType: "code", CodeChallenge: challenge(testVerifier), CodeChallengeMethod: "S256", Scope: "links:read offline_access", State: "xyz"})
	if err != nil {
		t.Fatal(err)
	}
	info, err := svc.ConsentInfo(ctx, id)
	if err != nil || info.RedirectHost != "claude.ai" || info.ClientName != "Claude" || len(info.Scopes) != 1 || info.Scopes[0] != ScopeLinksRead {
		t.Fatalf("consent = %+v, %v", info, err)
	}
	back, err := svc.Decide(ctx, id, "user1", false)
	if err != nil || !strings.Contains(back, "error=access_denied") || !strings.Contains(back, "state=xyz") || strings.Contains(back, "code=") {
		t.Fatalf("refus : %s %v", back, err)
	}
	if _, err := svc.Decide(ctx, id, "user1", true); !errors.Is(err, ErrRequestNotFound) {
		t.Errorf("demande déjà refusée puis acceptée : %v", err)
	}
	if _, err := svc.ConsentInfo(ctx, "inconnue"); !errors.Is(err, ErrRequestNotFound) {
		t.Errorf("demande inconnue : %v", err)
	}
}

func TestCodeExchangeChecksPKCEAndIsSingleUse(t *testing.T) {
	svc, _ := newTestService(t)
	ctx := context.Background()
	c := register(t, svc)

	// Mauvais verifier : refusé
	code := approve(t, svc, c, "user1", AuthorizeParams{})
	if _, err := exchange(svc, c, code, strings.Repeat("a", 43)); oauthCode(err) != "invalid_grant" {
		t.Errorf("mauvais code_verifier : %v", err)
	}
	// Le code est grillé même après un échec
	if _, err := exchange(svc, c, code, testVerifier); oauthCode(err) != "invalid_grant" {
		t.Errorf("code réutilisé après un échec : %v", err)
	}

	code = approve(t, svc, c, "user1", AuthorizeParams{State: "s"})
	other := register(t, svc)
	if _, err := exchange(svc, other, code, testVerifier); oauthCode(err) != "invalid_grant" {
		t.Errorf("code d'un autre client : %v", err)
	}

	code = approve(t, svc, c, "user1", AuthorizeParams{})
	if _, err := svc.Token(ctx, TokenRequest{GrantType: "authorization_code", Code: code, RedirectURI: testCallback, ClientID: c.ID, CodeVerifier: testVerifier, Resource: "https://other.example/mcp"}); oauthCode(err) != "invalid_target" {
		t.Errorf("autre ressource à l'échange : %v", err)
	}
	tok, err := exchange(svc, c, code, testVerifier)
	if err != nil {
		t.Fatalf("échange : %v", err)
	}
	if !strings.HasPrefix(tok.AccessToken, accessPrefix) || !strings.HasPrefix(tok.RefreshToken, refreshPrefix) || tok.ExpiresIn != 900 || tok.TokenType != "Bearer" {
		t.Errorf("réponse = %+v", tok)
	}
	at, err := svc.VerifyAccessToken(ctx, tok.AccessToken)
	if err != nil || at.UserID != "user1" || at.ClientID != c.ID || len(at.Scopes) != 3 {
		t.Fatalf("access token : %+v %v", at, err)
	}
	// Le client a servi : il n'est plus effacé automatiquement
	var stored Client
	_ = svc.clients.FindOne(ctx, bson.M{"_id": c.ID}).Decode(&stored)
	if stored.UnusedExpiresAt != nil {
		t.Error("client autorisé encore promis à l'effacement")
	}

	// Le même code présenté une seconde fois : refusé, et l'accès qu'il a
	// ouvert est révoqué (il a fuité)
	if _, err := exchange(svc, c, code, testVerifier); oauthCode(err) != "invalid_grant" {
		t.Errorf("code rejoué : %v", err)
	}
	if _, err := svc.VerifyAccessToken(ctx, tok.AccessToken); !errors.Is(err, ErrInvalidToken) {
		t.Errorf("jeton encore valide après un code rejoué : %v", err)
	}
}

func TestRefreshRotatesAndReuseRevokesEverything(t *testing.T) {
	svc, _ := newTestService(t)
	ctx := context.Background()
	c := register(t, svc)
	first, err := exchange(svc, c, approve(t, svc, c, "user1", AuthorizeParams{}), testVerifier)
	if err != nil {
		t.Fatal(err)
	}
	second, err := refresh(svc, c, first.RefreshToken)
	if err != nil {
		t.Fatalf("refresh : %v", err)
	}
	if second.RefreshToken == first.RefreshToken || second.AccessToken == first.AccessToken {
		t.Fatal("refresh token non remplacé")
	}
	if _, err := svc.VerifyAccessToken(ctx, second.AccessToken); err != nil {
		t.Fatalf("nouvel access token : %v", err)
	}
	// Un autre client ne peut pas utiliser ce refresh token
	if _, err := refresh(svc, register(t, svc), second.RefreshToken); oauthCode(err) != "invalid_grant" {
		t.Errorf("refresh par un autre client : %v", err)
	}

	// L'ancien refresh token revient : vol probable, tout l'accès est révoqué
	if _, err := refresh(svc, c, first.RefreshToken); oauthCode(err) != "invalid_grant" {
		t.Errorf("refresh token réutilisé : %v", err)
	}
	if _, err := svc.VerifyAccessToken(ctx, second.AccessToken); !errors.Is(err, ErrInvalidToken) {
		t.Error("access token encore valide après la réutilisation")
	}
	if n, _ := svc.grants.CountDocuments(ctx, bson.M{}); n != 0 {
		t.Errorf("%d accès restants, want 0", n)
	}
}

func TestRefreshCannotWidenScopes(t *testing.T) {
	svc, _ := newTestService(t)
	c := register(t, svc)
	tok, err := exchange(svc, c, approve(t, svc, c, "user1", AuthorizeParams{Scope: "folders:read"}), testVerifier)
	if err != nil {
		t.Fatal(err)
	}
	if tok.Scope != ScopeFoldersRead {
		t.Errorf("scope = %q", tok.Scope)
	}
	_, err = svc.Token(context.Background(), TokenRequest{GrantType: "refresh_token", RefreshToken: tok.RefreshToken, ClientID: c.ID, Scope: "folders:read links:write"})
	if oauthCode(err) != "invalid_scope" {
		t.Errorf("élargissement des droits : %v", err)
	}
}

func TestAccessTokenAudienceAndExpiry(t *testing.T) {
	svc, _ := newTestService(t)
	ctx := context.Background()
	c := register(t, svc)
	tok, err := exchange(svc, c, approve(t, svc, c, "user1", AuthorizeParams{}), testVerifier)
	if err != nil {
		t.Fatal(err)
	}
	// Mauvaise audience : le même jeton présenté à une autre ressource
	other := NewService(svc.clients.Database(), Config{Issuer: testIssuer, Resource: "https://autre.example/mcp", RedirectURIs: []string{testCallback}})
	if _, err := other.VerifyAccessToken(ctx, tok.AccessToken); !errors.Is(err, ErrInvalidToken) {
		t.Errorf("jeton accepté par une autre ressource : %v", err)
	}
	// Le refresh token n'est pas un access token
	if _, err := svc.VerifyAccessToken(ctx, tok.RefreshToken); !errors.Is(err, ErrInvalidToken) {
		t.Errorf("refresh token accepté comme access token : %v", err)
	}
	// Expiré au bout de 15 minutes
	svc.now = func() time.Time { return time.Now().Add(AccessTTL + time.Second) }
	if _, err := svc.VerifyAccessToken(ctx, tok.AccessToken); !errors.Is(err, ErrInvalidToken) {
		t.Errorf("access token expiré accepté : %v", err)
	}
}

func TestRefreshTokenNeverOutlivesTheGrant(t *testing.T) {
	svc, _ := newTestService(t)
	c := register(t, svc)
	tok, err := exchange(svc, c, approve(t, svc, c, "user1", AuthorizeParams{}), testVerifier)
	if err != nil {
		t.Fatal(err)
	}
	// 31 jours sans servir : le refresh token a expiré
	svc.now = func() time.Time { return time.Now().Add(RefreshTTL + time.Hour) }
	if _, err := refresh(svc, c, tok.RefreshToken); oauthCode(err) != "invalid_grant" {
		t.Errorf("refresh token expiré : %v", err)
	}
}

func TestRevoke(t *testing.T) {
	svc, _ := newTestService(t)
	ctx := context.Background()
	c := register(t, svc)
	tok, err := exchange(svc, c, approve(t, svc, c, "user1", AuthorizeParams{}), testVerifier)
	if err != nil {
		t.Fatal(err)
	}
	// Un autre client ne peut rien révoquer
	_ = svc.Revoke(ctx, tok.RefreshToken, "tbm_client_other")
	if _, err := svc.VerifyAccessToken(ctx, tok.AccessToken); err != nil {
		t.Fatalf("révoqué par un autre client : %v", err)
	}
	if err := svc.Revoke(ctx, tok.RefreshToken, c.ID); err != nil {
		t.Fatal(err)
	}
	if _, err := svc.VerifyAccessToken(ctx, tok.AccessToken); !errors.Is(err, ErrInvalidToken) {
		t.Error("access token valide après révocation du refresh token")
	}
	if _, err := refresh(svc, c, tok.RefreshToken); oauthCode(err) != "invalid_grant" {
		t.Errorf("refresh après révocation : %v", err)
	}
}

// Un jeton MCP ne marche pas sur l'API de l'app, ni un JWT de l'app sur /mcp ;
// un changement de mot de passe ferme aussi les accès des applications.
func TestTokensAreNotInterchangeableWithAppSessions(t *testing.T) {
	svc, db := newTestService(t)
	ctx := context.Background()
	users := db.Collection("users")
	authSvc := auth.NewService(users, "test-secret-test-secret-test-secret")
	user, appJWT, err := authSvc.Register(ctx, "alice@example.com", "motdepasse123", "Alice")
	if err != nil {
		t.Fatal(err)
	}
	svc.SetSessionCheck(authSvc.SessionValid)
	c := register(t, svc)
	tok, err := exchange(svc, c, approve(t, svc, c, user.ID.Hex(), AuthorizeParams{}), testVerifier)
	if err != nil {
		t.Fatal(err)
	}
	if _, err := authSvc.ValidateToken(tok.AccessToken); err == nil {
		t.Error("jeton MCP accepté par l'API de l'app")
	}
	if _, err := svc.VerifyAccessToken(ctx, appJWT); !errors.Is(err, ErrInvalidToken) {
		t.Error("JWT de l'app accepté sur /mcp")
	}
	if _, err := svc.VerifyAccessToken(ctx, tok.AccessToken); err != nil {
		t.Fatalf("jeton MCP valide refusé : %v", err)
	}
	// Mot de passe changé après l'autorisation
	if _, err := users.UpdateOne(ctx, bson.M{"_id": user.ID}, bson.M{"$set": bson.M{"password_changed_at": time.Now().Add(time.Second)}}); err != nil {
		t.Fatal(err)
	}
	if _, err := svc.VerifyAccessToken(ctx, tok.AccessToken); !errors.Is(err, ErrInvalidToken) {
		t.Error("jeton MCP encore valide après un changement de mot de passe")
	}
	if _, err := refresh(svc, c, tok.RefreshToken); oauthCode(err) != "invalid_grant" {
		t.Errorf("refresh après un changement de mot de passe : %v", err)
	}
}
