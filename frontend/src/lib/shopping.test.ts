import { describe, expect, it } from "vitest";
import {
  aisleOf,
  applyOp,
  emptyList,
  viewOf,
  aisleOrder,
  arrange,
  buildLines,
  ingredientKey,
  isStaple,
  parseIngredient,
  pickRecipes,
  scaleQty,
  shareText,
  suggestions,
} from "./shopping";

// Mêmes cas que ShoppingListTest (Android)

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
    expect(ingredientKey("Huile d’olive")).toBe(ingredientKey("huile d'olive"));
  });
});

// Mêmes cas que ShoppingListTest.picksRecipesAndThoseToComplete côté Android
describe("recettes proposées", () => {
  const links = [
    { id: "a", category: "LINK_CATEGORY_RECETTE", ingredients: ["200 g de farine"] },
    { id: "b", category: "LINK_CATEGORY_ACTIVITE", ingredients: ["2 œufs"] },
    { id: "c", category: "LINK_CATEGORY_RECETTE", ingredients: [] },
    { id: "d", category: "LINK_CATEGORY_RECETTE", ingredients: ["  "] },
    { id: "e", category: "LINK_CATEGORY_ACTIVITE" },
  ];

  it("ne prend que les recettes, et met à part celles sans ingrédients", () => {
    const { ready, missing } = pickRecipes(links);
    expect(ready.map((l) => l.id)).toEqual(["a"]);
    expect(missing.map((l) => l.id)).toEqual(["c", "d"]);
  });
});


// « Les courses » partagées : mêmes cas que ShoppingListTest (Android)
const recipe = (title: string, ingredients: string[], baseServings = 4, servings = baseServings) => ({
  title,
  ingredients,
  baseServings,
  servings,
});
const show = (lines: { name: string; qty: string }[]) => lines.map((l) => (l.qty ? `${l.name} · ${l.qty}` : l.name));

describe("rayons", () => {
  it.each([
    ["Pommes de terre", "fruits-legumes"],
    ["pomme", "fruits-legumes"],
    ["Lait", "cremerie"],
    ["Lait de coco", "epicerie"],
    ["Crème de coco", "epicerie"],
    ["Crème fraîche", "cremerie"],
    ["Œufs", "cremerie"],
    ["Pain de campagne", "boulangerie"],
    ["Blanc de poulet", "boucherie"],
    ["Farine de châtaigne", "epicerie"],
    ["Pâte feuilletée", "cremerie"],
    ["Couches taille 4", "bebe-hygiene"],
    ["Papier toilette", "maison"],
    ["Gousse d'ail", "fruits-legumes"],
    ["Quinoa soufflé", "epicerie"],
    ["Bougie d'anniversaire", "autres"],
  ])("%s → %s", (name, aisle) => {
    expect(aisleOf(ingredientKey(name))).toBe(aisle);
  });

  it("complète l'ordre de la tribu par l'ordre par défaut", () => {
    expect(aisleOrder(["epicerie", "inconnu", "boulangerie", "epicerie"]).slice(0, 4)).toEqual([
      "epicerie",
      "boulangerie",
      "fruits-legumes",
      "boucherie",
    ]);
    expect(aisleOrder()).toHaveLength(10);
  });
});

describe("placard", () => {
  it("reconnaît les produits de base, pas les produits proches", () => {
    expect(isStaple(ingredientKey("Sel"))).toBe(true);
    expect(isStaple(ingredientKey("Huile d’olive"))).toBe(true);
    expect(isStaple(ingredientKey("Farine de blé"))).toBe(true);
    expect(isStaple(ingredientKey("Farine de châtaigne"))).toBe(false);
    expect(isStaple(ingredientKey("Beurre"))).toBe(false);
  });
});

describe("parts", () => {
  it.each([
    [3, 0.75, "", 3],
    [1, 1.5, "gousse", 2],
    [1, 0.3, "", 1],
    [125, 1.5, "g", 190],
    [10, 1.24, "g", 12.5],
    [250, 4 / 3, "ml", 335],
    [1, 1.3, "c. à soupe", 1.5],
    [2, 3, "pincée", 2],
    [0.5, 1, "", 0.5],
  ])("%s × %s %s → %s", (qty, factor, unit, expected) => {
    expect(scaleQty(qty, factor, unit)).toBeCloseTo(expected);
  });

  it("recalcule les quantités pour les parts choisies", () => {
    const lines = buildLines([recipe("Crêpes", ["250 g de farine", "3 oeufs", "50 cl de lait", "1 pincée de sel"], 6, 9)]);
    expect(show(lines)).toEqual(["Farine · 375 g", "Oeufs · 5", "Lait · 75 cl", "Sel · 1 pincée"]);
  });

  it("ne touche pas aux quantités quand les parts sont celles de la recette", () => {
    const lines = buildLines([recipe("A", ["⅓ citron"]), recipe("B", ["⅔ citron"])]);
    expect(show(lines)).toEqual(["Citron · 1"]);
  });
});

