import { useMemo, type CSSProperties, type ReactNode } from "react";
import { Link as RouterLink } from "react-router";
import { IdeaVisual } from "../components/cards";
import Icon, { type IconName } from "../components/Icon";
import Logo, { TipiMark } from "../components/Logo";
import { Page } from "../components/Shell";
import Seo from "../components/Seo";
import * as api from "../lib/api";
import { CATEGORIES, categoryMeta } from "../lib/categories";
import { byPopularity } from "../lib/links";
import type { Link } from "../lib/types";
import { useAsync } from "../lib/useAsync";

const SIGN_UP = "/connexion?mode=inscription";

/**
 * Accueil d'un visiteur sans compte, façon « frigo de la tribu » : de vraies
 * idées publiées par les familles, épinglées en polaroïds, quelques mots écrits
 * à la main, et une semaine ordinaire dans une tribu pour montrer le partage.
 * Un compte connecté voit l'accueil de sa tribu (Home.tsx).
 */
export default function HomeVisitor() {
  const ideas = useAsync(() => api.community.links(undefined, 60), []);
  const popular = useMemo(() => [...(ideas.data ?? [])].sort(byPopularity), [ideas.data]);
  const pinned = useMemo(() => [...popular.filter((l) => l.imageUrl), ...popular.filter((l) => !l.imageUrl)].slice(0, 3), [popular]);
  const wall = useMemo(() => popular.slice(0, 8), [popular]);

  return (
    <div className="relative overflow-hidden">
      <Seo
        title="Tribbae — Les idées de la famille au même endroit"
        description="Tribbae, l'appli gratuite des idées en famille : sorties, recettes, cadeaux, livres. On les épingle au même endroit, toute la tribu les retrouve, et on pioche dans celles des autres familles."
        path="/"
      />

      <Page className="relative gap-20 pt-5 pb-12 md:gap-28 md:pt-10">
        <div className="md:hidden">
          <Logo />
        </div>

        {/* ── Le frigo ── */}
        <section className="grid items-center gap-12 lg:grid-cols-[1fr_1.05fr] lg:gap-10">
          <div className="flex flex-col gap-7">
            {/* La marque dans le titre principal : c'est ce que Google lit en premier */}
            <h1 className="flex flex-col gap-3">
              <span className="font-hand text-[26px] leading-none text-terracotta">Tribbae, le frigo de la tribu</span>
              <span className="font-display text-[clamp(40px,5.4vw,62px)] leading-[1] font-extrabold tracking-[-2px]">
                Chez nous, les bonnes idées finissaient toujours perdues.
              </span>
            </h1>
            <p className="max-w-[34ch] text-[19px] leading-relaxed text-ecorce-2 md:text-xl">
              Une capture d'écran par-ci, un lien dans la conversation de famille par-là, une recette au dos d'un ticket.
              <strong className="font-semibold text-ecorce"> Tribbae, c'est le frigo de la tribu</strong> : on y épingle
              les idées, et tout le monde les retrouve le jour où il en a besoin.
            </p>
            <div className="flex flex-wrap items-center gap-x-6 gap-y-3">
              <RouterLink
                to={SIGN_UP}
                className="group flex h-14 items-center gap-3 rounded-[18px] bg-terracotta pr-6 pl-3 text-[17px] font-bold text-white shadow-[0_6px_0_var(--color-terracotta-dark)] transition-[transform,box-shadow] hover:-translate-y-0.5 hover:shadow-[0_8px_0_var(--color-terracotta-dark)] active:translate-y-1 active:shadow-[0_2px_0_var(--color-terracotta-dark)]"
              >
                <span className="flex h-9 w-9 items-center justify-center rounded-xl bg-white/20">
                  <TipiMark size={24} stroke={5} />
                </span>
                Monter le tipi de ma tribu
              </RouterLink>
              <RouterLink to="/explorer" className="font-hand text-[22px] text-terracotta underline decoration-2 underline-offset-4 hover:text-terracotta-dark">
                ou fouiller dans le frigo des autres →
              </RouterLink>
            </div>
          </div>
          <Fridge ideas={pinned} />
        </section>

        {/* ── Une semaine dans une tribu ── */}
        <section className="flex flex-col gap-8" aria-labelledby="week">
          <div className="flex flex-col gap-2">
            <span className="font-hand text-2xl text-terracotta">Comment ça se passe, en vrai ?</span>
            <h2 id="week" className="max-w-2xl font-display text-[clamp(30px,4vw,46px)] leading-[1.02] font-extrabold tracking-[-1px]">
              Une semaine ordinaire chez les Martin
            </h2>
          </div>
          <ol className="grid gap-4 md:grid-cols-5 md:gap-0">
            <Day day="Lun." who="Léa" tone="bg-activite-bg text-activite-ink" first chip={<Chip icon="link">instagram.com/p/…</Chip>}>
              voit passer une cabane en carton sur Instagram. Elle colle le lien, Tribbae récupère la photo.
            </Day>
            <Day day="Mer." who="Mamie" tone="bg-cadeau-bg text-cadeau-ink" chip={<Chip icon="livre">Dossier · 8 ans de Jules</Chip>}>
              ajoute <em>Le Petit Prince</em> au dossier « 8 ans de Jules ». Elle est dans la tribu, elle voit tout.
            </Day>
            <Day day="Ven." who="Thomas" tone="bg-recette-bg text-recette-ink" chip={<Chip icon="cart">14 articles à prendre</Chip>}>
              met les crêpes et le gratin au menu, pour 6. La liste de courses se fait toute seule, rangée par rayon.
            </Day>
            <Day day="Sam." who="Toute la tribu" tone="bg-sortie-bg text-sortie-ink" chip={<Chip icon="check">Fait le 14 juin</Chip>}>
              va construire la cabane. On coche « C'est fait » et on garde la photo en souvenir.
            </Day>
            <Day day="Dim." who="Tonton Paul" tone="bg-deco-bg text-deco-ink" last chip={<Chip icon="cadeau">Réservé · surprise</Chip>}>
              n'a pas l'appli. Il ouvre la liste de cadeaux de Jules et réserve le vélo, sans que personne ne sache qui.
            </Day>
          </ol>
        </section>

        {/* ── Le frigo des autres familles ── */}
        <section className="flex flex-col gap-8" aria-labelledby="wall">
          <div className="flex flex-wrap items-end justify-between gap-4">
            <div className="flex flex-col gap-2">
              <span className="font-hand text-2xl text-terracotta">Et quand on manque d'idées…</span>
              <h2 id="wall" className="max-w-2xl font-display text-[clamp(30px,4vw,46px)] leading-[1.02] font-extrabold tracking-[-1px]">
                On pioche dans ce que les autres familles ont épinglé
              </h2>
            </div>
            <RouterLink to="/explorer" className="font-hand text-[22px] text-terracotta underline decoration-2 underline-offset-4 hover:text-terracotta-dark">
              tout voir dans l'Explorer →
            </RouterLink>
          </div>
          {/* Étiquettes des catégories, façon étiqueteuse */}
          <nav aria-label="Catégories" className="flex flex-wrap gap-2.5">
            {CATEGORIES.map((c, i) => (
              <RouterLink
                key={c.key}
                to={`/category/${c.slug}`}
                style={{ "--tilt": `${[-1.5, 1, -0.5, 1.5, -1, 0.5, -1.2][i % 7]}deg` } as CSSProperties}
                className="inline-flex rotate-(--tilt) items-center gap-2 rounded-[4px] bg-ecorce px-3 py-1.5 font-display text-[13px] font-extrabold tracking-[2px] text-sable uppercase shadow-[0_2px_0_rgba(0,0,0,0.25)] transition-transform hover:rotate-0 hover:bg-terracotta"
              >
                <Icon name={c.icon} size={15} />
                {c.plural}
              </RouterLink>
            ))}
          </nav>
          {ideas.loading ? (
            <div className="grid grid-cols-2 gap-6 md:grid-cols-4">
              {[0, 1, 2, 3].map((i) => <div key={i} className="aspect-[4/5] animate-pulse rounded-sm bg-sable-2" />)}
            </div>
          ) : wall.length ? (
            <div className="grid grid-cols-2 gap-x-5 gap-y-8 md:grid-cols-4 md:gap-x-8 md:gap-y-10">
              {wall.map((l, i) => (
                <Polaroid key={l.id} link={l} tilt={[-2.5, 1.8, -1, 2.4, 1.2, -2, 2.6, -1.4][i % 8]} delay={i * 60} />
              ))}
            </div>
          ) : (
            <Note tilt={-1.5} className="max-w-md">
              Le frigo des autres familles est encore vide. Créez votre tribu, et publiez la première idée !
            </Note>
          )}
        </section>

        {/* ── Ceux qui n'ont pas l'appli ── */}
        <section className="grid items-center gap-10 md:grid-cols-[1fr_1.1fr]" aria-labelledby="others">
          <Note tilt={-2} className="order-2 mx-auto w-full max-w-[420px] md:order-1" tone="bg-soleil">
            <span className="block text-[28px] leading-tight">Et Mamie, qui n'a pas l'appli ?</span>
            <span className="mt-3 block text-[22px] leading-snug text-ecorce-2">
              → un lien, et elle voit le dossier.
              <br />→ elle réserve un cadeau, sans compte.
              <br />→ personne ne sait qui a pris quoi.
            </span>
          </Note>
          <div className="order-1 flex flex-col gap-4 md:order-2">
            <h2 id="others" className="font-display text-[clamp(30px,4vw,46px)] leading-[1.02] font-extrabold tracking-[-1px]">
              Le partage ne s'arrête pas à ceux qui ont un compte
            </h2>
            <p className="max-w-[46ch] text-lg leading-relaxed text-ecorce-2">
              Jusqu'à douze personnes dans la tribu voient les mêmes idées. Pour les autres (parrains, amis, voisins), un dossier
              s'envoie par lien, et une liste de cadeaux se réserve sans s'inscrire. La surprise reste entière.
            </p>
          </div>
        </section>

        {/* ── Un mot pour finir ── */}
        <section className="relative mx-auto flex w-full max-w-3xl flex-col items-center gap-6 text-center">
          <span className="relative flex h-28 w-28 items-end justify-center rounded-full bg-soleil pb-3">
            <TipiMark size={72} stroke={4} />
          </span>
          <p className="relative max-w-[30ch] font-display text-[clamp(22px,2.6vw,30px)] leading-snug font-bold tracking-[-0.4px]">
            Tribbae n'est pas une start-up. C'est un projet indépendant, gratuit et sans publicité. Vos idées restent privées
            tant que vous ne décidez pas de les partager.
          </p>
          <div className="relative flex flex-wrap items-center justify-center gap-x-6 gap-y-3">
            <RouterLink
              to={SIGN_UP}
              className="flex h-14 items-center rounded-[18px] bg-terracotta px-7 text-[17px] font-bold text-white shadow-[0_6px_0_var(--color-terracotta-dark)] transition-[transform,box-shadow] hover:-translate-y-0.5 active:translate-y-1 active:shadow-[0_2px_0_var(--color-terracotta-dark)]"
            >
              Monter le tipi de ma tribu
            </RouterLink>
            <RouterLink to="/connexion" className="text-[15px] font-bold text-sec hover:text-ecorce">
              J'ai déjà un compte
            </RouterLink>
          </div>
          <RouterLink to="/a-propos" className="relative font-hand text-xl text-terracotta underline decoration-2 underline-offset-4">
            qui fait Tribbae, et comment ?
          </RouterLink>
        </section>
      </Page>
    </div>
  );
}

