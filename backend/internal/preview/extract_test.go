package preview

import (
	"net/url"
	"reflect"
	"strings"
	"testing"
	"time"
)

func extract(t *testing.T, page, html string) *Meta {
	t.Helper()
	base, _ := url.Parse(page)
	m, err := extractMeta(strings.NewReader(html), base)
	if err != nil {
		t.Fatalf("extractMeta: %v", err)
	}
	return m
}

func TestExtractImagesRankedAndResolved(t *testing.T) {
	m := extract(t, "https://example.com/blog/post", `<html><head>
		<title>Titre document</title>
		<meta name="twitter:image" content="/img/tw.jpg">
		<meta content="https://cdn.example.com/og.jpg" property="og:image">
		<meta property="og:image" content="https://cdn.example.com/og.jpg">
		<link rel="image_src" href="//static.example.com/src.png">
		<link rel="apple-touch-icon" href="/touch.png">
	</head><body>
		<header><img src="/header-banner.jpg" width="800" height="200"></header>
		<article>
			<img src="/logo.png" width="300" height="300">
			<img src="/tiny.jpg" width="16" height="16">
			<img class="site-logo" src="/brand.jpg">
			<img src="data:image/gif;base64,R0lGOD" data-src="/lazy.jpg">
			<img srcset="/s-400.jpg 400w, /s-1200.jpg 1200w, /s-3000.jpg 3000w" src="/s-400.jpg">
		</article>
		<noscript><img src="/noscript.jpg"></noscript>
	</body></html>`)

	want := []string{
		"https://cdn.example.com/og.jpg",
		"https://example.com/img/tw.jpg",
		"https://static.example.com/src.png",
		"https://example.com/lazy.jpg",
		"https://example.com/s-1200.jpg",
		"https://example.com/noscript.jpg",
		"https://example.com/header-banner.jpg",
	}
	if !reflect.DeepEqual(m.Images, want) {
		t.Errorf("images =\n%v\nwant\n%v", m.Images, want)
	}
	if m.Image != want[0] {
		t.Errorf("image = %q", m.Image)
	}
	if m.Title != "Titre document" {
		t.Errorf("title = %q", m.Title)
	}
}

func TestExtractTouchIconOnlyAsLastResort(t *testing.T) {
	m := extract(t, "https://example.com/", `<head><link rel="apple-touch-icon" href="/touch.png"></head>`)
	if m.Image != "https://example.com/touch.png" {
		t.Errorf("image = %q", m.Image)
	}
}

func TestExtractTitleCleanup(t *testing.T) {
	m := extract(t, "https://example.com/", `<head>
		<meta property="og:site_name" content="Marmiton">
		<meta property="og:title" content="  Crêpes   faciles &amp; rapides | Marmiton ">
		<meta name="description" content="Une recette">
	</head>`)
	if m.Title != "Crêpes faciles & rapides" {
		t.Errorf("title = %q", m.Title)
	}
	if m.SiteName != "Marmiton" || m.Description != "Une recette" {
		t.Errorf("siteName = %q, description = %q", m.SiteName, m.Description)
	}
}

func TestExtractRecipeJSONLD(t *testing.T) {
	m := extract(t, "https://recettes.example/crepes", `<head><script type="application/ld+json">
	{"@context":"https://schema.org","@graph":[
		{"@type":"Organization","name":"Site","logo":{"@type":"ImageObject","url":"https://recettes.example/logo.png"}},
		{"@type":"Recipe","name":"Crêpes","description":"Pâte à crêpes",
		 "image":[{"@type":"ImageObject","url":"https://recettes.example/crepes.jpg"},"https://recettes.example/crepes-2.jpg"],
		 "recipeIngredient":["250 g de farine","2 œufs","50 cl de lait"]}
	]}</script></head>`)
	if m.Category != "LINK_CATEGORY_RECETTE" {
		t.Errorf("category = %q", m.Category)
	}
	if m.Title != "Crêpes" || m.Description != "Pâte à crêpes" {
		t.Errorf("title = %q, description = %q", m.Title, m.Description)
	}
	if want := []string{"250 g de farine", "2 œufs", "50 cl de lait"}; !reflect.DeepEqual(m.Ingredients, want) {
		t.Errorf("ingredients = %v", m.Ingredients)
	}
	if want := []string{"https://recettes.example/crepes.jpg", "https://recettes.example/crepes-2.jpg"}; !reflect.DeepEqual(m.Images, want) {
		t.Errorf("images = %v (le logo de l'organisation ne doit pas apparaître)", m.Images)
	}
}

