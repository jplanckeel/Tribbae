package photo

import (
	"encoding/json"
	"errors"
	"io"
	"log"
	"net/http"
	"net/url"
	"strconv"
	"strings"
)

const (
	// UploadPath reçoit une photo (corps = l'image brute, compte connecté).
	UploadPath = "/v1/photos"
	// Path + jeton sert une photo, publiquement.
	Path = "/v1/photos/"
	// MaxUpload : taille maximale d'une photo envoyée (les clients la réduisent avant).
	MaxUpload = 12 << 20
)

// Handler sert :
//   - POST /v1/photos : enregistre la photo envoyée et renvoie son adresse ;
//   - GET  /v1/photos/{jeton} : la photo, en JPEG.
type Handler struct {
	svc         *Service
	tokenParser func(r *http.Request) (string, error)
}

func NewHandler(svc *Service, tokenParser func(r *http.Request) (string, error)) *Handler {
	return &Handler{svc: svc, tokenParser: tokenParser}
}

func writeJSON(w http.ResponseWriter, code int, v any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(code)
	_ = json.NewEncoder(w).Encode(v)
}

func (h *Handler) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	switch {
	case r.URL.Path == UploadPath && r.Method == http.MethodPost:
		h.upload(w, r)
	case strings.HasPrefix(r.URL.Path, Path) && (r.Method == http.MethodGet || r.Method == http.MethodHead):
		h.serve(w, r, strings.TrimPrefix(r.URL.Path, Path))
	default:
		writeJSON(w, http.StatusMethodNotAllowed, map[string]string{"message": "method not allowed"})
	}
}

func (h *Handler) upload(w http.ResponseWriter, r *http.Request) {
	userID, err := h.tokenParser(r)
	if err != nil || userID == "" {
		writeJSON(w, http.StatusUnauthorized, map[string]string{"message": "authentication required"})
		return
	}
	data, err := io.ReadAll(http.MaxBytesReader(w, r.Body, MaxUpload))
	if err != nil {
		var tooLarge *http.MaxBytesError
		if errors.As(err, &tooLarge) {
			writeJSON(w, http.StatusRequestEntityTooLarge, map[string]string{"message": "photo is too large"})
			return
		}
		writeJSON(w, http.StatusBadRequest, map[string]string{"message": "photo could not be read"})
		return
	}
	token, err := h.svc.Save(r.Context(), userID, data)
	if errors.Is(err, ErrUnsupported) {
		writeJSON(w, http.StatusBadRequest, map[string]string{"message": err.Error()})
		return
	}
	if err != nil {
		log.Printf("photo: save failed: %v", err)
		writeJSON(w, http.StatusInternalServerError, map[string]string{"message": "photo could not be saved"})
		return
	}
	writeJSON(w, http.StatusCreated, map[string]string{"url": baseURL(r) + Path + token})
}

// baseURL : l'adresse par laquelle le client joint l'API (https://tribbae.fr en
// production ; en développement localhost:8080 derrière le proxy Vite, ou
// 10.0.2.2:8080 depuis l'émulateur), pour que l'image s'affiche telle quelle
// partout.
func baseURL(r *http.Request) string {
	scheme := "http"
	if r.TLS != nil || r.Header.Get("X-Forwarded-Proto") == "https" {
		scheme = "https"
	}
	u, err := url.Parse(scheme + "://" + r.Host)
	if err != nil || u.Host == "" || u.Path != "" || u.User != nil {
		return ""
	}
	return u.String()
}

func (h *Handler) serve(w http.ResponseWriter, r *http.Request, token string) {
	ds, err := h.svc.Open(r.Context(), token)
	if errors.Is(err, ErrNotFound) {
		http.NotFound(w, r)
		return
	}
	if err != nil {
		log.Printf("photo: open failed: %v", err)
		http.Error(w, "photo unavailable", http.StatusInternalServerError)
		return
	}
	defer func() { _ = ds.Close() }()

	// Une photo ne change jamais : une nouvelle photo a un nouveau jeton.
	w.Header().Set("Content-Type", "image/jpeg")
	w.Header().Set("Content-Length", strconv.FormatInt(ds.GetFile().Length, 10))
	w.Header().Set("Cache-Control", "public, max-age=31536000, immutable")
	w.Header().Set("ETag", `"`+token+`"`)
	if r.Header.Get("If-None-Match") == `"`+token+`"` {
		w.WriteHeader(http.StatusNotModified)
		return
	}
	if r.Method == http.MethodHead {
		return
	}
	if _, err := io.Copy(w, ds); err != nil {
		log.Printf("photo: send failed: %v", err)
	}
}