/** Le frigo : idées publiées épinglées en polaroïds, un post-it, des aimants. */
function Fridge({ ideas }: { ideas: Link[] }) {
  const slots = [
    { tilt: -4, className: "left-[2%] top-[6%] w-[46%]" },
    { tilt: 3, className: "right-[3%] top-[1%] w-[40%]" },
    { tilt: -1.5, className: "right-[10%] bottom-[4%] w-[42%]" },
  ];
  const fallbacks = ["LINK_CATEGORY_ACTIVITE", "LINK_CATEGORY_RECETTE", "LINK_CATEGORY_LIVRE"];
  return (
    <div
      aria-hidden="true"
      className="relative mx-auto aspect-[1/1.45] w-full max-w-[580px] sm:aspect-[1/0.92] rounded-[34px] bg-sable-2 shadow-[inset_0_2px_0_rgba(255,255,255,0.5),inset_0_-10px_30px_rgba(58,33,27,0.06)]"
      style={{ backgroundImage: "radial-gradient(color-mix(in srgb, var(--color-ecorce) 7%, transparent) 1.2px, transparent 1.4px)", backgroundSize: "18px 18px" }}
    >
      {/* Poignée du frigo */}
      <span className="absolute top-[18%] left-3 h-[34%] w-2.5 rounded-full bg-card shadow-[2px_2px_0_rgba(58,33,27,0.12)]" />
      {slots.map((slot, i) => {
        const l = ideas[i];
        return (
          <div key={l?.id ?? i} className={`absolute ${slot.className}`}>
            {l ? (
              <Polaroid link={l} tilt={slot.tilt} delay={150 + i * 140} compact />
            ) : (
              <FallbackPolaroid category={fallbacks[i]} tilt={slot.tilt} delay={150 + i * 140} />
            )}
          </div>
        );
      })}
      <div className="absolute bottom-[8%] left-[6%] w-[40%]">
        <Note tilt={4} delay={600}>
          samedi&nbsp;: cabane&nbsp;! <span className="text-terracotta">♥</span>
          <span className="mt-1 block text-[17px] text-ecorce-2">— Léa</span>
        </Note>
      </div>
      <Magnet className="top-[44%] left-[48%] bg-terracotta" />
      <Magnet className="top-[6%] right-[44%] bg-activite-ic" />
      <span className="absolute top-[57%] left-[7%] hidden max-w-[40%] rotate-[-5deg] sm:block font-hand text-[clamp(15px,1.6vw,19px)] leading-tight text-sec">Mamie : « on l'a fait, génial »</span>
    </div>
  );
}

