import { normalizeCategory } from "./categories";
import { eventDateMs } from "./format";
import type { Folder, Link, LinkInput } from "./types";

/** Reconstitue les champs modifiables d'une idée (le PUT remplace tous les champs). */
export function toInput(l: Link): LinkInput {
  return {
    title: l.title,
    url: l.url ?? "",
    description: l.description ?? "",
    category: normalizeCategory(l.category),
    tags: l.tags ?? [],
    ageRange: l.ageRange ?? "",
    location: l.location ?? "",
    price: l.price ?? "",
    imageUrl: l.imageUrl ?? "",
    eventDate: eventDateMs(l.eventDate) ?? 0,
    reminderEnabled: !!l.reminderEnabled,
    rating: l.rating ?? 0,
    ingredients: l.ingredients ?? [],
    folderId: l.folderId ?? "",
    visibility: l.visibility === "public" ? "public" : "private",
  };
}

/** Copie d'une idée d'un autre membre vers ses propres idées. */
export function copyInput(l: Link, folderId?: string): LinkInput {
  return { ...toInput(l), folderId: folderId ?? "", visibility: "private", eventDate: 0, reminderEnabled: false };
}

const startOfToday = () => {
  const d = new Date();
  d.setHours(0, 0, 0, 0);
  return d.getTime();
};

/** Idées datées à venir (aujourd'hui inclus), de la plus proche à la plus lointaine. */
export function upcoming(links: Link[]): (Link & { at: number })[] {
  const from = startOfToday();
  return links
    .map((l) => ({ ...l, at: eventDateMs(l.eventDate) ?? 0 }))
    .filter((l) => l.at >= from)
    .sort((a, b) => a.at - b.at);
}

/** Rappels des 7 prochains jours (pastille de la cloche). */
export function soonReminders(links: Link[]): (Link & { at: number })[] {
  const limit = Date.now() + 7 * 24 * 3600 * 1000;
  return upcoming(links).filter((l) => l.reminderEnabled && l.at <= limit);
}

export const byPopularity = (a: Link, b: Link) => (b.likeCount ?? 0) - (a.likeCount ?? 0);

export const byRecent = (a: { createdAt?: string }, b: { createdAt?: string }) =>
  (b.createdAt ?? "").localeCompare(a.createdAt ?? "");

/** Durée d'une « une » de l'Explorer : l'ordre change toutes les 6 heures. */
export const ROTATION_MS = 6 * 3600 * 1000;
const DAY_MS = 24 * 3600 * 1000;

/** FNV-1a 32 bits : même résultat que l'app Android (rotationScore dans ExploreScreen.kt). */
function fnv1a(s: string): number {
  let h = 0x811c9dc5;
  for (const b of new TextEncoder().encode(s)) {
    h ^= b;
    h = Math.imul(h, 0x01000193) >>> 0;
  }
  return h >>> 0;
}

/**
 * Score « À la une » d'une idée pour la période en cours : tirage pondéré
 * (Efraimidis-Spirakis, u^(1/poids)), le même pour tout le monde pendant 6 h.
 * Les idées aimées et récentes remontent plus souvent, mais chacune a sa
 * chance d'être en tête ; à la période suivante, l'ordre est rebattu.
 */
export function rotationScore(l: { id: string; likeCount?: number; createdAt?: string }, now = Date.now()): number {
  const period = Math.floor(now / ROTATION_MS);
  const u = (fnv1a(`${l.id}:${period}`) + 0.5) / 4294967296; // ]0, 1[
  const ageDays = l.createdAt ? (now - Date.parse(l.createdAt)) / DAY_MS : Infinity;
  const fresh = ageDays < 14 ? 2 : ageDays < 60 ? 1 : 0;
  const weight = 1 + Math.log1p(Math.max(0, l.likeCount ?? 0)) + fresh;
  return Math.pow(u, 1 / weight);
}

/** Trie « À la une » (copie) : ordre stable pendant la période, rebattu à la suivante. */
export function byRotation<T extends { id: string; likeCount?: number; createdAt?: string }>(list: T[], now = Date.now()): T[] {
  const score = new Map(list.map((l) => [l.id, rotationScore(l, now)]));
  return [...list].sort((a, b) => score.get(b.id)! - score.get(a.id)!);
}

/** Comptage des tags, du plus utilisé au moins utilisé. */
export function tagCounts(links: Link[]): [string, number][] {
  const m = new Map<string, number>();
  for (const l of links) for (const t of l.tags ?? []) m.set(t, (m.get(t) ?? 0) + 1);
  return [...m.entries()].sort((a, b) => b[1] - a[1] || a[0].localeCompare(b[0], "fr"));
}

/** Propriétaire ou collaborateur éditeur du dossier. */
/**
 * L'utilisateur peut ajouter ou modifier des idées dans ce dossier : le sien,
 * celui d'un membre de sa tribu (`peers`), ou un dossier où il est éditeur.
 */
export function canEditFolder(f: Folder, userId: string | undefined, peers?: ReadonlySet<string>): boolean {
  if (!userId) return false;
  if (f.ownerId === userId || peers?.has(f.ownerId)) return true;
  return (f.collaborators ?? []).some((c) => c.userId === userId && c.role === "COLLABORATOR_ROLE_EDITOR");
}

// Adresse sans schéma : limitée aux extensions courantes pour ne pas prendre
// « fin.Début » pour un site.
const BARE_DOMAIN =
  /(?:^|\s)((?:www\.)?[a-z0-9-]+(?:\.[a-z0-9-]+)*\.(?:fr|com|org|net|eu|be|ch|ca|io|co|uk|de|es|it|info|shop|blog|app)(?:\/[^\s<>"']*)?)(?=$|\s)/i;

/**
 * Extrait une adresse web d'un texte collé ou partagé (« Regarde ça https://… »)
 * et complète le schéma s'il manque (« www.site.fr/page »). Undefined si rien.
 */
export function extractUrl(text: string | null | undefined): string | undefined {
  const raw = (text ?? "").trim();
  if (!raw) return undefined;
  const found = raw.match(/https?:\/\/[^\s<>"']+/i)?.[0]
    ?? raw.match(BARE_DOMAIN)?.[1];
  if (!found) return undefined;
  // Ponctuation de fin de phrase collée à l'adresse
  const cleaned = found.replace(/[.,;:!?)\]}»]+$/, "");
  try {
    const u = new URL(/^https?:\/\//i.test(cleaned) ? cleaned : `https://${cleaned}`);
    return u.protocol === "http:" || u.protocol === "https:" ? u.href : undefined;
  } catch {
    return undefined;
  }
}
