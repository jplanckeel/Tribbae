import { describe, expect, it } from "vitest";
import { limitTo, withinLimits } from "./preview";
import type { LinkPreview } from "./types";

// Mêmes cas qu'Android (composeApp/.../data/LinkPreviewLimitsTest.kt).
describe("limites des aperçus", () => {
  it("coupe à la limite sans casser un émoji", () => {
    expect(limitTo("court", 300)).toBe("court");
    expect(limitTo("x".repeat(400), 300)).toHaveLength(300);
    expect(limitTo("a".repeat(299) + "😀b", 300)).toBe("a".repeat(299));
  });

  it("ramène les textes aux limites des champs", () => {
    const p = withinLimits({
      title: "t".repeat(1000),
      description: "d".repeat(6000),
      price: "p".repeat(300),
      ingredients: ["i".repeat(250)],
    } as LinkPreview);
    expect(p.title).toHaveLength(300);
    expect(p.description).toHaveLength(5000);
    expect(p.price).toHaveLength(200);
    expect(p.ingredients).toEqual(["i".repeat(200)]);
  });
});
