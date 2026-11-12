import { matchesAge } from "./age";
import type { DoneSummary } from "./done";
import { ageInMonths } from "./format";
import type { Child, Folder, Link } from "./types";

// Logique de « Ma tribu » (maquette « Ma tribu v2 »), dupliquée dans
// composeApp/.../data/TribePage.kt : garder les deux en phase (TribePageTest).

/** Places autour du feu dans l'en-tête : au-delà, une pastille « +N ». */
export const RING_SLOTS = 5;

/**
 * Position d'un avatar sur l'arc au-dessus du tipi, en px depuis le centre du
 * feu (x vers la droite, y vers le bas). L'arc va de 155° (gauche) à 25° (droite).
 */
export function ringPosition(index: number, count: number, radius = 105): { dx: number; dy: number } {
  const angle = count <= 1 ? 90 : 155 - (index * 130) / (count - 1);
  const a = (angle * Math.PI) / 180;
  return { dx: Math.round(radius * Math.cos(a)), dy: Math.round(-radius * Math.sin(a)) };
}

/** Un proche du cercle : il a accès à un ou plusieurs dossiers de la tribu. */
export interface CircleMember {
  userId: string;
  name: string;
  email?: string;
  /** Peut ajouter des idées dans au moins un dossier. */
  editor: boolean;
  folders: Folder[];
}

/**
 * Le cercle : les collaborateurs des dossiers de la tribu, regroupés par
 * personne. Les membres du foyer (peers) n'en font pas partie.
 */
export function circleOf(folders: Folder[], isPeer: (userId: string) => boolean): CircleMember[] {
  const byId = new Map<string, CircleMember>();
  for (const f of folders) {
    for (const c of f.collaborators ?? []) {
      if (!c.userId || isPeer(c.userId)) continue;
      const m = byId.get(c.userId) ?? { userId: c.userId, name: c.displayName || c.email || "Proche", email: c.email, editor: false, folders: [] };
      m.editor ||= c.role === "COLLABORATOR_ROLE_EDITOR";
      if (!m.folders.some((x) => x.id === f.id)) m.folders.push(f);
      byId.set(c.userId, m);
    }
  }
  return [...byId.values()].sort((a, b) => b.folders.length - a.folders.length || a.name.localeCompare(b.name, "fr"));
}

export interface YearInIdeas {
  outings: number;
  recipes: number;
  books: number;
  total: number;
  /** Mois (0-11) où la tribu a fait le plus d'idées, s'il y en a. */
  bestMonth?: { month: number; count: number };
}

/**
 * « Notre année en idées » : ce que la tribu a fait pendant l'année civile.
 * Le serveur compte (GET /v1/done/summary), car une idée refaite compte
 * plusieurs fois ; sans réponse, tout est à zéro.
 */
export function yearInIdeas(s: DoneSummary | undefined): YearInIdeas {
  const out: YearInIdeas = { outings: s?.outings ?? 0, recipes: s?.recipes ?? 0, books: s?.books ?? 0, total: s?.total ?? 0 };
  if (s?.bestMonth && s.bestMonthCount) out.bestMonth = { month: s.bestMonth - 1, count: s.bestMonthCount };
  return out;
}

/** Libellés des trois compteurs : « sorties faites », « recettes cuisinées », « livres lus ». */
export function yearLabels(y: YearInIdeas): { outings: string; recipes: string; books: string } {
  return {
    outings: y.outings > 1 ? "sorties faites" : "sortie faite",
    recipes: y.recipes > 1 ? "recettes cuisinées" : "recette cuisinée",
    books: y.books > 1 ? "livres lus" : "livre lu",
  };
}

/** « Votre meilleur mois : juillet, 12 idées faites. » */
export function bestMonthLine(y: YearInIdeas): string {
  if (!y.bestMonth) return "Marquez vos premières idées faites : elles rempliront votre année.";
  const n = y.bestMonth.count;
  return `Votre meilleur mois : ${monthName(y.bestMonth.month)}, ${n} ${n > 1 ? "idées faites" : "idée faite"}.`;
}

const MONTHS = ["janvier", "février", "mars", "avril", "mai", "juin", "juillet", "août", "septembre", "octobre", "novembre", "décembre"];
export const monthName = (m: number) => MONTHS[m] ?? "";

/**
 * Idées pour un enfant : celles des dossiers « Pour qui » qui le citent, et
 * celles dont la tranche d'âge lui correspond.
 */
export function ideasForChild(links: Link[], folders: Folder[], child: Child): number {
  const forKid = new Set(folders.filter((f) => f.childIds?.includes(child.id)).map((f) => f.id));
  const years = ageInMonths(child.birthDate) / 12;
  return links.filter((l) => (l.folderId && forKid.has(l.folderId)) || matchesAge(l.ageRange, years, years)).length;
}

/** « Anniversaire le 3 février » */
export function birthdayLabel(birthDate: Child["birthDate"]): string {
  const d = new Date(Number(birthDate));
  return `Anniversaire le ${d.getDate() === 1 ? "1er" : d.getDate()} ${monthName(d.getMonth())}`;
}
