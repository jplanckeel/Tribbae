package ai

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"log"
	"regexp"
	"strings"
	"sync"
	"time"
	"unicode/utf8"

	"github.com/tribbae/backend/internal/preview"
)

// SuggestedLink représente une idée générée par l'IA.
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
}

type GenerateResponse struct {
	Ideas []SuggestedLink `json:"ideas"`
}

// Config de la génération d'idées (variables d'environnement, cf. config.Load).
type Config struct {
	OllamaURL       string
	OllamaModel     string
	OllamaKeepAlive string // durée de maintien en mémoire, ex. "30m"
	OllamaNumThread int    // 0 : laisser Ollama choisir
	SearxURL        string
	// WebSearchAPIKey : clé ollama.com pour l'API de recherche web d'Ollama,
	// essayée avant SearXNG (vide : SearXNG seul). WebSearchURL : pour les tests.
	WebSearchAPIKey string
	WebSearchURL    string
	GeminiAPIKey    string
	GeminiModel     string
}

// ErrBusy : le modèle local traite déjà une demande (un petit CPU n'en mène
// bien qu'une à la fois ; mieux vaut le dire que d'empiler).
var ErrBusy = errors.New("ai is busy")

const (
	generateBudget = 100 * time.Second
	enrichBudget   = 10 * time.Second
	enrichParallel = 3
	maxTitleRunes  = 80
	maxDescRunes   = 300
)

type Service struct {
	searxURL  string
	webSearch *webSearchClient
	ollama    *ollamaClient
	gemini    *geminiClient
	localSlot chan struct{}
}

func NewService(cfg Config) *Service {
	if cfg.OllamaURL == "" {
		cfg.OllamaURL = "http://localhost:11434"
	}
	if cfg.OllamaModel == "" {
		cfg.OllamaModel = "qwen3:1.7b"
	}
	if cfg.OllamaKeepAlive == "" {
		cfg.OllamaKeepAlive = "30m"
	}
	if cfg.GeminiModel == "" {
		cfg.GeminiModel = "gemini-2.5-flash"
	}
	return &Service{
		searxURL:  cfg.SearxURL,
		webSearch: newWebSearchClient(cfg.WebSearchURL, cfg.WebSearchAPIKey),
		ollama:    newOllamaClient(cfg.OllamaURL, cfg.OllamaModel, cfg.OllamaKeepAlive, cfg.OllamaNumThread),
		gemini:    newGeminiClient(cfg.GeminiAPIKey, cfg.GeminiModel),
		localSlot: make(chan struct{}, 1),
	}
}

// Warmup prépare le modèle local (téléchargement si besoin, chargement en
// mémoire). Au démarrage du compose, Ollama peut ne pas encore répondre : on
// réessaie quelques minutes avant d'abandonner (la génération réessaiera).
func (s *Service) Warmup(ctx context.Context) {
	for attempt := 0; attempt < 18; attempt++ {
		if s.ollama.EnsureReady(ctx) {
			return
		}
		select {
		case <-ctx.Done():
			return
		case <-time.After(10 * time.Second):
		}
	}
	log.Printf("ai: ollama still unavailable, giving up warm-up (generation will retry on demand)")
}

// Generate cherche des sources sur le web puis fait rédiger des idées qui s'y
// rattachent. Les comptes premium passent par Gemini (repli sur le modèle local).
func (s *Service) Generate(ctx context.Context, prompt string, isPremium bool) (*GenerateResponse, error) {
	start := time.Now()
	ctx, cancel := context.WithTimeout(ctx, generateBudget)
	defer cancel()

	useGemini := isPremium && s.gemini != nil
	holding := false
	acquire := func() bool {
		select {
		case s.localSlot <- struct{}{}:
			holding = true
			return true
		default:
			return false
		}
	}
	release := func() {
		if holding {
			<-s.localSlot
			holding = false
		}
	}
	defer release()
	// Modèle local : on réserve le créneau avant de chercher, pour ne pas lancer
	// une recherche web qui ne servirait à rien
	if !useGemini && !acquire() {
		return nil, ErrBusy
	}

	n := wantedCount(prompt)
	sources, provider, err := s.findSources(ctx, prompt)
	if err != nil {
		log.Printf("ai: web search failed, generating without sources: %v", err)
	}
	searchTook := time.Since(start)
	system, user := systemPrompt(n), userPrompt(prompt, sources)

	engine, raw := "", ""
	if useGemini {
		engine = "gemini"
		raw, err = s.gemini.generate(ctx, system, user, n, len(sources))
		if err != nil {
			log.Printf("ai: gemini failed, falling back to local model: %v", err)
			raw = ""
			if !acquire() {
				return nil, ErrBusy
			}
		}
	}
	if raw == "" {
		engine = "ollama"
		raw, err = s.ollama.generate(ctx, system, user, n, len(sources))
		if err != nil {
			return nil, err
		}
	}
	release() // l'enrichissement n'a plus besoin du modèle

	ideas, err := parseIdeas(raw, sources, n)
	if err != nil {
		return nil, err
	}
	enrichFromPages(ctx, ideas)

	// Pas de contenu de la demande dans les journaux (donnée personnelle)
	log.Printf("ai: %s generated %d ideas from %d %s sources in %s (search %s)",
		engine, len(ideas), len(sources), provider, time.Since(start).Round(time.Millisecond), searchTook.Round(time.Millisecond))
	return &GenerateResponse{Ideas: ideas}, nil
}

