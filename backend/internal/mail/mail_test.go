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
	m := New(rec, "https://tribbae.example/", "aide@tribbae.example")
	m.TribeInvitation(TribeInvitation{Email: "bob@example.com", InviterName: "<b>Alice</b>\nBcc: x", TribeName: `Les "Martin"`, Members: []string{"Alice"},
		URL: "https://tribbae.example/invitation#tok", Validity: 7 * 24 * time.Hour})
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
	if !strings.Contains(msg.HTML, `href="https://tribbae.example/invitation#tok"`) || !strings.Contains(msg.Text, "https://tribbae.example/invitation#tok") {
		t.Error("missing invitation link")
	}
	if msg.Tag != TagTribeInvite || msg.To != "bob@example.com" {
		t.Errorf("unexpected message %+v", msg)
	}
}

// Chaque email suit la maquette : contenus clés en HTML et en texte, images
// servies par le site, liens du pied de page.
func TestEmailsFollowTheMockups(t *testing.T) {
	rec := &recorder{}
	m := New(rec, "https://tribbae.example", "aide@tribbae.example")
	m.now = func() time.Time { return time.Date(2026, 10, 22, 14, 42, 0, 0, time.UTC) }
	m.Welcome("alice@example.com", "Alice", "https://tribbae.example/verifier-email#v1", 24*time.Hour)
	m.EmailVerification("alice@example.com", "Alice", "https://tribbae.example/verifier-email#v2", 24*time.Hour)
	m.PasswordResetLink("alice@example.com", "Alice", "https://tribbae.example/mot-de-passe/nouveau#abc",
		time.Hour, time.Date(2026, 10, 1, 7, 5, 0, 0, time.UTC), "Firefox sur Linux")
	m.PasswordChanged("alice@example.com", "Alice", time.Date(2026, 10, 1, 7, 9, 0, 0, time.UTC), "l'application Android")
	m.TribeInvitation(TribeInvitation{Email: "bob@example.com", InviterName: "Alice", TribeName: "Les Martin", Members: []string{"Alice", "Bruno", "Chloé", "Denis"},
		URL: "https://tribbae.example/invitation#tok", Validity: 7 * 24 * time.Hour})
	m.AccountDeleted("alice@example.com", "")
	m.CircleInvitation(CircleInvitation{Email: "mamie@example.com", InviterName: "Alice", Folders: []string{"Noël", "Sorties <été>"}, Editor: true,
		URL: "https://tribbae.example/invitation-cercle#tok", Validity: 7 * 24 * time.Hour})
	m.TribeMemberRemoved(TribeMemberRemoved{Email: "carol@example.com", Name: "Carol", TribeName: "Les Martin", RemovedByName: "Alice"})
	m.TribeNewOwner(TribeNewOwner{Email: "bob@example.com", Name: "Bob", TribeName: "Les Martin", FormerOwnerName: "Alice"})
	m.CollaboratorAdded(CollaboratorAdded{Email: "mamie@example.com", Name: "Mamie", OwnerName: "Alice", FolderID: "f1", FolderName: "Noël <2026>", Editor: true})
	wait(m)

	byTag := map[string]Message{}
	for _, msg := range rec.sent() {
		byTag[msg.Tag] = msg
	}
	cases := []struct {
		tag     string
		subject string
		html    []string
		text    []string
	}{
		{TagWelcome, "Bienvenue dans Tribbae",
			[]string{"Bienvenue dans la tribu, Alice !", "Pour bien démarrer", "Invitez votre tribu", "Confirmer mon adresse", `href="https://tribbae.example/verifier-email#v1"`, "Le lien est valable 24 heures", "https://tribbae.example/email/welcome.png"},
			[]string{"Bienvenue dans la tribu, Alice !", "Confirmer mon adresse : https://tribbae.example/verifier-email#v1", "3. Gardez une idée en un geste"}},
		{TagEmailVerify, "Confirmez votre adresse email Tribbae",
			[]string{"Confirmez votre adresse email", "Bonjour Alice, cliquez", "alice@example.com", `href="https://tribbae.example/verifier-email#v2"`, "<strong>24 heures</strong>"},
			[]string{"https://tribbae.example/verifier-email#v2", "Ce lien est valable 24 heures"}},
		{TagPasswordReset, "Choisissez un nouveau mot de passe Tribbae",
			[]string{"Bonjour Alice, une demande", "alice@example.com", "<strong>1 heure</strong>", "1er octobre 2026 à 09 h 05", "Firefox sur Linux", "https://tribbae.example/mot-de-passe/nouveau#abc"},
			[]string{"https://tribbae.example/mot-de-passe/nouveau#abc", "Ce lien expire dans 1 heure", "1er octobre 2026 à 09 h 05 depuis Firefox sur Linux"}},
		{TagPasswordChange, "Votre mot de passe Tribbae a été modifié",
			[]string{"Votre mot de passe a été modifié", "1er octobre 2026 à 09 h 09 depuis l&#39;application Android", `href="https://tribbae.example/mot-de-passe/oublie"`, "mailto:aide@tribbae.example"},
			[]string{"depuis l'application Android", "https://tribbae.example/mot-de-passe/oublie", "aide@tribbae.example"}},
		{TagTribeInvite, "Alice vous invite à rejoindre la tribu « Les Martin » sur Tribbae",
			[]string{"vous invite à rejoindre sa tribu", "Rejoignez la tribu « Les Martin »", "4 membres", "&#43;2<", ">A<", "Rejoindre la tribu", "Invitation valable 7 jours"},
			[]string{"Alice vous invite à rejoindre sa tribu (4 membres)", "Rejoindre la tribu : https://tribbae.example/invitation#tok"}},
		{TagCircleInvite, "Alice vous ouvre 2 dossiers sur Tribbae",
			[]string{"vous fait une place dans son cercle", "Des dossiers vous sont ouverts", ">Noël<", "Sorties &lt;été&gt;", "en ajouter", "Voir les dossiers", `href="https://tribbae.example/invitation-cercle#tok"`, "Invitation valable 7 jours"},
			[]string{"- Noël", "- Sorties <été>", "Voir les dossiers : https://tribbae.example/invitation-cercle#tok", "ces dossiers"}},
		{TagTribeRemoved, "Vous ne faites plus partie de la tribu « Les Martin »",
			[]string{"Bonjour Carol, <strong", ">Alice</strong> vous a retiré de la tribu « Les Martin » le 22 octobre 2026", "Ce qui change", "Vos idées et vos dossiers restent dans votre compte", `href="https://tribbae.example/"`},
			[]string{"Alice vous a retiré de la tribu « Les Martin » le 22 octobre 2026", "- Les vôtres ne lui sont plus partagés", "Ouvrir Tribbae : https://tribbae.example/"}},
		{TagTribeNewOwner, "Vous êtes désormais responsable de la tribu « Les Martin »",
			[]string{"Vous êtes responsable de la tribu « Les Martin »", ">Alice</strong>, qui avait créé la tribu, l'a quittée", "retirer des membres", `href="https://tribbae.example/tribu"`},
			[]string{"Bonjour Bob, Alice, qui avait créé la tribu, l'a quittée", "Voir ma tribu : https://tribbae.example/tribu"}},
		{TagCollabAdded, "Alice vous ouvre son dossier « Noël <2026> » sur Tribbae",
			[]string{"vous a ajouté à un dossier partagé", "Le dossier « Noël &lt;2026&gt; » vous est ouvert", "Vous pouvez voir et ajouter des idées", "Voir le dossier", `href="https://tribbae.example/dossiers/f1"`},
			[]string{"Alice vous a ajouté à son dossier « Noël <2026> » sur Tribbae. Vous pouvez y voir les idées et en ajouter.", "Voir le dossier : https://tribbae.example/dossiers/f1"}},
		{TagAccountDeleted, "Votre compte Tribbae a été supprimé",
			[]string{"Bonjour, comme vous l'avez demandé le 22 octobre 2026", "Ce qui a été effacé", "Votre profil et votre adresse email", `href="mailto:aide@tribbae.example?subject=Suppression%20de%20mon%20compte%20Tribbae"`},
			[]string{"le 22 octobre 2026", "- Vos idées et vos dossiers", "aide@tribbae.example"}},
	}
	for _, c := range cases {
		msg, ok := byTag[c.tag]
		if !ok {
			t.Errorf("%s: not sent", c.tag)
			continue
		}
		if msg.Subject != c.subject {
			t.Errorf("%s: subject = %q, want %q", c.tag, msg.Subject, c.subject)
		}
		for _, want := range append(c.html, "https://tribbae.example/email/logo.png", `href="https://tribbae.example/confidentialite"`, "tribbae.example</a>") {
			if !strings.Contains(msg.HTML, want) {
				t.Errorf("%s: HTML lacks %q", c.tag, want)
			}
		}
		for _, want := range c.text {
			if !strings.Contains(msg.Text, want) {
				t.Errorf("%s: text lacks %q", c.tag, want)
			}
		}
		if strings.Contains(msg.HTML, "<no value>") || strings.Contains(msg.Text, "<no value>") {
			t.Errorf("%s: missing template value", c.tag)
		}
	}
}

