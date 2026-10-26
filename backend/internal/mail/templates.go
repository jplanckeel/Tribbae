package mail

import (
	"bytes"
	"embed"
	"errors"
	htmltemplate "html/template"
	"net/url"
	"strconv"
	"strings"
	texttemplate "text/template"
	"time"
	_ "time/tzdata" // heure de Paris même sans base de fuseaux dans l'image Docker
	"unicode"
	"unicode/utf8"
)

// Les gabarits suivent la maquette « Emails Tribbae ». Le HTML est échappé
// par html/template : les noms et noms de tribu viennent des utilisateurs.
//
//go:embed templates
var templateFS embed.FS

var funcs = map[string]any{
	// dict construit les paramètres d'un sous-gabarit (icône, bouton…)
	"dict": func(kv ...any) (map[string]any, error) {
		if len(kv)%2 != 0 {
			return nil, errors.New("dict: odd number of arguments")
		}
		m := make(map[string]any, len(kv)/2)
		for i := 0; i < len(kv); i += 2 {
			k, ok := kv[i].(string)
			if !ok {
				return nil, errors.New("dict: keys must be strings")
			}
			m[k] = kv[i+1]
		}
		return m, nil
	},
}

const (
	tmplWelcome        = "welcome"
	tmplPasswordReset  = "password_reset"
	tmplPasswordChange = "password_changed"
	tmplTribeInvite    = "tribe_invitation"
	tmplAccountDeleted = "account_deleted"
	tmplEmailVerify    = "email_verification"
	tmplCircleInvite   = "circle_invitation"
)

var (
	htmlTemplates = map[string]*htmltemplate.Template{}
	textTemplates = texttemplate.Must(texttemplate.New("").ParseFS(templateFS, "templates/text.txt"))
)

func init() {
	for _, name := range []string{tmplWelcome, tmplPasswordReset, tmplPasswordChange, tmplTribeInvite, tmplAccountDeleted, tmplEmailVerify, tmplCircleInvite} {
		htmlTemplates[name] = htmltemplate.Must(htmltemplate.New("").Funcs(funcs).
			ParseFS(templateFS, "templates/layout.html", "templates/"+name+".html"))
	}
}

// base : données communes à tous les emails.
type base struct {
	Subject  string
	SiteURL  string
	SiteHost string
	// Assets : adresse des images des emails (frontend/public/email)
	Assets string
}

func newBase(siteURL, subject string) base {
	host := siteURL
	if u, err := url.Parse(siteURL); err == nil && u.Host != "" {
		host = u.Host
	}
	return base{Subject: subject, SiteURL: siteURL, SiteHost: host, Assets: siteURL + "/email"}
}

// render produit le HTML et le texte d'un email. data doit embarquer base.
func render(name string, b base, data any) (content, error) {
	var h, t bytes.Buffer
	if err := htmlTemplates[name].ExecuteTemplate(&h, "layout", data); err != nil {
		return content{}, err
	}
	if err := textTemplates.ExecuteTemplate(&t, name, data); err != nil {
		return content{}, err
	}
	return content{Subject: b.Subject, HTML: h.String(), Text: t.String()}, nil
}

type content struct {
	Subject, HTML, Text string
}

// oneLine retire les retours à la ligne et espaces superflus d'un texte saisi
// par un utilisateur (il peut finir dans l'objet de l'email).
func oneLine(s string) string {
	return strings.Join(strings.Fields(s), " ")
}

// initial : première lettre d'un nom, en majuscule, pour les pastilles.
func initial(name string) string {
	r, _ := utf8.DecodeRuneInString(oneLine(name))
	if r == utf8.RuneError || !unicode.IsLetter(r) && !unicode.IsDigit(r) {
		return "?"
	}
	return string(unicode.ToUpper(r))
}

var paris = func() *time.Location {
	if loc, err := time.LoadLocation("Europe/Paris"); err == nil {
		return loc
	}
	return time.UTC
}()

var months = [...]string{"janvier", "février", "mars", "avril", "mai", "juin", "juillet", "août", "septembre", "octobre", "novembre", "décembre"}

// frenchDate : « 22 octobre 2026 » (heure de Paris).
func frenchDate(t time.Time) string {
	t = t.In(paris)
	day := t.Format("2")
	if day == "1" {
		day = "1er"
	}
	return day + " " + months[t.Month()-1] + " " + t.Format("2006")
}