function Polaroid({ link, tilt, delay = 0, compact }: { link: Link; tilt: number; delay?: number; compact?: boolean }) {
  const cat = categoryMeta(link.category);
  return (
    <div className="animate-pin" style={{ "--tilt": `${tilt}deg`, animationDelay: `${delay}ms` } as CSSProperties}>
      <RouterLink
        to={`/links/${link.id}`}
        tabIndex={compact ? -1 : undefined}
        className="group relative block rounded-[3px] bg-card p-2.5 pb-3 shadow-[0_10px_24px_rgba(58,33,27,0.16),0_1px_2px_rgba(58,33,27,0.2)] transition-transform duration-300 hover:z-10 hover:-translate-y-1.5 hover:rotate-[calc(var(--tilt)*-0.6)]"
      >
        <Tape />
        <IdeaVisual link={link} height={compact ? 150 : 170} iconSize={40} className="rounded-[2px]" />
        <span className={`mt-2.5 block font-hand leading-[1.1] text-ecorce ${compact ? "text-[19px]" : "text-[21px]"} line-clamp-2`}>{link.title}</span>
        {!compact && <span className={`mt-1 block text-[12px] font-bold tracking-[1px] uppercase ${cat.ink}`}>{cat.label}</span>}
      </RouterLink>
    </div>
  );
}

