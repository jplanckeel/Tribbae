package seo

import (
	"context"
	"fmt"
	"net/http"
	"net/url"
	"regexp"
	"strings"
	"time"
	"unicode/utf8"
)

const (
	siteName    = "Tribbae"
	siteTagline = "Tribbae — Les idées de la famille au même endroit"
	// homeDescription : la description de l'accueil, complète en moins de 155 caractères
	homeDescription = "Tribbae, l'appli gratuite des idées en famille : sorties, recettes, cadeaux, livres. Rangez-les en dossiers et partagez-les avec votre tribu."
	siteDescription = "Tribbae rassemble les idées de la famille : sorties, activités, recettes, cadeaux, livres et déco. Rangez-les en dossiers, partagez-les avec votre tribu et piochez dans celles des autres familles."
	listLimit       = 30
)

// private : écrans réservés aux comptes, sans intérêt pour un moteur.
var private = map[string]string{
	"/mes-idees":             "Mes idées",
	"/courses":               "Liste de courses",
	"/tribu":                 "Ma tribu",
	"/admin":                 "Administration",
	"/partager":              "Ajouter une idée",
	"/connexion":             "Connexion",
	"/bienvenue":             "Bienvenue",
	"/invitation":            "Invitation dans une tribu",
	"/invitation-cercle":     "Invitation dans le cercle",
	"/mot-de-passe/oublie":   "Mot de passe oublié",
	"/mot-de-passe/nouveau":  "Nouveau mot de passe",
	"/verifier-email":        "Confirmer mon adresse",
	"/connecter-application": "Connecter une application",
}

// redirects : anciennes adresses du site, redirigées définitivement.
var redirects = map[string]string{
	"/login":    "/connexion",
	"/discover": "/explorer?vue=dossiers",
	"/folders":  "/mes-idees",
	"/tags":     "/mes-idees?vue=tags",
	"/children": "/tribu",
}

// redirect retourne la nouvelle adresse d'une ancienne, ou "".
func redirect(path string) string {
	if to, ok := redirects[path]; ok {
		return to
	}
	if slug, ok := strings.CutPrefix(path, "/categorie/"); ok && slug != "" {
		return "/category/" + url.PathEscape(slug)
	}
	return ""
}

// Identifiants lus dans l'adresse : une valeur malformée est une page inconnue,
// sans requête en base. Rien de l'adresse n'est repris tel quel dans la page.
var (
	objectID   = regexp.MustCompile(`^[0-9a-f]{24}$`) // idée, dossier (ObjectID MongoDB)
	shareToken = regexp.MustCompile(`^[0-9a-f]{32}$`) // lien de partage (folder.GenerateShareToken)
)

// page construit la page d'une adresse du site.
func (h *Handler) page(ctx context.Context, path string, query url.Values) (*Page, error) {
	if len(path) > 1 {
		path = strings.TrimRight(path, "/")
	}
	switch {
	case path == "/":
		return h.home(ctx)
	case path == "/explorer":
		p, err := h.explorer(ctx)
		if p != nil && (query.Get("q") != "" || query.Get("vue") != "") {
			// Résultats de recherche et vues filtrées : explorés, pas indexés
			p.NoIndex = true
		}
		return p, err
	case strings.HasPrefix(path, "/category/"):
		return h.category(ctx, strings.TrimPrefix(path, "/category/"))
	case strings.HasPrefix(path, "/links/"):
		if id := strings.TrimPrefix(path, "/links/"); objectID.MatchString(id) {
			return h.idea(ctx, id)
		}
		return notFound(), nil
	case strings.HasPrefix(path, "/dossiers/"):
		if id := strings.TrimPrefix(path, "/dossiers/"); objectID.MatchString(id) {
			return h.folder(ctx, id)
		}
		return notFound(), nil
	case strings.HasPrefix(path, "/share/"):
		if token := strings.TrimPrefix(path, "/share/"); shareToken.MatchString(token) {
			return h.shared(ctx, token)
		}
		return notFound(), nil
	case path == "/aide":
		return h.help(), nil
	case path == "/confidentialite":
		return privacy(), nil
	case path == "/a-propos":
		return h.aboutPage(), nil
	}
	if title, ok := private[path]; ok {
		return &Page{Status: http.StatusOK, Title: title + " · " + siteName, Description: siteDescription, NoIndex: true, H1: title}, nil
	}
	return notFound(), nil
}

