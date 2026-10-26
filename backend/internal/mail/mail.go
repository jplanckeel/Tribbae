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
	TagPasswordReset  = "password-reset"
	TagPasswordChange = "password-changed"
	TagAccountDeleted = "account-deleted"
	TagTribeInvite    = "tribe-invitation"
	TagEmailVerify    = "email-verification"
	TagCircleInvite   = "circle-invitation"

	sendTimeout = 30 * time.Second
)

// Mailer compose les emails de Tribbae et les envoie en arrière-plan.
type Mailer struct {
	sender       Sender
	siteURL      string
	supportEmail string

	wg  sync.WaitGroup
	now func() time.Time
}

// New : siteURL sert aux liens et aux images, supportEmail à « Écrire au support ».
func New(sender Sender, siteURL, supportEmail string) *Mailer {
	return &Mailer{
		sender:       sender,
		siteURL:      strings.TrimRight(siteURL, "/"),
		supportEmail: supportEmail,
		now:          time.Now,
	}
}

// Welcome souhaite la bienvenue à un compte qui vient d'être créé, avec le
// lien de confirmation de son adresse (verifyURL, facultatif).
func (m *Mailer) Welcome(email, name, verifyURL string, validity time.Duration) {
	c, err := welcomeEmail(m.siteURL, name, verifyURL, validity)
	m.send(email, name, TagWelcome, c, err)
}

// EmailVerification renvoie le lien de confirmation de l'adresse.
func (m *Mailer) EmailVerification(email, name, verifyURL string, validity time.Duration) {
	c, err := emailVerificationEmail(m.siteURL, email, name, verifyURL, validity)
	m.send(email, name, TagEmailVerify, c, err)
}

// PasswordReset : lien de réinitialisation du mot de passe.
type PasswordReset struct {
	Email, Name string
	ResetURL    string
	Validity    time.Duration
	RequestedAt time.Time
	// Device : appareil ou navigateur d'où vient la demande (facultatif)
	Device string
}

// PasswordResetLink envoie le lien de réinitialisation du mot de passe.
func (m *Mailer) PasswordResetLink(email, name, resetURL string, validity time.Duration, requestedAt time.Time, device string) {
	r := PasswordReset{Email: email, Name: name, ResetURL: resetURL, Validity: validity, RequestedAt: requestedAt, Device: device}
	c, err := passwordResetEmail(m.siteURL, r)
	m.send(email, name, TagPasswordReset, c, err)
}

// PasswordChanged prévient que le mot de passe du compte vient de changer.
func (m *Mailer) PasswordChanged(email, name string, changedAt time.Time, device string) {
	c, err := passwordChangedEmail(m.siteURL, m.supportEmail, name, changedAt, device)
	m.send(email, name, TagPasswordChange, c, err)
}

// AccountDeleted confirme l'effacement d'un compte. L'adresse n'est plus
// conservée nulle part : elle ne sert qu'à cet envoi.
func (m *Mailer) AccountDeleted(email, name string) {
	c, err := accountDeletedEmail(m.siteURL, m.supportEmail, name, m.now())
	m.send(email, name, TagAccountDeleted, c, err)
}

// TribeInvitation : invitation à rejoindre une tribu. La fréquence des envois
// est limitée par le service tribu (une invitation remplace la précédente).
type TribeInvitation struct {
	Email       string
	InviterName string
	TribeName   string
	// Members : noms des membres actuels (pastilles et nombre)
	Members []string
	// URL : lien « Rejoindre la tribu », jeton compris
	URL      string
	Validity time.Duration
}

// TribeInvitation envoie l'invitation à rejoindre une tribu.
func (m *Mailer) TribeInvitation(inv TribeInvitation) {
	c, err := tribeInvitationEmail(m.siteURL, inv)
	m.send(inv.Email, "", TagTribeInvite, c, err)
}

// CircleInvitation : invitation à des dossiers choisis (le cercle). La
// fréquence des envois est limitée par le service des dossiers.
type CircleInvitation struct {
	Email       string
	InviterName string
	// Folders : noms des dossiers ouverts
	Folders []string
	// Editor : la personne pourra ajouter des idées
	Editor bool
	// URL : lien « Voir les dossiers », jeton compris
	URL      string
	Validity time.Duration
}

// CircleInvitation envoie l'invitation à des dossiers.
func (m *Mailer) CircleInvitation(inv CircleInvitation) {
	c, err := circleInvitationEmail(m.siteURL, inv)
	m.send(inv.Email, "", TagCircleInvite, c, err)
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

func (m *Mailer) send(email, name, tag string, c content, err error) {
	if err != nil {
		log.Printf("mail: %q email not rendered: %v", tag, err)
		return
	}
	if email == "" {
		return
	}
	msg := Message{
		To:      email,
		ToName:  oneLine(name),
		Subject: c.Subject,
		HTML:    c.HTML,
		Text:    c.Text,
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
