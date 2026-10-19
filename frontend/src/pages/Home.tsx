import { useMemo, useState, type FormEvent } from "react";
import { Link as RouterLink, useNavigate } from "react-router";
import { AiCard, CategoryTile, ListCard, Rail, RailCard, RowCard } from "../components/cards";
import { useComposer } from "../components/Composer";
import Icon from "../components/Icon";
import Logo from "../components/Logo";
import { Page } from "../components/Shell";
import Sheet from "../components/Sheet";
import { EmptyState, SectionTitle } from "../components/ui";
import * as api from "../lib/api";
import { CATEGORIES, normalizeCategory } from "../lib/categories";
import { useMyData } from "../lib/data";
import { dayBadge, formatShortDate, formatTime, formatToday, metaLine } from "../lib/format";
import { byPopularity, byRecent, soonReminders, upcoming } from "../lib/links";
import { useSession } from "../lib/session";
import { useAsync } from "../lib/useAsync";
import { useDesktop } from "../lib/useMediaQuery";
import Seo from "../components/Seo";

export default function Home() {
  // Deux mises en page distinctes (maquettes mobile et « Web · Accueil »)
  return useDesktop() ? <HomeDesktop /> : <HomeMobile />;
}

function HomeMobile() {
  const session = useSession();
  const navigate = useNavigate();
  const { openAi } = useComposer();
  const { links, tribe } = useMyData();
  const [q, setQ] = useState("");
  const [remindersOpen, setRemindersOpen] = useState(false);

  const community = useAsync(() => api.community.links(undefined, 40), []);
  const popular = useMemo(() => [...(community.data ?? [])].sort(byPopularity).slice(0, 10), [community.data]);

  const next = useMemo(() => upcoming(links), [links]);
  const reminders = useMemo(() => soonReminders(links), [links]);
  const recent = useMemo(() => [...links].sort(byRecent).slice(0, 8), [links]);
  const counts = useMemo(() => {
    const m = new Map<string, number>();
    for (const l of links) {
      const k = normalizeCategory(l.category);
      m.set(k, (m.get(k) ?? 0) + 1);
    }
    return m;
  }, [links]);

  const search = (e: FormEvent) => {
    e.preventDefault();
    navigate(q.trim() ? `/explorer?q=${encodeURIComponent(q.trim())}` : "/explorer");
  };

  const event = next[0];
  // « La tribu Dupont » : nom choisi pour la tribu, à défaut celui du compte
  const firstName = tribe?.name?.trim() || session?.displayName?.trim();

  return (
    <div className="relative overflow-hidden">
      <Seo
        title="Tribbae — Les idées de la famille au même endroit"
        description="Tribbae rassemble les idées de la famille : sorties, activités, recettes, cadeaux, livres et déco. Rangez-les en dossiers, partagez-les avec votre tribu et piochez dans celles des autres familles."
        path="/"
      />
      {/* Motifs du logo : grand soleil, petit rond et croix */}
      <div aria-hidden="true" className="pointer-events-none absolute -top-[110px] -right-[120px] h-[330px] w-[330px] rounded-full bg-soleil md:right-[8%]" />
      <span aria-hidden="true" className="pointer-events-none absolute top-[118px] right-10 h-[9px] w-[9px] rounded-full border-2 border-terracotta md:right-[22%]" />
      <svg aria-hidden="true" width="12" height="12" viewBox="0 0 12 12" className="pointer-events-none absolute top-[70px] right-[150px] md:right-[30%]">
        <path d="M2 2l8 8M10 2l-8 8" stroke="#3A211B" strokeWidth="2" strokeLinecap="round" />
      </svg>

      <Page className="relative gap-6">
        <div className="flex items-center justify-between md:hidden">
          <Logo />
          {session && <BellButton count={reminders.length} onClick={() => setRemindersOpen(true)} />}
        </div>

        <div className="flex items-end justify-between gap-4">
          <div className="flex flex-col gap-1">
            <span className="text-[15px] text-sec">{formatToday()}</span>
            <h1 className="font-display text-[32px] leading-[1.05] font-extrabold tracking-[-0.8px] md:text-[44px]">
              {firstName ? <>La tribu {firstName},</> : <>Bienvenue dans la tribu,</>}
              <br />
              on fait quoi ce <span className="whitespace-nowrap">week-end ?</span>
            </h1>
          </div>
          {session && (
            <span className="hidden md:block">
              <BellButton count={reminders.length} onClick={() => setRemindersOpen(true)} />
            </span>
          )}
        </div>

        <form onSubmit={search} role="search" className="flex max-w-2xl gap-2">
          <label className="flex h-[52px] flex-1 items-center gap-2.5 rounded-m bg-white px-4 text-muted shadow-card focus-within:ring-2 focus-within:ring-terracotta">
            <Icon name="search" size={20} />
            <span className="sr-only">Rechercher une idée</span>
            <input
              type="search"
              value={q}
              onChange={(e) => setQ(e.target.value)}
              placeholder="Une sortie, une recette, un cadeau…"
              className="min-w-0 flex-1 bg-transparent text-[15px] font-medium text-ecorce outline-none placeholder:text-muted"
            />
          </label>
          <button
            type="button"
            onClick={() => openAi({ prompt: q.trim() || undefined })}
            aria-label="Générer des idées avec l'IA"
            title="Générer des idées avec l'IA"
            className="flex h-[52px] w-[52px] shrink-0 items-center justify-center rounded-m bg-prune text-soleil transition-colors hover:bg-[#4d3848]"
          >
            <Icon name="sparkles" size={22} />
          </button>
        </form>

        {session ? (
          event ? (
            <RouterLink to={`/links/${event.id}`} className="flex max-w-2xl items-center gap-3.5 rounded-l bg-ecorce p-3.5 transition-transform hover:-translate-y-0.5">
              <span className="flex h-16 w-[58px] shrink-0 flex-col items-center justify-center rounded-2xl bg-braise text-ecorce">
                <span className="text-[11px] font-bold tracking-[0.6px] uppercase">{dayBadge(event.at).weekday}</span>
                <span className="font-display text-[26px] leading-none font-extrabold">{dayBadge(event.at).day}</span>
              </span>
              <span className="flex min-w-0 flex-1 flex-col gap-0.5">
                <span className="text-xs font-semibold tracking-[0.8px] text-braise-soft uppercase">
                  À venir{event.reminderEnabled ? " · rappel activé" : ""}
                </span>
                <span className="truncate font-display text-[17px] font-bold text-sable">{event.title}</span>
                <span className="truncate text-[13px] text-bark-soft">
                  {[formatTime(event.at), metaLine(event)].filter(Boolean).join(" · ") || formatShortDate(event.at)}
                </span>
              </span>
              <Icon name="chevronRight" size={20} className="text-bark-soft" />
            </RouterLink>
          ) : (
            <NoEventCard />
          )
        ) : (
          <JoinCard />
        )}

        <section className="flex flex-col gap-3">
          <SectionTitle
            title="Nos catégories"
            action={<RouterLink to="/explorer" className="text-sm font-semibold text-terracotta hover:text-terracotta-dark">Tout voir</RouterLink>}
          />
          <Rail>
            {CATEGORIES.map((c) => (
              <CategoryTile
                key={c.key}
                cat={c}
                count={session ? counts.get(c.key) ?? 0 : undefined}
                to={session ? `/mes-idees?cat=${c.slug}` : `/category/${c.slug}`}
              />
            ))}
          </Rail>
        </section>

        <section className="flex flex-col gap-3">
          <SectionTitle
            title="Ça plaît aux tribus"
            action={<RouterLink to="/explorer" className="text-sm font-semibold text-terracotta hover:text-terracotta-dark">Explorer</RouterLink>}
          />
          {community.loading ? (
            <Rail>
              {[0, 1, 2].map((i) => <div key={i} className="h-[190px] w-[210px] shrink-0 animate-pulse rounded-l bg-sable-2" />)}
            </Rail>
          ) : popular.length ? (
            <Rail>{popular.map((l) => <RailCard key={l.id} link={l} />)}</Rail>
          ) : (
            <p className="text-sm text-sec">La communauté n'a encore rien partagé. Soyez la première tribu !</p>
          )}
        </section>

        {session && (
          <section className="flex flex-col gap-3">
            <SectionTitle
              title="Ajouté récemment"
              action={<RouterLink to="/mes-idees" className="text-sm font-semibold text-terracotta hover:text-terracotta-dark">Mes idées</RouterLink>}
            />
            {recent.length ? (
              <div className="grid gap-2.5 md:grid-cols-2">
                {recent.map((l) => <RowCard key={l.id} link={l} />)}
              </div>
            ) : (
              <EmptyState icon="tipi" title="Votre tipi est vide" text="Ajoutez une première idée avec le bouton « + » ou laissez l'IA vous inspirer." />
            )}
          </section>
        )}
      </Page>

      <UpcomingSheet open={remindersOpen} onClose={() => setRemindersOpen(false)} />
    </div>
  );
}

