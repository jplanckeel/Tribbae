import { eventDateMs } from "./format";
import type { CategoryKey, Link } from "./types";

/**
 * « C'est fait » : une idée vécue (sortie faite, recette cuisinée, livre lu,
 * cadeau acheté), autant de fois qu'on la refait, avec un souvenir facultatif.
 * L'état appartient à la tribu ; le serveur ne l'envoie qu'à elle et aux
 * membres du dossier. Mêmes libellés et mêmes cas que data/Done.kt (DoneTest).
 */

/** Un souvenir : une fois où l'idée a été faite. */
export interface LinkDone {
  id: string;
  linkId: string;
  userId: string;
  /** Jour, sans heure : AAAA-MM-JJ */
  doneOn: string;
  withUserIds?: string[];
  withChildIds?: string[];
  rating?: number;
  note?: string;
  photoUrl?: string;
  createdAt?: string;
}

/** Ce qu'on envoie pour marquer une idée ou corriger un souvenir. Tout est facultatif. */
export interface DoneInput {
  doneOn?: string;
  withUserIds?: string[];
  withChildIds?: string[];
  rating?: number;
  note?: string;
  photoUrl?: string;
  redo?: boolean;
}

/** « Notre année en idées » (GET /v1/done/summary) : chaque fois compte. */
export interface DoneSummary {
  year?: number;
  outings?: number;
  recipes?: number;
  books?: number;
  total?: number;
  /** 1 à 12 ; 0 si rien n'a été fait */
  bestMonth?: number;
  bestMonthCount?: number;
}

interface DoneWords {
  /** Bouton principal, idée à faire */
  verb: string;
  /** Bouton d'une idée déjà faite */
  again: string;
  /** Titre de la feuille souvenir */
  ask: string;
  /** Tampon posé sur le visuel */
  stamp: string;
  /** « Fait le », « Cuisinée le », « Lu en », « Acheté le » */
  prefix: string;
}

const DEFAULT_WORDS: DoneWords = { verb: "C'est fait", again: "On l'a refait", ask: "Comment c'était ?", stamp: "Fait", prefix: "Fait le" };

const WORDS: Partial<Record<CategoryKey, DoneWords>> = {
  LINK_CATEGORY_ACTIVITE: { verb: "On l'a fait !", again: "On l'a refait", ask: "Comment c'était ?", stamp: "Fait", prefix: "Fait le" },
  LINK_CATEGORY_EVENEMENT: { verb: "On y est allés !", again: "On y est retournés", ask: "Comment c'était ?", stamp: "Fait", prefix: "Fait le" },
  LINK_CATEGORY_RECETTE: { verb: "On l'a cuisinée", again: "On l'a refaite", ask: "C'était bon ?", stamp: "Cuisinée", prefix: "Cuisinée le" },
  LINK_CATEGORY_LIVRE: { verb: "On l'a lu", again: "On l'a relu", ask: "Il vous a plu ?", stamp: "Lu", prefix: "Lu en" },
  LINK_CATEGORY_CADEAU: { verb: "Acheté", again: "Racheté", ask: "C'est acheté ?", stamp: "Acheté", prefix: "Acheté le" },
};

export const doneWords = (category: CategoryKey): DoneWords => WORDS[category] ?? DEFAULT_WORDS;

export const isDone = (l: Pick<Link, "doneCount">) => (l.doneCount ?? 0) > 0;

/** Dernier jour où l'idée a été faite (AAAA-MM-JJ), ou "" si elle ne l'est pas. */
export const doneDay = (l: Pick<Link, "lastDoneAt">) => (l.lastDoneAt ?? "").slice(0, 10);

const MONTHS_SHORT = ["janv.", "févr.", "mars", "avr.", "mai", "juin", "juil.", "août", "sept.", "oct.", "nov.", "déc."];
const MONTHS_LONG = ["janvier", "février", "mars", "avril", "mai", "juin", "juillet", "août", "septembre", "octobre", "novembre", "décembre"];
const WEEKDAYS = ["Dimanche", "Lundi", "Mardi", "Mercredi", "Jeudi", "Vendredi", "Samedi"];

const parts = (day: string) => {
  const [y, m, d] = day.split("-").map(Number);
  return { y, m, d };
};
const pad = (n: number) => String(n).padStart(2, "0");

