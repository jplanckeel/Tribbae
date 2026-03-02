import { useEffect } from "react";
import { useLocation } from "react-router-dom";

interface SEOHeadProps {
    title?: string;
    description?: string;
    ogTitle?: string;
    ogDescription?: string;
    ogImage?: string;
    ogType?: string;
    canonical?: string;
    noindex?: boolean;
}

const BASE_URL = "https://tribbae.bananaops.cloud";
const DEFAULT_TITLE = "Tribbae — Organisez vos idées en famille";
const DEFAULT_DESCRIPTION =
    "Tribbae est l'application familiale pour organiser et partager vos idées : recettes, activités, cadeaux, événements.";
const DEFAULT_IMAGE = `${BASE_URL}/tribbae.jpg`;

/**
 * Composant SEO qui met à jour dynamiquement le <title> et les meta tags
 * de la page pour chaque route de l'application.
 * 
 * Fonctionne en modifiant le DOM directement car React (SPA) ne modifie
 * pas le <head> nativement.
 */
export default function SEOHead({
    title,
    description,
    ogTitle,
    ogDescription,
    ogImage,
    ogType = "website",
    canonical,
    noindex = false,
}: SEOHeadProps) {
    const location = useLocation();

    const fullTitle = title ? `${title} | Tribbae` : DEFAULT_TITLE;
    const fullDescription = description || DEFAULT_DESCRIPTION;
    const fullCanonical = canonical || `${BASE_URL}${location.pathname}`;
    const fullOgTitle = ogTitle || title || DEFAULT_TITLE;
    const fullOgDescription = ogDescription || fullDescription;
    const fullOgImage = ogImage || DEFAULT_IMAGE;

    useEffect(() => {
        // Title
        document.title = fullTitle;

        // Helper to set/create meta tags
        const setMeta = (attr: string, key: string, content: string) => {
            let el = document.querySelector(`meta[${attr}="${key}"]`) as HTMLMetaElement | null;
            if (!el) {
                el = document.createElement("meta");
                el.setAttribute(attr, key);
                document.head.appendChild(el);
            }
            el.setAttribute("content", content);
        };

        // Meta description
        setMeta("name", "description", fullDescription);

        // Robots
        setMeta("name", "robots", noindex ? "noindex, nofollow" : "index, follow, max-image-preview:large");

        // Open Graph
        setMeta("property", "og:title", fullOgTitle);
        setMeta("property", "og:description", fullOgDescription);
        setMeta("property", "og:image", fullOgImage);
        setMeta("property", "og:url", fullCanonical);
        setMeta("property", "og:type", ogType);

        // Twitter Cards
        setMeta("name", "twitter:title", fullOgTitle);
        setMeta("name", "twitter:description", fullOgDescription);
        setMeta("name", "twitter:image", fullOgImage);

        // Canonical link
        let linkEl = document.querySelector('link[rel="canonical"]') as HTMLLinkElement | null;
        if (!linkEl) {
            linkEl = document.createElement("link");
            linkEl.setAttribute("rel", "canonical");
            document.head.appendChild(linkEl);
        }
        linkEl.setAttribute("href", fullCanonical);
    }, [fullTitle, fullDescription, fullCanonical, fullOgTitle, fullOgDescription, fullOgImage, ogType, noindex]);

    return null;
}
