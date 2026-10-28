import { describe, expect, it } from "vitest";
import { autoIllustration, countdown, resolvedCover, sortFolderLinks } from "./folderStyle";
import type { Folder, Link } from "./types";

// Mêmes règles qu'Android (composeApp/.../data/FolderStyleTest.kt).
const at = (y: number, m: number, d: number, h = 12) => new Date(y, m - 1, d, h).getTime();
const folder = (f: Partial<Folder>) => ({ id: "1", ownerId: "o", name: "", ...f }) as Folder;

describe("illustration automatique", () => {
  it.each([
    ["Noël 2026", "noel"],
    ["Anniversaire Emma", "anniv"],
    ["Cadeaux pour Léo", "cadeaux"],
    ["Ma liste", "cadeaux"],
    ["Jeux de société", "jouets"],
    ["Été à la mer", "plage"],
    ["Vacances Drôme", "montagne"],
    ["Goûters", "gouter"],
    ["Jours de pluie", "pluie"],
    ["Automne", "automne"],
  ])("« %s » → %s", (name, expected) => {
    expect(autoIllustration(name)).toBe(expected);
  });

  it("rien pour un nom quelconque, même avec « ete » au milieu d'un mot", () => {
    expect(autoIllustration("Divers")).toBeNull();
    expect(autoIllustration("Compléments")).toBeNull();
  });

  it("se rabat sur la date clé", () => {
    expect(autoIllustration("Cadeaux pour Léo", at(2026, 11, 14), "anniv")).toBe("anniv");
    expect(autoIllustration("Divers", at(2026, 4, 2), "vacances")).toBe("montagne");
    expect(autoIllustration("Divers", at(2026, 12, 2), "event")).toBe("noel");
    expect(autoIllustration("Divers", at(2026, 10, 2), "event")).toBe("automne");
  });
});

it("résout la couverture", () => {
  expect(resolvedCover(folder({ name: "Vacances" }))).toEqual({ cover: "illu", illustration: "montagne" });
  expect(resolvedCover(folder({ name: "Divers" }))).toEqual({ cover: "tipis", illustration: null });
  expect(resolvedCover(folder({ name: "Vacances", cover: "pois" }))).toEqual({ cover: "pois", illustration: "montagne" });
  expect(resolvedCover(folder({ name: "Vacances", cover: "illu", illustration: "plage" }))).toEqual({ cover: "illu", illustration: "plage" });
});

describe("compte à rebours de la date clé", () => {
  const now = at(2026, 10, 21, 18);

  it("anniversaire, chaque année", () => {
    expect(countdown(at(2016, 11, 14, 0), "anniv", now)?.label).toBe("Anniv. dans 24 j");
    expect(countdown(at(2016, 10, 21), "anniv", now)?.label).toBe("Anniv. aujourd'hui !");
    expect(countdown(at(2016, 10, 20), "anniv", now)?.days).toBe(364);
  });

  it("départ et événement", () => {
    expect(countdown(at(2026, 11, 2), "vacances", now)?.label).toBe("Départ J-12");
    expect(countdown(at(2026, 10, 24), "event", now)?.label).toBe("Sam. 24 · J-3");
    expect(countdown(at(2026, 12, 25), "event", now)?.label).toBe("J-65");
  });

  it("rien pour une date passée ou absente", () => {
    expect(countdown(at(2026, 10, 20), "event", now)).toBeNull();
    expect(countdown(0, "event", now)).toBeNull();
    expect(countdown(undefined, "event", now)).toBeNull();
  });
});

describe("tri des idées d'un dossier", () => {
  const links = [
    { id: "a", category: "LINK_CATEGORY_RECETTE", createdAt: "2026-10-03" },
    { id: "b", category: "LINK_CATEGORY_EVENEMENT", createdAt: "2026-10-01", eventDate: String(at(2026, 11, 5)) },
    { id: "c", category: "LINK_CATEGORY_EVENEMENT", createdAt: "2026-10-02", eventDate: at(2026, 10, 30) / 1000 },
  ] as Link[];
  const ids = (l: Link[]) => l.map((x) => x.id);

  it("récentes d'abord par défaut", () => {
    expect(ids(sortFolderLinks(links, undefined, []))).toEqual(["a", "c", "b"]);
  });
  it("par date : sorties datées d'abord (secondes ou millisecondes)", () => {
    expect(ids(sortFolderLinks(links, "date", []))).toEqual(["c", "b", "a"]);
  });
  it("par catégorie, dans l'ordre donné", () => {
    expect(ids(sortFolderLinks(links, "category", ["LINK_CATEGORY_EVENEMENT", "LINK_CATEGORY_RECETTE"]))).toEqual(["c", "b", "a"]);
  });
});
