import { describe, expect, it } from "vitest";
import { createdViaLabel, scopeLabels } from "./oauth";

describe("scopeLabels", () => {
  it("affiche les droits connus dans un ordre fixe", () => {
    expect(scopeLabels(["links:write", "folders:read"])).toEqual([
      "Voir la liste de vos dossiers",
      "Ajouter des idées dans vos dossiers, toujours en privé",
    ]);
  });

  it("n'efface jamais un droit inconnu", () => {
    expect(scopeLabels(["links:read", "admin"])).toEqual(["Voir les idées de vos dossiers (titre, lien, catégorie)", "admin"]);
  });
});

describe("createdViaLabel", () => {
  it("signale les idées ajoutées par une application connectée", () => {
    expect(createdViaLabel("mcp")).toBe("Ajoutée par Claude");
    expect(createdViaLabel(undefined)).toBeUndefined();
    expect(createdViaLabel("")).toBeUndefined();
  });
});
