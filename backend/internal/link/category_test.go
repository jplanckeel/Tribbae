package link

import (
	"context"
	"testing"
	"time"

	"go.mongodb.org/mongo-driver/v2/bson"
)

// Catégorie « Idée » fusionnée dans « Activité ».

func TestNormalizeCategory(t *testing.T) {
	for in, want := range map[string]string{
		"LINK_CATEGORY_IDEE":        ActivityCategory,
		"LINK_CATEGORY_UNSPECIFIED": ActivityCategory,
		"":                          ActivityCategory,
		"LINK_CATEGORY_ACTIVITE":    ActivityCategory,
		"LINK_CATEGORY_RECETTE":     "LINK_CATEGORY_RECETTE",
	} {
		if got := normalizeCategory(in); got != want {
			t.Errorf("normalizeCategory(%q) = %q, want %q", in, got, want)
		}
	}
}

// Une ancienne version de l'app envoie encore « Idée » : rangée dans Activité.
func TestLegacyIdeaCategoryIsSavedAsActivity(t *testing.T) {
	_, db, cleanup := setupTestDB(t)
	defer cleanup()
	ctx := context.Background()
	svc := NewService(db.Collection("links"), db.Collection("folders"))

	l, err := svc.Create(ctx, "alice", &Link{Title: "Escape game", Category: legacyIdeaCategory})
	if err != nil {
		t.Fatalf("create: %v", err)
	}
	if l.Category != ActivityCategory {
		t.Errorf("create : catégorie %q, want %q", l.Category, ActivityCategory)
	}
	other, _ := svc.Create(ctx, "alice", &Link{Title: "Gâteau", Category: "LINK_CATEGORY_RECETTE"})
	updated, err := svc.Update(ctx, other.ID.Hex(), "alice", &Link{Title: "Gâteau", Category: legacyIdeaCategory})
	if err != nil {
		t.Fatalf("update: %v", err)
	}
	if updated.Category != ActivityCategory {
		t.Errorf("update : catégorie %q, want %q", updated.Category, ActivityCategory)
	}
}

func TestMigrateIdeaCategory(t *testing.T) {
	_, db, cleanup := setupTestDB(t)
	defer cleanup()
	ctx := context.Background()
	svc := NewService(db.Collection("links"), db.Collection("folders"))

	old := time.Date(2026, 1, 2, 3, 4, 5, 0, time.UTC)
	docs := map[string]bson.M{
		"idee":     {"category": legacyIdeaCategory},
		"vide":     {"category": "LINK_CATEGORY_UNSPECIFIED"},
		"absente":  {},
		"recette":  {"category": "LINK_CATEGORY_RECETTE"},
		"activite": {"category": ActivityCategory},
	}
	ids := map[string]bson.ObjectID{}
	for name, d := range docs {
		ids[name] = bson.NewObjectID()
		d["_id"], d["owner_id"], d["title"], d["updated_at"] = ids[name], "alice", name, old
		if _, err := svc.col.InsertOne(ctx, d); err != nil {
			t.Fatal(err)
		}
	}

	n, err := svc.MigrateIdeaCategory(ctx)
	if err != nil {
		t.Fatalf("migrate: %v", err)
	}
	if n != 3 {
		t.Errorf("%d idées migrées, want 3", n)
	}
	for name, want := range map[string]string{"idee": ActivityCategory, "vide": ActivityCategory, "absente": ActivityCategory, "recette": "LINK_CATEGORY_RECETTE", "activite": ActivityCategory} {
		var got Link
		if err := svc.col.FindOne(ctx, bson.M{"_id": ids[name]}).Decode(&got); err != nil {
			t.Fatal(err)
		}
		if got.Category != want {
			t.Errorf("%s : catégorie %q, want %q", name, got.Category, want)
		}
		if !got.UpdatedAt.Equal(old) {
			t.Errorf("%s : updated_at modifié (%v)", name, got.UpdatedAt)
		}
	}
	// Relancée : plus rien à faire
	if n, _ := svc.MigrateIdeaCategory(ctx); n != 0 {
		t.Errorf("seconde migration : %d idées, want 0", n)
	}
}
