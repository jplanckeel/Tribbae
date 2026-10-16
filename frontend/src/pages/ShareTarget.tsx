import { useEffect, useEffectEvent } from "react";
import { Navigate, useSearchParams } from "react-router";
import { useComposer } from "../components/Composer";
import { extractUrl } from "../lib/links";

/**
 * Cible du partage système (Web Share Target, cf. manifest.json) : « Partager
 * → Tribbae » depuis une autre application ouvre le formulaire d'idée
 * pré-rempli, par-dessus l'accueil.
 */
export default function ShareTarget() {
  const [params] = useSearchParams();
  const { newLink } = useComposer();

  const open = useEffectEvent(() => {
    const title = params.get("title")?.trim() ?? "";
    const text = params.get("text")?.trim() ?? "";
    // Selon l'application, l'adresse arrive dans `url` ou au milieu de `text`
    const url = extractUrl(params.get("url")) ?? extractUrl(text);
    if (url) {
      newLink({ url });
    } else {
      // Simple texte partagé : il devient le titre (et la description s'il est long)
      const body = text || title;
      newLink({ title: (title || body).slice(0, 300), description: title && text ? text : "" });
    }
  });
  useEffect(() => open(), []);

  return <Navigate to="/" replace />;
}
