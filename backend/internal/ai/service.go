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
	ollamaURL string
	model     string
	searxURL  string
}

func NewService(ollamaURL, model, searxURL string) *Service {
	if ollamaURL == "" {
		ollamaURL = "http://localhost:11434"
	}
	if model == "" {
		model = "qwen2.5:3b"
	}
	return &Service{ollamaURL: ollamaURL, model: model, searxURL: searxURL}
}

func (s *Service) Generate(ctx context.Context, prompt, model string) (*GenerateResponse, error) {
	if model == "" {
		model = s.model
	}

	systemPrompt := `Tu es un assistant factuel pour une application familiale française.
L'utilisateur décrit une liste d'idées qu'il veut créer (cadeaux, activités, recettes, événements, idées).
Tu dois générer entre 5 et 10 suggestions.

RÈGLES STRICTES — LIS ATTENTIVEMENT :
1. Réponds TOUJOURS en français.
2. Si des résultats de recherche web sont fournis ci-dessous, tu DOIS baser tes suggestions UNIQUEMENT sur ces résultats.
3. NE JAMAIS INVENTER de lieu, adresse, pays, nom d'établissement ou fait qui n'apparaît PAS dans les résultats de recherche.
4. NE JAMAIS déplacer un lieu dans un autre pays ou une autre région. Si un résultat mentionne "Noirmoutier, France", tu ne dois PAS écrire "Pays-Bas" ou tout autre pays.
5. Recopie fidèlement les noms de lieux, pays et régions tels qu'ils apparaissent dans les résultats de recherche.
6. Si tu n'as pas assez d'informations dans les résultats pour une suggestion, IGNORE-LA plutôt que d'inventer.
7. Inclus TOUJOURS l'URL source dans le champ "url" quand elle est disponible dans les résultats.
8. Si aucun résultat de recherche n'est fourni, génère des idées génériques SANS mentionner de lieux ou établissements spécifiques.

Réponds UNIQUEMENT avec un JSON valide, sans markdown, sans explication, sous cette forme exacte :
{"ideas": [
  {
    "title": "...",
    "description": "...",
    "url": "...",
    "category": "LINK_CATEGORY_CADEAU|LINK_CATEGORY_ACTIVITE|LINK_CATEGORY_RECETTE|LINK_CATEGORY_EVENEMENT|LINK_CATEGORY_IDEE",
    "tags": ["tag1", "tag2"],
    "ageRange": "...",
    "price": "...",
    "location": "...",
    "ingredients": []
  }
]}`

	// Étape 1 : Recherche web via SearXNG (requêtes multiples ciblées)
	var searchResults []SearchResult
	var searchContext string
	if s.searxURL != "" {
		results, err := searchWebMulti(ctx, s.searxURL, prompt, 15)
		if err != nil {
			fmt.Printf("searxng search failed: %v\n", err)
		} else {
			searchResults = results
			searchContext = formatSearchContext(results)
		}
	}

	fullPrompt := fmt.Sprintf("%s%s\n\nDemande de l'utilisateur : %s", systemPrompt, searchContext, prompt)

	body := map[string]any{
		"model":  model,
		"prompt": fullPrompt,
		"stream": false,
		"options": map[string]any{
			"temperature": 0.3,
		},
	}

	data, _ := json.Marshal(body)
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, s.ollamaURL+"/api/generate", bytes.NewReader(data))
	if err != nil {
		return nil, err
	}
	req.Header.Set("Content-Type", "application/json")

	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		return nil, fmt.Errorf("ollama unreachable: %w", err)
	}
	defer resp.Body.Close()

	raw, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, err
	}

	// Ollama retourne {"response": "...", ...}
	var ollamaResp struct {
		Response string `json:"response"`
	}
	if err := json.Unmarshal(raw, &ollamaResp); err != nil {
		return nil, fmt.Errorf("invalid ollama response: %w", err)
	}

	// Extraire le JSON de la réponse (peut contenir du texte autour)
	jsonStr := extractJSON(ollamaResp.Response)
	if jsonStr == "" {
		return nil, fmt.Errorf("no JSON found in ollama response")
	}

	var result GenerateResponse
	if err := json.Unmarshal([]byte(jsonStr), &result); err != nil {
		return nil, fmt.Errorf("failed to parse ideas JSON: %w", err)
	}

	// Normaliser les catégories + enrichir avec les images des résultats de recherche
	searchImageMap := buildSearchImageMap(searchResults)
	for i := range result.Ideas {
		result.Ideas[i].Category = normalizeCategory(result.Ideas[i].Category)
		// Associer l'image depuis les résultats SearXNG si l'URL correspond
		if result.Ideas[i].URL != "" && result.Ideas[i].ImageURL == "" {
			if img, ok := searchImageMap[result.Ideas[i].URL]; ok && img != "" {
				result.Ideas[i].ImageURL = img
			}
		}
	}

	// Étape 3 : Pour les idées avec URL mais sans image, scraper l'OG image
	enrichImagesFromOG(ctx, result.Ideas)

	return &result, nil
}

// buildSearchImageMap crée un mapping URL -> ImageURL depuis les résultats de recherche
func buildSearchImageMap(results []SearchResult) map[string]string {
	m := make(map[string]string, len(results))
	for _, r := range results {
		if r.URL != "" && r.ImageURL != "" {
			m[r.URL] = r.ImageURL
		}
	}
	return m
}

// enrichImagesFromOG scrape les OG images pour les idées qui ont une URL mais pas d'image
func enrichImagesFromOG(ctx context.Context, ideas []SuggestedLink) {
	// Limiter à 5 scrapes max pour ne pas ralentir la réponse
	scraped := 0
	for i := range ideas {
		if scraped >= 5 {
			break
		}
		if ideas[i].URL != "" && ideas[i].ImageURL == "" {
			if img := scrapeOGImage(ctx, ideas[i].URL); img != "" {
				ideas[i].ImageURL = img
				scraped++
			}
		}
	}
}

// scrapeOGImage récupère l'og:image d'une URL
func scrapeOGImage(ctx context.Context, rawURL string) string {
	client := &http.Client{Timeout: 5 * time.Second}
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

	// Lire seulement les premiers 64KB pour trouver les meta tags
	limited := io.LimitReader(resp.Body, 64*1024)
	body, err := io.ReadAll(limited)
	if err != nil {
		return ""
	}
	// Chercher og:image dans le HTML brut (rapide, pas de parsing complet)
	s := string(body)
	idx := strings.Index(s, `og:image`)
	if idx == -1 {
		return ""
	}
	// Trouver content="..." après og:image
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

// extractJSON extrait le premier objet JSON valide d'une chaîne
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
	// Tentative de mapping par mot-clé
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
