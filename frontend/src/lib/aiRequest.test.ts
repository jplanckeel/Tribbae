import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  ALL,
  blankLabel,
  defaultRequest,
  folderNameFor,
  inspirations,
  promptOf,
  resultSubtitle,
  resultTitle,
  toggleWho,
  whoOptions,
} from "./aiRequest";
import type { Child } from "./types";

// Mêmes règles qu'Android (composeApp/.../data/AiRequestTest.kt).
const at = (iso: string) => new Date(iso).getTime(); // heure locale
const now = at("2026-10-23T12:00:00");
const leo: Child = { id: "leo", name: "Léo", birthDate: at("2017-02-03T12:00:00") };
const emma: Child = { id: "emma", name: "Emma", birthDate: at("2020-11-16T12:00:00") };
const kids = [leo, emma];

beforeEach(() => vi.setSystemTime(now));
afterEach(() => vi.useRealTimers());

describe("phrase envoyée à l'IA", () => {
  it("nomme les enfants avec leur âge et ajoute le détail", () => {
    const r = defaultRequest(kids, "leo");
    expect(promptOf(r, kids)).toBe("Je cherche des activités pour Léo (9 ans) ce week-end, à la maison, budget gratuit.");
    const both = { ...r, who: ["leo", "emma"], detail: "ils adorent l'espace en ce moment !" };
    expect(promptOf(both, kids)).toBe(
      "Je cherche des activités pour Léo (9 ans) et Emma (5 ans) ce week-end, à la maison, budget gratuit. Ils adorent l'espace en ce moment.",
    );
    expect(blankLabel("who", both, kids)).toBe("Léo et Emma");
    expect(promptOf(defaultRequest([]), [])).toBe("Je cherche des activités pour toute la tribu ce week-end, à la maison, budget gratuit.");
  });

  it("un enfant inconnu donne toute la tribu", () => {
    expect(defaultRequest(kids, "inconnu").who).toEqual([ALL]);
  });
});

it("« toute la tribu » exclut les enfants, et inversement", () => {
  expect(toggleWho(["leo"], "emma")).toEqual(["leo", "emma"]);
  expect(toggleWho(["leo", "emma"], ALL)).toEqual([ALL]);
  expect(toggleWho([ALL], "emma")).toEqual(["emma"]);
  expect(toggleWho(["emma"], "emma")).toEqual([]);
  expect(whoOptions(kids).map((o) => o.label)).toEqual(["Léo, 9 ans", "Emma, 5 ans", "toute la tribu"]);
});

it("remplit la phrase depuis une inspiration", () => {
  const insp = inspirations(kids, now);
  expect(insp.map((i) => i.label)).toEqual(["Jour de pluie", "Anniversaire de Emma", "Goûter express", "Grand air"]);
  const r = { ...defaultRequest(kids), ...insp[1].set };
  expect(promptOf(r, kids)).toBe("Je cherche des idées cadeaux pour Emma (5 ans) pour un anniversaire, près de chez nous, budget moins de 20 €.");
  expect(resultTitle(5, r, kids)).toBe("5 idées pour Emma pour un anniversaire");
  expect(resultSubtitle(r)).toBe("idées cadeaux · près de chez nous · moins de 20 €");
  expect(folderNameFor(r, kids)).toBe("Cadeaux pour Emma");
  expect(folderNameFor(r, kids, "Jour de pluie")).toBe("Jour de pluie");
});
