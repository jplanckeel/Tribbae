// Package seo rend le site lisible par les moteurs de recherche et les
// assistants IA, qui n'exécutent pas (ou mal) le JavaScript de l'application :
// pour chaque adresse publique, index.html reçoit son propre en-tête (titre,
// description, adresse canonique, Open Graph, données schema.org) et un
// contenu HTML équivalent, remplacé par l'application React à son démarrage.
// Le paquet sert aussi le plan du site (sitemap.xml) et llms.txt.
package seo

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"log"
	"net/http"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"time"
)

const (
	headStart = "<!--seo:head-->"
	headEnd   = "<!--/seo:head-->"
	rootTag   = `<div id="root"></div>`

	renderTimeout = 3 * time.Second
	feedTTL       = 30 * time.Minute
)

type Handler struct {
	site  string // origine publique, ex. https://tribbae.fr
	src   Source
	faq   []QA
	about *aboutContent

	// index.html découpé autour de l'en-tête et de #root
	beforeHead, betweenHeadRoot, afterRoot []byte

	mu    sync.Mutex
	feeds map[string]cachedFeed
}

type cachedFeed struct {
	body []byte
	at   time.Time
}

// New prépare le rendu à partir de l'index.html construit par Vite.
func New(site, staticDir string, src Source) (*Handler, error) {
	index, err := os.ReadFile(filepath.Join(staticDir, "index.html"))
	if err != nil {
		return nil, err
	}
	h := &Handler{site: strings.TrimRight(site, "/"), src: src, feeds: map[string]cachedFeed{}}
	if err := h.split(index); err != nil {
		return nil, err
	}
	h.faq = loadFAQ(filepath.Join(staticDir, "faq.json"))
	h.about = loadAbout(filepath.Join(staticDir, "about.json"))
	return h, nil
}

func (h *Handler) split(index []byte) error {
	i, j, k := bytes.Index(index, []byte(headStart)), bytes.Index(index, []byte(headEnd)), bytes.Index(index, []byte(rootTag))
	if i < 0 || j < i || k < j {
		return errors.New("seo: index.html lacks <!--seo:head--> markers or #root")
	}
	h.beforeHead = index[:i]
	h.betweenHeadRoot = index[j+len(headEnd) : k]
	h.afterRoot = index[k+len(rootTag):]
	return nil
}

// loadFAQ lit la FAQ partagée avec la page Aide du client (faq.json).
func loadFAQ(path string) []QA {
	b, err := os.ReadFile(path)
	if err != nil {
		return nil
	}
	var sections []struct {
		Items [][2]string `json:"items"`
	}
	if err := json.Unmarshal(b, &sections); err != nil {
		log.Printf("seo: invalid faq.json: %v", err)
		return nil
	}
	var out []QA
	for _, s := range sections {
		for _, it := range s.Items {
			out = append(out, QA{Q: it[0], A: it[1]})
		}
	}
	return out
}

// ServeHTML répond à une adresse de l'application (hors API et fichiers).
func (h *Handler) ServeHTML(w http.ResponseWriter, r *http.Request) {
	if to := redirect(r.URL.Path); to != "" {
		if r.URL.RawQuery != "" && !strings.Contains(to, "?") {
			to += "?" + r.URL.RawQuery
		}
		http.Redirect(w, r, to, http.StatusMovedPermanently)
		return
	}
	ctx, cancel := context.WithTimeout(r.Context(), renderTimeout)
	defer cancel()
	p, err := h.page(ctx, r.URL.Path, r.URL.Query())
	if err != nil || p == nil {
		// Base indisponible : l'application doit quand même démarrer
		if err != nil {
			log.Printf("seo: render %s: %v", r.URL.Path, err)
		}
		p = &Page{Status: http.StatusOK, Title: siteTagline, Description: siteDescription, NoIndex: true, H1: siteName}
	}
	page, err := h.render(p)
	if err != nil {
		log.Printf("seo: template %s: %v", r.URL.Path, err)
		http.Error(w, "internal error", http.StatusInternalServerError)
		return
	}
	w.Header().Set("Content-Type", "text/html; charset=utf-8")
	// Le HTML change avec le contenu et pointe vers les fichiers du dernier build
	w.Header().Set("Cache-Control", "no-cache")
	if p.NoIndex {
		w.Header().Set("X-Robots-Tag", "noindex")
	}
	w.WriteHeader(p.Status)
	if r.Method != http.MethodHead {
		// deepcode ignore XSS: la page sort de html/template (texte échappé, JSON-LD via json.Marshal) ; l'adresse ne sert qu'à choisir la page, ses identifiants sont validés (objectID, shareToken) et rien n'en est recopié (TestMalformedAddressesAreUnknownPages)
		_, _ = w.Write(page)
	}
}

func (h *Handler) render(p *Page) ([]byte, error) {
	head, err := p.head(h.site)
	if err != nil {
		return nil, err
	}
	body, err := p.body()
	if err != nil {
		return nil, err
	}
	var buf bytes.Buffer
	buf.Grow(len(h.beforeHead) + len(head) + len(h.betweenHeadRoot) + len(body) + len(h.afterRoot) + 32)
	buf.Write(h.beforeHead)
	buf.Write(head)
	buf.Write(h.betweenHeadRoot)
	buf.WriteString(`<div id="root">`)
	buf.Write(body)
	buf.WriteString(`</div>`)
	buf.Write(h.afterRoot)
	return buf.Bytes(), nil
}

// feed sert un document généré (plan du site, llms.txt), gardé en cache.
func (h *Handler) feed(name, contentType string, build func(context.Context) ([]byte, error)) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		h.mu.Lock()
		c, ok := h.feeds[name]
		h.mu.Unlock()
		if !ok || time.Since(c.at) > feedTTL {
			ctx, cancel := context.WithTimeout(r.Context(), 10*time.Second)
			body, err := build(ctx)
			cancel()
			if err != nil {
				log.Printf("seo: %s: %v", name, err)
				if !ok {
					http.Error(w, "temporarily unavailable", http.StatusServiceUnavailable)
					return
				}
				body = c.body // ancienne version plutôt que rien
			}
			c = cachedFeed{body: body, at: time.Now()}
			h.mu.Lock()
			h.feeds[name] = c
			h.mu.Unlock()
		}
		w.Header().Set("Content-Type", contentType)
		w.Header().Set("Cache-Control", fmt.Sprintf("public, max-age=%d", int(feedTTL.Seconds())))
		_, _ = w.Write(c.body)
	}
}

// Sitemap sert /sitemap.xml.
func (h *Handler) Sitemap() http.HandlerFunc {
	return h.feed("sitemap", "application/xml; charset=utf-8", h.sitemap)
}

// LLMs sert /llms.txt.
func (h *Handler) LLMs() http.HandlerFunc {
	return h.feed("llms", "text/plain; charset=utf-8", h.llms)
}
