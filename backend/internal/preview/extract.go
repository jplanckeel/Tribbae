package preview

import (
	"encoding/json"
	"fmt"
	"io"
	"net/url"
	"path"
	"regexp"
	"sort"
	"strconv"
	"strings"
	"time"
	"unicode/utf8"

	"golang.org/x/net/html"
)

// Meta est l'aperçu d'une page renvoyé par /v1/links/preview. Image est la
// meilleure image ; Images liste tous les candidats, du meilleur au moins bon,
// pour que l'utilisateur puisse choisir. Les champs structurés (catégorie,
// ingrédients, prix, lieu, date) viennent des données schema.org de la page.
type Meta struct {
	Title       string   `json:"title"`
	Description string   `json:"description"`
	Image       string   `json:"image"`
	Images      []string `json:"images"`
	SiteName    string   `json:"siteName,omitempty"`
	Category    string   `json:"category,omitempty"`
	Ingredients []string `json:"ingredients,omitempty"`
	Price       string   `json:"price,omitempty"`
	Location    string   `json:"location,omitempty"`
	EventDate   int64    `json:"eventDate,omitempty"`

	// oembed est l'URL de découverte oEmbed de la page (fallback image/titre).
	oembed string
}

const (
	maxImages      = 12
	maxBodyImages  = 40
	maxTitleRunes  = 300
	maxDescRunes   = 1000
	maxFieldRunes  = 200
	maxIngredients = 60
)

// Scores des sources d'image : une image déclarée pour l'aperçu par le site
// vaut toujours mieux qu'une image trouvée dans le corps de la page.
const (
	scoreOG        = 100
	scoreTwitter   = 90
	scoreJSONLD    = 85
	scoreImageSrc  = 80
	scoreItemprop  = 75
	scoreThumbnail = 70
	scorePoster    = 45
	scoreBodyImg   = 30
	scoreTouchIcon = 1 // dernier recours : icône du site
)

type imageCandidate struct {
	url   string
	score int
	order int
}

type extractor struct {
	base      *url.URL
	meta      map[string]string // première valeur de chaque meta (clé en minuscules)
	docTitle  string
	h1        string
	cands     []imageCandidate
	order     int
	bodyImgs  int
	icons     []string
	oembed    string
	ld        ldData
	ldScripts []string
}

// ldData regroupe ce qui a été lu dans le JSON-LD.
type ldData struct {
	rank        int // priorité du type principal trouvé (Recipe > Event > Book > Product)
	category    string
	name        string
	description string
	ingredients []string
	price       string
	location    string
	eventDate   int64
}

// extractMeta lit une page HTML et en extrait l'aperçu. base est l'URL finale
// de la page (après redirections), utilisée pour résoudre les URL relatives.
func extractMeta(r io.Reader, base *url.URL) (*Meta, error) {
	// Scripting désactivé : le contenu des <noscript> est parsé comme du HTML,
	// ce qui expose les vraies <img> des pages à chargement différé.
	doc, err := html.ParseWithOptions(r, html.ParseOptionEnableScripting(false))
	if err != nil {
		return nil, err
	}
	e := &extractor{base: base, meta: map[string]string{}}
	e.walk(doc, false, false)
	for _, s := range e.ldScripts {
		e.parseJSONLD(s)
	}
	return e.result(), nil
}

func (e *extractor) walk(n *html.Node, chrome, content bool) {
	if n.Type == html.ElementNode {
		switch n.Data {
		case "base":
			if href := attrVal(n, "href"); href != "" {
				if b := e.resolve(href); b != nil {
					e.base = b
				}
			}
		case "title":
			if e.docTitle == "" {
				e.docTitle = textContent(n)
			}
		case "h1":
			if e.h1 == "" {
				e.h1 = textContent(n)
			}
		case "meta":
			e.metaTag(n)
		case "link":
			e.linkTag(n)
		case "script":
			if strings.Contains(strings.ToLower(attrVal(n, "type")), "ld+json") && n.FirstChild != nil {
				e.ldScripts = append(e.ldScripts, n.FirstChild.Data)
			}
			return
		case "img":
			e.imgTag(n, chrome, content)
		case "source":
			// <picture><source srcset> : variantes de l'<img> qui suit
			if n.Parent != nil && n.Parent.Data == "picture" {
				if src := bestFromSrcset(firstAttr(n, "data-srcset", "srcset")); src != "" {
					e.addBody(src, scoreBodyImg, chrome, content)
				}
			}
		case "video":
			if p := attrVal(n, "poster"); p != "" {
				e.add(p, scorePoster)
			}
		case "header", "nav", "footer", "aside":
			chrome = true
		case "article", "main":
			content = true
		}
	}
	for c := n.FirstChild; c != nil; c = c.NextSibling {
		e.walk(c, chrome, content)
	}
}

