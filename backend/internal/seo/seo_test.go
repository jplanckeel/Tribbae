package seo

import (
	"context"
	"encoding/json"
	"encoding/xml"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"regexp"
	"strings"
	"testing"
	"time"
	"unicode/utf8"
)

const testIndex = `<!doctype html>
<html lang="fr">
  <head>
    <meta charset="UTF-8" />
    <!--seo:head--><title>Défaut</title><!--/seo:head-->
    <script type="module" src="/assets/index.js"></script>
  </head>
  <body>
    <div id="root"></div>
  </body>
</html>`

type fakeSource struct {
	ideas   map[string]Idea
	folders map[string]Collection
}

func (f *fakeSource) Idea(_ context.Context, id string) (*Idea, error) {
	if i, ok := f.ideas[id]; ok {
		return &i, nil
	}
	return nil, nil
}
func (f *fakeSource) Folder(_ context.Context, id string) (*Collection, error) {
	if c, ok := f.folders[id]; ok {
		return &c, nil
	}
	return nil, nil
}
func (f *fakeSource) SharedFolder(_ context.Context, token string) (*Collection, error) {
	if token == "secret" {
		return &Collection{ID: "f9", Name: "Vacances", Author: "Alice"}, nil
	}
	return nil, nil
}
func (f *fakeSource) PublishedIdeas(_ context.Context, category string, _ int) ([]Idea, error) {
	var out []Idea
	for _, i := range f.ideas {
		if category == "" || i.Category == category {
			out = append(out, i)
		}
	}
	return out, nil
}
func (f *fakeSource) PublishedFolders(context.Context, int) ([]Collection, error) {
	var out []Collection
	for _, c := range f.folders {
		out = append(out, c)
	}
	return out, nil
}

func newTestHandler(t *testing.T) *Handler {
	t.Helper()
	dir := t.TempDir()
	if err := os.WriteFile(filepath.Join(dir, "index.html"), []byte(testIndex), 0o600); err != nil {
		t.Fatal(err)
	}
	faq := `[{"title":"Démarrage","items":[["Comment créer ma tribu ?","Touchez « Créer ma tribu »."]]}]`
	if err := os.WriteFile(filepath.Join(dir, "faq.json"), []byte(faq), 0o600); err != nil {
		t.Fatal(err)
	}
	src := &fakeSource{
		ideas: map[string]Idea{
			"r1": {
				ID: "r1", Title: "Crêpes </script><script>alert(1)</script>", Category: "LINK_CATEGORY_RECETTE",
				Description: "Une pâte inratable.", Ingredients: []string{"250 g de farine", "3 œufs"},
				ImageURL: "https://img.example/crepes.jpg", URL: "https://recettes.example/crepes", Author: "Alice",
				Created: time.Date(2026, 3, 1, 10, 0, 0, 0, time.UTC), Updated: time.Date(2026, 3, 2, 10, 0, 0, 0, time.UTC),
			},
			"e1": {
				ID: "e1", Title: "Fête de la courge", Category: "LINK_CATEGORY_EVENEMENT", Location: "Ferme des Saules",
				EventDate: time.Date(2026, 10, 25, 13, 0, 0, 0, time.UTC), Author: "Bob", ImageURL: "javascript:alert(1)",
			},
		},
		folders: map[string]Collection{
			"f1": {ID: "f1", Name: "Goûters", Author: "Alice", Ideas: []Idea{{ID: "r1", Title: "Crêpes"}}},
		},
	}
	h, err := New("https://tribbae.test/", dir, src)
	if err != nil {
		t.Fatal(err)
	}
	return h
}

func get(t *testing.T, h *Handler, target string) *httptest.ResponseRecorder {
	t.Helper()
	w := httptest.NewRecorder()
	h.ServeHTML(w, httptest.NewRequest(http.MethodGet, target, nil))
	return w
}

var ldRe = regexp.MustCompile(`(?s)<script type="application/ld\+json">(.*?)</script>`)

// jsonLD décode les blocs schema.org de la page.
func jsonLD(t *testing.T, html string) []map[string]any {
	t.Helper()
	var out []map[string]any
	for _, m := range ldRe.FindAllStringSubmatch(html, -1) {
		var v map[string]any
		if err := json.Unmarshal([]byte(m[1]), &v); err != nil {
			t.Fatalf("JSON-LD invalide : %v\n%s", err, m[1])
		}
		out = append(out, v)
	}
	return out
}