func TestExtractEventJSONLD(t *testing.T) {
	m := extract(t, "https://agenda.example/fete", `<head><script type="application/ld+json">
	[{"@type":"WebSite","name":"Agenda"},
	 {"@type":"MusicEvent","name":"Fête de la courge","startDate":"2026-10-24T14:00",
	  "location":{"@type":"Place","name":"Ferme des Saules","address":{"addressLocality":"Lyon"}},
	  "offers":{"price":"6","priceCurrency":"EUR"}}]
	</script></head>`)
	if m.Category != "LINK_CATEGORY_EVENEMENT" {
		t.Errorf("category = %q", m.Category)
	}
	if m.Location != "Ferme des Saules, Lyon" {
		t.Errorf("location = %q", m.Location)
	}
	if m.Price != "6 €" {
		t.Errorf("price = %q", m.Price)
	}
	paris, _ := time.LoadLocation("Europe/Paris")
	if want := time.Date(2026, 10, 24, 14, 0, 0, 0, paris).UnixMilli(); m.EventDate != want {
		t.Errorf("eventDate = %d, want %d", m.EventDate, want)
	}
}

func TestExtractProductMetaPrice(t *testing.T) {
	m := extract(t, "https://shop.example/p", `<head>
		<meta property="og:type" content="product">
		<meta property="product:price:amount" content="19.9">
		<meta property="product:price:currency" content="EUR">
	</head>`)
	if m.Category != "LINK_CATEGORY_CADEAU" || m.Price != "19,90 €" {
		t.Errorf("category = %q, price = %q", m.Category, m.Price)
	}
}

func TestExtractInvalidJSONLDIgnored(t *testing.T) {
	m := extract(t, "https://example.com/", `<head><title>Ok</title><script type="application/ld+json">{oops</script></head>`)
	if m.Title != "Ok" || m.Category != "" {
		t.Errorf("title = %q, category = %q", m.Title, m.Category)
	}
}

func TestExtractBaseHref(t *testing.T) {
	m := extract(t, "https://example.com/a/b", `<head><base href="https://cdn.example.net/assets/"><meta property="og:image" content="pic.jpg"></head>`)
	if m.Image != "https://cdn.example.net/assets/pic.jpg" {
		t.Errorf("image = %q", m.Image)
	}
}

func TestBestFromSrcset(t *testing.T) {
	cases := map[string]string{
		"a.jpg 300w, b.jpg 800w":   "b.jpg",
		"a.jpg 1x, b.jpg 2x":       "b.jpg",
		"big.jpg 2400w":            "big.jpg",
		"a.jpg 2000w, b.jpg 1800w": "b.jpg",
		"":                         "",
	}
	for in, want := range cases {
		if got := bestFromSrcset(in); got != want {
			t.Errorf("bestFromSrcset(%q) = %q, want %q", in, got, want)
		}
	}
}

func TestYouTubeID(t *testing.T) {
	cases := map[string]string{
		"https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=1": "dQw4w9WgXcQ",
		"https://youtu.be/dQw4w9WgXcQ":                    "dQw4w9WgXcQ",
		"https://m.youtube.com/shorts/dQw4w9WgXcQ":        "dQw4w9WgXcQ",
		"https://www.youtube.com/channel/abc":             "",
		"https://example.com/watch?v=dQw4w9WgXcQ":         "",
	}
	for in, want := range cases {
		if got := youTubeID(in); got != want {
			t.Errorf("youTubeID(%q) = %q, want %q", in, got, want)
		}
	}
}

