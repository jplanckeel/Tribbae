import { useId, type CSSProperties, type MouseEvent } from "react";
import { useViewTransitionState } from "react-router";
import type { Link } from "./types";

// Transitions d'écran (maquette « Transitions & navigation »), par l'API View
// Transitions que React Router déclenche (prop `viewTransition`) et rejoue à
// l'envers au retour arrière. Trois mouvements, choisis par <html data-motion> :
//  - « open » : la carte d'une idée devient sa fiche (image et titre partagés) ;
//  - « push » : on creuse (fiche → dossier), la page arrive par la droite ;
//  - « tab » : changer d'onglet, glissement latéral (dir forward = vers la droite).
// Les styles sont dans index.css (::view-transition-*). Même logique côté
// Android dans AppModern.kt / ui/Transitions.kt.

export type Motion = "open" | "push" | "tab";

const root = () => document.documentElement;
let timer = 0;

/** Annonce le mouvement de la prochaine navigation (aller ou retour). */
export function setMotion(motion: Motion, dir: "forward" | "back") {
  root().dataset.motion = motion;
  root().dataset.dir = dir;
  window.clearTimeout(timer);
  // Le temps de la transition, puis on oublie (les insertions suivantes n'animent plus)
  timer = window.setTimeout(() => {
    delete root().dataset.motion;
    delete root().dataset.dir;
  }, 700);
}

export const currentMotion = (): Motion | undefined => root().dataset.motion as Motion | undefined;

// Mouvement qui a mené à l'entrée d'historique affichée : au retour arrière,
// c'est lui qu'on rejoue à l'envers.
let shown: Motion | undefined;
let shownIdx = 0;

// React Router range l'état de navigation dans history.state.usr et la position dans .idx
type HistoryState = { idx?: number; usr?: { motion?: Motion } } | null;

/** À appeler à chaque changement d'écran avec le `state` de la location. */
export function trackLocation(state: unknown) {
  shown = (state as { motion?: Motion } | null)?.motion;
  shownIdx = (window.history.state as HistoryState)?.idx ?? 0;
}

if (typeof window !== "undefined") {
  // Enregistré avant le routeur (import depuis main.tsx) : passe avant son propre écouteur
  window.addEventListener("popstate", () => {
    const next = window.history.state as HistoryState;
    if ((next?.idx ?? 0) > shownIdx) {
      // Bouton « suivant » : on rejoue le mouvement de l'entrée retrouvée
      if (next?.usr?.motion) setMotion(next.usr.motion, "forward");
    } else if (shown) {
      setMotion(shown, "back");
    }
  });
}

// Carte d'origine de la dernière fiche ouverte : seule elle partage son image
// et son titre avec la fiche (une même idée peut figurer dans plusieurs rails).
let openedFrom = "";

/**
 * Pour une carte d'idée : props du lien (transition « open ») et noms de
 * transition de son visuel et de son titre pendant l'ouverture et le retour.
 */
export function useIdeaMorph(link: Link) {
  const id = useId();
  const to = `/links/${link.id}`;
  const transitioning = useViewTransitionState(to);
  const shared = transitioning && openedFrom === id && currentMotion() === "open";
  return {
    linkProps: {
      viewTransition: true,
      // `preview` : la fiche s'affiche tout de suite avec la carte, le temps de charger l'idée
      state: { motion: "open" as Motion, preview: link },
      onClick: (e: MouseEvent) => {
        if (e.defaultPrevented || e.metaKey || e.ctrlKey || e.shiftKey || e.button !== 0) return;
        openedFrom = id;
        setMotion("open", "forward");
      },
    },
    visualStyle: shared ? ({ viewTransitionName: "idea-visual" } as CSSProperties) : undefined,
    titleStyle: shared ? ({ viewTransitionName: "idea-title" } as CSSProperties) : undefined,
  };
}

/**
 * Pour la fiche : mêmes noms que la carte d'origine, seulement pendant une
 * ouverture ou son retour (la fiche se re-rend quand la transition démarre).
 */
export function useIdeaMorphTarget(linkId: string) {
  const shared = useViewTransitionState(`/links/${linkId}`) && currentMotion() === "open";
  return {
    visualStyle: shared ? ({ viewTransitionName: "idea-visual" } as CSSProperties) : undefined,
    titleStyle: shared ? ({ viewTransitionName: "idea-title" } as CSSProperties) : undefined,
  };
}

/** Props d'un onglet de la barre : la page glisse dans le sens de l'onglet choisi. */
export function tabLink(target: number, current: number) {
  return {
    viewTransition: target !== current && current >= 0,
    onClick: () => {
      if (target !== current && current >= 0) setMotion("tab", target > current ? "forward" : "back");
    },
  };
}

/** Props d'un lien qui « creuse » (fiche → dossier) ; `from` est surlignée à l'arrivée. */
export function pushLink(from?: string) {
  return {
    viewTransition: true,
    state: { motion: "push" as Motion, from },
    onClick: () => setMotion("push", "forward"),
  };
}
