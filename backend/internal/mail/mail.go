// Package mail envoie les emails transactionnels de Tribbae (bienvenue,
// suppression du compte, ajout dans une tribu…) par l'API de Brevo.
//
// Les envois partent en arrière-plan : un email qui échoue est journalisé mais
// ne fait jamais échouer l'action de l'utilisateur. Les journaux ne contiennent
// ni adresse ni nom, seulement le type d'email.
package mail

import (
	"context"
	"log"
	"strings"
	"sync"
	"time"
)

// Message est un email prêt à partir.
type Message struct {
	To      string
	ToName  string
	Subject string
	HTML    string
	Text    string
	// Tag : type d'email (statistiques Brevo et journaux)
	Tag string
}

// Sender remet un email à un service d'envoi.
type Sender interface {
	Send(ctx context.Context, m Message) error
}

// LogSender n'envoie rien : il journalise le type d'email. Utilisé sans clé
// Brevo (développement local, tests).
type LogSender struct{}

func (LogSender) Send(_ context.Context, m Message) error {
	log.Printf("mail: BREVO_API_KEY not set, %q email not sent", m.Tag)
	return nil
}

// NewSender retourne l'envoi par Brevo si une clé est fournie, sinon LogSender.
func NewSender(apiKey, fromEmail, fromName string) Sender {
	if apiKey == "" {
		log.Println("mail: BREVO_API_KEY not set, emails are only logged")
		return LogSender{}
	}
	return NewBrevo(apiKey, fromEmail, fromName)
}

const (
	TagWelcome        = "welcome"
	TagAccountDeleted = "account-deleted"
	TagTribeAdded     = "tribe-member-added"

	sendTimeout = 30 * time.Second
	// Un même compte ne reçoit qu'un email d'ajout à une tribu par jour : sinon
	// ajouter et retirer quelqu'un en boucle servirait à l'inonder d'emails.
	tribeCooldown = 24 * time.Hour
)

// Mailer compose les emails de Tribbae et les envoie en arrière-plan.
type Mailer struct {
	sender  Sender
	siteURL string

	wg sync.WaitGroup

	mu       sync.Mutex
	lastSent map[string]time.Time
	now      func() time.Time
}

func New(sender Sender, siteURL string) *Mailer {
	return &Mailer{
		sender:   sender,
		siteURL:  strings.TrimRight(siteURL, "/"),
		lastSent: map[string]time.Time{},
		now:      time.Now,
	}
}

// Welcome souhaite la bienvenue à un compte qui vient d'être créé.
func (m *Mailer) Welcome(email, name string) {
	m.send(email, name, TagWelcome, welcomeEmail(name, m.siteURL))
}

// AccountDeleted confirme l'effacement d'un compte. L'adresse n'est plus
// conservée nulle part : elle ne sert qu'à cet envoi.
func (m *Mailer) AccountDeleted(email, name string) {
	m.send(email, name, TagAccountDeleted, accountDeletedEmail(name, m.siteURL))
}

// TribeMemberAdded prévient un compte qu'il a été ajouté à une tribu.
func (m *Mailer) TribeMemberAdded(email, name, inviterName, tribeName string) {
	if !m.allow(TagTribeAdded + ":" + strings.ToLower(email)) {
		return
	}
	m.send(email, name, TagTribeAdded, tribeAddedEmail(name, inviterName, tribeName, m.siteURL))
}

// Wait attend la fin des envois en cours (arrêt du serveur), au plus jusqu'à
// l'échéance de ctx.
func (m *Mailer) Wait(ctx context.Context) {
	done := make(chan struct{})
	go func() {
		m.wg.Wait()
		close(done)
	}()
	select {
	case <-done:
	case <-ctx.Done():
	}
}

// allow limite la fréquence d'un email par destinataire.
func (m *Mailer) allow(key string) bool {
	m.mu.Lock()
	defer m.mu.Unlock()
	now := m.now()
	for k, t := range m.lastSent {
		if now.Sub(t) >= tribeCooldown {
			delete(m.lastSent, k)
		}
	}
	if _, ok := m.lastSent[key]; ok {
		return false
	}
	m.lastSent[key] = now
	return true
}

func (m *Mailer) send(email, name, tag string, c content) {
	if email == "" {
		return
	}
	msg := Message{
		To:      email,
		ToName:  name,
		Subject: c.Subject,
		HTML:    c.html(),
		Text:    c.text(),
		Tag:     tag,
	}
	m.wg.Add(1)
	go func() {
		defer m.wg.Done()
		ctx, cancel := context.WithTimeout(context.Background(), sendTimeout)
		defer cancel()
		if err := m.sender.Send(ctx, msg); err != nil {
			log.Printf("mail: %q email failed: %v", tag, err)
		}
	}()
}