func (e *extractor) metaTag(n *html.Node) {
	content := strings.TrimSpace(attrVal(n, "content"))
	if content == "" {
		return
	}
	for _, k := range []string{"property", "name", "itemprop"} {
		key := strings.ToLower(strings.TrimSpace(attrVal(n, k)))
		if key == "" {
			continue
		}
		if _, seen := e.meta[key]; !seen {
			e.meta[key] = content
		}
		switch key {
		case "og:image", "og:image:url", "og:image:secure_url":
			e.add(content, scoreOG)
		case "twitter:image", "twitter:image:src":
			e.add(content, scoreTwitter)
		case "thumbnail", "thumbnailurl":
			e.add(content, scoreThumbnail)
		case "image":
			if k == "itemprop" {
				e.add(content, scoreItemprop)
			}
		}
	}
}

func (e *extractor) linkTag(n *html.Node) {
	rel := strings.ToLower(attrVal(n, "rel"))
	href := strings.TrimSpace(attrVal(n, "href"))
	if href == "" {
		return
	}
	switch {
	case rel == "image_src":
		e.add(href, scoreImageSrc)
	case attrVal(n, "itemprop") == "image":
		e.add(href, scoreItemprop)
	case strings.Contains(rel, "apple-touch-icon"):
		e.icons = append(e.icons, href)
	case rel == "alternate" && strings.Contains(strings.ToLower(attrVal(n, "type")), "json+oembed"):
		if u := e.resolve(href); u != nil && e.oembed == "" {
			e.oembed = u.String()
		}
	}
}

func (e *extractor) imgTag(n *html.Node, chrome, content bool) {
	if attrVal(n, "itemprop") == "image" {
		if src := imgSource(n); src != "" {
			e.add(src, scoreItemprop)
			return
		}
	}
	// Les icônes, logos et pixels de suivi sont signalés par leurs dimensions
	// ou leurs classes : on ne les propose pas.
	w, h := atoiAttr(n, "width"), atoiAttr(n, "height")
	if (w > 0 && w < 120) || (h > 0 && h < 120) {
		return
	}
	hint := strings.ToLower(attrVal(n, "class") + " " + attrVal(n, "id") + " " + attrVal(n, "alt"))
	if junkHint.MatchString(hint) {
		return
	}
	src := imgSource(n)
	if src == "" {
		return
	}
	score := scoreBodyImg
	if w*h >= 300*200 {
		score += 20
	}
	e.addBody(src, score, chrome, content)
}

// addBody ajoute une image du corps de la page, pondérée par sa position.
func (e *extractor) addBody(src string, score int, chrome, content bool) {
	if e.bodyImgs >= maxBodyImages {
		return
	}
	u := e.resolve(src)
	if u == nil || junkImageURL(u) {
		return
	}
	if chrome {
		score -= 25
	}
	if content {
		score += 10
	}
	if strings.HasSuffix(strings.ToLower(u.Path), ".gif") {
		score -= 10
	}
	e.bodyImgs++
	e.push(u.String(), score)
}

// add ajoute une image déclarée par le site (meta, JSON-LD…).
func (e *extractor) add(raw string, score int) {
	if u := e.resolve(raw); u != nil {
		e.push(u.String(), score)
	}
}

