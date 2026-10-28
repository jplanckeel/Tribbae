import { describe, expect, it } from "vitest";
import { byRotation, canEditFolder, extractUrl, ROTATION_MS, rotationScore, tagCounts } from "./links";
import type { Folder, Link } from "./types";

describe("ordre « À la une »", () => {
  const now = Date.parse("2026-10-20T09:00:00Z");

  // Valeurs de référence partagées avec Android (ui/RotationScoreTest.kt) :
  // les deux clients doivent afficher le même ordre.
  it("donne les mêmes scores que l'app Android", () => {
    expect(rotationScore({ id: "6ad510ad50e6b1b0dd36abcd", likeCount: 3, createdAt: "2026-10-15T08:00:00Z" }, now)).toBeCloseTo(0.864783130593835, 12);
    expect(rotationScore({ id: "64f0c2aa11b2c3d4e5f60718", likeCount: 0, createdAt: "2025-01-01T00:00:00Z" }, now)).toBeCloseTo(0.754857371677645, 12);
    expect(rotationScore({ id: "crêpe-é", likeCount: 12 }, now)).toBeCloseTo(0.53694028735724, 12);
  });

  it("reste stable pendant la période et change à la suivante", () => {
    const l = { id: "6ad510ad50e6b1b0dd36abcd", likeCount: 3 };
    const a = rotationScore(l, now);
    expect(rotationScore(l, now + 3600_000)).toBe(a);
    expect(rotationScore(l, now + ROTATION_MS)).not.toBe(a);
  });

  it("trie une copie sans perdre d'idée", () => {
    const list = Array.from({ length: 20 }, (_, i) => ({ id: `id-${i}`, likeCount: i }));
    const sorted = byRotation(list, now);
    expect(sorted).not.toBe(list);
    expect(sorted.map((l) => l.id).sort()).toEqual(list.map((l) => l.id).sort());
    expect(byRotation(list, now + 3600_000)).toEqual(sorted);
  });
});

describe("extractUrl", () => {
  it.each([
    ["Regarde ça https://exemple.fr/page !", "https://exemple.fr/page"],
    ["https://exemple.fr/a?b=1.", "https://exemple.fr/a?b=1"],
    ["www.site.fr/page", "https://www.site.fr/page"],
    ["va voir lebonsite.com", "https://lebonsite.com/"],
    ["(https://exemple.fr/x)", "https://exemple.fr/x"],
  ])("trouve l'adresse dans « %s »", (text, url) => {
    expect(extractUrl(text)).toBe(url);
  });

  it.each([["fin.Début de phrase"], [""], [null], ["javascript:alert(1)"]])("ignore « %s »", (text) => {
    expect(extractUrl(text)).toBeUndefined();
  });
});

describe("canEditFolder", () => {
  const folder = {
    id: "f",
    ownerId: "alice",
    name: "Noël",
    collaborators: [
      { userId: "mamie", role: "COLLABORATOR_ROLE_EDITOR" },
      { userId: "papi", role: "COLLABORATOR_ROLE_VIEWER" },
    ],
  } as Folder;

  it("propriétaire, membre de la tribu et éditeur seulement", () => {
    expect(canEditFolder(folder, "alice")).toBe(true);
    expect(canEditFolder(folder, "bob", new Set(["bob", "alice"]))).toBe(true);
    expect(canEditFolder(folder, "mamie")).toBe(true);
    expect(canEditFolder(folder, "papi")).toBe(false);
    expect(canEditFolder(folder, "inconnu")).toBe(false);
    expect(canEditFolder(folder, undefined)).toBe(false);
  });
});

it("compte les tags, du plus utilisé au moins utilisé", () => {
  const links = [{ tags: ["pluie", "été"] }, { tags: ["été"] }, {}, { tags: ["automne"] }] as Link[];
  expect(tagCounts(links)).toEqual([["été", 2], ["automne", 1], ["pluie", 1]]);
});
