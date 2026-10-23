import { useLayoutEffect } from "react";
import { clip } from "../lib/seo";

const MAX_DESCRIPTION = 160;
const INDEX = "index, follow, max-image-preview:large, max-snippet:-1";

interface SeoProps {
  title: string;
  description?: string;
  /** Adresse canonique sans domaine (ex. `/links/123`) ; absente : pas de canonique. */
  path?: string;
  /** Écrans privés, recherches, liens de partage : à ne pas indexer. */
  noindex?: boolean;
}

/**
 * En-tête d'une page (titre, description, canonique, indexation), tenu à jour
 * pendant la navigation. Le serveur rend le même en-tête pour la première
 * adresse (backend/internal/seo) : ses balises `data-seo` sont retirées dès
 * que la page affiche le sien, pour ne jamais en avoir deux.
 */
export default function Seo({ title, description, path, noindex }: SeoProps) {
  useLayoutEffect(() => {
    document.querySelectorAll("head [data-seo]").forEach((el) => el.remove());
  }, []);
  return (
    <>
      <title>{title}</title>
      {description && <meta name="description" content={clip(description, MAX_DESCRIPTION)} />}
      {path && !noindex && <link rel="canonical" href={`${window.location.origin}${path}`} />}
      <meta name="robots" content={noindex ? "noindex, follow" : INDEX} />
    </>
  );
}
