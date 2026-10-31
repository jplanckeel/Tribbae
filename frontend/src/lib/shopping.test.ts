import { describe, expect, it } from "vitest";
import { buildShoppingList, ingredientKey, parseIngredient } from "./shopping";

// Mêmes cas que ShoppingListTest (Android)
const labels = (recipes: { title: string; ingredients: string[] }[]) => buildShoppingList(recipes).map((i) => i.label);

describe("parseIngredient", () => {
  it.each([
    ["250 g de farine", 250, "g", "farine"],
    ["1 kg de pommes de terre", 1000, "g", "pommes de terre"],
    ["½ litre lait", 500, "ml", "lait"],
    ["50 cl de lait", 500, "ml", "lait"],
    ["1,5 l d'eau", 1500, "ml", "eau"],
    ["2 c. à soupe d'huile d'olive", 2, "c. à soupe", "huile d'olive"],
    ["1 cuillère à café de sel", 1, "c. à café", "sel"],
    ["30 ml (2 c. à soupe) de beurre", 30, "ml", "beurre"],
    ["1 oignon, haché", 1, "", "oignon"],
    ["1/2 citron", 0.5, "", "citron"],
    ["Blanc(s) de poulet", null, "", "Blanc de poulet"],
    ["Sel", null, "", "Sel"],
    ["2-3 tomates", null, "", "2-3 tomates"],
    ["1.5 cups all-purpose flour", 1.5, "tasse", "all-purpose flour"],
    ["4 gésiers de canard", 4, "", "gésiers de canard"],
    ["3 c. à s. de sucre", 3, "c. à soupe", "sucre"],
    ["2 gousses d'ail", 2, "gousse", "ail"],
    ["250\u00a0g de farine", 250, "g", "farine"],
  ])("%s", (line, qty, unit, name) => {
    const p = parseIngredient(line);
    if (qty === null) expect(p.qty).toBeNull();
    else expect(p.qty).toBeCloseTo(qty as number);
    expect(p.unit).toBe(unit);
    expect(p.name).toBe(name);
  });
});

describe("ingredientKey", () => {
  it("ignore accents, ligatures, parenthèses et pluriels", () => {
    expect(ingredientKey("Œufs")).toBe(ingredientKey("oeuf"));
    expect(ingredientKey("Citron(s) vert(s)")).toBe(ingredientKey("citron vert"));
    expect(ingredientKey("Crème fraîche")).toBe(ingredientKey("creme fraiche"));
  });
});

describe("buildShoppingList", () => {
  it("additionne les quantités d'un même produit", () => {
    expect(
      labels([
        { title: "Crêpes", ingredients: ["250 g de farine", "4 oeufs", "½ litre lait"] },
        { title: "Gâteau", ingredients: ["100 g de farine", "2 œufs", "50 cl de lait"] },
      ]),
    ).toEqual(["350 g de farine", "1 l de lait", "6 oeufs"]);
  });

  it("garde telle quelle une ligne présente dans une seule recette", () => {
    expect(labels([{ title: "Gratin", ingredients: ["1 kg de pommes de terre, épluchées", "Sel"] }])).toEqual([
      "1 kg de pommes de terre, épluchées",
      "Sel",
    ]);
  });

  it("convertit les unités et passe au kilo ou au litre", () => {
    expect(
      labels([
        { title: "A", ingredients: ["600 g de sucre", "20 cl de crème", "30 ml d'huile"] },
        { title: "B", ingredients: ["0,5 kg de sucre", "25 cl de crème", "15 ml d'huile"] },
      ]),
    ).toEqual(["45 cl de crème", "45 ml d'huile", "1,1 kg de sucre"]);
  });

  it("juxtapose des unités qui ne se convertissent pas", () => {
    expect(
      labels([
        { title: "A", ingredients: ["2 c. à soupe de farine"] },
        { title: "B", ingredients: ["250 g de farine"] },
        { title: "C", ingredients: ["1 oignon"] },
        { title: "D", ingredients: ["100 g d'oignon"] },
      ]),
    ).toEqual(["250 g + 2 c. à soupe de farine", "100 g + 1 oignon"]);
  });

  it("élide devant une voyelle ou un h muet, pas devant un h aspiré", () => {
    expect(
      labels([
        { title: "A", ingredients: ["200 g d'haricots verts", "1 c. à soupe d'huile"] },
        { title: "B", ingredients: ["300 g de haricots verts", "2 c. à soupe d'huile"] },
      ]),
    ).toEqual(["500 g de haricots verts", "3 c. à soupe d'huile"]);
  });

  it("ne garde que le nom quand aucune quantité n'est donnée", () => {
    expect(labels([{ title: "A", ingredients: ["Sel"] }, { title: "B", ingredients: ["sel"] }])).toEqual(["Sel"]);
  });

  it("met le nom au pluriel quand une recette l'écrit ainsi", () => {
    expect(labels([{ title: "A", ingredients: ["1 tomate"] }, { title: "B", ingredients: ["2 tomates"] }])).toEqual(["3 tomates"]);
  });

  it("additionne les fractions", () => {
    expect(labels([{ title: "A", ingredients: ["⅓ citron"] }, { title: "B", ingredients: ["⅔ citron"] }])).toEqual(["1 citron"]);
  });

  it("note les recettes d'origine sans doublon", () => {
    const items = buildShoppingList([
      { title: "Crêpes", ingredients: ["2 oeufs", "1 oeuf"] },
      { title: "Gâteau", ingredients: ["3 oeufs"] },
    ]);
    expect(items).toHaveLength(1);
    expect(items[0].label).toBe("6 oeufs");
    expect(items[0].from).toEqual(["Crêpes", "Gâteau"]);
  });

  it("change la clé quand la quantité change (une case cochée ne reste pas cochée)", () => {
    const one = buildShoppingList([{ title: "A", ingredients: ["2 oeufs"] }])[0].key;
    const two = buildShoppingList([{ title: "A", ingredients: ["2 oeufs"] }, { title: "B", ingredients: ["1 oeuf"] }])[0].key;
    expect(one).not.toBe(two);
  });

  it("ignore les lignes vides", () => {
    expect(labels([{ title: "A", ingredients: ["", "  ", "Poivre"] }])).toEqual(["Poivre"]);
  });
});
