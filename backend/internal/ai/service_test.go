package ai

import (
	"context"
	"encoding/json"
	"errors"
	"net/http"
	"net/http/httptest"
	"reflect"
	"strings"
	"sync/atomic"
	"testing"
	"time"
)

func TestWantedCount(t *testing.T) {
	cases := map[string]int{
		"10 activités d'intérieur pour un jour de pluie": 8,
		"Top 4 des sorties": 4,
		"2 idées de goûter": 3,
		"Sorties nature autour de Lyon pour des 3–8 ans": defaultIdeas,
		"Cadeaux de Noël pour une ado de 13 ans":         defaultIdeas,
		"Goûters sans sucre ajouté":                      defaultIdeas,
	}
	for in, want := range cases {
		if got := wantedCount(in); got != want {
			t.Errorf("wantedCount(%q) = %d, want %d", in, got, want)
		}
	}
}

func TestBuildQueries(t *testing.T) {
	cases := map[string][]string{
		"Goûters sans sucre ajouté":  {"Goûters sans sucre ajouté", "Goûters sans sucre ajouté recette facile enfants"},
		"Cadeaux pour une ado":       {"Cadeaux pour une ado", "Cadeaux pour une ado idée"}, // « cadeau » est déjà dans « Cadeaux »
		"Sortie en famille à Lyon":   {"Sortie en famille à Lyon"},
		"Jeux pour un jour de pluie": {"Jeux pour un jour de pluie", "Jeux pour un jour de pluie en famille avec enfants"},
	}
	for in, want := range cases {
		if got := buildQueries(in); !reflect.DeepEqual(got, want) {
			t.Errorf("buildQueries(%q) = %q, want %q", in, got, want)
		}
	}
}

func TestSelectSources(t *testing.T) {
	in := []SearchResult{
		{Title: "A", URL: "https://www.site.fr/a"},
		{Title: "A bis", URL: "https://site.fr/a/"},          // doublon
		{Title: "B", URL: "https://site.fr/b"},               // 2e du même site
		{Title: "C", URL: "https://site.fr/c"},               // 3e : écarté
		{Title: "Fil", URL: "https://www.reddit.com/r/x"},    // bloqué
		{Title: "Insta", URL: "https://m.instagram.com/p/1"}, // bloqué (sous-domaine)
		{Title: "", URL: "https://autre.fr/sans-titre"},      // sans titre
		{Title: "JS", URL: "javascript:alert(1)"},            // schéma refusé
		{Title: "  Ateliers   cuisine ", URL: "https://autre.fr/d", Content: strings.Repeat("mot ", 100)},
	}
	got := selectSources(in)
	var urls []string
	for _, r := range got {
		urls = append(urls, r.URL)
	}
	want := []string{"https://www.site.fr/a", "https://site.fr/b", "https://autre.fr/d"}
	if !reflect.DeepEqual(urls, want) {
		t.Fatalf("urls = %v, want %v", urls, want)
	}
	if got[2].Title != "Ateliers cuisine" || !strings.HasSuffix(got[2].Content, "…") || len([]rune(got[2].Content)) > maxSnippetRunes+1 {
		t.Errorf("snippet mal nettoyé : %q / %q", got[2].Title, got[2].Content)
	}
}

func TestParseIdeasGroundsURLsOnSources(t *testing.T) {
	sources := []SearchResult{
		{Title: "Crêpes", URL: "https://recettes.fr/crepes", ImageURL: "https://recettes.fr/c.jpg"},
		{Title: "Gaufres", URL: "https://recettes.fr/gaufres"},
	}
	raw := `{"ideas":[
		{"source":1,"title":"  Crêpes   légères ","description":"Une pâte sans sucre.","category":"recette","tags":["#Goûter","goûter","rapide","facile"],"ageRange":"N/A","price":"inconnu"},
		{"source":9,"title":"Pique-nique","description":"Au parc.","category":"sortie","tags":[],"ageRange":"3-6 ans","price":"Gratuit"},
		{"source":2,"title":"crêpes légères","description":"doublon","category":"recette","tags":[],"ageRange":"","price":""},
		{"source":0,"title":"","description":"sans titre","category":"idee","tags":[],"ageRange":"","price":""},
		{"source":2,"title":"Cadre photo","description":"DIY","category":"décoration","tags":[],"ageRange":"","price":""}
	]}`
	ideas, err := parseIdeas(raw, sources, 5)
	if err != nil {
		t.Fatalf("parseIdeas: %v", err)
	}
	if len(ideas) != 3 {
		t.Fatalf("got %d ideas, want 3: %+v", len(ideas), ideas)
	}
	first := ideas[0]
	if first.Title != "Crêpes légères" || first.URL != sources[0].URL || first.ImageURL != sources[0].ImageURL ||
		first.Category != "LINK_CATEGORY_RECETTE" || first.AgeRange != "" || first.Price != "" ||
		!reflect.DeepEqual(first.Tags, []string{"goûter", "rapide", "facile"}) {
		t.Errorf("première idée mal normalisée : %+v", first)
	}
	if ideas[1].URL != "" || ideas[1].Category != "LINK_CATEGORY_EVENEMENT" || ideas[1].Price != "" {
		t.Errorf("source hors bornes : l'idée ne doit pas recevoir d'URL : %+v", ideas[1])
	}
	if ideas[2].Category != "LINK_CATEGORY_DECORATION" {
		t.Errorf("catégorie accentuée mal convertie : %q", ideas[2].Category)
	}
}

