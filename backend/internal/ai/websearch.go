package ai

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
)

// DefaultWebSearchURL : API de recherche web d'Ollama (compte gratuit ollama.com).
// La recherche s'exécute chez Ollama : pas de blocage d'IP de datacenter,
// contrairement à Google ou Qwant interrogés depuis SearXNG.
const DefaultWebSearchURL = "https://ollama.com/api/web_search"

// La limite de l'API est de 10 résultats par requête.
const webSearchMaxResults = 10

type webSearchClient struct {
	url    string
	apiKey string
	http   *http.Client
}

func newWebSearchClient(url, apiKey string) *webSearchClient {
	if apiKey == "" {
		return nil
	}
	if url == "" {
		url = DefaultWebSearchURL
	}
	return &webSearchClient{url: url, apiKey: apiKey, http: &http.Client{Timeout: searxHTTPTimeout}}
}

func (w *webSearchClient) search(ctx context.Context, query string) ([]SearchResult, error) {
	body, _ := json.Marshal(map[string]any{"query": query, "max_results": webSearchMaxResults})
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, w.url, bytes.NewReader(body))
	if err != nil {
		return nil, err
	}
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", "Bearer "+w.apiKey)
	resp, err := w.http.Do(req)
	if err != nil {
		return nil, fmt.Errorf("ollama web search unreachable: %w", err)
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		msg, _ := io.ReadAll(io.LimitReader(resp.Body, 300))
		return nil, fmt.Errorf("ollama web search: status %d: %s", resp.StatusCode, msg)
	}
	var out struct {
		Results []struct {
			Title   string `json:"title"`
			URL     string `json:"url"`
			Content string `json:"content"`
		} `json:"results"`
	}
	if err := json.NewDecoder(io.LimitReader(resp.Body, 4<<20)).Decode(&out); err != nil {
		return nil, fmt.Errorf("invalid ollama web search response: %w", err)
	}
	results := make([]SearchResult, 0, len(out.Results))
	for _, r := range out.Results {
		results = append(results, SearchResult{Title: r.Title, URL: r.URL, Content: r.Content})
	}
	return results, nil
}

// sources interroge l'API avec la variante orientée « familles » de la demande
// (une seule requête : chaque appel compte dans le quota du compte).
func (w *webSearchClient) sources(ctx context.Context, prompt string) ([]SearchResult, error) {
	queries := buildQueries(prompt)
	return runSearch(ctx, queries[len(queries)-1:], w.search)
}