func TestIdeaPageHasOwnHeadContentAndSchema(t *testing.T) {
	h := newTestHandler(t)
	w := get(t, h, "/links/r1")
	body := w.Body.String()
	if w.Code != http.StatusOK {
		t.Fatalf("status = %d", w.Code)
	}
	for _, want := range []string{
		`<link data-seo rel="canonical" href="https://tribbae.test/links/r1" />`,
		`<meta data-seo name="description" content="Recette partagée par Alice sur Tribbae : Une pâte inratable." />`,
		`<meta property="article:section" content="Recette" />`,
		`<meta property="og:image" content="https://img.example/crepes.jpg" />`,
		`<meta property="og:type" content="article" />`,
		`<li>250 g de farine</li>`,
		`href="https://recettes.example/crepes" rel="nofollow ugc noopener"`,
		`<script type="module" src="/assets/index.js"></script>`, // le reste d'index.html est conservé
	} {
		if !strings.Contains(body, want) {
			t.Errorf("manque %s", want)
		}
	}
	if strings.Contains(body, "<title>Défaut</title>") {
		t.Error("l'en-tête par défaut doit être remplacé")
	}
	// Un titre malveillant ne doit ni fermer la balise script ni injecter de HTML
	if strings.Count(body, "<script>alert(1)") != 0 {
		t.Fatalf("injection non échappée :\n%s", body)
	}
	ld := jsonLD(t, body)
	if len(ld) != 2 || ld[0]["@type"] != "Recipe" || ld[1]["@type"] != "BreadcrumbList" {
		t.Fatalf("JSON-LD = %v", ld)
	}
	if ld[0]["name"] != "Crêpes </script><script>alert(1)</script>" {
		t.Errorf("le nom doit être intact une fois décodé : %v", ld[0]["name"])
	}
	if ing, _ := ld[0]["recipeIngredient"].([]any); len(ing) != 2 {
		t.Errorf("recipeIngredient = %v", ld[0]["recipeIngredient"])
	}
}

func TestEventSchemaAndUnsafeImageIgnored(t *testing.T) {
	h := newTestHandler(t)
	body := get(t, h, "/links/e1").Body.String()
	ld := jsonLD(t, body)
	if ld[0]["@type"] != "Event" || ld[0]["startDate"] != "2026-10-25T14:00:00+01:00" {
		t.Errorf("événement = %v", ld[0])
	}
	if strings.Contains(body, "javascript:") {
		t.Error("une image javascript: ne doit jamais être publiée")
	}
	// Sans description, une phrase est composée à partir des informations
	if !strings.Contains(body, `content="Sortie partagée par Bob sur Tribbae : Fête de la courge. Lieu : Ferme des Saules. Date : 25 octobre 2026."`) {
		t.Errorf("description générée absente :\n%s", body)
	}
}

func TestPrivateOrUnknownContentIsNotIndexed(t *testing.T) {
	h := newTestHandler(t)
	for _, path := range []string{"/links/inconnu", "/dossiers/prive", "/category/inconnue", "/nimporte-quoi"} {
		w := get(t, h, path)
		if w.Code != http.StatusNotFound || w.Header().Get("X-Robots-Tag") != "noindex" {
			t.Errorf("%s : status %d, X-Robots-Tag %q", path, w.Code, w.Header().Get("X-Robots-Tag"))
		}
		if !strings.Contains(w.Body.String(), `<div id="root">`) {
			t.Errorf("%s : l'application doit quand même démarrer (idée privée d'un membre connecté)", path)
		}
	}
	for _, path := range []string{"/mes-idees", "/tribu", "/connexion", "/share/secret", "/explorer?q=crêpes"} {
		w := get(t, h, path)
		if w.Code != http.StatusOK || !strings.Contains(w.Body.String(), `content="noindex, follow"`) {
			t.Errorf("%s : status %d, doit être noindex", path, w.Code)
		}
	}
	// L'aperçu d'un lien de partage affiche le dossier, sans adresse canonique
	share := get(t, h, "/share/secret").Body.String()
	if !strings.Contains(share, `<meta property="og:title" content="Vacances · partagé sur Tribbae" />`) || strings.Contains(share, "canonical") {
		t.Errorf("partage :\n%s", share)
	}
}

func TestListingsAndFAQ(t *testing.T) {
	h := newTestHandler(t)
	cat := get(t, h, "/category/recette").Body.String()
	if !strings.Contains(cat, `<a href="/links/r1">`) || !strings.Contains(cat, "Recettes en famille") {
		t.Errorf("catégorie :\n%s", cat)
	}
	if ld := jsonLD(t, cat); ld[0]["@type"] != "CollectionPage" {
		t.Errorf("catégorie JSON-LD = %v", ld)
	}
	folder := get(t, h, "/dossiers/f1").Body.String()
	if !strings.Contains(folder, "Goûters : 1 idée rassemblée par Alice sur Tribbae.") {
		t.Errorf("dossier :\n%s", folder)
	}
	help := get(t, h, "/aide").Body.String()
	if ld := jsonLD(t, help); ld[0]["@type"] != "FAQPage" {
		t.Errorf("FAQ JSON-LD = %v", ld)
	}
	home := get(t, h, "/").Body.String()
	if ld := jsonLD(t, home); ld[0]["@graph"] == nil {
		t.Errorf("accueil : graphe du site attendu")
	}
	if !strings.Contains(home, `"urlTemplate":"https://tribbae.test/explorer?q={search_term_string}"`) {
		t.Error("la recherche du site doit pointer vers l'Explorer")
	}
}

