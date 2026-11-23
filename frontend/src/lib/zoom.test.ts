import { describe, expect, it } from "vitest";
import { clampPan, clampScale, toggleScale } from "./zoom";

// Mêmes cas que ZoomTest (Android)
describe("zoom", () => {
  it("reste entre l'image entière et 4 fois", () => {
    expect(clampScale(0.5)).toBe(1);
    expect(clampScale(2)).toBe(2);
    expect(clampScale(9)).toBe(4);
  });

  it("double-toucher : agrandit, puis revient à l'image entière", () => {
    expect(toggleScale(1)).toBe(2.5);
    expect(toggleScale(2.5)).toBe(1);
    expect(toggleScale(1.5)).toBe(1);
  });

  it("ne déplace pas au-delà des bords", () => {
    expect(clampPan(50, -50, 1, 400, 800)).toEqual({ x: 0, y: 0 });
    expect(clampPan(50, -50, 2, 400, 800)).toEqual({ x: 50, y: -50 });
    expect(clampPan(500, -900, 2, 400, 800)).toEqual({ x: 200, y: -400 });
  });
});
