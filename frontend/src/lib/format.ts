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

/**
 * Extrait une tranche d'âge [min, max] en années depuis un texte libre
 * ("3–10 ans", "18 mois", "6+", "dès 4 ans"). null si rien d'exploitable.
 */
export function parseAgeRange(text: string | undefined): [number, number] | null {
  if (!text) return null;
  const lower = text.toLowerCase();
  const nums = [...lower.matchAll(/\d+(?:[.,]\d+)?/g)].map((m) => parseFloat(m[0].replace(",", ".")));
  if (nums.length === 0) return null;
  const factor = lower.includes("mois") && !lower.includes("an") ? 1 / 12 : 1;
  const min = nums[0] * factor;
  const openEnded = nums.length === 1 && /\+|dès|des |à partir|plus/.test(lower);
  const max = nums.length > 1 ? nums[nums.length - 1] * factor : openEnded ? 99 : min;
  return [Math.min(min, max), Math.max(min, max)];
}

/** Vrai si la tranche d'âge de l'idée chevauche la tranche [min, max] (années). */
export function matchesAge(text: string | undefined, min: number, max: number): boolean {
  const r = parseAgeRange(text);
  if (!r) return false;
  return r[0] <= max && r[1] >= min;
}

export function initial(name: string | undefined): string {
  return (name?.trim().charAt(0) || "?").toUpperCase();
}

export const plural = (n: number, one: string, many: string) => `${n} ${n > 1 ? many : one}`;

/** Ligne de métadonnées courte : "3–10 ans · 6 €". */
export function metaLine(l: Link): string {
  return [l.ageRange, l.price].filter(Boolean).join(" · ");
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