func (e *extractor) push(u string, score int) {
	// Page de challenge anti-bot (Datadome, Cloudflare…) : son visuel n'a rien à voir
	if strings.Contains(strings.ToLower(u), "captcha") {
		return
	}
	e.cands = append(e.cands, imageCandidate{url: u, score: score, order: e.order})
	e.order++
}

// resolve rend une URL absolue http(s), ou nil.
func (e *extractor) resolve(raw string) *url.URL {
	raw = strings.TrimSpace(raw)
	if raw == "" || strings.HasPrefix(raw, "data:") {
		return nil
	}
	ref, err := url.Parse(raw)
	if err != nil {
		return nil
	}
	if e.base != nil {
		ref = e.base.ResolveReference(ref)
	}
	if ref.Scheme != "http" && ref.Scheme != "https" || ref.Host == "" {
		return nil
	}
	ref.Fragment = ""
	return ref
}

func (e *extractor) result() *Meta {
	m := &Meta{oembed: e.oembed}
	pick := func(keys ...string) string {
		for _, k := range keys {
			if v := e.meta[k]; v != "" {
				return v
			}
		}
		return ""
	}

	m.SiteName = cleanText(pick("og:site_name", "application-name"))
	m.Title = cleanText(firstNonEmpty(pick("og:title", "twitter:title"), e.ld.name, e.docTitle, e.h1))
	m.Title = truncateRunes(stripSiteSuffix(m.Title, m.SiteName), maxTitleRunes)
	m.Description = truncateRunes(cleanText(firstNonEmpty(
		pick("og:description", "twitter:description", "description"), e.ld.description)), maxDescRunes)

	m.Category = e.ld.category
	if m.Category == "" {
		m.Category = categoryFromOGType(pick("og:type"))
	}
	m.Ingredients = e.ld.ingredients
	m.Location = truncateRunes(e.ld.location, maxFieldRunes)
	m.EventDate = e.ld.eventDate
	m.Price = e.ld.price
	if m.Price == "" {
		m.Price = formatPrice(
			pick("product:price:amount", "og:price:amount", "price"),
			pick("product:price:currency", "og:price:currency", "pricecurrency"))
	}
	m.Price = truncateRunes(m.Price, maxFieldRunes)

	sort.SliceStable(e.cands, func(i, j int) bool {
		if e.cands[i].score != e.cands[j].score {
			return e.cands[i].score > e.cands[j].score
		}
		return e.cands[i].order < e.cands[j].order
	})
	for _, c := range e.cands {
		m.Images = appendUnique(m.Images, c.url)
	}
	// L'icône du site ne sert que s'il n'y a vraiment rien d'autre
	if len(m.Images) == 0 {
		for _, ic := range e.icons {
			if u := e.resolve(ic); u != nil {
				m.Images = appendUnique(m.Images, u.String())
			}
		}
	}
	finalizeImages(m)
	return m
}

// finalizeImages borne la liste et synchronise Image avec le premier candidat.
func finalizeImages(m *Meta) {
	if len(m.Images) > maxImages {
		m.Images = m.Images[:maxImages]
	}
	if m.Images == nil {
		m.Images = []string{}
	}
	m.Image = ""
	if len(m.Images) > 0 {
		m.Image = m.Images[0]
	}
}

// ── JSON-LD (schema.org) ───────────────────────────────────────────────

var ldRank = map[string]int{"LINK_CATEGORY_RECETTE": 4, "LINK_CATEGORY_EVENEMENT": 3, "LINK_CATEGORY_LIVRE": 2, "LINK_CATEGORY_CADEAU": 1}

func (e *extractor) parseJSONLD(raw string) {
	var v any
	if err := json.Unmarshal([]byte(strings.TrimSpace(raw)), &v); err != nil {
		return
	}
	e.ldWalk(v, 0)
}

func (e *extractor) ldWalk(v any, depth int) {
	if depth > 6 {
		return
	}
	switch t := v.(type) {
	case []any:
		for _, x := range t {
			e.ldWalk(x, depth+1)
		}
	case map[string]any:
		if g, ok := t["@graph"]; ok {
			e.ldWalk(g, depth+1)
		}
		if types := ldTypes(t["@type"]); len(types) > 0 {
			e.ldObject(t, types)
		}
		if me, ok := t["mainEntity"]; ok {
			e.ldWalk(me, depth+1)
		}
	}
}