// findSources essaie l'API de recherche d'Ollama (si configurée) puis SearXNG.
func (s *Service) findSources(ctx context.Context, prompt string) ([]SearchResult, string, error) {
	if s.webSearch != nil {
		res, err := s.webSearch.sources(ctx, prompt)
		if err == nil && len(res) > 0 {
			return res, "ollama-web", nil
		}
		log.Printf("ai: ollama web search gave nothing (%v), falling back to searxng", err)
	}
	res, err := searxSources(ctx, s.searxURL, prompt)
	return res, "searxng", err
}

// rawIdea est une idée telle que décrite par le schéma imposé au modèle.
type rawIdea struct {
	Source      int      `json:"source"`
	Title       string   `json:"title"`
	Description string   `json:"description"`
	Category    string   `json:"category"`
	Tags        []string `json:"tags"`
	AgeRange    string   `json:"ageRange"`
	Price       string   `json:"price"`
}

// parseIdeas valide et normalise la réponse du modèle. L'adresse de chaque
// idée vient du résultat web cité : le modèle n'écrit jamais d'URL.
func parseIdeas(raw string, sources []SearchResult, n int) ([]SuggestedLink, error) {
	var out struct {
		Ideas []rawIdea `json:"ideas"`
	}
	if err := json.Unmarshal([]byte(raw), &out); err != nil {
		// Filets de sécurité : JSON entouré de texte, ou réponse tronquée par la
		// limite de jetons (on garde alors les idées complètes)
		if j := extractJSON(raw); j != "" && json.Unmarshal([]byte(j), &out) == nil {
			// récupéré
		} else if out.Ideas = salvageIdeas(raw); len(out.Ideas) == 0 {
			return nil, fmt.Errorf("invalid ideas JSON: %w", err)
		}
	}
	seen := map[string]bool{}
	ideas := make([]SuggestedLink, 0, len(out.Ideas))
	for _, r := range out.Ideas {
		title := truncate(clean(r.Title), maxTitleRunes)
		key := strings.ToLower(title)
		if title == "" || seen[key] {
			continue
		}
		seen[key] = true
		idea := SuggestedLink{
			Title:       title,
			Description: truncate(clean(r.Description), maxDescRunes),
			Category:    categoryFor(r.Category),
			Tags:        cleanTags(r.Tags),
			AgeRange:    known(r.AgeRange),
		}
		if r.Source >= 1 && r.Source <= len(sources) {
			src := sources[r.Source-1]
			idea.URL = src.URL
			idea.ImageURL = src.ImageURL
			// Un prix n'est gardé que s'il figure dans la source citée : les petits
			// modèles complètent volontiers par « Gratuit » ou un montant inventé.
			// (L'aperçu de la page peut ensuite en fournir un vérifié.)
			if p := known(r.Price); priceInSource(p, src) {
				idea.Price = p
			}
		}
		ideas = append(ideas, idea)
		if len(ideas) == n {
			break
		}
	}
	if len(ideas) == 0 {
		return nil, errors.New("model returned no usable idea")
	}
	return ideas, nil
}

var digitsRe = regexp.MustCompile(`\d+(?:[.,]\d+)?`)

// priceInSource vérifie qu'un prix proposé apparaît dans le texte de la source.
func priceInSource(price string, src SearchResult) bool {
	if price == "" {
		return false
	}
	text := strings.ToLower(src.Title + " " + src.Content)
	if nums := digitsRe.FindAllString(price, -1); len(nums) > 0 {
		for _, n := range nums {
			if !strings.Contains(text, n) && !strings.Contains(text, strings.ReplaceAll(n, ",", ".")) {
				return false
			}
		}
		return true
	}
	return strings.Contains(strings.ToLower(price), "gratuit") && strings.Contains(text, "gratuit")
}

