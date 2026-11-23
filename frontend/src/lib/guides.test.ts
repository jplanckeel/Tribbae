import { describe, expect, it } from "vitest";
import guides from "../../public/guides.json";
import { GUIDE_LINKS } from "./guides";

describe("guides", () => {
  it("les routes et le pied de page couvrent tous les guides de guides.json, dans l'ordre", () => {
    expect(GUIDE_LINKS.map((g) => g.slug)).toEqual(guides.map((g) => g.slug));
  });

  it("chaque guide a un titre et une description de taille utile aux moteurs", () => {
    for (const g of guides) {
      expect([...g.title].length, g.slug).toBeLessThanOrEqual(65);
      expect([...g.description].length, g.slug).toBeLessThanOrEqual(160);
      expect(g.slug).toMatch(/^[a-z0-9]+(?:-[a-z0-9]+)*$/);
      for (const r of g.related ?? []) expect(guides.some((x) => x.slug === r), `${g.slug} → ${r}`).toBe(true);
    }
  });
});
