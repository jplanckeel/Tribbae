import type { IllustrationKey } from "../components/FolderBannerArt";
import { AVATAR_COLORS } from "./categories";
import { useMyData } from "./data";
import type { Child, Folder, FolderInput, Link } from "./types";
import { eventDateMs, safeImage } from "./format";
import { byRecent } from "./links";

// Personnalisation des dossiers (maquettes « Bannières illustrées » et « Personnaliser
// un dossier »). Même logique que composeApp/.../ui/campfire/FolderStyle.kt : garder
// les deux en phase (FolderStyleTest y fige les cas).

export type CoverKey = "illu" | "uni" | "tipis" | "pois" | "rayures" | "photo";
export type KeyDateType = "anniv" | "vacances" | "event";
export type ViewMode = "grid" | "list" | "mosaic";
export type SortKey = "recent" | "date" | "category";

export const COVERS: { key: CoverKey; label: string }[] = [
  { key: "illu", label: "Dessin" },
  { key: "uni", label: "Uni" },
  { key: "tipis", label: "Tipis" },
  { key: "pois", label: "Pois" },
  { key: "rayures", label: "Rayures" },
  { key: "photo", label: "Photo" },
];

export const ILLUSTRATIONS: { key: IllustrationKey; label: string }[] = [
  { key: "anniv", label: "Anniversaire" },
  { key: "cadeaux", label: "Cadeaux" },
  { key: "noel", label: "Noël" },
  { key: "jouets", label: "Jouets" },
  { key: "montagne", label: "Montagne" },
  { key: "plage", label: "Plage" },
  { key: "gouter", label: "Goûter" },
  { key: "pluie", label: "Jour de pluie" },
  { key: "automne", label: "Automne" },
];

export const KEY_DATE_TYPES: { key: KeyDateType; label: string }[] = [
  { key: "anniv", label: "Anniversaire" },
  { key: "vacances", label: "Vacances" },
  { key: "event", label: "Événement" },
];

export const VIEW_MODES: { key: ViewMode; label: string }[] = [
  { key: "grid", label: "Grille" },
  { key: "list", label: "Liste" },
  { key: "mosaic", label: "Mosaïque" },
];

export const SORTS: { key: SortKey; label: string }[] = [
  { key: "recent", label: "Plus récentes" },
  { key: "date", label: "Par date" },
  { key: "category", label: "Par catégorie" },
];

const fold = (s: string) => s.toLowerCase().normalize("NFD").replace(/[̀-ͯ]/g, "");

// Mots du nom → dessin, dans l'ordre de priorité
const KEYWORDS: [IllustrationKey, RegExp][] = [
  ["noel", /\bnoel\b|\bsapin/],
  ["anniv", /\banniv/],
  ["cadeaux", /\bcadeau|\bliste\b/],
  ["jouets", /\bjouet|\bjeux\b|\bjeu\b/],
  ["plage", /\bplage|\bmer\b|\bete\b|\bpiscine/],
  ["montagne", /\bvacances|\bbalade|\bmontagne|\brando|\bcamping|\bnature\b|\bforet/],
  ["gouter", /\brecette|\bgouter|\bcuisine|\brepas\b|\bcrepe|\bgateau/],
  ["pluie", /\bpluie|\binterieur|\bbricol/],
  ["automne", /\bautomne|\bhalloween|\bchataigne/],
];

/**
 * Dessin choisi automatiquement selon le nom du dossier ou sa date clé
 * (anniversaire, vacances ; mois de décembre → Noël, septembre–novembre →
 * automne, été → plage), ou null si rien ne correspond.
 */
export function autoIllustration(name: string, keyDate?: number, keyDateType?: string): IllustrationKey | null {
  const n = fold(name);
  if (keyDate && keyDateType === "anniv") return "anniv";
  for (const [key, re] of KEYWORDS) if (re.test(n)) return key;
  if (keyDate && keyDateType === "vacances") return "montagne";
  if (keyDate) {
    const m = new Date(keyDate).getMonth();
    if (m === 11) return "noel";
    if (m >= 8 && m <= 10) return "automne";
    if (m >= 5 && m <= 7) return "plage";
  }
  return null;
}

export function keyDateOf(f: Pick<Folder, "keyDate">): number | undefined {
  const ms = eventDateMs(f.keyDate);
  return ms ?? undefined;
}

