import { Link as RouterLink, Navigate, useLocation, useNavigate } from "react-router";
import { IdeaCard } from "../components/cards";
import Icon from "../components/Icon";
import Seo from "../components/Seo";
import { Page } from "../components/Shell";
import * as api from "../lib/api";
import { categoryMeta } from "../lib/categories";
import guidesData from "../../public/guides.json";
import type { Guide as GuideData } from "../lib/guides";
import { useSession } from "../lib/session";
import { useAsync } from "../lib/useAsync";

const SIGN_UP = "/connexion?mode=inscription";
const GUIDES = guidesData as GuideData[];
const guideBySlug = (slug: string) => GUIDES.find((g) => g.slug === slug);

/**
 * Guide : une page qui répond à une recherche précise (guides.json, rendue
 * aussi par le serveur pour les moteurs et les assistants IA). Contenu,
 * étapes, questions fréquentes, idées publiées de la catégorie, guides liés.
 */
export default function Guide() {
  const slug = useLocation().pathname.replace(/^\/+|\/+$/g, "");
  const g = guideBySlug(slug);
  if (!g) return <Navigate to="/guides" replace />;
  // Clé : un autre guide repart de zéro (idées, défilement)
  return <GuideView key={g.slug} g={g} />;
}

function GuideView({ g }: { g: GuideData }) {
  const session = useSession();
  const navigate = useNavigate();
  const ideas = useAsync(() => (g.ideas ? api.community.links(g.ideas.category, 8) : Promise.resolve([])), [g.slug]);
  const cat = g.ideas ? categoryMeta(g.ideas.category) : undefined;
  const related = (g.related ?? []).map(guideBySlug).filter((x): x is NonNullable<typeof x> => !!x);

  return (
    <Page className="max-w-3xl gap-10 pb-6">
      <Seo title={g.title} description={g.description} path={`/${g.slug}`} />
      <nav aria-label="Fil d'Ariane" className="-mb-6 text-sm text-sec">
        <RouterLink to="/" className="hover:text-ecorce">Accueil</RouterLink> › <RouterLink to="/guides" className="hover:text-ecorce">Guides</RouterLink>
      </nav>

      <header className="flex flex-col gap-5">
        <span className="flex h-14 w-14 items-center justify-center rounded-2xl bg-soleil text-ecorce">
          <Icon name={g.icon} size={28} />
        </span>
        <h1 className="font-display text-[clamp(32px,5vw,50px)] leading-[1.04] font-extrabold tracking-[-1.2px]">{g.h1}</h1>
        <p className="text-lg leading-relaxed text-ecorce-2">{g.intro}</p>
        {!session && (
          <div className="flex flex-wrap items-center gap-x-5 gap-y-2">
            <RouterLink to={SIGN_UP} className="flex h-12 items-center rounded-[16px] bg-terracotta px-5 font-bold text-white shadow-[0_5px_0_var(--color-terracotta-dark)] transition-transform hover:-translate-y-0.5">
              Créer ma tribu, c'est gratuit
            </RouterLink>
            <RouterLink to="/explorer" className="font-bold text-terracotta underline decoration-2 underline-offset-4">
              ou explorer les idées →
            </RouterLink>
          </div>
        )}
      </header>

      {g.sections.map((s) => (
        <section key={s.title} className="flex flex-col gap-3">
          <h2 className="font-display text-[26px] leading-tight font-bold tracking-[-0.4px]">{s.title}</h2>
          {s.paragraphs?.map((p) => <p key={p} className="text-[17px] leading-relaxed text-ecorce-2">{p}</p>)}
          {!!s.items?.length && (
            <ul className="flex flex-col gap-2.5">
              {s.items.map((it) => (
                <li key={it} className="flex gap-3 text-[17px] leading-relaxed text-ecorce-2">
                  <Icon name="check" size={20} className="mt-1 shrink-0 text-done" strokeWidth={2.6} />
                  {it}
                </li>
              ))}
            </ul>
          )}
        </section>
      ))}

      {g.steps && (
        <section className="flex flex-col gap-4 rounded-[26px] bg-card p-6 shadow-soft">
          <h2 className="font-display text-[24px] font-bold">{g.steps.title}</h2>
          <ol className="flex flex-col gap-3">
            {g.steps.items.map((it, i) => (
              <li key={it} className="flex items-start gap-3.5 text-[16px] leading-relaxed text-ecorce-2">
                <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-terracotta font-display font-extrabold text-white">{i + 1}</span>
                <span className="pt-1">{it}</span>
              </li>
            ))}
          </ol>
        </section>
      )}

      {g.ideas && (ideas.data?.length ?? 0) > 0 && (
        <section className="flex flex-col gap-4">
          <div className="flex flex-wrap items-baseline justify-between gap-3">
            <h2 className="font-display text-[26px] font-bold tracking-[-0.4px]">{g.ideas.title}</h2>
            {cat && (
              <RouterLink to={`/category/${cat.slug}`} className="text-[15px] font-bold text-terracotta hover:text-terracotta-dark">
                Toutes les {cat.plural.toLowerCase()} →
              </RouterLink>
            )}
          </div>
          <div className="grid grid-cols-2 gap-3 md:gap-4">
            {ideas.data!.slice(0, 6).map((l) => <IdeaCard key={l.id} link={l} onRequireAuth={() => navigate(SIGN_UP)} />)}
          </div>
        </section>
      )}

      {g.faq.length > 0 && (
        <section className="flex flex-col gap-3">
          <h2 className="font-display text-[26px] font-bold tracking-[-0.4px]">Questions fréquentes</h2>
          <div className="flex flex-col divide-y divide-sable-2 overflow-hidden rounded-large bg-card">
            {g.faq.map(([q, a]) => (
              <details key={q} className="group" open>
                <summary className="flex cursor-pointer list-none items-center gap-3 px-4 py-3.5 text-[16px] font-semibold [&::-webkit-details-marker]:hidden">
                  <h3 className="flex-1">{q}</h3>
                  <Icon name="chevronDown" size={18} className="text-muted transition-transform group-open:rotate-180" />
                </summary>
                <p className="px-4 pb-4 text-[15px] leading-relaxed text-ecorce-2">{a}</p>
              </details>
            ))}
          </div>
        </section>
      )}

      {related.length > 0 && (
        <section className="flex flex-col gap-3">
          <h2 className="font-display text-[22px] font-bold">À lire aussi</h2>
          <div className="grid gap-3 sm:grid-cols-2">
            {related.map((r) => <GuideCard key={r.slug} g={r} />)}
          </div>
        </section>
      )}
    </Page>
  );
}

