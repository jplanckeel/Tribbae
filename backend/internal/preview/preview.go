// Package preview construit l'aperçu d'une page web (titre, description,
// images candidates, données schema.org) pour pré-remplir une idée.
package preview

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"mime"
	"net/http"
	"net/url"
	"regexp"
	"strings"
	"sync"
	"time"

	"github.com/tribbae/backend/internal/netsafe"
	"golang.org/x/net/html/charset"
)

// maxPageBytes borne la taille de page lue pour l'extraction des métadonnées.
const maxPageBytes = 2 << 20

// previewBudget borne la durée totale d'un aperçu, tentatives comprises ;
// attemptBudget borne chaque tentative, pour qu'un site qui fait traîner un
// User-Agent laisse le temps d'essayer le suivant.
const (
	previewBudget = 12 * time.Second
	attemptBudget = 6 * time.Second
)

// attempt est une façon de demander la page : un client HTTP et ses en-têtes.
type attempt struct {
	name    string
	client  *http.Client
	headers map[string]string
}

var (
	// Client « navigateur » : empreinte TLS de Chrome (cf. netsafe.NewBrowserClient).
	// Sans elle, des pare-feu comme celui de Vertbaudet ou d'Etsy refusent la
	// connexion quel que soit le User-Agent.
	browserClient = netsafe.NewBrowserClient(8 * time.Second)

	robotHeaders = func(ua string) map[string]string {
		return map[string]string{
			"User-Agent":      ua,
			"Accept":          "text/html,application/xhtml+xml,application/xml;q=0.9,image/*;q=0.8,*/*;q=0.5",
			"Accept-Language": "fr-FR,fr;q=0.9,en;q=0.6",
		}
	}

	// attempts sont essayées dans l'ordre tant que l'aperçu reste incomplet.
	// Beaucoup de sites (e-commerce, réseaux sociaux, pages derrière un anti-bot
	// type Datadome) ne servent leurs balises Open Graph qu'aux robots d'aperçu
	// des messageries, qu'ils laissent passer.
	attempts = []attempt{
		{"chrome", browserClient, netsafe.ChromeHeaders},
		{"whatsapp", httpClient, robotHeaders("WhatsApp/2.24.20.0")},
		{"facebook", httpClient, robotHeaders("facebookexternalhit/1.1 (+http://www.facebook.com/externalhit_uatext.php)")},
	}
)

var (
	httpClient   = netsafe.NewClient(8 * time.Second)
	previewCache = newMetaCache(30*time.Minute, 512)
)

var errHTTPStatus = errors.New("unexpected http status")

// Fetch construit l'aperçu d'une page. Les résultats sont mis en cache :
// le formulaire peut redemander l'aperçu d'un lien déjà lu.
func Fetch(ctx context.Context, rawURL string) (*Meta, error) {
	key := strings.TrimSpace(rawURL)
	if m, ok := previewCache.get(key); ok {
		return m, nil
	}
	ctx, cancel := context.WithTimeout(ctx, previewBudget)
	defer cancel()

	// Plateformes dont les pages sont lourdes ou rendues en JavaScript : leur
	// oEmbed donne directement le vrai titre et la miniature.
	if endpoint := knownOEmbed(key); endpoint != "" {
		if meta := fromOEmbed(ctx, endpoint, key); meta != nil {
			previewCache.put(key, meta)
			return meta, nil
		}
	}

	var meta *Meta
	var err error
	for _, a := range attempts {
		m, fetchErr := fetchMeta(ctx, key, a)
		if fetchErr != nil {
			if meta == nil {
				err = fetchErr
			}
			if ctx.Err() != nil {
				break
			}
			continue
		}
		meta, err = mergeMeta(meta, m, key), nil
		if quality(meta, key) == qualityFull {
			break
		}
	}

	// oEmbed déclaré par la page, si elle n'a donné ni image ni vrai titre
	if meta != nil && meta.oembed != "" && (len(meta.Images) == 0 || genericTitle(meta, key)) {
		if o := fromOEmbed(ctx, meta.oembed, key); o != nil {
			if !genericTitle(o, key) && genericTitle(meta, key) {
				meta.Title = o.Title
			}
			meta.SiteName = firstNonEmpty(meta.SiteName, o.SiteName)
			meta.Images = append(o.Images, meta.Images...)
		}
	}
	if id := youTubeID(key); id != "" {
		// oEmbed indisponible : la miniature existe quand même
		if meta == nil {
			meta, err = &Meta{}, nil
		}
		meta.Images = append([]string{"https://i.ytimg.com/vi/" + id + "/hqdefault.jpg"}, meta.Images...)
	}
	if meta == nil {
		if err == nil {
			err = errHTTPStatus
		}
		return nil, err
	}
	dedupe := meta.Images[:0:0]
	for _, img := range meta.Images {
		dedupe = appendUnique(dedupe, img)
	}
	meta.Images = dedupe
	finalizeImages(meta)
	previewCache.put(key, meta)
	return meta, nil
}

const (
	qualityNone = iota
	qualityImage
	qualityTitle
	qualityFull
)