/** Couverture effective : celle choisie, sinon un dessin auto, sinon les tipis. */
export function resolvedCover(f: Folder): { cover: CoverKey; illustration: IllustrationKey | null } {
  const auto = autoIllustration(f.name, keyDateOf(f), f.keyDateType);
  const illustration = (ILLUSTRATIONS.some((i) => i.key === f.illustration) ? (f.illustration as IllustrationKey) : null) ?? auto;
  const cover = (COVERS.some((c) => c.key === f.cover) ? f.cover : "") as CoverKey | "";
  if (cover === "illu") return { cover, illustration: illustration ?? "anniv" };
  if (cover) return { cover, illustration };
  return { cover: illustration ? "illu" : "tipis", illustration };
}

/** Image de la couverture « Photo » : celle choisie, sinon la première idée illustrée du dossier. */
export function coverPhoto(f: Folder, links: Link[]): string | undefined {
  return safeImage(f.bannerUrl) ?? links.map((l) => safeImage(l.imageUrl)).find(Boolean);
}

const DAY = 86_400_000;
const startOfDay = (ms: number) => {
  const d = new Date(ms);
  return new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime();
};
const WEEKDAYS = ["Dim.", "Lun.", "Mar.", "Mer.", "Jeu.", "Ven.", "Sam."];

/**
 * Compte à rebours de la date clé (« Anniv. dans 24 j », « Départ J-12 »,
 * « Sam. 24 · J-3 »…). Un anniversaire revient chaque année ; une date passée
 * ne s'affiche plus.
 */
export function countdown(keyDate: number | undefined, type: string | undefined, now = Date.now()): { label: string; days: number } | null {
  if (!keyDate) return null;
  const today = startOfDay(now);
  let target = startOfDay(keyDate);
  if (type === "anniv") {
    const d = new Date(keyDate);
    const y = new Date(today).getFullYear();
    target = new Date(y, d.getMonth(), d.getDate()).getTime();
    if (target < today) target = new Date(y + 1, d.getMonth(), d.getDate()).getTime();
  }
  const days = Math.round((target - today) / DAY);
  if (days < 0) return null;
  if (type === "anniv") return { days, label: days === 0 ? "Anniv. aujourd'hui !" : days === 1 ? "Anniv. demain" : `Anniv. dans ${days} j` };
  if (type === "vacances") return { days, label: days === 0 ? "C'est le départ !" : `Départ J-${days}` };
  if (days === 0) return { days, label: "Aujourd'hui" };
  if (days <= 7) return { days, label: `${WEEKDAYS[new Date(target).getDay()]} ${new Date(target).getDate()} · J-${days}` };
  return { days, label: `J-${days}` };
}

/** Idées du dossier dans l'ordre choisi (« Par date » : sorties à venir d'abord). */
export function sortFolderLinks(links: Link[], sort: string | undefined, categoryOrder: string[]): Link[] {
  const recent = byRecent;
  const list = [...links];
  if (sort === "date") {
    return list.sort((a, b) => {
      const da = eventDateMs(a.eventDate), db = eventDateMs(b.eventDate);
      if (da && db) return da - db;
      if (da || db) return da ? -1 : 1;
      return recent(a, b);
    });
  }
  if (sort === "category") {
    return list.sort((a, b) => categoryOrder.indexOf(a.category) - categoryOrder.indexOf(b.category) || recent(a, b));
  }
  return list.sort(recent);
}

/** Champs modifiables d'un dossier existant, pour une mise à jour partielle (épingler…). */
export function folderInput(f: Folder): FolderInput {
  return {
    name: f.name,
    icon: f.icon || "FOLDER",
    color: f.color || "ORANGE",
    visibility: f.visibility ?? "VISIBILITY_PRIVATE",
    bannerUrl: f.bannerUrl,
    tags: f.tags,
    description: f.description ?? "",
    cover: f.cover ?? "",
    illustration: f.illustration ?? "",
    keyDate: keyDateOf(f) ?? 0,
    keyDateType: f.keyDateType ?? "",
    childIds: f.childIds ?? [],
    viewMode: f.viewMode ?? "",
    sort: f.sort ?? "",
    pinned: !!f.pinned,
  };
}

/** Couleurs d'avatar d'un enfant (mêmes que la liste des enfants de « Ma tribu »). */
export function childColors(children: Child[], id: string): [string, string] {
  const i = Math.max(0, children.findIndex((c) => c.id === id));
  return AVATAR_COLORS[(i + 2) % AVATAR_COLORS.length];
}

/** Enfants « Pour qui » d'un dossier, connus de la tribu. */
export function useFolderChildren(folder: Folder): { child: Child; colors: [string, string] }[] {
  const { children } = useMyData();
  return (folder.childIds ?? [])
    .map((id) => children.find((c) => c.id === id))
    .filter((c): c is Child => !!c)
    .map((child) => ({ child, colors: childColors(children, child.id) }));
}
