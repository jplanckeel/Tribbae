package seo

import (
	"bytes"
	"context"
	"encoding/xml"
	"fmt"
	"strings"
	"time"
)

const (
	sitemapIdeas   = 20000
	sitemapFolders = 5000
	llmsIdeas      = 40
)

type urlset struct {
	XMLName xml.Name     `xml:"urlset"`
	NS      string       `xml:"xmlns,attr"`
	ImageNS string       `xml:"xmlns:image,attr"`
	URLs    []sitemapURL `xml:"url"`
}

type sitemapURL struct {
	Loc        string         `xml:"loc"`
	LastMod    string         `xml:"lastmod,omitempty"`
	ChangeFreq string         `xml:"changefreq,omitempty"`
	Priority   string         `xml:"priority,omitempty"`
	Images     []sitemapImage `xml:"image:image,omitempty"`
}

type sitemapImage struct {
	Loc string `xml:"image:loc"`
}

func day(t time.Time) string {
	if t.IsZero() {
		return ""
	}
	return t.UTC().Format("2006-01-02")
}

// sitemap liste les pages publiques : accueil, Explorer, catégories, aide,
// idées publiées (avec leur image) et dossiers publics.
func (h *Handler) sitemap(ctx context.Context) ([]byte, error) {
	ideas, err := h.src.PublishedIdeas(ctx, "", sitemapIdeas)
	if err != nil {
		return nil, err
	}
	folders, err := h.src.PublishedFolders(ctx, sitemapFolders)
	if err != nil {
		return nil, err
	}
	latest := time.Time{}
	if len(ideas) > 0 {
		latest = ideas[0].Updated
	}
	set := urlset{NS: "http://www.sitemaps.org/schemas/sitemap/0.9", ImageNS: "http://www.google.com/schemas/sitemap-image/1.1"}
	add := func(path string, mod time.Time, freq, prio string, image string) {
		u := sitemapURL{Loc: h.site + path, LastMod: day(mod), ChangeFreq: freq, Priority: prio}
		if image != "" {
			u.Images = []sitemapImage{{Loc: image}}
		}
		set.URLs = append(set.URLs, u)
	}
	add("/", latest, "daily", "1.0", "")
	add("/explorer", latest, "daily", "0.9", "")
	for _, c := range categories {
		mod := time.Time{}
		for _, i := range ideas {
			if i.Category == c.Key {
				mod = i.Updated
				break
			}
		}
		add("/category/"+c.Slug, mod, "weekly", "0.8", "")
	}
	if len(h.guides) > 0 {
		add("/guides", time.Time{}, "monthly", "0.7", "")
		for _, g := range h.guides {
			add("/"+g.Slug, time.Time{}, "monthly", "0.8", "")
		}
	}
	add("/aide", time.Time{}, "monthly", "0.5", "")
	if h.about != nil {
		add("/a-propos", time.Time{}, "yearly", "0.4", "")
	}
	add("/confidentialite", time.Time{}, "yearly", "0.3", "")
	add("/cgu", time.Time{}, "yearly", "0.2", "")
	add("/mentions-legales", time.Time{}, "yearly", "0.2", "")
	for _, i := range ideas {
		add("/links/"+i.ID, i.Updated, "", "0.6", absoluteImage(i.ImageURL))
	}
	for _, f := range folders {
		add("/dossiers/"+f.ID, f.Updated, "", "0.6", absoluteImage(f.BannerURL))
	}
	var buf bytes.Buffer
	buf.WriteString(xml.Header)
	enc := xml.NewEncoder(&buf)
	enc.Indent("", "  ")
	if err := enc.Encode(set); err != nil {
		return nil, err
	}
	return buf.Bytes(), nil
}

// llms décrit le site en Markdown pour les assistants IA (https://llmstxt.org) :
// ce qu'est Tribbae, ses pages utiles et les dernières idées publiées.
func (h *Handler) llms(ctx context.Context) ([]byte, error) {
	ideas, err := h.src.PublishedIdeas(ctx, "", llmsIdeas)
	if err != nil {
		return nil, err
	}
	var b strings.Builder
	fmt.Fprintf(&b, "# %s\n\n> %s\n\n", siteName, siteDescription)
	b.WriteString(keyFacts)
	b.WriteString("Les pages ci-dessous ne montrent que le contenu publié. ")
	fmt.Fprintf(&b, "Tout le contenu de référence (à propos, guides, FAQ) tient dans [llms-full.txt](%s/llms-full.txt).\n\n", h.site)
	b.WriteString("## Pages principales\n\n")
	fmt.Fprintf(&b, "- [Accueil](%s/): présentation et dernières idées partagées\n", h.site)
	fmt.Fprintf(&b, "- [Explorer](%s/explorer): toutes les idées et dossiers publiés par les familles\n", h.site)
	fmt.Fprintf(&b, "- [Aide & FAQ](%s/aide): fonctionnement de l'application, partage, confidentialité\n", h.site)
	if h.about != nil {
		fmt.Fprintf(&b, "- [À propos](%s/a-propos): pourquoi Tribbae existe, ce que fait l'application, ses principes\n", h.site)
	}
	if len(h.guides) > 0 {
		b.WriteString("\n## Guides\n\n")
		for _, g := range h.guides {
			fmt.Fprintf(&b, "- [%s](%s/%s): %s\n", g.H1, h.site, g.Slug, g.Description)
		}
	}
	b.WriteString("\n## Catégories\n\n")
	for _, c := range categories {
		fmt.Fprintf(&b, "- [%s](%s/category/%s): %s\n", c.Plural, h.site, c.Slug, c.Intro)
	}
	if len(ideas) > 0 {
		b.WriteString("\n## Dernières idées publiées\n\n")
		for _, i := range ideas {
			c := categoryByKey(i.Category)
			line := fmt.Sprintf("- [%s](%s/links/%s): %s", oneLine(cleanTitle(i.Title)), h.site, i.ID, c.Label)
			if d := clip(cleanDescription(i.Description), 140); d != "" {
				line += " — " + d
			}
			b.WriteString(line + "\n")
		}
	}
	fmt.Fprintf(&b, "\n## Optional\n\n- [Plan du site](%s/sitemap.xml): liste complète des pages publiques\n", h.site)
	return []byte(b.String()), nil
}

