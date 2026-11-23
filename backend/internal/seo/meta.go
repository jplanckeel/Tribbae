package seo

import (
	"regexp"
	"strings"
	"unicode"
	"unicode/utf8"
)

// Métadonnées des pages d'idées. Le titre et la description d'une idée viennent
// souvent de la page d'origine (« … - Achat Livre | fnac ») : le titre n'en
// garde que ce qui nomme l'idée, la description commence par ce que Tribbae en
// dit. Même logique côté web dans frontend/src/lib/seo.ts.

// maxIdeaTitleRunes : avec « · Tribbae », environ 60 caractères, ce que les
// moteurs affichent.
const maxIdeaTitleRunes = 50

// titleNoise : segments de titre propres aux marchands, sans rapport avec l'idée.
var titleNoise = map[string]bool{
	"achat livre": true, "livre": true, "cartonné": true, "broché": true,
	"poche": true, "relié": true, "ebook": true,
}

var bracketed = regexp.MustCompile(`\s*\[[^\]]*\]`)

// Publications des réseaux sociaux : leur titre est « Lou sur Instagram : "Salade
// de pâtes…" » et leur description commence par les chiffres de la publication
// (« 151K likes, 788 comments - lou le October 9, 2025: »). Seule la légende
// nomme l'idée.
var (
	socialTitle = regexp.MustCompile(`(?i)^(.+?)\s+(?:sur|on)\s+(Instagram|TikTok|Facebook|Threads|Pinterest)\s*:\s*(.*)$`)
	socialStats = regexp.MustCompile(`(?i)^[\d\s.,]+[km]?\s*(?:likes?|j[’']aime),\s*[\d\s.,]+[km]?\s*(?:comments?|commentaires?)\s*-\s*\S+\s+(?:on|le)\s+[^:]{3,40}?\s*:\s*`)
	hashtag     = regexp.MustCompile(`(?:^|\s)#[\p{L}\p{N}_]+`)
)

// socialText retire d'une légende les mots-clés (#…), les émojis et les
// guillemets qui l'entourent.
func socialText(s string) string {
	s = hashtag.ReplaceAllString(s, " ")
	s = strings.Map(func(r rune) rune {
		// Émojis et pictogrammes (les symboles courants comme « ° » ou « © » restent)
		if (r >= 0x2190 && unicode.Is(unicode.So, r)) || r == 0xFE0F || r == 0x200D || (r >= 0x1F3FB && r <= 0x1F3FF) {
			return -1
		}
		return r
	}, s)
	s = strings.Join(strings.Fields(s), " ")
	return strings.TrimSpace(strings.Trim(s, `"“”«» '`))
}

// socialCaption : la légende d'une publication de réseau social, ou « Publication
// de Lou sur Instagram » quand elle est vide ; ok faux pour un autre titre.
func socialCaption(s string) (caption string, ok bool) {
	m := socialTitle.FindStringSubmatch(strings.Join(strings.Fields(s), " "))
	if m == nil {
		return "", false
	}
	if c := socialText(m[3]); c != "" {
		return c, true
	}
	return "Publication de " + m[1] + " sur " + m[2], true
}

// cleanDescription retire d'une description les chiffres d'une publication de
// réseau social (« 151K likes, 788 comments - lou le … : »).
func cleanDescription(s string) string {
	s = strings.TrimSpace(s)
	if loc := socialStats.FindStringIndex(s); loc != nil {
		return socialText(s[loc[1]:])
	}
	return s
}

// cleanTitle retire d'un titre le nom du site (après « | »), les mentions entre
// crochets, les segments marchands et les noms répétés, puis le raccourcit en
// abandonnant d'abord les derniers segments « - ».
func cleanTitle(s string) string {
	if c, ok := socialCaption(s); ok {
		return clip(c, maxIdeaTitleRunes)
	}
	s = strings.Join(strings.Fields(s), " ")
	head, _, _ := strings.Cut(s, " | ")
	head = bracketed.ReplaceAllString(head, "")
	var parts []string
	for _, p := range strings.Split(head, " - ") {
		p = dedupeList(strings.TrimSpace(p))
		if p != "" && !titleNoise[strings.ToLower(p)] {
			parts = append(parts, p)
		}
	}
	if len(parts) == 0 {
		return clip(s, maxIdeaTitleRunes)
	}
	for len(parts) > 1 && utf8.RuneCountInString(strings.Join(parts, " - ")) > maxIdeaTitleRunes {
		parts = parts[:len(parts)-1]
	}
	return clip(strings.Join(parts, " - "), maxIdeaTitleRunes)
}

// dedupeList retire les éléments répétés d'une liste « a, b, b ».
func dedupeList(s string) string {
	seen := map[string]bool{}
	var out []string
	for _, e := range strings.Split(s, ", ") {
		if k := strings.ToLower(e); !seen[k] {
			seen[k] = true
			out = append(out, e)
		}
	}
	return strings.Join(out, ", ")
}

func ideaTitle(i *Idea) string { return cleanTitle(i.Title) + " · " + siteName }

// ideaName : le nom affiché d'une idée (titre de la page, données structurées) :
// son titre, ou la légende d'une publication de réseau social.
func ideaName(i *Idea) string {
	if c, ok := socialCaption(i.Title); ok {
		return c
	}
	return strings.Join(strings.Fields(i.Title), " ")
}

// ideaDescription : « Activité partagée par Alice sur Tribbae, pour les 3–6 ans : »
// suivi de la description de l'idée, ou à défaut de son titre et de ses
// informations (prix, lieu, date).
func ideaDescription(i *Idea, c category) string {
	d := c.Label + " " + sharedBy(c)
	if i.Author != "" {
		d += " par " + i.Author
	}
	d += " sur Tribbae"
	if i.AgeRange != "" {
		d += ", " + forAges(i.AgeRange)
	}
	title := strings.TrimSpace(i.Title)
	if c, ok := socialCaption(title); ok {
		title = c
	}
	if desc := cleanDescription(i.Description); desc != "" && !strings.EqualFold(desc, title) {
		return d + " : " + desc
	}
	d += " : " + cleanTitle(i.Title) + "."
	for _, f := range ideaFacts(i) {
		if f.Label != "Âge" {
			d += " " + f.Label + " : " + f.Value + "."
		}
	}
	return d
}

// sharedBy accorde « partagé » avec la catégorie.
func sharedBy(c category) string {
	if c.Key == "LINK_CATEGORY_CADEAU" || c.Key == "LINK_CATEGORY_LIVRE" {
		return "partagé"
	}
	return "partagée"
}

// forAges : « pour les 3–6 ans », « pour tous les âges » ; une ancienne valeur
// libre qui ne commence pas par un âge reste telle quelle.
func forAges(age string) string {
	if strings.EqualFold(age, "Tous âges") {
		return "pour tous les âges"
	}
	if r, _ := utf8.DecodeRuneInString(age); unicode.IsDigit(r) {
		return "pour les " + age
	}
	return "âge : " + age
}
