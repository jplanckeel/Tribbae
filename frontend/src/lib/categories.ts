import type { IconName } from "../components/Icon";
import type { CategoryKey } from "./types";

export interface CategoryMeta {
  key: CategoryKey;
  slug: string;
  label: string;
  plural: string;
  /** Présentation de la page publique de la catégorie (mêmes textes que backend/internal/seo). */
  intro: string;
  icon: IconName;
  /** Classes Tailwind : fond pastel, encre (texte), couleur d'icône. */
  bg: string;
  ink: string;
  ic: string;
  /** Couleur d'icône brute (pour les styles inline : fonds pleins). */
  solid: string;
}

// Ordre d'affichage de la maquette : Activités, Cadeaux, Recettes, Sorties, Idées, Livres, Déco
export const CATEGORIES: CategoryMeta[] = [
  { key: "LINK_CATEGORY_ACTIVITE", slug: "activite", label: "Activité", plural: "Activités", intro: "Activités à faire en famille : jeux, bricolages, sorties nature et idées pour occuper les enfants, repérées et partagées par les familles de Tribbae.", icon: "activite", bg: "bg-activite-bg", ink: "text-activite-ink", ic: "text-activite-ic", solid: "#2F7393" },
  { key: "LINK_CATEGORY_CADEAU", slug: "cadeau", label: "Cadeau", plural: "Cadeaux", intro: "Idées de cadeaux pour enfants, ados et parents : des trouvailles testées et partagées par les familles de Tribbae.", icon: "cadeau", bg: "bg-cadeau-bg", ink: "text-cadeau-ink", ic: "text-cadeau-ic", solid: "#C35A1C" },
  { key: "LINK_CATEGORY_RECETTE", slug: "recette", label: "Recette", plural: "Recettes", intro: "Recettes faciles à cuisiner en famille et avec les enfants : goûters, repas et desserts partagés par les familles de Tribbae.", icon: "recette", bg: "bg-recette-bg", ink: "text-recette-ink", ic: "text-recette-ic", solid: "#4C7A3C" },
  { key: "LINK_CATEGORY_EVENEMENT", slug: "sortie", label: "Sortie", plural: "Sorties", intro: "Sorties et événements en famille : spectacles, visites, fêtes et balades repérés par les familles de Tribbae.", icon: "calendar", bg: "bg-sortie-bg", ink: "text-sortie-ink", ic: "text-sortie-ic", solid: "#B2412A" },
  { key: "LINK_CATEGORY_IDEE", slug: "idee", label: "Idée", plural: "Idées", intro: "Idées pour la vie de famille : astuces, inspirations et bons plans partagés par les familles de Tribbae.", icon: "idee", bg: "bg-idee-bg", ink: "text-idee-ink", ic: "text-idee-ic", solid: "#9A6510" },
  { key: "LINK_CATEGORY_LIVRE", slug: "livre", label: "Livre", plural: "Livres", intro: "Livres pour enfants et ados : albums, romans et BD recommandés par les familles de Tribbae.", icon: "livre", bg: "bg-livre-bg", ink: "text-livre-ink", ic: "text-livre-ic", solid: "#5E4558" },
  { key: "LINK_CATEGORY_DECORATION", slug: "deco", label: "Déco", plural: "Déco", intro: "Idées déco pour la maison et la chambre des enfants, partagées par les familles de Tribbae.", icon: "deco", bg: "bg-deco-bg", ink: "text-deco-ink", ic: "text-deco-ic", solid: "#9C3A5C" },
];

const BY_KEY = new Map(CATEGORIES.map((c) => [c.key, c]));
// Anciennes URLs (/category/evenement, /category/decoration) conservées pour le référencement
const SLUG_ALIASES: Record<string, string> = { evenement: "sortie", decoration: "deco" };
const NUM_TO_KEY: CategoryKey[] = [
  "LINK_CATEGORY_IDEE", "LINK_CATEGORY_IDEE", "LINK_CATEGORY_CADEAU", "LINK_CATEGORY_ACTIVITE",
  "LINK_CATEGORY_EVENEMENT", "LINK_CATEGORY_RECETTE", "LINK_CATEGORY_LIVRE", "LINK_CATEGORY_DECORATION",
];

/** Normalise une catégorie (nom d'enum, numéro ou valeur vide) en clé connue. */
export function normalizeCategory(cat: unknown): CategoryKey {
  if (typeof cat === "number") return NUM_TO_KEY[cat] ?? "LINK_CATEGORY_IDEE";
  if (typeof cat === "string") {
    if (/^\d+$/.test(cat)) return NUM_TO_KEY[Number(cat)] ?? "LINK_CATEGORY_IDEE";
    if (BY_KEY.has(cat as CategoryKey)) return cat as CategoryKey;
  }
  return "LINK_CATEGORY_IDEE";
}

