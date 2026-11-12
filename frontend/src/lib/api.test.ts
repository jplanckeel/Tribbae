import { describe, expect, it } from "vitest";
import { frenchMessage } from "./api";

// Mêmes messages que data/Done.kt (DoneTest.refusalsAreExplained)
describe("messages de « C'est fait »", () => {
  it("explique les refus", () => {
    expect(frenchMessage(429, "too many ideas marked done today")).toBe("Vous avez marqué beaucoup d'idées aujourd'hui : réessayez demain.");
    expect(frenchMessage(400, "gift list ideas are marked through their reservation")).toBe("Dans une liste de cadeaux, c'est la réservation qui dit qu'un cadeau est acheté.");
  });
});

// Mêmes messages que data/Gifts.kt (GiftsTest.refusalsAreExplained)
describe("messages des listes de cadeaux", () => {
  it("explique les refus de réservation", () => {
    expect(frenchMessage(409, "gift already reserved")).toBe("Quelqu'un vient de le réserver.");
    expect(frenchMessage(400, "gift reservations are off for this folder")).toBe("Cette liste n'accepte plus de réservations.");
    expect(frenchMessage(429, "too many gift reservations today")).toBe("Vous avez déjà réservé 5 cadeaux sur cette liste aujourd'hui : réessayez demain.");
  });
});

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
