import { useMemo, useState } from "react";
import { Link as RouterLink, useSearchParams } from "react-router";
import { FolderCard, GridCard, PinnedFolderCard, RowCard } from "../components/cards";
import { useComposer } from "../components/Composer";
import Icon from "../components/Icon";
import { Page } from "../components/Shell";
import { Button, EmptyState, ErrorNote, PageSpinner } from "../components/ui";
import { categoryBySlug, normalizeCategory } from "../lib/categories";
import { useMyData } from "../lib/data";
import { plural } from "../lib/format";
import { countdown, keyDateOf } from "../lib/folderStyle";
import { byRecent, tagCounts } from "../lib/links";
import type { Folder } from "../lib/types";
import Seo from "../components/Seo";

const FOLDER_SORTS = [
  { key: "recent", label: "Récents" },
  { key: "date", label: "Date clé" },
  { key: "name", label: "A → Z" },
] as const;
type FolderSort = (typeof FOLDER_SORTS)[number]["key"];

function sortFolders(list: Folder[], sort: FolderSort): Folder[] {
  if (sort === "name") return [...list].sort((a, b) => a.name.localeCompare(b.name, "fr"));
  if (sort === "date") {
    // Prochaine date clé d'abord, puis les dossiers sans date
    const days = (f: Folder) => countdown(keyDateOf(f), f.keyDateType)?.days ?? Infinity;
    return [...list].sort((a, b) => days(a) - days(b) || (b.updatedAt ?? "").localeCompare(a.updatedAt ?? ""));
  }
  return [...list].sort((a, b) => (b.updatedAt ?? "").localeCompare(a.updatedAt ?? ""));
}

const TABS = [
  { key: "dossiers", label: "Dossiers" },
  { key: "favoris", label: "Favoris" },
  { key: "tags", label: "Tags" },
] as const;

