package link

import (
	"context"
	"encoding/json"
	"io"
	"net/http"
	"net/url"
	"strings"
	"time"

	"github.com/tribbae/backend/internal/netsafe"
	"golang.org/x/net/html"
)

type OGMeta struct {
	Title       string `json:"title"`
	Description string `json:"description"`
	Image       string `json:"image"`
}

// maxPageBytes borne la taille de page lue pour l'extraction des métadonnées.
const maxPageBytes = 2 << 20

var httpClient = netsafe.NewClient(8 * time.Second)

func scrapeOG(ctx context.Context, rawURL string) (*OGMeta, error) {
	resp, err := netsafe.Get(ctx, httpClient, rawURL)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	doc, err := html.Parse(io.LimitReader(resp.Body, maxPageBytes))
	if err != nil {
		return nil, err
	}

	meta := &OGMeta{}
	var walk func(*html.Node)
	walk = func(n *html.Node) {
		if n.Type == html.ElementNode && n.Data == "meta" {
			prop, content := attrVal(n, "property"), attrVal(n, "content")
			name := attrVal(n, "name")
			switch prop {
			case "og:title":
				if meta.Title == "" {
					meta.Title = content
				}
			case "og:description":
				if meta.Description == "" {
					meta.Description = content
				}
			case "og:image":
				if meta.Image == "" {
					meta.Image = content
				}
			}
			// fallback sur <meta name="description">
			if name == "description" && meta.Description == "" {
				meta.Description = content
			}
		}
		// fallback <title>
		if n.Type == html.ElementNode && n.Data == "title" && meta.Title == "" {
			if n.FirstChild != nil {
				meta.Title = strings.TrimSpace(n.FirstChild.Data)
			}
		}
		for c := n.FirstChild; c != nil; c = c.NextSibling {
			walk(c)
		}
	}
	walk(doc)
	meta.Image = absoluteImageURL(resp.Request.URL, meta.Image)
	return meta, nil
}

// absoluteImageURL résout une og:image relative par rapport à la page finale
// et écarte tout ce qui n'est pas http(s).
func absoluteImageURL(page *url.URL, img string) string {
	img = strings.TrimSpace(img)
	if img == "" || page == nil {
		return ""
	}
	ref, err := url.Parse(img)
	if err != nil {
		return ""
	}
	abs := page.ResolveReference(ref)
	if abs.Scheme != "http" && abs.Scheme != "https" {
		return ""
	}
	return abs.String()
}

func attrVal(n *html.Node, key string) string {
	for _, a := range n.Attr {
		if a.Key == key {
			return a.Val
		}
	}
	return ""
}

// PreviewHandler retourne le handler de /v1/links/preview. Le endpoint exige
// un utilisateur authentifié : il déclenche une requête sortante vers une URL
// arbitraire, on ne l'ouvre donc pas aux anonymes.
func PreviewHandler(authenticate func(r *http.Request) (string, error)) http.HandlerFunc {
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
		meta, err := scrapeOG(r.Context(), rawURL)
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
