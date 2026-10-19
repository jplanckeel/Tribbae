package ai

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"log"
	"strings"
	"time"
	"unicode/utf8"
)

// SuggestedLink représente une idée générée par l'IA.
type SuggestedLink struct {
	Title       string   `json:"title"`
	Description string   `json:"description"`
	Category    string   `json:"category"`
	Tags        []string `json:"tags"`
	AgeRange    string   `json:"ageRange,omitempty"`
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
}

// ErrBusy : le modèle local traite déjà une demande (un petit CPU n'en mène
// bien qu'une à la fois ; mieux vaut le dire que d'empiler).
var ErrBusy = errors.New("ai is busy")

const (
	generateBudget = 100 * time.Second
	maxTitleRunes  = 80
	maxDescRunes   = 300
)

type Service struct {
	ollama    *ollamaClient
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
	return &Service{
		ollama:    newOllamaClient(cfg.OllamaURL, cfg.OllamaModel, cfg.OllamaKeepAlive, cfg.OllamaNumThread),
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

// Generate fait rédiger des idées par le modèle local d'Ollama. Un petit CPU
// ne mène bien qu'une génération à la fois : une demande concurrente reçoit
// ErrBusy plutôt que d'attendre.
func (s *Service) Generate(ctx context.Context, prompt string) (*GenerateResponse, error) {
	select {
	case s.localSlot <- struct{}{}:
		defer func() { <-s.localSlot }()
	default:
		return nil, ErrBusy
	}
	start := time.Now()
	ctx, cancel := context.WithTimeout(ctx, generateBudget)
	defer cancel()

	n := wantedCount(prompt)
	raw, err := s.ollama.generate(ctx, systemPrompt(n), userPrompt(prompt), n)
	if err != nil {
		return nil, err
	}
	ideas, err := parseIdeas(raw, n)
	if err != nil {
		return nil, err
	}
	// Pas de contenu de la demande dans les journaux (donnée personnelle)
	log.Printf("ai: generated %d ideas in %s", len(ideas), time.Since(start).Round(time.Millisecond))
	return &GenerateResponse{Ideas: ideas}, nil
}

// rawIdea est une idée telle que décrite par le schéma imposé au modèle.
type rawIdea struct {
	Title       string   `json:"title"`
	Description string   `json:"description"`
	Category    string   `json:"category"`
	Tags        []string `json:"tags"`
	AgeRange    string   `json:"ageRange"`
}

// parseIdeas valide et normalise la réponse du modèle.
func parseIdeas(raw string, n int) ([]SuggestedLink, error) {
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
