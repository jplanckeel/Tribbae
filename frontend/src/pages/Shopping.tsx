import { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router";
import { useComposer } from "../components/Composer";
import Icon from "../components/Icon";
import { Page } from "../components/Shell";
import { Button, EmptyState } from "../components/ui";
import { normalizeCategory } from "../lib/categories";
import { useMyData } from "../lib/data";
import { plural } from "../lib/format";
import Seo from "../components/Seo";

// Sélection des recettes et ingrédients cochés : conservés sur l'appareil
const STORE = "tribbae.courses";

interface Saved {
  recipes: string[];
  checked: string[];
}

function load(): Saved {
  try {
    const v = JSON.parse(localStorage.getItem(STORE) ?? "");
    if (Array.isArray(v?.recipes) && Array.isArray(v?.checked)) return v;
  } catch {
    // Stockage indisponible ou vide
  }
  return { recipes: [], checked: [] };
}

export default function Shopping() {
  const navigate = useNavigate();
  const { newLink } = useComposer();
  const { links } = useMyData();
  const [state, setState] = useState<Saved>(load);

  useEffect(() => {
    try {
      localStorage.setItem(STORE, JSON.stringify(state));
    } catch {
      // Navigation privée : la liste reste en mémoire
    }
  }, [state]);

  const recipes = useMemo(
    () => links.filter((l) => normalizeCategory(l.category) === "LINK_CATEGORY_RECETTE" && l.ingredients?.length),
    [links],
  );
  const selected = recipes.filter((r) => state.recipes.includes(r.id));

  // Ingrédients regroupés (même nom = une ligne), avec les recettes d'origine
  const items = useMemo(() => {
    const m = new Map<string, { name: string; from: string[] }>();
    for (const r of selected) {
      for (const raw of r.ingredients ?? []) {
        const name = raw.trim();
        if (!name) continue;
        const key = name.toLowerCase();
        const cur = m.get(key) ?? { name, from: [] };
        if (!cur.from.includes(r.title)) cur.from.push(r.title);
        m.set(key, cur);
      }
    }
    return [...m.entries()].sort((a, b) => a[1].name.localeCompare(b[1].name, "fr"));
  }, [selected]);

  const remaining = items.filter(([k]) => !state.checked.includes(k)).length;
  const toggleRecipe = (id: string) =>
    setState((s) => ({ ...s, recipes: s.recipes.includes(id) ? s.recipes.filter((x) => x !== id) : [...s.recipes, id] }));
  const toggleItem = (k: string) =>
    setState((s) => ({ ...s, checked: s.checked.includes(k) ? s.checked.filter((x) => x !== k) : [...s.checked, k] }));

  return (
    <Page className="max-w-3xl">
      <Seo title="Liste de courses · Tribbae" noindex />
      <div className="flex items-center gap-3">
        <button type="button" onClick={() => navigate(-1)} aria-label="Retour" className="flex h-11 w-11 items-center justify-center rounded-full bg-white shadow-soft hover:bg-sable-2">
          <Icon name="chevronLeft" size={22} />
        </button>
        <div className="flex flex-col">
          <h1 className="font-display text-[28px] leading-tight font-extrabold tracking-[-0.6px]">Liste de courses</h1>
          <span className="text-sm text-sec">
            {items.length ? `${remaining} restant${remaining > 1 ? "s" : ""} sur ${items.length}` : "Choisissez vos recettes de la semaine"}
          </span>
        </div>
      </div>

      {recipes.length === 0 ? (
        <EmptyState
          icon="recette"
          title="Aucune recette avec ingrédients"
          text="Ajoutez une recette et listez ses ingrédients : ils se retrouveront ici, regroupés."
          action={<Button icon="plus" onClick={() => newLink({ category: "LINK_CATEGORY_RECETTE" })}>Ajouter une recette</Button>}
        />
      ) : (
        <>
          <section className="flex flex-col gap-2.5">
            <h2 className="text-lg font-bold">Recettes</h2>
            <div className="no-scrollbar -mx-5 flex gap-2 overflow-x-auto px-5 pb-1">
              {recipes.map((r) => {
                const on = state.recipes.includes(r.id);
                return (
                  <button
                    key={r.id}
                    type="button"
                    aria-pressed={on}
                    onClick={() => toggleRecipe(r.id)}
                    className={`flex shrink-0 items-center gap-2 rounded-full py-2 pr-4 pl-2 text-sm font-semibold transition-colors ${on ? "bg-recette-ic text-white" : "bg-recette-bg text-recette-ink"}`}
                  >
                    <span className={`flex h-6 w-6 items-center justify-center rounded-full ${on ? "bg-white/25" : "bg-white"}`}>
                      <Icon name={on ? "check" : "plus"} size={14} strokeWidth={2.6} />
                    </span>
                    {r.title}
                    <span className="opacity-70">{r.ingredients!.length}</span>
                  </button>
                );
              })}
            </div>
          </section>

          {items.length > 0 ? (
            <section className="flex flex-col gap-2.5">
              <div className="flex items-center justify-between">
                <h2 className="text-lg font-bold">{plural(items.length, "ingrédient", "ingrédients")}</h2>
                {state.checked.length > 0 && (
                  <button type="button" onClick={() => setState((s) => ({ ...s, checked: [] }))} className="text-sm font-semibold text-terracotta">
                    Tout décocher
                  </button>
                )}
              </div>
              <ul className="flex flex-col divide-y divide-sable-2 overflow-hidden rounded-l bg-white">
                {items.map(([k, it]) => {
                  const done = state.checked.includes(k);
                  return (
                    <li key={k}>
                      <label className="flex cursor-pointer items-center gap-3 px-4 py-3">
                        <input type="checkbox" checked={done} onChange={() => toggleItem(k)} className="peer sr-only" />
                        <span
                          aria-hidden="true"
                          className={`flex h-6 w-6 shrink-0 items-center justify-center rounded-lg border-2 peer-focus-visible:ring-2 peer-focus-visible:ring-terracotta ${done ? "border-recette-ic bg-recette-ic text-white" : "border-dash"}`}
                        >
                          {done && <Icon name="check" size={14} strokeWidth={3} />}
                        </span>
                        <span className="flex min-w-0 flex-1 flex-col">
                          <span className={`text-[15px] ${done ? "text-muted line-through" : "font-medium"}`}>{it.name}</span>
                          {it.from.length > 1 && <span className="truncate text-xs text-sec">{it.from.join(" · ")}</span>}
                        </span>
                      </label>
                    </li>
                  );
                })}
              </ul>
            </section>
          ) : (
            <p className="text-sm text-sec">Touchez une ou plusieurs recettes pour composer la liste.</p>
          )}
        </>
      )}
    </Page>
  );
}