func TestFormatPrice(t *testing.T) {
	cases := [][3]string{{"12.5", "EUR", "12,50 €"}, {"0", "EUR", "Gratuit"}, {"30", "USD", "30 $"}, {"", "EUR", ""}, {"dès 5", "", "dès 5"}}
	for _, c := range cases {
		if got := formatPrice(c[0], c[1]); got != c[2] {
			t.Errorf("formatPrice(%q, %q) = %q, want %q", c[0], c[1], got, c[2])
		}
	}
}

func TestGenericTitle(t *testing.T) {
	cases := []struct {
		title, site, page string
		want              bool
	}{
		{"", "", "https://example.com/", true},
		{"Amazon.fr", "", "https://www.amazon.fr/dp/1", true},
		{"Instagram", "Instagram", "https://www.instagram.com/p/x/", true},
		{"TikTok - Make Your Day", "", "https://www.tiktok.com/@a/video/1", true},
		{"Just a moment...", "", "https://www.decathlon.fr/", true},
		{"Le Petit Prince", "Amazon.fr", "https://www.amazon.fr/dp/1", false},
	}
	for _, c := range cases {
		if got := genericTitle(&Meta{Title: c.title, SiteName: c.site}, c.page); got != c.want {
			t.Errorf("genericTitle(%q, %q) = %v, want %v", c.title, c.page, got, c.want)
		}
	}
}

func TestMergeMetaPrefersBetterAttempt(t *testing.T) {
	page := "https://www.amazon.fr/dp/1"
	blocked := &Meta{Title: "Amazon.fr", Images: []string{"https://x/a.jpg"}}
	finalizeImages(blocked)
	good := &Meta{Title: "Le Petit Prince", Description: "Un livre", Images: []string{"https://x/b.jpg"}}
	finalizeImages(good)
	m := mergeMeta(blocked, good, page)
	if m.Title != "Le Petit Prince" || m.Image != "https://x/b.jpg" || len(m.Images) != 2 {
		t.Errorf("merge = %+v", m)
	}
}

func TestKnownOEmbed(t *testing.T) {
	cases := map[string]string{
		"https://www.tiktok.com/@a/video/1": "https://www.tiktok.com/oembed?url=",
		"https://vimeo.com/1084537":         "https://vimeo.com/api/oembed.json?url=",
		"https://www.pinterest.fr/pin/42/":  "https://www.pinterest.com/oembed.json?url=https%3A%2F%2Fwww.pinterest.com%2Fpin%2F42%2F",
		"https://example.com/":              "",
	}
	for in, want := range cases {
		if got := knownOEmbed(in); !strings.HasPrefix(got, want) || (want == "" && got != "") {
			t.Errorf("knownOEmbed(%q) = %q, want prefix %q", in, got, want)
		}
	}
}

func TestCaptchaImagesIgnored(t *testing.T) {
	m := extract(t, "https://www.leboncoin.fr/", `<head><meta property="og:image" content="https://img.datadome.co/captcha/page.png"></head>`)
	if len(m.Images) != 0 {
		t.Errorf("images = %v", m.Images)
	}
}

func TestDeclaredLogoRanksBehindPageImages(t *testing.T) {
	m := extract(t, "https://shop.example/", `<head><meta property="og:image" content="/resources/header/nLogo.png"></head>
		<body><main><img src="/promo/rentree.jpg" width="800" height="400"></main></body>`)
	want := []string{"https://shop.example/promo/rentree.jpg", "https://shop.example/resources/header/nLogo.png"}
	if !reflect.DeepEqual(m.Images, want) {
		t.Errorf("images = %v, want %v", m.Images, want)
	}
}

func TestGenericPlatformImagesIgnored(t *testing.T) {
	m := extract(t, "https://www.instagram.com/p/x/", `<head><meta property="og:image" content="https://static.cdninstagram.com/rsrc.php/yj/r/f01UdNyJjJj.webp"></head>`)
	if len(m.Images) != 0 {
		t.Errorf("images = %v", m.Images)
	}
}
