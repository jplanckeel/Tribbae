import { countdown } from "./folderStyle";
import { formatAge } from "./format";
import type { Child } from "./types";

// « Idées avec l'IA v2 » : la demande est une phrase à compléter
// (« Je cherche [des activités] pour [Léo] [ce week-end], [à la maison],
// budget [gratuit]. »), envoyée telle quelle comme prompt. Dupliqué dans
// composeApp/.../data/AiRequest.kt : garder les deux en phase (AiRequestTest).

export type BlankKey = "what" | "who" | "when" | "where" | "budget";
export type Option = { key: string; label: string };

export const WHAT: Option[] = [
  { key: "activites", label: "des activités" },
  { key: "sorties", label: "des sorties" },
  { key: "recettes", label: "des recettes" },
  { key: "cadeaux", label: "des idées cadeaux" },
  { key: "livres", label: "des livres" },
];
export const WHEN: Option[] = [
  { key: "weekend", label: "ce week-end" },
  { key: "mercredi", label: "mercredi après-midi" },
  { key: "vacances", label: "pendant les vacances" },
  { key: "soir", label: "un soir de semaine" },
  { key: "anniv", label: "pour un anniversaire" },
];
export const WHERE: Option[] = [
  { key: "maison", label: "à la maison" },
  { key: "dehors", label: "dehors" },
  { key: "pres", label: "près de chez nous" },
  { key: "voiture", label: "en voiture" },
];
export const BUDGET: Option[] = [
  { key: "gratuit", label: "gratuit" },
  { key: "20", label: "moins de 20 €" },
  { key: "libre", label: "peu importe" },
];

/** « Toute la tribu » : exclusif avec les enfants. */
export const ALL = "tous";
/** Sans enfant enregistré : des tranches d'âge à la place des prénoms. */
const GENERIC_WHO: Option[] = [
  { key: "petits", label: "des tout-petits (0–3 ans)" },
  { key: "enfants", label: "des enfants (3–10 ans)" },
  { key: "ados", label: "des ados" },
];

export const HINTS: Record<BlankKey, string> = {
  what: "Vous cherchez plutôt…",
  who: "Pour qui ? (plusieurs choix possibles)",
  when: "Quand ?",
  where: "Où ?",
  budget: "Quel budget ?",
};

/** Couleurs des mots à toucher (fond, encre). */
export const BLANK_COLORS: Record<BlankKey, [string, string]> = {
  what: ["#FCE3D0", "#A04812"],
  who: ["#DCEDF3", "#235C77"],
  when: ["#FBEACB", "#85560C"],
  where: ["#E2EDD9", "#3E6630"],
  budget: ["#ECE1EA", "#5E4558"],
};

export interface AiRequest {
  what: string;
  /** Ids d'enfants, ALL, ou une tranche générique. */
  who: string[];
  when: string;
  where: string;
  budget: string;
  /** Le détail facultatif (« Léo adore l'espace en ce moment »). */
  detail: string;
}

const labelOf = (opts: Option[], key: string) => opts.find((o) => o.key === key)?.label ?? "";

/** Choix « pour qui » : les enfants de la tribu (« Léo, 9 ans »), puis toute la tribu. */
export function whoOptions(children: Child[]): Option[] {
  const kids = children.length
    ? children.map((c) => ({ key: c.id, label: `${c.name}, ${formatAge(c.birthDate)}` }))
    : GENERIC_WHO;
  return [...kids, { key: ALL, label: "toute la tribu" }];
}

export function optionsFor(k: BlankKey, children: Child[]): Option[] {
  return { what: WHAT, who: whoOptions(children), when: WHEN, where: WHERE, budget: BUDGET }[k];
}

/** Demande de départ : les activités du week-end, pour l'enfant choisi (ou toute la tribu). */
export function defaultRequest(children: Child[], childId?: string, detail = ""): AiRequest {
  const who = childId && children.some((c) => c.id === childId) ? [childId] : [ALL];
  return { what: "activites", who, when: "weekend", where: "maison", budget: "gratuit", detail };
}

/** Coche ou décoche un choix « pour qui » (toute la tribu exclut les autres). */
export function toggleWho(who: string[], key: string): string[] {
  if (key === ALL) return [ALL];
  const rest = who.filter((x) => x !== ALL);
  return rest.includes(key) ? rest.filter((x) => x !== key) : [...rest, key];
}

const join = (parts: string[]) => (parts.length < 2 ? parts.join("") : `${parts.slice(0, -1).join(", ")} et ${parts[parts.length - 1]}`);

/** « Léo et Emma », « toute la tribu », « qui ? » — avec les âges pour le prompt. */
function whoText(r: AiRequest, children: Child[], withAges: boolean): string {
  if (!r.who.length) return withAges ? "toute la tribu" : "qui ?";
  if (r.who.includes(ALL)) return "toute la tribu";
  return join(
    r.who.map((k) => {
      const c = children.find((x) => x.id === k);
      if (!c) return labelOf(GENERIC_WHO, k);
      return withAges ? `${c.name} (${formatAge(c.birthDate)})` : c.name;
    }).filter(Boolean),
  );
}

