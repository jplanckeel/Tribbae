import { useMemo, useState, type FormEvent, type ReactNode } from "react";
import { Link as RouterLink, useNavigate } from "react-router";
import { IdeaCard, IdeaVisual, ListCard } from "../components/cards";
import Icon, { type IconName } from "../components/Icon";
import Logo from "../components/Logo";
import { Page } from "../components/Shell";
import Seo from "../components/Seo";
import * as api from "../lib/api";
import { CATEGORIES, categoryMeta, normalizeCategory } from "../lib/categories";
import { byPopularity } from "../lib/links";
import type { Link } from "../lib/types";
import { useAsync } from "../lib/useAsync";

const SIGN_UP = "/connexion?mode=inscription";

/**
 * Accueil d'un visiteur sans compte : ce qu'est Tribbae (garder et partager les
 * idées de la famille), les catégories et les idées que les familles ont
 * publiées. Un compte connecté voit l'accueil de sa tribu (Home.tsx).
 */
export default function HomeVisitor() {
  const navigate = useNavigate();
  const [q, setQ] = useState("");
  const ideas = useAsync(() => api.community.links(undefined, 60), []);
  // Les listes les plus aimées, à défaut les dernières publiées
  const lists = useAsync(
    () =>
      api.community
        .top(6)
        .catch(() => [])
        .then((top) => (top.length ? top : api.community.folders(undefined, 6).then((r) => r.folders ?? []))),
    [],
  );

  const popular = useMemo(() => [...(ideas.data ?? [])].sort(byPopularity), [ideas.data]);
  // Pour l'illustration du partage : des idées publiées avec une image
  const pictured = useMemo(() => popular.filter((l) => l.imageUrl).slice(0, 3), [popular]);
  // Exemples par catégorie, tirés des idées publiées
  const examples = useMemo(() => {
    const m = new Map<string, Link[]>();
    for (const l of popular) {
      const k = normalizeCategory(l.category);
      const list = m.get(k) ?? [];
      if (list.length < 2) m.set(k, [...list, l]);
    }
    return m;
  }, [popular]);

  const search = (e: FormEvent) => {
    e.preventDefault();
    navigate(q.trim() ? `/explorer?q=${encodeURIComponent(q.trim())}` : "/explorer");
  };

  return (
    <div className="relative overflow-hidden">
      <Seo
        title="Tribbae — Les idées de la famille au même endroit"
        description="Tribbae, l'appli gratuite des idées en famille : sorties, recettes, cadeaux, livres. Rangez-les en dossiers, partagez-les avec votre tribu et piochez dans celles des autres familles."
        path="/"
      />
      {/* Motif du logo : grand soleil */}
      <div aria-hidden="true" className="pointer-events-none absolute -top-[140px] -right-[140px] h-[380px] w-[380px] rounded-full bg-soleil md:-top-[180px] md:right-[6%] md:h-[520px] md:w-[520px]" />

      <Page className="relative gap-16 pt-5 pb-10 md:gap-24 md:pt-8">
        <div className="md:hidden">
          <Logo />
        </div>

        {/* ── Accroche ── */}
        <section className="grid items-center gap-10 md:grid-cols-[1.1fr_1fr] md:gap-12">
          <div className="flex flex-col gap-6">
            <span className="w-fit rounded-full bg-card px-3.5 py-1.5 text-[13px] font-bold text-sec shadow-soft">
              Gratuit · sans publicité · en français
            </span>
            <h1 className="font-display text-[clamp(38px,6vw,64px)] leading-[1.02] font-extrabold tracking-[-1.6px]">
              Les bonnes idées de la famille, <span className="text-terracotta">partagées</span> au même endroit.
            </h1>
            <p className="max-w-xl text-[17px] leading-relaxed text-ecorce-2 md:text-lg">
              Une sortie repérée, une recette, un cadeau pour Mamie, un livre conseillé : gardez-les en un geste, rangez-les en
              dossiers et partagez-les avec votre tribu. Et piochez dans les idées que les autres familles ont publiées.
            </p>
            <div className="flex flex-wrap gap-3">
              <RouterLink to={SIGN_UP} className="flex h-14 items-center gap-2 rounded-medium bg-terracotta px-6 text-base font-bold text-white transition-colors hover:bg-terracotta-dark">
                <Icon name="tribe" size={20} />
                Créer ma tribu
              </RouterLink>
              <RouterLink to="/explorer" className="flex h-14 items-center gap-2 rounded-medium border-2 border-ecorce px-6 text-base font-bold transition-colors hover:bg-ecorce/5">
                <Icon name="compass" size={20} />
                Explorer les idées
              </RouterLink>
            </div>
            <form onSubmit={search} role="search" className="flex max-w-xl">
              <label className="flex h-14 min-w-0 flex-1 items-center gap-3 rounded-[18px] bg-card px-4 text-muted shadow-soft focus-within:ring-2 focus-within:ring-terracotta">
                <Icon name="search" size={20} />
                <span className="sr-only">Chercher dans les idées publiées</span>
                <input
                  type="search"
                  value={q}
                  onChange={(e) => setQ(e.target.value)}
                  placeholder="Chercher : pique-nique, crêpes, Lego…"
                  className="min-w-0 flex-1 bg-transparent text-base font-medium text-ecorce outline-none placeholder:text-muted"
                />
              </label>
            </form>
          </div>
          <SharingScene ideas={pictured} />
        </section>

        {/* ── Comment ça marche ── */}
        <section className="flex flex-col gap-6" aria-labelledby="how">
          <Title id="how" title="Garder, partager, faire ensemble" />
          <ol className="grid gap-4 md:grid-cols-3">
            <Step n={1} icon="link" tone="bg-activite-bg text-activite-ink" title="Gardez tout, en un geste">
              Collez un lien (site, Instagram, TikTok…), prenez une photo ou écrivez l'idée : Tribbae récupère le titre et
              l'image. Les recettes gardent leurs ingrédients.
            </Step>
            <Step n={2} icon="tribe" tone="bg-cadeau-bg text-cadeau-ink" title="Partagez avec votre tribu">
              Jusqu'à 12 proches voient les mêmes idées, dossiers et profils des enfants. Invitez aussi des amis sur un dossier,
              ou envoyez un lien pour le montrer sans compte.
            </Step>
            <Step n={3} icon="check" tone="bg-recette-bg text-recette-ink" title="Faites-le ensemble">
              Rappel la veille d'une sortie, « C'est fait » et souvenirs, liste de cadeaux où les proches réservent, courses
              tirées des recettes de la semaine.
            </Step>
          </ol>
        </section>

        {/* ── Catégories ── */}
        <section className="flex flex-col gap-6" aria-labelledby="cats">
          <Title id="cats" title="Sept familles d'idées" action={{ to: "/explorer", label: "Tout explorer" }} />
          <div className="grid grid-cols-2 gap-3 md:grid-cols-4">
            {CATEGORIES.map((c) => (
              <RouterLink
                key={c.key}
                to={`/category/${c.slug}`}
                className={`group flex flex-col gap-3 rounded-[22px] p-4 transition-transform hover:-translate-y-0.5 md:p-5 ${c.bg} ${c.ink}`}
              >
                <span className="flex items-center justify-between">
                  <span className="flex h-11 w-11 items-center justify-center rounded-2xl bg-card/70">
                    <Icon name={c.icon} size={24} />
                  </span>
                  <Icon name="arrowRight" size={18} className="opacity-60 transition-transform group-hover:translate-x-0.5" />
                </span>
                <span className="font-display text-lg leading-tight font-bold">{c.plural}</span>
                <span className="line-clamp-2 text-[13px] leading-snug opacity-80">
                  {(examples.get(c.key) ?? []).map((l) => l.title).join(" · ") || c.intro}
                </span>
              </RouterLink>
            ))}
            <RouterLink to="/explorer" className="flex flex-col justify-between gap-3 rounded-[22px] bg-ecorce p-4 text-sable transition-transform hover:-translate-y-0.5 md:p-5">
              <Icon name="sparkles" size={24} className="text-soleil" />
              <span className="font-display text-lg leading-tight font-bold">Une envie précise ?</span>
              <span className="text-[13px] text-bark-soft">Âge, budget, « ce week-end » : filtrez dans l'Explorer.</span>
            </RouterLink>
          </div>
        </section>

        {/* ── Idées publiques ── */}
        <section className="flex flex-col gap-6" aria-labelledby="public">
          <Title
            id="public"
            title="Partagées par les familles"
            text="Des idées que des tribus ont choisi de rendre publiques. Gardez celles qui vous plaisent dans vos propres dossiers."
            action={{ to: "/explorer", label: "Voir toutes les idées" }}
          />
          {ideas.loading ? (
            <div className="grid grid-cols-2 gap-3 md:grid-cols-4 md:gap-4">
              {[0, 1, 2, 3].map((i) => <div key={i} className="h-[260px] animate-pulse rounded-[22px] bg-sable-2" />)}
            </div>
          ) : popular.length ? (
            <div className="grid grid-cols-2 gap-3 md:grid-cols-4 md:gap-4">
              {popular.slice(0, 8).map((l) => <IdeaCard key={l.id} link={l} onRequireAuth={() => navigate(SIGN_UP)} />)}
            </div>
          ) : (
            <p className="rounded-large bg-card px-5 py-6 text-center text-[15px] text-sec">
              Les familles n'ont encore rien publié. Créez votre tribu et partagez la première idée !
            </p>
          )}
        </section>

        {/* ── Listes publiques ── */}
        {(lists.data?.length ?? 0) > 0 && (
          <section className="flex flex-col gap-6" aria-labelledby="lists">
            <Title id="lists" title="Les listes qui plaisent aux tribus" action={{ to: "/explorer?vue=dossiers", label: "Toutes les listes" }} />
            <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
              {lists.data!.slice(0, 6).map((f) => <ListCard key={f.id} folder={f} />)}
            </div>
          </section>
        )}

        {/* ── Partager, même sans compte ── */}
        <section className="ecorce-panel relative overflow-hidden rounded-[32px] bg-ecorce px-6 py-9 text-sable md:px-12 md:py-14" aria-labelledby="share">
          <div aria-hidden="true" className="absolute -right-24 -bottom-24 h-72 w-72 rounded-full bg-braise/25" />
          <div className="relative flex flex-col gap-8">
            <div className="flex max-w-2xl flex-col gap-3">
              <h2 id="share" className="font-display text-[clamp(26px,3.4vw,38px)] leading-tight font-extrabold tracking-[-0.6px]">
                Partagez aussi avec ceux qui n'ont pas Tribbae
              </h2>
              <p className="text-[16px] text-bark-soft">Grands-parents, parrains, amis : ils voient, réservent et cochent sans créer de compte.</p>
            </div>
            <div className="grid gap-4 md:grid-cols-3">
              <Feature icon="link" title="Un lien vers un dossier">
                « Vacances en Bretagne », « Idées pour l'anniversaire de Léo » : le dossier s'ouvre dans le navigateur, sans compte.
              </Feature>
              <Feature icon="cadeau" title="Une liste de cadeaux">
                Les proches réservent un cadeau sans que vous sachiez qui : la surprise reste entière, et pas de doublon.
              </Feature>
              <Feature icon="cart" title="Les courses à plusieurs">
                Une seule liste pour la tribu, rangée par rayon : l'un prépare le menu, l'autre coche en magasin.
              </Feature>
            </div>
          </div>
        </section>

        {/* ── Principes ── */}
        <section aria-label="Nos principes" className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          <Principle icon="lock" title="Privé par défaut">Rien n'est public sans votre choix, idée par idée.</Principle>
          <Principle icon="heart" title="Gratuit, sans publicité">Ni revente de données, ni profilage, ni cookie.</Principle>
          <Principle icon="sparkles" title="Une IA chez nous">Elle propose des idées sans envoyer vos demandes ailleurs.</Principle>
          <Principle icon="globe" title="Web et Android">Les mêmes idées partout, même hors connexion sur Android.</Principle>
        </section>

        {/* ── Appel final ── */}
        <section className="flex flex-col items-center gap-5 rounded-[32px] bg-card px-6 py-10 text-center shadow-card md:py-14">
          <Logo />
          <h2 className="max-w-xl font-display text-[clamp(26px,3.4vw,38px)] leading-tight font-extrabold tracking-[-0.6px]">
            Votre tribu n'attend plus que vous
          </h2>
          <p className="max-w-lg text-[16px] text-sec">Créez-la en quelques secondes, puis invitez vos proches par email.</p>
          <div className="flex flex-wrap justify-center gap-3">
            <RouterLink to={SIGN_UP} className="flex h-14 items-center rounded-medium bg-terracotta px-7 text-base font-bold text-white transition-colors hover:bg-terracotta-dark">
              Créer ma tribu
            </RouterLink>
            <RouterLink to="/connexion" className="flex h-14 items-center rounded-medium px-5 text-base font-bold text-terracotta hover:text-terracotta-dark">
              J'ai déjà un compte
            </RouterLink>
          </div>
          <RouterLink to="/a-propos" className="text-sm font-semibold text-sec underline-offset-2 hover:underline">
            En savoir plus sur Tribbae
          </RouterLink>
        </section>
      </Page>
    </div>
  );
}