export function categoryMeta(cat: unknown): CategoryMeta {
  return BY_KEY.get(normalizeCategory(cat))!;
}

export function categoryBySlug(slug: string | undefined): CategoryMeta | undefined {
  if (!slug) return undefined;
  const s = SLUG_ALIASES[slug] ?? slug;
  return CATEGORIES.find((c) => c.slug === s);
}

// ── Dossiers : couleurs et icônes partagées avec l'app Android (FolderColor / FolderIcon) ──

export interface FolderColorMeta {
  key: string;
  label: string;
  /** Fond pastel, teinte secondaire (motifs, dessins) et encre (icône, pastilles). */
  bg: string;
  mid: string;
  solid: string;
}

// Palette de la maquette « Personnaliser un dossier » (ui/campfire/FolderStyle.kt côté Android)
export const FOLDER_COLORS: FolderColorMeta[] = [
  { key: "ORANGE", label: "Orange", bg: "#FCE3D0", mid: "#F8CDAE", solid: "#A04812" },
  { key: "RED", label: "Corail", bg: "#F8DCD3", mid: "#F2C1B2", solid: "#9A3420" },
  { key: "YELLOW", label: "Miel", bg: "#FBEACB", mid: "#F6DAA3", solid: "#85560C" },
  { key: "GREEN", label: "Vert", bg: "#E2EDD9", mid: "#CCE0BE", solid: "#3E6630" },
  { key: "TEAL", label: "Sarcelle", bg: "#D5ECE8", mid: "#B5DDD6", solid: "#1F6B60" },
  { key: "BLUE", label: "Bleu", bg: "#DCEDF3", mid: "#BFE0EC", solid: "#235C77" },
  { key: "PURPLE", label: "Prune", bg: "#ECE1EA", mid: "#DCC8D8", solid: "#5E4558" },
  { key: "PINK", label: "Rose", bg: "#F7DDE6", mid: "#EFC3D3", solid: "#9C3A5C" },
  { key: "SABLE", label: "Sable", bg: "#F1E3D1", mid: "#E6D3C0", solid: "#6B5047" },
];

export function folderColor(key: string | undefined): FolderColorMeta {
  return FOLDER_COLORS.find((c) => c.key === key) ?? FOLDER_COLORS[0];
}

