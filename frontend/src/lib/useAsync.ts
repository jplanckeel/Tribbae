import { useCallback, useEffect, useEffectEvent, useState } from "react";

interface AsyncState<T> {
  data: T | undefined;
  error: Error | undefined;
  loading: boolean;
  reload: () => void;
  setData: (updater: T | ((prev: T | undefined) => T)) => void;
}

interface Settled<T> {
  key: string;
  data?: T;
  error?: Error;
}

/**
 * Charge une ressource asynchrone. Le résultat est associé à la clé des
 * dépendances : une réponse arrivée après un changement est ignorée et
 * l'état « chargement » se déduit sans setState synchrone.
 */
export function useAsync<T>(fn: () => Promise<T>, deps: unknown[]): AsyncState<T> {
  const [tick, setTick] = useState(0);
  const [state, setState] = useState<Settled<T>>({ key: "" });
  const key = JSON.stringify(deps) + "#" + tick;
  const run = useEffectEvent(fn);

  useEffect(() => {
    let alive = true;
    run()
      .then((data) => alive && setState({ key, data }))
      .catch((error: Error) => alive && setState({ key, error }));
    return () => {
      alive = false;
    };
  }, [key]);

  const reload = useCallback(() => setTick((t) => t + 1), []);
  const setData = useCallback(
    (u: T | ((prev: T | undefined) => T)) =>
      setState((s) => ({ ...s, error: undefined, data: typeof u === "function" ? (u as (p: T | undefined) => T)(s.data) : u })),
    [],
  );

  const fresh = state.key === key;
  return {
    data: fresh ? state.data : undefined,
    error: fresh ? state.error : undefined,
    loading: !fresh,
    reload,
    setData,
  };
}