function FallbackPolaroid({ category, tilt, delay }: { category: string; tilt: number; delay: number }) {
  const cat = categoryMeta(category);
  return (
    <div className="animate-pin" style={{ "--tilt": `${tilt}deg`, animationDelay: `${delay}ms` } as CSSProperties}>
      <div className="relative rounded-[3px] bg-card p-2.5 pb-3 shadow-[0_10px_24px_rgba(58,33,27,0.16)]">
        <Tape />
        <span className={`flex h-[150px] items-center justify-center rounded-[2px] ${cat.bg} ${cat.ic}`}>
          <Icon name={cat.icon} size={44} />
        </span>
        <span className="mt-2.5 block font-hand text-[19px] text-ecorce">{cat.plural}</span>
      </div>
    </div>
  );
}

/** Morceau de scotch en haut d'un polaroïd. */
function Tape() {
  return <span aria-hidden="true" className="absolute -top-3 left-1/2 h-6 w-20 -translate-x-1/2 rotate-[-3deg] bg-soleil/80 shadow-[0_1px_1px_rgba(58,33,27,0.08)]" />;
}

function Magnet({ className }: { className: string }) {
  return <span aria-hidden="true" className={`absolute h-7 w-7 rounded-full shadow-[inset_-3px_-3px_0_rgba(0,0,0,0.18),0_3px_6px_rgba(58,33,27,0.25)] ${className}`} />;
}

