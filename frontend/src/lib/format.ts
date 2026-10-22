import type { Child, Link } from "./types";

/**
 * Retourne une URL utilisable dans un href uniquement si elle est en http(s).
 * Protège contre les liens `javascript:` ou `data:` enregistrés par un tiers.
 */
export function safeHref(url: string | undefined): string | undefined {
  if (!url) return undefined;
  try {
    const u = new URL(url.trim());
    return u.protocol === "http:" || u.protocol === "https:" ? u.href : undefined;
  } catch {
    return undefined;
  }
}

/** Image affichable par le navigateur (http(s) seulement : les file:// viennent du mobile). */
export function safeImage(url: string | undefined): string | undefined {
  return safeHref(url);
}

export function hostname(url: string | undefined): string {
  const href = safeHref(url);
  if (!href) return "";
  return new URL(href).hostname.replace(/^www\./, "");
}

/** Date d'événement en ms (le mobile stocke des ms, d'anciennes données web des secondes). */
export function eventDateMs(v: Link["eventDate"]): number | null {
  const n = Number(v);
  if (!n || !Number.isFinite(n)) return null;
  return n < 1e11 ? n * 1000 : n;
}

const dayFmt = new Intl.DateTimeFormat("fr-FR", { weekday: "long", day: "numeric", month: "long" });
const shortFmt = new Intl.DateTimeFormat("fr-FR", { weekday: "short", day: "numeric", month: "short" });
const longFmt = new Intl.DateTimeFormat("fr-FR", { day: "numeric", month: "long", year: "numeric" });

const capitalize = (s: string) => s.charAt(0).toUpperCase() + s.slice(1);

export const formatToday = (d = new Date()) => capitalize(dayFmt.format(d));
export const formatShortDate = (ms: number) => capitalize(shortFmt.format(ms));
export const formatLongDate = (v: string | number | Date) => longFmt.format(new Date(v));

export function formatTime(ms: number): string {
  const d = new Date(ms);
  if (d.getHours() === 0 && d.getMinutes() === 0) return "";
  return `${d.getHours()}h${d.getMinutes() ? String(d.getMinutes()).padStart(2, "0") : ""}`;
}

/** "Sam." et "19" pour la vignette calendrier. */
export function dayBadge(ms: number): { weekday: string; day: string } {
  const d = new Date(ms);
  const wd = new Intl.DateTimeFormat("fr-FR", { weekday: "short" }).format(d);
  return { weekday: capitalize(wd.replace(".", "")) + ".", day: String(d.getDate()) };
}

export function ageInMonths(birthDate: Child["birthDate"]): number {
  const b = new Date(Number(birthDate));
  const now = new Date();
  return Math.max(0, (now.getFullYear() - b.getFullYear()) * 12 + now.getMonth() - b.getMonth() - (now.getDate() < b.getDate() ? 1 : 0));
}

export function formatAge(birthDate: Child["birthDate"]): string {
  const m = ageInMonths(birthDate);
  if (m < 24) return `${m} mois`;
  return `${Math.floor(m / 12)} ans`;
}

// Tranches d'âge : voir lib/age.ts
export { matchesAge, parseAgeRange } from "./age";

export function initial(name: string | undefined): string {
  return (name?.trim().charAt(0) || "?").toUpperCase();
}

export const plural = (n: number, one: string, many: string) => `${n} ${n > 1 ? many : one}`;

/** Ligne de métadonnées courte : "3–10 ans · 6 €". */
/** Prix affiché : « 0 », « 0 € »… deviennent « Gratuit ». */
export function priceLabel(price: string | undefined): string {
  const p = (price ?? "").trim();
  return /^0+(?:[.,]0+)?\s*(?:€|eur|euros?)?$/i.test(p) ? "Gratuit" : p;
}

export function metaLine(l: Link): string {
  return [l.ageRange, priceLabel(l.price)].filter(Boolean).join(" · ");
}

export function matchesQuery(l: Link, q: string): boolean {
  if (!q) return true;
  const s = q.toLowerCase();
  return [l.title, l.description, l.location, ...(l.tags ?? [])].some((v) => v?.toLowerCase().includes(s));
}

/** N'accepte que des chemins internes (évite les redirections ouvertes « //site.tld »). */
export function safeNext(raw: string | null): string {
  if (!raw || !raw.startsWith("/") || raw.startsWith("//") || raw.startsWith("/\\")) return "/";
  return raw;
}

/**
 * Prix comparable d'une idée, en euros : 0 si gratuite, le premier montant
 * trouvé sinon (« 6 € / enfant » → 6, « dès 12,50 € » → 12.5), undefined si
 * le prix n'est pas renseigné ou illisible.
 */
export function priceValue(price: string | undefined): number | undefined {
  const p = (price ?? "").trim().toLowerCase();
  if (!p) return undefined;
  if (/gratuit|free|offert/.test(p)) return 0;
  const m = p.match(/\d+(?:[.,]\d+)?/);
  return m ? Number(m[0].replace(",", ".")) : undefined;
}
