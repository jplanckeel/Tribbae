import { Fragment, useEffect, useMemo, useState } from "react";
import { useNavigate, useParams, useSearchParams } from "react-router";
import { FolderCard, GridCard } from "../components/cards";
import { useComposer } from "../components/Composer";
import Icon from "../components/Icon";
import { Page } from "../components/Shell";
import Sheet from "../components/Sheet";
import { Button, Chip, EmptyState, ErrorNote, PageSpinner } from "../components/ui";
import * as api from "../lib/api";
import { CATEGORIES, categoryBySlug } from "../lib/categories";
import { useMyData } from "../lib/data";
import { ageInMonths, matchesAge, matchesQuery } from "../lib/format";
import { byPopularity, byRecent } from "../lib/links";
import { useSession } from "../lib/session";
import { useAsync } from "../lib/useAsync";
import type { Folder } from "../lib/types";

const AGES = [
  { key: "0-3", label: "0–3", min: 0, max: 3 },
  { key: "3-6", label: "3–6", min: 3, max: 6 },
  { key: "6-10", label: "6–10", min: 6, max: 10 },
  { key: "10+", label: "10+", min: 10, max: 99 },
];

const ageKeyFor = (years: number) => AGES.find((a) => years >= a.min && years < a.max)?.key ?? "10+";

