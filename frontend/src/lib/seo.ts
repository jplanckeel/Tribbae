import type { CategoryMeta } from "./categories";
import { eventDateMs, formatLongDate } from "./format";
import type { Link } from "./types";

// Métadonnées des pages d'idées, identiques à celles du serveur
// (backend/internal/seo/meta.go) : garder les deux en phase.

/** Avec « · Tribbae », environ 60 caractères, ce que les moteurs affichent. */
const MAX_IDEA_TITLE = 50;

/** Segments de titre propres aux marchands, sans rapport avec l'idée. */
const TITLE_NOISE = new Set(["achat livre", "livre", "cartonné", "broché", "poche", "relié", "ebook"]);

/** Coupe un texte à `n` caractères sur une fin de mot, espaces normalisés. */
export function clip(s: string, n: number): string {
  const t = s.replace(/\s+/g, " ").trim();
  if ([...t].length <= n) return t;
  const cut = [...t].slice(0, n - 1).join("");
  const space = cut.lastIndexOf(" ");
  return `${(space > n / 2 ? cut.slice(0, space) : cut).replace(/[\s,.;:]+$/, "")}…`;
}

function dedupeList(s: string): string {
  const seen = new Set<string>();
  return s
    .split(", ")
    .filter((e) => {
      const k = e.toLowerCase();
      if (seen.has(k)) return false;
      seen.add(k);
      return true;
    })
    .join(", ");
}

/**
 * Retire d'un titre le nom du site (après « | »), les mentions entre crochets,
 * les segments marchands et les noms répétés, puis le raccourcit en abandonnant
 * d'abord les derniers segments « - ».
 */
export function cleanTitle(raw: string): string {
  const s = raw.replace(/\s+/g, " ").trim();
  const head = s.split(" | ")[0].replace(/\s*\[[^\]]*\]/g, "");
  const parts = head
    .split(" - ")
    .map((p) => dedupeList(p.trim()))
    .filter((p) => p && !TITLE_NOISE.has(p.toLowerCase()));
  if (parts.length === 0) return clip(s, MAX_IDEA_TITLE);
  while (parts.length > 1 && [...parts.join(" - ")].length > MAX_IDEA_TITLE) parts.pop();
  return clip(parts.join(" - "), MAX_IDEA_TITLE);
}

export const ideaSeoTitle = (link: Pick<Link, "title">) => `${cleanTitle(link.title)} · Tribbae`;

function forAges(age: string): string {
  if (age.toLowerCase() === "tous âges") return "pour tous les âges";
  return /^\d/.test(age) ? `pour les ${age}` : `âge : ${age}`;
}

/**
 * « Activité partagée par Alice sur Tribbae, pour les 3–6 ans : » suivi de la
 * description de l'idée, ou à défaut de son titre et de ses informations.
 */
export function ideaSeoDescription(link: Link, cat: CategoryMeta): string {
  const shared = cat.key === "LINK_CATEGORY_CADEAU" || cat.key === "LINK_CATEGORY_LIVRE" ? "partagé" : "partagée";
  let d = `${cat.label} ${shared}${link.ownerDisplayName ? ` par ${link.ownerDisplayName}` : ""} sur Tribbae`;
  if (link.ageRange) d += `, ${forAges(link.ageRange)}`;
  const desc = link.description?.trim();
  if (desc && desc.toLowerCase() !== link.title.trim().toLowerCase()) return `${d} : ${desc}`;
  d += ` : ${cleanTitle(link.title)}.`;
  if (link.price) d += ` Prix : ${link.price}.`;
  if (link.location) d += ` Lieu : ${link.location}.`;
  const date = eventDateMs(link.eventDate);
  if (date) d += ` Date : ${formatLongDate(date)}.`;
  return d;
}
