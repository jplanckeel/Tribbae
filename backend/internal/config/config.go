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
	MongoURI    string
	MongoDB     string
	JWTSecret   string
	Port        string
	GRPCPort    string
	BaseURL     string
	OllamaURL   string
	OllamaModel string
	// OllamaKeepAlive : durée de maintien du modèle en mémoire entre deux demandes
	OllamaKeepAlive string
	// OllamaNumThread : threads CPU pour l'inférence (0 = choix d'Ollama)
	OllamaNumThread int
	SearxURL        string
	// OllamaAPIKey : clé ollama.com pour l'API de recherche web (facultative)
	OllamaAPIKey   string
	GeminiAPIKey   string
	GeminiModel    string
	AdminPassword  string
	AllowedOrigins []string
}

// insecureJWTSecret est la valeur d'exemple de .env.example : elle n'est
// acceptée que lorsque BASE_URL pointe vers la machine locale.
const insecureJWTSecret = "change-me-in-production"

func Load() *Config {
	return &Config{
		MongoURI:  getEnv("MONGO_URI", "mongodb://localhost:27017"),
		MongoDB:   getEnv("MONGO_DB", "tribbae"),
		JWTSecret: getEnv("JWT_SECRET", insecureJWTSecret),
		Port:      getEnv("PORT", "8080"),
		GRPCPort:  getEnv("GRPC_PORT", "9090"),
		BaseURL:   getEnv("BASE_URL", "http://localhost:8080"),
		OllamaURL: getEnv("OLLAMA_URL", "http://localhost:11434"),
		// Petit modèle par défaut : la génération tourne sur le CPU d'un petit VPS
		OllamaModel:     getEnv("OLLAMA_MODEL", "qwen3:1.7b"),
		OllamaKeepAlive: getEnv("OLLAMA_KEEP_ALIVE", "30m"),
		OllamaNumThread: getEnvInt("OLLAMA_NUM_THREAD", 0),
		SearxURL:        getEnv("SEARXNG_URL", "http://localhost:8888"),
		OllamaAPIKey:    getEnv("OLLAMA_API_KEY", ""),
		GeminiAPIKey:    getEnv("GEMINI_API_KEY", ""),
		GeminiModel:     getEnv("GEMINI_MODEL", "gemini-2.5-flash"),
		AdminPassword:   os.Getenv("ADMIN_PASSWORD"),
		AllowedOrigins:  splitList(os.Getenv("CORS_ALLOWED_ORIGINS")),
	}
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

func splitList(v string) []string {
	var out []string
	for _, p := range strings.Split(v, ",") {
		if p = strings.TrimSpace(p); p != "" {
			out = append(out, p)
		}
	}
	return out
}
