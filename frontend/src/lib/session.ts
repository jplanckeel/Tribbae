import { useSyncExternalStore } from "react";

// Session stockée dans localStorage (mêmes clés que l'ancienne version, pour ne
// pas déconnecter les utilisateurs lors de la mise à jour).
export interface Session {
  token: string;
  userId: string;
  displayName: string;
  isAdmin: boolean;
}

const KEYS = ["token", "userId", "displayName", "isAdmin"] as const;
const listeners = new Set<() => void>();
let cached: Session | null | undefined;

function read(): Session | null {
  const token = localStorage.getItem("token");
  if (!token) return null;
  return {
    token,
    userId: localStorage.getItem("userId") ?? "",
    displayName: localStorage.getItem("displayName") ?? "",
    isAdmin: localStorage.getItem("isAdmin") === "true",
  };
}

function emit() {
  cached = undefined;
  listeners.forEach((l) => l());
}

export function getSession(): Session | null {
  if (cached === undefined) cached = read();
  return cached;
}

export function setSession(s: Session) {
  localStorage.setItem("token", s.token);
  localStorage.setItem("userId", s.userId);
  localStorage.setItem("displayName", s.displayName);
  localStorage.setItem("isAdmin", String(s.isAdmin));
  emit();
}

export function clearSession() {
  KEYS.forEach((k) => localStorage.removeItem(k));
  emit();
}

/** Âge d'un jeton après lequel on le renouvelle (au plus une fois par jour). */
export const RENEW_AFTER_MS = 24 * 60 * 60 * 1000;

/**
 * Date d'émission (ms) lue dans le jeton (champ `iat` du JWT, sans vérifier la
 * signature : seul le serveur en juge). Null si illisible. Même lecture côté
 * Android : data/TokenRenewal.kt.
 */
export function tokenIssuedAt(token: string): number | null {
  try {
    const part = token.split(".")[1];
    if (!part) return null;
    const payload = JSON.parse(atob(part.replace(/-/g, "+").replace(/_/g, "/").padEnd(Math.ceil(part.length / 4) * 4, "=")));
    return typeof payload.iat === "number" ? payload.iat * 1000 : null;
  } catch {
    return null;
  }
}

/** Le jeton a plus d'un jour (ou une date illisible) : à renouveler. */
export function shouldRenew(token: string, now = Date.now()): boolean {
  const iat = tokenIssuedAt(token);
  return iat === null || now - iat > RENEW_AFTER_MS;
}

function subscribe(cb: () => void) {
  listeners.add(cb);
  // Synchronise les onglets entre eux (déconnexion dans un autre onglet)
  const onStorage = (e: StorageEvent) => {
    if (e.key === null || (KEYS as readonly string[]).includes(e.key)) emit();
  };
  window.addEventListener("storage", onStorage);
  return () => {
    listeners.delete(cb);
    window.removeEventListener("storage", onStorage);
  };
}

export function useSession(): Session | null {
  return useSyncExternalStore(subscribe, getSession, () => null);
}
