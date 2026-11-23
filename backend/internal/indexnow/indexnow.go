// Package indexnow prévient les moteurs de recherche qui suivent le protocole
// IndexNow (Bing, Yandex, Seznam, Naver…) dès qu'une page publique change :
// idée ou dossier publié, modifié, retiré. Les adresses sont regroupées et
// envoyées par lots ; une erreur n'a aucun effet sur l'action de l'utilisateur.
// Google ne suit pas IndexNow : il lit le plan du site (sitemap.xml).
package indexnow

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"log"
	"net/http"
	"regexp"
	"sort"
	"strings"
	"sync"
	"time"
)

// Endpoint : point d'entrée commun, relayé à tous les moteurs participants.
const Endpoint = "https://api.indexnow.org/indexnow"

const (
	maxBatch = 1000
	// Un lot part au plus toutes les flushEvery : plusieurs modifications d'une
	// même idée ne font qu'une notification.
	flushEvery = 2 * time.Minute
)

// keyRe : forme imposée par le protocole (8 à 128 caractères a-z, A-Z, 0-9, -).
var keyRe = regexp.MustCompile(`^[a-zA-Z0-9-]{8,128}$`)

// ValidKey indique si key peut servir de clé IndexNow.
func ValidKey(key string) bool { return keyRe.MatchString(key) }

type Client struct {
	site, key, endpoint string
	http                *http.Client

	mu      sync.Mutex
	pending map[string]bool
}

// New retourne un client pour le site (origine publique, ex. https://tribbae.fr),
// ou nil si la clé est vide ou invalide : Notify ne fait alors rien.
func New(site, key string) *Client {
	if !ValidKey(key) {
		if key != "" {
			log.Printf("indexnow: invalid key, notifications disabled")
		}
		return nil
	}
	return &Client{
		site: strings.TrimRight(site, "/"), key: key, endpoint: Endpoint,
		http: &http.Client{Timeout: 15 * time.Second}, pending: map[string]bool{},
	}
}

// KeyPath : adresse du fichier de clé qui prouve la propriété du site
// (« /<clé>.txt », contenu = la clé).
func (c *Client) KeyPath() string { return "/" + c.key + ".txt" }

// Key : contenu du fichier de clé.
func (c *Client) Key() string { return c.key }

// Notify note des pages du site qui ont changé (chemins, ex. /links/<id>).
func (c *Client) Notify(paths ...string) {
	if c == nil {
		return
	}
	c.mu.Lock()
	defer c.mu.Unlock()
	for _, p := range paths {
		if strings.HasPrefix(p, "/") && len(c.pending) < 10*maxBatch {
			c.pending[c.site+p] = true
		}
	}
}

// Run envoie les pages notées toutes les flushEvery, jusqu'à l'annulation.
func (c *Client) Run(ctx context.Context) {
	if c == nil {
		return
	}
	t := time.NewTicker(flushEvery)
	defer t.Stop()
	for {
		select {
		case <-ctx.Done():
			return
		case <-t.C:
			if err := c.Flush(ctx); err != nil {
				log.Printf("indexnow: %v", err)
			}
		}
	}
}

// Flush envoie tout de suite les pages en attente.
func (c *Client) Flush(ctx context.Context) error {
	c.mu.Lock()
	urls := make([]string, 0, len(c.pending))
	for u := range c.pending {
		urls = append(urls, u)
	}
	c.pending = map[string]bool{}
	c.mu.Unlock()
	sort.Strings(urls)
	for len(urls) > 0 {
		n := min(len(urls), maxBatch)
		if err := c.send(ctx, urls[:n]); err != nil {
			return err
		}
		urls = urls[n:]
	}
	return nil
}

func (c *Client) send(ctx context.Context, urls []string) error {
	host := strings.TrimPrefix(strings.TrimPrefix(c.site, "https://"), "http://")
	body, err := json.Marshal(map[string]any{
		"host": host, "key": c.key, "keyLocation": c.site + c.KeyPath(), "urlList": urls,
	})
	if err != nil {
		return err
	}
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, c.endpoint, bytes.NewReader(body))
	if err != nil {
		return err
	}
	req.Header.Set("Content-Type", "application/json; charset=utf-8")
	resp, err := c.http.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	// 200 : reçu ; 202 : reçu, clé en cours de vérification
	if resp.StatusCode != http.StatusOK && resp.StatusCode != http.StatusAccepted {
		return fmt.Errorf("%d pages: status %d", len(urls), resp.StatusCode)
	}
	return nil
}
