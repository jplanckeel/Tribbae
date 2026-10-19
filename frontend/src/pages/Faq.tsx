import { useNavigate } from "react-router";
import Icon, { type IconName } from "../components/Icon";
import { Page } from "../components/Shell";
import faq from "../../public/faq.json";
import Seo from "../components/Seo";

// Partagée avec le serveur, qui en tire les données schema.org FAQPage de /aide
const SECTIONS = faq as { title: string; icon: IconName; items: [string, string][] }[];

export default function Faq() {
  const navigate = useNavigate();
  return (
    <Page className="max-w-3xl">
      <Seo
        title="Aide & FAQ · Tribbae"
        description="Questions fréquentes sur Tribbae : créer sa tribu, ajouter et ranger des idées, partager un dossier, idées générées par l'IA, confidentialité."
        path="/aide"
      />
      <div className="flex items-center gap-3">
        <button type="button" onClick={() => navigate(-1)} aria-label="Retour" className="flex h-11 w-11 items-center justify-center rounded-full bg-white shadow-soft hover:bg-sable-2">
          <Icon name="chevronLeft" size={22} />
        </button>
        <h1 className="font-display text-[28px] font-extrabold tracking-[-0.6px]">Aide & FAQ</h1>
      </div>
      {SECTIONS.map((s) => (
        <section key={s.title} className="flex flex-col gap-2.5">
          <h2 className="flex items-center gap-2 text-lg font-bold">
            <Icon name={s.icon} size={20} className="text-terracotta" />
            {s.title}
          </h2>
          <div className="flex flex-col divide-y divide-sable-2 overflow-hidden rounded-l bg-white">
            {s.items.map(([q, a]) => (
              <details key={q} className="group">
                <summary className="flex cursor-pointer list-none items-center gap-3 px-4 py-3.5 text-[15px] font-semibold [&::-webkit-details-marker]:hidden">
                  <span className="flex-1">{q}</span>
                  <Icon name="chevronDown" size={18} className="text-muted transition-transform group-open:rotate-180" />
                </summary>
                <p className="px-4 pb-4 text-[15px] leading-relaxed text-ecorce-2">{a}</p>
              </details>
            ))}
          </div>
        </section>
      ))}
      <p className="text-center text-sm text-sec">
        Une autre question ? <a href="mailto:support@tribbae.com" className="font-semibold text-terracotta">support@tribbae.com</a>
      </p>
    </Page>
  );
}
