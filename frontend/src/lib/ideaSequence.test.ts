import { describe, expect, it } from "vitest";
import { neighbors, swipeDirection } from "./ideaSequence";

// Mêmes cas que IdeaSequenceTest (Android)
describe("neighbors", () => {
  const seq = { ids: ["a", "b", "c"] };

  it("donne la précédente, la suivante et la position", () => {
    expect(neighbors(seq, "b")).toEqual({ prev: "a", next: "c", position: 2, total: 3 });
    expect(neighbors(seq, "a")).toEqual({ prev: undefined, next: "b", position: 1, total: 3 });
    expect(neighbors(seq, "c")).toEqual({ prev: "b", next: undefined, position: 3, total: 3 });
  });

  it("saute les idées qui n'existent plus", () => {
    expect(neighbors(seq, "a", (id) => id !== "b")).toEqual({ prev: undefined, next: "c", position: 1, total: 2 });
  });

  it("rien hors de la suite, ou avec une seule idée", () => {
    expect(neighbors(seq, "z")).toBeUndefined();
    expect(neighbors({ ids: ["a"] }, "a")).toBeUndefined();
    expect(neighbors(undefined, "a")).toBeUndefined();
    expect(neighbors(seq, "a", () => false)).toBeUndefined();
  });

  it("ignore les doublons", () => {
    expect(neighbors({ ids: ["a", "b", "a"] }, "b")).toEqual({ prev: "a", next: undefined, position: 2, total: 2 });
  });
});

describe("swipeDirection", () => {
  it.each([
    [-120, 10, 200, -1],
    [120, -20, 200, 1],
    [-40, 0, 200, 0],
    [-100, 90, 200, 0],
    [-150, 0, 900, 0],
  ])("dx=%i dy=%i en %i ms → %i", (dx, dy, ms, want) => {
    expect(swipeDirection(dx, dy, ms)).toBe(want);
  });
});
