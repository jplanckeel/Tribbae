import { clearSession, getSession } from "./session";
import type {
  AdminUser, AiIdea, Child, Comment, Folder, FolderInput, Link, LinkInput, UserProfile, CollaboratorRole,
} from "./types";

const BASE = "/v1";

export class ApiError extends Error {
  status: number;
  constructor(message: string, status: number) {
    super(message);
    this.status = status;
  }
}

/** Traduit les messages d'erreur serveur les plus courants. */
function frenchMessage(status: number, message: string): string {
  if (status === 429) return "Trop de tentatives, réessayez dans une minute.";
  if (status === 401 && /credentials/i.test(message)) return "Email ou mot de passe incorrect.";
  if (status === 401) return "Votre session a expiré, reconnectez-vous.";
  if (status === 403) return "Vous n'avez pas les droits pour cette action.";
  if (status === 404) return "Introuvable — l'élément a peut-être été supprimé.";
  if (/already registered/i.test(message)) return "Un compte existe déjà avec cet email.";
  if (/password must be/i.test(message)) return "Le mot de passe doit contenir au moins 8 caractères.";
  if (/valid email/i.test(message)) return "Adresse email invalide.";
  if (/user not found with this email/i.test(message)) return "Aucun compte Tribbae avec cet email.";
  if (/already a collaborator/i.test(message)) return "Cette personne fait déjà partie du dossier.";
  if (/url must be/i.test(message)) return "Le lien doit être une adresse web (https://…).";
  if (status >= 500) return "Le serveur a rencontré un problème, réessayez.";
  return message || "Une erreur est survenue.";
}

async function request<T>(path: string, opts: RequestInit = {}): Promise<T> {
  const session = getSession();
  const headers: Record<string, string> = {
    "Content-Type": "application/json",
    ...(opts.headers as Record<string, string>),
  };
  if (session) headers["Authorization"] = `Bearer ${session.token}`;

  let res: Response;
  try {
    res = await fetch(`${BASE}${path}`, { ...opts, headers });
  } catch {
    throw new ApiError("Connexion impossible. Vérifiez votre réseau.", 0);
  }
  if (!res.ok) {
    const body = await res.json().catch(() => ({}));
    // Jeton expiré ou révoqué : on ferme la session locale
    if (res.status === 401 && session && !path.startsWith("/auth/")) clearSession();
    throw new ApiError(frenchMessage(res.status, body.message || body.error || ""), res.status);
  }
  if (res.status === 204) return undefined as T;
  return res.json();
}

const json = (body: unknown) => JSON.stringify(body);
const qs = (params: Record<string, string | number | undefined>) => {
  const p = new URLSearchParams();
  for (const [k, v] of Object.entries(params)) if (v !== undefined && v !== "") p.set(k, String(v));
  const s = p.toString();
  return s ? `?${s}` : "";
};

export const auth = {
  register: (email: string, password: string, displayName: string) =>
    request<{ userId: string; token: string; isAdmin?: boolean }>("/auth/register", {
      method: "POST", body: json({ email, password, displayName }),
    }),
  login: (email: string, password: string) =>
    request<{ userId: string; token: string; displayName?: string; isAdmin?: boolean }>("/auth/login", {
      method: "POST", body: json({ email, password }),
    }),
};

export const links = {
  list: (folderId?: string) =>
    request<{ links?: Link[] }>(`/links${qs({ folderId })}`).then((r) => r.links ?? []),
  get: (id: string) => request<{ link: Link }>(`/links/${encodeURIComponent(id)}`).then((r) => r.link),
  create: (data: LinkInput) =>
    request<{ link: Link }>("/links", { method: "POST", body: json(data) }).then((r) => r.link),
  update: (id: string, data: LinkInput) =>
    request<{ link: Link }>(`/links/${encodeURIComponent(id)}`, {
      method: "PUT", body: json({ linkId: id, ...data }),
    }).then((r) => r.link),
  delete: (id: string) => request<void>(`/links/${encodeURIComponent(id)}`, { method: "DELETE" }),
  preview: (url: string) =>
    request<{ title: string; description: string; image: string }>(`/links/preview${qs({ url })}`),
  like: (id: string) =>
    request<{ likeCount?: number }>(`/links/${encodeURIComponent(id)}/like`, { method: "POST", body: "{}" }),
  unlike: (id: string) =>
    request<{ likeCount?: number }>(`/links/${encodeURIComponent(id)}/like`, { method: "DELETE" }),
  toggleFavorite: (id: string) =>
    request<{ favorite?: boolean }>(`/links/${encodeURIComponent(id)}/favorite`, { method: "POST", body: "{}" }),
};

