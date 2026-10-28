// Package mcpserver est le serveur MCP distant de Tribbae (/mcp, transport
// HTTP streamable), branché comme connecteur personnalisé dans Claude.
//
// Il est appelé par un agent qui lit des pages web non fiables : il ne fait
// que lister les dossiers et les idées, et ajouter des idées privées dans un
// dossier où l'utilisateur peut écrire. Aucun outil ne modifie, supprime ou
// publie. Les outils appellent directement les services link et folder, avec
// les mêmes contrôles de droits que l'app.
package mcpserver

import (
	"context"
	"encoding/json"
	"errors"
	"log"
	"net/http"
	"net/url"
	"slices"
	"time"

	"github.com/modelcontextprotocol/go-sdk/auth"
	"github.com/modelcontextprotocol/go-sdk/mcp"

	"github.com/tribbae/backend/internal/folder"
	"github.com/tribbae/backend/internal/httpx"
	"github.com/tribbae/backend/internal/link"
	"github.com/tribbae/backend/internal/oauth"
)

// Path est l'adresse du serveur MCP.
const Path = "/mcp"

const (
	// maxBody : un appel d'outil tient largement en 64 Kio
	maxBody = 64 << 10
	// Limites de débit : par accès (jeton) et par compte, toutes applications confondues
	perGrantPerMinute = 60
	perUserPerMinute  = 120
	// DefaultDailyQuota : idées créées par compte sur 24 h glissantes
	DefaultDailyQuota = 30
)

// TokenVerifier vérifie un access token OAuth (oauth.Service).
type TokenVerifier interface {
	VerifyAccessToken(ctx context.Context, raw string) (*oauth.AccessToken, error)
}

// Config règle le serveur MCP.
type Config struct {
	// SiteURL : adresse publique du site (liens vers les idées créées)
	SiteURL string
	// ResourceMetadataURL : métadonnées RFC 9728, annoncées sur chaque 401
	ResourceMetadataURL string
	// AllowedOrigins : en-têtes Origin acceptés (DNS rebinding, appels depuis
	// une page web) ; une requête sans Origin (serveur à serveur) passe
	AllowedOrigins []string
	// RequireHTTPS refuse les requêtes arrivées en clair (hors local)
	RequireHTTPS bool
	// DailyQuota : créations par compte sur 24 h (DefaultDailyQuota si 0)
	DailyQuota int
}

// Server regroupe les outils et ce dont ils ont besoin.
type Server struct {
	cfg     Config
	links   *link.Service
	folders *folder.Service
	audit   *Audit
	grants  *httpx.RateLimiter
	users   *httpx.RateLimiter
	now     func() time.Time
}

func New(cfg Config, links *link.Service, folders *folder.Service, audit *Audit) *Server {
	if cfg.DailyQuota <= 0 {
		cfg.DailyQuota = DefaultDailyQuota
	}
	return &Server{
		cfg:     cfg,
		links:   links,
		folders: folders,
		audit:   audit,
		grants:  httpx.NewRateLimiter(perGrantPerMinute, time.Minute),
		users:   httpx.NewRateLimiter(perUserPerMinute, time.Minute),
		now:     time.Now,
	}
}

// instructions sont envoyées au client à l'initialisation : factuelles, sans
// aucun contenu d'utilisateur.
const instructions = "Tribbae stores family ideas (activities, outings, gifts, recipes, books) in folders. " +
	"Use list_folders to find a writable folder, list_links to avoid adding a duplicate, then create_link. " +
	"Ideas added here are always private; only the account owner can publish them from the Tribbae app."

