//go:build aibench

// Banc d'essai des petits modèles sur des demandes réelles, en conditions de
// petit VPS (CPU seul, 2 threads). Nécessite un Ollama local avec les modèles :
//
//	AI_BENCH_MODELS=qwen3:1.7b,qwen3.5:2b go test -tags aibench -run TestBench -v -timeout 60m ./internal/ai
package ai

import (
	"context"
	"encoding/json"
	"fmt"
	"os"
	"strings"
	"testing"
	"time"
)

var benchCases = []struct {
	prompt  string
	sources []SearchResult
}{
	{"Goûters sans sucre ajouté", []SearchResult{
		{Title: "15 recettes de goûters sans sucre ajouté pour les enfants", URL: "https://www.mamanpourlavie.fr/gouters-sans-sucre", Content: "Muffins à la banane, compote maison, energy balls aux dattes : des goûters sucrés naturellement avec les fruits."},
		{Title: "Banana bread sans sucre : la recette facile", URL: "https://www.marmiton.org/recettes/banana-bread-sans-sucre.aspx", Content: "Un cake moelleux sucré uniquement avec des bananes bien mûres. Préparation 15 min, cuisson 45 min."},
		{Title: "Energy balls dattes et cacao", URL: "https://www.cuisineaz.com/recettes/energy-balls-dattes.aspx", Content: "Mixez des dattes, des flocons d'avoine et du cacao, roulez en boules : prêt en 10 minutes, sans cuisson."},
		{Title: "Compote pomme-poire sans sucre", URL: "https://www.750g.com/compote-pomme-poire.htm", Content: "Pommes et poires cuites doucement avec un bâton de cannelle, idéal dès 6 mois."},
		{Title: "Pancakes à la banane (2 ingrédients)", URL: "https://www.journaldesfemmes.fr/pancakes-banane", Content: "Une banane écrasée et deux œufs : des pancakes sans farine ni sucre pour le goûter."},
		{Title: "Goûter : les erreurs à éviter selon une diététicienne", URL: "https://www.parents.fr/gouter-dieteticienne", Content: "Privilégier fruit, produit laitier et céréales ; limiter les biscuits industriels."},
	}},
	{"Sorties nature autour de Lyon pour des 3–8 ans", []SearchResult{
		{Title: "Parc de la Tête d'Or : zoo gratuit et jardin botanique", URL: "https://www.lyon-france.com/parc-tete-or", Content: "Zoo gratuit, serres, petit train et poneys : le grand parc lyonnais idéal avec de jeunes enfants."},
		{Title: "Grand Parc Miribel Jonage : baignade et balades", URL: "https://www.grand-parc.fr", Content: "Plages surveillées en été, sentiers, aires de jeux et L'Iloz' pour découvrir la nature en famille, à 20 min de Lyon."},
		{Title: "Les Monts d'Or : randonnées faciles avec enfants", URL: "https://www.montsdor.com/randonnees-famille", Content: "Boucles de 3 à 6 km au départ de Saint-Cyr, panoramas sur Lyon et fermes pédagogiques."},
		{Title: "Ferme de la Cour à Saint-Romain : visite pédagogique", URL: "https://www.fermedelacour.fr", Content: "Nourrir les animaux, traite des chèvres le samedi. Entrée 6 € par enfant."},
		{Title: "Parc des oiseaux de Villars-les-Dombes", URL: "https://www.parcdesoiseaux.com", Content: "Plus de 3000 oiseaux, spectacle d'oiseaux en vol, à 40 min de Lyon. Tarif enfant 15 €."},
		{Title: "Sorties nature : 10 idées pour les vacances", URL: "https://www.lyonenfant.fr/sorties-nature", Content: "Balades, parcs, fermes : notre sélection autour de Lyon pour les 3-10 ans."},
	}},
	{"Cadeaux de Noël pour une ado de 13 ans", []SearchResult{
		{Title: "Appareil photo instantané Instax Mini 12", URL: "https://www.fnac.com/instax-mini-12", Content: "Photos imprimées instantanément, cinq coloris pastel. 79,99 €."},
		{Title: "Coffret de bijoux à créer soi-même", URL: "https://www.natureetdecouvertes.com/coffret-bijoux", Content: "Perles, fermoirs et pinces pour créer bracelets et colliers. Dès 10 ans, 29,95 €."},
		{Title: "Casque audio sans fil JBL Tune 520BT", URL: "https://www.boulanger.com/jbl-tune-520bt", Content: "Autonomie 57 h, pliable, léger. 49,99 €."},
		{Title: "Les meilleurs romans pour ados de 13 ans", URL: "https://www.babelio.com/romans-ados", Content: "La Passe-Miroir, Phobos, Nos étoiles contraires : notre sélection de romans pour les 12-15 ans."},
		{Title: "Escape game à la maison : coffret Noël", URL: "https://www.escape-kit.fr/noel", Content: "Un jeu d'énigmes à imprimer pour toute la famille, 2 à 8 joueurs, 60 minutes."},
	}},
	{"10 activités d'intérieur pour un jour de pluie", []SearchResult{
		{Title: "30 activités d'intérieur pour occuper les enfants", URL: "https://www.momes.net/activites-interieur", Content: "Cabane de draps, chasse au trésor, pâte à sel, théâtre d'ombres, bowling de bouteilles…"},
		{Title: "Pâte à sel : la recette et des idées de modelage", URL: "https://www.teteamodeler.com/pate-a-sel", Content: "Farine, sel, eau : modeler, cuire au four puis peindre ses créations."},
		{Title: "Fabriquer un théâtre d'ombres en carton", URL: "https://www.hugolescargot.com/theatre-ombres", Content: "Une boîte, du papier calque et une lampe de poche pour inventer des histoires."},
		{Title: "Yoga pour enfants : 10 postures en s'amusant", URL: "https://www.magicmaman.com/yoga-enfants", Content: "Le chat, l'arbre, le papillon : des postures ludiques dès 3 ans."},
		{Title: "Jeux de société coopératifs pour les 4-8 ans", URL: "https://www.jeuxdenim.be/cooperatifs", Content: "Le Verger, Little Cooperation, Rhino Hero : jouer ensemble sans perdant."},
	}},
}

