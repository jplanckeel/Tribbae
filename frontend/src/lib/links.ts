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

/** Comptage des tags, du plus utilisé au moins utilisé. */
export function tagCounts(links: Link[]): [string, number][] {
  const m = new Map<string, number>();
  for (const l of links) for (const t of l.tags ?? []) m.set(t, (m.get(t) ?? 0) + 1);
  return [...m.entries()].sort((a, b) => b[1] - a[1] || a[0].localeCompare(b[0], "fr"));
}

/** Propriétaire ou collaborateur éditeur du dossier. */
export function canEditFolder(f: Folder, userId: string | undefined): boolean {
  if (!userId) return false;
  if (f.ownerId === userId) return true;
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