/** Tout ce qui est prévu (idées datées), avec les rappels. */
function UpcomingSheet({ open, onClose }: { open: boolean; onClose: () => void }) {
  const { links } = useMyData();
  const next = useMemo(() => upcoming(links), [links]);
  return (
    <Sheet open={open} onClose={onClose} title="À venir">
      {next.length ? (
        <ul className="flex flex-col gap-2.5">
          {next.slice(0, 20).map((l) => (
            <li key={l.id}>
              <RowCard
                link={l}
                trailing={
                  <span className="flex shrink-0 flex-col items-end text-xs text-sec">
                    <span className="font-bold text-ecorce">{formatShortDate(l.at)}</span>
                    {l.reminderEnabled && <span className="flex items-center gap-1"><Icon name="bell" size={12} />rappel</span>}
                  </span>
                }
              />
            </li>
          ))}
        </ul>
      ) : (
        <EmptyState icon="calendar" title="Rien de prévu" text="Ajoutez une date à une sortie ou une activité pour la retrouver ici." />
      )}
    </Sheet>
  );
}

// Raccourcis du hero bureau : filtres de l'Explorer, ou l'IA pour les envies ouvertes
const QUICK: { label: string; to?: string; prompt?: string }[] = [
  { label: "Ce week-end", to: "/explorer?quand=weekend" },
  { label: "Gratuit", to: "/explorer?budget=gratuit" },
  { label: "Jour de pluie", prompt: "Des activités d'intérieur pour un jour de pluie" },
  { label: "3–6 ans", to: "/explorer?age=3-6" },
];

