package mcpserver

import (
	"errors"
	"net"
	"strings"
	"time"
	"unicode"
	"unicode/utf8"

	"github.com/tribbae/backend/internal/netsafe"
)

// Limites plus strictes que celles de l'app : un agent n'a aucune raison
// d'écrire un roman dans une idée.
const (
	maxTitleLen       = 200
	maxDescriptionLen = 2000
	maxShortLen       = 100
	maxTags           = 10
	maxTagLen         = 40
	maxURLLen         = 2048
	maxQueryLen       = 100
)

// categories : valeur de l'outil → valeur stockée (enum de link.proto).
var categories = map[string]string{
	"idee":     "LINK_CATEGORY_IDEE",
	"cadeau":   "LINK_CATEGORY_CADEAU",
	"activite": "LINK_CATEGORY_ACTIVITE",
	"sortie":   "LINK_CATEGORY_EVENEMENT",
	"recette":  "LINK_CATEGORY_RECETTE",
	"livre":    "LINK_CATEGORY_LIVRE",
	"deco":     "LINK_CATEGORY_DECORATION",
}

// categoryName : valeur stockée → valeur de l'outil.
func categoryName(stored string) string {
	for name, v := range categories {
		if v == stored {
			return name
		}
	}
	return ""
}

func invalid(msg string) error { return errors.New(msg) }

// cleanText retire les caractères de contrôle et de mise en forme invisibles
// (inversion de sens d'écriture…) ; multiline garde les retours à la ligne.
func cleanText(s string, multiline bool) string {
	s = strings.Map(func(r rune) rune {
		switch {
		case multiline && r == '\n':
			return r
		case r == '\t', unicode.IsControl(r), unicode.Is(unicode.Cf, r):
			return ' '
		}
		return r
	}, s)
	if !multiline {
		return strings.Join(strings.Fields(s), " ")
	}
	return strings.TrimSpace(s)
}

func checkLen(field, v string, max int) error {
	if utf8.RuneCountInString(v) > max {
		return invalid(field + " is too long")
	}
	return nil
}

// validURL n'accepte qu'une adresse https absolue vers un hôte public, sans
// identifiants.
func validURL(raw string) (string, error) {
	raw = strings.TrimSpace(raw)
	if raw == "" {
		return "", invalid("url is required")
	}
	if len(raw) > maxURLLen {
		return "", invalid("url is too long")
	}
	u, err := netsafe.ValidateURL(raw)
	if err != nil || u.Scheme != "https" {
		return "", invalid("url must be an absolute https URL")
	}
	if u.User != nil {
		return "", invalid("url must not contain credentials")
	}
	host := strings.ToLower(u.Hostname())
	if ip := net.ParseIP(host); ip != nil {
		if !netsafe.IsPublicIP(ip) {
			return "", invalid("url must point to a public host")
		}
	} else if !strings.Contains(host, ".") || host == "localhost" || strings.HasSuffix(host, ".localhost") ||
		strings.HasSuffix(host, ".local") || strings.HasSuffix(host, ".internal") {
		return "", invalid("url must point to a public host")
	}
	return u.String(), nil
}

var paris = func() *time.Location {
	if loc, err := time.LoadLocation("Europe/Paris"); err == nil {
		return loc
	}
	return time.UTC
}()

// parseEventDate lit « AAAA-MM-JJ » ou « AAAA-MM-JJTHH:MM » (heure de Paris)
// et la retourne en millisecondes, comme les clients. La date doit être
// plausible : entre un an avant et trois ans après aujourd'hui.
func parseEventDate(raw string, now time.Time) (int64, error) {
	raw = strings.TrimSpace(raw)
	if raw == "" {
		return 0, nil
	}
	var t time.Time
	var err error
	for _, layout := range []string{"2006-01-02", "2006-01-02T15:04"} {
		if t, err = time.ParseInLocation(layout, raw, paris); err == nil {
			break
		}
	}
	if err != nil {
		return 0, invalid("event_date must be YYYY-MM-DD or YYYY-MM-DDTHH:MM")
	}
	if t.Before(now.AddDate(-1, 0, 0)) || t.After(now.AddDate(3, 0, 0)) {
		return 0, invalid("event_date must be within one year in the past and three years in the future")
	}
	return t.UnixMilli(), nil
}

func cleanTags(in []string) ([]string, error) {
	out := []string{}
	for _, t := range in {
		t = cleanText(t, false)
		if t == "" {
			continue
		}
		if err := checkLen("tag", t, maxTagLen); err != nil {
			return nil, err
		}
		dup := false
		for _, o := range out {
			if strings.EqualFold(o, t) {
				dup = true
			}
		}
		if !dup {
			out = append(out, t)
		}
	}
	if len(out) > maxTags {
		return nil, invalid("at most 10 tags are allowed")
	}
	return out, nil
}
