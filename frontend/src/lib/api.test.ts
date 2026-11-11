import { describe, expect, it } from "vitest";
import { frenchMessage } from "./api";

// Mêmes messages que data/Moderation.kt (ModerationTest.reportErrorsAreExplained)
describe("messages de modération", () => {
  it("le plafond de signalements dit d'attendre demain, pas une minute", () => {
    expect(frenchMessage(429, "too many reports, try again tomorrow")).toBe("Vous avez envoyé beaucoup de signalements aujourd'hui : réessayez demain.");
  });
  it("les autres 429 restent « une minute »", () => {
    expect(frenchMessage(429, "too many attempts")).toBe("Trop de tentatives, réessayez dans une minute.");
  });
  it("explique les refus de modération", () => {
    expect(frenchMessage(400, "cannot report your own content")).toBe("Vous ne pouvez pas signaler votre contenu ni celui de votre tribu.");
    expect(frenchMessage(400, "idea removed by moderation")).toBe("Cette idée a été retirée par la modération : elle ne peut plus être publiée.");
    expect(frenchMessage(403, "account suspended")).toMatch(/^Ce compte a été suspendu/);
  });
});
