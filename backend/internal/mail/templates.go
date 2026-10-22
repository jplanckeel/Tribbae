package mail

import (
	"bytes"
	"html/template"
	"strings"
)

// content est le contenu d'un email, rendu en HTML (aux couleurs « Le feu de
// camp ») et en texte brut. Tout texte est échappé : les noms viennent des
// utilisateurs.
type content struct {
	Subject    string
	Title      string
	Paragraphs []string
	// Bouton d'action (facultatif)
	ActionLabel string
	ActionURL   string
	Footer      string
}

var layout = template.Must(template.New("mail").Parse(`<!doctype html>
<html lang="fr">
<head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>{{.Subject}}</title></head>
<body style="margin:0;padding:0;background:#fbf3e7;">
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="background:#fbf3e7;">
<tr><td align="center" style="padding:32px 16px;">
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="max-width:520px;background:#ffffff;border:1px solid #eadbc8;border-radius:20px;">
<tr><td style="padding:28px 28px 8px;font-family:'DM Sans',Helvetica,Arial,sans-serif;font-size:20px;font-weight:700;color:#d45a43;">Tribbae</td></tr>
<tr><td style="padding:8px 28px 0;font-family:'DM Sans',Helvetica,Arial,sans-serif;font-size:22px;font-weight:700;line-height:1.3;color:#3a211b;">{{.Title}}</td></tr>
{{range .Paragraphs}}<tr><td style="padding:14px 28px 0;font-family:'DM Sans',Helvetica,Arial,sans-serif;font-size:16px;line-height:1.55;color:#4e3530;">{{.}}</td></tr>
{{end}}{{if .ActionURL}}<tr><td style="padding:24px 28px 0;">
<a href="{{.ActionURL}}" style="display:inline-block;padding:12px 22px;border-radius:999px;background:#c8492c;color:#ffffff;font-family:'DM Sans',Helvetica,Arial,sans-serif;font-size:16px;font-weight:700;text-decoration:none;">{{.ActionLabel}}</a>
</td></tr>
{{end}}<tr><td style="padding:28px;font-family:'DM Sans',Helvetica,Arial,sans-serif;font-size:13px;line-height:1.5;color:#8a6f64;">{{.Footer}}</td></tr>
</table>
</td></tr>
</table>
</body>
</html>
`))

func (c content) html() string {
	var b bytes.Buffer
	if err := layout.Execute(&b, c); err != nil {
		// Gabarit fixe et données simples : n'arrive pas, mais le texte suffit
		return "<pre>" + template.HTMLEscapeString(c.text()) + "</pre>"
	}
	return b.String()
}

func (c content) text() string {
	var b strings.Builder
	b.WriteString(c.Title + "\n\n")
	for _, p := range c.Paragraphs {
		b.WriteString(p + "\n\n")
	}
	if c.ActionURL != "" {
		b.WriteString(c.ActionLabel + " : " + c.ActionURL + "\n\n")
	}
	b.WriteString("-- \n" + c.Footer + "\n")
	return b.String()
}

// oneLine retire les retours à la ligne d'un texte saisi par un utilisateur
// (objet de l'email).
func oneLine(s string) string {
	return strings.Join(strings.Fields(s), " ")
}

func hello(name string) string {
	if name = oneLine(name); name != "" {
		return "Bonjour " + name + ","
	}
	return "Bonjour,"
}

const footer = "Vous recevez cet email parce qu'un compte Tribbae utilise cette adresse. Tribbae, les idées de toute la famille au même endroit."

func welcomeEmail(name, siteURL string) content {
	return content{
		Subject: "Bienvenue dans Tribbae",
		Title:   "Bienvenue dans Tribbae !",
		Paragraphs: []string{
			hello(name),
			"Votre compte est prêt. Rangez-y les idées de la famille : liens, recettes, sorties, cadeaux, livres… et retrouvez-les au bon moment.",
			"Invitez vos proches dans votre tribu pour tout partager : idées, dossiers et profils des enfants.",
			"Si vous n'êtes pas à l'origine de cette inscription, ignorez cet email ou écrivez-nous en y répondant.",
		},
		ActionLabel: "Ouvrir Tribbae",
		ActionURL:   siteURL + "/",
		Footer:      footer,
	}
}

func accountDeletedEmail(name, siteURL string) content {
	return content{
		Subject: "Votre compte Tribbae a été supprimé",
		Title:   "Votre compte a été supprimé",
		Paragraphs: []string{
			hello(name),
			"Comme vous l'avez demandé, votre compte Tribbae a été supprimé, avec vos idées, vos dossiers, les profils de vos enfants, vos commentaires et vos « j'aime ». Les idées des autres membres de votre tribu leur restent.",
			"Cet effacement est définitif. Nous ne conservons pas cette adresse : c'est le dernier email que vous recevez de notre part.",
			"Vous pourrez toujours créer un nouveau compte sur " + siteURL + ".",
		},
		Footer: "Merci d'avoir utilisé Tribbae.",
	}
}

func tribeAddedEmail(name, inviterName, tribeName, siteURL string) content {
	inviter := oneLine(inviterName)
	if inviter == "" {
		inviter = "Un membre de Tribbae"
	}
	tribe := "sa tribu"
	if t := oneLine(tribeName); t != "" {
		tribe = "la tribu « " + t + " »"
	}
	return content{
		Subject: oneLine(inviter + " vous a ajouté à " + tribe + " sur Tribbae"),
		Title:   "Vous faites partie d'une tribu",
		Paragraphs: []string{
			hello(name),
			inviter + " vous a ajouté à " + tribe + ". Vous voyez dès maintenant les idées, les dossiers et les enfants qu'elle partage.",
			"Vos propres idées restent privées tant que vous n'avez pas confirmé le partage depuis l'écran « Ma tribu ». Vous pouvez aussi quitter la tribu à tout moment.",
		},
		ActionLabel: "Voir ma tribu",
		ActionURL:   siteURL + "/tribu",
		Footer:      footer,
	}
}