/**
 * Illustration du partage : un dossier de la tribu, ses membres et quelques
 * idées publiées (vraies images quand il y en a), avec des bulles d'activité.
 */
function SharingScene({ ideas }: { ideas: Link[] }) {
  const tiles = [0, 1, 2].map((i) => ideas[i]);
  const fallbacks = ["LINK_CATEGORY_EVENEMENT", "LINK_CATEGORY_RECETTE", "LINK_CATEGORY_CADEAU"] as const;
  return (
    <div aria-hidden="true" className="relative mx-auto w-full max-w-[460px] pt-6 pb-10 md:pt-4">
      <div className="relative z-10 flex flex-col gap-4 rounded-[28px] bg-card p-5 shadow-[0_18px_50px_rgba(58,33,27,0.16)]">
        <div className="flex items-center gap-3">
          <span className="flex h-12 w-12 items-center justify-center rounded-2xl bg-sortie-bg text-sortie-ic">
            <Icon name="folder" size={24} />
          </span>
          <span className="flex min-w-0 flex-1 flex-col">
            <span className="font-display text-lg font-bold">Vacances d'été</span>
            <span className="text-[13px] text-sec">Dans 24 jours · 12 idées</span>
          </span>
          <span className="flex -space-x-2">
            {[["M", "bg-cadeau-bg text-cadeau-ink"], ["L", "bg-activite-bg text-activite-ink"], ["C", "bg-recette-bg text-recette-ink"]].map(([n, c]) => (
              <span key={n} className={`flex h-8 w-8 items-center justify-center rounded-full font-display text-sm font-extrabold ring-2 ring-card ${c}`}>{n}</span>
            ))}
          </span>
        </div>
        <div className="grid grid-cols-3 gap-2.5">
          {tiles.map((l, i) => {
            const cat = categoryMeta(l?.category ?? fallbacks[i]);
            return (
              <div key={l?.id ?? i} className="flex flex-col gap-1.5">
                {l ? (
                  <IdeaVisual link={l} height={92} iconSize={30} className="rounded-2xl" />
                ) : (
                  <span className={`flex h-[92px] items-center justify-center rounded-2xl ${cat.bg} ${cat.ic}`}>
                    <Icon name={cat.icon} size={30} />
                  </span>
                )}
                <span className="line-clamp-2 text-xs leading-tight font-semibold text-ecorce-2">{l?.title ?? cat.label}</span>
              </div>
            );
          })}
        </div>
        <div className="flex items-center gap-2 rounded-2xl bg-sable px-3.5 py-2.5 text-[13px] text-ecorce-2">
          <Icon name="people" size={16} className="text-terracotta" />
          Partagé avec la tribu et 2 amis
        </div>
      </div>
      <Bubble className="-top-3 -left-2 rotate-[-3deg] md:-top-5 md:-left-10">
        <b>Mamie</b> a ajouté une idée
      </Bubble>
      <Bubble className="right-[-6px] bottom-1 rotate-[2deg] md:right-[-28px]">
        <b>Léo</b> : « On y va samedi ? »
      </Bubble>
    </div>
  );
}

