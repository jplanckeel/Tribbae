import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { bestMonthLine, birthdayLabel, circleOf, ideasForChild, monthName, ringPosition, yearInIdeas, yearLabels } from "./tribe";
import type { Child, Collaborator, Folder, Link } from "./types";

// Mêmes règles qu'Android (composeApp/.../data/TribePageTest.kt).
it("place la tribu autour du feu", () => {
  const pos = (i: number, n: number) => {
    const p = ringPosition(i, n);
    return [p.dx, p.dy];
  };
  expect(pos(0, 1)).toEqual([0, -105]);
  expect([0, 1, 2, 3].map((i) => pos(i, 4))).toEqual([[-95, -44], [-39, -98], [39, -98], [95, -44]]);
  expect(pos(2, 5)).toEqual([0, -105]);
});

it("regroupe le cercle par personne, sans le foyer", () => {
  const jo: Collaborator = { userId: "jo", displayName: "Mamie Jo", role: "COLLABORATOR_ROLE_EDITOR" };
  const michel: Collaborator = { userId: "mi", displayName: "Papi Michel", role: "COLLABORATOR_ROLE_VIEWER" };
  const camille: Collaborator = { userId: "cam", displayName: "Camille", role: "COLLABORATOR_ROLE_EDITOR" };
  const folders = [
    { id: "a", ownerId: "o", name: "Cadeaux Léo", collaborators: [jo, michel, camille] },
    { id: "b", ownerId: "o", name: "Noël", collaborators: [michel, { ...jo, role: "COLLABORATOR_ROLE_VIEWER" }] },
    { id: "c", ownerId: "o", name: "Vacances", collaborators: [{ userId: "ines", email: "ines@exemple.fr", role: "COLLABORATOR_ROLE_VIEWER" }] },
  ] as Folder[];
  const circle = circleOf(folders, (id) => id === "cam");
  expect(circle.map((m) => m.name)).toEqual(["Mamie Jo", "Papi Michel", "ines@exemple.fr"]);
  expect(circle.map((m) => m.editor)).toEqual([true, false, false]);
  expect(circle[0].folders.map((f) => f.id)).toEqual(["a", "b"]);
});

it("résume l'année en idées faites", () => {
  const y = yearInIdeas({ year: 2026, outings: 14, recipes: 24, books: 9, total: 52, bestMonth: 7, bestMonthCount: 12 });
  expect(y).toEqual({ outings: 14, recipes: 24, books: 9, total: 52, bestMonth: { month: 6, count: 12 } });
  expect(yearLabels(y)).toEqual({ outings: "sorties faites", recipes: "recettes cuisinées", books: "livres lus" });
  expect(bestMonthLine(y)).toBe("Votre meilleur mois : juillet, 12 idées faites.");
  expect(monthName(6)).toBe("juillet");

  const one = yearInIdeas({ year: 2026, outings: 1, recipes: 1, books: 1, total: 3, bestMonth: 3, bestMonthCount: 1 });
  expect(yearLabels(one)).toEqual({ outings: "sortie faite", recipes: "recette cuisinée", books: "livre lu" });
  expect(bestMonthLine(one)).toBe("Votre meilleur mois : mars, 1 idée faite.");

  // Rien de fait, ou le bilan n'est pas encore arrivé
  const none = yearInIdeas(undefined);
  expect(none).toEqual({ outings: 0, recipes: 0, books: 0, total: 0 });
  expect(yearInIdeas({ year: 2024 }).bestMonth).toBeUndefined();
  expect(bestMonthLine(none)).toBe("Marquez vos premières idées faites : elles rempliront votre année.");
});

describe("idées pour un enfant", () => {
  beforeEach(() => vi.setSystemTime(new Date("2026-10-23T12:00:00Z")));
  afterEach(() => vi.useRealTimers());

  it("compte celles de ses dossiers et celles de son âge", () => {
    const emma: Child = { id: "e", name: "Emma", birthDate: String(Date.parse("2020-11-16T12:00:00Z")) };
    const folders = [{ id: "anniv", ownerId: "o", name: "Anniv. Emma", childIds: ["e"] }] as Folder[];
    const links = [
      { id: "1", folderId: "anniv" },
      { id: "2", ageRange: "3–6 ans" },
      { id: "3", ageRange: "10 ans et +" },
    ] as Link[];
    expect(ideasForChild(links, folders, emma)).toBe(2);
    expect(birthdayLabel(emma.birthDate)).toBe("Anniversaire le 16 novembre");
    expect(birthdayLabel(Date.parse("2019-02-01T12:00:00Z"))).toBe("Anniversaire le 1er février");
  });
});