/** Accueil bureau (maquette « Web · Accueil »). */
function HomeDesktop() {
  const session = useSession();
  const navigate = useNavigate();
  const { openAi, newLink } = useComposer();
  const { links, tribe } = useMyData();
  const [q, setQ] = useState("");
  const [upcomingOpen, setUpcomingOpen] = useState(false);
  const next = useMemo(() => upcoming(links), [links]);
  // Les listes les plus aimées ; à défaut (aucun j'aime encore), les dernières listes publiques
  const lists = useAsync(
    () =>
      api.community
        .top(7)
        .catch(() => [])
        .then((top) => (top.length ? top : api.community.folders(undefined, 7).then((r) => r.folders ?? []))),
    [],
  );

  const search = (e: FormEvent) => {
    e.preventDefault();
    navigate(q.trim() ? `/explorer?q=${encodeURIComponent(q.trim())}` : "/explorer");
  };
  // « La tribu Dupont » : nom choisi pour la tribu, à défaut celui du compte
  const firstName = tribe?.name?.trim() || session?.displayName?.trim();

  return (
    <Page className="gap-14 pt-6 pb-4">
      <section className="flex flex-wrap items-stretch gap-8">
        <div className="flex min-w-0 flex-[999_1_560px] flex-col gap-[22px] pt-6">
          <span className="text-base font-semibold text-sec">{formatToday()}</span>
          <h1 className="font-display text-[clamp(36px,5vw,60px)] leading-[1.02] font-extrabold tracking-[-1.6px]">
            {firstName ? <>La tribu {firstName},</> : <>Bienvenue dans la tribu,</>}
            <br />
            on fait quoi ce week-end ?
          </h1>
          <form onSubmit={search} role="search" className="flex flex-wrap gap-2.5">
            <label className="flex h-[60px] min-w-0 flex-[1_1_320px] items-center gap-3 rounded-[20px] bg-white px-5 text-muted shadow-[0_4px_16px_rgba(58,33,27,0.08)] focus-within:ring-2 focus-within:ring-terracotta">
              <Icon name="search" size={22} />
              <span className="sr-only">Rechercher une idée</span>
              <input
                type="search"
                value={q}
                onChange={(e) => setQ(e.target.value)}
                placeholder="Une sortie, une recette, un cadeau…"
                className="min-w-0 flex-1 bg-transparent text-[17px] font-medium text-ecorce outline-none placeholder:text-muted"
              />
            </label>
            <button
              type="button"
              onClick={() => openAi({ prompt: q.trim() || undefined })}
              className="flex h-[60px] items-center gap-2.5 rounded-[20px] bg-prune px-5 text-base font-bold text-soleil transition-colors hover:bg-[#4d3848]"
            >
              <Icon name="sparkles" size={22} />
              Demander à l'IA
            </button>
          </form>
          <div className="flex flex-wrap gap-2">
            {QUICK.map((c) =>
              c.to ? (
                <RouterLink key={c.label} to={c.to} className="rounded-full border-[1.5px] border-line-2 bg-white px-3.5 py-[9px] text-sm font-semibold transition-colors hover:border-ecorce">
                  {c.label}
                </RouterLink>
              ) : (
                <button key={c.label} type="button" onClick={() => openAi({ prompt: c.prompt })} className="rounded-full border-[1.5px] border-line-2 bg-white px-3.5 py-[9px] text-sm font-semibold transition-colors hover:border-ecorce">
                  {c.label}
                </button>
              ),
            )}
          </div>
        </div>

        <aside className={`flex min-w-0 flex-[1_1_340px] flex-col gap-4 rounded-[28px] bg-ecorce p-6 text-sable ${session && next.length ? "" : "self-start"}`}>
          <h2 className="font-display text-xl font-bold">À venir pour la tribu</h2>
          {!session ? (
            <>
              <p className="text-[15px] text-bark-soft">Créez votre tribu pour ranger vos sorties datées et recevoir un rappel la veille.</p>
              <RouterLink to="/bienvenue" className="self-start rounded-[14px] bg-braise px-4 py-2.5 text-sm font-bold text-ecorce transition-colors hover:bg-soleil">
                Créer ma tribu
              </RouterLink>
            </>
          ) : next.length ? (
            <>
              {next.slice(0, 3).map((l, i) => {
                const b = dayBadge(l.at);
                return (
                  <RouterLink key={l.id} to={`/links/${l.id}`} className="flex items-center gap-3.5 rounded-[18px] bg-[#4D2F28] p-3 transition-colors hover:bg-[#583830]">
                    <span className={`flex h-[62px] w-[58px] shrink-0 flex-col items-center justify-center rounded-2xl text-ecorce ${i === 0 ? "bg-braise" : "bg-soleil"}`}>
                      <span className="text-[11px] font-bold uppercase">{b.weekday}</span>
                      <span className="font-display text-2xl leading-none font-extrabold">{b.day}</span>
                    </span>
                    <span className="flex min-w-0 flex-col gap-0.5">
                      <span className="truncate text-base font-bold">{l.title}</span>
                      <span className="truncate text-[13px] text-bark-soft">
                        {[formatTime(l.at), metaLine(l)].filter(Boolean).join(" · ") || formatShortDate(l.at)}
                      </span>
                    </span>
                  </RouterLink>
                );
              })}
              <button type="button" onClick={() => setUpcomingOpen(true)} className="self-start text-sm font-bold text-braise-soft hover:text-soleil">
                {next.length > 3 ? `Voir les ${next.length} sorties prévues` : "Voir tout le programme"}
              </button>
            </>
          ) : (
            <>
              <p className="text-[15px] text-bark-soft">Rien de prévu pour l'instant. Ajoutez une sortie datée pour la voir ici et recevoir un rappel.</p>
              <button type="button" onClick={() => newLink({ category: "LINK_CATEGORY_EVENEMENT" })} className="self-start rounded-[14px] bg-braise px-4 py-2.5 text-sm font-bold text-ecorce transition-colors hover:bg-soleil">
                Ajouter une sortie
              </button>
            </>
          )}
        </aside>
      </section>

      <section className="flex flex-col gap-[18px]">
        <DesktopTitle title="Explorer par catégorie" to="/explorer" action="Tout voir" />
        <div className="grid grid-cols-[repeat(auto-fill,minmax(140px,1fr))] gap-3">
          {CATEGORIES.map((c) => (
            <RouterLink
              key={c.key}
              to={`/category/${c.slug}`}
              className={`flex flex-col gap-3.5 rounded-[22px] p-[18px] transition-transform hover:-translate-y-0.5 ${c.bg} ${c.ink}`}
            >
              <Icon name={c.icon} size={30} />
              <span className="text-base font-bold">{c.plural}</span>
            </RouterLink>
          ))}
        </div>
      </section>

      <section className="flex flex-col gap-[18px]">
        <DesktopTitle title="Les listes qui plaisent aux tribus" to="/explorer?vue=dossiers" action="Toutes les listes" />
        <div className="grid grid-cols-[repeat(auto-fill,minmax(260px,1fr))] gap-4">
          {lists.loading
            ? [0, 1, 2].map((i) => <div key={i} className="h-[214px] animate-pulse rounded-[24px] bg-sable-2" />)
            : (lists.data ?? []).map((f) => <ListCard key={f.id} folder={f} />)}
          <AiCard
            title="Une liste sur mesure en 30 secondes"
            text="Décrivez votre envie, l'IA prépare un dossier d'idées."
            onClick={() => openAi()}
          />
        </div>
      </section>

      {session && <RecentSection />}
      <UpcomingSheet open={upcomingOpen} onClose={() => setUpcomingOpen(false)} />
    </Page>
  );
}

