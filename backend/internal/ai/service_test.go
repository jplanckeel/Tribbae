package ai

import (
	"context"
	"encoding/json"
	"errors"
	"net/http"
	"net/http/httptest"
	"reflect"
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

func TestParseIdeasNormalizes(t *testing.T) {
	raw := `{"ideas":[
		{"title":"  Crêpes   légères ","description":"Une pâte sans sucre.","category":"recette","tags":["#Goûter","goûter","rapide","facile"],"ageRange":"N/A"},
		{"title":"Pique-nique","description":"Au parc.","category":"sortie","tags":[],"ageRange":"3-6 ans"},
		{"title":"crêpes légères","description":"doublon","category":"recette","tags":[],"ageRange":""},
		{"title":"","description":"sans titre","category":"idee","tags":[],"ageRange":""},
		{"title":"Cadre photo","description":"DIY","category":"décoration","tags":[],"ageRange":""}
	]}`
	ideas, err := parseIdeas(raw, 5)
	if err != nil {
		t.Fatalf("parseIdeas: %v", err)
	}
	if len(ideas) != 3 {
		t.Fatalf("got %d ideas, want 3: %+v", len(ideas), ideas)
	}
	first := ideas[0]
	if first.Title != "Crêpes légères" || first.Category != "LINK_CATEGORY_RECETTE" || first.AgeRange != "" ||
		!reflect.DeepEqual(first.Tags, []string{"goûter", "rapide", "facile"}) {
		t.Errorf("première idée mal normalisée : %+v", first)
	}
	if ideas[1].Category != "LINK_CATEGORY_EVENEMENT" || ideas[1].AgeRange != "3-6 ans" {
		t.Errorf("deuxième idée : %+v", ideas[1])
	}
	if ideas[2].Category != "LINK_CATEGORY_DECORATION" {
		t.Errorf("catégorie accentuée mal convertie : %q", ideas[2].Category)
	}
}

func TestParseIdeasToleratesSurroundingText(t *testing.T) {
	ideas, err := parseIdeas("Voici : {\"ideas\":[{\"source\":0,\"title\":\"Jeu {de} piste\",\"description\":\"\",\"category\":\"activite\",\"tags\":[],\"ageRange\":\"\",\"price\":\"\"}]} Bonne journée", 5)
	if err != nil || len(ideas) != 1 || ideas[0].Title != "Jeu {de} piste" {
		t.Fatalf("ideas = %+v, err = %v", ideas, err)
	}
	// Réponse tronquée par la limite de jetons : les idées complètes sont gardées
	truncated := `{"ideas":[{"title":"Cabane","description":"","category":"activite","tags":[],"ageRange":""},{"title":"Pâte à sel","description":"Modeler puis cui`
	if ideas, err := parseIdeas(truncated, 5); err != nil || len(ideas) != 1 || ideas[0].Title != "Cabane" {
		t.Errorf("réponse tronquée : ideas = %+v, err = %v", ideas, err)
	}
	if _, err := parseIdeas("pas de JSON", 5); err == nil {
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

func TestGenerateEndToEnd(t *testing.T) {
	var think atomic.Value
	answer := `{"ideas":[{"title":"Banana bread","description":"Moelleux et sans sucre ajouté.","category":"recette","tags":["goûter"],"ageRange":""}]}`
	ollama := fakeOllama(t, true, answer, 0, &think)
	defer ollama.Close()

	svc := NewService(Config{OllamaURL: ollama.URL, OllamaModel: "test"})
	res, err := svc.Generate(context.Background(), "Goûters sans sucre ajouté")
	if err != nil {
		t.Fatalf("Generate: %v", err)
	}
	if len(res.Ideas) != 1 || res.Ideas[0].Title != "Banana bread" || res.Ideas[0].Category != "LINK_CATEGORY_RECETTE" {
		t.Errorf("ideas = %+v", res.Ideas)
	}
	if got := think.Load().([2]any); got[0] != true || got[1] != false {
		t.Errorf("think = %v, want désactivé explicitement pour un modèle à réflexion", got)
	}
}

func TestGenerateOmitsThinkForPlainModels(t *testing.T) {
	var think atomic.Value
	ollama := fakeOllama(t, false, `{"ideas":[{"title":"Cabane","description":"","category":"activite","tags":[],"ageRange":""}]}`, 0, &think)
	defer ollama.Close()
	svc := NewService(Config{OllamaURL: ollama.URL, OllamaModel: "test"})
	if _, err := svc.Generate(context.Background(), "Activités en forêt"); err != nil {
		t.Fatalf("Generate: %v", err)
	}
	if got := think.Load().([2]any); got[0] != false {
		t.Errorf("`think` ne doit pas être envoyé à un modèle sans réflexion (Ollama le refuse)")
	}
}

func TestGenerateBusy(t *testing.T) {
	var think atomic.Value
	ollama := fakeOllama(t, false, `{"ideas":[{"title":"X","description":"","category":"idee","tags":[],"ageRange":""}]}`, 300*time.Millisecond, &think)
	defer ollama.Close()
	svc := NewService(Config{OllamaURL: ollama.URL, OllamaModel: "test"})

	done := make(chan error, 1)
	go func() {
		_, err := svc.Generate(context.Background(), "première demande")
		done <- err
	}()
	time.Sleep(100 * time.Millisecond)
	if _, err := svc.Generate(context.Background(), "seconde demande"); !errors.Is(err, ErrBusy) {
		t.Errorf("seconde demande simultanée : err = %v, want ErrBusy", err)
	}
	if err := <-done; err != nil {
		t.Fatalf("première demande : %v", err)
	}
	// Le créneau est libéré : une nouvelle demande passe
	if _, err := svc.Generate(context.Background(), "troisième demande"); err != nil {
		t.Errorf("après libération : %v", err)
	}
}