// quality note un aperçu : un vrai titre compte plus qu'une image.
func quality(m *Meta, page string) int {
	q := qualityNone
	if m == nil {
		return q
	}
	if !genericTitle(m, page) {
		q += qualityTitle
	}
	if m.Image != "" {
		q += qualityImage
	}
	return q
}

// genericTitle détecte un titre absent ou réduit au nom du site (« Amazon.fr »,
// « Instagram ») : signe d'une page anti-bot ou d'une coquille JavaScript.
func genericTitle(m *Meta, page string) bool {
	t := strings.ToLower(strings.TrimSpace(m.Title))
	if t == "" || strings.EqualFold(t, m.SiteName) {
		return true
	}
	u, err := url.Parse(page)
	if err != nil {
		return false
	}
	host := strings.TrimPrefix(strings.ToLower(u.Hostname()), "www.")
	label, _, _ := strings.Cut(host, ".")
	return t == host || t == label || strings.HasPrefix(t, label+" - ") || strings.Contains(t, "just a moment")
}

// knownOEmbed retourne l'endpoint oEmbed des plateformes connues, ou "".
func knownOEmbed(page string) string {
	u, err := url.Parse(page)
	if err != nil {
		return ""
	}
	host := strings.TrimPrefix(strings.ToLower(u.Hostname()), "www.")
	host = strings.TrimPrefix(host, "m.")
	q := url.QueryEscape(page)
	switch {
	case youTubeID(page) != "":
		return "https://www.youtube.com/oembed?format=json&url=" + q
	case host == "vimeo.com":
		return "https://vimeo.com/api/oembed.json?url=" + q
	case host == "tiktok.com" || strings.HasSuffix(host, ".tiktok.com"):
		return "https://www.tiktok.com/oembed?url=" + q
	case host == "dailymotion.com" || host == "dai.ly":
		return "https://www.dailymotion.com/services/oembed?format=json&url=" + q
	case strings.HasPrefix(host, "pinterest.") || strings.Contains(host, ".pinterest."):
		// L'oEmbed Pinterest n'accepte que le domaine .com
		u.Host = "www.pinterest.com"
		return "https://www.pinterest.com/oembed.json?url=" + url.QueryEscape(u.String())
	}
	return ""
}

// Image retourne la meilleure image d'une page, ou "" (erreurs ignorées).
func Image(ctx context.Context, rawURL string) string {
	m, err := Fetch(ctx, rawURL)
	if err != nil {
		return ""
	}
	return m.Image
}

// fetchMeta télécharge la page selon la tentative donnée et en extrait l'aperçu.
func fetchMeta(ctx context.Context, rawURL string, a attempt) (*Meta, error) {
	ctx, cancel := context.WithTimeout(ctx, attemptBudget)
	defer cancel()
	resp, err := netsafe.GetWithHeaders(ctx, a.client, rawURL, a.headers)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()
	if resp.StatusCode >= 400 {
		return nil, fmt.Errorf("%w: %d", errHTTPStatus, resp.StatusCode)
	}

	final := resp.Request.URL
	ct := resp.Header.Get("Content-Type")
	mediaType, _, _ := mime.ParseMediaType(ct)
	switch {
	case strings.HasPrefix(mediaType, "image/"):
		// Lien direct vers une image : c'est l'image de l'idée
		return &Meta{Image: final.String(), Images: []string{final.String()}}, nil
	case mediaType != "" && !strings.Contains(mediaType, "html") && !strings.Contains(mediaType, "xml"):
		return &Meta{Images: []string{}}, nil
	}

	body := io.LimitReader(resp.Body, maxPageBytes)
	// Pages en ISO-8859-1 / Windows-1252 : on décode en UTF-8 avant de parser
	r, err := charset.NewReader(body, ct)
	if err != nil {
		r = body
	}
	return extractMeta(r, final)
}

// mergeMeta combine deux tentatives : la meilleure sert de base, l'autre
// complète les champs manquants et ajoute ses images à la suite.
func mergeMeta(a, b *Meta, page string) *Meta {
	if a == nil {
		return b
	}
	if quality(b, page) > quality(a, page) {
		a, b = b, a
	}
	if genericTitle(a, page) && !genericTitle(b, page) {
		a.Title = b.Title
	}
	a.Title = firstNonEmpty(a.Title, b.Title)
	a.Description = firstNonEmpty(a.Description, b.Description)
	a.SiteName = firstNonEmpty(a.SiteName, b.SiteName)
	a.Category = firstNonEmpty(a.Category, b.Category)
	a.Price = firstNonEmpty(a.Price, b.Price)
	a.Location = firstNonEmpty(a.Location, b.Location)
	a.oembed = firstNonEmpty(a.oembed, b.oembed)
	if len(a.Ingredients) == 0 {
		a.Ingredients = b.Ingredients
	}
	if a.EventDate == 0 {
		a.EventDate = b.EventDate
	}
	for _, img := range b.Images {
		a.Images = appendUnique(a.Images, img)
	}
	finalizeImages(a)
	return a
}