function Bubble({ className, children }: { className: string; children: ReactNode }) {
  return (
    <span className={`absolute z-20 rounded-full bg-ecorce px-4 py-2 text-[13px] whitespace-nowrap text-sable shadow-card ${className}`}>{children}</span>
  );
}

function Title({ id, title, text, action }: { id: string; title: string; text?: string; action?: { to: string; label: string } }) {
  return (
    <div className="flex flex-wrap items-end justify-between gap-3">
      <div className="flex max-w-2xl flex-col gap-2">
        <h2 id={id} className="font-display text-[clamp(26px,3.2vw,34px)] leading-tight font-bold tracking-[-0.5px]">{title}</h2>
        {text && <p className="text-[15px] text-sec">{text}</p>}
      </div>
      {action && (
        <RouterLink to={action.to} className="flex items-center gap-1 text-[15px] font-bold text-terracotta hover:text-terracotta-dark">
          {action.label}
          <Icon name="arrowRight" size={16} />
        </RouterLink>
      )}
    </div>
  );
}

function Step({ n, icon, tone, title, children }: { n: number; icon: IconName; tone: string; title: string; children: ReactNode }) {
  return (
    <li className="flex flex-col gap-3 rounded-[24px] bg-card p-5 shadow-soft md:p-6">
      <span className="flex items-center gap-3">
        <span className={`flex h-12 w-12 items-center justify-center rounded-2xl ${tone}`}><Icon name={icon} size={24} /></span>
        <span className="font-display text-sm font-extrabold text-muted">0{n}</span>
      </span>
      <h3 className="font-display text-xl leading-tight font-bold">{title}</h3>
      <p className="text-[15px] leading-relaxed text-ecorce-2">{children}</p>
    </li>
  );
}

function Feature({ icon, title, children }: { icon: IconName; title: string; children: ReactNode }) {
  return (
    <div className="flex flex-col gap-2.5 rounded-[22px] bg-(--panel-row) p-5">
      <span className="flex h-11 w-11 items-center justify-center rounded-2xl bg-braise text-ecorce"><Icon name={icon} size={22} /></span>
      <h3 className="font-display text-lg font-bold">{title}</h3>
      <p className="text-[14px] leading-relaxed text-bark-soft">{children}</p>
    </div>
  );
}

function Principle({ icon, title, children }: { icon: IconName; title: string; children: ReactNode }) {
  return (
    <div className="flex items-start gap-3 rounded-[20px] bg-card/70 p-4">
      <Icon name={icon} size={22} className="mt-0.5 shrink-0 text-terracotta" />
      <span className="flex flex-col gap-0.5">
        <span className="text-[15px] font-bold">{title}</span>
        <span className="text-[13px] text-sec">{children}</span>
      </span>
    </div>
  );
}
