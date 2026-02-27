package ai

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"strings"
	"time"
)

// SuggestedLink représente une idée générée par l'IA
type SuggestedLink struct {
	Title       string   `json:"title"`
	Description string   `json:"description"`
	URL         string   `json:"url,omitempty"`
	ImageURL    string   `json:"imageUrl,omitempty"`
	Category    string   `json:"category"`
	Tags        []string `json:"tags"`
	AgeRange    string   `json:"ageRange,omitempty"`
	Price       string   `json:"price,omitempty"`
	Location    string   `json:"location,omitempty"`
	Ingredients []string `json:"ingredients,omitempty"`
}

type GenerateRequest struct {
	Prompt string `json:"prompt"`
	Model  string `json:"model,omitempty"`
}

type GenerateResponse struct {
	Ideas []SuggestedLink `json:"ideas"`
}

type Service struct {
	ollamaURL    string
	model        string
	searxURL     string
	geminiAPIKey string
}

func NewService(ollamaURL, model, searxURL, geminiAPIKey string) *Service {
	if ollamaURL == "" {
		ollamaURL = "http://localhost:11434"
	}
	if model == "" {
		model = "qwen2.5:1.5b"
	}
	return &Service{
		ollamaURL:    ollamaURL,
		model:        model,
		searxURL:     searxURL,
		geminiAPIKey: geminiAPIKey,
	}
}

const systemPrompt = `Assistant familial français. Génère 3 idées en JSON.
Format: {"ideas":[{"title":"...","description":"...","url":"...","category":"LINK_CATEGORY_CADEAU|LINK_CATEGORY_ACTIVITE|LINK_CATEGORY_RECETTE|LINK_CATEGORY_EVENEMENT|LINK_CATEGORY_IDEE","tags":["..."],"ageRange":"...","price":"...","location":"...","ingredients":[]}]}
Règles: français uniquement, JSON valide sans markdown, base-toi sur les résultats web fournis.`

