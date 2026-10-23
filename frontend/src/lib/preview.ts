import type { LinkPreview } from "./types";

/** Coupe à `max` unités sans casser un émoji (paire de substitution). */
export function limitTo(s: string, max: number): string {
  if (s.length <= max) return s;
  const cut = /[\uD800-\uDBFF]/.test(s[max - 1]) ? max - 1 : max;
  return s.slice(0, cut).trimEnd();
}

/**
 * Textes d'un aperçu ramenés aux limites des champs (`maxLength`) et du serveur :
 * titre 300, description 5000, prix / lieu / ingrédient 200. Au-delà, le
 * navigateur bloque la saisie dans le champ pré-rempli.
 */
export function withinLimits(p: LinkPreview): LinkPreview {
  return {
    ...p,
    title: limitTo(p.title ?? "", 300),
    description: limitTo(p.description ?? "", 5000),
    price: p.price && limitTo(p.price, 200),
    location: p.location && limitTo(p.location, 200),
    ingredients: p.ingredients?.map((i) => limitTo(i, 200)),
  };
}