func TestPriceMustAppearInSource(t *testing.T) {
	src := SearchResult{Title: "Ferme de la Cour", Content: "Entrée 6 € par enfant, visite gratuite le dimanche."}
	cases := map[string]bool{"6 €": true, "6 € par enfant": true, "12 €": false, "Gratuit": true, "": false}
	for price, want := range cases {
		if got := priceInSource(price, src); got != want {
			t.Errorf("priceInSource(%q) = %v, want %v", price, got, want)
		}
	}
	if priceInSource("Gratuit", SearchResult{Content: "Tarif enfant 15 €"}) {
		t.Error("« Gratuit » absent de la source : à écarter")
	}
}

func TestParseIdeasToleratesSurroundingText(t *testing.T) {
	ideas, err := parseIdeas("Voici : {\"ideas\":[{\"source\":0,\"title\":\"Jeu {de} piste\",\"description\":\"\",\"category\":\"activite\",\"tags\":[],\"ageRange\":\"\",\"price\":\"\"}]} Bonne journée", nil, 5)
	if err != nil || len(ideas) != 1 || ideas[0].Title != "Jeu {de} piste" {
		t.Fatalf("ideas = %+v, err = %v", ideas, err)
	}
	// Réponse tronquée par la limite de jetons : les idées complètes sont gardées
	truncated := `{"ideas":[{"source":0,"title":"Cabane","description":"","category":"activite","tags":[],"ageRange":"","price":""},{"source":0,"title":"Pâte à sel","description":"Modeler puis cui`
	if ideas, err := parseIdeas(truncated, nil, 5); err != nil || len(ideas) != 1 || ideas[0].Title != "Cabane" {
		t.Errorf("réponse tronquée : ideas = %+v, err = %v", ideas, err)
	}
	if _, err := parseIdeas("pas de JSON", nil, 5); err == nil {
		t.Error("une réponse sans JSON doit échouer")
	}
}

// fakeOllama simule l'API Ollama : /api/show, /api/chat (réponse fournie).
func fakeOllama(t *testing.T, thinking bool, answer string, delay time.Duration, sawThink *atomic.Value) *httptest.Server {
	t.Helper()
	return httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		var body map[string]any
		_ = json.NewDecoder(r.Body).Decode(&body)
		switch r.URL.Path {
		case "/api/show":
			caps := []string{"completion"}
			if thinking {
				caps = append(caps, "thinking")
			}
			_ = json.NewEncoder(w).Encode(map[string]any{"capabilities": caps})
		case "/api/chat":
			if _, ok := body["format"].(map[string]any); !ok {
				t.Error("le schéma JSON doit être imposé via `format`")
			}
			think, has := body["think"]
			sawThink.Store([2]any{has, think})
			time.Sleep(delay)
			_ = json.NewEncoder(w).Encode(map[string]any{"message": map[string]string{"content": answer}, "eval_count": 120})
		default:
			http.NotFound(w, r)
		}
	}))
}

func fakeSearx(results []map[string]string) *httptest.Server {
	return httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		_ = r.ParseForm()
		if r.Form.Get("safesearch") != "1" {
			http.Error(w, "safesearch attendu", http.StatusBadRequest)
			return
		}
		_ = json.NewEncoder(w).Encode(map[string]any{"results": results})
	}))
}

func TestGenerateEndToEnd(t *testing.T) {
	searx := fakeSearx([]map[string]string{
		{"title": "10 goûters sans sucre", "url": "https://blog.example/gouters", "content": "Des idées faciles."},
		{"title": "Banana bread", "url": "https://cuisine.example/banana", "content": "Sans sucre ajouté."},
	})
	defer searx.Close()
	var think atomic.Value
	answer := `{"ideas":[{"source":2,"title":"Banana bread","description":"Moelleux et sans sucre ajouté.","category":"recette","tags":["goûter"],"ageRange":"","price":""}]}`
	ollama := fakeOllama(t, true, answer, 0, &think)
	defer ollama.Close()

	svc := NewService(Config{OllamaURL: ollama.URL, OllamaModel: "test", SearxURL: searx.URL})
	res, err := svc.Generate(context.Background(), "Goûters sans sucre ajouté", false)
	if err != nil {
		t.Fatalf("Generate: %v", err)
	}
	if len(res.Ideas) != 1 || res.Ideas[0].URL != "https://cuisine.example/banana" || res.Ideas[0].Category != "LINK_CATEGORY_RECETTE" {
		t.Errorf("ideas = %+v", res.Ideas)
	}
	if got := think.Load().([2]any); got[0] != true || got[1] != false {
		t.Errorf("think = %v, want désactivé explicitement pour un modèle à réflexion", got)
	}
}