function DesktopTitle({ title, to, action }: { title: string; to: string; action: string }) {
  return (
    <div className="flex flex-wrap items-baseline justify-between gap-3">
      <h2 className="font-display text-[28px] font-bold tracking-[-0.5px]">{title}</h2>
      <RouterLink to={to} className="text-[15px] font-bold text-terracotta hover:text-terracotta-dark">{action}</RouterLink>
    </div>
  );
}

/** Dernières idées de la tribu (bureau, sous les listes). */
function RecentSection() {
  const { links } = useMyData();
  const recent = useMemo(() => [...links].sort(byRecent).slice(0, 6), [links]);
  if (!recent.length) return null;
  return (
    <section className="flex flex-col gap-[18px]">
      <DesktopTitle title="Ajouté récemment" to="/mes-idees" action="Mes idées" />
      <div className="grid gap-2.5 md:grid-cols-2 lg:grid-cols-3">
        {recent.map((l) => <RowCard key={l.id} link={l} />)}
      </div>
    </section>
  );
}

function BellButton({ count, onClick }: { count: number; onClick: () => void }) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-label={count ? `À venir, ${count} rappel${count > 1 ? "s" : ""} cette semaine` : "À venir"}
      className="relative flex h-11 w-11 items-center justify-center rounded-full bg-white text-ecorce shadow-soft transition-colors hover:bg-sable-2"
    >
      <Icon name="bell" size={21} />
      {count > 0 && <span className="absolute top-2.5 right-[11px] h-2 w-2 rounded-full border-2 border-white bg-terracotta" />}
    </button>
  );
}

