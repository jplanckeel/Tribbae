import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { birthdayLabel, circleOf, ideasForChild, monthName, ringPosition, yearInIdeas } from "./tribe";
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

it("résume l'année en idées", () => {
  const link = (id: string, category: string, createdAt: string) => ({ id, category, createdAt }) as Link;
  const links = [
    link("1", "LINK_CATEGORY_EVENEMENT", "2026-07-02T12:00:00Z"),
    link("2", "LINK_CATEGORY_ACTIVITE", "2026-07-14T12:00:00Z"),
    link("3", "LINK_CATEGORY_RECETTE", "2026-03-01T12:00:00Z"),
    link("4", "LINK_CATEGORY_CADEAU", "2025-12-24T12:00:00Z"),
    link("5", "LINK_CATEGORY_IDEE", ""),
  ];
  expect(yearInIdeas(links, 2026)).toEqual({ outings: 2, recipes: 1, gifts: 0, total: 3, bestMonth: { month: 6, count: 2 } });
  expect(monthName(6)).toBe("juillet");
  expect(yearInIdeas(links, 2024).bestMonth).toBeUndefined();
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
