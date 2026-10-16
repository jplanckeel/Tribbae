import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import * as api from "./api";
import { useSession } from "./session";
import type { Child, Folder, Link } from "./types";

// Données personnelles de l'utilisateur connecté (idées, dossiers, enfants),
// chargées une fois et partagées par tous les écrans. Les mutations mettent
// le cache à jour localement pour éviter de tout recharger.

interface MyData {
  links: Link[];
  folders: Folder[];
  children: Child[];
  loading: boolean;
  error?: Error;
  reload: () => void;
  upsertLink: (l: Link) => void;
  removeLink: (id: string) => void;
  upsertFolder: (f: Folder) => void;
  removeFolder: (id: string) => void;
  reloadChildren: () => Promise<void>;
}

const DataContext = createContext<MyData | null>(null);

const upsert = <T extends { id: string }>(list: T[], item: T) =>
  list.some((x) => x.id === item.id) ? list.map((x) => (x.id === item.id ? { ...x, ...item } : x)) : [item, ...list];

interface Loaded {
  /** « userId#tick » de la requête qui a produit ces données. */
  key: string;
  links: Link[];
  folders: Folder[];
  children: Child[];
  error?: Error;
}

const EMPTY: Loaded = { key: "", links: [], folders: [], children: [] };

export function DataProvider({ children: node }: { children: ReactNode }) {
  const session = useSession();
  const userId = session?.userId ?? "";
  const [tick, setTick] = useState(0);
  const [state, setState] = useState<Loaded>(EMPTY);
  const key = userId ? `${userId}#${tick}` : "";

  useEffect(() => {
    if (!key) return;
    let alive = true;
    Promise.all([api.links.list(), api.folders.list(), api.children.list().catch(() => [])])
      .then(([links, folders, children]) => alive && setState({ key, links, folders, children }))
      .catch((error: Error) => alive && setState((s) => ({ ...s, key, error })));
    return () => {
      alive = false;
    };
  }, [key]);

  // Les données d'un autre compte (déconnexion, changement d'utilisateur) ne sont jamais exposées ;
  // pendant un rechargement on garde l'affichage précédent du même compte.
  const mine = userId !== "" && state.key.startsWith(`${userId}#`);
  const current = mine ? state : EMPTY;
  const loading = userId !== "" && state.key !== key;

  const reload = useCallback(() => setTick((t) => t + 1), []);
  const upsertLink = useCallback((l: Link) => setState((s) => ({ ...s, links: upsert(s.links, l) })), []);
  const removeLink = useCallback((id: string) => setState((s) => ({ ...s, links: s.links.filter((l) => l.id !== id) })), []);
  const upsertFolder = useCallback((f: Folder) => setState((s) => ({ ...s, folders: upsert(s.folders, f) })), []);
  const removeFolder = useCallback(
    (id: string) =>
      setState((s) => ({
        ...s,
        folders: s.folders.filter((f) => f.id !== id),
        // Le backend conserve les idées du dossier supprimé : elles deviennent « non rangées »
        links: s.links.map((l) => (l.folderId === id ? { ...l, folderId: undefined } : l)),
      })),
    [],
  );
  const reloadChildren = useCallback(async () => {
    const children = await api.children.list();
    setState((s) => ({ ...s, children }));
  }, []);

  const value = useMemo<MyData>(
    () => ({
      links: current.links,
      folders: current.folders,
      children: current.children,
      loading,
      error: state.key === key ? current.error : undefined,
      reload, upsertLink, removeLink, upsertFolder, removeFolder, reloadChildren,
    }),
    [current, loading, state.key, key, reload, upsertLink, removeLink, upsertFolder, removeFolder, reloadChildren],
  );
  return <DataContext.Provider value={value}>{node}</DataContext.Provider>;
}

export function useMyData(): MyData {
  const ctx = useContext(DataContext);
  if (!ctx) throw new Error("useMyData must be used inside DataProvider");
  return ctx;
}
