import { useSyncExternalStore } from "react";

/** Vrai si la media query correspond (mise à jour au redimensionnement). */
export function useMediaQuery(query: string): boolean {
  return useSyncExternalStore(
    (onChange) => {
      const mql = window.matchMedia(query);
      mql.addEventListener("change", onChange);
      return () => mql.removeEventListener("change", onChange);
    },
    () => window.matchMedia(query).matches,
    () => false,
  );
}

/** Mise en page bureau : mêmes points de rupture que le `md:` de Tailwind. */
export const useDesktop = () => useMediaQuery("(min-width: 768px)");