function GuideCard({ g }: { g: GuideData }) {
  return (
    <RouterLink to={`/${g.slug}`} className="flex items-start gap-3.5 rounded-[22px] bg-card p-4 shadow-soft transition-transform hover:-translate-y-0.5">
      <span className="flex h-11 w-11 shrink-0 items-center justify-center rounded-2xl bg-soleil text-ecorce">
        <Icon name={g.icon} size={22} />
      </span>
      <span className="flex flex-col gap-1">
        <span className="font-display text-[17px] leading-tight font-bold">{g.h1}</span>
        <span className="line-clamp-2 text-[13px] text-sec">{g.description}</span>
      </span>
    </RouterLink>
  );
}

/** /guides : tous les guides. */
export function GuidesIndex() {
  return (
    <Page className="max-w-3xl gap-8 pb-6">
      <Seo
        title="Guides : idées en famille, listes partagées · Tribbae"
        description="Les guides Tribbae : garder ses idées et ses liens, sauvegarder les recettes d'Instagram, listes de cadeaux et de courses partagées, activités pour enfants."
        path="/guides"
      />
      <header className="flex flex-col gap-3">
        <span className="text-sm font-extrabold tracking-[1.5px] text-terracotta uppercase">Pas à pas</span>
        <h1 className="font-display text-[clamp(32px,5vw,48px)] leading-[1.04] font-extrabold tracking-[-1.2px]">Les guides Tribbae</h1>
        <p className="text-lg text-ecorce-2">Comment garder, ranger et partager les idées de la famille.</p>
      </header>
      <div className="grid gap-3 sm:grid-cols-2">
        {GUIDES.map((g) => <GuideCard key={g.slug} g={g} />)}
      </div>
    </Page>
  );
}
