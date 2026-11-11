package config

import (
	"errors"
	"log"
	"net/url"
	"os"
	"strconv"
	"strings"
)

type Config struct {
	MongoURI  string
	MongoDB   string
	JWTSecret string
	Port      string
	GRPCPort  string
	BaseURL   string
	// SiteURL : adresse publique du site (adresses canoniques, plan du site,
	// aperçus, liens des emails et des partages)
	SiteURL string
	// RedirectHosts : autres noms du site (ancien domaine, www), redirigés
	// vers SiteURL
	RedirectHosts []string
	OllamaURL     string
	OllamaModel   string
	// OllamaKeepAlive : durée de maintien du modèle en mémoire entre deux demandes
	OllamaKeepAlive string
	// OllamaNumThread : threads CPU pour l'inférence (0 = choix d'Ollama)
	OllamaNumThread int
	AdminPassword   string
	AllowedOrigins  []string
	// Emails transactionnels (Brevo) : sans clé, ils sont seulement journalisés
	BrevoAPIKey  string
	MailFrom     string
	MailFromName string
	// SupportEmail : adresse « Écrire au support » des emails
	SupportEmail string
	// MCPEnabled : serveur MCP (/mcp) et autorisation OAuth des applications
	// connectées (Claude). Désactivé par défaut : toutes ces routes répondent 404.
	MCPEnabled bool
	// OAuthRedirectURIs : seules adresses de retour qu'un client OAuth peut
	// enregistrer (comparaison exacte)
	OAuthRedirectURIs []string
	// MCPDailyQuota : idées qu'un compte peut ajouter par le serveur MCP sur 24 h
	MCPDailyQuota int
	// MCPAllowedOrigins : en-têtes Origin acceptés sur /mcp en plus de SITE_URL
	// (une requête sans Origin, de serveur à serveur, passe toujours)
	MCPAllowedOrigins []string
	// AndroidCertFingerprints : empreintes SHA-256 des certificats de l'app
	// Android (clé de signature Play, clé d'importation), publiées dans
	// /.well-known/assetlinks.json pour qu'Android ouvre les liens du site dans
	// l'app. Vide : le fichier répond 404.
	AndroidCertFingerprints []string
}

// insecureJWTSecret est la valeur d'exemple de .env.example : elle n'est
// acceptée que lorsque BASE_URL pointe vers la machine locale.
const insecureJWTSecret = "change-me-in-production"

func Load() *Config {
	cfg := &Config{
		MongoURI:  getEnv("MONGO_URI", "mongodb://localhost:27017"),
		MongoDB:   getEnv("MONGO_DB", "tribbae"),
		JWTSecret: getEnv("JWT_SECRET", insecureJWTSecret),
		Port:      getEnv("PORT", "8080"),
		GRPCPort:  getEnv("GRPC_PORT", "9090"),
		BaseURL:   getEnv("BASE_URL", "http://localhost:8080"),
		SiteURL:   getEnv("SITE_URL", "https://tribbae.fr"),
		OllamaURL: getEnv("OLLAMA_URL", "http://localhost:11434"),
		// Petit modèle par défaut : la génération tourne sur le CPU d'un petit VPS
		OllamaModel:     getEnv("OLLAMA_MODEL", "qwen3:1.7b"),
		OllamaKeepAlive: getEnv("OLLAMA_KEEP_ALIVE", "30m"),
		OllamaNumThread: getEnvInt("OLLAMA_NUM_THREAD", 0),
		AdminPassword:   os.Getenv("ADMIN_PASSWORD"),
		AllowedOrigins:  splitList(os.Getenv("CORS_ALLOWED_ORIGINS")),
		BrevoAPIKey:     os.Getenv("BREVO_API_KEY"),
		MailFrom:        getEnv("MAIL_FROM", "contact@tribbae.fr"),
		MailFromName:    getEnv("MAIL_FROM_NAME", "Tribbae"),
	}
	cfg.SupportEmail = getEnv("SUPPORT_EMAIL", cfg.MailFrom)
	cfg.RedirectHosts = splitList(getEnv("REDIRECT_HOSTS", "tribbae.bananaops.cloud,www.tribbae.fr"))
	cfg.MCPEnabled = getEnvBool("MCP_ENABLED", false)
	// Callback de claude.ai, et celui de claude.com annoncé par Anthropic
	cfg.OAuthRedirectURIs = splitList(getEnv("OAUTH_REDIRECT_URIS", "https://claude.ai/api/mcp/auth_callback,https://claude.com/api/mcp/auth_callback"))
	cfg.MCPDailyQuota = getEnvInt("MCP_DAILY_CREATE_QUOTA", 30)
	cfg.MCPAllowedOrigins = splitList(getEnv("MCP_ALLOWED_ORIGINS", "https://claude.ai,https://claude.com"))
	cfg.AndroidCertFingerprints = splitList(os.Getenv("ANDROID_CERT_FINGERPRINTS"))
	return cfg
}

// IsLocal indique si l'instance tourne en local (BASE_URL sur localhost).
func (c *Config) IsLocal() bool {
	u, err := url.Parse(c.BaseURL)
	if err != nil {
		return false
	}
	h := u.Hostname()
	return h == "localhost" || h == "127.0.0.1" || h == "::1"
}

// Validate refuse de démarrer une instance non locale avec le secret JWT
// d'exemple : quiconque le connaît peut forger un token pour n'importe quel compte.
func (c *Config) Validate() error {
	if c.IsLocal() {
		return nil
	}
	if c.JWTSecret == insecureJWTSecret || len(c.JWTSecret) < 16 {
		return errors.New("JWT_SECRET must be set to a random value (e.g. `openssl rand -hex 32`)")
	}
	if len(c.JWTSecret) < 32 {
		log.Printf("WARNING: JWT_SECRET is shorter than 32 characters; consider `openssl rand -hex 32`")
	}
	return nil
}

func getEnv(key, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return fallback
}

func getEnvInt(key string, fallback int) int {
	if v, err := strconv.Atoi(strings.TrimSpace(os.Getenv(key))); err == nil && v >= 0 {
		return v
	}
	return fallback
}

func getEnvBool(key string, fallback bool) bool {
	if v, err := strconv.ParseBool(strings.TrimSpace(os.Getenv(key))); err == nil {
		return v
	}
	return fallback
}

func splitList(v string) []string {
	var out []string
	for _, p := range strings.Split(v, ",") {
		if p = strings.TrimSpace(p); p != "" {
			out = append(out, p)
		}
	}
	return out
}
