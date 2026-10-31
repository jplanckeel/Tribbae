// Liste de courses : ingrédients des recettes choisies, regroupés par produit
// avec leurs quantités additionnées (« 250 g de farine » + « 100 g de farine »
// = « 350 g de farine »). Même logique dans data/ShoppingList.kt (Android) :
// shopping.test.ts ↔ ShoppingListTest, garder les mêmes cas.

export interface ShoppingRecipe {
  title: string;
  ingredients: string[];
}

export interface ShoppingItem {
  /** Identifie la ligne pour la cocher ; change quand la quantité change. */
  key: string;
  label: string;
  /** Recettes d'où vient la ligne, dans l'ordre. */
  from: string[];
}

// Unités : famille (g et ml se convertissent entre elles) et facteur vers la base.
// Fin de mot explicite plutôt que \b, qui ne connaît pas les lettres accentuées
// (« 4 gésiers » n'est pas « 4 g »).
const END = String.raw`(?=[\s.,;:()]|$)`;
const unit = (pattern: string) => new RegExp(`^(?:${pattern})${END}`);
const UNITS: [RegExp, string, number][] = [
  [unit("kg|kilos?|kilogrammes?"), "g", 1000],
  [unit("mg|milligrammes?"), "g", 0.001],
  [unit("g|gr|grammes?"), "g", 1],
  [unit("dl|décilitres?"), "ml", 100],
  [unit("cl|centilitres?"), "ml", 10],
  [unit("ml|millilitres?"), "ml", 1],
  [unit("l|litres?"), "ml", 1000],
  [unit(String.raw`c\.?\s*à\s*soupe|c\.?\s*à\s*s\.?|càs|cs|cuil(?:\.|lères?|lerées?)?\s*à\s*soupe|tbsp|tablespoons?`), "c. à soupe", 1],
  [unit(String.raw`c\.?\s*à\s*café|c\.?\s*à\s*c\.?|càc|cc|cuil(?:\.|lères?|lerées?)?\s*à\s*café|tsp|teaspoons?`), "c. à café", 1],
  [unit("pincées?"), "pincée", 1],
  [unit("sachets?"), "sachet", 1],
  [unit("tranches?"), "tranche", 1],
  [unit("gousses?"), "gousse", 1],
  [unit("boîtes?|boites?"), "boîte", 1],
  [unit("verres?"), "verre", 1],
  [unit("tasses?|cups?"), "tasse", 1],
  [unit("pots?"), "pot", 1],
  [unit("bottes?"), "botte", 1],
  [unit("brins?"), "brin", 1],
  [unit("feuilles?"), "feuille", 1],
  [unit("morceaux|morceau"), "morceau", 1],
];

const PLURAL_UNITS: Record<string, string> = { morceau: "morceaux", "c. à soupe": "c. à soupe", "c. à café": "c. à café" };
// h aspiré : pas d'élision (« de haricots »)
const H_ASPIRE = ["haricot", "hachis", "homard", "hareng", "houblon"];
const FRACTIONS: Record<string, number> = { "½": 0.5, "¼": 0.25, "¾": 0.75, "⅓": 1 / 3, "⅔": 2 / 3, "⅛": 0.125 };

interface Parsed {
  qty: number | null;
  unit: string; // famille ("" = à l'unité)
  name: string; // nom affiché (sans quantité, sans précisions après la virgule)
}

