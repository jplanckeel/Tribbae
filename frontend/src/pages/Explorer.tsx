import { useEffect, useMemo, useState } from "react";
import { useNavigate, useParams, useSearchParams } from "react-router";
import { AiCard, FolderCard, GridCard, IdeaCard, ListCard } from "../components/cards";
import { useComposer } from "../components/Composer";
import Icon from "../components/Icon";
import { Page } from "../components/Shell";
import Sheet from "../components/Sheet";
import { Button, Chip, EmptyState, ErrorNote, PageHeader, PageSpinner } from "../components/ui";
import * as api from "../lib/api";
import { CATEGORIES, categoryBySlug, normalizeCategory } from "../lib/categories";
import { useMyData } from "../lib/data";
import { ageInMonths, eventDateMs, matchesAge, matchesQuery, priceValue } from "../lib/format";
import { byPopularity, byRecent, byRotation } from "../lib/links";
import { useSession } from "../lib/session";
import { useAsync } from "../lib/useAsync";
import { useDesktop } from "../lib/useMediaQuery";
import type { Folder, Link } from "../lib/types";
import Seo from "../components/Seo";

const AGES = [
  { key: "0-3", label: "0–3", min: 0, max: 3 },
  { key: "3-6", label: "3–6", min: 3, max: 6 },
  { key: "6-10", label: "6–10", min: 6, max: 10 },
  { key: "10+", label: "10+", min: 10, max: 99 },
];

const BUDGETS = [
  { key: "gratuit", label: "Gratuit", max: 0 },
  { key: "moins10", label: "Moins de 10 €", max: 10 },
] as const;
type BudgetKey = (typeof BUDGETS)[number]["key"];

const ageKeyFor = (years: number) => AGES.find((a) => years >= a.min && years < a.max)?.key ?? "10+";
const toggle = (list: string[], v: string) => (list.includes(v) ? list.filter((x) => x !== v) : [...list, v]);

/** Samedi 0 h → lundi 0 h du week-end en cours ou à venir. */
function weekendRange(now = new Date()): [number, number] {
  const d = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  const day = d.getDay(); // 0 = dimanche, 6 = samedi
  const offset = day === 6 ? 0 : day === 0 ? -1 : 6 - day;
  d.setDate(d.getDate() + offset);
  const start = d.getTime();
  d.setDate(d.getDate() + 2);
  return [start, d.getTime()];
}

interface Filters {
  q: string;
  cats: string[]; // clés LINK_CATEGORY_*
  ages: string[];
  budget?: BudgetKey;
  weekend: boolean;
  sort: "une" | "popular" | "recent";
}

/** Filtres hors catégorie (les compteurs par catégorie en dépendent). */
function matchesOther(l: Link, f: Filters): boolean {
  if (!matchesQuery(l, f.q)) return false;
  const ranges = AGES.filter((a) => f.ages.includes(a.key));
  if (ranges.length && !ranges.some((r) => matchesAge(l.ageRange, r.min, r.max))) return false;
  if (f.budget) {
    const max = BUDGETS.find((b) => b.key === f.budget)!.max;
    const price = priceValue(l.price);
    if (price === undefined || (max === 0 ? price > 0 : price >= max)) return false;
  }
  if (f.weekend) {
    const at = eventDateMs(l.eventDate);
    const [start, end] = weekendRange();
    if (!at || at < start || at >= end) return false;
  }
  return true;
}