describe("buildLines", () => {
  const menu = [
    recipe("Crêpes", ["50 cl de lait", "30 g de beurre", "2 c. à soupe d'huile"], 6, 6),
    recipe("Gratin", ["50 cl de lait", "20 g de beurre", "1 gousse d'ail", "Sel"], 4, 4),
  ];

  it("additionne par produit et garde le détail par recette", () => {
    const lines = buildLines(menu);
    expect(show(lines)).toEqual(["Lait · 1 l", "Beurre · 50 g", "Huile · 2 c. à soupe", "Ail · 1 gousse", "Sel"]);
    expect(lines[0].from).toEqual([
      { recipe: "Crêpes", qty: "50 cl" },
      { recipe: "Gratin", qty: "50 cl" },
    ]);
    expect(lines[0].key).toBe("lait|1 l");
    expect(lines[0].aisle).toBe("cremerie");
    expect(lines.find((l) => l.product === "sel")?.staple).toBe(true);
  });

  it("additionne un produit cité deux fois dans la même recette", () => {
    const lines = buildLines([recipe("Gâteau", ["2 oeufs", "1 oeuf"])]);
    expect(lines[0].from).toEqual([{ recipe: "Gâteau", qty: "3" }]);
  });

  it("applique la quantité et le rayon choisis à la main", () => {
    const lines = buildLines(menu, [], { quantities: { lait: "2 bouteilles" }, aisles: { beurre: "epicerie", ail: "pas-un-rayon" } });
    expect(lines[0]).toMatchObject({ qty: "2 bouteilles", key: "lait|2 bouteilles", edited: true });
    expect(lines[1].aisle).toBe("epicerie");
    expect(lines.find((l) => l.product === "ail")?.aisle).toBe("fruits-legumes");
  });

  it("convertit les unités et passe au kilo ou au litre", () => {
    const lines = buildLines([
      recipe("A", ["600 g de sucre", "20 cl de crème", "30 ml d'huile"]),
      recipe("B", ["0,5 kg de sucre", "25 cl de crème", "15 ml d'huile"]),
    ]);
    expect(show(lines)).toEqual(["Sucre · 1,1 kg", "Crème · 45 cl", "Huile · 45 ml"]);
  });

  it("juxtapose des unités qui ne se convertissent pas", () => {
    const lines = buildLines([recipe("A", ["2 c. à soupe de farine", "1 oignon"]), recipe("B", ["250 g de farine", "100 g d'oignon"])]);
    expect(show(lines)).toEqual(["Farine · 250 g + 2 c. à soupe", "Oignon · 100 g + 1"]);
  });

  it("met le nom au pluriel quand une recette l'écrit ainsi, et ignore les lignes vides", () => {
    const lines = buildLines([recipe("A", ["1 tomate", "", "  "]), recipe("B", ["2 tomates"])]);
    expect(show(lines)).toEqual(["Tomates · 3"]);
  });

  it("ajoute les articles libres avec leur rayon", () => {
    const lines = buildLines([], [
      { id: "a1", label: "Couches taille 4", quantity: "1 paquet", aisle: "", addedBy: "u2" },
      { id: "a2", label: "bougies", quantity: "", aisle: "maison", addedBy: "u1" },
      { id: "a3", label: "  ", quantity: "", aisle: "", addedBy: "u1" },
    ]);
    expect(lines.map((l) => [l.key, l.name, l.qty, l.aisle, l.addedBy])).toEqual([
      ["item:a1", "Couches taille 4", "1 paquet", "bebe-hygiene", "u2"],
      ["item:a2", "Bougies", "", "maison", "u1"],
    ]);
  });
});