export default function MyIdeas() {
  const { links, folders, loading, error, reload } = useMyData();
  const { newFolder, newLink } = useComposer();
  const [params, setParams] = useSearchParams();
  const tab = TABS.find((t) => t.key === params.get("vue"))?.key ?? "dossiers";
  const cat = categoryBySlug(params.get("cat") ?? undefined);
  const tag = params.get("tag") ?? "";
  const [folderSort, setFolderSort] = useState<FolderSort>("recent");

  const setTab = (key: string) => setParams(key === "dossiers" ? {} : { vue: key }, { replace: true });

  const counts = useMemo(() => {
    const m = new Map<string, number>();
    for (const l of links) if (l.folderId) m.set(l.folderId, (m.get(l.folderId) ?? 0) + 1);
    return m;
  }, [links]);
  const loose = links.filter((l) => !l.folderId);
  const pinned = folders.filter((f) => f.pinned);
  const others = sortFolders(folders.filter((f) => !f.pinned), folderSort);
  const tagTotal = useMemo(() => tagCounts(links).length, [links]);
  const tabCounts: Record<string, number> = { dossiers: folders.length, favoris: links.filter((l) => l.favorite).length, tags: tagTotal };
  const recipes = links.filter((l) => normalizeCategory(l.category) === "LINK_CATEGORY_RECETTE" && l.ingredients?.length);
  const ingredientCount = new Set(recipes.flatMap((r) => r.ingredients!.map((i) => i.trim().toLowerCase()))).size;

  if (loading && !links.length && !folders.length) return <PageSpinner />;

  // Vue filtrée par catégorie (depuis les tuiles de l'accueil)
  if (cat) {
    const list = links.filter((l) => normalizeCategory(l.category) === cat.key).sort(byRecent);
    return (
      <Page>
        <Seo title={`Mes ${cat.plural.toLowerCase()} · Tribbae`} noindex />
        <div className="flex items-center gap-3">
          <RouterLink to="/mes-idees" aria-label="Retour à mes idées" className="flex h-11 w-11 items-center justify-center rounded-full bg-white shadow-soft hover:bg-sable-2">
            <Icon name="chevronLeft" size={22} />
          </RouterLink>
          <span className={`flex h-11 w-11 items-center justify-center rounded-[14px] ${cat.bg} ${cat.ic}`}>
            <Icon name={cat.icon} size={22} />
          </span>
          <div className="flex flex-col">
            <h1 className="font-display text-[28px] leading-tight font-extrabold tracking-[-0.6px]">{cat.plural}</h1>
            <span className="text-sm text-sec">{plural(list.length, "idée", "idées")} dans la tribu</span>
          </div>
        </div>
        {list.length ? (
          <div className="columns-2 gap-3 md:columns-3 lg:columns-4">
            {list.map((l, i) => <GridCard key={l.id} link={l} index={i} />)}
          </div>
        ) : (
          <EmptyState
            icon={cat.icon}
            title={`Aucune ${cat.label.toLowerCase()} pour l'instant`}
            action={<Button icon="plus" onClick={() => newLink({ category: cat.key })}>Ajouter</Button>}
          />
        )}
        <RouterLink to={`/category/${cat.slug}`} className="flex items-center gap-2 self-start text-sm font-bold text-terracotta">
          <Icon name="compass" size={18} />
          Voir les {cat.plural.toLowerCase()} de la communauté
        </RouterLink>
      </Page>
    );
  }

  return (
    <Page>
      <Seo title="Mes idées · Tribbae" noindex />
      <div className="flex flex-col gap-0.5">
        <h1 className="font-display text-[32px] font-extrabold tracking-[-0.8px] md:text-[40px]">Mes idées</h1>
        <span className="text-sm text-sec">
          {[plural(links.length, "idée", "idées"), plural(folders.length, "dossier", "dossiers"), plural(tagTotal, "tag", "tags")].join(" · ")}
        </span>
      </div>

      {error && (
        <div className="flex items-center gap-3">
          <ErrorNote>{error.message}</ErrorNote>
          <Button variant="ghost" size="sm" onClick={reload}>Réessayer</Button>
        </div>
      )}

      <div role="tablist" aria-label="Affichage" className="grid h-12 grid-cols-3 gap-1 rounded-2xl bg-sable-2 p-1 md:max-w-md">
        {TABS.map((t) => (
          <button
            key={t.key}
            role="tab"
            aria-selected={tab === t.key}
            onClick={() => setTab(t.key)}
            className={`flex items-center justify-center gap-1.5 rounded-small text-sm transition-colors ${tab === t.key ? "bg-white font-bold shadow-[0_1px_4px_rgba(58,33,27,0.1)]" : "font-semibold text-sec hover:text-ecorce"}`}
          >
            {t.key === "dossiers" && <Icon name="tipi" size={17} className={tab === t.key ? "text-terracotta" : ""} />}
            {t.label}
            {tabCounts[t.key] > 0 && <span className="text-xs font-semibold text-muted">{tabCounts[t.key]}</span>}
          </button>
        ))}
      </div>

      {tab === "dossiers" && (
        <>
          <RouterLink to="/courses" className="flex items-center gap-3 rounded-[18px] bg-white px-3.5 py-3 shadow-card transition-transform hover:-translate-y-0.5 md:max-w-md">
            <span className="flex h-10 w-10 items-center justify-center rounded-small bg-recette-bg text-recette-ic">
              <Icon name="cart" size={20} />
            </span>
            <span className="flex flex-1 flex-col">
              <span className="text-[15px] font-bold">Liste de courses</span>
              <span className="text-[13px] text-sec">
                {recipes.length ? `${plural(ingredientCount, "ingrédient", "ingrédients")} · ${plural(recipes.length, "recette", "recettes")}` : "À partir de vos recettes"}
              </span>
            </span>
            <Icon name="chevronRight" size={20} className="text-muted" />
          </RouterLink>

          {pinned.length > 0 && (
            <section aria-labelledby="pinned-title" className="flex flex-col gap-3">
              <h2 id="pinned-title" className="flex items-center gap-1.5 text-xs font-extrabold tracking-[0.8px] text-sec uppercase">
                <PinGlyph />Épinglés
              </h2>
              <div className="grid gap-3.5 md:grid-cols-2">
                {pinned.map((f) => <PinnedFolderCard key={f.id} folder={f} count={counts.get(f.id) ?? f.linkCount} />)}
              </div>
            </section>
          )}

          <div className="flex items-center justify-between">
            <h2 className="text-xs font-extrabold tracking-[0.8px] text-sec uppercase">Tous les dossiers</h2>
            <label className="flex items-center gap-1 text-[13px] font-semibold text-sec">
              <span className="sr-only">Trier les dossiers</span>
              <select
                value={folderSort}
                onChange={(e) => setFolderSort(e.target.value as FolderSort)}
                className="cursor-pointer appearance-none bg-transparent pr-1 text-right font-semibold outline-none focus-visible:underline"
              >
                {FOLDER_SORTS.map((o) => <option key={o.key} value={o.key}>{o.label}</option>)}
              </select>
              <Icon name="chevronDown" size={14} />
            </label>
          </div>

          <div className="grid grid-cols-2 gap-3 md:grid-cols-3 lg:grid-cols-4">
            {others.map((f) => <FolderCard key={f.id} folder={f} count={counts.get(f.id) ?? f.linkCount} />)}
            {loose.length > 0 && (
              <RouterLink
                to="/dossiers/non-rangees"
                className="relative flex flex-col gap-3.5 overflow-hidden rounded-large bg-white p-3.5 shadow-card transition-transform hover:-translate-y-0.5"
              >
                <span aria-hidden="true" className="absolute -top-3.5 -right-3.5 h-16 w-16 rounded-full bg-sable-2" />
                <span className="relative flex h-11 w-11 items-center justify-center rounded-[14px] bg-sec text-white">
                  <Icon name="bookmark" size={22} />
                </span>
                <span className="flex flex-col gap-0.5">
                  <span className="font-display text-base font-bold">Non rangées</span>
                  <span className="text-[13px] text-sec">{plural(loose.length, "idée", "idées")}</span>
                </span>
              </RouterLink>
            )}
            <button
              type="button"
              onClick={newFolder}
              className="flex min-h-[150px] flex-col items-center justify-center gap-2 rounded-large border-2 border-dashed border-dash text-sm font-bold text-sec transition-colors hover:bg-sable-2/60"
            >
              <span className="flex h-11 w-11 items-center justify-center rounded-[14px] bg-sable-2 text-terracotta">
                <Icon name="plus" size={22} />
              </span>
              Nouveau dossier
            </button>
          </div>
        </>
      )}

      {tab === "favoris" && <Favorites />}
      {tab === "tags" && <Tags selected={tag} onSelect={(t) => setParams(t ? { vue: "tags", tag: t } : { vue: "tags" }, { replace: true })} />}
    </Page>
  );
}