export default function Explorer() {
  const { slug } = useParams();
  const navigate = useNavigate();
  const session = useSession();
  const { children } = useMyData();
  const { openAi } = useComposer();
  const [params, setParams] = useSearchParams();
  const [filtersOpen, setFiltersOpen] = useState(false);

  const view = params.get("vue") === "dossiers" ? "dossiers" : "idees";
  const q = params.get("q") ?? "";
  const cat = categoryBySlug(slug ?? params.get("cat") ?? undefined);
  const ages = (params.get("age") ?? "").split(",").filter(Boolean);
  const sort = params.get("tri") === "recent" ? "recent" : "popular";

  const update = (patch: Record<string, string | undefined>) => {
    const next = new URLSearchParams(params);
    for (const [k, v] of Object.entries(patch)) {
      if (v) next.set(k, v);
      else next.delete(k);
    }
    // Les URLs /category/:slug restent valides mais les filtres passent en paramètres
    if (slug) {
      if (!("cat" in patch) && cat) next.set("cat", cat.slug);
      navigate(`/explorer?${next}`, { replace: true });
    } else {
      setParams(next, { replace: true });
    }
  };

  // Champ de recherche local, resynchronisé si l'URL change (retour arrière…)
  const [draft, setDraft] = useState(q);
  const [lastQ, setLastQ] = useState(q);
  if (q !== lastQ) {
    setLastQ(q);
    if (q !== draft.trim()) setDraft(q);
  }
  // Recherche à la frappe, temporisée
  useEffect(() => {
    if (draft === q) return;
    const t = setTimeout(() => update({ q: draft.trim() || undefined }), 250);
    return () => clearTimeout(t);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [draft]);

  const requireAuth = () => navigate("/bienvenue?next=/explorer");
  const activeFilters = ages.length + (sort === "recent" ? 1 : 0);

  return (
    <Page className="gap-4">
      <title>{cat ? `${cat.plural} en famille · Tribbae` : "Explorer les idées des tribus · Tribbae"}</title>
      <div className="flex items-center justify-between gap-3">
        <h1 className="font-display text-[32px] font-extrabold tracking-[-0.8px] md:text-[40px]">
          {cat && view === "idees" ? cat.plural : "Explorer"}
        </h1>
        {view === "idees" && (
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
      </div>

      <div className="flex flex-col gap-4 md:flex-row md:items-center">
        <label className="flex h-[52px] shrink-0 items-center gap-2.5 rounded-m bg-white px-4 text-muted shadow-card focus-within:ring-2 focus-within:ring-terracotta md:max-w-xl md:flex-1">
          <Icon name="search" size={20} />
          <input
            type="search"
            aria-label={view === "idees" ? "Rechercher une idée" : "Rechercher un dossier"}
            value={draft}
            onChange={(e) => setDraft(e.target.value)}
            placeholder={view === "idees" ? "Sortie nature, crêpes, Lego…" : "Vacances, Noël, goûters…"}
            className="min-w-0 flex-1 bg-transparent text-[15px] font-medium text-ecorce outline-none placeholder:text-muted"
          />
        </label>
        <div role="tablist" aria-label="Que chercher ?" className="grid h-12 grid-cols-2 gap-1 rounded-2xl bg-sable-2 p-1 md:w-64">
          {(["idees", "dossiers"] as const).map((v) => (
            <button
              key={v}
              role="tab"
              aria-selected={view === v}
              onClick={() => update({ vue: v === "dossiers" ? "dossiers" : undefined })}
              className={`rounded-s text-sm transition-colors ${view === v ? "bg-white font-bold shadow-[0_1px_4px_rgba(58,33,27,0.1)]" : "font-semibold text-sec"}`}
            >
              {v === "idees" ? "Idées" : "Dossiers"}
            </button>
          ))}
        </div>
      </div>

      {view === "idees" ? (
        <IdeasView
          q={q}
          catKey={cat?.key}
          catSlug={cat?.slug}
          ages={ages}
          sort={sort}
          onCat={(s) => update({ cat: s })}
          onAges={(a) => update({ age: a.join(",") || undefined })}
          onRequireAuth={requireAuth}
          onAi={() => openAi({ prompt: q || undefined })}
        />
      ) : (
        <FoldersView key={q} q={q} />
      )}

      <Sheet open={filtersOpen} onClose={() => setFiltersOpen(false)} title="Filtres">
        <div className="flex flex-col gap-5">
          <fieldset>
            <legend className="mb-2 text-[13px] font-bold text-sec">Trier par</legend>
            <div className="flex gap-2">
              <Chip active={sort === "popular"} onClick={() => update({ tri: undefined })}>Les plus aimées</Chip>
              <Chip active={sort === "recent"} onClick={() => update({ tri: "recent" })}>Les plus récentes</Chip>
            </div>
          </fieldset>
          <fieldset>
            <legend className="mb-2 text-[13px] font-bold text-sec">Âge des enfants</legend>
            <div className="flex flex-wrap gap-2">
              {AGES.map((a) => (
                <Chip
                  key={a.key}
                  active={ages.includes(a.key)}
                  onClick={() => update({ age: toggle(ages, a.key).join(",") || undefined })}
                >
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
          <div className="flex gap-2.5">
            <Button variant="ghost" block onClick={() => update({ age: undefined, tri: undefined })}>Réinitialiser</Button>
            <Button variant="dark" block onClick={() => setFiltersOpen(false)}>Voir les idées</Button>
          </div>
        </div>
      </Sheet>
    </Page>
  );
}

const toggle = (list: string[], v: string) => (list.includes(v) ? list.filter((x) => x !== v) : [...list, v]);

function IdeasView({ q, catKey, catSlug, ages, sort, onCat, onAges, onRequireAuth, onAi }: {
  q: string; catKey?: string; catSlug?: string; ages: string[]; sort: "popular" | "recent";
  onCat: (slug?: string) => void; onAges: (a: string[]) => void; onRequireAuth: () => void; onAi: () => void;
}) {
  const { data, error, loading, reload } = useAsync(() => api.community.links(catKey, 100), [catKey]);

  const list = useMemo(() => {
    const ranges = AGES.filter((a) => ages.includes(a.key));
    return (data ?? [])
      .filter((l) => matchesQuery(l, q))
      .filter((l) => !ranges.length || ranges.some((r) => matchesAge(l.ageRange, r.min, r.max)))
      .sort(sort === "recent" ? byRecent : byPopularity);
  }, [data, q, ages, sort]);

  // Carte « IA » glissée dans la mosaïque
  const aiSlot = Math.min(3, list.length);

  return (
    <>
      <div className="no-scrollbar -mx-5 flex gap-2 overflow-x-auto px-5 md:-mx-8 md:px-8">
        <Chip active={!catSlug} onClick={() => onCat(undefined)}>Tout</Chip>
        {CATEGORIES.map((c) => (
          <Chip
            key={c.key}
            active={catSlug === c.slug}
            onClick={() => onCat(catSlug === c.slug ? undefined : c.slug)}
            idleClass={`${c.bg} ${c.ink}`}
          >
            {c.plural}
          </Chip>
        ))}
      </div>

      <div className="flex flex-wrap items-center gap-2 text-[13px] text-sec">
        <span className="font-semibold">Âge</span>
        {AGES.map((a) => {
          const on = ages.includes(a.key);
          return (
            <button
              key={a.key}
              type="button"
              aria-pressed={on}
              onClick={() => onAges(toggle(ages, a.key))}
              className={`rounded-full border-[1.5px] px-3 py-1.5 transition-colors ${on ? "border-terracotta bg-white font-semibold text-terracotta-dark" : "border-line-2 hover:bg-sable-2"}`}
            >
              {a.label}
            </button>
          );
        })}
      </div>

      {loading ? (
        <PageSpinner />
      ) : error ? (
        <div className="flex flex-col items-start gap-3">
          <ErrorNote>{error.message}</ErrorNote>
          <Button variant="ghost" size="sm" onClick={reload}>Réessayer</Button>
        </div>
      ) : (
        <>
          <span className="text-[13px] text-sec">
            <strong className="text-ecorce">{list.length} idée{list.length > 1 ? "s" : ""}</strong> partagée{list.length > 1 ? "s" : ""} par la communauté
          </span>
          {list.length === 0 ? (
            <EmptyState
              icon="compass"
              title="Rien trouvé par ici"
              text="Élargissez les filtres ou demandez à l'IA de vous préparer un dossier sur mesure."
              action={<Button variant="ai" icon="sparkles" onClick={onAi}>Demander à l'IA</Button>}
            />
          ) : (
            <div className="columns-2 gap-3 md:columns-3 lg:columns-4">
              {list.map((l, i) => (
                <Fragment key={l.id}>
                  {i === aiSlot && <AiPromo onClick={onAi} />}
                  <GridCard link={l} index={i} onRequireAuth={onRequireAuth} />
                </Fragment>
              ))}
              {aiSlot === list.length && <AiPromo onClick={onAi} />}
            </div>
          )}
        </>
      )}
    </>
  );
}

function AiPromo({ onClick }: { onClick: () => void }) {
  return (
    <div className="mb-3 flex break-inside-avoid flex-col gap-2.5 rounded-[20px] bg-prune p-3.5 text-sable">
      <Icon name="sparkles" size={26} className="text-soleil" />
      <span className="font-display text-base leading-tight font-bold">Pas d'idée ? L'IA prépare un dossier sur mesure.</span>
      <button type="button" onClick={onClick} className="self-start rounded-s bg-soleil px-3 py-2 text-[13px] font-bold text-ecorce transition-colors hover:bg-white">
        Essayer
      </button>
    </div>
  );
}

/** Monté avec `key={q}` : la pagination repart de zéro à chaque recherche. */
function FoldersView({ q }: { q: string }) {
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
      <div className="grid grid-cols-2 gap-3 md:grid-cols-3 lg:grid-cols-4">
        {pages.map((f) => <FolderCard key={f.id} folder={f} showOwner />)}
      </div>
      {token && (
        <Button variant="ghost" className="self-center" loading={loadingMore} onClick={more}>
          Voir plus de dossiers
        </Button>
      )}
    </>
  );
}