describe("arrange", () => {
  const lines = buildLines(
    [recipe("Gratin", ["1 kg de pommes de terre", "50 cl de lait", "1 pincée de muscade", "Sel", "Pain"])],
    [{ id: "x", label: "Lessive", quantity: "", aisle: "", addedBy: "u1" }],
  );

  it("range par rayon dans l'ordre de la tribu", () => {
    const list = arrange(lines, { aisleOrder: ["maison", "cremerie"] });
    expect(list.groups.map((g) => [g.name, g.lines.map((l) => l.name)])).toEqual([
      ["Maison", ["Lessive"]],
      ["Crèmerie et œufs", ["Lait"]],
      ["Fruits et légumes", ["Pommes de terre"]],
      ["Boulangerie", ["Pain"]],
      ["Épicerie", ["Muscade", "Sel"]],
    ]);
    expect(list.remaining).toBe(6);
    expect(list.total).toBe(6);
    expect(list.staples.map((l) => l.name)).toEqual(["Muscade", "Sel"]);
  });

  it("sort du compte ce qu'on a, ce qu'on a retiré, et met le coché au panier", () => {
    const list = arrange(lines, { checked: ["lait|50 cl", "item:x"], pantry: ["sel"], removed: ["pain", "lessive"] });
    expect(list.cart.map((l) => l.name)).toEqual(["Lait", "Lessive"]);
    expect(list.have.map((l) => l.name)).toEqual(["Sel"]);
    expect(list.removed.map((l) => l.name)).toEqual(["Pain"]);
    expect(list.groups.flatMap((g) => g.lines.map((l) => l.name))).toEqual(["Pommes de terre", "Muscade"]);
    expect(list.remaining).toBe(2);
    expect(list.total).toBe(4);
  });

  it("décoche une ligne dont la quantité a changé", () => {
    const more = buildLines([recipe("Gratin", ["50 cl de lait"], 4, 8)]);
    expect(arrange(more, { checked: ["lait|50 cl"] }).cart).toEqual([]);
  });

  it("écrit la liste en texte", () => {
    expect(shareText(arrange(lines, { pantry: ["sel", "muscade"] }))).toBe(
      "Courses Tribbae — 4 articles\n" +
        "Fruits et légumes : pommes de terre (1 kg)\n" +
        "Boulangerie : pain\n" +
        "Crèmerie et œufs : lait (50 cl)\n" +
        "Maison : lessive",
    );
  });
});

describe("suggestions", () => {
  it("propose les articles fréquents absents de la liste", () => {
    const lines = buildLines([recipe("A", ["1 l de lait", "2 oeufs"])]);
    expect(suggestions(lines)).toEqual([
      { label: "Pain", aisle: "boulangerie" },
      { label: "Yaourts", aisle: "cremerie" },
      { label: "Beurre", aisle: "cremerie" },
      { label: "Café", aisle: "epicerie" },
    ]);
  });
});

