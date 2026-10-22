import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import * as api from "./api";
import { useSession } from "./session";
import type { Child, Folder, Link, Tribe } from "./types";

// Données de l'utilisateur connecté et de sa tribu (idées, dossiers, enfants),
// chargées une fois et partagées par tous les écrans. Les mutations mettent
// le cache à jour localement pour éviter de tout recharger.

interface MyData {
  links: Link[];
  folders: Folder[];
  children: Child[];
  /** Tribu de l'utilisateur (nom affiché, membres). */
  tribe?: Tribe;
  /**
   * Comptes dont l'utilisateur voit et modifie le contenu : lui-même et les
   * membres de sa tribu qui ont confirmé le partage.
   */
  peers: ReadonlySet<string>;
  /** Le contenu de ownerId appartient à l'utilisateur ou à sa tribu. */
  isTribePeer: (ownerId: string | undefined) => boolean;
  /** Après une modification de la tribu : idées, dossiers et enfants changent aussi. */
  setTribe: (t: Tribe) => void;
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
  tribe?: Tribe;
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
    Promise.all([
      api.links.list(),
      api.folders.list(),
      api.children.list().catch(() => []),
      api.tribe.get().catch(() => undefined),
    ])
      .then(([links, folders, children, tribe]) => alive && setState({ key, links, folders, children, tribe }))
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

  const peers = useMemo(() => {
    const ids = new Set<string>(userId ? [userId] : []);
    for (const m of current.tribe?.members ?? []) if (m.sharing) ids.add(m.userId);
    return ids;
  }, [current.tribe, userId]);
  const isTribePeer = useCallback((ownerId: string | undefined) => !!ownerId && peers.has(ownerId), [peers]);
  const setTribe = useCallback(
    (t: Tribe) => {
      setState((s) => ({ ...s, tribe: t }));
      reload();
    },
    [reload],
  );

  const value = useMemo<MyData>(
    () => ({
      links: current.links,
      folders: current.folders,
      children: current.children,
      tribe: current.tribe,
      peers,
      isTribePeer,
      setTribe,
      loading,
      error: state.key === key ? current.error : undefined,
      reload, upsertLink, removeLink, upsertFolder, removeFolder, reloadChildren,
    }),
    [current, peers, isTribePeer, setTribe, loading, state.key, key, reload, upsertLink, removeLink, upsertFolder, removeFolder, reloadChildren],
  );
  return <DataContext.Provider value={value}>{node}</DataContext.Provider>;
}

export function useMyData(): MyData {
  const ctx = useContext(DataContext);
  if (!ctx) throw new Error("useMyData must be used inside DataProvider");
  return ctx;
}