function NoEventCard() {
  const { newLink } = useComposer();
  return (
    <button
      type="button"
      onClick={() => newLink({ category: "LINK_CATEGORY_EVENEMENT" })}
      className="flex max-w-2xl items-center gap-3.5 rounded-l border-2 border-dashed border-dash p-3.5 text-left transition-colors hover:bg-sable-2/60"
    >
      <span className="flex h-16 w-[58px] shrink-0 items-center justify-center rounded-2xl bg-sortie-bg text-sortie-ic">
        <Icon name="calendar" size={26} />
      </span>
      <span className="flex flex-1 flex-col gap-0.5">
        <span className="font-display text-[17px] font-bold">Rien de prévu pour l'instant</span>
        <span className="text-[13px] text-sec">Ajoutez une sortie datée pour la voir ici et recevoir un rappel.</span>
      </span>
      <Icon name="plus" size={20} className="text-terracotta" />
    </button>
  );
}

function JoinCard() {
  return (
    <RouterLink to="/bienvenue" className="flex max-w-2xl items-center gap-3.5 rounded-l bg-ecorce p-4 text-sable transition-transform hover:-translate-y-0.5">
      <span className="flex h-12 w-12 shrink-0 items-center justify-center rounded-2xl bg-braise text-ecorce">
        <Icon name="tribe" size={24} />
      </span>
      <span className="flex flex-1 flex-col gap-0.5">
        <span className="font-display text-[17px] font-bold">Créez votre tribu</span>
        <span className="text-[13px] text-bark-soft">Rangez vos idées, partagez-les avec vos proches.</span>
      </span>
      <Icon name="chevronRight" size={20} className="text-bark-soft" />
    </RouterLink>
  );
}
