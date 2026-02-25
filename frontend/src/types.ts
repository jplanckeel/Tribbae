export interface Link {
  id: string;
  ownerId: string;
  folderId: string;
  title: string;
  url: string;
  description: string;
  category: string;
  tags: string[];
  ageRange: string;
  location: string;
  price: string;
  imageUrl: string;
  eventDate: string;
  reminderEnabled: boolean;
  rating: number;
  ingredients: string[];
  createdAt?: string;
  updatedAt?: string;
}

export interface Folder {
  id: string;
  ownerId: string;
  name: string;
  icon: string;
  color: string;
  visibility: string;
  shareToken: string;
  collaborators: Collaborator[];
  ownerDisplayName: string;
  linkCount: number;
  createdAt?: string;
  updatedAt?: string;
}

export interface Collaborator {
  userId: string;
  email: string;
  displayName: string;
  role: string; // "COLLABORATOR_ROLE_VIEWER" | "COLLABORATOR_ROLE_EDITOR"
  addedAt?: string;
}

export interface AuthResponse {
  userId: string;
  token: string;
  displayName?: string;
}

export interface Child {
  id: string;
  ownerId: string;
  name: string;
  birthDate: number; // timestamp ms
}

export const CATEGORIES = [
  { value: "LINK_CATEGORY_IDEE", label: "Idée", icon: "💡" },
  { value: "LINK_CATEGORY_CADEAU", label: "Cadeau", icon: "🎁" },
  { value: "LINK_CATEGORY_ACTIVITE", label: "Activité", icon: "🏃" },
  { value: "LINK_CATEGORY_EVENEMENT", label: "Événement", icon: "📅" },
  { value: "LINK_CATEGORY_RECETTE", label: "Recette", icon: "🍳" },
] as const;

// Mapping numérique → string (pour compatibilité grpc-gateway sans protojson)
const CATEGORY_NUM_MAP: Record<number, string> = {
  1: "LINK_CATEGORY_IDEE",
  2: "LINK_CATEGORY_CADEAU",
  3: "LINK_CATEGORY_ACTIVITE",
  4: "LINK_CATEGORY_EVENEMENT",
  5: "LINK_CATEGORY_RECETTE",
};

export function normalizeCategory(cat: string | number): string {
  if (typeof cat === "number") return CATEGORY_NUM_MAP[cat] ?? "LINK_CATEGORY_IDEE";
  if (typeof cat === "string" && /^\d+$/.test(cat)) return CATEGORY_NUM_MAP[parseInt(cat)] ?? "LINK_CATEGORY_IDEE";
  return cat || "LINK_CATEGORY_IDEE";
}

export const CATEGORY_COLORS: Record<string, string> = {
  LINK_CATEGORY_IDEE: "#FFD700",
  LINK_CATEGORY_CADEAU: "#FF8C00",
  LINK_CATEGORY_ACTIVITE: "#4FC3F7",
  LINK_CATEGORY_EVENEMENT: "#FF7043",
  LINK_CATEGORY_RECETTE: "#81C784",
};
