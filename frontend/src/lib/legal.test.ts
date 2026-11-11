import { describe, expect, it } from "vitest";
import { isPlaceholder, LEGAL } from "./legal";

describe("identité légale", () => {
  it("repère les champs encore à compléter", () => {
    expect(isPlaceholder("[à compléter : nom]")).toBe(true);
    expect(isPlaceholder("Tribbae SAS")).toBe(false);
    expect(LEGAL.contact).toBe("contact@tribbae.fr");
  });
});
