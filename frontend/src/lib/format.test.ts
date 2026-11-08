import { describe, expect, it } from "vitest";
import { eventDateMs, formatTime, hostname, matchesQuery, priceLabel, priceValue, safeHref, safeNext, mapsUrl } from "./format";
import type { Link } from "./types";

describe("liens sûrs", () => {
  it.each([
    ["https://exemple.fr/a", "https://exemple.fr/a"],
    [" http://exemple.fr ", "http://exemple.fr/"],
    ["javascript:alert(1)", undefined],
    ["data:text/html,<script>", undefined],
    ["file:///sdcard/photo.jpg", undefined],
    ["pas une adresse", undefined],
    [undefined, undefined],
  ])("safeHref(%s)", (url, expected) => {
    expect(safeHref(url)).toBe(expected);
  });

  it("nom de site sans www", () => {
    expect(hostname("https://www.exemple.fr/page")).toBe("exemple.fr");
    expect(hostname("javascript:alert(1)")).toBe("");
  });

  it.each([
    ["/tribu", "/tribu"],
    ["/dossiers/1?vue=liste", "/dossiers/1?vue=liste"],
    ["//evil.example", "/"],
    ["/\\evil.example", "/"],
    ["https://evil.example", "/"],
    [null, "/"],
  ])("safeNext(%s) n'ouvre pas de redirection externe", (raw, expected) => {
    expect(safeNext(raw)).toBe(expected);
  });
});

describe("prix", () => {
  it.each([
    ["0", "Gratuit"],
    ["0 €", "Gratuit"],
    ["0,00 euros", "Gratuit"],
    ["10 €", "10 €"],
    [undefined, ""],
  ])("priceLabel(%s)", (p, expected) => {
    expect(priceLabel(p)).toBe(expected);
  });

  it.each([
    ["6 € / enfant", 6],
    ["dès 12,50 €", 12.5],
    ["Gratuit", 0],
    ["entrée offerte", 0],
    ["sur devis", undefined],
    ["", undefined],
  ])("priceValue(%s)", (p, expected) => {
    expect(priceValue(p)).toBe(expected);
  });
});

it("date d'événement en millisecondes, même enregistrée en secondes", () => {
  expect(eventDateMs("1793000000000")).toBe(1793000000000);
  expect(eventDateMs(1793000000)).toBe(1793000000000);
  expect(eventDateMs("0")).toBeNull();
  expect(eventDateMs(undefined)).toBeNull();
});

it("heure affichée seulement si elle est fixée", () => {
  expect(formatTime(new Date(2026, 9, 24, 0, 0).getTime())).toBe("");
  expect(formatTime(new Date(2026, 9, 24, 14, 0).getTime())).toBe("14h");
  expect(formatTime(new Date(2026, 9, 24, 9, 5).getTime())).toBe("9h05");
});

it("recherche dans le titre, la description, le lieu et les tags", () => {
  const l = { title: "Balade", description: "En forêt", location: "Lyon", tags: ["Automne"] } as Link;
  expect(matchesQuery(l, "")).toBe(true);
  expect(matchesQuery(l, "forêt")).toBe(true);
  expect(matchesQuery(l, "lyon")).toBe(true);
  expect(matchesQuery(l, "automne")).toBe(true);
  expect(matchesQuery(l, "plage")).toBe(false);
});

// Mêmes cas que data/MapsTest.kt côté Android
describe("adresse dans Google Maps", () => {
  it("encode l'adresse", () => {
    expect(mapsUrl("  Parc de la Tête d'Or, Lyon ")).toBe("https://www.google.com/maps/search/?api=1&query=Parc%20de%20la%20T%C3%AAte%20d'Or%2C%20Lyon");
    expect(mapsUrl("12 rue & place")).toBe("https://www.google.com/maps/search/?api=1&query=12%20rue%20%26%20place");
    expect(mapsUrl("Gare (Nord) ! ~")).toBe("https://www.google.com/maps/search/?api=1&query=Gare%20(Nord)%20!%20~");
  });
});