// Mêmes cas que ShoppingListTest.appliesOps* (Android) et internal/shopping (serveur)
describe("applyOp", () => {
  const recipes: Record<string, { title: string; ingredients: string[]; servings?: number }> = {
    r1: { title: "Crêpes", ingredients: ["250 g de farine", "50 cl de lait"], servings: 6 },
    r2: { title: "Soupe", ingredients: ["1 potimarron"] },
  };
  const find = (id: string) => recipes[id];
  const run = (ops: Parameters<typeof applyOp>[1][], from = emptyList()) =>
    ops.reduce((l, op) => applyOp(l, op, "u1", 100, find), from);

  it("ajoute une recette au menu avec ses parts, sans doublon", () => {
    const l = run([{ type: "menu_add", linkId: "r1" }, { type: "menu_add", linkId: "r2", servings: 2 }, { type: "menu_add", linkId: "r1" }, { type: "menu_add", linkId: "inconnue" }]);
    expect(l.menu.map((e) => [e.linkId, e.baseServings, e.servings, e.addedBy])).toEqual([
      ["r1", 6, 6, "u1"],
      ["r2", 4, 2, "u1"],
    ]);
    expect(run([{ type: "menu_add", linkId: "r1", servings: 3 }], l).menu[0].servings).toBe(3);
  });

  it("change les parts et retire une recette", () => {
    const l = run([{ type: "menu_add", linkId: "r1" }, { type: "menu_servings", linkId: "r1", servings: 9 }, { type: "menu_servings", linkId: "r1", servings: 0 }]);
    expect(l.menu[0].servings).toBe(9);
    expect(run([{ type: "menu_remove", linkId: "r1" }], l).menu).toEqual([]);
  });

  it("ajoute, modifie et retire un article libre, et oublie sa case cochée", () => {
    let l = run([
      { type: "item_add", id: "a", label: "  Couches   taille 4 ", quantity: "1 paquet" },
      { type: "item_add", id: "a", label: "Doublon" },
      { type: "item_add", id: "b", label: "   " },
      { type: "check", key: "item:a" },
    ]);
    expect(l.items.map((it) => [it.id, it.label, it.quantity, it.addedBy])).toEqual([["a", "Couches taille 4", "1 paquet", "u1"]]);
    l = run([{ type: "item_update", id: "a", label: "Couches taille 5", quantity: "", aisle: "bebe-hygiene" }], l);
    expect(l.items[0]).toMatchObject({ label: "Couches taille 5", quantity: "", aisle: "bebe-hygiene" });
    l = run([{ type: "item_remove", id: "a" }], l);
    expect([l.items, l.checked]).toEqual([[], []]);
  });

  it("coche, décoche, retient quantités, rayons, placard et retraits", () => {
    const l = run([
      { type: "check", key: "lait|50 cl" },
      { type: "check", key: "farine|250 g" },
      { type: "uncheck", key: "farine|250 g" },
      { type: "quantity", key: "lait", value: "1 brique" },
      { type: "aisle", key: "lait", value: "epicerie" },
      { type: "aisle", key: "lait", value: "" },
      { type: "pantry_have", key: "sel" },
      { type: "pantry_have", key: "sel" },
      { type: "pantry_have", key: "huile" },
      { type: "pantry_out", key: "huile" },
      { type: "remove", key: "pain" },
      { type: "aisle_order", order: ["epicerie"] },
    ]);
    expect(l.checked).toEqual([{ key: "lait|50 cl", by: "u1", at: 100 }]);
    expect(l.quantities).toEqual([{ key: "lait", value: "1 brique" }]);
    expect(l.aisles).toEqual([]);
    expect(l.pantry).toEqual(["sel"]);
    expect(l.removed).toEqual(["pain"]);
    expect(l.aisleOrder).toEqual(["epicerie"]);
    expect(run([{ type: "restore", key: "pain" }], l).removed).toEqual([]);
  });

  it("note qui est en magasin, et seul lui l'arrête", () => {
    const l = run([{ type: "shopping_start" }]);
    expect([l.shopperId, l.shoppingSince]).toEqual(["u1", 100]);
    expect(applyOp(l, { type: "shopping_stop" }, "u2", 200).shopperId).toBe("u1");
    expect(run([{ type: "shopping_stop" }], l).shopperId).toBe("");
  });

  it("termine les courses : vide le panier, garde le menu et le placard", () => {
    const l = run([
      { type: "menu_add", linkId: "r2" },
      { type: "item_add", id: "a", label: "Lait" },
      { type: "item_add", id: "b", label: "Pain" },
      { type: "check", key: "item:a" },
      { type: "check", key: "potimarron|1" },
      { type: "pantry_have", key: "sel" },
      { type: "shopping_start" },
      { type: "finish" },
    ]);
    expect([l.menu.length, l.items.map((it) => it.id), l.checked, l.pantry, l.shopperId]).toEqual([1, ["b"], [], ["sel"], ""]);
  });

  it("recommence une liste en gardant placard et rayons", () => {
    const l = run([
      { type: "menu_add", linkId: "r1" },
      { type: "item_add", id: "a", label: "Lait" },
      { type: "quantity", key: "lait", value: "2" },
      { type: "aisle", key: "lait", value: "epicerie" },
      { type: "pantry_have", key: "sel" },
      { type: "remove", key: "farine" },
      { type: "aisle_order", order: ["maison"] },
      { type: "clear" },
    ]);
    expect([l.menu, l.items, l.quantities, l.removed]).toEqual([[], [], [], []]);
    expect([l.aisles, l.pantry, l.aisleOrder]).toEqual([[{ key: "lait", value: "epicerie" }], ["sel"], ["maison"]]);
  });

  it("montre la liste qui en découle", () => {
    const l = run([{ type: "menu_add", linkId: "r1", servings: 3 }, { type: "check", key: "lait|25 cl" }]);
    const { arranged } = viewOf(l);
    expect(arranged.groups.flatMap((g) => show(g.lines))).toEqual(["Farine · 125 g"]);
    expect(show(arranged.cart)).toEqual(["Lait · 25 cl"]);
  });
});
