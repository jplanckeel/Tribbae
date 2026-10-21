package folder

import (
	"errors"
	"testing"
)

func TestSanitizeStyle(t *testing.T) {
	st, err := sanitizeStyle(Style{
		Description: "  Pour ses 10 ans ",
		Cover:       "illu", Illustration: "anniv",
		KeyDate:  1_800_000_000_000,
		ChildIDs: []string{"a", " a ", "", "b"},
	})
	if err != nil {
		t.Fatal(err)
	}
	if st.Description != "Pour ses 10 ans" || st.KeyDateType != "event" || len(st.ChildIDs) != 2 {
		t.Fatalf("unexpected style %+v", st)
	}

	// Sans date, le type de date n'a pas de sens
	if st, _ := sanitizeStyle(Style{KeyDateType: "anniv"}); st.KeyDateType != "" {
		t.Fatalf("keyDateType kept without keyDate: %q", st.KeyDateType)
	}

	for _, bad := range []Style{{Cover: "neon"}, {Illustration: "lune"}, {ViewMode: "kanban"}, {Sort: "random"}, {KeyDate: -1}} {
		if _, err := sanitizeStyle(bad); !errors.Is(err, ErrInvalidInput) {
			t.Fatalf("%+v: expected ErrInvalidInput, got %v", bad, err)
		}
	}
}

func TestStylePatchKeepsOmittedFields(t *testing.T) {
	cover, pinned := "pois", false
	got := StylePatch{Cover: &cover, Pinned: &pinned}.apply(Style{
		Description: "avec tata Inès", Cover: "illu", ChildIDs: []string{"leo"}, Pinned: true,
	})
	if got.Cover != "pois" || got.Pinned || got.Description != "avec tata Inès" || len(got.ChildIDs) != 1 {
		t.Fatalf("unexpected patch result %+v", got)
	}
	none := []string{}
	if got := (StylePatch{ChildIDs: &none}).apply(Style{ChildIDs: []string{"leo"}}); len(got.ChildIDs) != 0 {
		t.Fatalf("childIds not cleared: %v", got.ChildIDs)
	}
}
