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