/** Post-it écrit à la main. */
function Note({ children, tilt, delay = 0, tone = "bg-idee-bg", className = "" }: { children: ReactNode; tilt: number; delay?: number; tone?: string; className?: string }) {
  return (
    <div className={`animate-pin ${className}`} style={{ "--tilt": `${tilt}deg`, animationDelay: `${delay}ms` } as CSSProperties}>
      <div
        className={`relative p-5 font-hand text-[24px] leading-tight text-ecorce shadow-[0_12px_24px_rgba(58,33,27,0.14)] ${tone}`}
        style={{ clipPath: "polygon(0 0, 100% 0, 100% calc(100% - 22px), calc(100% - 22px) 100%, 0 100%)" }}
      >
        {children}
        {/* Coin corné */}
        <span aria-hidden="true" className="absolute right-0 bottom-0 h-[22px] w-[22px] bg-[linear-gradient(135deg,rgba(58,33,27,0.18)_50%,transparent_50%)]" />
      </div>
    </div>
  );
}

/** Une journée de la semaine : qui, quoi, et l'objet que Tribbae en garde. */
function Day({ day, who, tone, first, last, chip, children }: { day: string; who: string; tone: string; first?: boolean; last?: boolean; chip: ReactNode; children: ReactNode }) {
  return (
    <li
      className={`relative flex flex-col gap-3 rounded-[22px] bg-card p-5 md:rounded-none md:border-l md:border-dashed md:border-dash md:bg-transparent md:px-5 md:py-2 ${first ? "md:border-l-0 md:pl-0" : ""} ${last ? "md:pr-0" : ""}`}
    >
      <span className="font-display text-[15px] font-extrabold tracking-[2px] text-muted uppercase">{day}</span>
      <span className={`w-fit rounded-full px-3 py-1 font-hand text-[20px] leading-none ${tone}`}>{who}</span>
      <p className="text-[15px] leading-relaxed text-ecorce-2">{children}</p>
      {chip}
    </li>
  );
}

function Chip({ icon, children }: { icon: IconName; children: ReactNode }) {
  return (
    <span className="flex w-fit items-center gap-1.5 rounded-lg bg-sable px-2.5 py-1.5 text-[12px] font-semibold text-sec md:bg-card md:shadow-soft">
      <Icon name={icon} size={14} />
      {children}
    </span>
  );
}
