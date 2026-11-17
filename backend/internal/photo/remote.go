package photo

import (
	"context"
	"errors"
	"fmt"
	"io"
	"net/http"
	"time"

	"github.com/tribbae/backend/internal/netsafe"
)

// ErrSourceGone : l'image d'origine n'est plus servie (adresse expirée,
// publication supprimée ou devenue privée). Inutile de réessayer.
var ErrSourceGone = errors.New("source image is gone")

// remoteClient ne joint que des adresses publiques (voir netsafe).
var remoteClient = netsafe.NewClient(15 * time.Second)

// SaveFrom télécharge l'image d'une autre adresse et la garde comme une photo
// envoyée (redressée, réduite, sans métadonnées), au nom de ownerID : sert aux
// images des sites qui signent leurs adresses pour quelques jours (Instagram,
// Facebook, TikTok), qui disparaîtraient sinon. Renvoie le jeton de la photo.
func (s *Service) SaveFrom(ctx context.Context, ownerID, rawURL string) (string, error) {
	resp, err := netsafe.GetWithHeaders(ctx, remoteClient, rawURL, map[string]string{
		"User-Agent": "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36",
		"Accept":     "image/avif,image/webp,image/jpeg,image/png,image/*;q=0.8",
	})
	if err != nil {
		return "", err
	}
	defer resp.Body.Close()
	switch {
	case resp.StatusCode == http.StatusForbidden || resp.StatusCode == http.StatusNotFound || resp.StatusCode == http.StatusGone:
		return "", fmt.Errorf("%w: %d", ErrSourceGone, resp.StatusCode)
	case resp.StatusCode >= 400:
		return "", fmt.Errorf("source image: status %d", resp.StatusCode)
	}
	data, err := io.ReadAll(io.LimitReader(resp.Body, MaxUpload+1))
	if err != nil {
		return "", err
	}
	if len(data) > MaxUpload {
		return "", fmt.Errorf("%w: image is too large", ErrUnsupported)
	}
	return s.Save(ctx, ownerID, data)
}
