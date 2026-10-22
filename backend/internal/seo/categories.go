package seo

// category reprend les catégories d'idées (cf. frontend/src/lib/categories.ts,
// link.proto) avec un texte de présentation pour leur page publique.
type category struct {
	Key, Slug, Label, Plural, Intro string
}

var categories = []category{
	{"LINK_CATEGORY_ACTIVITE", "activite", "Activité", "Activités",
		"Activités à faire en famille : jeux, bricolages, sorties nature et idées pour occuper les enfants, repérées et partagées par les familles de Tribbae."},
	{"LINK_CATEGORY_CADEAU", "cadeau", "Cadeau", "Cadeaux",
		"Idées de cadeaux pour enfants, ados et parents : des trouvailles testées et partagées par les familles de Tribbae."},
	{"LINK_CATEGORY_RECETTE", "recette", "Recette", "Recettes",
		"Recettes faciles à cuisiner en famille et avec les enfants : goûters, repas et desserts partagés par les familles de Tribbae."},
	{"LINK_CATEGORY_EVENEMENT", "sortie", "Sortie", "Sorties",
		"Sorties et événements en famille : spectacles, visites, fêtes et balades repérés par les familles de Tribbae."},
	{"LINK_CATEGORY_IDEE", "idee", "Idée", "Idées",
		"Idées pour la vie de famille : astuces, inspirations et bons plans partagés par les familles de Tribbae."},
	{"LINK_CATEGORY_LIVRE", "livre", "Livre", "Livres",
		"Livres pour enfants et ados : albums, romans et BD recommandés par les familles de Tribbae."},
	{"LINK_CATEGORY_DECORATION", "deco", "Déco", "Déco",
		"Idées déco pour la maison et la chambre des enfants, partagées par les familles de Tribbae."},
}

func categoryByKey(key string) category {
	for _, c := range categories {
		if c.Key == key {
			return c
		}
	}
	return categories[4] // Idée
}

func categoryBySlug(slug string) (category, bool) {
	for _, c := range categories {
		if c.Slug == slug {
			return c, true
		}
	}
	return category{}, false
}
