import { describe, expect, it } from "vitest";
import type { Collaborator, Folder } from "./types";
import { folderAudience, ideaAudience } from "./visibility";

const peers = new Set(["moi", "tribu"]);
const folder = (over: Partial<Folder> = {}) => ({ visibility: "VISIBILITY_PRIVATE", collaborators: [], ownerId: "moi", ...over }) as Folder;
const collaborator = { userId: "camille", email: "", displayName: "Camille", role: "COLLABORATOR_ROLE_VIEWER" } as Collaborator;

// Mêmes cas que data/AudienceTest.kt côté Android
describe("visibilité d'un dossier", () => {
  it("est Privé pour un dossier du compte ou de la tribu", () => {
    expect(folderAudience(folder(), peers)).toBe("private");
    expect(folderAudience(folder({ ownerId: "tribu" }), peers)).toBe("private");
    expect(folderAudience(folder({ ownerId: "" }), peers)).toBe("private");
  });

  it("est Tribu avec des collaborateurs ou un propriétaire hors tribu", () => {
    expect(folderAudience(folder({ collaborators: [collaborator] }), peers)).toBe("shared");
    expect(folderAudience(folder({ ownerId: "tata" }), peers)).toBe("shared");
  });

  it("est Communauté dès qu'il est public, même avec des collaborateurs", () => {
    expect(folderAudience(folder({ visibility: "VISIBILITY_PUBLIC", collaborators: [collaborator] }), peers)).toBe("public");
  });
});

describe("visibilité d'une idée", () => {
  it("suit l'idée puis son dossier", () => {
    expect(ideaAudience({ visibility: "public" }, undefined, peers)).toBe("public");
    expect(ideaAudience({ visibility: "private" }, undefined, peers)).toBe("private");
    expect(ideaAudience({ visibility: "private" }, folder(), peers)).toBe("private");
    expect(ideaAudience({ visibility: "private" }, folder({ collaborators: [collaborator] }), peers)).toBe("shared");
  });

  it("est Communauté dans un dossier public, même marquée privée", () => {
    expect(ideaAudience({ visibility: "private" }, folder({ visibility: "VISIBILITY_PUBLIC" }), peers)).toBe("public");
  });
});