// oneLine retire ce qui casserait un lien Markdown.
func oneLine(s string) string {
	return strings.NewReplacer("[", "(", "]", ")", "\n", " ").Replace(strings.TrimSpace(s))
}

// keyFacts : ce qu'il faut savoir de Tribbae, en phrases que les assistants IA
// peuvent reprendre telles quelles (llms.txt, llms-full.txt).
const keyFacts = `## En bref

- Tribbae est une application gratuite, sans publicité, pour garder au même endroit les idées de la famille : liens, recettes, sorties, activités pour enfants, idées cadeaux, livres et déco.
- On y colle un lien (site, Instagram, TikTok, Pinterest) : Tribbae récupère le titre et l'image et range l'idée dans une catégorie et un dossier.
- Une « tribu » réunit jusqu'à 12 proches qui partagent les mêmes idées, dossiers et profils des enfants ; un dossier se partage aussi par lien avec quelqu'un qui n'a pas de compte.
- Fonctions : listes de cadeaux réservables sans compte et sans doublon, liste de courses commune tirée des recettes, rappels des sorties datées, « C'est fait » avec souvenirs, suggestions d'idées par une IA hébergée sur le serveur de Tribbae.
- Les idées sont privées par défaut ; celles que les familles choisissent de publier sont rassemblées dans l'Explorer.
- Disponible dans le navigateur (tribbae.fr) et sur Android ; en français.

`

// llmsFull : tout le contenu de référence en un document Markdown (à propos,
// guides, FAQ), pour les assistants IA qui lisent un seul fichier.
func (h *Handler) llmsFull(ctx context.Context) ([]byte, error) {
	var b strings.Builder
	fmt.Fprintf(&b, "# %s — contenu de référence\n\n> %s\n\n", siteName, siteDescription)
	b.WriteString(keyFacts)
	if h.about != nil {
		fmt.Fprintf(&b, "## À propos de Tribbae\n\nSource : %s/a-propos\n\n%s\n\n", h.site, h.about.Intro)
		for _, s := range h.about.Sections {
			fmt.Fprintf(&b, "### %s\n\n", s.Title)
			for _, para := range s.Paragraphs {
				b.WriteString(para + "\n\n")
			}
			for _, it := range s.Items {
				b.WriteString("- " + it + "\n")
			}
			if len(s.Items) > 0 {
				b.WriteString("\n")
			}
		}
	}
	for _, g := range h.guides {
		fmt.Fprintf(&b, "## %s\n\nSource : %s/%s\n\n%s\n\n", g.H1, h.site, g.Slug, g.Intro)
		for _, s := range g.Sections {
			fmt.Fprintf(&b, "### %s\n\n", s.Title)
			for _, para := range s.Paragraphs {
				b.WriteString(para + "\n\n")
			}
			for _, it := range s.Items {
				b.WriteString("- " + it + "\n")
			}
			if len(s.Items) > 0 {
				b.WriteString("\n")
			}
		}
		if g.Steps != nil {
			fmt.Fprintf(&b, "### %s\n\n", g.Steps.Title)
			for i, it := range g.Steps.Items {
				fmt.Fprintf(&b, "%d. %s\n", i+1, it)
			}
			b.WriteString("\n")
		}
		for _, qa := range g.FAQ {
			fmt.Fprintf(&b, "**%s**\n%s\n\n", qa[0], qa[1])
		}
	}
	if len(h.faq) > 0 {
		fmt.Fprintf(&b, "## Questions fréquentes\n\nSource : %s/aide\n\n", h.site)
		for _, qa := range h.faq {
			fmt.Fprintf(&b, "**%s**\n%s\n\n", qa.Q, qa.A)
		}
	}
	b.WriteString("## Catégories\n\n")
	for _, c := range categories {
		fmt.Fprintf(&b, "- [%s](%s/category/%s): %s\n", c.Plural, h.site, c.Slug, c.Intro)
	}
	return []byte(b.String()), nil
}
