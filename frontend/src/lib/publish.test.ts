import { describe, expect, it } from "vitest";
import { exposedBy, PUBLISH_IDEA, publishFolder } from "./publish";

// Mêmes cas que data/PublishConfirmTest.kt côté Android
describe("confirmation avant de publier", () => {
  it("dit ce que devient une idée", () => {
    expect(PUBLISH_IDEA.text).toBe("Votre idée sera visible par toutes les familles dans Explorer. Les prénoms des enfants ne sont jamais publiés.");
  });

  it("ne compte que les idées que le dossier expose", () => {
    const folder = { id: "d", ownerId: "alice" };
    expect(exposedBy({ folderId: "d", ownerId: "alice", visibility: "private" }, folder)).toBe(true);
    expect(exposedBy({ folderId: "d", ownerId: "dan", visibility: "private" }, folder)).toBe(false);
    expect(exposedBy({ folderId: "d", ownerId: "dan", visibility: "public" }, folder)).toBe(true);
    expect(exposedBy({ folderId: "autre", ownerId: "alice", visibility: "private" }, folder)).toBe(false);
  });

  it("compte les idées d'un dossier", () => {
    expect(publishFolder(12).text).toBe("Les 12 idées de ce dossier seront visibles par toutes les familles dans Explorer. Les prénoms des enfants ne sont jamais publiés.");
    expect(publishFolder(1).text).toBe("L'idée de ce dossier sera visible par toutes les familles dans Explorer. Les prénoms des enfants ne sont jamais publiés.");
    expect(publishFolder(0).text).toBe("Ce dossier sera visible par toutes les familles dans Explorer. Les prénoms des enfants ne sont jamais publiés.");
  });
});