func TestLegacyRedirects(t *testing.T) {
	h := newTestHandler(t)
	for from, to := range map[string]string{"/login": "/connexion", "/categorie/recette": "/category/recette", "/discover": "/explorer?vue=dossiers"} {
		w := get(t, h, from)
		if w.Code != http.StatusMovedPermanently || w.Header().Get("Location") != to {
			t.Errorf("%s → %d %s, want 301 %s", from, w.Code, w.Header().Get("Location"), to)
		}
	}
}

func TestSitemapAndLLMs(t *testing.T) {
	h := newTestHandler(t)
	w := httptest.NewRecorder()
	h.Sitemap()(w, httptest.NewRequest(http.MethodGet, "/sitemap.xml", nil))
	var set struct {
		URLs []struct {
			Loc string `xml:"loc"`
		} `xml:"url"`
	}
	if err := xml.Unmarshal(w.Body.Bytes(), &set); err != nil {
		t.Fatalf("sitemap invalide : %v", err)
	}
	locs := map[string]bool{}
	for _, u := range set.URLs {
		locs[u.Loc] = true
	}
	for _, want := range []string{"https://tribbae.test/", "https://tribbae.test/category/deco", "https://tribbae.test/links/r1", "https://tribbae.test/dossiers/f1"} {
		if !locs[want] {
			t.Errorf("sitemap sans %s", want)
		}
	}
	if !strings.Contains(w.Body.String(), "<image:loc>https://img.example/crepes.jpg</image:loc>") {
		t.Error("l'image de l'idée doit figurer au plan du site")
	}

	w = httptest.NewRecorder()
	h.LLMs()(w, httptest.NewRequest(http.MethodGet, "/llms.txt", nil))
	txt := w.Body.String()
	if !strings.HasPrefix(txt, "# Tribbae\n\n> ") || !strings.Contains(txt, "(https://tribbae.test/category/livre)") ||
		!strings.Contains(txt, "- [Crêpes </script><script>alert(1)</script>](https://tribbae.test/links/r1)") {
		t.Errorf("llms.txt :\n%s", txt)
	}
}

func TestPlusDe(t *testing.T) {
	for in, want := range map[string]string{"activités": "Plus d'activités", "idées": "Plus d'idées", "recettes": "Plus de recettes", "déco": "Plus de déco"} {
		if got := plusDe(in); got != want {
			t.Errorf("plusDe(%q) = %q, want %q", in, got, want)
		}
	}
}

func TestCleanTitle(t *testing.T) {
	for in, want := range map[string]string{
		"Le voleur de citrouilles - cartonné - Alice Hemming, Nicola Slater, Nicola Slater - Achat Livre | fnac": "Le voleur de citrouilles",
		"Le voleur de citrouilles - Alice Hemming | fnac":                                                        "Le voleur de citrouilles - Alice Hemming",
		"Mini World Lyon - Une super sortie loisirs en famille à Lyon":                                           "Mini World Lyon",
		"Le parc de la Tête d’or | Ville de Lyon":                                                                "Le parc de la Tête d’or",
		"Inexpensive Rabbit Arts and Crafts for Kids to Make [Video] | Bunny arts and crafts for kids":           "Inexpensive Rabbit Arts and Crafts for Kids to…",
		"Le ciel des tout-petits – Planetarium":                                                                  "Le ciel des tout-petits – Planetarium",
		"Un titre unique beaucoup trop long pour tenir dans un résultat de recherche":                            "Un titre unique beaucoup trop long pour tenir…",
		"Livre": "Livre",
	} {
		if got := cleanTitle(in); got != want {
			t.Errorf("cleanTitle(%q) = %q, want %q", in, got, want)
		}
	}
}

func TestIdeaDescription(t *testing.T) {
	book := categoryByKey("LINK_CATEGORY_LIVRE")
	for _, c := range []struct {
		idea Idea
		want string
	}{
		{Idea{Title: "Le voleur", Description: "Un album.", AgeRange: "3–6 ans", Author: "Alice"}, "Livre partagé par Alice sur Tribbae, pour les 3–6 ans : Un album."},
		{Idea{Title: "Le voleur", Description: "Le voleur", AgeRange: "Tous âges"}, "Livre partagé sur Tribbae, pour tous les âges : Le voleur."},
		{Idea{Title: "Le voleur", AgeRange: "petits", Price: "12 €"}, "Livre partagé sur Tribbae, âge : petits : Le voleur. Prix : 12 €."},
	} {
		if got := ideaDescription(&c.idea, book); got != c.want {
			t.Errorf("ideaDescription = %q, want %q", got, c.want)
		}
	}
}

func TestHomeDescriptionIsNotClipped(t *testing.T) {
	if n := utf8.RuneCountInString(homeDescription); n > 155 {
		t.Errorf("homeDescription : %d caractères, 155 au plus", n)
	}
}

func TestArticleMetaOnlyOnIdeas(t *testing.T) {
	h := newTestHandler(t)
	if body := get(t, h, "/links/e1").Body.String(); !strings.Contains(body, `<meta property="article:section" content="Sortie" />`) {
		t.Error("article:section absent d'une idée")
	}
	if body := get(t, h, "/").Body.String(); strings.Contains(body, "article:") {
		t.Error("l'accueil n'est pas un article")
	}
}
