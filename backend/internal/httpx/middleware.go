// Package httpx regroupe les middlewares HTTP du serveur (en-têtes de sécurité,
// CORS, limitation de débit, taille des requêtes).
package httpx

import (
	"encoding/json"
	"net"
	"net/http"
	"strings"
	"sync"
	"time"
)

// ClientIP retourne l'IP du client. Derrière Traefik, la dernière entrée de
// X-Forwarded-For est celle ajoutée par le proxy (les précédentes sont
// fournies par le client et donc falsifiables).
func ClientIP(r *http.Request) string {
	if xff := r.Header.Get("X-Forwarded-For"); xff != "" {
		parts := strings.Split(xff, ",")
		if ip := strings.TrimSpace(parts[len(parts)-1]); ip != "" {
			return ip
		}
	}
	host, _, err := net.SplitHostPort(r.RemoteAddr)
	if err != nil {
		return r.RemoteAddr
	}
	return host
}

// RateLimiter limite le nombre de requêtes par IP sur une fenêtre fixe.
type RateLimiter struct {
	mu     sync.Mutex
	limit  int
	window time.Duration
	hits   map[string]*bucket
}

type bucket struct {
	count int
	reset time.Time
}

// NewRateLimiter autorise limit requêtes par IP et par fenêtre.
func NewRateLimiter(limit int, window time.Duration) *RateLimiter {
	rl := &RateLimiter{limit: limit, window: window, hits: map[string]*bucket{}}
	go rl.gc()
	return rl
}

// Allow enregistre une requête pour key et indique si elle est acceptée.
func (rl *RateLimiter) Allow(key string) bool {
	now := time.Now()
	rl.mu.Lock()
	defer rl.mu.Unlock()
	b, ok := rl.hits[key]
	if !ok || now.After(b.reset) {
		rl.hits[key] = &bucket{count: 1, reset: now.Add(rl.window)}
		return true
	}
	b.count++
	return b.count <= rl.limit
}

func (rl *RateLimiter) gc() {
	for range time.Tick(rl.window) {
		now := time.Now()
		rl.mu.Lock()
		for k, b := range rl.hits {
			if now.After(b.reset) {
				delete(rl.hits, k)
			}
		}
		rl.mu.Unlock()
	}
}

// Limit applique le limiteur aux requêtes dont le chemin est dans paths.
func Limit(rl *RateLimiter, paths map[string]bool, next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if paths[r.URL.Path] && r.Method != http.MethodOptions && !rl.Allow(r.URL.Path+"|"+ClientIP(r)) {
			w.Header().Set("Content-Type", "application/json")
			w.Header().Set("Retry-After", "60")
			w.WriteHeader(http.StatusTooManyRequests)
			_ = json.NewEncoder(w).Encode(map[string]string{"message": "too many requests, please retry later"})
			return
		}
		next.ServeHTTP(w, r)
	})
}

// contentSecurityPolicy n'autorise que les ressources servies par l'application ;
// les images peuvent venir de n'importe quel site https (aperçus des liens).
// Exception : la mesure d'audience Umami (script et envoi des visites).
const contentSecurityPolicy = "default-src 'self'; " +
	"script-src 'self' https://cloud.umami.is; " +
	"style-src 'self' 'unsafe-inline'; " +
	"img-src 'self' https: data: blob:; " +
	"font-src 'self'; " +
	"connect-src 'self' https://gateway.umami.is; " +
	"object-src 'none'; " +
	"base-uri 'self'; " +
	"form-action 'self'; " +
	"frame-ancestors 'none'"

// SecurityHeaders ajoute les en-têtes de sécurité standards.
func SecurityHeaders(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		h := w.Header()
		h.Set("X-Content-Type-Options", "nosniff")
		h.Set("Referrer-Policy", "strict-origin-when-cross-origin")
		h.Set("X-Frame-Options", "DENY")
		h.Set("Permissions-Policy", "camera=(), microphone=(), geolocation=()")
		if !strings.HasPrefix(r.URL.Path, "/v1/") {
			h.Set("Content-Security-Policy", contentSecurityPolicy)
		}
		if r.TLS != nil || r.Header.Get("X-Forwarded-Proto") == "https" {
			h.Set("Strict-Transport-Security", "max-age=31536000; includeSubDomains")
		}
		next.ServeHTTP(w, r)
	})
}

// CORS n'autorise que les origines listées. Le web est servi sur la même
// origine que l'API et l'app Android n'utilise pas CORS : par défaut, aucune
// origine tierce n'est autorisée.
func CORS(allowed []string, next http.Handler) http.Handler {
	set := map[string]bool{}
	for _, o := range allowed {
		set[o] = true
	}
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		origin := r.Header.Get("Origin")
		if origin != "" && set[origin] {
			w.Header().Set("Access-Control-Allow-Origin", origin)
			w.Header().Set("Vary", "Origin")
			w.Header().Set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
			w.Header().Set("Access-Control-Allow-Headers", "Content-Type, Authorization")
			w.Header().Set("Access-Control-Max-Age", "600")
		}
		if r.Method == http.MethodOptions && origin != "" {
			w.WriteHeader(http.StatusNoContent)
			return
		}
		next.ServeHTTP(w, r)
	})
}

// MaxBody borne la taille du corps des requêtes.
func MaxBody(n int64, next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Body != nil {
			r.Body = http.MaxBytesReader(w, r.Body, n)
		}
		next.ServeHTTP(w, r)
	})
}

// CanonicalHost redirige définitivement tout ce qui est demandé sur un autre
// nom du site (ancien domaine, www) vers canonical (ex. https://tribbae.fr),
// en gardant le chemin et la requête : 301 pour les pages, 308 pour les autres
// méthodes (l'appel garde sa méthode et son corps). Seuls les noms listés sont
// redirigés, jamais les noms internes.
func CanonicalHost(canonical string, hosts []string, next http.Handler) http.Handler {
	redirect := map[string]bool{}
	for _, h := range hosts {
		if h = strings.ToLower(strings.TrimSpace(h)); h != "" {
			redirect[h] = true
		}
	}
	canonical = strings.TrimRight(canonical, "/")
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		host := strings.ToLower(r.Host)
		if h, _, err := net.SplitHostPort(host); err == nil {
			host = h
		}
		if redirect[host] {
			code := http.StatusPermanentRedirect
			if r.Method == http.MethodGet || r.Method == http.MethodHead {
				code = http.StatusMovedPermanently
			}
			http.Redirect(w, r, canonical+r.URL.RequestURI(), code)
			return
		}
		next.ServeHTTP(w, r)
	})
}
