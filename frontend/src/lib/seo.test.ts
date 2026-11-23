import { describe, expect, it } from "vitest";
import { categoryMeta } from "./categories";
import { cleanTitle, ideaSeoDescription } from "./seo";
import type { Link } from "./types";

// Mêmes cas que backend/internal/seo (TestCleanTitle, TestIdeaDescription)
describe("cleanTitle", () => {
  it.each([
    ["Le voleur de citrouilles - cartonné - Alice Hemming, Nicola Slater, Nicola Slater - Achat Livre | fnac", "Le voleur de citrouilles"],
    ["Le voleur de citrouilles - Alice Hemming | fnac", "Le voleur de citrouilles - Alice Hemming"],
    ["Mini World Lyon - Une super sortie loisirs en famille à Lyon", "Mini World Lyon"],
    ["Le parc de la Tête d’or | Ville de Lyon", "Le parc de la Tête d’or"],
    ["Livre", "Livre"],
    [`Lou Elsener sur Instagram: "Salade de pâtes croustillantes pour mes djeuns"`, "Salade de pâtes croustillantes pour mes djeuns"],
    [`CG | Nutritionniste on Instagram: "🍪 Cookies sans cuisson #recette #healthy"`, "Cookies sans cuisson"],
    [`Zoe sur Instagram : « Cuire à 180°C pendant 20 min ✨ »`, "Cuire à 180°C pendant 20 min"],
    [`Zoe sur Instagram: ""`, "Publication de Zoe sur Instagram"],
  ])("%s", (raw, want) => {
    expect(cleanTitle(raw)).toBe(want);
  });
});

describe("ideaSeoDescription", () => {
  const book = categoryMeta("LINK_CATEGORY_LIVRE");
  const link = (l: Partial<Link>) => ({ id: "x", ownerId: "o", category: "LINK_CATEGORY_LIVRE", title: "", ...l }) as Link;

  it.each([
    [link({ title: "Le voleur", description: "Un album.", ageRange: "3–6 ans", ownerDisplayName: "Alice" }), "Livre partagé par Alice sur Tribbae, pour les 3–6 ans : Un album."],
    [link({ title: `Lou sur Instagram: "Salade"`, description: `151K likes, 788 comments - louloukitchen_ le October 9, 2025: "Salade"` }), "Livre partagé sur Tribbae : Salade."],
    [link({ title: `Lou sur Instagram: "Salade"`, description: `1 234 J’aime, 56 commentaires - lou le 9 octobre 2025 : « Une salade d'été 🥗 »` }), "Livre partagé sur Tribbae : Une salade d'été"],
  ])("%#", (l, want) => {
    expect(ideaSeoDescription(l, book)).toBe(want);
  });
});
