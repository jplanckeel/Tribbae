// Liste de courses : ingrédients des recettes choisies, regroupés par produit
// avec leurs quantités additionnées (« 250 g de farine » + « 100 g de farine »
// = « 350 g de farine »), recalculées selon les parts, rangées par rayon.
// Même logique dans data/ShoppingList.kt (Android) : shopping.test.ts ↔
// ShoppingListTest, garder les mêmes cas.

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
    .replace(/[’‘`]/g, "'")
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

// Ordre des quantités : poids et volumes, autres unités, puis à l'unité
function unitRank(unit: string): number {
  return unit === "g" || unit === "ml" ? 0 : unit ? 1 : 2;
}

/**
 * Recettes de la liste de courses : celles qui ont des ingrédients, et à part
 * celles qui n'en ont pas encore, à compléter. Mêmes cas que ShoppingList.pick
 * côté Android.
 */
export function pickRecipes<T extends { category?: string; ingredients?: string[] }>(links: T[]): { ready: T[]; missing: T[] } {
  const isRecipe = (l: T) => (l.category ?? "").replace("LINK_CATEGORY_", "") === "RECETTE";
  return {
    ready: links.filter((l) => isRecipe(l) && l.ingredients?.some((i) => i.trim())),
    missing: links.filter((l) => isRecipe(l) && !l.ingredients?.some((i) => i.trim())),
  };
}


// ---------------------------------------------------------------------------
// « Les courses » partagées : parts, rayons, placard, articles libres.
// Le serveur garde les décisions de la tribu (menu, articles, cases cochées,
// quantités et rayons choisis à la main, placard) ; la liste en est déduite ici.
// ---------------------------------------------------------------------------

/** Rayons, dans l'ordre d'un supermarché (ordre par défaut de la tribu). */
export const AISLES = [
  { id: "fruits-legumes", name: "Fruits et légumes" },
  { id: "boulangerie", name: "Boulangerie" },
  { id: "boucherie", name: "Boucherie, poisson" },
  { id: "cremerie", name: "Crèmerie et œufs" },
  { id: "epicerie", name: "Épicerie" },
  { id: "surgeles", name: "Surgelés" },
  { id: "boissons", name: "Boissons" },
  { id: "bebe-hygiene", name: "Bébé et hygiène" },
  { id: "maison", name: "Maison" },
  { id: "autres", name: "Autres" },
] as const;

export type AisleId = (typeof AISLES)[number]["id"];

const AISLE_IDS: readonly string[] = AISLES.map((a) => a.id);
export const isAisle = (id: string): id is AisleId => AISLE_IDS.includes(id);
export const aisleName = (id: string) => AISLES.find((a) => a.id === id)?.name ?? "Autres";

// Petit dictionnaire de mots-clés. Le mot le plus long qui apparaît dans le nom
// l'emporte : « lait de coco » (épicerie) avant « lait » (crèmerie).
const AISLE_WORDS: Record<Exclude<AisleId, "autres">, string> = {
  "fruits-legumes":
    "fruit|légume|pomme|pomme de terre|patate douce|poire|banane|orange|citron|clémentine|mandarine|pamplemousse|fraise|framboise|myrtille|cerise|abricot|pêche|prune|raisin|kiwi|ananas|mangue|melon|pastèque|avocat|tomate|tomate cerise|carotte|courgette|aubergine|poivron|concombre|salade|laitue|mâche|roquette|épinard|chou|chou-fleur|brocoli|poireau|oignon|échalote|ail|potimarron|potiron|citrouille|courge|butternut|champignon|haricot vert|navet|radis|betterave|céleri|fenouil|endive|artichaut|asperge|panais|persil|coriandre|basilic|ciboulette|menthe|gingembre",
  boulangerie: "pain|baguette|brioche|pain de mie|pain de campagne|croissant|pain au chocolat|viennoiserie",
  boucherie:
    "viande|boeuf|veau|porc|agneau|poulet|blanc de poulet|dinde|canard|magret|lapin|lardon|jambon|bacon|saucisse|saucisson|chorizo|merguez|steak|steak haché|viande hachée|escalope|rôti|gésier|poisson|saumon|cabillaud|colin|truite|thon|crevette|moule|calamar|noix de saint-jacques",
  cremerie:
    "lait|beurre|crème|crème fraîche|oeuf|yaourt|fromage|fromage blanc|petit suisse|emmental|gruyère|parmesan|mozzarella|comté|chèvre|feta|ricotta|mascarpone|camembert|reblochon|raclette|pâte feuilletée|pâte brisée|pâte sablée|pâte à pizza",
  epicerie:
    "farine|sucre|sel|poivre|huile|vinaigre|moutarde|riz|pâtes|spaghetti|tagliatelle|coquillette|penne|lasagne|feuille de lasagne|semoule|quinoa|boulgour|lentille|pois chiche|haricot rouge|haricot blanc|conserve|coulis|coulis de tomate|concentré de tomate|sauce tomate|tomate pelée|bouillon|cube de bouillon|chocolat|cacao|café|thé|miel|confiture|pâte à tartiner|levure|vanille|sucre vanillé|cannelle|muscade|noix de muscade|cumin|curry|paprika|épice|herbes de provence|thym|laurier|origan|amande|noisette|noix|cerneau de noix|cacahuète|raisin sec|biscuit|céréale|flocon d'avoine|compote|lait de coco|crème de coco|maïzena|fécule|chapelure|olive|cornichon|câpre|sauce soja|ketchup|mayonnaise|gélatine|sirop|bicarbonate",
  surgeles: "surgelé|glace|sorbet|frite|poêlée",
  boissons: "eau|eau gazeuse|jus|jus d'orange|soda|limonade|vin|vin blanc|vin rouge|bière|cidre",
  "bebe-hygiene":
    "couche|lingette|lait infantile|petit pot|savon|shampoing|gel douche|dentifrice|brosse à dents|déodorant|coton|mouchoir|crème solaire",
  maison:
    "papier toilette|essuie-tout|sopalin|liquide vaisselle|pastille lave-vaisselle|lessive|adoucissant|éponge|sac poubelle|nettoyant|javel|film alimentaire|papier aluminium|papier cuisson|pile|ampoule",
};

const AISLE_RULES: [string, AisleId][] = Object.entries(AISLE_WORDS)
  .flatMap(([aisle, words]) => words.split("|").map((w): [string, AisleId] => [ingredientKey(w), aisle as AisleId]))
  .filter(([w]) => w);

// « d'ail » se lit « d' ail » : un mot-clé se trouve aussi après une élision
const spaced = (s: string) => ` ${s.replace(/'/g, "' ")} `;
const hasWords = (product: string, words: string) => spaced(product).includes(spaced(words));

/** Rayon d'un produit (clé de ingredientKey) d'après le dictionnaire ; « autres » sinon. */
export function aisleOf(product: string): AisleId {
  let best: AisleId = "autres";
  let length = 0;
  for (const [w, aisle] of AISLE_RULES) {
    if (w.length > length && hasWords(product, w)) {
      best = aisle;
      length = w.length;
    }
  }
  return best;
}

// Produits de base qu'on a souvent déjà : la liste demande « Déjà dans le placard ? »
const STAPLES = new Set(
  "sel|gros sel|fleur de sel|sel et poivre|poivre|poivre noir|huile|huile d'olive|huile de tournesol|huile de colza|huile neutre|vinaigre|vinaigre balsamique|farine|farine de blé|sucre|sucre en poudre|sucre roux|sucre glace|sucre vanillé|cassonade|levure|levure chimique|levure boulangère|bicarbonate|maïzena|muscade|noix de muscade|cannelle|cumin|curry|paprika|piment d'espelette|herbes de provence|thym|laurier|origan|moutarde|eau|eau tiède|eau froide|eau chaude"
    .split("|")
    .map(ingredientKey),
);

/** Produit de base (sel, huile, farine, épices…) qu'on a probablement au placard. */
export const isStaple = (product: string) => STAPLES.has(product);

// Unités qu'on achète à la pièce : arrondies au-dessus quand les parts changent
const WHOLE_UNITS = ["", "sachet", "tranche", "gousse", "boîte", "pot", "botte", "brin", "feuille", "morceau"];

/**
 * Quantité recalculée pour d'autres parts (factor = parts choisies / parts de la
 * recette), arrondie : à la pièce au-dessus, poids et volumes au 5 près au-delà
 * de 50, le reste à la demie. Une pincée reste une pincée.
 */
export function scaleQty(qty: number, factor: number, unit: string): number {
  if (factor === 1 || unit === "pincée") return qty;
  return roundScaled(qty * factor, unit);
}

function roundScaled(qty: number, unit: string): number {
  if (WHOLE_UNITS.includes(unit)) return Math.max(1, Math.ceil(qty - 0.01));
  if (unit === "g" || unit === "ml") return qty >= 50 ? Math.round(qty / 5) * 5 : Math.max(0.5, Math.round(qty * 2) / 2);
  return Math.max(0.5, Math.round(qty * 2) / 2);
}

/** Une recette au menu, avec les parts choisies et celles prévues par la recette. */
export interface MenuRecipe {
  title: string;
  ingredients: string[];
  baseServings: number;
  servings: number;
}

/** Un article ajouté à la main. */
export interface FreeItem {
  id: string;
  label: string;
  quantity: string;
  aisle: string;
  addedBy: string;
}

export interface ShoppingLine {
  /** Clé de la ligne pour la cocher : produit + quantité (« item:<id> » pour un article libre). */
  key: string;
  /** Produit (ingredientKey du nom) : clé des quantités, rayons, placard et retraits. */
  product: string;
  name: string;
  /** Quantité affichée (vide si la recette n'en donne pas). */
  qty: string;
  aisle: AisleId;
  /** Quantité par recette d'où vient la ligne, dans l'ordre du menu. */
  from: { recipe: string; qty: string }[];
  /** Article libre : son id et qui l'a ajouté. */
  itemId?: string;
  addedBy?: string;
  staple: boolean;
  /** Quantité tapée à la main. */
  edited: boolean;
}

interface Totals {
  product: string;
  names: string[];
  totals: Map<string, number>;
  /** Recette → quantités par unité, dans l'ordre du menu. */
  perRecipe: Map<string, Map<string, number>>;
}

const capitalize = (s: string) => s.charAt(0).toUpperCase() + s.slice(1);

function totalsLabel(totals: Map<string, number>): string {
  return [...totals.entries()].sort(([a], [b]) => unitRank(a) - unitRank(b)).map(([u, q]) => formatQty(q, u)).join(" + ");
}

const addTo = (totals: Map<string, number>, unit: string, qty: number) => totals.set(unit, (totals.get(unit) ?? 0) + qty);

/**
 * Lignes de la liste, avant le rangement : ingrédients des recettes du menu
 * recalculés pour leurs parts et additionnés par produit, puis articles libres.
 * Les rayons et quantités choisis à la main par la tribu s'appliquent.
 */
export function buildLines(
  menu: MenuRecipe[],
  items: FreeItem[] = [],
  choices: { quantities?: Record<string, string>; aisles?: Record<string, string> } = {},
): ShoppingLine[] {
  const quantities = choices.quantities ?? {};
  const aisles = choices.aisles ?? {};
  const aisleFor = (product: string, own = "") => {
    const picked = own || aisles[product] || "";
    return isAisle(picked) ? picked : aisleOf(product);
  };
  const groups = new Map<string, Totals>();
  for (const r of menu) {
    const factor = r.baseServings > 0 && r.servings > 0 ? r.servings / r.baseServings : 1;
    for (const raw of r.ingredients) {
      const line = raw.trim();
      if (!line) continue;
      const p = parseIngredient(line);
      const product = ingredientKey(p.name) || ingredientKey(line);
      if (!product) continue;
      const g: Totals = groups.get(product) ?? { product, names: [], totals: new Map(), perRecipe: new Map() };
      g.names.push(p.name);
      const mine = g.perRecipe.get(r.title) ?? new Map<string, number>();
      if (p.qty !== null) {
        const qty = scaleQty(p.qty, factor, p.unit);
        addTo(g.totals, p.unit, qty);
        addTo(mine, p.unit, qty);
      }
      g.perRecipe.set(r.title, mine);
      groups.set(product, g);
    }
  }
  const lines = [...groups.values()].map((g): ShoppingLine => {
    const count = g.totals.get("") ?? 0;
    // À l'unité, au pluriel : la variante du nom au pluriel si la recette l'écrit ainsi
    const picked = count > 1 ? (g.names.find((n) => /[sx]$/i.test(n)) ?? g.names[0]) : g.names[0];
    const typed = quantities[g.product];
    const qty = typed ?? totalsLabel(g.totals);
    return {
      key: `${g.product}|${qty}`,
      product: g.product,
      name: capitalize(picked),
      qty,
      aisle: aisleFor(g.product),
      from: [...g.perRecipe.entries()].map(([recipe, totals]) => ({ recipe, qty: totalsLabel(totals) })),
      staple: isStaple(g.product),
      edited: typed !== undefined,
    };
  });
  for (const it of items) {
    const name = it.label.trim();
    if (!name) continue;
    const product = ingredientKey(name);
    lines.push({
      key: `item:${it.id}`,
      product,
      name: capitalize(name),
      qty: it.quantity.trim(),
      aisle: aisleFor(product, it.aisle),
      from: [],
      itemId: it.id,
      addedBy: it.addedBy,
      staple: false,
      edited: false,
    });
  }
  return lines;
}

export interface ShoppingGroup {
  aisle: AisleId;
  name: string;
  lines: ShoppingLine[];
}

export interface ArrangedList {
  /** À prendre, par rayon dans l'ordre de la tribu. */
  groups: ShoppingGroup[];
  /** Dans le panier (cochées), dans l'ordre des rayons. */
  cart: ShoppingLine[];
  /** Produits de base des recettes, pour « Déjà dans le placard ? » (dans l'ordre des rayons). */
  staples: ShoppingLine[];
  /** Produits de base qu'on a : sortis de la liste. */
  have: ShoppingLine[];
  /** Ingrédients de recette retirés de la liste, à rétablir. */
  removed: ShoppingLine[];
  remaining: number;
  total: number;
}

/** Ordre des rayons : celui de la tribu, complété par l'ordre par défaut. */
export function aisleOrder(order: string[] = []): AisleId[] {
  const own = order.filter(isAisle).filter((a, i, all) => all.indexOf(a) === i);
  return [...own, ...AISLES.map((a) => a.id).filter((a) => !own.includes(a))];
}

/**
 * Range les lignes : retire celles qu'on a au placard ou qu'on a retirées,
 * sépare le panier (lignes cochées) et groupe le reste par rayon, triées par
 * produit dans chaque rayon.
 */
export function arrange(
  lines: ShoppingLine[],
  state: { checked?: string[]; pantry?: string[]; removed?: string[]; aisleOrder?: string[] } = {},
): ArrangedList {
  const checked = new Set(state.checked ?? []);
  const pantry = new Set(state.pantry ?? []);
  const removedSet = new Set(state.removed ?? []);
  const order = aisleOrder(state.aisleOrder);
  const rank = (l: ShoppingLine) => order.indexOf(l.aisle);
  const sorted = [...lines].sort((a, b) => rank(a) - rank(b) || (a.product < b.product ? -1 : a.product > b.product ? 1 : 0));
  const fromRecipe = (l: ShoppingLine) => !l.itemId;
  const removed = sorted.filter((l) => fromRecipe(l) && removedSet.has(l.product));
  const kept = sorted.filter((l) => !(fromRecipe(l) && removedSet.has(l.product)));
  const staples = kept.filter((l) => l.staple);
  const have = staples.filter((l) => pantry.has(l.product));
  const onList = kept.filter((l) => !(l.staple && pantry.has(l.product)));
  const todo = onList.filter((l) => !checked.has(l.key));
  const cart = onList.filter((l) => checked.has(l.key));
  const groups: ShoppingGroup[] = [];
  for (const l of todo) {
    const last = groups[groups.length - 1];
    if (last?.aisle === l.aisle) last.lines.push(l);
    else groups.push({ aisle: l.aisle, name: aisleName(l.aisle), lines: [l] });
  }
  return { groups, cart, staples, have, removed, remaining: todo.length, total: onList.length };
}

// Articles qu'on achète souvent sans recette : proposés tant qu'ils ne sont pas sur la liste
const FREQUENT = ["Lait", "Pain", "Œufs", "Yaourts", "Beurre", "Café", "Pommes", "Bananes", "Fromage", "Papier toilette", "Couches", "Liquide vaisselle"];

/** Suggestions « Souvent » : articles fréquents absents de la liste, avec leur rayon. */
export function suggestions(lines: ShoppingLine[], max = 4): { label: string; aisle: AisleId }[] {
  const present = new Set(lines.map((l) => l.product));
  return FREQUENT.filter((label) => !present.has(ingredientKey(label)))
    .slice(0, max)
    .map((label) => ({ label, aisle: aisleOf(ingredientKey(label)) }));
}

const SPOKEN_NUMBERS: Record<string, string> = { deux: "2", trois: "3", quatre: "4", cinq: "5", six: "6", sept: "7", huit: "8", neuf: "9", dix: "10", douze: "12" };

/**
 * Courses dictées : « du pain et du sucre, 2 baguettes » → ["Pain", "Sucre", "2 baguettes"].
 * Coupe sur les virgules, points, « et », « puis », retire l'article de tête
 * et écrit en chiffres le nombre dit en lettres (« trois yaourts » → « 3 yaourts »).
 */
export function splitSpoken(text: string): string[] {
  return text
    .split(/\s*(?:[,;\n]|\.(?=\s|$))\s*|\s+(?:et|puis)\s+/i)
    .map((part) => {
      const words = part
        .trim()
        .replace(/[.!?]+$/, "")
        .replace(/^(?:(?:du|des|de la|les|le|la|une|un)\s+|(?:de l|d|l)['’]\s*)/i, "")
        .trim()
        .split(/\s+/);
      const n = SPOKEN_NUMBERS[words[0]?.toLowerCase()];
      if (n && words.length > 1) words[0] = n;
      const item = words.join(" ");
      return item.charAt(0).toUpperCase() + item.slice(1);
    })
    .filter(Boolean);
}

/** La liste en texte, pour quelqu'un sans Tribbae : un rayon par ligne. */
export function shareText(list: ArrangedList): string {
  const head = `Courses Tribbae — ${list.remaining} ${list.remaining > 1 ? "articles" : "article"}`;
  const body = list.groups.map(
    (g) => `${g.name} : ${g.lines.map((l) => l.name.toLowerCase() + (l.qty ? ` (${l.qty})` : "")).join(", ")}`,
  );
  return [head, ...body].join("\n");
}

// ---------------------------------------------------------------------------
// La liste partagée telle que le serveur la renvoie, et les opérations qui la
// changent. applyOp rejoue une opération sur l'appareil, sans attendre le
// serveur (affichage immédiat, et hors ligne sur Android) ; la réponse du
// serveur, qui applique les mêmes règles (internal/shopping), la remplace.
// ---------------------------------------------------------------------------

export interface MenuEntry {
  linkId: string;
  title: string;
  imageUrl?: string;
  ingredients: string[];
  baseServings: number;
  servings: number;
  addedBy?: string;
  addedAt?: number;
  /** Marquée « C'est fait » depuis son ajout : estompée, quitte le menu le lendemain. */
  cooked?: boolean;
}

export interface SharedList {
  version: number;
  menu: MenuEntry[];
  items: (FreeItem & { addedAt?: number })[];
  checked: { key: string; by: string; at?: number }[];
  quantities: { key: string; value: string }[];
  aisles: { key: string; value: string }[];
  aisleOrder: string[];
  pantry: string[];
  removed: string[];
  shopperId: string;
  shoppingSince: number;
}

export type ShoppingOpType =
  | "menu_add" | "menu_servings" | "menu_remove"
  | "item_add" | "item_update" | "item_remove"
  | "check" | "uncheck" | "quantity" | "aisle" | "aisle_order"
  | "pantry_have" | "pantry_out" | "remove" | "restore"
  | "shopping_start" | "shopping_stop" | "finish" | "clear";

export interface ShoppingOp {
  type: ShoppingOpType;
  linkId?: string;
  servings?: number;
  id?: string;
  label?: string;
  quantity?: string;
  aisle?: string;
  key?: string;
  value?: string;
  order?: string[];
}

export const emptyList = (): SharedList => ({
  version: 0, menu: [], items: [], checked: [], quantities: [], aisles: [], aisleOrder: [], pantry: [], removed: [], shopperId: "", shoppingSince: 0,
});

/** Recette ajoutée au menu sur l'appareil : ce qu'il faut pour en tirer les ingrédients. */
export interface RecipeInfo {
  title: string;
  imageUrl?: string;
  ingredients: string[];
  /** Nombre de personnes prévu par la recette (0 = non précisé : 4). */
  servings?: number;
}

const DEFAULT_SERVINGS = 4;
const tidy = (s = "") => s.trim().split(/\s+/).filter(Boolean).join(" ");
const setValue = (vs: { key: string; value: string }[], key: string, value: string) =>
  value ? (vs.some((v) => v.key === key) ? vs.map((v) => (v.key === key ? { key, value } : v)) : [...vs, { key, value }]) : vs.filter((v) => v.key !== key);
const addKey = (keys: string[], key: string) => (keys.includes(key) ? keys : [...keys, key]);

/**
 * Applique une opération à la liste, comme le serveur : renvoie une nouvelle
 * liste (la version ne change pas, c'est le serveur qui la donne). [now] en
 * secondes ; [recipe] fournit le contenu d'une recette ajoutée au menu.
 */
export function applyOp(list: SharedList, op: ShoppingOp, userId: string, now: number, recipe?: (linkId: string) => RecipeInfo | undefined): SharedList {
  const key = (op.key ?? "").trim();
  switch (op.type) {
    case "menu_add": {
      const id = op.linkId ?? "";
      if (list.menu.some((e) => e.linkId === id)) {
        return op.servings ? applyOp(list, { type: "menu_servings", linkId: id, servings: op.servings }, userId, now) : list;
      }
      const r = recipe?.(id);
      if (!r) return list;
      const base = r.servings && r.servings > 0 ? r.servings : DEFAULT_SERVINGS;
      const entry: MenuEntry = {
        linkId: id, title: r.title, imageUrl: r.imageUrl, ingredients: r.ingredients, baseServings: base,
        servings: op.servings && op.servings > 0 ? op.servings : base, addedBy: userId, addedAt: now, cooked: false,
      };
      return { ...list, menu: [...list.menu, entry] };
    }
    case "menu_servings":
      if (!op.servings || op.servings < 1) return list;
      return { ...list, menu: list.menu.map((e) => (e.linkId === op.linkId ? { ...e, servings: op.servings! } : e)) };
    case "menu_remove":
      return { ...list, menu: list.menu.filter((e) => e.linkId !== op.linkId) };
    case "item_add": {
      const label = tidy(op.label);
      if (!label || !op.id || list.items.some((it) => it.id === op.id)) return list;
      return { ...list, items: [...list.items, { id: op.id, label, quantity: tidy(op.quantity), aisle: (op.aisle ?? "").trim(), addedBy: userId, addedAt: now }] };
    }
    case "item_update": {
      const label = tidy(op.label);
      if (!label) return list;
      return { ...list, items: list.items.map((it) => (it.id === op.id ? { ...it, label, quantity: tidy(op.quantity), aisle: (op.aisle ?? "").trim() } : it)) };
    }
    case "item_remove":
      return { ...list, items: list.items.filter((it) => it.id !== op.id), checked: list.checked.filter((c) => c.key !== `item:${op.id}`) };
    case "check":
      if (!key) return list;
      return { ...list, checked: [...list.checked.filter((c) => c.key !== key), { key, by: userId, at: now }] };
    case "uncheck":
      return { ...list, checked: list.checked.filter((c) => c.key !== op.key) };
    case "quantity":
      return key ? { ...list, quantities: setValue(list.quantities, key, (op.value ?? "").trim()) } : list;
    case "aisle":
      return key ? { ...list, aisles: setValue(list.aisles, key, (op.value ?? "").trim()) } : list;
    case "aisle_order":
      return { ...list, aisleOrder: op.order ?? [] };
    case "pantry_have":
      return key ? { ...list, pantry: addKey(list.pantry, key) } : list;
    case "pantry_out":
      return { ...list, pantry: list.pantry.filter((k) => k !== op.key) };
    case "remove":
      return key ? { ...list, removed: addKey(list.removed, key) } : list;
    case "restore":
      return { ...list, removed: list.removed.filter((k) => k !== op.key) };
    case "shopping_start":
      return { ...list, shopperId: userId, shoppingSince: now };
    case "shopping_stop":
      return list.shopperId === userId ? { ...list, shopperId: "", shoppingSince: 0 } : list;
    case "finish": {
      // Le panier se vide : lignes cochées et articles libres achetés ; menu, placard et rayons restent
      const bought = new Set(list.checked.map((c) => c.key));
      return { ...list, items: list.items.filter((it) => !bought.has(`item:${it.id}`)), checked: [], shopperId: "", shoppingSince: 0 };
    }
    case "clear":
      // Nouvelle liste : on garde le placard, les rayons et leur ordre
      return { ...list, menu: [], items: [], checked: [], quantities: [], removed: [], shopperId: "", shoppingSince: 0 };
  }
  return list;
}

/** La liste prête à afficher : lignes construites puis rangées. */
export function viewOf(list: SharedList): { lines: ShoppingLine[]; arranged: ArrangedList } {
  const toMap = (vs: { key: string; value: string }[]) => Object.fromEntries(vs.map((v) => [v.key, v.value]));
  const lines = buildLines(list.menu, list.items, { quantities: toMap(list.quantities), aisles: toMap(list.aisles) });
  const arranged = arrange(lines, { checked: list.checked.map((c) => c.key), pantry: list.pantry, removed: list.removed, aisleOrder: list.aisleOrder });
  return { lines, arranged };
}
