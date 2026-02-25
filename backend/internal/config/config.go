package config

import (
	"os"
)

type Config struct {
	MongoURI  string
	MongoDB   string
	JWTSecret string
	Port      string
	GRPCPort  string
	BaseURL   string
}

func Load() *Config {
	return &Config{
		MongoURI:  getEnv("MONGO_URI", "mongodb://localhost:27017"),
		MongoDB:   getEnv("MONGO_DB", "tribbae"),
		JWTSecret: getEnv("JWT_SECRET", "change-me-in-production"),
		Port:      getEnv("PORT", "8080"),
		GRPCPort:  getEnv("GRPC_PORT", "9090"),
		BaseURL:   getEnv("BASE_URL", "http://localhost:8080"),
	}
}

func getEnv(key, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return fallback
}
