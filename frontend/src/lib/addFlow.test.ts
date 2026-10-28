import { describe, expect, it } from "vitest";
import { ago, agoShort, bandsToAge, selectedBands, suggestFolders, whoSees } from "./addFlow";
import type { Collaborator, Folder, Link } from "./types";

// Mêmes règles qu'Android (composeApp/.../data/AddFlowTest.kt).
const now = Date.parse("2026-10-21T12:00:00Z");
const folder = (f: Partial<Folder>) => ({ ownerId: "o", name: "", ...f }) as Folder;
const collab = (displayName: string) => ({ userId: displayName, displayName, role: "COLLABORATOR_ROLE_VIEWER" }) as Collaborator;

describe("dossiers suggérés", () => {
  const sorties = folder({ id: "a", name: "Sorties d'automne", updatedAt: "2026-10-01" });
  const drome = folder({ id: "b", name: "Vacances Drôme", updatedAt: "2026-10-10", collaborators: [collab("Inès")] });
  const noel = folder({ id: "c", name: "Noël", updatedAt: "2026-10-05" });
  const links = [
    { id: "1", category: "LINK_CATEGORY_EVENEMENT", folderId: "a", createdAt: "2026-10-01T10:00:00Z" },
    { id: "2", category: "LINK_CATEGORY_RECETTE", folderId: "c", createdAt: "2026-10-21T09:00:00Z" },
  ] as Link[];

  it("propose trois dossiers avec leur raison", () => {
    const s = suggestFolders("LINK_CATEGORY_EVENEMENT", [sorties, drome, noel], links, undefined, now);
    expect(s.map((x) => [x.folder.id, x.reason])).toEqual([
      ["a", "Vous y rangez vos sorties"],
      ["c", "Utilisé il y a 3 h"],
      ["b", "Partagé avec Inès"],
    ]);
  });

  it("met le dossier ouvert en premier", () => {
    expect(suggestFolders("LINK_CATEGORY_EVENEMENT", [sorties, drome, noel], links, "b", now)[0].folder.id).toBe("b");
  });

  it("pour un cadeau, propose le dossier dont la date clé approche", () => {
    const anniv = folder({ id: "d", name: "Anniv. Emma", keyDate: String(Date.parse("2026-11-14T12:00:00Z")), keyDateType: "anniv" });
    const s = suggestFolders("LINK_CATEGORY_CADEAU", [sorties, anniv], [], undefined, now);
    expect(s[0]).toEqual({ folder: anniv, reason: "Anniv. dans 24 j" });
  });
});

describe("qui la verra", () => {
  it.each([
    [folder({ id: "b", collaborators: [collab("Inès")] }), false, "Inès la verra"],
    [folder({ id: "b", collaborators: [collab("Inès"), collab("Camille")] }), false, "Inès et Camille la verront"],
    [folder({ id: "b", collaborators: ["Inès", "Camille", "Jo", "Léa"].map(collab) }), false, "Inès, Camille et 2 autres la verront"],
    [folder({ id: "b", visibility: "VISIBILITY_PUBLIC" }), false, "Visible par toute la communauté"],
    [undefined, true, "Visible par toute la communauté"],
    [undefined, false, "Visible par vous et votre tribu"],
  ])("%#", (f, isPublic, expected) => {
    expect(whoSees(f, isPublic)).toBe(expected);
  });
});

it("tranches d'âge cochées", () => {
  expect(bandsToAge(["3-6", "6-10"])).toBe("3–10 ans");
  expect(selectedBands("3–10 ans")).toEqual(["3-6", "6-10"]);
  expect(bandsToAge([])).toBe("");
  expect(bandsToAge(["10+"])).toBe("10 ans et +");
});

it("durées écoulées", () => {
  expect(ago("2026-10-20T10:00:00Z", now)).toBe("hier");
  expect(ago("2026-10-21T11:59:30Z", now)).toBe("à l'instant");
  expect(ago("2026-10-21T11:30:00Z", now)).toBe("il y a 30 min");
  expect(ago("2026-10-14T12:00:00Z", now)).toBe("il y a 7 jours");
  expect(ago("2026-09-01T12:00:00Z", now)).toBe("il y a 7 semaines");
  expect(ago(undefined, now)).toBe("récemment");
  expect(agoShort("2026-10-18T12:00:00Z", now)).toBe("3 j");
  expect(agoShort("", now)).toBe("");
});
