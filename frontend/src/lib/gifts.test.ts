import { describe, expect, it } from "vitest";
import { giftProgress, giftState, matchesGiftFilter, sharedGiftStatus, surpriseLabel } from "./gifts";

// Mêmes cas que GiftsTest (Android)
describe("offrir une idée", () => {
  it("donne le bon état, sans jamais dire qui offre", () => {
    expect(giftState("").label).toBe("Libre");
    expect(giftState("").free).toBe(true);
    expect(giftState("reserved").label).toBe("Réservé");
    expect(giftState("reserved").dim).toBe(true);
    expect(giftState("bought").label).toBe("Offert");
    expect(giftState("bought").strike).toBe(true);
    expect(giftState("mine").label).toBe("Vous l'offrez");
    expect(giftState("mine_bought").label).toBe("Acheté par vous");
    expect(giftState("pending").label).toBe("À confirmer");
  });

  it("compte l'avancement de la liste", () => {
    const p = giftProgress(["", "reserved", "mine", "bought", "mine_bought"]);
    expect(p).toMatchObject({ done: 2, taken: 2, free: 1, pDone: 40, pTaken: 40 });
    expect(p.label).toBe("2 offerts · 2 réservés");
    expect(p.freeLabel).toBe("1 encore libre");
    expect(giftProgress(["", ""]).label).toBe("0 offert · 0 réservé");
    expect(giftProgress([]).pDone).toBe(0);
  });

  it("filtre À offrir et Mes cadeaux", () => {
    expect(matchesGiftFilter("", "free")).toBe(true);
    expect(matchesGiftFilter("reserved", "free")).toBe(false);
    expect(matchesGiftFilter("mine", "mine")).toBe(true);
    expect(matchesGiftFilter("pending", "mine")).toBe(true);
    expect(matchesGiftFilter("bought", "mine")).toBe(false);
    expect(matchesGiftFilter("bought", "all")).toBe(true);
  });

  it("dit à la créatrice combien d'idées sont prises, pas lesquelles", () => {
    expect(surpriseLabel(["", ""])).toBe("Aucune idée n'a encore trouvé preneur.");
    expect(surpriseLabel(["reserved", "", ""])).toBe("1 idée sur 3 a trouvé preneur.");
    expect(surpriseLabel(["reserved", "bought", "", "", ""])).toBe("2 idées sur 5 ont trouvé preneur.");
  });

  it("montre « À confirmer » au navigateur qui attend l'e-mail", () => {
    expect(sharedGiftStatus("reserved", true)).toBe("pending");
    expect(sharedGiftStatus("bought", true)).toBe("bought");
    expect(sharedGiftStatus("reserved", false)).toBe("reserved");
    expect(sharedGiftStatus(undefined, false)).toBe("");
  });
});