export default function Explorer() {
  const { slug } = useParams();
  const navigate = useNavigate();
  const { openAi } = useComposer();
  const desktop = useDesktop();
  const [params, setParams] = useSearchParams();

  const view = params.get("vue") === "dossiers" ? "dossiers" : "idees";
  const catSlugs = (params.get("cat") ?? slug ?? "").split(",").filter((s) => categoryBySlug(s));
  const filters: Filters = {
    q: params.get("q") ?? "",
    cats: catSlugs.map((s) => categoryBySlug(s)!.key),
    ages: (params.get("age") ?? "").split(",").filter(Boolean),
    budget: BUDGETS.find((b) => b.key === params.get("budget"))?.key,
    weekend: params.get("quand") === "weekend",
    sort: params.get("tri") === "recent" ? "recent" : params.get("tri") === "aimees" ? "popular" : "une",
  };

  const update = (patch: Record<string, string | undefined>) => {
    const next = new URLSearchParams(params);
    for (const [k, v] of Object.entries(patch)) {
      if (v) next.set(k, v);
      else next.delete(k);
    }
    // Les URLs /category/:slug restent valides mais les filtres passent en paramètres
    if (slug) {
      if (!("cat" in patch) && catSlugs.length) next.set("cat", catSlugs.join(","));
      navigate(`/explorer?${next}`, { replace: true });
    } else {
      setParams(next, { replace: true });
    }
  };
  const setCats = (keys: string[]) =>
    update({ cat: keys.map((k) => CATEGORIES.find((c) => c.key === k)!.slug).join(",") || undefined });
  const reset = () => update({ cat: undefined, age: undefined, budget: undefined, quand: undefined, tri: undefined });

  // Champ de recherche local, resynchronisé si l'URL change (retour arrière…)
  const [draft, setDraft] = useState(filters.q);
  const [lastQ, setLastQ] = useState(filters.q);
  if (filters.q !== lastQ) {
    setLastQ(filters.q);
    if (filters.q !== draft.trim()) setDraft(filters.q);
  }
  // Recherche à la frappe, temporisée
  useEffect(() => {
    if (draft === filters.q) return;
    const t = setTimeout(() => update({ q: draft.trim() || undefined }), 250);
    return () => clearTimeout(t);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [draft]);

  // Toutes les idées publiques, par catégorie (l'API plafonne à 100 par appel) :
  // les filtres et les compteurs se calculent ensuite sans nouvel appel.
  const all = useAsync(
    () => Promise.all(CATEGORIES.map((c) => api.community.links(c.key, 100))).then((lists) => lists.flat()),
    [],
  );
  const pool = useMemo(() => (all.data ?? []).filter((l) => matchesOther(l, filters)), [all.data, params]); // eslint-disable-line react-hooks/exhaustive-deps
  const counts = useMemo(() => {
    const m = new Map<string, number>();
    for (const l of pool) m.set(normalizeCategory(l.category), (m.get(normalizeCategory(l.category)) ?? 0) + 1);
    return m;
  }, [pool]);
  const list = useMemo(() => {
    const shown = pool.filter((l) => !filters.cats.length || filters.cats.includes(normalizeCategory(l.category)));
    // « À la une » par défaut : un ordre qui change toutes les 6 heures
    if (filters.sort === "une") return byRotation(shown);
    return shown.sort(filters.sort === "recent" ? byRecent : byPopularity);
  }, [pool, params]); // eslint-disable-line react-hooks/exhaustive-deps

  const single = filters.cats.length === 1 ? CATEGORIES.find((c) => c.key === filters.cats[0]) : undefined;
  const props: LayoutProps = {
    view,
    filters,
    draft,
    setDraft,
    single,
    list,
    counts,
    loading: all.loading,
    error: all.error,
    reload: all.reload,
    update,
    setCats,
    reset,
    onAi: () => openAi({ prompt: filters.q || undefined }),
    onRequireAuth: () => navigate("/bienvenue?next=/explorer"),
  };

  return (
    <>
      <Seo
        title={single ? `${single.plural} en famille : les idées des tribus · Tribbae` : "Explorer les idées des familles · Tribbae"}
        description={single?.intro ?? "Sorties, activités, recettes, cadeaux, livres et déco : les idées et dossiers publiés par les familles de Tribbae, à garder dans vos propres listes."}
        path={slug && single ? `/category/${single.slug}` : "/explorer"}
        noindex={params.has("q") || params.has("vue")}
      />
      {desktop ? <ExplorerDesktop {...props} /> : <ExplorerMobile {...props} />}
    </>
  );
}

interface LayoutProps {
  view: "idees" | "dossiers";
  filters: Filters;
  draft: string;
  setDraft: (v: string) => void;
  single?: (typeof CATEGORIES)[number];
  list: Link[];
  counts: Map<string, number>;
  loading: boolean;
  error?: Error | null;
  reload: () => void;
  update: (patch: Record<string, string | undefined>) => void;
  setCats: (keys: string[]) => void;
  reset: () => void;
  onAi: () => void;
  onRequireAuth: () => void;
}

function ViewTabs({ view, update, className = "" }: Pick<LayoutProps, "view" | "update"> & { className?: string }) {
  return (
    <div role="tablist" aria-label="Que chercher ?" className={`grid h-12 grid-cols-2 gap-1 rounded-2xl bg-sable-2 p-1 ${className}`}>
      {(["idees", "dossiers"] as const).map((v) => (
        <button
          key={v}
          role="tab"
          aria-selected={view === v}
          onClick={() => update({ vue: v === "dossiers" ? "dossiers" : undefined })}
          className={`rounded-small text-sm transition-colors ${view === v ? "bg-white font-bold shadow-[0_1px_4px_rgba(58,33,27,0.1)]" : "font-semibold text-sec"}`}
        >
          {v === "idees" ? "Idées" : "Dossiers"}
        </button>
      ))}
    </div>
  );
}

function SearchBox({ view, draft, setDraft, className = "" }: Pick<LayoutProps, "view" | "draft" | "setDraft"> & { className?: string }) {
  return (
    <label className={`flex items-center gap-2.5 bg-white px-4 text-muted focus-within:ring-2 focus-within:ring-terracotta ${className}`}>
      <Icon name="search" size={20} />
      <input
        type="search"
        aria-label={view === "idees" ? "Rechercher une idée" : "Rechercher un dossier"}
        value={draft}
        onChange={(e) => setDraft(e.target.value)}
        placeholder={view === "idees" ? "Sortie nature, crêpes, Lego…" : "Vacances, Noël, goûters…"}
        className="min-w-0 flex-1 bg-transparent font-medium text-ecorce outline-none placeholder:text-muted"
      />
    </label>
  );
}

/** Compteur de résultats : « 38 idées pour « sortie nature » ». */
function ResultCount({ n, q }: { n: number; q: string }) {
  return (
    <span className="text-sec">
      <strong className="text-ecorce">{n} idée{n > 1 ? "s" : ""}</strong>
      {q ? <> pour « {q} »</> : <> partagée{n > 1 ? "s" : ""} par la communauté</>}
    </span>
  );
}

/** Pastille d'un filtre actif qui n'a pas de contrôle visible (ex. « Ce week-end » depuis l'accueil). */
function WeekendChip({ update }: Pick<LayoutProps, "update">) {
  return (
    <button
      type="button"
      onClick={() => update({ quand: undefined })}
      aria-label="Retirer le filtre « Ce week-end »"
      className="flex items-center gap-1.5 self-start rounded-full bg-ecorce px-3 py-1.5 text-[13px] font-semibold text-sable"
    >
      <Icon name="calendar" size={14} />
      Ce week-end
      <Icon name="close" size={13} />
    </button>
  );
}

// ── Mobile ────────────────────────────────────────────────────────────

const explorerSubtitle = (view: string) =>
  view === "idees" ? "Les idées partagées par les tribus" : "Les dossiers publiés par les tribus";

function ExplorerMobile(p: LayoutProps) {
  const { view, filters, update } = p;
  const session = useSession();
  const { children } = useMyData();
  const [filtersOpen, setFiltersOpen] = useState(false);
  const activeFilters = filters.ages.length + (filters.budget ? 1 : 0) + (filters.sort !== "une" ? 1 : 0);
  // Carte « IA » glissée dans la mosaïque
  const aiSlot = Math.min(3, p.list.length);

  return (
    <Page className="gap-4">
      <PageHeader
        title={p.single && view === "idees" ? p.single.plural : "Explorer"}
        subtitle={explorerSubtitle(view)}
        action={view === "idees" && (
          <button
            type="button"
            onClick={() => setFiltersOpen(true)}
            className="flex h-11 items-center gap-2 rounded-[14px] border-[1.5px] border-line-2 bg-white px-3.5 text-sm font-semibold transition-colors hover:bg-sable-2"
          >
            <Icon name="filter" size={20} />
            Filtres
            {activeFilters > 0 && (
              <span className="flex h-5 min-w-5 items-center justify-center rounded-[10px] bg-terracotta px-1 text-xs text-white">{activeFilters}</span>
            )}
          </button>
        )}
      />

      <SearchBox {...p} className="h-[52px] shrink-0 rounded-medium text-[15px] shadow-card" />
      <ViewTabs {...p} />

      {view === "dossiers" ? (
        <FoldersView key={filters.q} q={filters.q} desktop={false} />
      ) : (
        <>
          <div className="no-scrollbar -mx-5 flex gap-2 overflow-x-auto px-5">
            <Chip active={!filters.cats.length} onClick={() => p.setCats([])}>Tout</Chip>
            {CATEGORIES.map((c) => (
              <Chip key={c.key} active={filters.cats.includes(c.key)} onClick={() => p.setCats(toggle(filters.cats, c.key))} idleClass={`${c.bg} ${c.ink}`}>
                {c.plural}
              </Chip>
            ))}
          </div>

          <div className="flex flex-wrap items-center gap-2 text-[13px] text-sec">
            <span className="font-semibold">Âge</span>
            {AGES.map((a) => {
              const on = filters.ages.includes(a.key);
              return (
                <button
                  key={a.key}
                  type="button"
                  aria-pressed={on}
                  onClick={() => update({ age: toggle(filters.ages, a.key).join(",") || undefined })}
                  className={`rounded-full border-[1.5px] px-3 py-1.5 transition-colors ${on ? "border-terracotta bg-white font-semibold text-terracotta-dark" : "border-line-2 hover:bg-sable-2"}`}
                >
                  {a.label}
                </button>
              );
            })}
          </div>
          {filters.weekend && <WeekendChip update={update} />}

          {p.loading ? (
            <PageSpinner />
          ) : p.error ? (
            <div className="flex flex-col items-start gap-3">
              <ErrorNote>{p.error.message}</ErrorNote>
              <Button variant="ghost" size="sm" onClick={p.reload}>Réessayer</Button>
            </div>
          ) : (
            <>
              <span className="text-[13px]"><ResultCount n={p.list.length} q={filters.q} /></span>
              {p.list.length === 0 ? (
                <NoResult onAi={p.onAi} />
              ) : (
                // Comme l'app : deux colonnes remplies en alternance (1 à gauche,
                // 2 à droite…), la carte IA en 4e position
                <div className="flex gap-3">
                  {[0, 1].map((col) => (
                    <div key={col} className="flex min-w-0 flex-1 flex-col">
                      {[...p.list.slice(0, aiSlot), null, ...p.list.slice(aiSlot)].map((l, i) =>
                        i % 2 !== col ? null : l ? (
                          <GridCard key={l.id} link={l} index={i} onRequireAuth={p.onRequireAuth} />
                        ) : (
                          <AiPromo key="ai" onClick={p.onAi} />
                        ),
                      )}
                    </div>
                  ))}
                </div>
              )}
            </>
          )}
        </>
      )}

      <Sheet open={filtersOpen} onClose={() => setFiltersOpen(false)} title="Filtres">
        <div className="flex flex-col gap-5">
          <fieldset>
            <legend className="mb-2 text-[13px] font-bold text-sec">Trier par</legend>
            <div className="flex flex-wrap gap-2">
              <Chip active={filters.sort === "une"} onClick={() => update({ tri: undefined })}>À la une</Chip>
              <Chip active={filters.sort === "popular"} onClick={() => update({ tri: "aimees" })}>Les plus aimées</Chip>
              <Chip active={filters.sort === "recent"} onClick={() => update({ tri: "recent" })}>Les plus récentes</Chip>
            </div>
          </fieldset>
          <fieldset>
            <legend className="mb-2 text-[13px] font-bold text-sec">Âge des enfants</legend>
            <div className="flex flex-wrap gap-2">
              {AGES.map((a) => (
                <Chip key={a.key} active={filters.ages.includes(a.key)} onClick={() => update({ age: toggle(filters.ages, a.key).join(",") || undefined })}>
                  {a.label} ans
                </Chip>
              ))}
            </div>
            {session && children.length > 0 && (
              <button
                type="button"
                onClick={() => update({ age: [...new Set(children.map((c) => ageKeyFor(ageInMonths(c.birthDate) / 12)))].join(",") })}
                className="mt-3 flex items-center gap-2 text-sm font-bold text-terracotta"
              >
                <Icon name="baby" size={18} />
                L'âge de mes enfants ({children.map((c) => c.name).join(", ")})
              </button>
            )}
          </fieldset>
          <fieldset>
            <legend className="mb-2 text-[13px] font-bold text-sec">Budget</legend>
            <div className="flex flex-wrap gap-2">
              <Chip active={!filters.budget} onClick={() => update({ budget: undefined })}>Peu importe</Chip>
              {BUDGETS.map((b) => (
                <Chip key={b.key} active={filters.budget === b.key} onClick={() => update({ budget: b.key })}>{b.label}</Chip>
              ))}
            </div>
          </fieldset>
          <div className="flex gap-2.5">
            <Button variant="ghost" block onClick={() => update({ age: undefined, tri: undefined, budget: undefined })}>Réinitialiser</Button>
            <Button variant="dark" block onClick={() => setFiltersOpen(false)}>Voir les idées</Button>
          </div>
        </div>
      </Sheet>
    </Page>
  );
}

function AiPromo({ onClick }: { onClick: () => void }) {
  return (
    <div className="mb-3 flex break-inside-avoid flex-col gap-2.5 rounded-[20px] bg-prune p-3.5 text-sable">
      <Icon name="sparkles" size={26} className="text-soleil" />
      <span className="font-display text-base leading-tight font-bold">Pas d'idée ? L'IA prépare un dossier sur mesure.</span>
      <button type="button" onClick={onClick} className="self-start rounded-small bg-soleil px-3 py-2 text-[13px] font-bold text-ecorce transition-colors hover:bg-white">
        Essayer
      </button>
    </div>
  );
}

function NoResult({ onAi }: { onAi: () => void }) {
  return (
    <EmptyState
      icon="compass"
      title="Rien trouvé par ici"
      text="Élargissez les filtres ou demandez à l'IA de vous préparer un dossier sur mesure."
      action={<Button variant="ai" icon="sparkles" onClick={onAi}>Demander à l'IA</Button>}
    />
  );
}

// ── Bureau (maquette « Web · Explorer ») ─────────────────────────────────

function ExplorerDesktop(p: LayoutProps) {
  const { view, filters, update } = p;
  const legend = "mb-2 text-[13px] font-bold tracking-[0.8px] text-sec uppercase";
  const hasFilters = filters.cats.length + filters.ages.length > 0 || !!filters.budget || filters.weekend;

  return (
    <Page className="gap-7 pb-4">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <PageHeader title={p.single && view === "idees" ? p.single.plural : "Explorer"} subtitle={explorerSubtitle(view)} />
        <div className="flex flex-[0_1_560px] items-center gap-3">
          <ViewTabs {...p} className="w-52 shrink-0" />
          <SearchBox {...p} className="h-[54px] min-w-0 flex-1 rounded-[18px] text-base shadow-card" />
        </div>
      </div>

      {view === "dossiers" ? (
        <FoldersView key={filters.q} q={filters.q} desktop />
      ) : (
        <div className="flex flex-wrap items-start gap-7">
          <aside aria-label="Filtres" className="flex max-w-full flex-[1_1_240px] flex-col gap-[22px] rounded-[24px] bg-white p-[22px] lg:sticky lg:top-6">
            <fieldset className="flex flex-col gap-2">
              <legend className={legend}>Catégorie</legend>
              {CATEGORIES.map((c) => {
                const on = filters.cats.includes(c.key);
                return (
                  <label
                    key={c.key}
                    className={`flex cursor-pointer items-center gap-2.5 rounded-xl px-2.5 py-2 text-[15px] transition-colors ${on ? `${c.bg} ${c.ink} font-bold` : "hover:bg-sable"}`}
                  >
                    <input
                      type="checkbox"
                      checked={on}
                      onChange={() => p.setCats(toggle(filters.cats, c.key))}
                      className="h-[18px] w-[18px]"
                      style={{ accentColor: c.solid }}
                    />
                    {c.plural}
                    <span className={`ml-auto text-[13px] ${on ? "" : "text-sec"}`}>{p.counts.get(c.key) ?? 0}</span>
                  </label>
                );
              })}
            </fieldset>

            <fieldset className="flex flex-col gap-2">
              <legend className={legend}>Âge des enfants</legend>
              <div className="flex flex-wrap gap-1.5">
                {AGES.map((a) => {
                  const on = filters.ages.includes(a.key);
                  return (
                    <button
                      key={a.key}
                      type="button"
                      aria-pressed={on}
                      onClick={() => update({ age: toggle(filters.ages, a.key).join(",") || undefined })}
                      className={`rounded-full border-[1.5px] px-3 py-2 text-sm transition-colors ${on ? "border-terracotta bg-[#FFF4EE] font-bold text-terracotta-dark" : "border-line-2 font-semibold hover:bg-sable"}`}
                    >
                      {a.label}
                    </button>
                  );
                })}
              </div>
            </fieldset>

            <fieldset className="flex flex-col gap-2">
              <legend className={legend}>Budget</legend>
              {[...BUDGETS, { key: undefined, label: "Peu importe" }].map((b) => (
                <label key={b.label} className="flex cursor-pointer items-center gap-2.5 text-[15px]">
                  <input
                    type="radio"
                    name="budget"
                    checked={filters.budget === b.key}
                    onChange={() => update({ budget: b.key })}
                    className="h-[18px] w-[18px] accent-terracotta"
                  />
                  {b.label}
                </label>
              ))}
            </fieldset>

            {filters.weekend && <WeekendChip update={update} />}

            <button
              type="button"
              onClick={p.reset}
              disabled={!hasFilters}
              className="h-[46px] rounded-[14px] border-2 border-ecorce text-[15px] font-bold transition-colors hover:bg-ecorce hover:text-sable disabled:border-line-2 disabled:text-muted disabled:hover:bg-transparent"
            >
              Réinitialiser
            </button>
          </aside>

          <section aria-label="Résultats" className="flex min-w-0 flex-[999_1_560px] flex-col gap-4">
            <div className="flex flex-wrap items-center justify-between gap-2.5">
              <span className="text-[15px]">{!p.loading && !p.error && <ResultCount n={p.list.length} q={filters.q} />}</span>
              <label className="flex items-center gap-2 text-sm text-sec">
                Trier par
                <select
                  value={filters.sort}
                  onChange={(e) => update({ tri: e.target.value === "recent" ? "recent" : e.target.value === "popular" ? "aimees" : undefined })}
                  className="h-10 rounded-xl border-[1.5px] border-line-2 bg-white px-2.5 text-sm font-semibold text-ecorce outline-none focus:border-terracotta"
                >
                  <option value="une">À la une</option>
                  <option value="popular">Les plus aimées</option>
                  <option value="recent">Les plus récentes</option>
                </select>
              </label>
            </div>

            {p.loading ? (
              <div className="grid grid-cols-[repeat(auto-fill,minmax(230px,1fr))] gap-4">
                {[0, 1, 2, 3, 4, 5].map((i) => <div key={i} className="h-[240px] animate-pulse rounded-[22px] bg-sable-2" />)}
              </div>
            ) : p.error ? (
              <div className="flex flex-col items-start gap-3">
                <ErrorNote>{p.error.message}</ErrorNote>
                <Button variant="ghost" size="sm" onClick={p.reload}>Réessayer</Button>
              </div>
            ) : p.list.length === 0 ? (
              <NoResult onAi={p.onAi} />
            ) : (
              <div className="grid grid-cols-[repeat(auto-fill,minmax(230px,1fr))] gap-4">
                {p.list.map((l) => <IdeaCard key={l.id} link={l} onRequireAuth={p.onRequireAuth} />)}
                <AiCard title="Pas trouvé ? L'IA prépare une liste sur mesure." onClick={p.onAi} className="rounded-[22px] p-5" />
              </div>
            )}
          </section>
        </div>
      )}
    </Page>
  );
}

// ── Dossiers de la communauté ──────────────────────────────────────────

/** Monté avec `key={q}` : la pagination repart de zéro à chaque recherche. */
function FoldersView({ q, desktop }: { q: string; desktop: boolean }) {
  const [extra, setExtra] = useState<Folder[]>([]);
  const [nextToken, setNextToken] = useState<string | null>(null);
  const [loadingMore, setLoadingMore] = useState(false);
  const first = useAsync(() => api.community.folders(q || undefined, 24), [q]);
  const pages = [...(first.data?.folders ?? []), ...extra];
  const token = nextToken ?? (first.data?.nextPageToken || undefined);

  const more = async () => {
    if (!token) return;
    setLoadingMore(true);
    try {
      const r = await api.community.folders(q || undefined, 24, token);
      setExtra((p) => [...p, ...(r.folders ?? [])]);
      setNextToken(r.nextPageToken || "");
    } finally {
      setLoadingMore(false);
    }
  };

  if (first.loading) return <PageSpinner />;
  if (first.error) return <ErrorNote>{first.error.message}</ErrorNote>;
  if (!pages.length) return <EmptyState icon="tipi" title="Aucun dossier public" text="Rendez un de vos dossiers public pour inspirer les autres tribus." />;

  return (
    <>
      {desktop ? (
        <div className="grid grid-cols-[repeat(auto-fill,minmax(260px,1fr))] gap-4">
          {pages.map((f) => <ListCard key={f.id} folder={f} />)}
        </div>
      ) : (
        <div className="grid grid-cols-2 gap-3">
          {pages.map((f) => <FolderCard key={f.id} folder={f} showOwner />)}
        </div>
      )}
      {token && (
        <Button variant="ghost" className="self-center" loading={loadingMore} onClick={more}>
          Voir plus de dossiers
        </Button>
      )}
    </>
  );
}
