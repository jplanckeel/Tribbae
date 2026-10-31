import { describe, expect, it } from "vitest";
import { bounds, clamp, dayOptions, daysInMonth, describe as describeDate, minuteOptions, monthOptions, partsOf, setPart, shortcuts, toMs, yearOptions } from "./dateWheel";

// Mêmes cas que DateWheelTest (Android). Fuseau Europe/Paris (TZ dans npm test).
const at = (y: number, m: number, d: number, h = 0, min = 0) => new Date(y, m - 1, d, h, min).getTime();
const NOW = at(2026, 10, 28, 15, 30); // mercredi 28 octobre 2026

describe("daysInMonth", () => {
  it("suit les mois et les années bissextiles", () => {
    expect([1, 2, 4, 12].map((m) => daysInMonth(2026, m))).toEqual([31, 28, 30, 31]);
    expect(daysInMonth(2024, 2)).toBe(29);
    expect(daysInMonth(2000, 2)).toBe(29);
    expect(daysInMonth(2100, 2)).toBe(28);
  });
});

describe("conversions", () => {
  it("relit un instant en heure locale", () => {
    expect(partsOf(at(2026, 3, 29, 2, 30))).toEqual({ year: 2026, month: 3, day: 29, hour: 3, minute: 30 }); // passage à l'heure d'été
    expect(partsOf(at(2026, 12, 31, 23, 45))).toEqual({ year: 2026, month: 12, day: 31, hour: 23, minute: 45 });
  });

  it("enregistre l'heure selon l'usage", () => {
    const p = { year: 2027, month: 3, day: 14, hour: 15, minute: 30 };
    expect(toMs(p, "event")).toBe(at(2027, 3, 14, 15, 30));
    expect(toMs(p, "key")).toBe(at(2027, 3, 14));
    expect(toMs(p, "anniv")).toBe(at(2027, 3, 14));
    expect(toMs(p, "birth")).toBe(at(2027, 3, 14, 12));
    expect(partsOf(toMs(p, "event"))).toEqual(p);
  });
});

describe("bounds", () => {
  it("naissance : au plus aujourd'hui, 30 ans en arrière", () => {
    const { min, max } = bounds("birth", NOW);
    expect([min.year, min.month, min.day]).toEqual([1996, 1, 1]);
    expect([max.year, max.month, max.day]).toEqual([2026, 10, 28]);
  });

  it("date clé : à partir d'aujourd'hui, sauf une date déjà passée", () => {
    expect(bounds("key", NOW).min).toMatchObject({ year: 2026, month: 10, day: 28 });
    expect(bounds("key", NOW, at(2025, 6, 1)).min).toMatchObject({ year: 2025, month: 6, day: 1 });
    expect(bounds("anniv", NOW).min.year).toBe(1926);
  });

  it("sortie : cinq ans en arrière, dix ans en avant", () => {
    const { min, max } = bounds("event", NOW);
    expect([min.year, max.year, max.month, max.day]).toEqual([2021, 2036, 12, 31]);
  });
});

describe("colonnes", () => {
  const { min, max } = bounds("birth", NOW);

  it("limite mois et jours aux bornes", () => {
    expect(yearOptions(min, max)).toHaveLength(31);
    expect(monthOptions(2026, min, max)).toEqual([1, 2, 3, 4, 5, 6, 7, 8, 9, 10]);
    expect(monthOptions(2025, min, max)).toHaveLength(12);
    expect(dayOptions(2026, 10, min, max)).toHaveLength(28);
    expect(dayOptions(2024, 2, min, max)).toHaveLength(29);
  });

  it("garde la minute enregistrée entre deux crans", () => {
    expect(minuteOptions(30)).toHaveLength(12);
    expect(minuteOptions(17)).toEqual([0, 5, 10, 15, 17, 20, 25, 30, 35, 40, 45, 50, 55]);
  });
});

describe("setPart", () => {
  const { min, max } = bounds("event", NOW);

  it("ramène le jour au dernier du mois", () => {
    const jan31 = { year: 2027, month: 1, day: 31, hour: 10, minute: 0 };
    expect(setPart(jan31, "month", 2, min, max)).toMatchObject({ month: 2, day: 28 });
    expect(setPart({ ...jan31, year: 2028 }, "month", 2, min, max)).toMatchObject({ month: 2, day: 29 });
    expect(setPart({ year: 2028, month: 2, day: 29, hour: 0, minute: 0 }, "year", 2027, min, max)).toMatchObject({ day: 28 });
  });

  it("reste entre les bornes", () => {
    const b = bounds("birth", NOW);
    expect(setPart({ year: 2026, month: 9, day: 30, hour: 0, minute: 0 }, "month", 10, b.min, b.max)).toMatchObject({ month: 10, day: 28 });
    expect(clamp({ year: 2026, month: 12, day: 25, hour: 0, minute: 0 }, b.min, b.max)).toMatchObject({ year: 2026, month: 10, day: 28 });
  });
});

describe("shortcuts", () => {
  const dates = (kind: "event" | "key", now: number) => shortcuts(kind, now).map((s) => `${s.label} ${s.date.day}/${s.date.month}`);

  it("propose aujourd'hui, demain et le week-end pour une sortie", () => {
    expect(dates("event", NOW)).toEqual(["Aujourd'hui 28/10", "Demain 29/10", "Ce week-end 31/10"]);
    // Un même jour une seule fois : samedi, dimanche, vendredi (le week-end est demain)
    expect(dates("event", at(2026, 10, 31))).toEqual(["Aujourd'hui 31/10", "Demain 1/11"]);
    expect(dates("event", at(2026, 11, 1))).toEqual(["Aujourd'hui 1/11", "Demain 2/11"]);
    expect(dates("event", at(2026, 10, 30))).toEqual(["Aujourd'hui 30/10", "Demain 31/10"]);
    expect(dates("event", at(2026, 12, 31))[1]).toBe("Demain 1/1");
  });

  it("propose le week-end et le mois suivant pour une date clé", () => {
    expect(dates("key", NOW)).toEqual(["Ce week-end 31/10", "Dans un mois 28/11"]);
    expect(dates("key", at(2027, 1, 31))[1]).toBe("Dans un mois 28/2");
    expect(dates("key", at(2026, 12, 15))[1]).toBe("Dans un mois 15/1");
  });

  it("n'en propose pas pour une naissance", () => {
    expect(shortcuts("birth", NOW)).toEqual([]);
  });
});

describe("describe", () => {
  it("écrit la date en toutes lettres", () => {
    expect(describeDate({ year: 2027, month: 3, day: 14, hour: 15, minute: 5 }, true)).toBe("14 mars 2027 à 15 h 05");
    expect(describeDate({ year: 2027, month: 1, day: 1, hour: 0, minute: 0 }, false)).toBe("1er janvier 2027");
  });
});
