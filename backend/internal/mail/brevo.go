package mail

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"time"
)

// brevoEndpoint : API d'envoi d'emails transactionnels de Brevo.
const brevoEndpoint = "https://api.brevo.com/v3/smtp/email"

// Brevo envoie les emails par l'API transactionnelle de Brevo
// (https://developers.brevo.com/reference/sendtransacemail).
type Brevo struct {
	apiKey    string
	fromEmail string
	fromName  string
	endpoint  string
	client    *http.Client
}

func NewBrevo(apiKey, fromEmail, fromName string) *Brevo {
	return &Brevo{
		apiKey:    apiKey,
		fromEmail: fromEmail,
		fromName:  fromName,
		endpoint:  brevoEndpoint,
		client:    &http.Client{Timeout: 15 * time.Second},
	}
}

type brevoContact struct {
	Email string `json:"email"`
	Name  string `json:"name,omitempty"`
}

type brevoRequest struct {
	Sender      brevoContact   `json:"sender"`
	To          []brevoContact `json:"to"`
	Subject     string         `json:"subject"`
	HTMLContent string         `json:"htmlContent"`
	TextContent string         `json:"textContent,omitempty"`
	Tags        []string       `json:"tags,omitempty"`
}

func (b *Brevo) Send(ctx context.Context, m Message) error {
	req := brevoRequest{
		Sender:      brevoContact{Email: b.fromEmail, Name: b.fromName},
		To:          []brevoContact{{Email: m.To, Name: m.ToName}},
		Subject:     m.Subject,
		HTMLContent: m.HTML,
		TextContent: m.Text,
	}
	if m.Tag != "" {
		req.Tags = []string{m.Tag}
	}
	body, err := json.Marshal(req)
	if err != nil {
		return err
	}
	httpReq, err := http.NewRequestWithContext(ctx, http.MethodPost, b.endpoint, bytes.NewReader(body))
	if err != nil {
		return err
	}
	httpReq.Header.Set("api-key", b.apiKey)
	httpReq.Header.Set("Content-Type", "application/json")
	httpReq.Header.Set("Accept", "application/json")
	resp, err := b.client.Do(httpReq)
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	if resp.StatusCode/100 != 2 {
		// Brevo répond {"code": "...", "message": "..."} : sans donnée personnelle
		detail, _ := io.ReadAll(io.LimitReader(resp.Body, 512))
		return fmt.Errorf("brevo: status %d: %s", resp.StatusCode, bytes.TrimSpace(detail))
	}
	_, _ = io.Copy(io.Discard, io.LimitReader(resp.Body, 4<<10))
	return nil
}
