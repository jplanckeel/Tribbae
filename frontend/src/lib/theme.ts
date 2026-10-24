import { useSyncExternalStore } from "react";

// Apparence (réglage de Ma tribu) : « jour », « soir » ou « auto » (comme
// l'appareil). Retenue sur cet appareil ; public/theme.js l'applique avant le
// premier affichage, ce module la tient à jour ensuite.

export type ThemePref = "jour" | "soir" | "auto";

const KEY = "theme";
const listeners = new Set<() => void>();
const media = typeof window !== "undefined" && window.matchMedia ? window.matchMedia("(prefers-color-scheme: dark)") : null;

function read(): ThemePref {
  try {
    const v = localStorage.getItem(KEY);
    return v === "jour" || v === "soir" ? v : "auto";
  } catch {
    return "auto";
  }
}

let current: ThemePref = read();

/** Le mode sombre est-il actif pour cette préférence ? */
export function isDark(pref: ThemePref = current): boolean {
  return pref === "soir" || (pref === "auto" && !!media?.matches);
}

function apply() {
  const dark = isDark();
  document.documentElement.dataset.theme = dark ? "dark" : "light";
  document.querySelector('meta[name="theme-color"]')?.setAttribute("content", dark ? "#1C1411" : "#FBF3E7");
}

export function setThemePref(pref: ThemePref) {
  current = pref;
  try {
    if (pref === "auto") localStorage.removeItem(KEY);
    else localStorage.setItem(KEY, pref);
  } catch {
    // stockage bloqué : la préférence vaut jusqu'à la fermeture de la page
  }
  apply();
  listeners.forEach((l) => l());
}

// En « auto », suivre l'appareil quand il change de mode
media?.addEventListener("change", () => {
  if (current === "auto") {
    apply();
    listeners.forEach((l) => l());
  }
});

export function useThemePref(): ThemePref {
  return useSyncExternalStore(
    (l) => {
      listeners.add(l);
      return () => listeners.delete(l);
    },
    () => current,
    () => "auto",
  );
}
