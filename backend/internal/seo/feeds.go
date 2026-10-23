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
	add("/aide", time.Time{}, "monthly", "0.5", "")
	if h.about != nil {
		add("/a-propos", time.Time{}, "yearly", "0.4", "")
	}
	add("/confidentialite", time.Time{}, "yearly", "0.3", "")
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
	b.WriteString("Tribbae est une application gratuite, en français, sur le web et Android. ")
	b.WriteString("Chaque famille (sa « tribu ») y range ses idées en dossiers ; les idées restent privées sauf si leur auteur choisit de les publier. ")
	b.WriteString("Les pages ci-dessous ne montrent que le contenu publié.\n\n")
	b.WriteString("## Pages principales\n\n")
	fmt.Fprintf(&b, "- [Accueil](%s/): présentation et dernières idées partagées\n", h.site)
	fmt.Fprintf(&b, "- [Explorer](%s/explorer): toutes les idées et dossiers publiés par les familles\n", h.site)
	fmt.Fprintf(&b, "- [Aide & FAQ](%s/aide): fonctionnement de l'application, partage, confidentialité\n", h.site)
	if h.about != nil {
		fmt.Fprintf(&b, "- [À propos](%s/a-propos): pourquoi Tribbae existe, ce que fait l'application, ses principes\n", h.site)
	}
	b.WriteString("\n## Catégories\n\n")
	for _, c := range categories {
		fmt.Fprintf(&b, "- [%s](%s/category/%s): %s\n", c.Plural, h.site, c.Slug, c.Intro)
	}
	if len(ideas) > 0 {
		b.WriteString("\n## Dernières idées publiées\n\n")
		for _, i := range ideas {
			c := categoryByKey(i.Category)
			line := fmt.Sprintf("- [%s](%s/links/%s): %s", oneLine(i.Title), h.site, i.ID, c.Label)
			if d := clip(i.Description, 140); d != "" {
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
