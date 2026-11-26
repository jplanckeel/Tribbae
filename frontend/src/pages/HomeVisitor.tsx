import { useMemo, type CSSProperties, type ReactNode } from "react";
import { Link as RouterLink } from "react-router";
import { IdeaVisual } from "../components/cards";
import Icon, { type IconName } from "../components/Icon";
import Logo, { TipiMark } from "../components/Logo";
import { Page } from "../components/Shell";
import Seo from "../components/Seo";
import { Avatar } from "../components/ui";
import * as api from "../lib/api";
import { CATEGORIES, categoryMeta } from "../lib/categories";
import { byPopularity } from "../lib/links";
import type { Link } from "../lib/types";
import { useAsync } from "../lib/useAsync";

const SIGN_UP = "/connexion?mode=inscription";

// La tribu de démonstration des écrans (avatars aux couleurs de l'appli)
const TRIBE = [
  { name: "Léa", color: "var(--av-0-bg)", ink: "var(--av-0-ink)" },
  { name: "Mamie", color: "var(--av-1-bg)", ink: "var(--av-1-ink)" },
  { name: "Thomas", color: "var(--av-2-bg)", ink: "var(--av-2-ink)" },
  { name: "Paul", color: "var(--av-3-bg)", ink: "var(--av-3-ink)" },
];

/**
 * Accueil d'un visiteur sans compte : dire tout de suite ce qu'est Tribbae et
 * le montrer. Un téléphone avec un dossier de la tribu (rempli des vraies idées
 * publiées), puis quatre usages avec leur écran (garder, partager, courses,
 * cadeaux), les idées des autres familles et un mot pour finir.
 * Les écrans sont dessinés en HTML avec les styles de l'appli, pas des captures.
 * Un compte connecté voit l'accueil de sa tribu (Home.tsx).
 */
