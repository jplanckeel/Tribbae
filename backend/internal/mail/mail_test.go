package mail

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"strings"
	"sync"
	"testing"
	"time"
)

func TestBrevoSendsTransactionalEmail(t *testing.T) {
	var got brevoRequest
	var apiKey string
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		apiKey = r.Header.Get("api-key")
		if err := json.NewDecoder(r.Body).Decode(&got); err != nil {
			t.Errorf("decode: %v", err)
		}
		w.WriteHeader(http.StatusCreated)
		_, _ = w.Write([]byte(`{"messageId":"<1@smtp-relay.mailin.fr>"}`))
	}))
	defer srv.Close()

	b := NewBrevo("key-123", "tribbae@example.com", "Tribbae")
	b.endpoint = srv.URL
	err := b.Send(context.Background(), Message{To: "alice@example.com", ToName: "Alice", Subject: "Sujet", HTML: "<p>x</p>", Text: "x", Tag: TagWelcome})
	if err != nil {
		t.Fatal(err)
	}
	if apiKey != "key-123" {
		t.Errorf("api-key header = %q", apiKey)
	}
	if got.Sender.Email != "tribbae@example.com" || got.Sender.Name != "Tribbae" {
		t.Errorf("sender = %+v", got.Sender)
	}
	if len(got.To) != 1 || got.To[0].Email != "alice@example.com" || got.To[0].Name != "Alice" {
		t.Errorf("to = %+v", got.To)
	}
	if got.Subject != "Sujet" || got.HTMLContent != "<p>x</p>" || got.TextContent != "x" {
		t.Errorf("content = %+v", got)
	}
	if len(got.Tags) != 1 || got.Tags[0] != TagWelcome {
		t.Errorf("tags = %v", got.Tags)
	}
}

func TestBrevoReportsErrors(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusUnauthorized)
		_, _ = w.Write([]byte(`{"code":"unauthorized","message":"Key not found"}`))
	}))
	defer srv.Close()

	b := NewBrevo("bad", "tribbae@example.com", "Tribbae")
	b.endpoint = srv.URL
	err := b.Send(context.Background(), Message{To: "alice@example.com", Subject: "s", HTML: "h"})
	if err == nil || !strings.Contains(err.Error(), "401") || !strings.Contains(err.Error(), "Key not found") {
		t.Fatalf("err = %v", err)
	}
}

type recorder struct {
	mu   sync.Mutex
	msgs []Message
}

func (r *recorder) Send(_ context.Context, m Message) error {
	r.mu.Lock()
	defer r.mu.Unlock()
	r.msgs = append(r.msgs, m)
	return nil
}

func (r *recorder) sent() []Message {
	r.mu.Lock()
	defer r.mu.Unlock()
	return append([]Message(nil), r.msgs...)
}

func wait(m *Mailer) {
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	m.Wait(ctx)
}

func TestMailerEscapesUserInput(t *testing.T) {
	rec := &recorder{}
	m := New(rec, "https://tribbae.example/")
	m.TribeMemberAdded("bob@example.com", "Bob", "<b>Alice</b>\nBcc: x", `Les "Martin"`)
	wait(m)

	msgs := rec.sent()
	if len(msgs) != 1 {
		t.Fatalf("sent %d emails, want 1", len(msgs))
	}
	msg := msgs[0]
	if strings.ContainsAny(msg.Subject, "\r\n") {
		t.Errorf("subject has a line break: %q", msg.Subject)
	}
	if strings.Contains(msg.HTML, "<b>Alice</b>") || !strings.Contains(msg.HTML, "&lt;b&gt;Alice&lt;/b&gt;") {
		t.Error("inviter name is not escaped in the HTML body")
	}
	if !strings.Contains(msg.HTML, `href="https://tribbae.example/tribu"`) || !strings.Contains(msg.Text, "https://tribbae.example/tribu") {
		t.Error("missing link to the tribe screen")
	}
	if msg.Tag != TagTribeAdded || msg.To != "bob@example.com" {
		t.Errorf("unexpected message %+v", msg)
	}
}

func TestMailerLimitsTribeEmailsPerRecipient(t *testing.T) {
	rec := &recorder{}
	m := New(rec, "https://tribbae.example")
	now := time.Now()
	m.now = func() time.Time { return now }

	m.TribeMemberAdded("bob@example.com", "Bob", "Alice", "Martin")
	m.TribeMemberAdded("BOB@example.com", "Bob", "Alice", "Martin")
	m.TribeMemberAdded("carol@example.com", "Carol", "Alice", "Martin")
	wait(m)
	if n := len(rec.sent()); n != 2 {
		t.Fatalf("sent %d emails, want 2 (one per recipient)", n)
	}

	now = now.Add(tribeCooldown)
	m.TribeMemberAdded("bob@example.com", "Bob", "Alice", "Martin")
	wait(m)
	if n := len(rec.sent()); n != 3 {
		t.Fatalf("sent %d emails, want 3 after the cooldown", n)
	}
}

func TestMailerSendsEachEmail(t *testing.T) {
	rec := &recorder{}
	m := New(rec, "https://tribbae.example")
	m.Welcome("alice@example.com", "")
	m.AccountDeleted("alice@example.com", "Alice")
	m.Welcome("", "nobody")
	wait(m)

	tags := map[string]Message{}
	for _, msg := range rec.sent() {
		tags[msg.Tag] = msg
	}
	if len(rec.sent()) != 2 || tags[TagWelcome].To == "" || tags[TagAccountDeleted].To == "" {
		t.Fatalf("sent %+v", rec.sent())
	}
	if !strings.Contains(tags[TagWelcome].Text, "Bonjour,") {
		t.Error("welcome email without a name should greet with « Bonjour, »")
	}
	if !strings.Contains(tags[TagAccountDeleted].Text, "Bonjour Alice,") {
		t.Error("deletion email should greet the account by name")
	}
}
