import { describe, expect, it } from "vitest";
import { AGE_BANDS, covers, formatAgeRange, hull, matchesAge, parseAgeRange, snapAge } from "./age";

// Mêmes cas qu'Android (composeApp/.../data/AgeRangesTest.kt) : lecture puis écriture.
describe("tranches d'âge", () => {
  const roundTrip = (text: string) => {
    const r = parseAgeRange(text);
    return r && formatAgeRange(r);
  };

  it.each([
    ["3–6 ans", "3–6 ans"],
    ["3-10 ans", "3–10 ans"],
    ["18 mois – 3 ans", "18 mois – 3 ans"],
    ["6–18 mois", "6–18 mois"],
    ["18 mois", "18 mois"],
    ["dès 4 ans", "4 ans et +"],
    ["10+", "10 ans et +"],
    ["Tous âges", "Tous âges"],
    ["8 ans", "8 ans"],
    ["1 an", "1 an"],
    ["0–3 ans", "0–3 ans"],
  ])("relit « %s » et l'écrit « %s »", (input, expected) => {
    expect(roundTrip(input)).toBe(expected);
  });

  it("ignore un texte sans nombre", () => {
    expect(parseAgeRange("trois ans")).toBeNull();
    expect(parseAgeRange(undefined)).toBeNull();
    expect(parseAgeRange("18 mois – 3 ans")).toEqual([1.5, 3]);
  });

  it("« Tous âges » correspond à chaque tranche de l'Explorer", () => {
    for (const b of AGE_BANDS) expect(matchesAge("Tous âges", b.range[0], b.range[1])).toBe(true);
    expect(matchesAge("6–18 mois", 3, 6)).toBe(false);
    expect(matchesAge("3–6 ans", 6, 10)).toBe(true);
  });

  it("ramène l'âge d'un enfant au cran inférieur", () => {
    expect(snapAge(8.7)).toBe(8);
    expect(snapAge(1.9)).toBe(1.5);
    expect(snapAge(0.3)).toBe(0);
  });

  it("enveloppe et couverture", () => {
    expect(hull([3, 6], [6, 10])).toEqual([3, 10]);
    expect(covers([3, 10], [3, 6])).toBe(true);
    expect(covers([3, 6], [3, 10])).toBe(false);
  });
});
