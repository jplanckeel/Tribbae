// Tranches d'âge des idées. La valeur reste un texte (« 3–6 ans ») pour ne rien
// changer côté serveur ni aux idées existantes, mais le sélecteur l'écrit
// toujours de la même façon, et ce module sait la relire. Même logique dans
// l'app Android : composeApp/.../data/AgeRange.kt (garder les deux alignés).

/** Âge maximal « ouvert » (« 10 ans et + », « Tous âges »). */
export const OPEN_AGE = 99;

/** Tranche [min, max] en années (0.5 = 6 mois). */
export type AgeRange = [number, number];

/** Tranches proposées en un toucher : les mêmes que les filtres de l'Explorer. */
export const AGE_BANDS: { key: string; label: string; range: AgeRange }[] = [
  { key: "0-3", label: "0–3", range: [0, 3] },
  { key: "3-6", label: "3–6", range: [3, 6] },
  { key: "6-10", label: "6–10", range: [6, 10] },
  { key: "10+", label: "10+", range: [10, OPEN_AGE] },
];

/** Crans des réglages précis : par 6 mois jusqu'à 2 ans, puis par an. */
export const AGE_STOPS: number[] = [0, 0.5, 1, 1.5, ...Array.from({ length: 17 }, (_, i) => i + 2), OPEN_AGE];

const ALL_AGES = /tous\s+(les\s+)?âges|tout\s+âge|toute la famille/;

/**
 * Relit une tranche d'âge, qu'elle vienne du sélecteur ou d'une saisie libre
 * plus ancienne : « 3–6 ans », « 18 mois – 3 ans », « 6–18 mois », « dès 4 ans »,
 * « 10+ », « Tous âges ». Chaque nombre prend l'unité qui le suit (ou celle du
 * nombre suivant : « 3–6 ans »). null si rien d'exploitable.
 */
export function parseAgeRange(text: string | undefined): AgeRange | null {
  if (!text) return null;
  const lower = text.toLowerCase();
  if (ALL_AGES.test(lower)) return [0, OPEN_AGE];
  const tokens = [...lower.matchAll(/(\d+(?:[.,]\d+)?)\s*(mois|ans?)?/g)].map((m) => ({
    n: parseFloat(m[1].replace(",", ".")),
    unit: m[2] as string | undefined,
  }));
  if (!tokens.length) return null;
  for (let i = tokens.length - 2; i >= 0; i--) tokens[i].unit ??= tokens[i + 1].unit;
  const years = tokens.map((t) => (t.unit === "mois" ? t.n / 12 : t.n));
  const min = years[0];
  const open = years.length === 1 && /\+|dès|des |à partir|plus/.test(lower);
  const max = years.length > 1 ? years[years.length - 1] : open ? OPEN_AGE : min;
  return [Math.min(min, max), Math.max(min, max)];
}

/** Vrai si la tranche de l'idée chevauche [min, max] (années). */
export function matchesAge(text: string | undefined, min: number, max: number): boolean {
  const r = parseAgeRange(text);
  return !!r && r[0] <= max && r[1] >= min;
}

const isMonths = (y: number) => y < 2 && !Number.isInteger(y);

/** Un âge seul : « 6 mois », « 1 an », « 18 mois », « 8 ans ». */
export function ageLabel(y: number): string {
  if (isMonths(y) || y === 0) return `${Math.round(y * 12)} mois`;
  return y === 1 ? "1 an" : `${y} ans`;
}

/** Écrit une tranche comme le sélecteur l'affiche et l'enregistre. */
export function formatAgeRange([min, max]: AgeRange): string {
  if (max >= OPEN_AGE) return min <= 0 ? "Tous âges" : `${ageLabel(min)} et +`;
  if (min === max) return ageLabel(min);
  // Deux âges en mois (« 6–18 mois ») ou en années (« 3–6 ans »)
  if (max < 2 && (isMonths(min) || isMonths(max) || min === 0)) return `${Math.round(min * 12)}–${Math.round(max * 12)} mois`;
  if (!isMonths(min) && !isMonths(max)) return `${min}–${max} ans`;
  return `${ageLabel(min)} – ${ageLabel(max)}`;
}

/** Cran le plus proche (en dessous) d'un âge réel : celui d'un enfant. */
export function snapAge(years: number): number {
  let best = 0;
  for (const s of AGE_STOPS) if (s <= years && s < OPEN_AGE) best = s;
  return best;
}

/** Plus petite tranche contenant les deux. */
export const hull = (a: AgeRange, b: AgeRange): AgeRange => [Math.min(a[0], b[0]), Math.max(a[1], b[1])];

/** La tranche a couvre entièrement b. */
export const covers = (a: AgeRange, b: AgeRange) => a[0] <= b[0] && a[1] >= b[1];
