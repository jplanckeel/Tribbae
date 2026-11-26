package link

import (
	"context"

	"go.mongodb.org/mongo-driver/v2/bson"
)

// Catégorie « Idée » fusionnée dans « Activité » : elle était devenue un
// fourre-tout. L'enum la garde (LINK_CATEGORY_IDEE = 1) pour les anciennes
// versions de l'app, mais elle n'est plus jamais enregistrée.
const (
	ActivityCategory   = "LINK_CATEGORY_ACTIVITE"
	legacyIdeaCategory = "LINK_CATEGORY_IDEE"
)

// mergedCategories : valeurs converties en Activité, à l'écriture comme par
// MigrateIdeaCategory (sans catégorie : l'ancien défaut était Idée).
var mergedCategories = []string{legacyIdeaCategory, "LINK_CATEGORY_UNSPECIFIED", ""}

// normalizeCategory remplace une catégorie fusionnée ou absente par Activité.
func normalizeCategory(c string) string {
	for _, m := range mergedCategories {
		if c == m {
			return ActivityCategory
		}
	}
	return c
}

// MigrateIdeaCategory range dans Activité les idées encore classées « Idée »
// (ou sans catégorie). Sans effet une fois fait : rien ne les recrée.
// updated_at ne bouge pas : l'idée n'a pas été modifiée par son auteur.
func (s *Service) MigrateIdeaCategory(ctx context.Context) (int64, error) {
	res, err := s.col.UpdateMany(ctx,
		bson.M{"category": bson.M{"$in": append([]any{nil}, toAny(mergedCategories)...)}},
		bson.M{"$set": bson.M{"category": ActivityCategory}})
	if err != nil {
		return 0, err
	}
	return res.ModifiedCount, nil
}

func toAny(v []string) []any {
	out := make([]any, len(v))
	for i, s := range v {
		out[i] = s
	}
	return out
}