export const folders = {
  list: () => request<{ folders?: Folder[] }>("/folders").then((r) => r.folders ?? []),
  get: (id: string) => request<{ folder: Folder }>(`/folders/${encodeURIComponent(id)}`).then((r) => r.folder),
  create: (data: FolderInput) =>
    request<{ folder: Folder }>("/folders", { method: "POST", body: json(data) }).then((r) => r.folder),
  update: (id: string, data: FolderInput) =>
    request<{ folder: Folder }>(`/folders/${encodeURIComponent(id)}`, {
      method: "PUT", body: json({ folderId: id, ...data }),
    }).then((r) => r.folder),
  delete: (id: string) => request<void>(`/folders/${encodeURIComponent(id)}`, { method: "DELETE" }),
  share: (id: string) =>
    request<{ shareToken: string; shareUrl: string }>(`/folders/${encodeURIComponent(id)}/share`, {
      method: "POST", body: "{}",
    }),
  addCollaborator: (id: string, email: string, role: CollaboratorRole) =>
    request<{ folder: Folder }>(`/folders/${encodeURIComponent(id)}/collaborators`, {
      method: "POST", body: json({ email, role }),
    }).then((r) => r.folder),
  removeCollaborator: (id: string, userId: string) =>
    request<{ folder: Folder }>(
      `/folders/${encodeURIComponent(id)}/collaborators/${encodeURIComponent(userId)}`,
      { method: "DELETE" },
    ).then((r) => r.folder),
  like: (id: string) =>
    request<{ likeCount?: number }>(`/folders/${encodeURIComponent(id)}/like`, { method: "POST", body: "{}" }),
  unlike: (id: string) =>
    request<{ likeCount?: number }>(`/folders/${encodeURIComponent(id)}/like`, { method: "DELETE" }),
};

export const shared = {
  get: (token: string) =>
    request<{ folder: Folder; links?: Link[] }>(`/share/${encodeURIComponent(token)}`),
};

export const community = {
  folders: (search?: string, pageSize = 30, pageToken?: string) =>
    request<{ folders?: Folder[]; nextPageToken?: string }>(`/community/folders${qs({ search, pageSize, pageToken })}`),
  top: (limit = 12) => request<{ folders?: Folder[] }>(`/community/top${qs({ limit })}`).then((r) => r.folders ?? []),
  links: (category?: string, limit = 60) =>
    request<{ links?: Link[] }>(`/community/links${qs({ category, limit })}`).then((r) => r.links ?? []),
  newLinks: (limit = 12) =>
    request<{ links?: Link[] }>(`/community/new${qs({ limit })}`).then((r) => r.links ?? []),
};

export const children = {
  list: () => request<{ children?: Child[] }>("/children").then((r) => r.children ?? []),
  create: (name: string, birthDate: number) =>
    request("/children", { method: "POST", body: json({ name, birthDate }) }),
  update: (childId: string, name: string, birthDate: number) =>
    request(`/children/${encodeURIComponent(childId)}`, { method: "PUT", body: json({ childId, name, birthDate }) }),
  delete: (childId: string) => request<void>(`/children/${encodeURIComponent(childId)}`, { method: "DELETE" }),
};

export const comments = {
  list: (linkId: string) =>
    request<{ comments?: Comment[] }>(`/links/${encodeURIComponent(linkId)}/comments`).then((r) => r.comments ?? []),
  create: (linkId: string, text: string) =>
    request<{ comment: Comment }>(`/links/${encodeURIComponent(linkId)}/comments`, {
      method: "POST", body: json({ text }),
    }).then((r) => r.comment),
  delete: (commentId: string) => request<void>(`/comments/${encodeURIComponent(commentId)}`, { method: "DELETE" }),
};

export const follows = {
  following: (userId: string) =>
    request<{ following?: UserProfile[] }>(`/users/${encodeURIComponent(userId)}/following`).then((r) => r.following ?? []),
  isFollowing: (userId: string) =>
    request<{ isFollowing?: boolean }>(`/users/${encodeURIComponent(userId)}/is-following`).then((r) => !!r.isFollowing),
  follow: (userId: string) =>
    request<void>(`/users/${encodeURIComponent(userId)}/follow`, { method: "POST", body: "{}" }),
  unfollow: (userId: string) =>
    request<void>(`/users/${encodeURIComponent(userId)}/follow`, { method: "DELETE" }),
};

export const ai = {
  generate: (prompt: string) =>
    request<{ ideas?: AiIdea[] }>("/ai/generate", { method: "POST", body: json({ prompt }) }).then((r) => r.ideas ?? []),
};

export const admin = {
  listUsers: () => request<{ users?: AdminUser[] }>("/admin/users").then((r) => r.users ?? []),
  setPremium: (userId: string, isPremium: boolean) =>
    request(`/admin/users/${encodeURIComponent(userId)}/premium`, { method: "PUT", body: json({ isPremium }) }),
};