func notFound() *Page {
	return &Page{
		Status: http.StatusNotFound, Title: "Page introuvable · " + siteName, Description: siteDescription,
		NoIndex: true, H1: "Page introuvable", Intro: "Cette page n'existe pas ou n'est pas publique.",
	}
}

func (h *Handler) home(ctx context.Context) (*Page, error) {
	ideas, err := h.src.PublishedIdeas(ctx, "", listLimit)
	if err != nil {
		return nil, err
	}
	p := &Page{
		Status: http.StatusOK, Title: siteTagline, Description: homeDescription, Path: "/",
		H1: "Les idées de la famille au même endroit", Intro: siteDescription,
		Sections: []Section{categorySection(), ideaSection("Dernières idées partagées", ideas)},
	}
	p.JSONLD = []any{h.siteGraph(), h.itemList("Dernières idées partagées sur Tribbae", ideaItems(ideas))}
	return p, nil
}

func (h *Handler) explorer(ctx context.Context) (*Page, error) {
	ideas, err := h.src.PublishedIdeas(ctx, "", listLimit)
	if err != nil {
		return nil, err
	}
	folders, err := h.src.PublishedFolders(ctx, listLimit)
	if err != nil {
		return nil, err
	}
	p := &Page{
		Status: http.StatusOK, Title: "Explorer les idées des familles · " + siteName,
		Description: "Sorties, activités, recettes, cadeaux, livres et déco : les idées et dossiers publiés par les familles de Tribbae, à garder dans vos propres listes.",
		Path:        "/explorer",
		Breadcrumb:  []Crumb{{"Accueil", "/"}, {"Explorer", "/explorer"}},
		H1:          "Explorer les idées des familles",
		Intro:       "Les idées et dossiers que les familles de Tribbae ont choisi de partager.",
		Sections:    []Section{categorySection(), ideaSection("Idées récentes", ideas), folderSection(folders)},
	}
	p.JSONLD = []any{
		h.collectionPage(p, ideaItems(ideas)),
		h.breadcrumbs(p.Breadcrumb),
	}
	return p, nil
}

func (h *Handler) category(ctx context.Context, slug string) (*Page, error) {
	c, ok := categoryBySlug(slug)
	if !ok {
		return notFound(), nil
	}
	ideas, err := h.src.PublishedIdeas(ctx, c.Key, listLimit)
	if err != nil {
		return nil, err
	}
	path := "/category/" + c.Slug
	p := &Page{
		Status: http.StatusOK, Title: c.Plural + " en famille : les idées des tribus · " + siteName,
		Description: c.Intro, Path: path,
		Breadcrumb: []Crumb{{"Accueil", "/"}, {"Explorer", "/explorer"}, {c.Plural, path}},
		H1:         c.Plural + " en famille", Intro: c.Intro,
		Sections: []Section{ideaSection(c.Plural+" partagées par les familles", ideas), categorySection()},
	}
	if len(ideas) == 0 {
		p.Sections[0] = Section{Title: "Bientôt ici", Items: []Item{{Title: "Explorer toutes les idées", Path: "/explorer"}}}
	}
	p.JSONLD = []any{h.collectionPage(p, ideaItems(ideas)), h.breadcrumbs(p.Breadcrumb)}
	return p, nil
}

