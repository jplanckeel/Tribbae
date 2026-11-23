import type { IconName } from "../components/Icon";

// Guides (pages qui répondent à une recherche précise) : le contenu est dans
// public/guides.json, partagé avec le serveur qui les rend pour les moteurs et
// les assistants IA (backend/internal/seo/guides.go). Ici, seulement leurs
// adresses et libellés courts (pied de page, routes) : le texte se charge avec
// la page. guides.test.ts vérifie que les deux listes concordent.

export interface Guide {
  slug: string;
  icon: IconName;
  title: string;
  description: string;
  h1: string;
  intro: string;
  ideas?: { category: string; title: string };
  sections: { title: string; paragraphs?: string[]; items?: string[] }[];
  steps?: { title: string; items: string[] };
  faq: [string, string][];
  related?: string[];
}

/** Adresses et libellés courts, dans l'ordre de guides.json. */
export const GUIDE_LINKS = [
  { slug: "application-pour-garder-ses-idees", short: "Garder ses idées et ses liens" },
  { slug: "sauvegarder-recettes-instagram-tiktok", short: "Recettes d'Instagram et TikTok" },
  { slug: "liste-de-cadeaux-partagee", short: "Liste de cadeaux partagée" },
  { slug: "liste-de-courses-partagee", short: "Liste de courses partagée" },
  { slug: "idees-activites-enfants", short: "Activités pour enfants" },
  { slug: "organiser-anniversaire-enfant", short: "Anniversaire d'un enfant" },
];
