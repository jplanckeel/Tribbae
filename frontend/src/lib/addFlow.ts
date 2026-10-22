import { AGE_BANDS, covers, formatAgeRange, hull, parseAgeRange, type AgeRange } from "./age";
import { categoryMeta } from "./categories";
import { countdown, keyDateOf } from "./folderStyle";
import type { CategoryKey, Folder, Link } from "./types";

// Parcours « Ajouter une idée » (maquette « Ajouter une idée v2 ») : dossiers
// suggérés, « qui la verra », tranches d'âge. Même logique côté Android :
// composeApp/.../data/AddFlow.kt (garder les deux alignés ; AddFlowTest y fige les cas).

export interface FolderSuggestion {
  folder: Folder;
  reason: string;
}

const MIN = 60_000;
const HOUR = 60 * MIN;
const DAY = 24 * HOUR;

/** « à l'instant », « il y a 3 h », « hier », « il y a 2 jours », « il y a 3 semaines ». */
export function ago(iso: string | undefined, now = Date.now()): string {
  const t = iso ? Date.parse(iso) : NaN;
  if (!Number.isFinite(t)) return "récemment";
  const d = Math.max(0, now - t);
  if (d < 2 * MIN) return "à l'instant";
  if (d < HOUR) return `il y a ${Math.floor(d / MIN)} min`;
  if (d < DAY) return `il y a ${Math.floor(d / HOUR)} h`;
  const days = Math.floor(d / DAY);
  if (days === 1) return "hier";
  if (days < 14) return `il y a ${days} jours`;
  return `il y a ${Math.floor(days / 7)} semaines`;
}

/** Version courte pour « Ajoutée récemment par la tribu » (« 2 h », « 3 j »). */
export function agoShort(iso: string | undefined, now = Date.now()): string {
  const t = iso ? Date.parse(iso) : NaN;
  if (!Number.isFinite(t)) return "";
  const d = Math.max(0, now - t);
  if (d < HOUR) return `${Math.max(1, Math.floor(d / MIN))} min`;
  if (d < DAY) return `${Math.floor(d / HOUR)} h`;
  return `${Math.floor(d / DAY)} j`;
}

/**
 * Trois dossiers proposés pour ranger une idée de catégorie `cat`, chacun avec
 * sa raison : celui où l'on range ce genre d'idées, la date clé proche (pour un
 * cadeau), le dernier utilisé, un dossier partagé actif, puis les plus récents.
 * `preset` (dossier ouvert à l'écran) passe en premier.
 */
export function suggestFolders(cat: CategoryKey, folders: Folder[], links: Link[], preset?: string, now = Date.now()): FolderSuggestion[] {
  const out: FolderSuggestion[] = [];
  const add = (f: Folder | undefined, reason: string) => {
    if (f && out.length < 3 && !out.some((s) => s.folder.id === f.id)) out.push({ folder: f, reason });
  };
  const byId = new Map(folders.map((f) => [f.id, f]));
  add(preset ? byId.get(preset) : undefined, "Le dossier ouvert");

  // Dossier où l'on range le plus d'idées de cette catégorie
  const counts = new Map<string, number>();
  for (const l of links) if (l.folderId && byId.has(l.folderId) && l.category === cat) counts.set(l.folderId, (counts.get(l.folderId) ?? 0) + 1);
  const best = [...counts.entries()].sort((a, b) => b[1] - a[1])[0];
  if (best) add(byId.get(best[0]), `Vous y rangez vos ${categoryMeta(cat).plural.toLowerCase()}`);

  // Un cadeau : le dossier dont la date clé approche
  if (cat === "LINK_CATEGORY_CADEAU") {
    const soon = folders
      .map((f) => ({ f, cd: countdown(keyDateOf(f), f.keyDateType, now) }))
      .filter((x) => x.cd && x.cd.days <= 90)
      .sort((a, b) => a.cd!.days - b.cd!.days)[0];
    if (soon) add(soon.f, soon.cd!.label);
  }

  // Dernier dossier utilisé
  const latest = links.filter((l) => l.folderId && byId.has(l.folderId)).sort((a, b) => (b.createdAt ?? "").localeCompare(a.createdAt ?? ""))[0];
  if (latest) add(byId.get(latest.folderId!), `Utilisé ${ago(latest.createdAt, now)}`);

  // Un dossier partagé
  const shared = folders.filter((f) => f.collaborators?.length).sort((a, b) => (b.updatedAt ?? "").localeCompare(a.updatedAt ?? ""))[0];
  if (shared) add(shared, `Partagé avec ${firstName(shared.collaborators![0])}`);

  for (const f of [...folders].sort((a, b) => (b.updatedAt ?? "").localeCompare(a.updatedAt ?? ""))) add(f, "Modifié récemment");
  return out;
}

const firstName = (c: { displayName?: string; email?: string }) => c.displayName || c.email || "un proche";

/** « Inès la verra », « Inès, Camille et 1 autre la verront », « Visible par vous seulement »… */
export function whoSees(folder: Folder | undefined, isPublic: boolean): string {
  if (!folder) return isPublic ? "Visible par toute la communauté" : "Visible par vous et votre tribu";
  if (folder.visibility === "VISIBILITY_PUBLIC") return "Visible par toute la communauté";
  const names = (folder.collaborators ?? []).map(firstName);
  if (!names.length) return "Visible par vous et votre tribu";
  if (names.length === 1) return `${names[0]} la verra`;
  if (names.length === 2) return `${names[0]} et ${names[1]} la verront`;
  return `${names[0]}, ${names[1]} et ${names.length - 2} autre${names.length > 3 ? "s" : ""} la verront`;
}

/** Tranches cochées d'un âge (« 3–10 ans » → 3–6 et 6–10). */
export function selectedBands(ageRange: string | undefined): string[] {
  const r = parseAgeRange(ageRange);
  return r ? AGE_BANDS.filter((b) => covers(r, b.range)).map((b) => b.key) : [];
}

/** Âge écrit à partir des tranches cochées (leur enveloppe), "" si aucune. */
export function bandsToAge(keys: string[]): string {
  const ranges = AGE_BANDS.filter((b) => keys.includes(b.key)).map((b) => b.range);
  if (!ranges.length) return "";
  return formatAgeRange(ranges.reduce((a, b) => hull(a, b)) as AgeRange);
}
