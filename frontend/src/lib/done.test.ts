import { describe, expect, it } from "vitest";
import {
  awaitingConfirmation, awaitingLabel, celebrationTitle, doneMeta, dayChoices, dayOf, doneCounts, doneDay, doneDimmed, doneLabel, doneStamp,
  doneToast, doneWords, familiesLabel, folderProgress, formatDoneDay, inGiftList, matchesDoneFilter, memoryLine, progressHint,
  progressLabel, promptKicker, promptQuestion, sortTodoFirst, starWord,
} from "./done";
import type { CategoryKey, Link } from "./types";

// Mêmes cas que DoneTest (Android), en Europe/Paris.
const at = (y: number, m: number, d: number, h = 12) => new Date(y, m - 1, d, h).getTime();
const NOW = at(2026, 11, 11, 15); // mercredi 11 novembre 2026

const link = (id: string, cat: string, extra: Partial<Link> = {}): Link =>
  ({ id, ownerId: "a", title: id, category: `LINK_CATEGORY_${cat}` as CategoryKey, ...extra });
const done = (id: string, cat: string, day: string, count = 1, extra: Partial<Link> = {}) =>
  link(id, cat, { doneCount: count, lastDoneAt: `${day}T12:00:00Z`, ...extra });

describe("c'est fait", () => {
  it("donne le bouton et l'état de chaque catégorie", () => {
    expect(doneWords("LINK_CATEGORY_ACTIVITE").verb).toBe("On l'a fait !");
    expect(doneWords("LINK_CATEGORY_EVENEMENT").verb).toBe("On y est allés !");
    expect(doneWords("LINK_CATEGORY_RECETTE").verb).toBe("On l'a cuisinée");
    expect(doneWords("LINK_CATEGORY_LIVRE").verb).toBe("On l'a lu");
    expect(doneWords("LINK_CATEGORY_CADEAU").verb).toBe("Acheté");
    expect(doneWords("LINK_CATEGORY_IDEE").verb).toBe("C'est fait");
    expect(doneWords("LINK_CATEGORY_DECORATION").verb).toBe("C'est fait");
    expect(doneWords("LINK_CATEGORY_EVENEMENT").again).toBe("On y est retournés");

    expect(doneLabel(done("1", "ACTIVITE", "2026-10-26"), NOW)).toBe("Fait le 26 oct.");
    expect(doneLabel(done("2", "EVENEMENT", "2026-11-08"), NOW)).toBe("Fait le 8 nov.");
    expect(doneLabel(done("3", "RECETTE", "2026-11-03"), NOW)).toBe("Cuisinée le 3 nov.");
    expect(doneLabel(done("3", "RECETTE", "2026-11-03", 3), NOW)).toBe("Cuisinée 3 fois");
    expect(doneLabel(done("4", "LIVRE", "2026-10-12"), NOW)).toBe("Lu en octobre");
    expect(doneLabel(done("5", "CADEAU", "2026-11-02"), NOW)).toBe("Acheté le 2 nov.");
    expect(doneLabel(done("6", "IDEE", "2026-11-05"), NOW)).toBe("Fait le 5 nov.");
    expect(doneLabel(done("7", "DECORATION", "2026-11-01", 2), NOW)).toBe("Fait le 1er nov.");
    expect(doneLabel(link("8", "ACTIVITE"), NOW)).toBe("");
  });

  it("ajoute l'année quand ce n'est pas l'année en cours", () => {
    expect(formatDoneDay("2026-10-26", NOW)).toBe("26 oct.");
    expect(formatDoneDay("2025-12-01", NOW)).toBe("1er déc. 2025");
    expect(doneLabel(done("1", "LIVRE", "2025-08-20"), NOW)).toBe("Lu en août 2025");
    expect(doneDay(done("1", "LIVRE", "2025-08-20"))).toBe("2025-08-20");
    expect(doneStamp(done("1", "RECETTE", "2026-11-03", 3))).toBe("Cuisinée ×3");
    expect(doneStamp(done("1", "ACTIVITE", "2026-11-03"))).toBe("Fait");
  });

  it("demande « Fait ? » le lendemain d'une sortie datée", () => {
    const past = link("1", "EVENEMENT", { title: "Fête de la courge", eventDate: at(2026, 11, 7, 14), location: "Ferme du Mas" });
    expect(awaitingConfirmation(past, NOW)).toBe(true);
    expect(awaitingLabel(past, NOW)).toBe("Samedi · c'était bien ?");
    expect(promptKicker(past, NOW)).toBe("Samedi · Ferme du Mas");
    expect(promptQuestion(past)).toBe("Vous êtes allés à « Fête de la courge » ?");
    expect(promptQuestion(link("Cabane", "ACTIVITE"))).toBe("Vous avez fait « Cabane » ?");
    expect(awaitingLabel(link("2", "ACTIVITE", { eventDate: at(2026, 11, 10, 9) }), NOW)).toBe("Hier · c'était bien ?");
    expect(awaitingLabel(link("3", "ACTIVITE", { eventDate: at(2026, 10, 3, 9) }), NOW)).toBe("3 oct. · c'était bien ?");

    // Le jour même, à venir, déjà faite, « Pas cette fois », sans date, autre catégorie
    expect(awaitingConfirmation(link("4", "EVENEMENT", { eventDate: at(2026, 11, 11, 9) }), NOW)).toBe(false);
    expect(awaitingConfirmation(link("5", "EVENEMENT", { eventDate: at(2026, 11, 22, 9) }), NOW)).toBe(false);
    expect(awaitingConfirmation({ ...past, doneCount: 1 }, NOW)).toBe(false);
    expect(awaitingConfirmation({ ...past, donePromptDismissed: true }, NOW)).toBe(false);
    expect(awaitingConfirmation(link("6", "ACTIVITE"), NOW)).toBe(false);
    expect(awaitingConfirmation(link("7", "RECETTE", { eventDate: at(2026, 11, 7, 14) }), NOW)).toBe(false);
  });

  it("ne pose la question qu'à qui peut répondre", () => {
    const past = link("1", "EVENEMENT", { eventDate: at(2026, 11, 7, 14) });
    expect(doneMeta(past, NOW, true)?.text).toBe("Samedi · c'était bien ?");
    // Lecteur du cercle : il voit l'état fait, pas la question
    expect(doneMeta(past, NOW, true, false)).toBeNull();
    expect(doneMeta(done("2", "ACTIVITE", "2026-10-26"), NOW, true, false)?.text).toBe("Fait le 26 oct.");
    // Hors de la tribu et du dossier : rien
    expect(doneMeta(done("2", "ACTIVITE", "2026-10-26"), NOW, false)).toBeNull();
  });

  it("mesure l'avancement d'un dossier", () => {
    const links = [
      done("1", "ACTIVITE", "2026-10-26"), done("2", "RECETTE", "2026-11-08", 3), link("3", "EVENEMENT"),
      link("4", "LIVRE"), link("5", "CADEAU"), done("6", "IDEE", "2026-11-05"),
    ];
    const p = folderProgress(links);
    expect(p).toEqual({ done: 2, total: 4 });
    expect(progressLabel(p!)).toBe("2 sur 4 faites");
    expect(progressHint(p!)).toBe("Encore 2 à faire");
    expect(progressHint({ done: 3, total: 4 })).toBe("Plus qu'une à faire");
    expect(progressHint({ done: 4, total: 4 })).toBe("Bravo, tout est fait !");
    // Moins de 3 idées comptées, ou liste de cadeaux : ni anneau ni barre
    expect(folderProgress(links.slice(0, 2))).toBeNull();
    expect(folderProgress([link("a", "CADEAU"), link("b", "IDEE"), link("c", "DECORATION")])).toBeNull();
    expect(folderProgress(links, true)).toBeNull();
    expect(doneToast(p)).toBe("2 sur 4 faites");
    expect(doneToast(null)).toBe("C'est noté");
  });

  it("filtre et trie « À faire d'abord »", () => {
    const links = [
      done("faite-nov", "ACTIVITE", "2026-11-08", 1, { createdAt: "2026-09-01T10:00:00Z" }),
      link("recente", "RECETTE", { createdAt: "2026-11-01T10:00:00Z" }),
      link("a-venir", "EVENEMENT", { eventDate: at(2026, 11, 22), createdAt: "2026-08-01T10:00:00Z" }),
      done("faite-oct", "LIVRE", "2026-10-12", 1, { createdAt: "2026-09-02T10:00:00Z" }),
      link("ancienne", "IDEE", { createdAt: "2026-06-01T10:00:00Z" }),
      link("passee", "EVENEMENT", { eventDate: at(2026, 11, 7), createdAt: "2026-07-01T10:00:00Z" }),
    ];
    expect(sortTodoFirst(links, NOW).map((l) => l.id)).toEqual(["passee", "a-venir", "recente", "ancienne", "faite-oct", "faite-nov"]);
    expect(doneCounts(links)).toEqual({ all: 6, todo: 4, done: 2 });
    expect(links.filter((l) => matchesDoneFilter(l, "done")).map((l) => l.id)).toEqual(["faite-nov", "faite-oct"]);
    expect(links.filter((l) => matchesDoneFilter(l, "todo"))).toHaveLength(4);
    expect(links.filter((l) => matchesDoneFilter(l, "all"))).toHaveLength(6);
  });

  it("garde la surprise des listes de cadeaux et n'estompe que les cadeaux", () => {
    expect(inGiftList(link("1", "CADEAU"), { giftReservations: true })).toBe(true);
    expect(inGiftList(link("1", "CADEAU"), { giftReservations: false })).toBe(false);
    expect(inGiftList(link("1", "CADEAU"), undefined)).toBe(false);
    expect(inGiftList(link("2", "LIVRE"), { giftReservations: true })).toBe(false);
    expect(doneDimmed(done("1", "CADEAU", "2026-11-02"))).toBe(true);
    expect(doneDimmed(link("1", "CADEAU"))).toBe(false);
    expect(doneDimmed(done("2", "ACTIVITE", "2026-11-02"))).toBe(false);
  });

  it("écrit le souvenir, la célébration et le compteur public", () => {
    expect(memoryLine(["Léo", "Emma", "Camille"], "2026-10-26", 1, NOW)).toBe("Avec Léo, Emma et Camille · 26 oct.");
    expect(memoryLine(["Léo"], "2026-11-08", 3, NOW)).toBe("Avec Léo · 3 fois, dernière le 8 nov.");
    expect(memoryLine([], "2026-10-26", 1, NOW)).toBe("26 oct.");
    expect(starWord(0)).toBe("Touchez une étoile");
    expect(starWord(5)).toBe("Génial !");
    expect(celebrationTitle("LINK_CATEGORY_RECETTE", false)).toBe("Miam, c'est noté !");
    expect(celebrationTitle("LINK_CATEGORY_ACTIVITE", false)).toBe("C'est fait !");
    expect(celebrationTitle("LINK_CATEGORY_LIVRE", true)).toBe("On l'a relu !");
    expect(familiesLabel(120)).toBe("120 familles l'ont fait");
    expect(familiesLabel(0)).toBe("");
    expect(familiesLabel(undefined)).toBe("");
  });

  it("propose aujourd'hui et hier", () => {
    expect(dayOf(NOW)).toBe("2026-11-11");
    expect(dayChoices(NOW).map((c) => [c.label, c.day])).toEqual([["Aujourd'hui", "2026-11-11"], ["Hier", "2026-11-10"]]);
    // Passage à l'heure d'hiver (25 octobre 2026) : hier reste la veille
    expect(dayChoices(at(2026, 10, 26, 0)).map((c) => c.day)).toEqual(["2026-10-26", "2026-10-25"]);
  });
});
