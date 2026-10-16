package ai

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"strings"
	"sync"
	"time"
	"unicode/utf8"
)

// SearchResult est un résultat web proposé au modèle comme source d'idée.
type SearchResult struct {
	Title    string
	URL      string
	Content  string
	ImageURL string
}

// Domain retourne le domaine de la source, sans « www. ».
func (r SearchResult) Domain() string {
	u, err := url.Parse(r.URL)
	if err != nil {
		return ""
	}
	return strings.TrimPrefix(strings.ToLower(u.Hostname()), "www.")
}

const (
	maxSources       = 8
	perQueryResults  = 8
	maxPerDomain     = 2
	maxSnippetRunes  = 220
	searchBudget     = 12 * time.Second
	searxHTTPTimeout = 10 * time.Second
)

// Sites dont les pages ne donnent pas d'idée exploitable (contenu derrière
// connexion, fils de discussion, petites annonces) : écartés des sources.
var blockedDomains = []string{
	"reddit.com", "facebook.com", "instagram.com", "tiktok.com", "x.com", "twitter.com",
	"linkedin.com", "quora.com", "leboncoin.fr",
}

var searxClient = &http.Client{Timeout: searxHTTPTimeout}

// Orientation ajoutée à la seconde requête selon le type de demande.
// category est la catégorie attendue (clé courte de prompt.go), suggérée au modèle.
var queryHints = []struct {
	words    []string
	hint     string
	category string
}{
	{[]string{"recette", "cuisine", "goûter", "gouter", "repas", "dessert", "gâteau", "gateau", "menu"}, "recette facile enfants", "recette"},
	{[]string{"cadeau", "offrir", "anniversaire", "noël", "noel"}, "idée cadeau", "cadeau"},
	{[]string{"livre", "lecture", "roman", "album"}, "livre jeunesse", "livre"},
	{[]string{"sortie", "visite", "autour de", "près de", "pres de", "week-end", "weekend", "balade"}, "sortie en famille", "sortie"},
	{[]string{"déco", "deco", "décoration", "decoration", "chambre"}, "idée déco DIY", "deco"},
}

// expectedCategory retourne la catégorie évidente d'une demande, ou "".
func expectedCategory(prompt string) string {
	lower := strings.ToLower(prompt)
	for _, h := range queryHints {
		if containsAny(lower, h.words...) {
			return h.category
		}
	}
	return ""
}

// buildQueries décline la demande en une ou deux requêtes : la demande telle
// quelle, et une variante orientée vers des pages d'idées pour familles.
func buildQueries(prompt string) []string {
	p := strings.TrimSpace(prompt)
	lower := strings.ToLower(p)
	hint := "en famille avec enfants"
	for _, h := range queryHints {
		if containsAny(lower, h.words...) {
			hint = h.hint
			break
		}
	}
	// Ne garde de l'orientation que les mots absents de la demande
	var extra []string
	for _, w := range strings.Fields(hint) {
		if !strings.Contains(lower, strings.ToLower(w)) {
			extra = append(extra, w)
		}
	}
	if len(extra) == 0 {
		return []string{p}
	}
	return []string{p, p + " " + strings.Join(extra, " ")}
}

func containsAny(s string, subs ...string) bool {
	for _, sub := range subs {
		if strings.Contains(s, sub) {
			return true
		}
	}
	return false
}

// searchFunc lance une requête auprès d'un moteur (SearXNG, API Ollama…).
type searchFunc func(ctx context.Context, query string) ([]SearchResult, error)

// runSearch lance les requêtes en parallèle et retourne des sources
// dédoublonnées, variées (max 2 par site) et nettoyées.
func runSearch(ctx context.Context, queries []string, search searchFunc) ([]SearchResult, error) {
	ctx, cancel := context.WithTimeout(ctx, searchBudget)
	defer cancel()

	lists := make([][]SearchResult, len(queries))
	errs := make([]error, len(queries))
	var wg sync.WaitGroup
	for i, q := range queries {
		wg.Add(1)
		go func() {
			defer wg.Done()
			lists[i], errs[i] = search(ctx, q)
		}()
	}
	wg.Wait()

	// Entrelace les listes : les meilleurs résultats de chaque requête d'abord
	var merged []SearchResult
	for rank := 0; rank < perQueryResults+2; rank++ {
		for _, l := range lists {
			if rank < len(l) {
				merged = append(merged, l[rank])
			}
		}
	}
	sources := selectSources(merged)
	if len(sources) == 0 {
		for _, err := range errs {
			if err != nil {
				return nil, err
			}
		}
	}
	return sources, nil
}