func (s *Service) Generate(ctx context.Context, prompt, model string, isPremium bool) (*GenerateResponse, error) {
	if model == "" {
		model = s.model
	}

	start := time.Now()
	fmt.Printf("[AI] Starting generation for prompt: %q (Premium: %v)\n", prompt, isPremium)

	// Timeout global : 2 minutes max
	ctx, cancel := context.WithTimeout(ctx, 2*time.Minute)
	defer cancel()

	// Étape 1 : Recherche web
	var searchResults []SearchResult
	var searchContext string
	
	// Si premium et clé Gemini disponible, utiliser Gemini
	if isPremium && s.geminiAPIKey != "" {
		t := time.Now()
		results, err := s.searchWithGemini(ctx, prompt)
		fmt.Printf("[AI] Gemini search took %v, got %d results\n", time.Since(t), len(results))
		if err != nil {
			fmt.Printf("[AI] Gemini search failed: %v, falling back to SearXNG\n", err)
			// Fallback sur SearXNG en cas d'erreur
			if s.searxURL != "" {
				results, err = searchWebMulti(ctx, s.searxURL, prompt, 5)
				if err == nil {
					searchResults = results
					searchContext = formatSearchContext(results)
				}
			}
		} else {
			searchResults = results
			searchContext = formatSearchContext(results)
		}
	} else if s.searxURL != "" {
		// Utilisateurs non-premium : SearXNG
		t := time.Now()
		results, err := searchWebMulti(ctx, s.searxURL, prompt, 5)
		fmt.Printf("[AI] Search took %v, got %d results\n", time.Since(t), len(results))
		if err != nil {
			fmt.Printf("[AI] searxng search failed: %v\n", err)
		} else {
			searchResults = results
			searchContext = formatSearchContext(results)
		}
	}

	// Étape 2 : Génération Ollama (prompt compact, 5 idées max)
	fullPrompt := fmt.Sprintf("%s%s\n\nDemande : %s", systemPrompt, searchContext, prompt)

	body := map[string]any{
		"model":  model,
		"prompt": fullPrompt,
		"stream": false,
		"options": map[string]any{
			"temperature": 0.3,
			"num_predict": 1024,
			"num_ctx":     2048,
		},
	}

	data, _ := json.Marshal(body)

	fmt.Printf("[AI] Calling Ollama (model: %s, prompt length: %d chars)...\n", model, len(fullPrompt))
	t := time.Now()
	ollamaClient := &http.Client{Timeout: 120 * time.Second}
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, s.ollamaURL+"/api/generate", bytes.NewReader(data))
	if err != nil {
		return nil, err
	}
	req.Header.Set("Content-Type", "application/json")

	resp, err := ollamaClient.Do(req)
	if err != nil {
		return nil, fmt.Errorf("ollama unreachable: %w", err)
	}
	defer resp.Body.Close()

	raw, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, err
	}

	var ollamaResp struct {
		Response string `json:"response"`
	}
	if err := json.Unmarshal(raw, &ollamaResp); err != nil {
		return nil, fmt.Errorf("invalid ollama response: %w", err)
	}
	fmt.Printf("[AI] Ollama responded in %v\n", time.Since(t))

	jsonStr := extractJSON(ollamaResp.Response)
	if jsonStr == "" {
		return nil, fmt.Errorf("no JSON found in ollama response")
	}

	var result GenerateResponse
	if err := json.Unmarshal([]byte(jsonStr), &result); err != nil {
		return nil, fmt.Errorf("failed to parse ideas JSON: %w", err)
	}

	// Normaliser les catégories + enrichir images depuis SearXNG
	searchImageMap := buildSearchImageMap(searchResults)
	for i := range result.Ideas {
		result.Ideas[i].Category = normalizeCategory(result.Ideas[i].Category)
		if result.Ideas[i].URL != "" && result.Ideas[i].ImageURL == "" {
			if img, ok := searchImageMap[result.Ideas[i].URL]; ok && img != "" {
				result.Ideas[i].ImageURL = img
			}
		}
	}

	// Étape 3 : Scrape OG images en parallèle (max 8s)
	enrichImagesFromOG(ctx, result.Ideas)

	fmt.Printf("[AI] Total generation took %v, returning %d ideas\n", time.Since(start), len(result.Ideas))
	return &result, nil
}

func buildSearchImageMap(results []SearchResult) map[string]string {
	m := make(map[string]string, len(results))
	for _, r := range results {
		if r.URL != "" && r.ImageURL != "" {
			m[r.URL] = r.ImageURL
		}
	}
	return m
}

func scrapeOGImage(ctx context.Context, rawURL string) string {
	client := &http.Client{Timeout: 3 * time.Second}
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, rawURL, nil)
	if err != nil {
		return ""
	}
	req.Header.Set("User-Agent", "Mozilla/5.0 (compatible; Tribbae/1.0)")
	resp, err := client.Do(req)
	if err != nil {
		return ""
	}
	defer resp.Body.Close()

	limited := io.LimitReader(resp.Body, 32*1024)
	body, err := io.ReadAll(limited)
	if err != nil {
		return ""
	}
	s := string(body)
	idx := strings.Index(s, `og:image`)
	if idx == -1 {
		return ""
	}
	sub := s[idx:]
	cIdx := strings.Index(sub, `content="`)
	if cIdx == -1 {
		cIdx = strings.Index(sub, `content='`)
	}
	if cIdx == -1 {
		return ""
	}
	quote := sub[cIdx+8 : cIdx+9]
	start := cIdx + 9
	end := strings.Index(sub[start:], quote)
	if end == -1 || end > 500 {
		return ""
	}
	return sub[start : start+end]
}

func extractJSON(s string) string {
	start := strings.Index(s, "{")
	if start == -1 {
		return ""
	}
	depth := 0
	for i := start; i < len(s); i++ {
		switch s[i] {
		case '{':
			depth++
		case '}':
			depth--
			if depth == 0 {
				return s[start : i+1]
			}
		}
	}
	return ""
}