func (e *extractor) ldObject(o map[string]any, types []string) {
	category := ""
	imageful := false
	for _, ty := range types {
		switch {
		case ty == "Recipe":
			category = "LINK_CATEGORY_RECETTE"
		case strings.HasSuffix(ty, "Event"):
			category = firstNonEmpty(category, "LINK_CATEGORY_EVENEMENT")
		case ty == "Book":
			category = firstNonEmpty(category, "LINK_CATEGORY_LIVRE")
		case ty == "Product":
			category = firstNonEmpty(category, "LINK_CATEGORY_CADEAU")
		case ty == "ImageObject":
			// Le graphe Yoast/WordPress déclare l'image principale ainsi ; le logo aussi
			id := strings.ToLower(ldString(o["@id"]) + ldString(o["url"]))
			if !strings.Contains(id, "logo") {
				e.add(firstNonEmpty(ldString(o["contentUrl"]), ldString(o["url"])), scoreJSONLD)
			}
		case strings.HasSuffix(ty, "Article"), ty == "BlogPosting", ty == "WebPage", ty == "VideoObject",
			ty == "Place", ty == "LocalBusiness", ty == "TouristAttraction", ty == "Restaurant", ty == "CreativeWork", ty == "HowTo":
			imageful = true
		}
	}
	if category != "" || imageful {
		for _, img := range ldImages(o["image"]) {
			e.add(img, scoreJSONLD)
		}
		for _, img := range ldImages(o["thumbnailUrl"]) {
			e.add(img, scoreJSONLD)
		}
		for _, img := range ldImages(o["primaryImageOfPage"]) {
			e.add(img, scoreJSONLD)
		}
	}
	if category == "" || ldRank[category] <= e.ld.rank {
		return
	}

	d := ldData{rank: ldRank[category], category: category}
	d.name = cleanText(ldString(o["name"]))
	d.description = cleanText(ldString(o["description"]))
	switch category {
	case "LINK_CATEGORY_RECETTE":
		ing := o["recipeIngredient"]
		if ing == nil {
			ing = o["ingredients"]
		}
		for _, s := range ldStrings(ing) {
			if s = cleanText(s); s != "" && len(d.ingredients) < maxIngredients {
				d.ingredients = append(d.ingredients, truncateRunes(s, maxFieldRunes))
			}
		}
	case "LINK_CATEGORY_EVENEMENT":
		d.eventDate = parseLDDate(ldString(o["startDate"]))
		d.location = ldLocation(o["location"])
		d.price = ldPrice(o["offers"])
	case "LINK_CATEGORY_CADEAU", "LINK_CATEGORY_LIVRE":
		d.price = ldPrice(o["offers"])
	}
	e.ld = d
}

func ldTypes(v any) []string {
	var out []string
	for _, s := range ldStrings(v) {
		// "http://schema.org/Recipe" → "Recipe"
		out = append(out, s[strings.LastIndex(s, "/")+1:])
	}
	return out
}

// ldString lit une valeur texte (ou nombre) JSON-LD.
func ldString(v any) string {
	switch t := v.(type) {
	case string:
		return t
	case float64:
		return strconv.FormatFloat(t, 'f', -1, 64)
	case []any:
		if len(t) > 0 {
			return ldString(t[0])
		}
	case map[string]any:
		if s, ok := t["@value"]; ok {
			return ldString(s)
		}
	}
	return ""
}

func ldStrings(v any) []string {
	switch t := v.(type) {
	case string:
		return []string{t}
	case []any:
		var out []string
		for _, x := range t {
			if s, ok := x.(string); ok {
				out = append(out, s)
			}
		}
		return out
	}
	return nil
}

// ldImages lit une propriété image : URL, liste, ou ImageObject.
func ldImages(v any) []string {
	switch t := v.(type) {
	case string:
		return []string{t}
	case []any:
		var out []string
		for _, x := range t {
			out = append(out, ldImages(x)...)
		}
		return out
	case map[string]any:
		if s := firstNonEmpty(ldString(t["url"]), ldString(t["contentUrl"])); s != "" {
			return []string{s}
		}
	}
	return nil
}