// Sans nom ni lien de confirmation (confirmation désactivée) : titre neutre
// et bouton « Ouvrir Tribbae ».
func TestWelcomeWithoutNameNorLink(t *testing.T) {
	c, err := welcomeEmail("https://tribbae.example", "  ", "", 0)
	if err != nil {
		t.Fatal(err)
	}
	if !strings.Contains(c.Text, "Bienvenue dans la tribu !") || !strings.Contains(c.Text, "Ouvrir Tribbae : https://tribbae.example/") {
		t.Errorf("welcome without a name nor link:\n%s", c.Text)
	}
	if strings.Contains(c.HTML, "Confirmer mon adresse") {
		t.Error("welcome without a link must not ask to confirm the address")
	}
}

func TestFrenchFormats(t *testing.T) {
	for d, want := range map[time.Duration]string{time.Hour: "1 heure", 24 * time.Hour: "24 heures", 7 * 24 * time.Hour: "7 jours", 30 * time.Minute: "30 minutes"} {
		if got := frenchDuration(d); got != want {
			t.Errorf("frenchDuration(%v) = %q, want %q", d, got, want)
		}
	}
	// Heure de Paris, été comme hiver
	if got := frenchDateTime(time.Date(2026, 1, 15, 23, 30, 0, 0, time.UTC)); got != "16 janvier 2026 à 00 h 30" {
		t.Errorf("frenchDateTime = %q", got)
	}
	for in, want := range map[string]string{"alice": "A", " élodie": "É", "": "?", "😀": "?"} {
		if got := initial(in); got != want {
			t.Errorf("initial(%q) = %q, want %q", in, got, want)
		}
	}
}