func (h *Handler) idea(ctx context.Context, id string) (*Page, error) {
	i, err := h.src.Idea(ctx, id)
	if err != nil || i == nil {
		return notFound(), err
	}
	c := categoryByKey(i.Category)
	path := "/links/" + i.ID
	p := &Page{
		Status: http.StatusOK, Title: ideaTitle(i),
		Description: ideaDescription(i, c), Path: path, Image: absoluteImage(i.ImageURL), OGType: "article",
		Published: i.Created, Modified: i.Updated, Section: c.Label,
		Breadcrumb: []Crumb{{"Accueil", "/"}, {c.Plural, "/category/" + c.Slug}, {i.Title, path}},
		H1:         i.Title, Intro: i.Description, Tags: i.Tags, Ingredients: i.Ingredients,
		Source: safeURL(i.URL),
	}
	if i.Author != "" {
		p.Facts = append(p.Facts, Fact{"Partagée par", i.Author})
	}
	p.Facts = append(p.Facts, ideaFacts(i)...)
	p.Sections = []Section{{Title: plusDe(strings.ToLower(c.Plural)), Items: []Item{{Title: c.Plural + " en famille", Path: "/category/" + c.Slug}}}}
	p.JSONLD = []any{h.ideaSchema(i, c, p), h.breadcrumbs(p.Breadcrumb)}
	return p, nil
}

// plusDe élide « de » devant une voyelle : « Plus d'activités », « Plus de recettes ».
func plusDe(s string) string {
	if r, _ := utf8.DecodeRuneInString(s); strings.ContainsRune("aàâeéèêiîoôuy", r) {
		return "Plus d'" + s
	}
	return "Plus de " + s
}

func (h *Handler) folder(ctx context.Context, id string) (*Page, error) {
	f, err := h.src.Folder(ctx, id)
	if err != nil || f == nil {
		return notFound(), err
	}
	path := "/dossiers/" + f.ID
	desc := fmt.Sprintf("%s : %d idée%s rassemblée%s", f.Name, len(f.Ideas), plural(len(f.Ideas)), plural(len(f.Ideas)))
	if f.Author != "" {
		desc += " par " + f.Author
	}
	desc += " sur Tribbae."
	if titles := ideaTitles(f.Ideas, 4); titles != "" {
		desc += " " + titles
	}
	p := &Page{
		Status: http.StatusOK, Title: f.Name + " · dossier d'idées · " + siteName,
		Description: desc, Path: path, Image: absoluteImage(f.BannerURL),
		Breadcrumb: []Crumb{{"Accueil", "/"}, {"Explorer", "/explorer"}, {f.Name, path}},
		H1:         f.Name, Intro: desc, Tags: f.Tags,
		Sections: []Section{ideaSection("Les idées du dossier", f.Ideas)},
	}
	p.JSONLD = []any{h.collectionPage(p, ideaItems(f.Ideas)), h.breadcrumbs(p.Breadcrumb)}
	return p, nil
}

// shared : dossier ouvert par un lien de partage. L'aperçu (WhatsApp, Messenger…)
// affiche son nom, mais l'adresse est secrète : jamais indexée.
func (h *Handler) shared(ctx context.Context, token string) (*Page, error) {
	f, err := h.src.SharedFolder(ctx, token)
	if err != nil || f == nil {
		return notFound(), err
	}
	who := "Une famille"
	if f.Author != "" {
		who = f.Author
	}
	return &Page{
		Status: http.StatusOK, Title: f.Name + " · partagé sur " + siteName,
		Description: who + " vous partage le dossier « " + f.Name + " » sur Tribbae : ouvrez-le pour découvrir ses idées.",
		Image:       absoluteImage(f.BannerURL), NoIndex: true, H1: f.Name,
	}, nil
}

