import { useMemo, useState, type FormEvent } from "react";
import { Link as RouterLink, useNavigate } from "react-router";
import { CategoryTile, Rail, RailCard, RowCard } from "../components/cards";
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

export default function Home() {
  const session = useSession();
  const navigate = useNavigate();
  const { openAi } = useComposer();
  const { links } = useMyData();
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
  const firstName = session?.displayName?.trim();

  return (
    <div className="relative overflow-hidden">
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

      <Sheet open={remindersOpen} onClose={() => setRemindersOpen(false)} title="À venir">
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
    </div>
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
