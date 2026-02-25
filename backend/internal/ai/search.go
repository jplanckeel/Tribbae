package ai

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"strings"
	"time"
)

// SearchResult représente un résultat de recherche web
type SearchResult struct {
	Title    string `json:"title"`
	URL      string `json:"url"`
	Content  string `json:"content"`
	ImageURL string `json:"img_src,omitempty"`
}

// buildSearchQueries génère plusieurs requêtes de recherche ciblées à partir du prompt utilisateur
func buildSearchQueries(prompt string) []string {
	p := strings.TrimSpace(prompt)
	queries := []string{p}

	// Ajouter des variantes pour améliorer la couverture
	lower := strings.ToLower(p)

	// Détecter un lieu dans le prompt et ajouter des requêtes géo-ciblées
	locationKeywords := []string{
		"à ", "a ", "en ", "au ", "aux ", "sur ", "dans ", "près de ", "autour de ",
	}
	for _, kw := range locationKeywords {
		if idx := strings.Index(lower, kw); idx >= 0 {
			location := strings.TrimSpace(p[idx+len(kw):])
			// Prendre les premiers mots comme lieu (max 4 mots)
			words := strings.Fields(location)
			if len(words) > 4 {
				words = words[:4]
			}
			loc := strings.Join(words, " ")
			if loc != "" {
				queries = append(queries, loc+" tourisme")
				queries = append(queries, loc+" que faire")
			}
			break
		}
	}

	// Ajouter "avis" ou "recommandation" pour des résultats plus fiables
	if !strings.Contains(lower, "recette") {
		queries = append(queries, p+" avis recommandation")
	} else {
		queries = append(queries, p+" recette facile")
	}

	// Limiter à 4 requêtes max
	if len(queries) > 4 {
		queries = queries[:4]
	}
	return queries
}

// searchWebMulti lance plusieurs requêtes SearXNG et déduplique les résultats
func searchWebMulti(ctx context.Context, searxURL, prompt string, maxResults int) ([]SearchResult, error) {
	if searxURL == "" {
		return nil, nil
	}
	if maxResults == 0 {
		maxResults = 15
	}

	queries := buildSearchQueries(prompt)
	seen := make(map[string]bool)
	var allResults []SearchResult

	for _, q := range queries {
		results, err := searchWeb(ctx, searxURL, q, 8)
		if err != nil {
			fmt.Printf("search query %q failed: %v\n", q, err)
			continue
		}
		for _, r := range results {
			if seen[r.URL] {
				continue
			}
			seen[r.URL] = true
			allResults = append(allResults, r)
		}
		if len(allResults) >= maxResults {
			break
		}
	}

	if len(allResults) > maxResults {
		allResults = allResults[:maxResults]
	}
	return allResults, nil
}

// searchWeb interroge SearXNG et retourne les résultats pertinents
func searchWeb(ctx context.Context, searxURL, query string, maxResults int) ([]SearchResult, error) {
	if searxURL == "" {
		return nil, nil
	}
	if maxResults == 0 {
		maxResults = 10
	}

	params := url.Values{
		"q":      {query},
		"format": {"json"},
		"lang":   {"fr"},
	}

	reqURL := fmt.Sprintf("%s/search?%s", strings.TrimRight(searxURL, "/"), params.Encode())

	client := &http.Client{Timeout: 15 * time.Second}
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, reqURL, nil)
	if err != nil {
		return nil, err
	}
	req.Header.Set("Accept", "application/json")

	resp, err := client.Do(req)
	if err != nil {
		return nil, fmt.Errorf("searxng unreachable: %w", err)
	}
	defer resp.Body.Close()

	raw, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, err
	}

	var searxResp struct {
		Results []struct {
			Title   string `json:"title"`
			URL     string `json:"url"`
			Content string `json:"content"`
			ImgSrc  string `json:"img_src"`
		} `json:"results"`
	}
	if err := json.Unmarshal(raw, &searxResp); err != nil {
		return nil, fmt.Errorf("invalid searxng response: %w", err)
	}

	var results []SearchResult
	for _, r := range searxResp.Results {
		if len(results) >= maxResults {
			break
		}
		if r.Title == "" && r.Content == "" {
			continue
		}
		content := r.Content
		if len(content) > 500 {
			content = content[:500] + "..."
		}
		results = append(results, SearchResult{
			Title:    r.Title,
			URL:      r.URL,
			Content:  content,
			ImageURL: r.ImgSrc,
		})
	}

	return results, nil
}

// formatSearchContext formate les résultats de recherche pour le prompt LLM
func formatSearchContext(results []SearchResult) string {
	if len(results) == 0 {
		return ""
	}

	var sb strings.Builder
	sb.WriteString("\n\n--- RÉSULTATS DE RECHERCHE WEB (données réelles vérifiées) ---\n")
	sb.WriteString("RAPPEL : Utilise UNIQUEMENT les informations ci-dessous. Ne modifie PAS les lieux, pays ou adresses.\n\n")
	for i, r := range results {
		fmt.Fprintf(&sb, "[%d] %s\n", i+1, r.Title)
		if r.URL != "" {
			fmt.Fprintf(&sb, "    URL: %s\n", r.URL)
		}
		if r.Content != "" {
			fmt.Fprintf(&sb, "    Extrait: %s\n", r.Content)
		}
		sb.WriteString("\n")
	}
	sb.WriteString("--- FIN DES RÉSULTATS ---\n")
	return sb.String()
}
