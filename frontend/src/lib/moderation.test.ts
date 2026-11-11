import { describe, expect, it } from "vitest";
import { DETAILS_MAX, groupReports, reasonLabel, REPORT_REASONS } from "./moderation";

// Mêmes cas que ModerationTest côté Android
describe("signalements", () => {
  it("propose les motifs connus du serveur", () => {
    expect(REPORT_REASONS.map(([k]) => k)).toEqual(["inappropriate", "harassment", "spam", "other"]);
    expect(reasonLabel("spam")).toBe("Spam ou publicité");
    expect(reasonLabel("inconnu")).toBe("Autre raison");
    expect(DETAILS_MAX).toBe(500);
  });
});

describe("file de l'administration", () => {
  it("regroupe les signalements d'un même contenu, dans l'ordre d'arrivée", () => {
    const r = (id: string, targetType: string, targetId: string) => ({ id, targetType, targetId });
    const groups = groupReports([r("1", "idea", "a"), r("2", "comment", "a"), r("3", "idea", "a"), r("4", "account", "b")]);
    expect(groups.map((g) => g.map((x) => x.id))).toEqual([["1", "3"], ["2"], ["4"]]);
  });
});

