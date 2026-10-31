import { useEffect, useMemo, useState } from "react";
import { useNavigate, useSearchParams } from "react-router";
import { useComposer } from "../components/Composer";
import Icon from "../components/Icon";
import { Page } from "../components/Shell";
import { Button, EmptyState } from "../components/ui";
import { normalizeCategory } from "../lib/categories";
import { useMyData } from "../lib/data";
import { plural } from "../lib/format";
import { buildShoppingList, type ShoppingItem } from "../lib/shopping";
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

/** Ne garde cochées que les lignes encore dans la liste (une quantité qui change décoche la ligne). */
function keepChecked(checked: string[], items: ShoppingItem[]): string[] {
  const keys = new Set(items.map((i) => i.key));
  return checked.filter((k) => keys.has(k));
}

export default function Shopping() {
  const navigate = useNavigate();
  const { newLink } = useComposer();
  const { links } = useMyData();
  const [params] = useSearchParams();
  // « Préparer la liste de courses » depuis une recette : elle est ajoutée à la sélection
  const [state, setState] = useState<Saved>(() => {
    const saved = load();
    const id = params.get("recette");
    return id && !saved.recipes.includes(id) ? { ...saved, recipes: [...saved.recipes, id] } : saved;
  });

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
  const listFor = (ids: string[]) =>
    buildShoppingList(recipes.filter((r) => ids.includes(r.id)).map((r) => ({ title: r.title, ingredients: r.ingredients ?? [] })));

  // Ingrédients regroupés par produit, quantités additionnées
  const items = listFor(state.recipes);

  const remaining = items.filter((i) => !state.checked.includes(i.key)).length;
  const toggleRecipe = (id: string) =>
    setState((s) => {
      const ids = s.recipes.includes(id) ? s.recipes.filter((x) => x !== id) : [...s.recipes, id];
      return { recipes: ids, checked: keepChecked(s.checked, listFor(ids)) };
    });
  const toggleItem = (k: string) =>
    setState((s) => ({ ...s, checked: s.checked.includes(k) ? s.checked.filter((x) => x !== k) : [...s.checked, k] }));

  return (
    <Page className="max-w-3xl">
      <Seo title="Liste de courses · Tribbae" noindex />
      <div className="flex items-center gap-3">
        <button type="button" onClick={() => navigate(-1)} aria-label="Retour" className="flex h-11 w-11 items-center justify-center rounded-full bg-card shadow-soft hover:bg-sable-2">
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
                    <span className={`flex h-6 w-6 items-center justify-center rounded-full ${on ? "bg-white/25" : "bg-card"}`}>
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
              <div className="flex items-center justify-between gap-3">
                <h2 className="text-lg font-bold">{plural(items.length, "ingrédient", "ingrédients")}</h2>
                <div className="flex gap-4">
                  {state.checked.length > 0 && (
                    <button type="button" onClick={() => setState((s) => ({ ...s, checked: [] }))} className="text-sm font-semibold text-terracotta">
                      Tout décocher
                    </button>
                  )}
                  <button type="button" onClick={() => setState({ recipes: [], checked: [] })} className="text-sm font-semibold text-terracotta">
                    Nouvelle liste
                  </button>
                </div>
              </div>
              <ul className="flex flex-col divide-y divide-sable-2 overflow-hidden rounded-large bg-card">
                {items.map((it) => {
                  const done = state.checked.includes(it.key);
                  return (
                    <li key={it.key}>
                      <label className="flex cursor-pointer items-center gap-3 px-4 py-3">
                        <input type="checkbox" checked={done} onChange={() => toggleItem(it.key)} className="peer sr-only" />
                        <span
                          aria-hidden="true"
                          className={`flex h-6 w-6 shrink-0 items-center justify-center rounded-lg border-2 peer-focus-visible:ring-2 peer-focus-visible:ring-terracotta ${done ? "border-recette-ic bg-recette-ic text-white" : "border-dash"}`}
                        >
                          {done && <Icon name="check" size={14} strokeWidth={3} />}
                        </span>
                        <span className="flex min-w-0 flex-1 flex-col">
                          <span className={`text-[15px] ${done ? "text-muted line-through" : "font-medium"}`}>{it.label}</span>
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