func TestGenerateOmitsThinkForPlainModels(t *testing.T) {
	var think atomic.Value
	ollama := fakeOllama(t, false, `{"ideas":[{"source":0,"title":"Cabane","description":"","category":"activite","tags":[],"ageRange":"","price":""}]}`, 0, &think)
	defer ollama.Close()
	svc := NewService(Config{OllamaURL: ollama.URL, OllamaModel: "test"})
	if _, err := svc.Generate(context.Background(), "Activités en forêt", false); err != nil {
		t.Fatalf("Generate: %v", err)
	}
	if got := think.Load().([2]any); got[0] != false {
		t.Errorf("`think` ne doit pas être envoyé à un modèle sans réflexion (Ollama le refuse)")
	}
}

func TestGenerateBusy(t *testing.T) {
	var think atomic.Value
	ollama := fakeOllama(t, false, `{"ideas":[{"source":0,"title":"X","description":"","category":"idee","tags":[],"ageRange":"","price":""}]}`, 300*time.Millisecond, &think)
	defer ollama.Close()
	svc := NewService(Config{OllamaURL: ollama.URL, OllamaModel: "test"})

	done := make(chan error, 1)
	go func() {
		_, err := svc.Generate(context.Background(), "première demande", false)
		done <- err
	}()
	time.Sleep(100 * time.Millisecond)
	if _, err := svc.Generate(context.Background(), "seconde demande", false); !errors.Is(err, ErrBusy) {
		t.Errorf("seconde demande simultanée : err = %v, want ErrBusy", err)
	}
	if err := <-done; err != nil {
		t.Fatalf("première demande : %v", err)
	}
	// Le créneau est libéré : une nouvelle demande passe
	if _, err := svc.Generate(context.Background(), "troisième demande", false); err != nil {
		t.Errorf("après libération : %v", err)
	}
}

func TestWebSearchPreferredThenSearxFallback(t *testing.T) {
	var webCalls atomic.Int32
	failWeb := atomic.Bool{}
	web := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		webCalls.Add(1)
		if r.Header.Get("Authorization") != "Bearer cle-test" {
			http.Error(w, "unauthorized", http.StatusUnauthorized)
			return
		}
		if failWeb.Load() {
			http.Error(w, "quota", http.StatusTooManyRequests)
			return
		}
		var body map[string]any
		_ = json.NewDecoder(r.Body).Decode(&body)
		if body["max_results"] != float64(webSearchMaxResults) || body["query"] == "" {
			t.Errorf("requête web search inattendue : %v", body)
		}
		_ = json.NewEncoder(w).Encode(map[string]any{"results": []map[string]string{
			{"title": "Parc de la Tête d'Or", "url": "https://web.example/parc", "content": "Zoo gratuit."},
		}})
	}))
	defer web.Close()
	searx := fakeSearx([]map[string]string{{"title": "Ferme pédagogique", "url": "https://searx.example/ferme", "content": "6 €"}})
	defer searx.Close()

	svc := NewService(Config{SearxURL: searx.URL, WebSearchAPIKey: "cle-test", WebSearchURL: web.URL})
	res, provider, err := svc.findSources(context.Background(), "Sorties nature à Lyon")
	if err != nil || provider != "ollama-web" || len(res) != 1 || res[0].URL != "https://web.example/parc" {
		t.Fatalf("API Ollama attendue : provider=%s res=%+v err=%v", provider, res, err)
	}
	if webCalls.Load() != 1 {
		t.Errorf("une seule requête par recherche (quota) : %d appels", webCalls.Load())
	}

	failWeb.Store(true)
	res, provider, err = svc.findSources(context.Background(), "Sorties nature à Lyon")
	if err != nil || provider != "searxng" || len(res) != 1 || res[0].URL != "https://searx.example/ferme" {
		t.Fatalf("repli SearXNG attendu : provider=%s res=%+v err=%v", provider, res, err)
	}

	// Sans clé : SearXNG directement
	plain := NewService(Config{SearxURL: searx.URL})
	if _, provider, _ := plain.findSources(context.Background(), "x"); provider != "searxng" {
		t.Errorf("sans clé, provider = %s", provider)
	}
}
