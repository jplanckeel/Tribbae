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
)

// SearchResult représente un résultat de recherche web
type SearchResult struct {
	Title    string `json:"title"`
	URL      string `json:"url"`
	Content  string `json:"content"`
	ImageURL string `json:"img_src,omitempty"`
}

// buildSearchQueries génère des requêtes de recherche ciblées (max 2 pour la perf)
func buildSearchQueries(prompt string) []string {
	p := strings.TrimSpace(prompt)
	queries := []string{p}

	lower := strings.ToLower(p)
	if !strings.Contains(lower, "recette") {
		queries = append(queries, p+" avis recommandation")
	} else {
		queries = append(queries, p+" recette facile")
	}

	return queries
}

// searchWebMulti lance les requêtes SearXNG en parallèle et déduplique
func searchWebMulti(ctx context.Context, searxURL, prompt string, maxResults int) ([]SearchResult, error) {
	if searxURL == "" {
		return nil, nil
	}
	if maxResults == 0 {
		maxResults = 8
	}

	// Timeout global pour toute la recherche : 15s max
	searchCtx, cancel := context.WithTimeout(ctx, 15*time.Second)
	defer cancel()

	queries := buildSearchQueries(prompt)

	type queryResult struct {
		results []SearchResult
		err     error
	}

	ch := make(chan queryResult, len(queries))
	for _, q := range queries {
		go func(query string) {
			results, err := searchWeb(searchCtx, searxURL, query, 5)
			ch <- queryResult{results, err}
		}(q)
	}

	seen := make(map[string]bool)
	var allResults []SearchResult

	for range queries {
		qr := <-ch
		if qr.err != nil {
			fmt.Printf("search query failed: %v\n", qr.err)
			continue
		}
		for _, r := range qr.results {
			if seen[r.URL] {
				continue
			}
			seen[r.URL] = true
			allResults = append(allResults, r)
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
		maxResults = 5
	}

	params := url.Values{
		"q":      {query},
		"format": {"json"},
		"lang":   {"fr"},
	}

	reqURL := fmt.Sprintf("%s/search", strings.TrimRight(searxURL, "/"))

	client := &http.Client{Timeout: 10 * time.Second}
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, reqURL, strings.NewReader(params.Encode()))
	if err != nil {
		return nil, err
	}
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")
	req.Header.Set("Accept", "application/json")
	req.Header.Set("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")

	resp, err := client.Do(req)
	if err != nil {
		return nil, fmt.Errorf("searxng unreachable: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("searxng returned status %d", resp.StatusCode)
	}

	raw, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, err
	}

	trimmed := strings.TrimSpace(string(raw))
	if len(trimmed) > 0 && trimmed[0] != '{' {
		preview := trimmed
		if len(preview) > 200 {
			preview = preview[:200]
		}
		return nil, fmt.Errorf("searxng returned non-JSON (status %d), starts with: %s", resp.StatusCode, preview)
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
		if len(content) > 150 {
			content = content[:150] + "..."
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

// formatSearchContext formate les résultats de recherche pour le prompt LLM (compact)
func formatSearchContext(results []SearchResult) string {
	if len(results) == 0 {
		return ""
	}

	var sb strings.Builder
	sb.WriteString("\n\n--- RÉSULTATS WEB ---\n")
	for i, r := range results {
		fmt.Fprintf(&sb, "[%d] %s\n", i+1, r.Title)
		if r.URL != "" {
			fmt.Fprintf(&sb, "    URL: %s\n", r.URL)
		}
		if r.Content != "" {
			fmt.Fprintf(&sb, "    %s\n", r.Content)
		}
	}
	sb.WriteString("---\n")
	return sb.String()
}

// enrichImagesFromOG scrape les OG images en parallèle (max 3, timeout 3s chacun)
func enrichImagesFromOG(ctx context.Context, ideas []SuggestedLink) {
	ogCtx, cancel := context.WithTimeout(ctx, 8*time.Second)
	defer cancel()

	var wg sync.WaitGroup
	sem := make(chan struct{}, 3) // max 3 en parallèle

	for i := range ideas {
		if ideas[i].URL == "" || ideas[i].ImageURL != "" {
			continue
		}
		wg.Add(1)
		go func(idx int) {
			defer wg.Done()
			sem <- struct{}{}
			defer func() { <-sem }()
			if img := scrapeOGImage(ogCtx, ideas[idx].URL); img != "" {
				ideas[idx].ImageURL = img
			}
		}(i)
	}
	wg.Wait()
}