/** Jour local d'un instant (AAAA-MM-JJ). */
export function dayOf(ms: number): string {
  const d = new Date(ms);
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

/** « 26 oct. », « 1er nov. », avec l'année si ce n'est pas l'année en cours. */
export function formatDoneDay(day: string, now: number): string {
  const { y, m, d } = parts(day);
  if (!y || !m || !d) return "";
  const year = y === new Date(now).getFullYear() ? "" : ` ${y}`;
  return `${d === 1 ? "1er" : d} ${MONTHS_SHORT[m - 1]}${year}`;
}

/** « octobre », pour un livre : le mois seul, avec l'année si ce n'est pas l'année en cours. */
export function formatDoneMonth(day: string, now: number): string {
  const { y, m } = parts(day);
  if (!y || !m) return "";
  return `${MONTHS_LONG[m - 1]}${y === new Date(now).getFullYear() ? "" : ` ${y}`}`;
}

/** État d'une idée faite : « Fait le 26 oct. », « Cuisinée 3 fois », « Lu en octobre », « Acheté le 2 nov. ». */
export function doneLabel(l: Pick<Link, "category" | "doneCount" | "lastDoneAt">, now: number): string {
  if (!isDone(l)) return "";
  const w = doneWords(l.category);
  if (l.category === "LINK_CATEGORY_RECETTE" && (l.doneCount ?? 0) > 1) return `Cuisinée ${l.doneCount} fois`;
  const day = doneDay(l);
  if (!day) return w.stamp;
  return `${w.prefix} ${l.category === "LINK_CATEGORY_LIVRE" ? formatDoneMonth(day, now) : formatDoneDay(day, now)}`;
}

/** Tampon de la fiche : « Fait », « Cuisinée ×3 ». */
export function doneStamp(l: Pick<Link, "category" | "doneCount">): string {
  const n = l.doneCount ?? 0;
  return n > 1 ? `${doneWords(l.category).stamp} ×${n}` : doneWords(l.category).stamp;
}

/** Idée Cadeau d'une liste de cadeaux : son achat est une réservation, jamais un « fait ». */
export const inGiftList = (l: Pick<Link, "category">, folder?: { giftReservations?: boolean } | null) =>
  l.category === "LINK_CATEGORY_CADEAU" && !!folder?.giftReservations;

/** Seul un cadeau acheté s'estompe (60 %, titre barré) : une activité faite reste un souvenir. */
export const doneDimmed = (l: Pick<Link, "category" | "doneCount">) => l.category === "LINK_CATEGORY_CADEAU" && isDone(l);

const startOfDay = (ms: number) => {
  const d = new Date(ms);
  d.setHours(0, 0, 0, 0);
  return d.getTime();
};

const DATED: CategoryKey[] = ["LINK_CATEGORY_ACTIVITE", "LINK_CATEGORY_EVENEMENT"];

/**
 * La sortie ou l'activité datée est passée (la veille ou avant), n'est pas
 * faite et personne n'a répondu « Pas cette fois » : on demande « Fait ? ».
 */
export function awaitingConfirmation(l: Link, now: number): boolean {
  const at = eventDateMs(l.eventDate);
  return !!at && DATED.includes(l.category) && !isDone(l) && !l.donePromptDismissed && at < startOfDay(now);
}

/** « Samedi » si c'était dans la semaine, sinon « 26 oct. ». */
export function promptDay(l: Link, now: number): string {
  const at = eventDateMs(l.eventDate);
  if (!at) return "";
  const days = Math.round((startOfDay(now) - startOfDay(at)) / 86_400_000);
  if (days === 1) return "Hier";
  return days > 0 && days < 7 ? WEEKDAYS[new Date(at).getDay()] : formatDoneDay(dayOf(at), now);
}

/** Méta d'une vignette en attente : « Samedi · c'était bien ? ». */
export const awaitingLabel = (l: Link, now: number) => `${promptDay(l, now)} · c'était bien ?`;

/** Question du bandeau et de la notification. */
export const promptQuestion = (l: Pick<Link, "category" | "title">) =>
  l.category === "LINK_CATEGORY_EVENEMENT" ? `Vous êtes allés à « ${l.title} » ?` : `Vous avez fait « ${l.title} » ?`;

/** Sur-titre du bandeau : « Samedi · Ferme du Mas ». */
export const promptKicker = (l: Link, now: number) => [promptDay(l, now), l.location?.trim()].filter(Boolean).join(" · ");

const COUNTED: CategoryKey[] = ["LINK_CATEGORY_ACTIVITE", "LINK_CATEGORY_EVENEMENT", "LINK_CATEGORY_RECETTE", "LINK_CATEGORY_LIVRE"];

export interface FolderProgress {
  done: number;
  total: number;
}

/**
 * Avancement d'un dossier, sur ses activités, sorties, recettes et livres.
 * null (ni anneau ni barre) s'il en compte moins de 3 ou si c'est une liste de cadeaux.
 */
export function folderProgress(links: Link[], giftList = false): FolderProgress | null {
  if (giftList) return null;
  const counted = links.filter((l) => COUNTED.includes(l.category));
  if (counted.length < 3) return null;
  return { done: counted.filter(isDone).length, total: counted.length };
}

export const progressLabel = (p: FolderProgress) => `${p.done} sur ${p.total} faites`;

export function progressHint(p: FolderProgress): string {
  const left = p.total - p.done;
  if (left <= 0) return "Bravo, tout est fait !";
  return left === 1 ? "Plus qu'une à faire" : `Encore ${left} à faire`;
}

export type DoneFilter = "all" | "todo" | "done";

export const DONE_FILTERS: { key: DoneFilter; label: string }[] = [
  { key: "all", label: "Toutes" },
  { key: "todo", label: "À faire" },
  { key: "done", label: "Faites" },
];

export const matchesDoneFilter = (l: Link, filter: DoneFilter) => filter === "all" || isDone(l) === (filter === "done");

export function doneCounts(links: Link[]): Record<DoneFilter, number> {
  const done = links.filter(isDone).length;
  return { all: links.length, todo: links.length - done, done };
}

const created = (l: Link) => l.createdAt || l.updatedAt || "";

/**
 * Tri « À faire d'abord » : les idées en attente de confirmation, puis celles
 * à faire (par date d'événement, puis les plus récentes), puis les faites
 * (la dernière faite en bas).
 */
export function sortTodoFirst(links: Link[], now: number): Link[] {
  const rank = (l: Link) => (awaitingConfirmation(l, now) ? 0 : isDone(l) ? 2 : 1);
  return [...links].sort((a, b) => {
    const ra = rank(a), rb = rank(b);
    if (ra !== rb) return ra - rb;
    if (ra === 2) return doneDay(a).localeCompare(doneDay(b)) || created(b).localeCompare(created(a));
    const da = eventDateMs(a.eventDate), db = eventDateMs(b.eventDate);
    if (da && db && da !== db) return da - db;
    if (!!da !== !!db) return da ? -1 : 1;
    return created(b).localeCompare(created(a));
  });
}

/** « Léo, Emma et Camille » */
export function joinNames(names: string[]): string {
  if (names.length <= 1) return names[0] ?? "";
  return `${names.slice(0, -1).join(", ")} et ${names[names.length - 1]}`;
}

/** Ligne du souvenir : « Avec Léo, Emma et Camille · 26 oct. », « … · 3 fois, dernière le 8 nov. ». */
export function memoryLine(names: string[], day: string, count: number, now: number): string {
  const date = formatDoneDay(day, now);
  const when = count > 1 ? `${count} fois, dernière le ${date}` : date;
  return names.length ? `Avec ${joinNames(names)} · ${when}` : when;
}

const STAR_WORDS = ["Touchez une étoile", "Bof", "Pas mal", "Bien", "Super", "Génial !"];
export const starWord = (rating: number) => STAR_WORDS[rating] ?? STAR_WORDS[0];

/** Titre de la célébration. */
export function celebrationTitle(category: CategoryKey, again: boolean): string {
  if (again) return `${doneWords(category).again} !`;
  return category === "LINK_CATEGORY_RECETTE" ? "Miam, c'est noté !" : "C'est fait !";
}

/** Toast après un marquage : « 5 sur 8 faites », ou « C'est noté » hors d'un dossier suivi. */
export const doneToast = (p: FolderProgress | null) => (p ? progressLabel(p) : "C'est noté");

/** Explorer : « 120 familles l'ont fait » (le serveur n'envoie le nombre qu'à partir de 5). */
export const familiesLabel = (n: number | undefined) => (n && n > 0 ? `${n} familles l'ont fait` : "");

/** Choix « Quand ? » de la feuille souvenir. */
export function dayChoices(now: number): { key: "today" | "yesterday"; label: string; day: string }[] {
  return [
    { key: "today", label: "Aujourd'hui", day: dayOf(now) },
    { key: "yesterday", label: "Hier", day: dayOf(startOfDay(now) - 12 * 3_600_000) },
  ];
}

/**
 * Méta d'une vignette : état fait (vert), ou « Samedi · c'était bien ? »
 * (terracotta) pour qui peut répondre — pas un lecteur du cercle.
 */
export function doneMeta(link: Link, now: number, visible: boolean, canAnswer = visible): { text: string; className: string } | null {
  if (!visible) return null;
  if (isDone(link)) return { text: doneLabel(link, now), className: "font-bold text-done" };
  if (canAnswer && awaitingConfirmation(link, now)) return { text: awaitingLabel(link, now), className: "font-bold text-cadeau-ink" };
  return null;
}

/** Classes d'une vignette de cadeau acheté (hors liste) : estompée, titre barré. */
export const dimmedCard = (link: Link, visible: boolean) => (visible && doneDimmed(link) ? "opacity-60" : "");
export const dimmedTitle = (link: Link, visible: boolean) => (visible && doneDimmed(link) ? "line-through" : "");