func TestDevice(t *testing.T) {
	for ua, want := range map[string]string{
		"Mozilla/5.0 (X11; Linux x86_64; rv:140.0) Gecko/20100101 Firefox/140.0":                                                                  "Firefox sur Linux",
		"Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Mobile/15E148 Safari/604.1": "Safari sur iPhone",
		"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0 Safari/537.36 Edg/140.0":                   "Edge sur Windows",
		"Dalvik/2.1.0 (Linux; U; Android 15; Pixel 8 Build/AP3A)":                                                                                 "l'application Android",
		"curl/8.0": "",
		"":         "",
	} {
		if got := Device(ua); got != want {
			t.Errorf("Device(%q) = %q, want %q", ua, got, want)
		}
	}
}

// Un seul dossier : il est nommé dans l'objet et le titre.
func TestCircleInvitationSingleFolder(t *testing.T) {
	c, err := circleInvitationEmail("https://tribbae.example", CircleInvitation{InviterName: "Alice", Folders: []string{"Noël"}, URL: "https://tribbae.example/invitation-cercle#t", Validity: 7 * 24 * time.Hour})
	if err != nil {
		t.Fatal(err)
	}
	if c.Subject != "Alice vous ouvre son dossier « Noël » sur Tribbae" || !strings.Contains(c.Text, "Le dossier « Noël » vous est ouvert") || !strings.Contains(c.Text, "ce dossier.") {
		t.Errorf("subject %q\n%s", c.Subject, c.Text)
	}
}

// Le nouveau responsable d'une tribu dont le créateur a supprimé son compte :
// l'email ne nomme personne.
func TestTribeNewOwnerAfterDeletionNamesNoOne(t *testing.T) {
	rec := &recorder{}
	m := New(rec, "https://tribbae.example", "aide@tribbae.example")
	m.TribeNewOwner(TribeNewOwner{Email: "bob@example.com", Name: "Bob", TribeName: "Les Martin"})
	wait(m)
	msgs := rec.sent()
	if len(msgs) != 1 {
		t.Fatalf("sent %d emails, want 1", len(msgs))
	}
	for _, body := range []string{msgs[0].HTML, msgs[0].Text} {
		if !strings.Contains(body, "Bonjour Bob, la personne qui avait créé la tribu l") {
			t.Errorf("neutral wording expected:\n%s", body)
		}
	}
}