// frenchDateTime : « 22 octobre 2026 à 16 h 42 » (heure de Paris).
func frenchDateTime(t time.Time) string {
	return frenchDate(t) + " à " + t.In(paris).Format("15 h 04")
}

// frenchDuration : « 1 heure », « 24 heures », « 7 jours », « 30 minutes ».
func frenchDuration(d time.Duration) string {
	plural := func(n int, unit string) string {
		s := strconv.Itoa(n) + " " + unit
		if n > 1 {
			s += "s"
		}
		return s
	}
	switch {
	case d >= 48*time.Hour && d%(24*time.Hour) == 0:
		return plural(int(d/(24*time.Hour)), "jour")
	case d >= time.Hour && d%time.Hour == 0:
		return plural(int(d/time.Hour), "heure")
	default:
		return plural(max(1, int(d/time.Minute)), "minute")
	}
}

// --- Bienvenue ---

type step struct {
	N           int
	Bg, Ink     string
	Title, Text string
}

var welcomeSteps = []step{
	{1, "#FBEACB", "#85560C", "Créez votre premier dossier", "Sorties du week-end, idées cadeaux, recettes…"},
	{2, "#DCEDF3", "#235C77", "Invitez votre tribu", "Toutes les idées de la famille au même endroit, à portée de chacun."},
	{3, "#E2EDD9", "#3E6630", "Gardez une idée en un geste", "Un lien, une photo, une note : tout se range dans le bon dossier."},
}

type welcomeData struct {
	base
	Name    string
	Heading string
	AppURL  string
	// Lien de confirmation de l'adresse (vide si la confirmation n'est pas
	// activée : le bouton ouvre alors Tribbae)
	VerifyURL      string
	VerifyValidity string
	Steps          []step
}

func welcomeEmail(siteURL, name, verifyURL string, validity time.Duration) (content, error) {
	name = oneLine(name)
	heading := "Bienvenue dans la tribu !"
	if name != "" {
		heading = "Bienvenue dans la tribu, " + name + " !"
	}
	d := welcomeData{
		base:    newBase(siteURL, "Bienvenue dans Tribbae"),
		Name:    name,
		Heading: heading,
		AppURL:  siteURL + "/",
		Steps:   welcomeSteps,
	}
	if verifyURL != "" {
		d.VerifyURL, d.VerifyValidity = verifyURL, frenchDuration(validity)
	}
	return render(tmplWelcome, d.base, d)
}

// --- Confirmation de l'adresse ---

type emailVerificationData struct {
	base
	Name      string
	Email     string
	VerifyURL string
	Validity  string
}

func emailVerificationEmail(siteURL, email, name, verifyURL string, validity time.Duration) (content, error) {
	d := emailVerificationData{
		base:      newBase(siteURL, "Confirmez votre adresse email Tribbae"),
		Name:      oneLine(name),
		Email:     email,
		VerifyURL: verifyURL,
		Validity:  frenchDuration(validity),
	}
	return render(tmplEmailVerify, d.base, d)
}

// --- Mot de passe oublié ---

type passwordResetData struct {
	base
	Name        string
	Email       string
	ResetURL    string
	Validity    string
	RequestedAt string
	Device      string
}

func passwordResetEmail(siteURL string, r PasswordReset) (content, error) {
	d := passwordResetData{
		base:        newBase(siteURL, "Choisissez un nouveau mot de passe Tribbae"),
		Name:        oneLine(r.Name),
		Email:       r.Email,
		ResetURL:    r.ResetURL,
		Validity:    frenchDuration(r.Validity),
		RequestedAt: frenchDateTime(r.RequestedAt),
		Device:      oneLine(r.Device),
	}
	return render(tmplPasswordReset, d.base, d)
}

// --- Mot de passe modifié ---

type passwordChangedData struct {
	base
	Name         string
	ChangedAt    string
	Device       string
	ForgotURL    string
	SupportURL   string
	SupportEmail string
}

func passwordChangedEmail(siteURL, supportEmail, name string, changedAt time.Time, device string) (content, error) {
	d := passwordChangedData{
		base:         newBase(siteURL, "Votre mot de passe Tribbae a été modifié"),
		Name:         oneLine(name),
		ChangedAt:    frenchDateTime(changedAt),
		Device:       oneLine(device),
		ForgotURL:    siteURL + "/mot-de-passe/oublie",
		SupportEmail: supportEmail,
		SupportURL:   "mailto:" + supportEmail + "?subject=" + url.PathEscape("Mot de passe de mon compte Tribbae"),
	}
	return render(tmplPasswordChange, d.base, d)
}

