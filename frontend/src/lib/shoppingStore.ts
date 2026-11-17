// « Les courses » sur le web : la liste de la tribu, gardée en mémoire et
// partagée par les écrans qui la montrent (page, carte de Mes idées, fiche
// recette). Chaque changement s'affiche tout de suite (applyOp) puis part au
// serveur, dans l'ordre ; la réponse du serveur remplace la liste. La page la
// relit à l'ouverture et toutes les 10 s quand elle est affichée.

import { useEffect, useSyncExternalStore } from "react";
import { ApiError, shopping } from "./api";
import { getSession } from "./session";
import { applyOp, emptyList, type RecipeInfo, type SharedList, type ShoppingOp } from "./shopping";

interface Pending {
  op: ShoppingOp;
  /** Contenu de la recette ajoutée au menu, pour l'afficher avant la réponse. */
  recipe?: RecipeInfo;
  at: number;
}

export interface ShoppingSnapshot {
  /** Liste affichée : celle du serveur, plus les changements pas encore confirmés. */
  list: SharedList;
  /** La liste a été lue au moins une fois. */
  loaded: boolean;
  /** Le dernier envoi n'a pas pu partir : les changements attendent le réseau. */
  offline: boolean;
  /** Changements pas encore confirmés par le serveur. */
  pending: number;
  /** Message d'un changement refusé (affiché une fois). */
  error?: string;
}

const now = () => Math.floor(Date.now() / 1000);

let owner: string | undefined;
let server: SharedList = emptyList();
let loaded = false;
let offline = false;
let error: string | undefined;
let queue: Pending[] = [];
let sending = false;
let snapshot: ShoppingSnapshot = { list: server, loaded, offline, pending: 0 };
const listeners = new Set<() => void>();

function publish() {
  const userId = owner ?? "";
  const list = queue.reduce((l, p) => applyOp(l, p.op, userId, p.at, () => p.recipe), server);
  snapshot = { list, loaded, offline, pending: queue.length, error };
  for (const l of listeners) l();
}

/** Une autre session (déconnexion, autre compte) : on repart d'une liste vide. */
function ensureOwner() {
  const userId = getSession()?.userId;
  if (userId === owner) return;
  owner = userId;
  server = emptyList();
  loaded = false;
  offline = false;
  error = undefined;
  queue = [];
  publish();
}

async function flush() {
  if (sending || !queue.length) return;
  sending = true;
  const batch = queue.slice(0, 100);
  try {
    server = await shopping.apply(batch.map((p) => p.op));
    queue = queue.slice(batch.length);
    loaded = true;
    offline = false;
  } catch (e) {
    if (e instanceof ApiError && e.status === 0) {
      // Pas de réseau : les changements attendent la prochaine lecture
      offline = true;
    } else {
      // Refusé (recette plus visible, limite atteinte…) : on revient à la liste du serveur
      queue = queue.slice(batch.length);
      error = e instanceof Error ? e.message : "Le changement n'a pas pu être enregistré.";
    }
  } finally {
    sending = false;
    publish();
  }
  if (queue.length && !offline) void flush();
}

/** Relit la liste (rien n'est renvoyé si elle n'a pas changé). */
export async function refreshShopping(): Promise<void> {
  ensureOwner();
  if (!owner) return;
  if (queue.length) return flush();
  try {
    const fresh = await shopping.get(loaded ? server.version : 0);
    if (fresh) server = fresh;
    loaded = true;
    offline = false;
  } catch (e) {
    offline = e instanceof ApiError && e.status === 0;
    if (!offline && !loaded) error = e instanceof Error ? e.message : undefined;
  }
  publish();
}

/** Change la liste : affiché tout de suite, envoyé au serveur dans l'ordre. */
export function changeShopping(op: ShoppingOp, recipe?: RecipeInfo) {
  ensureOwner();
  queue = [...queue, { op, recipe, at: now() }];
  publish();
  void flush();
}

/** Le message d'erreur a été montré. */
export function clearShoppingError() {
  if (!error) return;
  error = undefined;
  publish();
}

function subscribe(listener: () => void) {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

/**
 * La liste de la tribu. [poll] : relue toutes les 10 s tant que la page est
 * visible (écran des courses) ; sinon lue une fois à l'affichage.
 */
export function useShopping({ poll = false }: { poll?: boolean } = {}): ShoppingSnapshot {
  const snap = useSyncExternalStore(subscribe, () => snapshot, () => snapshot);
  useEffect(() => {
    void refreshShopping();
    if (!poll) return;
    const tick = () => {
      if (document.visibilityState === "visible") void refreshShopping();
    };
    const id = setInterval(tick, 10_000);
    document.addEventListener("visibilitychange", tick);
    return () => {
      clearInterval(id);
      document.removeEventListener("visibilitychange", tick);
    };
  }, [poll]);
  return snap;
}

/** Identifiant d'un article libre choisi par l'appareil (rejouer l'ajout ne le double pas). */
export function newItemId(): string {
  const rand = typeof crypto !== "undefined" && "randomUUID" in crypto ? crypto.randomUUID().replace(/-/g, "") : Math.random().toString(36).slice(2);
  return `w${rand.slice(0, 20)}`;
}
