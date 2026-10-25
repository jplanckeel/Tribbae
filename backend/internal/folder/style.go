package folder

import (
	"context"
	"slices"
	"strings"
	"unicode/utf8"

	"go.mongodb.org/mongo-driver/v2/bson"
)

// Style regroupe la personnalisation d'un dossier (écran « Personnaliser ») :
// couverture, petit mot, date clé, enfants concernés et affichage des idées.
type Style struct {
	Description  string   `bson:"description,omitempty"`
	Cover        string   `bson:"cover,omitempty"`        // "" = automatique
	Illustration string   `bson:"illustration,omitempty"` // "" = choisie selon le nom / la date clé
	KeyDate      int64    `bson:"key_date,omitempty"`     // ms
	KeyDateType  string   `bson:"key_date_type,omitempty"`
	ChildIDs     []string `bson:"child_ids,omitempty"`
	ViewMode     string   `bson:"view_mode,omitempty"`
	Sort         string   `bson:"sort,omitempty"`
	Pinned       bool     `bson:"pinned,omitempty"`
}

// StylePatch : champs de Style à modifier (nil = inchangé), pour qu'un client
// qui ne connaît pas encore la personnalisation ne l'efface pas.
type StylePatch struct {
	Description  *string
	Cover        *string
	Illustration *string
	KeyDate      *int64
	KeyDateType  *string
	ChildIDs     *[]string
	ViewMode     *string
	Sort         *string
	Pinned       *bool
}

var (
	covers        = []string{"", "illu", "uni", "tipis", "pois", "rayures", "photo"}
	illustrations = []string{"", "anniv", "cadeaux", "noel", "jouets", "montagne", "plage", "gouter", "pluie", "automne"}
	keyDateTypes  = []string{"", "anniv", "vacances", "event"}
	viewModes     = []string{"", "grid", "list", "mosaic"}
	sorts         = []string{"", "recent", "date", "category"}
)

func (p StylePatch) apply(st Style) Style {
	set := func(dst *string, src *string) {
		if src != nil {
			*dst = *src
		}
	}
	set(&st.Description, p.Description)
	set(&st.Cover, p.Cover)
	set(&st.Illustration, p.Illustration)
	set(&st.KeyDateType, p.KeyDateType)
	set(&st.ViewMode, p.ViewMode)
	set(&st.Sort, p.Sort)
	if p.KeyDate != nil {
		st.KeyDate = *p.KeyDate
	}
	if p.ChildIDs != nil {
		st.ChildIDs = *p.ChildIDs
	}
	if p.Pinned != nil {
		st.Pinned = *p.Pinned
	}
	return st
}

// sanitizeStyle valide la personnalisation ; les valeurs inconnues sont refusées.
func sanitizeStyle(st Style) (Style, error) {
	st.Description = strings.TrimSpace(st.Description)
	if utf8.RuneCountInString(st.Description) > 140 {
		return st, invalid("description is too long")
	}
	for _, c := range []struct {
		v       string
		allowed []string
		field   string
	}{
		{st.Cover, covers, "cover"},
		{st.Illustration, illustrations, "illustration"},
		{st.KeyDateType, keyDateTypes, "keyDateType"},
		{st.ViewMode, viewModes, "viewMode"},
		{st.Sort, sorts, "sort"},
	} {
		if !slices.Contains(c.allowed, c.v) {
			return st, invalid("unknown " + c.field)
		}
	}
	if st.KeyDate < 0 {
		return st, invalid("keyDate must be positive")
	}
	if st.KeyDate == 0 {
		st.KeyDateType = ""
	} else if st.KeyDateType == "" {
		st.KeyDateType = "event"
	}
	ids := make([]string, 0, len(st.ChildIDs))
	for _, id := range st.ChildIDs {
		id = strings.TrimSpace(id)
		if id == "" || slices.Contains(ids, id) {
			continue
		}
		if len(id) > 64 {
			return st, invalid("childId is too long")
		}
		ids = append(ids, id)
	}
	if len(ids) > 20 {
		return st, invalid("too many children")
	}
	st.ChildIDs = ids
	return st, nil
}

func (st Style) setDoc() bson.M {
	return bson.M{
		"description": st.Description, "cover": st.Cover, "illustration": st.Illustration,
		"key_date": st.KeyDate, "key_date_type": st.KeyDateType, "child_ids": st.ChildIDs,
		"view_mode": st.ViewMode, "sort": st.Sort, "pinned": st.Pinned,
	}
}

// IsPeer indique si viewerID fait partie de la tribu du propriétaire ownerID :
// lui seul voit les données familiales du dossier (date clé, enfants).
func (s *Service) IsPeer(ctx context.Context, viewerID, ownerID string) bool {
	if viewerID == "" {
		return false
	}
	if viewerID == ownerID {
		return true
	}
	return s.peers != nil && slices.Contains(s.peers(ctx, viewerID), ownerID)
}