func (h *Handler) help() *Page {
	p := &Page{
		Status: http.StatusOK, Title: "Aide & FAQ · " + siteName,
		Description: "Questions fréquentes sur Tribbae : créer sa tribu, ajouter et ranger des idées, partager un dossier, idées générées par l'IA, confidentialité.",
		Path:        "/aide",
		Breadcrumb:  []Crumb{{"Accueil", "/"}, {"Aide", "/aide"}},
		H1:          "Aide & FAQ", FAQ: h.faq,
	}
	var questions []any
	for _, qa := range h.faq {
		questions = append(questions, map[string]any{
			"@type": "Question", "name": qa.Q,
			"acceptedAnswer": map[string]any{"@type": "Answer", "text": qa.A},
		})
	}
	if len(questions) > 0 {
		p.JSONLD = append(p.JSONLD, map[string]any{"@context": "https://schema.org", "@type": "FAQPage", "mainEntity": questions})
	}
	p.JSONLD = append(p.JSONLD, h.breadcrumbs(p.Breadcrumb))
	return p
}

// ── contenu ─────────────────────────────────────────────

func categorySection() Section {
	s := Section{Title: "Les catégories"}
	for _, c := range categories {
		s.Items = append(s.Items, Item{Title: c.Plural, Path: "/category/" + c.Slug, Text: c.Intro})
	}
	return s
}

func ideaSection(title string, ideas []Idea) Section {
	s := Section{Title: title}
	for _, i := range ideas {
		s.Items = append(s.Items, Item{Title: i.Title, Path: "/links/" + i.ID, Text: clip(i.Description, 140)})
	}
	return s
}

func folderSection(folders []Collection) Section {
	s := Section{Title: "Dossiers partagés par les familles"}
	for _, f := range folders {
		text := ""
		if f.Author != "" {
			text = "par " + f.Author
		}
		s.Items = append(s.Items, Item{Title: f.Name, Path: "/dossiers/" + f.ID, Text: text})
	}
	return s
}

func ideaFacts(i *Idea) []Fact {
	var facts []Fact
	if i.AgeRange != "" {
		facts = append(facts, Fact{"Âge", i.AgeRange})
	}
	if i.Price != "" {
		facts = append(facts, Fact{"Prix", i.Price})
	}
	if i.Location != "" {
		facts = append(facts, Fact{"Lieu", i.Location})
	}
	if !i.EventDate.IsZero() {
		facts = append(facts, Fact{"Date", frenchDate(i.EventDate)})
	}
	return facts
}

func ideaTitles(ideas []Idea, n int) string {
	var t []string
	for _, i := range ideas {
		if len(t) == n {
			break
		}
		t = append(t, i.Title)
	}
	return strings.Join(t, ", ")
}

func plural(n int) string {
	if n > 1 {
		return "s"
	}
	return ""
}

var months = [...]string{"janvier", "février", "mars", "avril", "mai", "juin", "juillet", "août", "septembre", "octobre", "novembre", "décembre"}

func frenchDate(t time.Time) string {
	t = t.In(paris)
	return fmt.Sprintf("%d %s %d", t.Day(), months[t.Month()-1], t.Year())
}

var paris = func() *time.Location {
	if l, err := time.LoadLocation("Europe/Paris"); err == nil {
		return l
	}
	return time.UTC
}()

// safeURL ne garde que les adresses http(s) absolues.
func safeURL(raw string) string {
	u, err := url.Parse(strings.TrimSpace(raw))
	if err != nil || (u.Scheme != "http" && u.Scheme != "https") || u.Host == "" {
		return ""
	}
	return u.String()
}

func absoluteImage(raw string) string { return safeURL(raw) }

func privacy() *Page {
	return &Page{
		Status: http.StatusOK, Title: "Confidentialité & données personnelles · " + siteName,
		Description: "Quelles données Tribbae conserve, pourquoi, qui les voit, combien de temps, et comment les télécharger ou supprimer votre compte.",
		Path:        "/confidentialite",
		Breadcrumb:  []Crumb{{"Accueil", "/"}, {"Confidentialité", "/confidentialite"}},
		H1:          "Confidentialité",
		Intro:       "Vos idées sont privées par défaut, rien n'est vendu ni utilisé pour de la publicité, aucun cookie n'est déposé, et vous pouvez à tout moment télécharger vos données ou supprimer votre compte.",
	}
}
