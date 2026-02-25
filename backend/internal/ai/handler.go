package ai

import (
	"context"
	"encoding/json"
	"net/http"
	"strings"
)

// FolderCreator crée un dossier communautaire et retourne son ID
type FolderCreator func(ctx context.Context, ownerID, name string) (folderID string, err error)

// LinkCreator crée un lien dans un dossier
type LinkCreator func(ctx context.Context, ownerID string, link SuggestedLink, folderID string) error

type Handler struct {
	svc           *Service
	tokenParser   func(r *http.Request) (userID string, err error)
	folderCreator FolderCreator
	linkCreator   LinkCreator
}

func NewHandler(svc *Service, tokenParser func(r *http.Request) (string, error), fc FolderCreator, lc LinkCreator) *Handler {
	return &Handler{svc: svc, tokenParser: tokenParser, folderCreator: fc, linkCreator: lc}
}

type generateResponseWithFolder struct {
	Ideas    []SuggestedLink `json:"ideas"`
	FolderID string          `json:"folderId,omitempty"`
}

func (h *Handler) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
		return
	}

	var req GenerateRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "invalid request body", http.StatusBadRequest)
		return
	}
	if req.Prompt == "" {
		http.Error(w, "prompt is required", http.StatusBadRequest)
		return
	}

	result, err := h.svc.Generate(r.Context(), req.Prompt, req.Model)
	if err != nil {
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusBadGateway)
		json.NewEncoder(w).Encode(map[string]string{"error": err.Error()})
		return
	}

	resp := generateResponseWithFolder{Ideas: result.Ideas}

	// Créer automatiquement un dossier communautaire avec les idées
	if h.tokenParser != nil && h.folderCreator != nil && h.linkCreator != nil && len(result.Ideas) > 0 {
		if userID, err := h.tokenParser(r); err == nil && userID != "" {
			folderName := buildFolderName(req.Prompt)
			if folderID, err := h.folderCreator(r.Context(), userID, folderName); err == nil {
				resp.FolderID = folderID
				for _, idea := range result.Ideas {
					_ = h.linkCreator(r.Context(), userID, idea, folderID)
				}
			}
		}
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(resp)
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