type oEmbed struct {
	Title        string `json:"title"`
	ProviderName string `json:"provider_name"`
	ThumbnailURL string `json:"thumbnail_url"`
}

func fetchOEmbed(ctx context.Context, endpoint string) (*oEmbed, error) {
	resp, err := netsafe.GetWithHeaders(ctx, httpClient, endpoint, map[string]string{
		"User-Agent": netsafe.ChromeHeaders["User-Agent"],
		"Accept":     "application/json",
	})
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("%w: %d", errHTTPStatus, resp.StatusCode)
	}
	var o oEmbed
	if err := json.NewDecoder(io.LimitReader(resp.Body, 256<<10)).Decode(&o); err != nil {
		return nil, err
	}
	return &o, nil
}

var youTubeIDRe = regexp.MustCompile(`^[A-Za-z0-9_-]{11}$`)

// youTubeID extrait l'identifiant d'une vidéo YouTube, ou "".
func youTubeID(raw string) string {
	u, err := url.Parse(raw)
	if err != nil {
		return ""
	}
	host := strings.TrimPrefix(strings.ToLower(u.Hostname()), "www.")
	host = strings.TrimPrefix(host, "m.")
	var id string
	switch host {
	case "youtu.be":
		id = strings.Trim(u.Path, "/")
	case "youtube.com", "music.youtube.com":
		if u.Path == "/watch" {
			id = u.Query().Get("v")
		} else if parts := strings.Split(strings.Trim(u.Path, "/"), "/"); len(parts) == 2 &&
			(parts[0] == "shorts" || parts[0] == "embed" || parts[0] == "live") {
			id = parts[1]
		}
	}
	if !youTubeIDRe.MatchString(id) {
		return ""
	}
	return id
}

// fromOEmbed construit un aperçu depuis un endpoint oEmbed, ou nil.
func fromOEmbed(ctx context.Context, endpoint, page string) *Meta {
	o, err := fetchOEmbed(ctx, endpoint)
	if err != nil {
		return nil
	}
	m := &Meta{
		Title:    truncateRunes(cleanText(o.Title), maxTitleRunes),
		SiteName: cleanText(o.ProviderName),
		Images:   []string{},
	}
	if id := youTubeID(page); id != "" {
		// hqdefault existe toujours, contrairement à maxresdefault
		m.Images = append(m.Images, "https://i.ytimg.com/vi/"+id+"/hqdefault.jpg")
	}
	if u, err := netsafe.ValidateURL(o.ThumbnailURL); err == nil {
		m.Images = appendUnique(m.Images, u.String())
	}
	if m.Title == "" && len(m.Images) == 0 {
		return nil
	}
	finalizeImages(m)
	return m
}

// ── Cache ──────────────────────────────────────────────────────────────

type cachedMeta struct {
	meta    *Meta
	expires time.Time
}

type metaCache struct {
	mu    sync.Mutex
	ttl   time.Duration
	max   int
	items map[string]cachedMeta
}

func newMetaCache(ttl time.Duration, max int) *metaCache {
	return &metaCache{ttl: ttl, max: max, items: map[string]cachedMeta{}}
}

func (c *metaCache) get(key string) (*Meta, bool) {
	c.mu.Lock()
	defer c.mu.Unlock()
	it, ok := c.items[key]
	if !ok || time.Now().After(it.expires) {
		return nil, false
	}
	return it.meta, true
}

func (c *metaCache) put(key string, m *Meta) {
	c.mu.Lock()
	defer c.mu.Unlock()
	now := time.Now()
	if len(c.items) >= c.max {
		// Purge des entrées expirées, puis d'une entrée quelconque si besoin
		for k, it := range c.items {
			if now.After(it.expires) {
				delete(c.items, k)
			}
		}
		for k := range c.items {
			if len(c.items) < c.max {
				break
			}
			delete(c.items, k)
		}
	}
	c.items[key] = cachedMeta{meta: m, expires: now.Add(c.ttl)}
}

// ── Handler ────────────────────────────────────────────────────────────

// Handler retourne le handler de /v1/links/preview. Le endpoint exige
// un utilisateur authentifié : il déclenche une requête sortante vers une URL
// arbitraire, on ne l'ouvre donc pas aux anonymes.
func Handler(authenticate func(r *http.Request) (string, error)) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		if userID, err := authenticate(r); err != nil || userID == "" {
			writeJSONError(w, http.StatusUnauthorized, "authentication required")
			return
		}
		rawURL := r.URL.Query().Get("url")
		if _, err := netsafe.ValidateURL(rawURL); err != nil {
			writeJSONError(w, http.StatusBadRequest, "a valid http(s) url is required")
			return
		}
		meta, err := Fetch(r.Context(), rawURL)
		if err != nil {
			writeJSONError(w, http.StatusBadGateway, "scrape failed")
			return
		}
		_ = json.NewEncoder(w).Encode(meta)
	}
}

func writeJSONError(w http.ResponseWriter, code int, msg string) {
	w.WriteHeader(code)
	_ = json.NewEncoder(w).Encode(map[string]string{"message": msg})
}
