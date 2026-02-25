package link

import (
	"fmt"
	"net/http"
	"strings"
	"time"

	"golang.org/x/net/html"
)

type OGMeta struct {
	Title       string `json:"title"`
	Description string `json:"description"`
	Image       string `json:"image"`
}

var httpClient = &http.Client{Timeout: 8 * time.Second}

func scrapeOG(rawURL string) (*OGMeta, error) {
	req, err := http.NewRequest("GET", rawURL, nil)
	if err != nil {
		return nil, err
	}
	req.Header.Set("User-Agent", "Mozilla/5.0 (compatible; Tribbae/1.0)")
	resp, err := httpClient.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	doc, err := html.Parse(resp.Body)
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
	return meta, nil
}

func attrVal(n *html.Node, key string) string {
	for _, a := range n.Attr {
		if a.Key == key {
			return a.Val
		}
	}
	return ""
}

// PreviewHandler retourne un http.HandlerFunc pour le endpoint /v1/links/preview
func PreviewHandler() func(http.ResponseWriter, *http.Request) {
	return func(w http.ResponseWriter, r *http.Request) {
		rawURL := r.URL.Query().Get("url")
		if rawURL == "" {
			http.Error(w, `{"message":"url required"}`, http.StatusBadRequest)
			return
		}
		meta, err := scrapeOG(rawURL)
		if err != nil {
			http.Error(w, `{"message":"scrape failed"}`, http.StatusBadGateway)
			return
		}
		w.Header().Set("Content-Type", "application/json")
		fmt.Fprintf(w, `{"title":%q,"description":%q,"image":%q}`,
			meta.Title, meta.Description, meta.Image)
	}
}
