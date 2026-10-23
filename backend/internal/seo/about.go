package seo

import (
	"encoding/json"
	"log"
	"net/http"
	"os"
	"strings"
)

const aboutDescription = "Pourquoi Tribbae existe, ce que fait l'application et nos principes : gratuite, sans publicité, idées privées par défaut, IA hébergée sur notre serveur."

// aboutContent : la page À propos, partagée avec le client (about.json).
type aboutContent struct {
	Intro    string `json:"intro"`
	Contact  string `json:"contact"`
	Sections []struct {
		Title      string   `json:"title"`
		Paragraphs []string `json:"paragraphs"`
		Items      []string `json:"items"`
	} `json:"sections"`
}

// loadAbout lit about.json ; absent ou invalide, /a-propos est une page
// inconnue.
func loadAbout(path string) *aboutContent {
	b, err := os.ReadFile(path)
	if err != nil {
		return nil
	}
	var a aboutContent
	if err := json.Unmarshal(b, &a); err != nil {
		log.Printf("seo: invalid about.json: %v", err)
		return nil
	}
	return &a
}

func (h *Handler) aboutPage() *Page {
	if h.about == nil {
		return notFound()
	}
	p := &Page{
		Status: http.StatusOK, Title: "À propos de " + siteName, Description: aboutDescription,
		Path:       "/a-propos",
		Breadcrumb: []Crumb{{"Accueil", "/"}, {"À propos", "/a-propos"}},
		H1:         "À propos de Tribbae", Intro: h.about.Intro,
	}
	for _, s := range h.about.Sections {
		p.FAQ = append(p.FAQ, QA{Q: s.Title, A: strings.Join(append(s.Paragraphs, s.Items...), " ")})
	}
	if h.about.Contact != "" {
		p.FAQ = append(p.FAQ, QA{Q: "Nous écrire", A: "Une question, une idée d'amélioration ou un souci : écrivez à " + h.about.Contact + "."})
	}
	p.JSONLD = []any{obj{
		"@context": schemaContext, "@type": "AboutPage",
		"name": p.Title, "url": h.site + p.Path, "description": p.Description, "inLanguage": "fr-FR",
		"isPartOf":   obj{"@id": h.site + "/#website"},
		"about":      obj{"@id": h.site + "/#organization"},
		"mainEntity": obj{"@id": h.site + "/#organization"},
	}, h.breadcrumbs(p.Breadcrumb)}
	return p
}