func ldLocation(v any) string {
	switch t := v.(type) {
	case string:
		return cleanText(t)
	case []any:
		if len(t) > 0 {
			return ldLocation(t[0])
		}
	case map[string]any:
		name := cleanText(ldString(t["name"]))
		addr := ""
		switch a := t["address"].(type) {
		case string:
			addr = cleanText(a)
		case map[string]any:
			addr = cleanText(firstNonEmpty(ldString(a["addressLocality"]), ldString(a["streetAddress"])))
		}
		switch {
		case name == "":
			return addr
		case addr == "" || strings.Contains(strings.ToLower(name), strings.ToLower(addr)):
			return name
		default:
			return name + ", " + addr
		}
	}
	return ""
}

func ldPrice(v any) string {
	switch t := v.(type) {
	case []any:
		for _, x := range t {
			if p := ldPrice(x); p != "" {
				return p
			}
		}
	case map[string]any:
		amount := firstNonEmpty(ldString(t["price"]), ldString(t["lowPrice"]))
		return formatPrice(amount, ldString(t["priceCurrency"]))
	}
	return ""
}

var ldDateLayouts = []string{
	time.RFC3339, "2006-01-02T15:04:05Z0700", "2006-01-02T15:04Z07:00",
	"2006-01-02T15:04:05", "2006-01-02T15:04", "2006-01-02 15:04", "2006-01-02",
}

// parseLDDate convertit une date schema.org en ms epoch. Sans fuseau, la date
// est interprétée à l'heure de Paris (public de l'application).
func parseLDDate(s string) int64 {
	s = strings.TrimSpace(s)
	if s == "" {
		return 0
	}
	loc, err := time.LoadLocation("Europe/Paris")
	if err != nil {
		loc = time.UTC
	}
	for _, layout := range ldDateLayouts {
		if t, err := time.ParseInLocation(layout, s, loc); err == nil {
			return t.UnixMilli()
		}
	}
	return 0
}

// ── Helpers ────────────────────────────────────────────────────────────

func categoryFromOGType(t string) string {
	switch t = strings.ToLower(t); {
	case t == "book" || strings.HasPrefix(t, "books."):
		return "LINK_CATEGORY_LIVRE"
	case t == "product" || strings.HasPrefix(t, "product.") || t == "og:product":
		return "LINK_CATEGORY_CADEAU"
	}
	return ""
}

var currencySymbols = map[string]string{"EUR": "€", "USD": "$", "GBP": "£", "CHF": "CHF", "CAD": "$ CA"}

// formatPrice met un prix au format français : « 12,99 € ».
func formatPrice(amount, currency string) string {
	amount = strings.TrimSpace(amount)
	if amount == "" {
		return ""
	}
	if f, err := strconv.ParseFloat(strings.ReplaceAll(amount, ",", "."), 64); err == nil {
		if f <= 0 {
			return "Gratuit"
		}
		if f == float64(int64(f)) {
			amount = strconv.FormatInt(int64(f), 10)
		} else {
			amount = strings.ReplaceAll(fmt.Sprintf("%.2f", f), ".", ",")
		}
	}
	currency = strings.ToUpper(strings.TrimSpace(currency))
	if sym, ok := currencySymbols[currency]; ok {
		currency = sym
	}
	return strings.TrimSpace(amount + " " + currency)
}

var junkHint = regexp.MustCompile(`\b(logo|icon|avatar|sprite|badge|emoji|pixel|spinner|loader|placeholder)`)

var junkPath = regexp.MustCompile(`logo|icon|sprite|avatar|pixel|spacer|blank\.|1x1|tracking|badge|emoji|gravatar|placeholder|spinner|loader|/ads?/|doubleclick|facebook\.com/tr`)

