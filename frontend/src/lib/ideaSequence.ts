import { createContext } from "react";

// Suite d'idées : les idées d'un dossier, dans l'ordre où le dossier les montre
// (tri et filtre compris). La fiche d'une idée ouverte depuis le dossier passe à
// la précédente ou à la suivante d'un glissement sur le côté. Même logique sur
// Android (data/IdeaSequence.kt) : ideaSequence.test.ts ↔ IdeaSequenceTest.

export interface IdeaSequence {
  /** Identifiants, dans l'ordre affiché. */
  ids: string[];
}

/** Fournie par un écran qui liste des idées : les cartes la passent à la fiche. */
export const IdeaSequenceContext = createContext<IdeaSequence | undefined>(undefined);

export interface Neighbors {
  prev?: string;
  next?: string;
  /** Position (à partir de 1) et nombre d'idées. */
  position: number;
  total: number;
}

/**
 * Voisines d'une idée dans la suite (seules [available] comptent : une idée
 * supprimée entre-temps est sautée). Aucune voisine hors de la suite, ou si la
 * suite n'a qu'une idée.
 */
export function neighbors(sequence: IdeaSequence | undefined, id: string, available?: (id: string) => boolean): Neighbors | undefined {
  const ids = (sequence?.ids ?? []).filter((x, i, all) => all.indexOf(x) === i && (x === id || !available || available(x)));
  const i = ids.indexOf(id);
  if (i < 0 || ids.length < 2) return undefined;
  return { prev: ids[i - 1], next: ids[i + 1], position: i + 1, total: ids.length };
}

/**
 * Glissement horizontal d'un doigt : -1 vers la gauche (idée suivante), 1 vers
 * la droite (précédente), 0 sinon (trop court, trop lent, ou plutôt vertical).
 */
export function swipeDirection(dx: number, dy: number, ms: number): -1 | 0 | 1 {
  if (Math.abs(dx) < 60 || Math.abs(dx) < 1.5 * Math.abs(dy) || ms > 700) return 0;
  return dx < 0 ? -1 : 1;
}
