package ai

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"log"
	"net/http"
	"slices"
	"strings"
	"sync"
	"time"
)

// ollamaClient parle à l'API Ollama. Réglé pour un petit VPS sans GPU :
// une génération à la fois, contexte court, modèle gardé en mémoire.
type ollamaClient struct {
	baseURL   string
	model     string
	keepAlive string
	numThread int
	http      *http.Client

	capMu    sync.Mutex
	thinking *bool // le modèle a-t-il un mode « réflexion » (à couper) ?

	// extraOptions s'ajoute aux options Ollama (banc d'essai : CPU seul, etc.)
	extraOptions map[string]any
}

var errModelMissing = errors.New("ollama model not found")

const (
	ollamaNumCtx = 2048
	// Une idée en JSON (clés comprises, en français) coûte ~90 jetons ; marge
	// pour ne pas tronquer la dernière idée.
	tokensPerIdea     = 115
	ollamaTemperature = 0.4
)

func newOllamaClient(baseURL, model, keepAlive string, numThread int) *ollamaClient {
	return &ollamaClient{
		baseURL:   strings.TrimRight(baseURL, "/"),
		model:     model,
		keepAlive: keepAlive,
		numThread: numThread,
		// Pas de timeout global : la génération est bornée par le contexte de la requête
		http: &http.Client{},
	}
}

func (o *ollamaClient) post(ctx context.Context, path string, body any, out any) error {
	data, err := json.Marshal(body)
	if err != nil {
		return err
	}
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, o.baseURL+path, bytes.NewReader(data))
	if err != nil {
		return err
	}
	req.Header.Set("Content-Type", "application/json")
	resp, err := o.http.Do(req)
	if err != nil {
		return fmt.Errorf("ollama unreachable: %w", err)
	}
	defer resp.Body.Close()
	raw, err := io.ReadAll(io.LimitReader(resp.Body, 4<<20))
	if err != nil {
		return err
	}
	if resp.StatusCode == http.StatusNotFound {
		return errModelMissing
	}
	if resp.StatusCode != http.StatusOK {
		return fmt.Errorf("ollama %s: status %d: %s", path, resp.StatusCode, strings.TrimSpace(string(raw)))
	}
	if out != nil {
		return json.Unmarshal(raw, out)
	}
	return nil
}

// supportsThinking interroge (une fois) les capacités du modèle. Ollama refuse
// le paramètre `think` pour un modèle qui n'a pas de mode réflexion.
func (o *ollamaClient) supportsThinking(ctx context.Context) (bool, error) {
	o.capMu.Lock()
	defer o.capMu.Unlock()
	if o.thinking != nil {
		return *o.thinking, nil
	}
	var show struct {
		Capabilities []string `json:"capabilities"`
	}
	if err := o.post(ctx, "/api/show", map[string]string{"model": o.model}, &show); err != nil {
		return false, err
	}
	t := slices.Contains(show.Capabilities, "thinking")
	o.thinking = &t
	return t, nil
}

type chatResponse struct {
	Message struct {
		Content string `json:"content"`
	} `json:"message"`
	PromptEvalCount    int   `json:"prompt_eval_count"`
	PromptEvalDuration int64 `json:"prompt_eval_duration"`
	EvalCount          int   `json:"eval_count"`
	EvalDuration       int64 `json:"eval_duration"`
	LoadDuration       int64 `json:"load_duration"`
}

// generate produit la réponse JSON (conforme au schéma) pour n idées.
func (o *ollamaClient) generate(ctx context.Context, system, user string, n, sources int) (string, error) {
	thinking, err := o.supportsThinking(ctx)
	if err != nil {
		return "", err
	}
	options := map[string]any{
		"temperature": ollamaTemperature,
		"num_ctx":     ollamaNumCtx,
		"num_predict": n*tokensPerIdea + 80,
	}
	if o.numThread > 0 {
		options["num_thread"] = o.numThread
	}
	for k, v := range o.extraOptions {
		options[k] = v
	}
	body := map[string]any{
		"model": o.model,
		"messages": []map[string]string{
			{"role": "system", "content": system},
			{"role": "user", "content": user},
		},
		"stream":     false,
		"format":     ideaSchema(n, sources),
		"keep_alive": o.keepAlive,
		"options":    options,
	}
	if thinking {
		// La réflexion multiplierait le nombre de jetons générés : trop lent sur CPU
		body["think"] = false
	}
	var resp chatResponse
	if err := o.post(ctx, "/api/chat", body, &resp); err != nil {
		return "", err
	}
	log.Printf("ai: ollama %s: prompt %d tok (%s), output %d tok (%s, %.1f tok/s), load %s",
		o.model, resp.PromptEvalCount, dur(resp.PromptEvalDuration),
		resp.EvalCount, dur(resp.EvalDuration), rate(resp.EvalCount, resp.EvalDuration), dur(resp.LoadDuration))
	return resp.Message.Content, nil
}

// EnsureReady télécharge le modèle s'il manque puis le charge en mémoire, pour
// que la première demande d'un utilisateur ne paie ni le téléchargement ni le
// chargement. Retourne false si Ollama n'a pas répondu.
func (o *ollamaClient) EnsureReady(ctx context.Context) bool {
	start := time.Now()
	_, err := o.supportsThinking(ctx)
	if errors.Is(err, errModelMissing) {
		log.Printf("ai: pulling ollama model %s (first start, may take a few minutes)…", o.model)
		if err := o.post(ctx, "/api/pull", map[string]any{"model": o.model, "stream": false}, nil); err != nil {
			log.Printf("ai: pull %s failed: %v", o.model, err)
			return false
		}
		_, err = o.supportsThinking(ctx)
	}
	if err != nil {
		log.Printf("ai: ollama not ready yet: %v", err)
		return false
	}
	// Requête vide : charge le modèle et le garde en mémoire (keep_alive)
	if err := o.post(ctx, "/api/generate", map[string]any{"model": o.model, "keep_alive": o.keepAlive}, nil); err != nil {
		log.Printf("ai: warm-up of %s failed: %v", o.model, err)
		return false
	}
	log.Printf("ai: ollama model %s ready in %s", o.model, time.Since(start).Round(time.Millisecond))
	return true
}

func dur(ns int64) time.Duration { return time.Duration(ns).Round(10 * time.Millisecond) }

func rate(tokens int, ns int64) float64 {
	if ns <= 0 {
		return 0
	}
	return float64(tokens) / (float64(ns) / 1e9)
}
