export type CategoryKey =
  | "LINK_CATEGORY_IDEE"
  | "LINK_CATEGORY_CADEAU"
  | "LINK_CATEGORY_ACTIVITE"
  | "LINK_CATEGORY_EVENEMENT"
  | "LINK_CATEGORY_RECETTE"
  | "LINK_CATEGORY_LIVRE"
  | "LINK_CATEGORY_DECORATION";

export type Visibility = "VISIBILITY_PRIVATE" | "VISIBILITY_SHARED" | "VISIBILITY_PUBLIC";
export type CollaboratorRole = "COLLABORATOR_ROLE_VIEWER" | "COLLABORATOR_ROLE_EDITOR";

export interface Link {
  id: string;
  ownerId: string;
  folderId?: string;
  title: string;
  url?: string;
  description?: string;
  category: CategoryKey;
  tags?: string[];
  ageRange?: string;
  location?: string;
  price?: string;
  imageUrl?: string;
  /** Timestamp (ms ou s selon le client qui l'a créé) sérialisé en chaîne par grpc-gateway. */
  eventDate?: string | number;
  reminderEnabled?: boolean;
  rating?: number;
  ingredients?: string[];
  favorite?: boolean;
  likeCount?: number;
  likedByMe?: boolean;
  ownerDisplayName?: string;
  ownerIsAdmin?: boolean;
  visibility?: string;
  createdAt?: string;
  updatedAt?: string;
}

/** Champs modifiables d'une idée (création / édition). */
export interface LinkInput {
  title: string;
  url?: string;
  description?: string;
  category: CategoryKey;
  tags?: string[];
  ageRange?: string;
  location?: string;
  price?: string;
  imageUrl?: string;
  eventDate?: number;
  reminderEnabled?: boolean;
  rating?: number;
  ingredients?: string[];
  folderId?: string;
  visibility?: "private" | "public";
}

/** Aperçu d'une page web (GET /v1/links/preview) : de quoi pré-remplir une idée. */
export interface LinkPreview {
  title: string;
  description: string;
  /** Meilleure image ; `images` liste tous les candidats, du meilleur au moins bon. */
  image: string;
  images?: string[];
  siteName?: string;
  /** Catégorie déduite des données schema.org (recette, événement, livre, produit). */
  category?: CategoryKey;
  ingredients?: string[];
  price?: string;
  location?: string;
  /** Timestamp ms. */
  eventDate?: number;
}

export type TribeRole = "TRIBE_ROLE_OWNER" | "TRIBE_ROLE_MEMBER";

export interface TribeMember {
  userId: string;
  displayName?: string;
  email?: string;
  role: TribeRole;
  /** Le contenu de ce membre est partagé avec la tribu (confirmé par lui). */
  sharing?: boolean;
  addedAt?: number | string;
}

/** Tribu : les comptes d'une famille, qui partagent idées, dossiers et enfants. */
export interface Tribe {
  /** Vide tant que la tribu ne compte que son créateur. */
  id?: string;
  /** Nom affiché (« La tribu … »), par défaut le nom du compte. */
  name: string;
  members?: TribeMember[];
}

export interface Collaborator {
  userId: string;
  email?: string;
  displayName?: string;
  role: CollaboratorRole;
  addedAt?: string;
}

export interface Folder {
  id: string;
  ownerId: string;
  name: string;
  icon?: string;
  color?: string;
  bannerUrl?: string;
  tags?: string[];
  visibility?: Visibility;
  shareToken?: string;
  collaborators?: Collaborator[];
  ownerDisplayName?: string;
  ownerIsAdmin?: boolean;
  linkCount?: number;
  likeCount?: number;
  likedByMe?: boolean;
  aiGenerated?: boolean;
  createdAt?: string;
  updatedAt?: string;
}

export interface FolderInput {
  name: string;
  icon: string;
  color: string;
  visibility: Visibility;
  bannerUrl?: string;
  tags?: string[];
}

export interface Child {
  id: string;
  name: string;
  /** Timestamp ms, sérialisé en chaîne par grpc-gateway. */
  birthDate: string | number;
}

export interface Comment {
  id: string;
  linkId: string;
  userId: string;
  userDisplayName?: string;
  userIsAdmin?: boolean;
  text: string;
  createdAt?: string;
}

export interface UserProfile {
  id: string;
  displayName?: string;
  isAdmin?: boolean;
  followerCount?: number;
  followingCount?: number;
}

export interface AiIdea {
  title: string;
  description: string;
  url?: string;
  imageUrl?: string;
  category: CategoryKey;
  tags?: string[];
  ageRange?: string;
  price?: string;
  location?: string;
  ingredients?: string[];
}

export interface AdminUser {
  id: string;
  email: string;
  displayName: string;
  isAdmin: boolean;
  isPremium: boolean;
  createdAt?: string | number;
}
