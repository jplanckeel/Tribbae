package ai

import (
	"context"
	"encoding/json"
	"log"
	"net/http"
	"strings"
	"unicode/utf8"
)

// UserGetter récupère un utilisateur par son ID
type UserGetter interface {
	GetUser(ctx context.Context, userID string) (isPremium bool, err error)
}

// FolderCreator crée un dossier communautaire et retourne son ID
type FolderCreator func(ctx context.Context, ownerID, name string) (folderID string, err error)

// LinkCreator crée un lien dans un dossier
type LinkCreator func(ctx context.Context, ownerID string, link SuggestedLink, folderID string) error

type Handler struct {
	svc           *Service
	userGetter    UserGetter
	tokenParser   func(r *http.Request) (userID string, err error)
	folderCreator FolderCreator
	linkCreator   LinkCreator
}

func NewHandler(svc *Service, userGetter UserGetter, tokenParser func(r *http.Request) (string, error), fc FolderCreator, lc LinkCreator) *Handler {
	return &Handler{svc: svc, userGetter: userGetter, tokenParser: tokenParser, folderCreator: fc, linkCreator: lc}
}

type generateResponseWithFolder struct {
	Ideas    []SuggestedLink `json:"ideas"`
	FolderID string          `json:"folderId,omitempty"`
}

const (
	maxRequestBytes = 4 << 10
	maxPromptRunes  = 500
	// maxConcurrent borne le nombre de générations simultanées : Ollama traite
	// une requête à la fois, au-delà on répond 429 plutôt que d'empiler.
	maxConcurrent = 2
)

var slots = make(chan struct{}, maxConcurrent)

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

	select {
	case slots <- struct{}{}:
		defer func() { <-slots }()
	default:
		writeJSON(w, http.StatusTooManyRequests, map[string]string{"message": "AI is busy, please retry in a moment"})
		return
	}

	// Récupérer l'utilisateur pour vérifier son statut premium
	var isPremium bool
	if h.userGetter != nil {
		if premium, err := h.userGetter.GetUser(r.Context(), userID); err == nil {
			isPremium = premium
		}
	}

	// Le modèle est imposé par la configuration serveur : le client ne choisit pas.
	result, err := h.svc.Generate(r.Context(), req.Prompt, "", isPremium)
	if err != nil {
		log.Printf("ai: generate: %v", err)
		writeJSON(w, http.StatusBadGateway, map[string]string{"message": "idea generation failed"})
		return
	}

	writeJSON(w, http.StatusOK, generateResponseWithFolder{Ideas: result.Ideas})
}

// buildFolderName génère un nom de dossier à partir du prompt
func buildFolderName(prompt string) string {
	name := strings.TrimSpace(prompt)
	if len(name) > 60 {
		name = name[:60] + "..."
	}
	// Mettre la première lettre en majuscule
	if len(name) > 0 {
		name = strings.ToUpper(name[:1]) + name[1:]
	}
	return "✨ " + name
}
