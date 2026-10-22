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

var benchPrompts = []string{
	"Goûters sans sucre ajouté",
	"Sorties nature autour de Lyon pour des 3–8 ans",
	"Cadeaux de Noël pour une ado de 13 ans",
	"10 activités d'intérieur pour un jour de pluie",
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
		for _, prompt := range benchPrompts {
			n := wantedCount(prompt)
			start := time.Now()
			raw, err := o.generate(context.Background(), systemPrompt(n), userPrompt(prompt), n)
			took := time.Since(start)
			if err != nil {
				t.Errorf("%s | %s | ERREUR %v", model, prompt, err)
				continue
			}
			ideas, perr := parseIdeas(raw, n)
			if perr != nil || os.Getenv("AI_BENCH_RAW") != "" {
				fmt.Printf("RAW (%d car.) : %s\n", len(raw), raw)
			}
			fmt.Printf("\n=== %s | %q | %s | %d/%d idées, parse=%v\n", model, prompt, took.Round(100*time.Millisecond), len(ideas), n, perr)
			for _, i := range ideas {
				b, _ := json.Marshal(map[string]any{"t": i.Title, "d": i.Description, "c": strings.TrimPrefix(i.Category, "LINK_CATEGORY_"), "age": i.AgeRange})
				fmt.Println("   ", string(b))
			}
		}
	}
}