// salvageIdeas lit une à une les idées complètes du tableau "ideas", même si
// la réponse s'arrête au milieu de la suivante.
func salvageIdeas(raw string) []rawIdea {
	i := strings.Index(raw, `"ideas"`)
	if i < 0 {
		return nil
	}
	j := strings.Index(raw[i:], "[")
	if j < 0 {
		return nil
	}
	dec := json.NewDecoder(strings.NewReader(raw[i+j:]))
	if _, err := dec.Token(); err != nil { // « [ »
		return nil
	}
	var out []rawIdea
	for dec.More() {
		var r rawIdea
		if err := dec.Decode(&r); err != nil {
			break
		}
		out = append(out, r)
	}
	return out
}

// enrichFromPages complète chaque idée liée à une page avec l'aperçu de cette
// page : meilleure image, ingrédients d'une recette, prix, lieu, catégorie.
func enrichFromPages(ctx context.Context, ideas []SuggestedLink) {
	ctx, cancel := context.WithTimeout(ctx, enrichBudget)
	defer cancel()
	var wg sync.WaitGroup
	sem := make(chan struct{}, enrichParallel)
	for i := range ideas {
		if ideas[i].URL == "" {
			continue
		}
		wg.Add(1)
		go func(idea *SuggestedLink) {
			defer wg.Done()
			sem <- struct{}{}
			defer func() { <-sem }()
			m, err := preview.Fetch(ctx, idea.URL)
			if err != nil {
				return
			}
			if m.Image != "" {
				idea.ImageURL = m.Image
			}
			// Les données structurées de la page priment sur la supposition du modèle
			if m.Category != "" {
				idea.Category = m.Category
			}
			if idea.Category == "LINK_CATEGORY_RECETTE" && len(m.Ingredients) > 0 {
				idea.Ingredients = m.Ingredients
			}
			if idea.Price == "" {
				idea.Price = m.Price
			}
			if idea.Location == "" {
				idea.Location = m.Location
			}
		}(&ideas[i])
	}
	wg.Wait()
}

func categoryFor(key string) string {
	k := strings.ToLower(strings.TrimSpace(key))
	k = strings.NewReplacer("é", "e", "è", "e", "ê", "e").Replace(k)
	if c, ok := categoryKeys[k]; ok {
		return c
	}
	for short, c := range categoryKeys {
		if strings.Contains(k, short) {
			return c
		}
	}
	return "LINK_CATEGORY_IDEE"
}

func cleanTags(tags []string) []string {
	out := []string{}
	for _, t := range tags {
		t = strings.ToLower(strings.TrimPrefix(clean(t), "#"))
		if t != "" && utf8.RuneCountInString(t) <= 30 && !containsString(out, t) {
			out = append(out, t)
		}
		if len(out) == 3 {
			break
		}
	}
	return out
}

// known écarte les valeurs « je ne sais pas » que les petits modèles écrivent
// malgré la consigne de laisser vide.
func known(v string) string {
	v = truncate(clean(v), 60)
	switch strings.ToLower(strings.Trim(v, " .")) {
	case "", "-", "n/a", "na", "inconnu", "non précisé", "non precise", "non communiqué", "variable", "?", "aucun", "none", "null":
		return ""
	}
	return v
}

func clean(s string) string { return strings.Join(strings.Fields(s), " ") }

func truncate(s string, n int) string {
	if utf8.RuneCountInString(s) <= n {
		return s
	}
	return strings.TrimSpace(string([]rune(s)[:n-1])) + "…"
}

func containsString(list []string, v string) bool {
	for _, x := range list {
		if x == v {
			return true
		}
	}
	return false
}

// extractJSON isole le premier objet JSON équilibré d'un texte.
func extractJSON(s string) string {
	start := strings.Index(s, "{")
	if start == -1 {
		return ""
	}
	depth, inString, escaped := 0, false, false
	for i := start; i < len(s); i++ {
		c := s[i]
		switch {
		case escaped:
			escaped = false
		case c == '\\' && inString:
			escaped = true
		case c == '"':
			inString = !inString
		case inString:
		case c == '{':
			depth++
		case c == '}':
			depth--
			if depth == 0 {
				return s[start : i+1]
			}
		}
	}
	return ""
}
