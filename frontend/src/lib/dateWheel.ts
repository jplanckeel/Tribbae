// Sélecteur de date à roulettes : colonnes jour / mois / année (+ heure / minute
// pour une sortie). Règles communes avec Android : data/DateWheel.kt
// (dateWheel.test.ts ↔ DateWheelTest, garder les mêmes cas).

/**
 * Usage de la date, qui fixe les bornes et l'heure enregistrée :
 * - `event` : date (et heure) d'une idée, heure locale choisie ;
 * - `key` : date clé d'un dossier, à venir, minuit local ;
 * - `anniv` : date clé « anniversaire » d'un dossier, passée ou future, minuit local ;
 * - `birth` : naissance d'un enfant, au plus aujourd'hui, midi local (la date ne glisse pas d'un fuseau à l'autre).
 */
export type DateKind = "event" | "key" | "anniv" | "birth";

export interface DateParts {
  year: number;
  /** 1 à 12 */
  month: number;
  day: number;
  hour: number;
  minute: number;
}

export const MONTHS = ["janvier", "février", "mars", "avril", "mai", "juin", "juillet", "août", "septembre", "octobre", "novembre", "décembre"];

export function daysInMonth(year: number, month: number): number {
  return new Date(year, month, 0).getDate();
}

/** Parties d'un instant, en heure locale. */
export function partsOf(ms: number): DateParts {
  const d = new Date(ms);
  return { year: d.getFullYear(), month: d.getMonth() + 1, day: d.getDate(), hour: d.getHours(), minute: d.getMinutes() };
}

/** Instant enregistré pour ces parties, selon l'usage (voir DateKind). */
export function toMs(p: DateParts, kind: DateKind): number {
  if (kind === "event") return new Date(p.year, p.month - 1, p.day, p.hour, p.minute).getTime();
  return new Date(p.year, p.month - 1, p.day, kind === "birth" ? 12 : 0, 0).getTime();
}

const rank = (p: DateParts) => (((p.year * 100 + p.month) * 100 + p.day) * 100 + p.hour) * 100 + p.minute;
const day = (year: number, month: number, d: number, hour = 0, minute = 0): DateParts => ({ year, month, day: d, hour, minute });

/**
 * Bornes de l'usage. Une valeur déjà enregistrée hors des bornes (vieille sortie,
 * date clé passée) les élargit, pour rester affichable telle quelle.
 */
export function bounds(kind: DateKind, now: number, value?: number): { min: DateParts; max: DateParts } {
  const n = partsOf(now);
  const today = day(n.year, n.month, n.day);
  let min: DateParts;
  let max: DateParts;
  if (kind === "birth") {
    min = day(n.year - 30, 1, 1);
    max = day(n.year, n.month, n.day, 23, 59);
  } else if (kind === "key") {
    min = today;
    max = day(n.year + 10, 12, 31, 23, 59);
  } else if (kind === "anniv") {
    min = day(n.year - 100, 1, 1);
    max = day(n.year + 10, 12, 31, 23, 59);
  } else {
    min = day(n.year - 5, 1, 1);
    max = day(n.year + 10, 12, 31, 23, 59);
  }
  if (value) {
    const v = partsOf(value);
    if (rank(v) < rank(min)) min = day(v.year, v.month, v.day);
    if (rank(v) > rank(max)) max = day(v.year, v.month, v.day, 23, 59);
  }
  return { min, max };
}

/** Ramène le jour dans le mois, puis la date entre les bornes. */
export function clamp(p: DateParts, min: DateParts, max: DateParts): DateParts {
  const fixed = { ...p, day: Math.min(p.day, daysInMonth(p.year, p.month)) };
  if (rank(fixed) < rank(min)) return { ...min };
  if (rank(fixed) > rank(max)) return { ...max };
  return fixed;
}

/** Change une colonne : « 31 janvier » → février donne le 28 (ou 29). */
export function setPart(p: DateParts, field: keyof DateParts, v: number, min: DateParts, max: DateParts): DateParts {
  return clamp({ ...p, [field]: v }, min, max);
}

const range = (from: number, to: number) => Array.from({ length: Math.max(0, to - from + 1) }, (_, i) => from + i);

export const yearOptions = (min: DateParts, max: DateParts) => range(min.year, max.year);

export function monthOptions(year: number, min: DateParts, max: DateParts): number[] {
  return range(year === min.year ? min.month : 1, year === max.year ? max.month : 12);
}

export function dayOptions(year: number, month: number, min: DateParts, max: DateParts): number[] {
  const first = year === min.year && month === min.month ? min.day : 1;
  const last = year === max.year && month === max.month ? max.day : daysInMonth(year, month);
  return range(first, last);
}

export const HOURS = range(0, 23);

/** Minutes de 5 en 5, plus la minute déjà enregistrée si elle tombe entre deux. */
export function minuteOptions(current: number): number[] {
  const steps = range(0, 11).map((i) => i * 5);
  return steps.includes(current) ? steps : [...steps, current].sort((a, b) => a - b);
}

/** Raccourcis au-dessus des roulettes (date seule : l'heure déjà choisie est gardée). */
export function shortcuts(kind: DateKind, now: number): { label: string; date: DateParts }[] {
  if (kind !== "event" && kind !== "key") return [];
  const d = new Date(now);
  const at = (offset: number) => partsOf(new Date(d.getFullYear(), d.getMonth(), d.getDate() + offset).getTime());
  // Samedi qui vient ; le week-end en cours s'il a commencé
  const dow = d.getDay();
  const weekend = dow === 6 || dow === 0 ? 0 : 6 - dow;
  const list = [
    { label: "Aujourd'hui", date: at(0) },
    { label: "Demain", date: at(1) },
    { label: "Ce week-end", date: at(weekend) },
  ];
  // Un même jour une seule fois (samedi : « Ce week-end » est « Aujourd'hui »)
  if (kind === "event") return list.filter((s, i) => list.findIndex((o) => o.date.day === s.date.day && o.date.month === s.date.month) === i);
  // Même jour le mois suivant, ramené au dernier jour du mois (31 janvier → 28 ou 29 février)
  const n = partsOf(now);
  const [y, m] = n.month === 12 ? [n.year + 1, 1] : [n.year, n.month + 1];
  return [list[2], { label: "Dans un mois", date: day(y, m, Math.min(n.day, daysInMonth(y, m))) }];
}

/** « 14 mars 2027 », « 14 mars 2027 à 15 h 30 » (lecteurs d'écran, résumé du sélecteur). */
export function describe(p: DateParts, withTime: boolean): string {
  const date = `${p.day === 1 ? "1er" : p.day} ${MONTHS[p.month - 1]} ${p.year}`;
  if (!withTime) return date;
  return `${date} à ${p.hour} h ${String(p.minute).padStart(2, "0")}`;
}
