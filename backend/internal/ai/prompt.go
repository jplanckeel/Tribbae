package ai

import (
	"fmt"
	"regexp"
	"strconv"
	"strings"
)

// Catégories proposées au modèle sous une forme courte (moins de jetons à
// générer qu'avec les noms d'enum complets), puis converties.
var categoryKeys = map[string]string{
	"idee":     "LINK_CATEGORY_IDEE",
	"cadeau":   "LINK_CATEGORY_CADEAU",
	"activite": "LINK_CATEGORY_ACTIVITE",
	"sortie":   "LINK_CATEGORY_EVENEMENT",
	"recette":  "LINK_CATEGORY_RECETTE",
	"livre":    "LINK_CATEGORY_LIVRE",
	"deco":     "LINK_CATEGORY_DECORATION",
}

var categoryEnum = []string{"activite", "sortie", "recette", "cadeau", "livre", "deco", "idee"}

const (
	defaultIdeas = 5
	maxIdeas     = 8
	minIdeas     = 3
)

// ideaSchema est le schéma JSON imposé à la réponse (paramètre `format`
// d'Ollama) : le modèle ne peut pas produire de JSON invalide. minItems force
// le nombre d'idées (les petits modèles s'arrêtent sinon après une ou deux).
// Ni lien ni prix : sans recherche web, ce seraient des inventions.
func ideaSchema(n int) map[string]any {
	return map[string]any{
		"type": "object",
		"properties": map[string]any{
			"ideas": map[string]any{
				"type":     "array",
				"minItems": n,
				"maxItems": n,
				"items": map[string]any{
					"type": "object",
					"properties": map[string]any{
						"title":       map[string]any{"type": "string"},
						"description": map[string]any{"type": "string"},
						"category":    map[string]any{"type": "string", "enum": categoryEnum},
						"tags":        map[string]any{"type": "array", "items": map[string]any{"type": "string"}, "maxItems": 3},
						"ageRange":    map[string]any{"type": "string"},
					},
					"required": []string{"title", "description", "category", "tags", "ageRange"},
				},
			},
		},
		"required": []string{"ideas"},
	}
}

func systemPrompt(n int) string {
	return fmt.Sprintf(`Tu es l'assistant de Tribbae, une application où les familles rangent leurs idées : sorties, activités, recettes, cadeaux, livres, déco.
Propose %d idées concrètes, variées et adaptées à des parents, en réponse à la demande.
Règles :
- Une idée = une chose précise à faire, offrir, cuisiner ou lire (pas « 10 idées de… »).
- title : court, 60 caractères maximum.
- description : une phrase utile pour des parents, 120 caractères maximum.
- category : la catégorie la plus juste.
- tags : 1 à 3 mots-clés en minuscules.
- ageRange : tranche d'âge adaptée (ex. "3-6 ans"), "" si l'idée convient à tous.
- N'invente ni adresse, ni prix, ni date, ni nom de lieu précis que tu ne connais pas.
- Réponds uniquement en français.`, n)
}

// queryHints : mots qui révèlent la catégorie évidente d'une demande.
var queryHints = []struct {
	words    []string
	category string
}{
	{[]string{"recette", "cuisine", "goûter", "gouter", "repas", "dessert", "gâteau", "gateau", "menu"}, "recette"},
	{[]string{"cadeau", "offrir", "anniversaire", "noël", "noel"}, "cadeau"},
	{[]string{"livre", "lecture", "roman", "album"}, "livre"},
	{[]string{"sortie", "visite", "autour de", "près de", "pres de", "week-end", "weekend", "balade"}, "sortie"},
	{[]string{"déco", "deco", "décoration", "decoration", "chambre"}, "deco"},
}

// expectedCategory retourne la catégorie évidente d'une demande, ou "".
func expectedCategory(prompt string) string {
	lower := strings.ToLower(prompt)
	for _, h := range queryHints {
		for _, w := range h.words {
			if strings.Contains(lower, w) {
				return h.category
			}
		}
	}
	return ""
}

// userPrompt présente la demande, avec la catégorie probable si elle est évidente.
func userPrompt(prompt string) string {
	var b strings.Builder
	fmt.Fprintf(&b, "Demande : %s\n", prompt)
	if c := expectedCategory(prompt); c != "" {
		fmt.Fprintf(&b, "Catégorie probable : %s\n", c)
	}
	return b.String()
}

// countRe : un nombre en tête de demande (« 10 activités… », « Top 5 des… »).
// Ailleurs, un nombre est plutôt un âge ou un nombre d'enfants.
var countRe = regexp.MustCompile(`(?i)^\s*(?:top\s+)?(\d{1,2})\s+\pL`)

// wantedCount lit le nombre d'idées demandé et le borne : chaque idée coûte
// ~80 jetons générés, soit plusieurs secondes sur un petit CPU.
func wantedCount(prompt string) int {
	if m := countRe.FindStringSubmatch(prompt); m != nil {
		if n, err := strconv.Atoi(m[1]); err == nil {
			return min(max(n, minIdeas), maxIdeas)
		}
	}
	return defaultIdeas
}