// junkImageURL écarte les images du corps de page qui ne sont manifestement
// pas un visuel : logos, icônes, pixels de suivi, SVG décoratifs…
func junkImageURL(u *url.URL) bool {
	p := strings.ToLower(u.Host + u.Path)
	switch path.Ext(strings.ToLower(u.Path)) {
	case ".svg", ".ico":
		return true
	}
	return junkPath.MatchString(p)
}

// imgSource retourne la meilleure source d'une <img>, chargement différé compris.
func imgSource(n *html.Node) string {
	if s := bestFromSrcset(firstAttr(n, "data-srcset", "data-lazy-srcset", "srcset")); s != "" {
		return s
	}
	for _, k := range []string{"data-src", "data-lazy-src", "data-original", "data-lazy", "data-url", "src"} {
		if v := strings.TrimSpace(attrVal(n, k)); v != "" && !strings.HasPrefix(v, "data:") {
			return v
		}
	}
	return ""
}

// bestFromSrcset choisit dans un srcset la plus grande variante raisonnable
// (≤ 1600 px de large), sinon la plus petite au-delà.
func bestFromSrcset(srcset string) string {
	best, bestW := "", -1
	over, overW := "", 0
	for _, part := range strings.Split(srcset, ",") {
		fields := strings.Fields(strings.TrimSpace(part))
		if len(fields) == 0 || strings.HasPrefix(fields[0], "data:") {
			continue
		}
		w := 0
		if len(fields) > 1 {
			d := fields[1]
			switch {
			case strings.HasSuffix(d, "w"):
				w, _ = strconv.Atoi(strings.TrimSuffix(d, "w"))
			case strings.HasSuffix(d, "x"):
				x, _ := strconv.ParseFloat(strings.TrimSuffix(d, "x"), 64)
				w = int(x * 600)
			}
		}
		if w > 1600 {
			if over == "" || w < overW {
				over, overW = fields[0], w
			}
			continue
		}
		if w > bestW {
			best, bestW = fields[0], w
		}
	}
	if best != "" {
		return best
	}
	return over
}

// stripSiteSuffix retire « | Nom du site » de la fin d'un titre.
func stripSiteSuffix(title, site string) string {
	if site == "" {
		return title
	}
	lt, ls := strings.ToLower(title), strings.ToLower(site)
	for _, sep := range []string{" | ", " - ", " – ", " — ", " · ", " : "} {
		if strings.HasSuffix(lt, strings.ToLower(sep)+ls) {
			if t := strings.TrimSpace(title[:len(title)-len(sep)-len(site)]); t != "" {
				return t
			}
		}
	}
	return title
}

// cleanText décode les entités résiduelles et normalise les espaces.
func cleanText(s string) string {
	return strings.Join(strings.Fields(html.UnescapeString(s)), " ")
}

func truncateRunes(s string, n int) string {
	if utf8.RuneCountInString(s) <= n {
		return s
	}
	r := []rune(s)
	return strings.TrimSpace(string(r[:n-1])) + "…"
}

func textContent(n *html.Node) string {
	var b strings.Builder
	var rec func(*html.Node)
	rec = func(n *html.Node) {
		if n.Type == html.TextNode {
			b.WriteString(n.Data)
			b.WriteByte(' ')
		}
		for c := n.FirstChild; c != nil; c = c.NextSibling {
			rec(c)
		}
	}
	rec(n)
	return cleanText(b.String())
}

func attrVal(n *html.Node, key string) string {
	for _, a := range n.Attr {
		if a.Key == key {
			return a.Val
		}
	}
	return ""
}

func firstAttr(n *html.Node, keys ...string) string {
	for _, k := range keys {
		if v := attrVal(n, k); v != "" {
			return v
		}
	}
	return ""
}

func atoiAttr(n *html.Node, key string) int {
	v, _ := strconv.Atoi(strings.TrimSuffix(strings.TrimSpace(attrVal(n, key)), "px"))
	return v
}

func firstNonEmpty(vals ...string) string {
	for _, v := range vals {
		if strings.TrimSpace(v) != "" {
			return v
		}
	}
	return ""
}

func appendUnique(list []string, v string) []string {
	for _, x := range list {
		if x == v {
			return list
		}
	}
	return append(list, v)
}
