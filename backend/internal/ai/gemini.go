package ai

import (
	"context"
	"errors"
	"strings"
	"sync"

	"google.golang.org/genai"
)

// geminiClient génère les idées des comptes premium : même prompt, mêmes
// sources et même schéma que le modèle local, en quelques secondes.
type geminiClient struct {
	apiKey string
	model  string

	once   sync.Once
	client *genai.Client
	err    error
}

func newGeminiClient(apiKey, model string) *geminiClient {
	if apiKey == "" {
		return nil
	}
	return &geminiClient{apiKey: apiKey, model: model}
}

func (g *geminiClient) get(ctx context.Context) (*genai.Client, error) {
	g.once.Do(func() {
		g.client, g.err = genai.NewClient(ctx, &genai.ClientConfig{APIKey: g.apiKey, Backend: genai.BackendGeminiAPI})
	})
	return g.client, g.err
}

func (g *geminiClient) generate(ctx context.Context, system, user string, n, sources int) (string, error) {
	client, err := g.get(ctx)
	if err != nil {
		return "", err
	}
	cfg := &genai.GenerateContentConfig{
		SystemInstruction:  genai.NewContentFromText(system, genai.RoleUser),
		ResponseMIMEType:   "application/json",
		ResponseJsonSchema: ideaSchema(n, sources),
		Temperature:        genai.Ptr[float32](0.5),
		MaxOutputTokens:    2048,
	}
	// Les modèles « flash » acceptent de couper la réflexion : réponse plus rapide
	if strings.Contains(g.model, "flash") {
		cfg.ThinkingConfig = &genai.ThinkingConfig{ThinkingBudget: genai.Ptr[int32](0)}
	}
	resp, err := client.Models.GenerateContent(ctx, g.model, genai.Text(user), cfg)
	if err != nil {
		return "", err
	}
	text := resp.Text()
	if text == "" {
		return "", errors.New("empty gemini response")
	}
	return text, nil
}
