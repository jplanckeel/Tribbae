import { Link as RouterLink, useNavigate } from "react-router";
import Icon, { type IconName } from "../components/Icon";
import Seo from "../components/Seo";
import { Page } from "../components/Shell";
import about from "../../public/about.json";

// Partagé avec le serveur, qui en tire le contenu lisible sans JavaScript et
// les données schema.org AboutPage de /a-propos
const ABOUT = about as {
  intro: string;
  contact: string;
  sections: { title: string; icon: IconName; paragraphs?: string[]; items?: string[] }[];
};

export default function About() {
  const navigate = useNavigate();
  return (
    <Page className="max-w-3xl">
      <Seo
        title="À propos de Tribbae"
        description="Pourquoi Tribbae existe, ce que fait l'application et nos principes : gratuite, sans publicité, idées privées par défaut, IA hébergée sur notre serveur."
        path="/a-propos"
      />
      <div className="flex items-center gap-3">
        <button type="button" onClick={() => navigate(-1)} aria-label="Retour" className="flex h-11 w-11 items-center justify-center rounded-full bg-card shadow-soft hover:bg-sable-2">
          <Icon name="chevronLeft" size={22} />
        </button>
        <h1 className="font-display text-[28px] font-extrabold tracking-[-0.6px]">À propos de Tribbae</h1>
      </div>
      <p className="text-[17px] leading-relaxed text-ecorce-2">{ABOUT.intro}</p>

      {ABOUT.sections.map((s) => (
        <section key={s.title} className="flex flex-col gap-2.5 rounded-large bg-card px-5 py-4 text-[15px] leading-relaxed text-ecorce-2">
          <h2 className="flex items-center gap-2 font-display text-lg font-bold text-ecorce">
            <Icon name={s.icon} size={20} className="text-terracotta" />
            {s.title}
          </h2>
          {s.paragraphs?.map((p) => <p key={p}>{p}</p>)}
          {s.items && (
            <ul className="flex flex-col gap-1.5">
              {s.items.map((it) => (
                <li key={it} className="flex gap-2">
                  <Icon name="check" size={18} className="mt-0.5 shrink-0 text-terracotta" />
                  <span>{it}</span>
                </li>
              ))}
            </ul>
          )}
        </section>
      ))}

      <section className="flex flex-col gap-2 rounded-large bg-sable px-5 py-4 text-[15px] leading-relaxed text-ecorce-2">
        <h2 className="font-display text-lg font-bold text-ecorce">Nous écrire</h2>
        <p>
          Une question, une idée d'amélioration ou un souci : écrivez à{" "}
          <a href={`mailto:${ABOUT.contact}`} className="font-semibold text-terracotta">{ABOUT.contact}</a>.
        </p>
        <p className="flex flex-wrap gap-x-4 gap-y-1">
          <RouterLink to="/explorer" className="font-semibold text-terracotta">Explorer les idées</RouterLink>
          <RouterLink to="/aide" className="font-semibold text-terracotta">Aide & FAQ</RouterLink>
          <RouterLink to="/confidentialite" className="font-semibold text-terracotta">Confidentialité</RouterLink>
        </p>
      </section>
    </Page>
  );
}