// selectSources filtre, dédoublonne et nettoie les résultats bruts.
func selectSources(results []SearchResult) []SearchResult {
	seen := map[string]bool{}
	perDomain := map[string]int{}
	var out []SearchResult
	for _, r := range results {
		u, err := url.Parse(r.URL)
		if err != nil || (u.Scheme != "http" && u.Scheme != "https") || u.Host == "" {
			continue
		}
		d := r.Domain()
		if blocked(d) || perDomain[d] >= maxPerDomain {
			continue
		}
		key := d + strings.TrimRight(u.EscapedPath(), "/") + "?" + u.RawQuery
		if seen[key] {
			continue
		}
		r.Title = cleanSnippet(r.Title, 120)
		r.Content = cleanSnippet(r.Content, maxSnippetRunes)
		if r.Title == "" {
			continue
		}
		seen[key] = true
		perDomain[d]++
		out = append(out, r)
		if len(out) == maxSources {
			break
		}
	}
	return out
}

func blocked(domain string) bool {
	for _, b := range blockedDomains {
		if domain == b || strings.HasSuffix(domain, "."+b) {
			return true
		}
	}
	return false
}

// cleanSnippet normalise les espaces et coupe proprement sur un mot.
func cleanSnippet(s string, maxRunes int) string {
	s = strings.Join(strings.Fields(s), " ")
	if utf8.RuneCountInString(s) <= maxRunes {
		return s
	}
	cut := string([]rune(s)[:maxRunes])
	if i := strings.LastIndex(cut, " "); i > len(cut)/2 {
		cut = cut[:i]
	}
	return strings.TrimRight(cut, " ,;:.-") + "…"
}

// searxSources interroge SearXNG avec la demande et sa variante orientée.
func searxSources(ctx context.Context, searxURL, prompt string) ([]SearchResult, error) {
	if searxURL == "" {
		return nil, nil
	}
	return runSearch(ctx, buildQueries(prompt), func(ctx context.Context, q string) ([]SearchResult, error) {
		return searxSearch(ctx, searxURL, q)
	})
}

// searxSearch interroge l'API JSON de SearXNG.
func searxSearch(ctx context.Context, searxURL, query string) ([]SearchResult, error) {
	form := url.Values{
		"q":          {query},
		"format":     {"json"},
		"language":   {"fr-FR"},
		"safesearch": {"1"}, // application familiale
		"categories": {"general"},
	}
	req, err := http.NewRequestWithContext(ctx, http.MethodPost,
		strings.TrimRight(searxURL, "/")+"/search", strings.NewReader(form.Encode()))
	if err != nil {
		return nil, err
	}
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")
	req.Header.Set("Accept", "application/json")
	req.Header.Set("Accept-Language", "fr-FR,fr;q=0.9")

	resp, err := searxClient.Do(req)
	if err != nil {
		return nil, fmt.Errorf("searxng unreachable: %w", err)
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("searxng returned status %d", resp.StatusCode)
	}

	var body struct {
		Results []struct {
			Title   string `json:"title"`
			URL     string `json:"url"`
			Content string `json:"content"`
			ImgSrc  string `json:"img_src"`
			Thumb   string `json:"thumbnail"`
		} `json:"results"`
	}
	if err := json.NewDecoder(io.LimitReader(resp.Body, 2<<20)).Decode(&body); err != nil {
		return nil, fmt.Errorf("invalid searxng response: %w", err)
	}
	var out []SearchResult
	for _, r := range body.Results {
		if len(out) == perQueryResults {
			break
		}
		out = append(out, SearchResult{
			Title:    r.Title,
			URL:      r.URL,
			Content:  r.Content,
			ImageURL: firstNonEmpty(r.ImgSrc, r.Thumb),
		})
	}
	return out, nil
}

func firstNonEmpty(vals ...string) string {
	for _, v := range vals {
		if strings.TrimSpace(v) != "" {
			return v
		}
	}
	return ""
}