/** Texte de chaque mot à toucher, dans l'ordre de la phrase. */
export function blankLabel(k: BlankKey, r: AiRequest, children: Child[]): string {
  if (k === "who") return whoText(r, children, false);
  return labelOf(optionsFor(k, children), r[k]);
}

/** À qui s'adressent les idées, en court (« Léo », « la tribu »). */
export function whoShort(r: AiRequest, children: Child[]): string {
  return !r.who.length || r.who.includes(ALL) ? "la tribu" : whoText(r, children, false);
}

/** La phrase envoyée à l'IA, telle qu'affichée (avec l'âge des enfants nommés) et le détail. */
export function promptOf(r: AiRequest, children: Child[]): string {
  const sentence = `Je cherche ${labelOf(WHAT, r.what)} pour ${whoText(r, children, true)} ${labelOf(WHEN, r.when)}, ${labelOf(WHERE, r.where)}, budget ${labelOf(BUDGET, r.budget)}.`;
  const detail = r.detail.trim().replace(/[.!\s]*$/, "");
  return detail ? `${sentence} ${detail.charAt(0).toUpperCase()}${detail.slice(1)}.` : sentence;
}

/** « 5 idées pour Léo ce week-end » */
export function resultTitle(n: number, r: AiRequest, children: Child[]): string {
  return `${n} idée${n > 1 ? "s" : ""} pour ${whoShort(r, children)} ${labelOf(WHEN, r.when)}`;
}

/** « activités · à la maison · gratuit » */
export function resultSubtitle(r: AiRequest): string {
  return [labelOf(WHAT, r.what).replace(/^des /, ""), labelOf(WHERE, r.where), labelOf(BUDGET, r.budget)].join(" · ");
}

/** Nom du dossier créé pour ranger les idées : l'inspiration, sinon « Activités pour Léo ». */
export function folderNameFor(r: AiRequest, children: Child[], inspiration?: string): string {
  if (inspiration) return inspiration;
  const what = labelOf(WHAT, r.what).replace(/^des (idées )?/, "");
  return `${what.charAt(0).toUpperCase()}${what.slice(1)} pour ${whoShort(r, children)}`.slice(0, 80);
}

export interface Inspiration {
  label: string;
  /** Icône (chemin SVG 24×24) et couleurs (fond, encre). */
  d: string;
  colors: [string, string];
  set: Partial<AiRequest>;
}

/**
 * Inspirations qui remplissent la phrase d'un coup. L'anniversaire vise
 * l'enfant dont l'anniversaire arrive le plus tôt.
 */
export function inspirations(children: Child[], now = Date.now()): Inspiration[] {
  const next = children
    .map((c) => ({ c, days: countdown(Number(c.birthDate), "anniv", now)?.days ?? 999 }))
    .sort((a, b) => a.days - b.days)[0]?.c;
  const list: Inspiration[] = [
    { label: "Jour de pluie", colors: ["#ECE1EA", "#5E4558"], d: "M7 15a4 4 0 0 1 .5-8A6 6 0 0 1 19 9a3 3 0 0 1 0 6z M9 19l-1 2M13 19l-1 2M17 19l-1 2", set: { what: "activites", where: "maison", budget: "gratuit", when: "weekend" } },
    {
      label: next ? `Anniversaire de ${next.name}` : "Un anniversaire",
      colors: ["#FCE3D0", "#A04812"],
      d: "M3 8h18v5H3z M5 13v8h14v-8M12 8v13",
      set: { what: "cadeaux", who: [next?.id ?? ALL], budget: "20", when: "anniv", where: "pres" },
    },
    { label: "Goûter express", colors: ["#E2EDD9", "#3E6630"], d: "M7 3v8M5 3v5a2 2 0 0 0 4 0V3M7 11v10M17 21V3c-2 1.5-3 4-3 7h3", set: { what: "recettes", who: [ALL], when: "mercredi", where: "maison", budget: "gratuit" } },
    { label: "Grand air", colors: ["#DCEDF3", "#235C77"], d: "M3 20l6-11 4 6 3-4 5 9z", set: { what: "sorties", where: "dehors", when: "weekend", budget: "libre" } },
  ];
  return list;
}

/** Messages qui défilent pendant que l'IA rédige (elle ne cherche pas sur le web). */
export function waitingMessages(r: AiRequest, children: Child[]): string[] {
  return [
    "On rassemble vos envies…",
    "On écarte les idées trop compliquées…",
    `On choisit le meilleur pour ${whoShort(r, children)}…`,
    "On rédige chaque idée…",
    "Encore quelques secondes…",
  ];
}