/** Enlève accents, ligatures, parenthèses et pluriels simples pour comparer les noms. */
export function ingredientKey(name: string): string {
  return name
    .toLowerCase()
    .replace(/œ/g, "oe")
    .replace(/æ/g, "ae")
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/\([^)]*\)/g, " ")
    .replace(/[^a-z0-9' -]/g, " ")
    .split(/\s+/)
    .filter(Boolean)
    .map((w) => (w.length > 3 && /[sx]$/.test(w) ? w.slice(0, -1) : w))
    .join(" ");
}

function parseNumber(s: string): number | null {
  if (FRACTIONS[s] !== undefined) return FRACTIONS[s];
  const mixed = /^(\d+)\s*([½¼¾⅓⅔⅛])$/.exec(s);
  if (mixed) return Number(mixed[1]) + FRACTIONS[mixed[2]];
  const frac = /^(\d+)\s*\/\s*(\d+)$/.exec(s);
  if (frac) return Number(frac[2]) ? Number(frac[1]) / Number(frac[2]) : null;
  const n = Number(s.replace(",", "."));
  return Number.isFinite(n) ? n : null;
}

export function parseIngredient(line: string): Parsed {
  // Espaces insécables (fréquentes sur les sites de recettes) : des espaces simples
  const text = line.replace(/[\u00a0\u202f]/g, " ").trim();
  const m = /^(\d+\s*[½¼¾⅓⅔⅛]|\d+\s*\/\s*\d+|\d+(?:[.,]\d+)?|[½¼¾⅓⅔⅛])\s*/.exec(text);
  let qty: number | null = null;
  let unit = "";
  let factor = 1;
  let rest = text;
  // Une fourchette (« 2-3 », « 2 à 3 ») reste telle quelle
  if (m && !/^[-–]|^à\s*\d/.test(text.slice(m[0].length))) {
    qty = parseNumber(m[1].replace(/\s+/g, ""));
    rest = text.slice(m[0].length);
    const lower = rest.toLowerCase();
    for (const [re, u, f] of UNITS) {
      const um = re.exec(lower);
      if (um) {
        unit = u;
        factor = f;
        rest = rest.slice(um[0].length);
        break;
      }
    }
    if (qty !== null) qty *= factor;
    rest = rest.replace(/^\s*\([^)]*\)/, "").replace(/^\s*(?:de |d'|d’|du |des )/i, "");
  }
  const name = rest.split(",")[0].replace(/\([^)]*\)/g, " ").replace(/\s+/g, " ").trim();
  return { qty, unit, name: name || text };
}

function formatNumber(n: number): string {
  return String(Math.round(n * 100) / 100).replace(".", ",");
}

function formatQty(qty: number, unit: string): string {
  if (unit === "g") return qty >= 1000 ? `${formatNumber(qty / 1000)} kg` : `${formatNumber(qty)} g`;
  if (unit === "ml") {
    if (qty >= 1000) return `${formatNumber(qty / 1000)} l`;
    if (qty >= 10 && Math.round(qty) % 10 === 0) return `${formatNumber(qty / 10)} cl`;
    return `${formatNumber(qty)} ml`;
  }
  if (!unit) return formatNumber(qty);
  return `${formatNumber(qty)} ${qty > 1 ? (PLURAL_UNITS[unit] ?? `${unit}s`) : unit}`;
}

function elides(s: string): boolean {
  const k = ingredientKey(s);
  return /^[aeiouyh]/.test(k) && !H_ASPIRE.some((w) => k.startsWith(w));
}

// Ordre des quantités : poids et volumes, autres unités, puis à l'unité
function unitRank(unit: string): number {
  return unit === "g" || unit === "ml" ? 0 : unit ? 1 : 2;
}

interface Group {
  key: string;
  lines: string[];
  names: string[];
  totals: Map<string, number>; // famille → quantité (dans l'ordre d'apparition)
  from: string[];
}

/** Regroupe les ingrédients des recettes choisies, triés par nom. */
export function buildShoppingList(recipes: ShoppingRecipe[]): ShoppingItem[] {
  const groups = new Map<string, Group>();
  for (const r of recipes) {
    for (const raw of r.ingredients) {
      const line = raw.trim();
      if (!line) continue;
      const p = parseIngredient(line);
      const key = ingredientKey(p.name) || ingredientKey(line);
      if (!key) continue;
      const g: Group = groups.get(key) ?? { key, lines: [], names: [], totals: new Map(), from: [] };
      g.lines.push(line);
      g.names.push(p.name);
      if (p.qty !== null) g.totals.set(p.unit, (g.totals.get(p.unit) ?? 0) + p.qty);
      if (!g.from.includes(r.title)) g.from.push(r.title);
      groups.set(key, g);
    }
  }
  const items = [...groups.values()].map((g): ShoppingItem => {
    let label: string;
    if (g.lines.length === 1) {
      // Une seule ligne : telle qu'écrite dans la recette
      label = g.lines[0];
    } else if (g.totals.size === 0) {
      label = g.names[0];
    } else {
      const count = g.totals.get("") ?? 0;
      // À l'unité, au pluriel : la variante du nom au pluriel si la recette l'écrit ainsi
      const picked = count > 1 ? (g.names.find((n) => /[sx]$/i.test(n)) ?? g.names[0]) : g.names[0];
      const name = picked.charAt(0).toLowerCase() + picked.slice(1);
      // « 250 g + 2 c. à soupe de farine », « 100 g + 1 oignon »
      const parts = [...g.totals.entries()].sort(([a], [b]) => unitRank(a) - unitRank(b)).map(([u, q]) => formatQty(q, u));
      const glue = count > 0 ? " " : elides(name) ? " d'" : " de ";
      label = parts.join(" + ") + glue + name;
    }
    label = label.charAt(0).toUpperCase() + label.slice(1);
    return { key: `${g.key}|${label}`, label, from: g.from };
  });
  return items.sort((a, b) => a.key.localeCompare(b.key, "fr"));
}