var validCategories = map[string]bool{
	"LINK_CATEGORY_IDEE":      true,
	"LINK_CATEGORY_CADEAU":    true,
	"LINK_CATEGORY_ACTIVITE":  true,
	"LINK_CATEGORY_EVENEMENT": true,
	"LINK_CATEGORY_RECETTE":   true,
}

func normalizeCategory(cat string) string {
	cat = strings.ToUpper(strings.TrimSpace(cat))
	if validCategories[cat] {
		return cat
	}
	switch {
	case strings.Contains(cat, "CADEAU") || strings.Contains(cat, "GIFT"):
		return "LINK_CATEGORY_CADEAU"
	case strings.Contains(cat, "ACTIVIT") || strings.Contains(cat, "ACTIVITY"):
		return "LINK_CATEGORY_ACTIVITE"
	case strings.Contains(cat, "RECETTE") || strings.Contains(cat, "RECIPE"):
		return "LINK_CATEGORY_RECETTE"
	case strings.Contains(cat, "EVENEMENT") || strings.Contains(cat, "EVENT"):
		return "LINK_CATEGORY_EVENEMENT"
	default:
		return "LINK_CATEGORY_IDEE"
	}
}


// searchWithGemini utilise l'API Gemini pour rechercher des informations
func (s *Service) searchWithGemini(ctx context.Context, query string) ([]SearchResult, error) {
	if s.geminiAPIKey == "" {
		return nil, fmt.Errorf("gemini API key not configured")
	}

	// Préparer la requête Gemini avec grounding (recherche web)
	reqBody := map[string]interface{}{
		"contents": []map[string]interface{}{
			{
				"parts": []map[string]string{
					{
						"text": fmt.Sprintf("Recherche des informations pertinentes sur : %s. Fournis 5 résultats avec titre, description et contexte utile pour des idées familiales.", query),
					},
				},
			},
		},
		"generationConfig": map[string]interface{}{
			"temperature":     0.2,
			"maxOutputTokens": 1000,
		},
	}

	jsonData, err := json.Marshal(reqBody)
	if err != nil {
		return nil, fmt.Errorf("marshal request: %w", err)
	}

	// Utiliser Gemini Pro
	url := fmt.Sprintf("https://generativelanguage.googleapis.com/v1beta/models/gemini-pro:generateContent?key=%s", s.geminiAPIKey)
	req, err := http.NewRequestWithContext(ctx, "POST", url, bytes.NewBuffer(jsonData))
	if err != nil {
		return nil, fmt.Errorf("create request: %w", err)
	}

	req.Header.Set("Content-Type", "application/json")

	client := &http.Client{Timeout: 30 * time.Second}
	resp, err := client.Do(req)
	if err != nil {
		return nil, fmt.Errorf("gemini request: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		body, _ := io.ReadAll(resp.Body)
		return nil, fmt.Errorf("gemini returned status %d: %s", resp.StatusCode, string(body))
	}

	var geminiResp struct {
		Candidates []struct {
			Content struct {
				Parts []struct {
					Text string `json:"text"`
				} `json:"parts"`
			} `json:"content"`
			GroundingMetadata struct {
				WebSearchQueries []string `json:"webSearchQueries"`
			} `json:"groundingMetadata"`
		} `json:"candidates"`
	}

	if err := json.NewDecoder(resp.Body).Decode(&geminiResp); err != nil {
		return nil, fmt.Errorf("decode response: %w", err)
	}

	if len(geminiResp.Candidates) == 0 || len(geminiResp.Candidates[0].Content.Parts) == 0 {
		return nil, fmt.Errorf("no results from gemini")
	}

	// Convertir la réponse Gemini en SearchResults
	content := geminiResp.Candidates[0].Content.Parts[0].Text
	results := []SearchResult{
		{
			Title:   "Résultats Gemini pour: " + query,
			Content: content,
			URL:     "",
		},
	}

	return results, nil
}
