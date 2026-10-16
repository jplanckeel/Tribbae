package ai

import (
	"context"
	"encoding/json"
	"errors"
	"log"
	"net/http"
	"strings"
	"unicode/utf8"
)

// UserGetter indique si un utilisateur est premium.
type UserGetter interface {
	GetUser(ctx context.Context, userID string) (isPremium bool, err error)
}

// Handler sert POST /v1/ai/generate. Les idées sont seulement proposées :
// c'est le client qui enregistre celles que l'utilisateur garde.
type Handler struct {
	svc         *Service
	userGetter  UserGetter
	tokenParser func(r *http.Request) (userID string, err error)
}

func NewHandler(svc *Service, userGetter UserGetter, tokenParser func(r *http.Request) (string, error)) *Handler {
	return &Handler{svc: svc, userGetter: userGetter, tokenParser: tokenParser}
}

const (
	maxRequestBytes = 4 << 10
	maxPromptRunes  = 500
)

func writeJSON(w http.ResponseWriter, code int, v any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(code)
	_ = json.NewEncoder(w).Encode(v)
}

func (h *Handler) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		writeJSON(w, http.StatusMethodNotAllowed, map[string]string{"message": "method not allowed"})
		return
	}

	// La génération coûte cher (LLM + recherche web) : réservée aux comptes connectés.
	userID, err := h.tokenParser(r)
	if err != nil || userID == "" {
		writeJSON(w, http.StatusUnauthorized, map[string]string{"message": "authentication required"})
		return
	}

	var req GenerateRequest
	if err := json.NewDecoder(http.MaxBytesReader(w, r.Body, maxRequestBytes)).Decode(&req); err != nil {
		writeJSON(w, http.StatusBadRequest, map[string]string{"message": "invalid request body"})
		return
	}
	req.Prompt = strings.TrimSpace(req.Prompt)
	if req.Prompt == "" {
		writeJSON(w, http.StatusBadRequest, map[string]string{"message": "prompt is required"})
		return
	}
	if utf8.RuneCountInString(req.Prompt) > maxPromptRunes {
		writeJSON(w, http.StatusBadRequest, map[string]string{"message": "prompt is too long"})
		return
	}

	var isPremium bool
	if h.userGetter != nil {
		if premium, err := h.userGetter.GetUser(r.Context(), userID); err == nil {
			isPremium = premium
		}
	}

	result, err := h.svc.Generate(r.Context(), req.Prompt, isPremium)
	switch {
	case errors.Is(err, ErrBusy):
		w.Header().Set("Retry-After", "30")
		writeJSON(w, http.StatusTooManyRequests, map[string]string{"message": "AI is busy, please retry in a moment"})
	case err != nil:
		log.Printf("ai: generate: %v", err)
		writeJSON(w, http.StatusBadGateway, map[string]string{"message": "idea generation failed"})
	default:
		writeJSON(w, http.StatusOK, result)
	}
}
