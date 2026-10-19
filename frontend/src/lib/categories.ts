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
  bg: string;
  solid: string;
}

export const FOLDER_COLORS: FolderColorMeta[] = [
  { key: "BLUE", label: "Lac", bg: "#DCEDF3", solid: "#2F7393" },
  { key: "ORANGE", label: "Braise", bg: "#FCE3D0", solid: "#C35A1C" },
  { key: "GREEN", label: "Forêt", bg: "#E2EDD9", solid: "#4C7A3C" },
  { key: "RED", label: "Terre", bg: "#F8DCD3", solid: "#B2412A" },
  { key: "YELLOW", label: "Soleil", bg: "#FBEACB", solid: "#9A6510" },
  { key: "PURPLE", label: "Prune", bg: "#ECE1EA", solid: "#5E4558" },
  { key: "PINK", label: "Rose", bg: "#F7DDE6", solid: "#9C3A5C" },
  { key: "TEAL", label: "Sauge", bg: "#D7ECE8", solid: "#2F7A6E" },
];

export function folderColor(key: string | undefined): FolderColorMeta {
  return FOLDER_COLORS.find((c) => c.key === key) ?? FOLDER_COLORS[0];
}

export const FOLDER_ICONS: { key: string; icon: IconName; label: string }[] = [
  { key: "FOLDER", icon: "tipi", label: "Tipi" },
  { key: "STAR", icon: "star", label: "Étoile" },
  { key: "HEART", icon: "heart", label: "Cœur" },
  { key: "HOME", icon: "home", label: "Maison" },
  { key: "TRAVEL", icon: "compass", label: "Voyage" },
  { key: "MOUNTAIN", icon: "landscape", label: "Nature" },
  { key: "PARTY", icon: "cadeau", label: "Fête" },
  { key: "RESTAURANT", icon: "recette", label: "Repas" },
  { key: "SPORTS", icon: "activite", label: "Sport" },
  { key: "PALETTE", icon: "deco", label: "Créatif" },
  { key: "SCHOOL", icon: "livre", label: "École" },
  { key: "SHOPPING", icon: "cart", label: "Courses" },
  { key: "BABY", icon: "baby", label: "Bébé" },
  { key: "MAGIC", icon: "sparkle", label: "Magie" },
];

const LEGACY_FOLDER_ICONS: Record<string, IconName> = {
  sparkles: "sparkle", CAMPING: "tipi", PARK: "landscape", BEACH: "landscape", FOREST: "landscape",
  CAKE: "recette", COFFEE: "recette", GAMES: "activite", MUSIC: "deco", MOVIE: "deco",
  WORK: "livre", BOOKMARK: "bookmark", ROCKET: "sparkle", PIRATE: "compass", CANDLE: "idee", PETS: "heart",
};

export function folderIcon(key: string | undefined): IconName {
  return FOLDER_ICONS.find((i) => i.key === key)?.icon ?? LEGACY_FOLDER_ICONS[key ?? ""] ?? "tipi";
}

// Couleurs d'avatars (fond, encre), dans l'ordre de la maquette « Ma tribu »
export const AVATAR_COLORS: [string, string][] = [
  ["#F28C38", "#3A211B"], ["#5E4558", "#FBF3E7"], ["#DCEDF3", "#235C77"], ["#E2EDD9", "#3E6630"],
  ["#FBEACB", "#85560C"], ["#F7DDE6", "#9C3A5C"],
];