// Icônes des dossiers (mêmes clés que FolderIcon côté Android), tracés « trait » viewBox 24
export const FOLDER_ICONS: { key: string; label: string; d: string }[] = [
  { key: "CAMPING", label: "Camping", d: "M12 3L21 20H3z M12 11l4 9H8z" },
  { key: "FOREST", label: "Forêt", d: "M12 3l5 7h-3l4 6H6l4-6H7z M12 16v5" },
  { key: "MOUNTAIN", label: "Montagne", d: "M3 20l6-11 4 6 3-4 5 9z" },
  { key: "BEACH", label: "Plage", d: "M4 20h16 M12 8v12 M5 9a7 7 0 0 1 14 0z" },
  { key: "SPORTS", label: "Sport", d: "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18z M12 7l4 3-1.5 4.5h-5L8 10z" },
  { key: "MUSIC", label: "Musique", d: "M9 18V6l10-2v12 M6 21a3 3 0 1 0 0-6a3 3 0 1 0 0 6z M16 19a3 3 0 1 0 0-6a3 3 0 1 0 0 6z" },
  { key: "PALETTE", label: "Arts", d: "M12 3a9 9 0 1 0 0 18c1.5 0 2-1 2-2s-1-1.5-1-2.5 1-1.5 2-1.5h2a4 4 0 0 0 4-4c0-4.4-4-8-9-8z M7.5 11h.01M11 7.5h.01M16 8.5h.01" },
  { key: "MOVIE", label: "Cinéma", d: "M4 6h16v12H4z M4 10h16M8 6l2 4M14 6l2 4" },
  { key: "CAKE", label: "Gâteau", d: "M4 21h16v-8H4z M4 16c2 1.2 4 1.2 6 0s4-1.2 6 0 3 1.2 4 0 M12 13V9 M12 5v1" },
  { key: "RESTAURANT", label: "Cuisine", d: "M7 3v8M5 3v5a2 2 0 0 0 4 0V3M7 11v10M17 21V3c-2 1.5-3 4-3 7h3" },
  { key: "PARTY", label: "Fête", d: "M4 20l4-12 8 8z M14 4l1 2M19 5l-2 2M20 10l-2-.5" },
  { key: "ROCKET", label: "Fusée", d: "M12 2c3 3 4 7 4 11l-4 3-4-3c0-4 1-8 4-11z M8 13l-3 3v3l3-2M16 13l3 3v3l-3-2" },
  { key: "MAGIC", label: "Magie", d: "M12 3l1.8 5.2L19 10l-5.2 1.8L12 17l-1.8-5.2L5 10l5.2-1.8z" },
  { key: "BABY", label: "Bébé", d: "M12 4a4 4 0 1 0 0 8a4 4 0 1 0 0-8z M6 21c0-3.3 2.7-6 6-6s6 2.7 6 6" },
  { key: "PETS", label: "Animaux", d: "M7 9a1.5 1.5 0 1 0 0-3a1.5 1.5 0 1 0 0 3z M17 9a1.5 1.5 0 1 0 0-3a1.5 1.5 0 1 0 0 3z M12 12c-3 0-5 3-5 5.5 0 1.5 1.5 2 3 1.5s2.5-.5 4 0 3 0 3-1.5c0-2.5-2-5.5-5-5.5z" },
  { key: "SCHOOL", label: "École", d: "M2 9l10-5 10 5-10 5z M6 11v5c0 1.5 3 3 6 3s6-1.5 6-3v-5" },
  { key: "HEART", label: "Cœur", d: "M12 20s-7-4.4-7-10a4 4 0 0 1 7-2.6A4 4 0 0 1 19 10c0 5.6-7 10-7 10z" },
  { key: "STAR", label: "Étoile", d: "M12 3l2.7 5.6 6.1.9-4.4 4.3 1 6.1L12 17l-5.4 2.9 1-6.1-4.4-4.3 6.1-.9z" },
  { key: "FOLDER", label: "Dossier", d: "M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z" },
  { key: "HOME", label: "Maison", d: "M4 11l8-7 8 7v9a1 1 0 0 1-1 1h-4v-6h-6v6H5a1 1 0 0 1-1-1z" },
  { key: "TRAVEL", label: "Voyage", d: "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18z M15.5 8.5l-2 5-5 2 2-5z" },
  { key: "PARK", label: "Parc", d: "M12 3a5 5 0 0 0-5 5c0 3 2.2 5 5 5s5-2 5-5a5 5 0 0 0-5-5z M12 13v8 M8 21h8" },
  { key: "GAMES", label: "Jeux", d: "M6 8h12a4 4 0 0 1 0 8c-1.5 0-2.5-1-3-2h-6c-.5 1-1.5 2-3 2a4 4 0 0 1 0-8z M8 10v4M6 12h4 M15.5 11h.01M17.5 13h.01" },
  { key: "COFFEE", label: "Goûter", d: "M4 9h12v6a5 5 0 0 1-5 5H9a5 5 0 0 1-5-5z M16 11h1.5a2.5 2.5 0 0 1 0 5H16 M8 3v3M12 3v3" },
  { key: "CANDLE", label: "Veillée", d: "M12 3c2 2.5 3 4 3 5.5a3 3 0 0 1-6 0C9 7 10 5.5 12 3z M9 13h6v8H9z" },
  { key: "PIRATE", label: "Aventure", d: "M3 17h18l-2 4H5z M12 3v14 M12 4l7 9h-7z M12 7l-5 6h5" },
  { key: "SHOPPING", label: "Courses", d: "M4 5h2l2 11h11l2-8H7M10 20a1 1 0 1 0 0-.1M17 20a1 1 0 1 0 0-.1" },
  { key: "WORK", label: "Travail", d: "M4 8h16v11H4z M9 8V5h6v3 M4 13h16" },
  { key: "BOOKMARK", label: "Signet", d: "M6 4h12v17l-6-4-6 4z" },
];

// Anciennes valeurs (émojis, clés d'avant la refonte)
const LEGACY_FOLDER_ICONS: Record<string, string> = { sparkles: "MAGIC" };

export function folderIconPath(key: string | undefined): string {
  const k = LEGACY_FOLDER_ICONS[key ?? ""] ?? key;
  return (FOLDER_ICONS.find((i) => i.key === k) ?? FOLDER_ICONS.find((i) => i.key === "FOLDER")!).d;
}

// Couleurs d'avatars (fond, encre), dans l'ordre de la maquette « Ma tribu »
export const AVATAR_COLORS: [string, string][] = [
  ["#F28C38", "#3A211B"], ["#5E4558", "#FBF3E7"], ["#DCEDF3", "#235C77"], ["#E2EDD9", "#3E6630"],
  ["#FBEACB", "#85560C"], ["#F7DDE6", "#9C3A5C"],
];
