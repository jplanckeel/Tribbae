package seo

import (
	"time"
)

// Données structurées schema.org (JSON-LD) : elles disent aux moteurs et aux
// assistants IA ce qu'est chaque page (recette, événement, livre, liste…).

type obj = map[string]any

const schemaContext = "https://schema.org"

func (h *Handler) siteGraph() obj {
	return obj{
		"@context": schemaContext,
		"@graph": []obj{
			{
				"@type": "WebSite", "@id": h.site + "/#website", "url": h.site + "/",
				"name": siteName, "description": siteDescription, "inLanguage": "fr-FR",
				"publisher": obj{"@id": h.site + "/#organization"},
				"potentialAction": obj{
					"@type":       "SearchAction",
					"target":      obj{"@type": "EntryPoint", "urlTemplate": h.site + "/explorer?q={search_term_string}"},
					"query-input": "required name=search_term_string",
				},
			},
			{
				"@type": "Organization", "@id": h.site + "/#organization", "name": siteName, "url": h.site + "/",
				"logo":  obj{"@type": "ImageObject", "url": h.site + "/tribbae.jpg", "width": 1024, "height": 1024},
				"email": "contact@tribbae.fr",
			},
			{
				"@type": "WebApplication", "@id": h.site + "/#app", "name": siteName, "url": h.site + "/",
				"applicationCategory": "LifestyleApplication", "operatingSystem": "Web, Android",
				"inLanguage": "fr-FR", "description": siteDescription,
				"offers": obj{"@type": "Offer", "price": "0", "priceCurrency": "EUR"},
				"featureList": []string{
					"Idées classées par catégorie : sorties, activités, recettes, cadeaux, livres, déco",
					"Aperçu automatique (titre, image) des liens ajoutés",
					"Dossiers partagés avec la famille",
					"Tribu : plusieurs comptes d'une même famille",
					"Liste de courses à partir des recettes",
					"Rappels pour les sorties datées",
					"Génération d'idées par IA",
					"Idées et dossiers publics des autres familles",
				},
			},
		},
	}
}

func (h *Handler) breadcrumbs(crumbs []Crumb) obj {
	items := make([]obj, 0, len(crumbs))
	for i, c := range crumbs {
		items = append(items, obj{"@type": "ListItem", "position": i + 1, "name": c.Name, "item": h.site + c.Path})
	}
	return obj{"@context": schemaContext, "@type": "BreadcrumbList", "itemListElement": items}
}

func ideaItems(ideas []Idea) []Item {
	items := make([]Item, 0, len(ideas))
	for _, i := range ideas {
		items = append(items, Item{Title: i.Title, Path: "/links/" + i.ID})
	}
	return items
}

func (h *Handler) itemList(name string, items []Item) obj {
	list := make([]obj, 0, len(items))
	for i, it := range items {
		list = append(list, obj{"@type": "ListItem", "position": i + 1, "url": h.site + it.Path, "name": it.Title})
	}
	return obj{"@context": schemaContext, "@type": "ItemList", "name": name, "numberOfItems": len(list), "itemListElement": list}
}

func (h *Handler) collectionPage(p *Page, items []Item) obj {
	list := h.itemList(p.H1, items)
	delete(list, "@context")
	return obj{
		"@context": schemaContext, "@type": "CollectionPage",
		"name": p.H1, "description": clip(p.Description, maxDescriptionRunes), "url": h.site + p.Path,
		"inLanguage": "fr-FR", "isPartOf": obj{"@id": h.site + "/#website"},
		"mainEntity": list,
	}
}

// ideaSchema décrit une idée selon sa nature : recette, événement, livre, ou
// à défaut œuvre créative.
func (h *Handler) ideaSchema(i *Idea, c category, p *Page) obj {
	o := obj{
		"@context": schemaContext, "@type": "CreativeWork",
		"name": i.Title, "url": h.site + p.Path, "inLanguage": "fr-FR",
		"description": clip(p.Description, 300),
		"genre":       c.Label,
	}
	if p.Image != "" {
		o["image"] = []string{p.Image}
	}
	if i.Author != "" {
		o["author"] = obj{"@type": "Person", "name": i.Author}
	}
	if !i.Created.IsZero() {
		o["datePublished"] = i.Created.UTC().Format(time.RFC3339)
	}
	if !i.Updated.IsZero() {
		o["dateModified"] = i.Updated.UTC().Format(time.RFC3339)
	}
	if len(i.Tags) > 0 {
		o["keywords"] = i.Tags
	}
	if p.Source != "" {
		o["isBasedOn"] = p.Source
	}
	if i.AgeRange != "" {
		o["typicalAgeRange"] = i.AgeRange
	}
	switch c.Key {
	case "LINK_CATEGORY_RECETTE":
		o["@type"] = "Recipe"
		delete(o, "genre")
		if len(i.Ingredients) > 0 {
			o["recipeIngredient"] = i.Ingredients
		}
	case "LINK_CATEGORY_LIVRE":
		o["@type"] = "Book"
	case "LINK_CATEGORY_EVENEMENT":
		if !i.EventDate.IsZero() {
			o["@type"] = "Event"
			delete(o, "genre")
			o["startDate"] = i.EventDate.In(paris).Format(time.RFC3339)
			o["eventAttendanceMode"] = "https://schema.org/OfflineEventAttendanceMode"
			o["eventStatus"] = "https://schema.org/EventScheduled"
			if i.Location != "" {
				o["location"] = obj{"@type": "Place", "name": i.Location, "address": i.Location}
			}
			// La famille qui partage la sortie n'en est pas l'organisatrice
			delete(o, "author")
		}
	}
	return o
}
