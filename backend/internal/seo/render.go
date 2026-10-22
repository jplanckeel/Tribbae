package seo

import (
	"bytes"
	"encoding/json"
	"fmt"
	"html/template"
	"strings"
	"time"
	"unicode/utf8"
)

// Page décrit ce qu'un moteur (ou un aperçu de partage) voit d'une adresse :
// l'en-tête et un contenu HTML lisible sans JavaScript. Le client React
// remplace ce contenu dès qu'il démarre.
type Page struct {
	Status      int
	Title       string
	Description string
	Path        string // adresse canonique, sans domaine (vide : pas de canonique)
	Image       string // absolue ; vide : image par défaut du site
	OGType      string // website, article…
	NoIndex     bool
	JSONLD      []any

	// Article (og:type article) : dates et catégorie
	Published, Modified time.Time
	Section             string

	// Contenu
	Breadcrumb  []Crumb
	H1          string
	Intro       string
	Facts       []Fact
	Tags        []string
	Ingredients []string
	Source      string // page d'origine de l'idée
	Sections    []Section
	FAQ         []QA
}

type Crumb struct{ Name, Path string }
type Fact struct{ Label, Value string }
type Section struct {
	Title string
	Items []Item
}
type Item struct{ Title, Path, Text string }
type QA struct{ Q, A string }

const (
	defaultImage        = "/og-image.png"
	defaultImageWidth   = 1200
	defaultImageHeight  = 630
	maxDescriptionRunes = 160
	maxArticleTags      = 10
)

var headTmpl = template.Must(template.New("head").Parse(`<title data-seo>{{.Title}}</title>
    <meta data-seo name="description" content="{{.Description}}" />
    <meta data-seo name="robots" content="{{.Robots}}" />
    {{- if .Canonical}}
    <link data-seo rel="canonical" href="{{.Canonical}}" />{{end}}
    <meta property="og:type" content="{{.OGType}}" />
    <meta property="og:site_name" content="Tribbae" />
    <meta property="og:locale" content="fr_FR" />
    <meta property="og:title" content="{{.Title}}" />
    <meta property="og:description" content="{{.Description}}" />
    {{- if .Canonical}}
    <meta property="og:url" content="{{.Canonical}}" />{{end}}
    {{- if .Published}}
    <meta property="article:published_time" content="{{.Published}}" />{{end}}
    {{- if .Modified}}
    <meta property="article:modified_time" content="{{.Modified}}" />{{end}}
    {{- if .Section}}
    <meta property="article:section" content="{{.Section}}" />{{end}}
    {{- range .ArticleTags}}
    <meta property="article:tag" content="{{.}}" />{{end}}
    <meta property="og:image" content="{{.Image}}" />
    {{- if .ImageSize}}
    <meta property="og:image:width" content="{{index .ImageSize 0}}" />
    <meta property="og:image:height" content="{{index .ImageSize 1}}" />{{end}}
    <meta property="og:image:alt" content="{{.ImageAlt}}" />
    <meta name="twitter:card" content="summary_large_image" />
    <meta name="twitter:title" content="{{.Title}}" />
    <meta name="twitter:description" content="{{.Description}}" />
    <meta name="twitter:image" content="{{.Image}}" />
    {{- range .JSONLD}}
    <script type="application/ld+json">{{.}}</script>{{end}}`))

var bodyTmpl = template.Must(template.New("body").Parse(`<main class="seo-fallback">
      <nav aria-label="Navigation"><a href="/">Tribbae</a> · <a href="/explorer">Explorer les idées</a> · <a href="/aide">Aide</a></nav>
      {{- if .Breadcrumb}}
      <p class="crumbs">{{range $i, $c := .Breadcrumb}}{{if $i}} › {{end}}<a href="{{$c.Path}}">{{$c.Name}}</a>{{end}}</p>{{end}}
      <h1>{{.H1}}</h1>
      {{- if .Intro}}
      <p>{{.Intro}}</p>{{end}}
      {{- if .Facts}}
      <ul class="facts">{{range .Facts}}<li><strong>{{.Label}} :</strong> {{.Value}}</li>{{end}}</ul>{{end}}
      {{- if .Ingredients}}
      <h2>Ingrédients</h2>
      <ul>{{range .Ingredients}}<li>{{.}}</li>{{end}}</ul>{{end}}
      {{- if .Tags}}
      <p class="tags">{{range .Tags}}#{{.}} {{end}}</p>{{end}}
      {{- if .Source}}
      <p><a href="{{.Source}}" rel="nofollow ugc noopener">Voir la page d'origine</a></p>{{end}}
      {{- range .Sections}}
      <section>
        <h2>{{.Title}}</h2>
        <ul>{{range .Items}}<li><a href="{{.Path}}">{{.Title}}</a>{{if .Text}} — {{.Text}}{{end}}</li>{{end}}</ul>
      </section>{{end}}
      {{- range .FAQ}}
      <h2>{{.Q}}</h2>
      <p>{{.A}}</p>{{end}}
    </main>`))

type headData struct {
	Title, Description, Robots, Canonical, OGType, Image, ImageAlt string
	Published, Modified, Section                                   string
	ArticleTags                                                    []string
	ImageSize                                                      []int
	JSONLD                                                         []template.JS
}

func (p *Page) head(site string) ([]byte, error) {
	d := headData{
		Title:       p.Title,
		Description: clip(p.Description, maxDescriptionRunes),
		Robots:      "index, follow, max-image-preview:large, max-snippet:-1",
		OGType:      p.OGType,
		Image:       p.Image,
		ImageAlt:    p.H1,
	}
	if d.OGType == "" {
		d.OGType = "website"
	}
	if p.NoIndex {
		d.Robots = "noindex, follow"
	}
	if d.OGType == "article" {
		if !p.Published.IsZero() {
			d.Published = p.Published.UTC().Format(time.RFC3339)
		}
		if !p.Modified.IsZero() {
			d.Modified = p.Modified.UTC().Format(time.RFC3339)
		}
		d.Section = p.Section
		d.ArticleTags = p.Tags[:min(len(p.Tags), maxArticleTags)]
	}
	if p.Path != "" {
		d.Canonical = site + p.Path
	}
	if d.Image == "" {
		d.Image = site + defaultImage
		d.ImageSize = []int{defaultImageWidth, defaultImageHeight}
		d.ImageAlt = "Tribbae — les idées de la famille au même endroit"
	}
	for _, v := range p.JSONLD {
		// json.Marshal échappe <, > et & : rien ne peut fermer la balise script
		b, err := json.Marshal(v)
		if err != nil {
			return nil, fmt.Errorf("json-ld: %w", err)
		}
		d.JSONLD = append(d.JSONLD, template.JS(b))
	}
	var buf bytes.Buffer
	err := headTmpl.Execute(&buf, d)
	return buf.Bytes(), err
}

func (p *Page) body() ([]byte, error) {
	var buf bytes.Buffer
	err := bodyTmpl.Execute(&buf, p)
	return buf.Bytes(), err
}

// clip coupe un texte à n caractères sur une fin de mot, espaces normalisés.
func clip(s string, n int) string {
	s = strings.Join(strings.Fields(s), " ")
	if utf8.RuneCountInString(s) <= n {
		return s
	}
	r := []rune(s)[:n-1]
	if i := strings.LastIndexByte(string(r), ' '); i > n/2 {
		return strings.TrimRight(string(r)[:i], " ,.;:") + "…"
	}
	return string(r) + "…"
}