func TestBench(t *testing.T) {
	models := strings.Split(os.Getenv("AI_BENCH_MODELS"), ",")
	if models[0] == "" {
		t.Skip("AI_BENCH_MODELS non défini")
	}
	for _, model := range models {
		o := newOllamaClient("http://localhost:11434", model, "5m", 2)
		o.extraOptions = map[string]any{"num_gpu": 0} // CPU seul, comme le VPS
		// Préchargement (non chronométré)
		o.EnsureReady(context.Background())
		for _, c := range benchCases {
			n := wantedCount(c.prompt)
			start := time.Now()
			raw, err := o.generate(context.Background(), systemPrompt(n), userPrompt(c.prompt, c.sources), n, len(c.sources))
			took := time.Since(start)
			if err != nil {
				t.Errorf("%s | %s | ERREUR %v", model, c.prompt, err)
				continue
			}
			ideas, perr := parseIdeas(raw, c.sources, n)
			if perr != nil || os.Getenv("AI_BENCH_RAW") != "" {
				fmt.Printf("RAW (%d car.) : %s\n", len(raw), raw)
			}
			grounded := 0
			for _, i := range ideas {
				if i.URL != "" {
					grounded++
				}
			}
			fmt.Printf("\n=== %s | %q | %s | %d/%d idées, %d reliées à une source, parse=%v\n", model, c.prompt, took.Round(100*time.Millisecond), len(ideas), n, grounded, perr)
			for _, i := range ideas {
				b, _ := json.Marshal(map[string]any{"t": i.Title, "d": i.Description, "c": strings.TrimPrefix(i.Category, "LINK_CATEGORY_"), "age": i.AgeRange, "prix": i.Price, "src": i.URL != ""})
				fmt.Println("   ", string(b))
			}
		}
	}
}