// Handler retourne le handler HTTP de /mcp : contrôle de l'Origin et du
// HTTPS, jeton OAuth, limites de débit, puis le transport MCP.
func (s *Server) Handler(tokens TokenVerifier) http.Handler {
	server := mcp.NewServer(&mcp.Implementation{Name: "tribbae", Title: "Tribbae", Version: "1.0.0"}, &mcp.ServerOptions{Instructions: instructions})
	s.addTools(server)
	var h http.Handler = mcp.NewStreamableHTTPHandler(func(*http.Request) *mcp.Server { return server }, &mcp.StreamableHTTPOptions{
		// Sans état : chaque requête porte son jeton, rien n'est gardé en mémoire
		Stateless:    true,
		JSONResponse: true,
	})
	h = s.rateLimit(h)
	h = auth.RequireBearerToken(func(ctx context.Context, raw string, _ *http.Request) (*auth.TokenInfo, error) {
		t, err := tokens.VerifyAccessToken(ctx, raw)
		if err != nil {
			return nil, auth.ErrInvalidToken
		}
		return &auth.TokenInfo{
			Scopes:     t.Scopes,
			Expiration: t.ExpiresAt,
			UserID:     t.UserID,
			Extra:      map[string]any{"client_id": t.ClientID, "grant_id": t.GrantID},
		}, nil
	}, &auth.RequireBearerTokenOptions{ResourceMetadataURL: s.cfg.ResourceMetadataURL})(h)
	h = s.checkOrigin(h)
	return http.MaxBytesHandler(h, maxBody)
}

func writeError(w http.ResponseWriter, status int, msg string) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(map[string]string{"error": msg})
}

// checkOrigin refuse le HTTP en clair et les Origin inconnus : une page web
// ouverte dans un navigateur ne doit pas pouvoir parler au serveur (DNS
// rebinding). Les appels de Claude se font de serveur à serveur, sans Origin.
func (s *Server) checkOrigin(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if s.cfg.RequireHTTPS && !oauth.IsHTTPS(r) {
			writeError(w, http.StatusBadRequest, "HTTPS is required")
			return
		}
		if origin := r.Header.Get("Origin"); origin != "" && !s.originAllowed(origin) {
			writeError(w, http.StatusForbidden, "origin not allowed")
			return
		}
		next.ServeHTTP(w, r)
	})
}

func (s *Server) originAllowed(origin string) bool {
	u, err := url.Parse(origin)
	if err != nil || u.Scheme == "" || u.Host == "" {
		return false
	}
	return slices.Contains(s.cfg.AllowedOrigins, u.Scheme+"://"+u.Host)
}

// rateLimit limite par accès (jeton) et par compte ; le refus est journalisé
// dans l'audit.
func (s *Server) rateLimit(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		info := auth.TokenInfoFromContext(r.Context())
		if info == nil {
			writeError(w, http.StatusUnauthorized, "missing token")
			return
		}
		c := callerOf(info)
		if !s.grants.Allow("grant|"+c.grantID) || !s.users.Allow("user|"+c.userID) {
			s.audit.Record(r.Context(), c, "", OutcomeRateLimited)
			w.Header().Set("Retry-After", "60")
			writeError(w, http.StatusTooManyRequests, "too many requests, please retry later")
			return
		}
		next.ServeHTTP(w, r)
	})
}

// caller identifie l'appelant d'un outil.
type caller struct {
	userID   string
	clientID string
	grantID  string
	scopes   []string
}

func callerOf(info *auth.TokenInfo) caller {
	c := caller{userID: info.UserID, scopes: info.Scopes}
	c.clientID, _ = info.Extra["client_id"].(string)
	c.grantID, _ = info.Extra["grant_id"].(string)
	return c
}

var errNoToken = errors.New("missing token")

func callerFrom(req *mcp.CallToolRequest) (caller, error) {
	if req == nil || req.Extra == nil || req.Extra.TokenInfo == nil || req.Extra.TokenInfo.UserID == "" {
		return caller{}, errNoToken
	}
	return callerOf(req.Extra.TokenInfo), nil
}

// logError journalise une erreur interne sans contenu d'utilisateur.
func logError(tool string, err error) {
	log.Printf("mcp: %s: %v", tool, err)
}
