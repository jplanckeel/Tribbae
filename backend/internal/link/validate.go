package link

import (
	"errors"
	"net/url"
	"strings"
	"unicode/utf8"
)

// ErrInvalidInput regroupe les erreurs de validation (→ codes.InvalidArgument).
var ErrInvalidInput = errors.New("invalid input")

// ErrNotFound est retourné quand un lien est absent ou invisible pour l'utilisateur.
var ErrNotFound = errors.New("link not found")

// ErrForbidden est retourné quand l'utilisateur ne peut pas modifier la ressource.
var ErrForbidden = errors.New("not authorized")

const (
	maxTitleLen       = 300
	maxDescriptionLen = 5000
	maxShortFieldLen  = 200
	maxURLLen         = 2048
	maxTags           = 30
	maxTagLen         = 50
	maxIngredients    = 100
	maxIngredientLen  = 200
)

func invalid(msg string) error {
	return errors.Join(ErrInvalidInput, errors.New(msg))
}

// normalizeWebURL accepte une URL vide, ajoute https:// si le schéma manque et
// refuse tout schéma autre que http(s) (javascript:, data:, file:…).
func normalizeWebURL(raw string) (string, error) {
	s := strings.TrimSpace(raw)
	if s == "" {
		return "", nil
	}
	if len(s) > maxURLLen {
		return "", invalid("url is too long")
	}
	if !strings.Contains(s, "://") && !strings.Contains(strings.SplitN(s, "/", 2)[0], ":") {
		s = "https://" + s
	}
	u, err := url.Parse(s)
	if err != nil || u.Host == "" || (u.Scheme != "http" && u.Scheme != "https") {
		return "", invalid("url must be an http(s) address")
	}
	return u.String(), nil
}

// normalizeImageURL accepte http(s) et file:// (image locale de l'app mobile,
// jamais affichée par le web), et refuse les schémas exécutables.
func normalizeImageURL(raw string) (string, error) {
	s := strings.TrimSpace(raw)
	if s == "" {
		return "", nil
	}
	if len(s) > maxURLLen {
		return "", invalid("imageUrl is too long")
	}
	u, err := url.Parse(s)
	if err != nil {
		return "", invalid("imageUrl is invalid")
	}
	switch u.Scheme {
	case "http", "https":
		if u.Host == "" {
			return "", invalid("imageUrl is invalid")
		}
	case "file", "content":
	default:
		return "", invalid("imageUrl must be an http(s) address")
	}
	return s, nil
}

func checkLen(field, v string, max int) error {
	if utf8.RuneCountInString(v) > max {
		return invalid(field + " is too long")
	}
	return nil
}

func cleanList(field string, in []string, maxItems, maxLen int) ([]string, error) {
	out := make([]string, 0, len(in))
	for _, v := range in {
		v = strings.TrimSpace(v)
		if v == "" {
			continue
		}
		if utf8.RuneCountInString(v) > maxLen {
			return nil, invalid(field + " entry is too long")
		}
		out = append(out, v)
	}
	if len(out) > maxItems {
		return nil, invalid("too many " + field)
	}
	return out, nil
}

// sanitize valide et normalise les champs saisis par l'utilisateur.
func (l *Link) sanitize() error {
	var err error
	l.Title = strings.TrimSpace(l.Title)
	if err = checkLen("title", l.Title, maxTitleLen); err != nil {
		return err
	}
	if err = checkLen("description", l.Description, maxDescriptionLen); err != nil {
		return err
	}
	for name, v := range map[string]string{"ageRange": l.AgeRange, "location": l.Location, "price": l.Price} {
		if err = checkLen(name, v, maxShortFieldLen); err != nil {
			return err
		}
	}
	if l.URL, err = normalizeWebURL(l.URL); err != nil {
		return err
	}
	if l.ImageURL, err = normalizeImageURL(l.ImageURL); err != nil {
		return err
	}
	if l.Tags, err = cleanList("tags", l.Tags, maxTags, maxTagLen); err != nil {
		return err
	}
	if l.Ingredients, err = cleanList("ingredients", l.Ingredients, maxIngredients, maxIngredientLen); err != nil {
		return err
	}
	if l.Rating < 0 || l.Rating > 5 {
		return invalid("rating must be between 0 and 5")
	}
	switch l.Visibility {
	case "", "private", "public":
	default:
		return invalid("visibility must be private or public")
	}
	return nil
}
