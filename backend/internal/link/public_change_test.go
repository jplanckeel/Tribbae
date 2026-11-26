package link

import (
	"context"
	"testing"
)

// Les moteurs de recherche sont prévenus quand une page publique d'idée change :
// publiée, modifiée, rendue privée, supprimée. Une idée privée ne dit rien.
func TestPublicChangeNotifications(t *testing.T) {
	_, db, cleanup := setupTestDB(t)
	defer cleanup()
	ctx := context.Background()
	svc := NewService(db.Collection("links"), db.Collection("folders"))
	// Compte à l'adresse confirmée (publier l'exige)
	svc.SetEmailCheck(func(context.Context, string) error { return nil })
	var got []string
	svc.SetPublicChange(func(paths ...string) { got = append(got, paths...) })

	private, err := svc.Create(ctx, "u1", &Link{Title: "Privée", Category: "LINK_CATEGORY_ACTIVITE"})
	if err != nil {
		t.Fatal(err)
	}
	public, err := svc.Create(ctx, "u1", &Link{Title: "Publique", Category: "LINK_CATEGORY_ACTIVITE", Visibility: "public"})
	if err != nil {
		t.Fatal(err)
	}
	path := "/links/" + public.ID.Hex()
	if len(got) != 1 || got[0] != path {
		t.Fatalf("création : %v", got)
	}
	if _, err := svc.Update(ctx, public.ID.Hex(), "u1", &Link{Title: "Rendue privée", Category: "LINK_CATEGORY_ACTIVITE", Visibility: "private"}); err != nil {
		t.Fatal(err)
	}
	if _, err := svc.Update(ctx, private.ID.Hex(), "u1", &Link{Title: "Toujours privée", Category: "LINK_CATEGORY_ACTIVITE", Visibility: "private"}); err != nil {
		t.Fatal(err)
	}
	if err := svc.Delete(ctx, private.ID.Hex(), "u1"); err != nil {
		t.Fatal(err)
	}
	if len(got) != 2 || got[1] != path {
		t.Errorf("rendue privée seulement : %v", got)
	}
}