export default function HomeVisitor() {
  const ideas = useAsync(() => api.community.links(undefined, 60), []);
  const popular = useMemo(() => [...(ideas.data ?? [])].sort(byPopularity), [ideas.data]);
  const shown = useMemo(() => [...popular.filter((l) => l.imageUrl), ...popular.filter((l) => !l.imageUrl)].slice(0, 4), [popular]);
  const wall = useMemo(() => popular.slice(0, 8), [popular]);

  return (
    <div className="relative overflow-hidden">
      <Seo
        title="Tribbae — Les idées de la famille au même endroit"
        description="Tribbae, l'appli gratuite des idées en famille : sorties, recettes, cadeaux, livres. On les garde au même endroit, toute la tribu les retrouve, et on pioche dans celles des autres familles."
        path="/"
      />

      <Page className="relative gap-20 pt-5 pb-12 md:gap-28 md:pt-10">
        <div className="md:hidden">
          <Logo />
        </div>

        {/* ── Ce qu'est Tribbae, et l'appli ── */}
        <section className="grid grid-cols-1 items-center gap-12 lg:grid-cols-[minmax(0,1.1fr)_minmax(0,1fr)] lg:gap-10">
          <div className="flex flex-col gap-7">
            {/* La marque dans le titre principal : c'est ce que Google lit en premier */}
            <h1 className="flex flex-col gap-3">
              <span className="font-hand text-[26px] leading-none text-terracotta">Tribbae, l'appli des idées de la famille</span>
              <span className="font-display text-[clamp(36px,5vw,58px)] leading-[1.02] font-extrabold tracking-[-1.5px]">
                Les sorties, recettes, cadeaux et livres de la famille, au même endroit.
              </span>
            </h1>
            <p className="max-w-[40ch] text-[19px] leading-relaxed text-ecorce-2 md:text-xl">
              Une idée vue sur Instagram, dans un magazine ou chez des amis ? Vous la gardez dans Tribbae en deux secondes.
              <strong className="font-semibold text-ecorce"> Toute la tribu la retrouve</strong>, et on coche « C'est fait » le jour où on l'a vécue.
            </p>
            <div className="flex flex-col gap-3">
              <div className="flex flex-wrap items-center gap-x-6 gap-y-3">
                <RouterLink
                  to={SIGN_UP}
                  className="group flex h-14 items-center gap-3 rounded-[18px] bg-terracotta pr-6 pl-3 text-[17px] font-bold text-white shadow-[0_6px_0_var(--color-terracotta-dark)] transition-[transform,box-shadow] hover:-translate-y-0.5 hover:shadow-[0_8px_0_var(--color-terracotta-dark)] active:translate-y-1 active:shadow-[0_2px_0_var(--color-terracotta-dark)]"
                >
                  <span className="flex h-9 w-9 items-center justify-center rounded-xl bg-white/20">
                    <TipiMark size={24} stroke={5} />
                  </span>
                  Créer ma tribu
                </RouterLink>
                <RouterLink to="/explorer" className="font-hand text-[22px] text-terracotta underline decoration-2 underline-offset-4 hover:text-terracotta-dark">
                  ou voir les idées des familles →
                </RouterLink>
              </div>
              <span className="text-sm text-sec">Gratuit, sans publicité · sur Android et sur le web</span>
            </div>
          </div>
          <PhoneFolder ideas={shown} />
        </section>

        {/* ── Ce que fait Tribbae ── */}
        <section className="flex flex-col gap-10" aria-labelledby="what">
          <div className="flex flex-col gap-2">
            <span className="font-hand text-2xl text-terracotta">Concrètement</span>
            <h2 id="what" className="max-w-2xl font-display text-[clamp(30px,4vw,46px)] leading-[1.02] font-extrabold tracking-[-1px]">
              Ce que fait Tribbae pour votre famille
            </h2>
          </div>
          <div className="grid grid-cols-1 gap-5 md:grid-cols-2 md:gap-6">
            <Feature icon="link" tone="bg-activite-bg text-activite-ink" title="Garder une idée en deux secondes" screen={<SaveScreen />}>
              Collez un lien Instagram, TikTok ou d'un site : Tribbae récupère la photo et le titre. Ajoutez une adresse, un prix,
              un âge, et rangez-la dans un dossier.
            </Feature>
            <Feature icon="tribe" tone="bg-cadeau-bg text-cadeau-ink" title="Toute la tribu voit les mêmes idées" screen={<TribeScreen />}>
              Jusqu'à douze personnes : parents, grands-parents, parrains. Chacun ajoute ses idées, commente, et coche « C'est fait ».
            </Feature>
            <Feature icon="cart" tone="bg-recette-bg text-recette-ink" title="La liste de courses se fait toute seule" screen={<ShoppingScreen />}>
              Mettez des recettes au menu : les ingrédients s'ajoutent, rangés par rayon. La liste est partagée en direct avec
              celui qui est au magasin.
            </Feature>
            <Feature icon="cadeau" tone="bg-deco-bg text-deco-ink" title="Des listes de cadeaux sans gâcher la surprise" screen={<GiftScreen />}>
              Envoyez la liste par lien : Tonton Paul réserve le vélo sans créer de compte, et personne ne sait qui a pris quoi.
            </Feature>
          </div>
        </section>

        {/* ── Les idées des autres familles ── */}
        <section className="flex flex-col gap-8" aria-labelledby="wall">
          <div className="flex flex-wrap items-end justify-between gap-4">
            <div className="flex flex-col gap-2">
              <span className="font-hand text-2xl text-terracotta">Et quand on manque d'idées…</span>
              <h2 id="wall" className="max-w-2xl font-display text-[clamp(30px,4vw,46px)] leading-[1.02] font-extrabold tracking-[-1px]">
                On pioche dans les idées des autres familles
              </h2>
            </div>
            <RouterLink to="/explorer" className="font-hand text-[22px] text-terracotta underline decoration-2 underline-offset-4 hover:text-terracotta-dark">
              tout voir dans l'Explorer →
            </RouterLink>
          </div>
          {/* Étiquettes des catégories, façon étiqueteuse, chacune à sa couleur */}
          <nav aria-label="Catégories" className="flex flex-wrap gap-2.5">
            {CATEGORIES.map((c, i) => (
              <RouterLink
                key={c.key}
                to={`/category/${c.slug}`}
                style={{ "--tilt": `${[-1.5, 1, -0.5, 1.5, -1, 0.5, -1.2][i % 7]}deg` } as CSSProperties}
                className={`inline-flex rotate-(--tilt) items-center gap-2 rounded-[4px] px-3 py-1.5 font-display text-[13px] font-extrabold tracking-[2px] uppercase shadow-[0_2px_0_currentColor] transition-transform hover:-translate-y-0.5 hover:rotate-0 hover:shadow-[0_4px_0_currentColor] ${c.bg} ${c.ink}`}
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
            <p className="max-w-md text-lg text-ecorce-2">Aucune idée publiée pour l'instant. Créez votre tribu, et publiez la première !</p>
          )}
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
              Créer ma tribu
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

// ── Écrans de démonstration ──────────────────────────────────────────────────

/** Un téléphone qui affiche un dossier de la tribu, avec de vraies idées publiées. */
function PhoneFolder({ ideas }: { ideas: Link[] }) {
  const rows = ideas.length
    ? ideas.map((l, i) => ({ id: l.id, title: l.title, category: l.category, link: l as Link | undefined, by: TRIBE[i % TRIBE.length], done: i === 1 }))
    : [
        { id: "a", title: "Cabane en carton", category: "LINK_CATEGORY_ACTIVITE", link: undefined, by: TRIBE[0], done: false },
        { id: "b", title: "Crêpes de la Chandeleur", category: "LINK_CATEGORY_RECETTE", link: undefined, by: TRIBE[2], done: true },
        { id: "c", title: "Le Petit Prince", category: "LINK_CATEGORY_LIVRE", link: undefined, by: TRIBE[1], done: false },
        { id: "d", title: "Vélo 20 pouces", category: "LINK_CATEGORY_CADEAU", link: undefined, by: TRIBE[3], done: false },
      ];
  return (
    <div aria-hidden="true" className="relative mx-auto w-full max-w-[330px]">
      <span className="absolute -inset-8 rounded-full bg-[radial-gradient(closest-side,var(--color-soleil),transparent)]" />
      <div className="relative rounded-[46px] border-[10px] border-(--phone-frame) bg-sable shadow-[0_24px_50px_rgba(58,33,27,0.25)]">
        <span className="absolute top-2 left-1/2 h-5 w-24 -translate-x-1/2 rounded-full bg-(--phone-frame)" />
        <div className="flex flex-col gap-3 px-4 pt-10 pb-5">
          <div className="flex items-center gap-2 text-sec">
            <Icon name="chevronLeft" size={18} />
            <span className="text-xs font-semibold">Mes idées</span>
          </div>
          <div className="flex items-center justify-between gap-2">
            <div className="flex min-w-0 items-center gap-2.5">
              <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-small bg-activite-bg text-activite-ic">
                <Icon name="folder" size={22} />
              </span>
              <div className="min-w-0">
                <div className="truncate font-display text-lg leading-tight font-extrabold">Idées de la tribu</div>
                <div className="text-xs text-sec">{rows.length} idées · partagé</div>
              </div>
            </div>
            <AvatarStack />
          </div>
          <div className="flex gap-1.5">
            {["Toutes", "Sorties", "Recettes", "Cadeaux"].map((c, i) => (
              <span key={c} className={`rounded-full px-2.5 py-1 text-[11px] font-bold ${i === 0 ? "bg-ecorce text-sable" : "bg-card text-sec"}`}>{c}</span>
            ))}
          </div>
          <ul className="flex flex-col gap-2">
            {rows.map((r) => {
              const cat = categoryMeta(r.category);
              return (
                <li key={r.id} className="flex items-center gap-3 rounded-medium bg-card p-2 shadow-card">
                  {r.link ? (
                    <IdeaVisual link={r.link} height={52} iconSize={22} className="w-[52px] shrink-0 rounded-small" />
                  ) : (
                    <span className={`flex h-[52px] w-[52px] shrink-0 items-center justify-center rounded-small ${cat.bg} ${cat.ic}`}>
                      <Icon name={cat.icon} size={22} />
                    </span>
                  )}
                  <div className="flex min-w-0 flex-1 flex-col gap-1">
                    <span className="line-clamp-1 text-[13px] leading-tight font-bold">{r.title}</span>
                    <span className="flex items-center gap-1.5 text-[11px]">
                      <span className={`font-bold ${cat.ink}`}>{cat.label}</span>
                      <span className="text-muted">·</span>
                      <Avatar name={r.by.name} color={r.by.color} ink={r.by.ink} size={14} />
                      <span className="truncate text-sec">{r.by.name}</span>
                    </span>
                  </div>
                  {r.done && (
                    <span className="flex shrink-0 items-center gap-1 rounded-full bg-done-bg px-2 py-0.5 text-[10px] font-bold text-done">
                      <Icon name="check" size={11} strokeWidth={3} />
                      Fait
                    </span>
                  )}
                </li>
              );
            })}
          </ul>
          <span className="mx-auto mt-1 flex h-12 w-12 items-center justify-center rounded-[16px] bg-terracotta text-white shadow-nav">
            <Icon name="plus" size={24} strokeWidth={2.6} />
          </span>
        </div>
      </div>
    </div>
  );
}

function AvatarStack({ size = 24 }: { size?: number }) {
  return (
    <span className="flex shrink-0 -space-x-1.5">
      {TRIBE.map((m) => (
        <Avatar key={m.name} name={m.name} color={m.color} ink={m.ink} size={size} className="ring-2 ring-sable" />
      ))}
    </span>
  );
}

/** Un usage : son titre, deux phrases et l'écran qui le montre. */
function Feature({ icon, tone, title, screen, children }: { icon: IconName; tone: string; title: string; screen: ReactNode; children: ReactNode }) {
  return (
    <article className="flex min-w-0 flex-col gap-5 rounded-xl bg-card p-5 shadow-card md:p-6">
      <div className="flex flex-col gap-2.5">
        <span className={`flex h-11 w-11 items-center justify-center rounded-small ${tone}`}>
          <Icon name={icon} size={22} />
        </span>
        <h3 className="font-display text-[22px] leading-tight font-extrabold tracking-[-0.4px]">{title}</h3>
        <p className="text-[15px] leading-relaxed text-ecorce-2">{children}</p>
      </div>
      <div aria-hidden="true" className="mt-auto rounded-large bg-sable p-3.5">
        {screen}
      </div>
    </article>
  );
}

function SaveScreen() {
  return (
    <div className="flex flex-col gap-2.5">
      <div className="flex items-center gap-2 rounded-medium bg-card px-3 py-2.5 text-[13px]">
        <Icon name="link" size={16} className="text-sec" />
        <span className="min-w-0 flex-1 truncate text-sec">instagram.com/p/cabane-carton</span>
        <span className="rounded-full bg-terracotta px-2.5 py-1 text-[11px] font-bold text-white">Ajouter</span>
      </div>
      <div className="flex items-center gap-3 rounded-medium bg-card p-2">
        <span className="flex h-14 w-14 shrink-0 items-center justify-center rounded-small bg-activite-bg text-activite-ic">
          <Icon name="activite" size={24} />
        </span>
        <div className="flex min-w-0 flex-col gap-1">
          <span className="text-[13px] font-bold">Cabane en carton géante</span>
          <span className="flex flex-wrap gap-1 text-[10px] font-bold">
            <span className="rounded-full bg-activite-bg px-2 py-0.5 text-activite-ink">Activité</span>
            <span className="rounded-full bg-sable-2 px-2 py-0.5 text-sec">dès 4 ans</span>
            <span className="rounded-full bg-sable-2 px-2 py-0.5 text-sec">Gratuit</span>
          </span>
        </div>
      </div>
    </div>
  );
}

function TribeScreen() {
  const rows = [
    { title: "Le Petit Prince", cat: "LINK_CATEGORY_LIVRE", by: TRIBE[1], note: "« Il l'adorera »" },
    { title: "Vélo 20 pouces", cat: "LINK_CATEGORY_CADEAU", by: TRIBE[2], note: "2 commentaires" },
  ];
  return (
    <div className="flex flex-col gap-2.5">
      <div className="flex items-center justify-between gap-2 px-1">
        <span className="font-display text-[15px] font-extrabold">Dossier · 8 ans de Jules</span>
        <AvatarStack size={20} />
      </div>
      {rows.map((r) => {
        const cat = categoryMeta(r.cat);
        return (
          <div key={r.title} className="flex items-center gap-3 rounded-medium bg-card p-2">
            <span className={`flex h-10 w-10 shrink-0 items-center justify-center rounded-small ${cat.bg} ${cat.ic}`}>
              <Icon name={cat.icon} size={20} />
            </span>
            <div className="flex min-w-0 flex-1 flex-col gap-0.5">
              <span className="text-[13px] font-bold">{r.title}</span>
              <span className="flex items-center gap-1.5 text-[11px] text-sec">
                <Avatar name={r.by.name} color={r.by.color} ink={r.by.ink} size={14} />
                ajouté par {r.by.name} · {r.note}
              </span>
            </div>
          </div>
        );
      })}
    </div>
  );
}

function ShoppingScreen() {
  const aisles = [
    { name: "Crèmerie", items: [["Lait", "1,5 L", true], ["Beurre", "125 g", false]] },
    { name: "Épicerie", items: [["Farine", "375 g", false], ["Sucre", "75 g", false]] },
  ] as const;
  return (
    <div className="flex flex-col gap-2.5">
      <div className="flex flex-wrap gap-1.5">
        <span className="rounded-full bg-recette-bg px-2.5 py-1 text-[11px] font-bold whitespace-nowrap text-recette-ink">Crêpes · 6 pers.</span>
        <span className="rounded-full bg-recette-bg px-2.5 py-1 text-[11px] font-bold whitespace-nowrap text-recette-ink">Gratin dauphinois</span>
      </div>
      {aisles.map((a) => (
        <div key={a.name} className="rounded-medium bg-card px-3 py-2">
          <span className="text-[10px] font-extrabold tracking-[1.5px] text-muted uppercase">{a.name}</span>
          {a.items.map(([label, qty, done]) => (
            <div key={label} className="flex items-center gap-2.5 py-1 text-[13px]">
              <span className={`flex h-[18px] w-[18px] items-center justify-center rounded-[6px] ${done ? "bg-done-solid text-white" : "border-2 border-dash"}`}>
                {done && <Icon name="check" size={12} strokeWidth={3} />}
              </span>
              <span className={`flex-1 font-semibold ${done ? "text-muted line-through" : ""}`}>{label}</span>
              <span className="text-sec">{qty}</span>
            </div>
          ))}
        </div>
      ))}
    </div>
  );
}

function GiftScreen() {
  const gifts = [
    { title: "Vélo 20 pouces", price: "149 €", reserved: true },
    { title: "Jeu d'échecs enfant", price: "24 €", reserved: false },
  ];
  return (
    <div className="flex flex-col gap-2.5">
      <span className="px-1 font-display text-[15px] font-extrabold">Liste de Jules · 8 ans</span>
      {gifts.map((g) => (
        <div key={g.title} className="flex items-center gap-3 rounded-medium bg-card p-2">
          <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-small bg-cadeau-bg text-cadeau-ic">
            <Icon name="cadeau" size={20} />
          </span>
          <div className="flex min-w-0 flex-1 flex-col">
            <span className="text-[13px] font-bold">{g.title}</span>
            <span className="text-[11px] text-sec">{g.price}</span>
          </div>
          {g.reserved ? (
            <span className="flex items-center gap-1 rounded-full bg-sable-2 px-2.5 py-1 text-[11px] font-bold text-sec">
              <Icon name="lock" size={11} />
              Réservé
            </span>
          ) : (
            <span className="rounded-full bg-terracotta px-2.5 py-1 text-[11px] font-bold text-white">Je réserve</span>
          )}
        </div>
      ))}
      <span className="px-1 text-[11px] text-sec">Sans compte · personne ne sait qui a réservé</span>
    </div>
  );
}

// ── Les idées des autres familles ────────────────────────────────────────────

function Polaroid({ link, tilt, delay = 0 }: { link: Link; tilt: number; delay?: number }) {
  const cat = categoryMeta(link.category);
  return (
    <div className="animate-pin" style={{ "--tilt": `${tilt}deg`, animationDelay: `${delay}ms` } as CSSProperties}>
      <RouterLink
        to={`/links/${link.id}`}
        className="group relative block rounded-[3px] bg-card p-2.5 pb-3 shadow-[0_10px_24px_rgba(58,33,27,0.16),0_1px_2px_rgba(58,33,27,0.2)] transition-transform duration-300 hover:z-10 hover:-translate-y-1.5 hover:rotate-[calc(var(--tilt)*-0.6)]"
      >
        <span aria-hidden="true" className="absolute -top-3 left-1/2 h-6 w-20 -translate-x-1/2 rotate-[-3deg] bg-soleil/80 shadow-[0_1px_1px_rgba(58,33,27,0.08)]" />
        <IdeaVisual link={link} height={170} iconSize={40} className="rounded-[2px]" />
        <span className="mt-2.5 line-clamp-2 font-hand text-[21px] leading-[1.1] text-ecorce">{link.title}</span>
        <span className={`mt-1 block text-[12px] font-bold tracking-[1px] uppercase ${cat.ink}`}>{cat.label}</span>
      </RouterLink>
    </div>
  );
}
