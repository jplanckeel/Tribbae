import { Link as RouterLink, useParams } from "react-router";
import { IdeaVisual } from "../components/cards";
import Icon from "../components/Icon";
import Logo from "../components/Logo";
import { EmptyState, PageSpinner } from "../components/ui";
import * as api from "../lib/api";
import { categoryMeta, folderColor, folderIcon } from "../lib/categories";
import { metaLine, plural, safeHref } from "../lib/format";
import { useSession } from "../lib/session";
import { useAsync } from "../lib/useAsync";

/** Dossier ouvert depuis un lien de partage : lisible sans compte. */
export default function SharedFolder() {
  const { token = "" } = useParams();
  const session = useSession();
  const res = useAsync(() => api.shared.get(token), [token]);

  if (res.loading) return <PageSpinner />;
  if (res.error || !res.data) {
    return (
      <div className="mx-auto max-w-md px-5 pt-16">
        <EmptyState
          icon="link"
          title="Lien de partage invalide"
          text="Ce lien a expiré ou le dossier a été supprimé. Demandez un nouveau lien à votre proche."
          action={<RouterLink to="/" className="font-bold text-terracotta">Découvrir Tribbae</RouterLink>}
        />
      </div>
    );
  }

  const { folder, links = [] } = res.data;
  const color = folderColor(folder.color);

  return (
    <div className="min-h-dvh pb-16">
      <title>{`${folder.name} · partagé sur Tribbae`}</title>
      <header className="relative overflow-hidden px-5 pt-5 pb-8 md:px-8" style={{ background: color.bg }}>
        <div aria-hidden="true" className="absolute -right-24 -bottom-40 h-80 w-80 rounded-full bg-white/45" />
        <div className="relative mx-auto flex max-w-4xl flex-col gap-6">
          <RouterLink to="/" aria-label="Tribbae"><Logo /></RouterLink>
          <div className="flex items-end gap-4">
            <span className="flex h-16 w-16 shrink-0 items-center justify-center rounded-l text-white" style={{ background: color.solid }}>
              <Icon name={folderIcon(folder.icon)} size={30} />
            </span>
            <div className="flex flex-col gap-1">
              <span className="text-sm font-semibold text-ecorce-2">
                {folder.ownerDisplayName ? `La tribu ${folder.ownerDisplayName} vous partage` : "Dossier partagé"}
              </span>
              <h1 className="font-display text-[30px] leading-[1.05] font-extrabold tracking-[-0.6px] md:text-[38px]">{folder.name}</h1>
              <span className="text-sm text-ecorce-2">{plural(links.length, "idée", "idées")}</span>
            </div>
          </div>
        </div>
      </header>

      <div className="mx-auto flex max-w-4xl flex-col gap-3 px-5 pt-6 md:px-8">
        {links.length === 0 ? (
          <EmptyState icon="tipi" title="Ce dossier est encore vide" />
        ) : (
          <ul className="grid gap-3 md:grid-cols-2">
            {links.map((l) => {
              const cat = categoryMeta(l.category);
              const href = safeHref(l.url);
              const meta = metaLine(l);
              return (
                <li key={l.id} className="flex flex-col overflow-hidden rounded-l bg-white shadow-card">
                  <IdeaVisual link={l} height={130} />
                  <div className="flex flex-1 flex-col gap-1.5 p-4">
                    <span className={`self-start rounded-full px-2.5 py-0.5 text-xs font-bold ${cat.bg} ${cat.ink}`}>{cat.label}</span>
                    <span className="font-display text-lg leading-tight font-bold">{l.title}</span>
                    {meta && <span className="text-[13px] text-sec">{meta}</span>}
                    {l.description && <p className="line-clamp-3 text-sm text-ecorce-2">{l.description}</p>}
                    {href && (
                      <a href={href} target="_blank" rel="noopener noreferrer nofollow" className="mt-auto flex items-center gap-1.5 pt-2 text-sm font-bold text-terracotta">
                        Voir le site <Icon name="external" size={16} />
                      </a>
                    )}
                  </div>
                </li>
              );
            })}
          </ul>
        )}

        <div className="mt-6 flex flex-col items-center gap-3 rounded-l bg-ecorce p-6 text-center text-sable">
          <span className="font-display text-xl font-bold">Rangez aussi les idées de votre tribu</span>
          <span className="text-sm text-bark-soft">Sorties, recettes, cadeaux, activités : tout au même endroit, à partager en un lien.</span>
          <RouterLink
            to={session ? "/" : "/bienvenue"}
            className="mt-1 flex h-12 items-center rounded-m bg-terracotta px-5 font-bold text-white hover:bg-terracotta-dark"
          >
            {session ? "Ouvrir Tribbae" : "Créer ma tribu"}
          </RouterLink>
        </div>
      </div>
    </div>
  );
}
