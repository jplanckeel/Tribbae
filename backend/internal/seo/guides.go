package seo

import (
	"context"
	"encoding/json"
	"log"
	"net/http"
	"os"
	"regexp"
)

// Guides : pages qui répondent à une recherche précise (« application pour
// garder ses idées », « liste de cadeaux partagée », « idées d'activités pour
// enfants »…), lues par les moteurs et les assistants IA. Le contenu est
// partagé avec le client (guides.json, pages/Guide.tsx) ; une page de guide
// montre aussi les dernières idées publiées de sa catégorie.

type guide struct {
	Slug        string `json:"slug"`
	Title       string `json:"title"`
	Description string `json:"description"`
	H1          string `json:"h1"`
	Intro       string `json:"intro"`
	Ideas       *struct {
		Category string `json:"category"`
		Title    string `json:"title"`
	} `json:"ideas"`
	Sections []struct {
		Title      string   `json:"title"`
		Paragraphs []string `json:"paragraphs"`
		Items      []string `json:"items"`
	} `json:"sections"`
	Steps *struct {
		Title string   `json:"title"`
		Items []string `json:"items"`
	} `json:"steps"`
	FAQ     [][2]string `json:"faq"`
	Related []string    `json:"related"`
}

// guideSlug : forme des adresses des guides (un mot-clé par segment).
var guideSlug = regexp.MustCompile(`^[a-z0-9]+(?:-[a-z0-9]+)*$`)

const guideIdeas = 8

// loadGuides lit guides.json ; absent ou invalide, aucun guide.
func loadGuides(path string) []guide {
	b, err := os.ReadFile(path)
	if err != nil {
		return nil
	}
	var gs []guide
	if err := json.Unmarshal(b, &gs); err != nil {
		log.Printf("seo: invalid guides.json: %v", err)
		return nil
	}
	out := gs[:0]
	for _, g := range gs {
		if guideSlug.MatchString(g.Slug) && g.H1 != "" {
			out = append(out, g)
		} else {
			log.Printf("seo: guides.json: invalid guide %q", g.Slug)
		}
	}
	return out
}

func (h *Handler) guideBySlug(slug string) *guide {
	for i := range h.guides {
		if h.guides[i].Slug == slug {
			return &h.guides[i]
		}
	}
	return nil
}

// guidesSection : liens vers les guides (accueil, catégories, autres guides).
func (h *Handler) guidesSection(title string, only []string) Section {
	s := Section{Title: title}
	for _, g := range h.guides {
		if only != nil && !contains(only, g.Slug) {
			continue
		}
		s.Items = append(s.Items, Item{Title: g.H1, Path: "/" + g.Slug})
	}
	return s
}

func contains(list []string, v string) bool {
	for _, x := range list {
		if x == v {
			return true
		}
	}
	return false
}

func (h *Handler) guidePage(ctx context.Context, g *guide) (*Page, error) {
	path := "/" + g.Slug
	p := &Page{
		Status: http.StatusOK, Title: g.Title, Description: g.Description, Path: path, OGType: "article",
		Breadcrumb: []Crumb{{"Accueil", "/"}, {"Guides", "/guides"}, {g.H1, path}},
		H1:         g.H1, Intro: g.Intro,
	}
	for _, s := range g.Sections {
		p.Blocks = append(p.Blocks, Block{Title: s.Title, Paragraphs: s.Paragraphs, Items: s.Items})
	}
	if g.Steps != nil {
		p.Blocks = append(p.Blocks, Block{Title: g.Steps.Title, Items: g.Steps.Items, Ordered: true})
	}
	for _, qa := range g.FAQ {
		p.Questions = append(p.Questions, QA{Q: qa[0], A: qa[1]})
	}
	var items []Item
	if g.Ideas != nil {
		ideas, err := h.src.PublishedIdeas(ctx, g.Ideas.Category, guideIdeas)
		if err != nil {
			return nil, err
		}
		if len(ideas) > 0 {
			p.Sections = append(p.Sections, ideaSection(g.Ideas.Title, ideas))
			items = ideaItems(ideas)
		}
		if c := categoryByKey(g.Ideas.Category); c.Slug != "" {
			p.Sections = append(p.Sections, Section{Title: "Aller plus loin", Items: []Item{{Title: c.Plural + " en famille", Path: "/category/" + c.Slug, Text: c.Intro}}})
		}
	}
	if len(g.Related) > 0 {
		p.Sections = append(p.Sections, h.guidesSection("Guides liés", g.Related))
	}
	p.JSONLD = []any{h.guideSchema(g, p), h.breadcrumbs(p.Breadcrumb)}
	if len(p.Questions) > 0 {
		p.JSONLD = append(p.JSONLD, faqSchema(p.Questions))
	}
	if len(items) > 0 {
		p.JSONLD = append(p.JSONLD, h.itemList(g.Ideas.Title, items))
	}
	return p, nil
}

// guidesIndex : la liste des guides (/guides).
func (h *Handler) guidesIndex() *Page {
	if len(h.guides) == 0 {
		return notFound()
	}
	p := &Page{
		Status: http.StatusOK, Title: "Guides : idées en famille, listes partagées · " + siteName,
		Description: "Les guides Tribbae : garder ses idées et ses liens, sauvegarder les recettes d'Instagram, listes de cadeaux et de courses partagées, activités pour enfants.",
		Path:        "/guides",
		Breadcrumb:  []Crumb{{"Accueil", "/"}, {"Guides", "/guides"}},
		H1:          "Les guides Tribbae",
		Intro:       "Comment garder, ranger et partager les idées de la famille, pas à pas.",
	}
	s := Section{Title: "Tous les guides"}
	for _, g := range h.guides {
		s.Items = append(s.Items, Item{Title: g.H1, Path: "/" + g.Slug, Text: g.Description})
	}
	p.Sections = []Section{s}
	p.JSONLD = []any{h.collectionPage(p, s.Items), h.breadcrumbs(p.Breadcrumb)}
	return p
}

func (h *Handler) guideSchema(g *guide, p *Page) obj {
	return obj{
		"@context": schemaContext, "@type": "Article",
		"headline": g.H1, "name": g.Title, "description": g.Description,
		"url": h.site + p.Path, "inLanguage": "fr-FR",
		"mainEntityOfPage": h.site + p.Path,
		"image":            h.site + defaultImage,
		"author":           obj{"@id": h.site + "/#organization"},
		"publisher":        obj{"@id": h.site + "/#organization"},
		"isPartOf":         obj{"@id": h.site + "/#website"},
		"about":            obj{"@id": h.site + "/#app"},
	}
}

func faqSchema(qs []QA) obj {
	entities := make([]obj, 0, len(qs))
	for _, q := range qs {
		entities = append(entities, obj{"@type": "Question", "name": q.Q, "acceptedAnswer": obj{"@type": "Answer", "text": q.A}})
	}
	return obj{"@context": schemaContext, "@type": "FAQPage", "mainEntity": entities}
}
