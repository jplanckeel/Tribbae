package account

import (
	"context"
	"encoding/json"
	"errors"
	"log"
	"net/http"
	"time"
)

// PasswordChecker vérifie le mot de passe d'un compte (auth.Service).
type PasswordChecker interface {
	CheckPassword(ctx context.Context, userID, password string) error
}

// Handler sert, pour le compte connecté :
//   - GET  /v1/account/export : toutes ses données, en fichier JSON ;
//   - POST /v1/account/delete : suppression définitive, mot de passe exigé.
type Handler struct {
	svc         *Service
	passwords   PasswordChecker
	tokenParser func(r *http.Request) (string, error)
}

func NewHandler(svc *Service, passwords PasswordChecker, tokenParser func(r *http.Request) (string, error)) *Handler {
	return &Handler{svc: svc, passwords: passwords, tokenParser: tokenParser}
}

const (
	ExportPath = "/v1/account/export"
	DeletePath = "/v1/account/delete"
)

func writeJSON(w http.ResponseWriter, code int, v any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(code)
	_ = json.NewEncoder(w).Encode(v)
}

func (h *Handler) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	userID, err := h.tokenParser(r)
	if err != nil || userID == "" {
		writeJSON(w, http.StatusUnauthorized, map[string]string{"message": "authentication required"})
		return
	}
	w.Header().Set("Cache-Control", "no-store")
	switch {
	case r.URL.Path == ExportPath && r.Method == http.MethodGet:
		h.export(w, r, userID)
	case r.URL.Path == DeletePath && r.Method == http.MethodPost:
		h.delete(w, r, userID)
	default:
		writeJSON(w, http.StatusMethodNotAllowed, map[string]string{"message": "method not allowed"})
	}
}

func (h *Handler) export(w http.ResponseWriter, r *http.Request, userID string) {
	data, err := h.svc.Export(r.Context(), userID)
	if err != nil {
		log.Printf("account: export failed: %v", err)
		writeJSON(w, http.StatusInternalServerError, map[string]string{"message": "export failed"})
		return
	}
	name := "tribbae-mes-donnees-" + time.Now().Format("2006-01-02") + ".json"
	w.Header().Set("Content-Disposition", `attachment; filename="`+name+`"`)
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	enc := json.NewEncoder(w)
	enc.SetIndent("", "  ")
	_ = enc.Encode(data)
}

func (h *Handler) delete(w http.ResponseWriter, r *http.Request, userID string) {
	var req struct {
		Password string `json:"password"`
	}
	if err := json.NewDecoder(http.MaxBytesReader(w, r.Body, 4<<10)).Decode(&req); err != nil || req.Password == "" {
		writeJSON(w, http.StatusBadRequest, map[string]string{"message": "password is required"})
		return
	}
	if err := h.passwords.CheckPassword(r.Context(), userID, req.Password); err != nil {
		writeJSON(w, http.StatusForbidden, map[string]string{"message": "wrong password"})
		return
	}
	// L'effacement va jusqu'au bout même si l'application se ferme entre-temps
	ctx, cancel := context.WithTimeout(context.WithoutCancel(r.Context()), time.Minute)
	defer cancel()
	if err := h.svc.Delete(ctx, userID); err != nil && !errors.Is(err, ErrNotFound) {
		log.Printf("account: delete failed: %v", err)
		writeJSON(w, http.StatusInternalServerError, map[string]string{"message": "account deletion failed, please retry"})
		return
	}
	// Pas d'identifiant ni d'email dans les journaux : le compte n'existe plus
	log.Printf("account: an account was deleted at its owner's request")
	writeJSON(w, http.StatusOK, map[string]bool{"deleted": true})
}