// --- Invitation dans une tribu ---

type avatar struct{ Bg, Ink, Text string }

var avatarColors = [][2]string{{"#FFC857", "#3A211B"}, {"#ECE1EA", "#5E4558"}, {"#DCEDF3", "#235C77"}}

type tribeInvitationData struct {
	base
	Inviter        string
	InviterInitial string
	Tribe          string
	MemberCount    int
	Avatars        []avatar
	InviteURL      string
	Validity       string
}

func tribeInvitationEmail(siteURL string, inv TribeInvitation) (content, error) {
	inviter := oneLine(inv.InviterName)
	if inviter == "" {
		inviter = "Un membre de Tribbae"
	}
	tribe := oneLine(inv.TribeName)
	if tribe == "" {
		tribe = inviter
	}
	// Trois pastilles au plus : deux membres puis « +N »
	var avatars []avatar
	for i, m := range inv.Members {
		if len(inv.Members) > 3 && i == 2 {
			avatars = append(avatars, avatar{"#5E4558", "#FDE6BD", "+" + strconv.Itoa(len(inv.Members)-2)})
			break
		}
		c := avatarColors[i%len(avatarColors)]
		avatars = append(avatars, avatar{c[0], c[1], initial(m)})
	}
	d := tribeInvitationData{
		base:           newBase(siteURL, oneLine(inviter+" vous invite à rejoindre la tribu « "+tribe+" » sur Tribbae")),
		Inviter:        inviter,
		InviterInitial: initial(inviter),
		Tribe:          tribe,
		MemberCount:    len(inv.Members),
		Avatars:        avatars,
		InviteURL:      inv.URL,
		Validity:       frenchDuration(inv.Validity),
	}
	return render(tmplTribeInvite, d.base, d)
}

// --- Invitation dans le cercle ---

type circleInvitationData struct {
	base
	Inviter        string
	InviterInitial string
	Heading        string
	Folders        []string
	Editor         bool
	InviteURL      string
	Validity       string
}

func circleInvitationEmail(siteURL string, inv CircleInvitation) (content, error) {
	inviter := oneLine(inv.InviterName)
	if inviter == "" {
		inviter = "Un membre de Tribbae"
	}
	folders := make([]string, 0, len(inv.Folders))
	for _, f := range inv.Folders {
		if f = oneLine(f); f != "" {
			folders = append(folders, f)
		}
	}
	heading := "Des dossiers vous sont ouverts"
	subject := inviter + " vous ouvre " + strconv.Itoa(len(folders)) + " dossiers sur Tribbae"
	if len(folders) == 1 {
		heading = "Le dossier « " + folders[0] + " » vous est ouvert"
		subject = inviter + " vous ouvre son dossier « " + folders[0] + " » sur Tribbae"
	}
	d := circleInvitationData{
		base:           newBase(siteURL, oneLine(subject)),
		Inviter:        inviter,
		InviterInitial: initial(inviter),
		Heading:        heading,
		Folders:        folders,
		Editor:         inv.Editor,
		InviteURL:      inv.URL,
		Validity:       frenchDuration(inv.Validity),
	}
	return render(tmplCircleInvite, d.base, d)
}

// --- Suppression du compte ---

var erased = []string{
	"Votre profil et votre adresse email",
	"Vos idées et vos dossiers",
	"Les profils de vos enfants, vos commentaires et vos « j'aime »",
	"Votre place dans votre tribu et dans les dossiers partagés",
}

type accountDeletedData struct {
	base
	Name         string
	DeletedOn    string
	Erased       []string
	SupportEmail string
	SupportURL   string
}

func accountDeletedEmail(siteURL, supportEmail, name string, deletedAt time.Time) (content, error) {
	d := accountDeletedData{
		base:         newBase(siteURL, "Votre compte Tribbae a été supprimé"),
		Name:         oneLine(name),
		DeletedOn:    frenchDate(deletedAt),
		Erased:       erased,
		SupportEmail: supportEmail,
		SupportURL:   "mailto:" + supportEmail + "?subject=" + url.PathEscape("Suppression de mon compte Tribbae"),
	}
	return render(tmplAccountDeleted, d.base, d)
}