/** Punaise « Épinglés » (maquette Mes idées). */
function PinGlyph({ size = 14 }: { size?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" aria-hidden="true" className="icon text-terracotta">
      <path d="M9 4h6l-1 6 3 3H7l3-3z M12 13v7" />
    </svg>
  );
}

function Favorites() {
  const { links } = useMyData();
  const list = links.filter((l) => l.favorite).sort(byRecent);
  if (!list.length) {
    return (
      <EmptyState
        icon="star"
        title="Pas encore de favoris"
        text="Ouvrez une idée et touchez « Ajouter aux favoris » pour la retrouver ici en un clin d'œil."
      />
    );
  }
  return (
    <div className="columns-2 gap-3 md:columns-3 lg:columns-4">
      {list.map((l, i) => <GridCard key={l.id} link={l} index={i} />)}
    </div>
  );
}

function Tags({ selected, onSelect }: { selected: string; onSelect: (t: string) => void }) {
  const { links } = useMyData();
  const tags = useMemo(() => tagCounts(links), [links]);
  const list = selected ? links.filter((l) => l.tags?.includes(selected)).sort(byRecent) : [];

  if (!tags.length) {
    return <EmptyState icon="tag" title="Aucun tag" text="Ajoutez des tags à vos idées (#automne, #pluie…) pour les retrouver par thème." />;
  }
  return (
    <div className="flex flex-col gap-5">
      <div className="flex flex-wrap gap-2">
        {tags.map(([t, n]) => {
          const on = t === selected;
          return (
            <button
              key={t}
              type="button"
              aria-pressed={on}
              onClick={() => onSelect(on ? "" : t)}
              className={`rounded-[10px] px-3 py-1.5 text-sm transition-colors ${on ? "bg-ecorce font-semibold text-sable" : "bg-sable-2 text-sec hover:bg-line"}`}
            >
              #{t} <span className="opacity-60">{n}</span>
            </button>
          );
        })}
      </div>
      {selected && (
        <div className="grid gap-2.5 md:grid-cols-2">
          {list.map((l) => <RowCard key={l.id} link={l} />)}
        </div>
      )}
    </div>
  );
}
