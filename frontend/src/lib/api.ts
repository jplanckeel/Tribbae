import { clearSession, getSession, setSession, shouldRenew } from "./session";
import { withinLimits } from "./preview";
import type {
  AdminStats, AdminUser, AiIdea, Child, CircleInvitation, CircleInvitationInfo, Comment, ConnectedApp, Folder, FolderInput, Link, LinkInput, LinkPreview, OAuthConsent, Tribe, TribeInvitationInfo, UserProfile, CollaboratorRole, StatsPeriod,
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
  if (status === 429 && /ai is busy/i.test(message)) return "L'IA prépare déjà des idées pour une autre tribu. Réessayez dans une trentaine de secondes.";
  if (status === 429) return "Trop de tentatives, réessayez dans une minute.";
  if (/idea generation failed/i.test(message)) return "L'IA n'a pas réussi à préparer d'idées. Réessayez ou reformulez votre demande.";
  if (status === 401 && /credentials/i.test(message)) return "Email ou mot de passe incorrect.";
  if (status === 401) return "Votre session a expiré, reconnectez-vous.";
  if (status === 403) return "Vous n'avez pas les droits pour cette action.";
  if (/invalid or expired invitation/i.test(message)) return "Cette invitation n'est plus valable : elle a expiré, a été annulée ou a déjà servi.";
  if (/connected app not found/i.test(message)) return "Cette application n'est déjà plus connectée.";
  if (/authorization request not found/i.test(message)) return "Cette demande de connexion a expiré ou a déjà été traitée. Relancez la connexion depuis l'application.";
  if (status === 404) return "Introuvable — l'élément a peut-être été supprimé.";
  if (/already registered/i.test(message)) return "Un compte existe déjà avec cet email.";
  if (/password must be/i.test(message)) return "Le mot de passe doit contenir au moins 8 caractères.";
  if (/valid email/i.test(message)) return "Adresse email invalide.";
  if (/user not found with this email/i.test(message)) return "Aucun compte Tribbae avec cet email.";
  if (/already a collaborator/i.test(message)) return "Cette personne fait déjà partie du dossier.";
  if (/already a member of this tribe/i.test(message)) return "Cette personne fait déjà partie de votre tribu.";
  if (/already belongs to another tribe/i.test(message)) return "Ce compte fait déjà partie d'une autre tribu : la personne doit d'abord la quitter.";
  if (/too many pending invitations/i.test(message)) return "Trop d'invitations en attente (30 au plus) : annulez-en ou attendez qu'elles soient acceptées.";
  if (/choose the folders to open/i.test(message)) return "Choisissez au moins un dossier à ouvrir.";
  if (/cannot invite yourself/i.test(message)) return "C'est votre propre adresse.";
  if (/tribe is full/i.test(message)) return "La tribu est complète (12 membres et invitations en attente au plus).";
  if (/invitation is for another email/i.test(message)) return "Cette invitation a été envoyée à une autre adresse que celle de votre compte.";
  if (/only the tribe owner/i.test(message)) return "Seul le créateur de la tribu peut retirer un membre.";
  if (/name must be 1 to 60/i.test(message)) return "Le nom de la tribu doit faire entre 1 et 60 caractères.";
  if (/url must be/i.test(message)) return "Le lien doit être une adresse web (https://…).";
  if (/wrong password/i.test(message)) return "Mot de passe incorrect.";
  if (/email address not verified/i.test(message)) return "Confirmez d'abord votre adresse email : ouvrez le lien reçu par email, ou demandez-en un nouveau avec « Renvoyer l'email ».";
  if (/invalid or expired verification link/i.test(message)) return "Ce lien n'est plus valable : il a expiré ou a déjà servi. Demandez-en un nouveau.";
  if (/invalid or expired reset link/i.test(message)) return "Ce lien n'est plus valable : il a expiré ou a déjà servi. Demandez-en un nouveau.";
  if (/account deletion failed/i.test(message)) return "La suppression n'a pas abouti. Réessayez : elle reprendra là où elle s'est arrêtée.";
  if (/photo is too large/i.test(message)) return "Cette photo est trop lourde.";
  if (/photo must be/i.test(message)) return "Ce fichier n'est pas une photo (JPEG, PNG ou WebP).";
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

let renewing: Promise<void> | undefined;

/**
 * Session glissante : le jeton dure 30 jours ; on l'échange contre un neuf au
 * plus une fois par jour (au lancement et au retour sur l'onglet), pour qu'une
 * personne qui utilise l'app ne soit jamais déconnectée. Un jeton déjà refusé
 * ne se renouvelle pas : la prochaine requête (401) ferme la session.
 */
export function renewSessionIfStale(): Promise<void> {
  const session = getSession();
  if (!session || !shouldRenew(session.token) || renewing) return renewing ?? Promise.resolve();
  renewing = request<{ token: string }>("/auth/refresh", { method: "POST", body: json({ token: session.token }) })
    .then(({ token }) => {
      // Toujours la même session (pas de déconnexion entre-temps) : on garde le reste
      const current = getSession();
      if (token && current?.token === session.token) setSession({ ...current, token });
    })
    .catch(() => {
      // Hors ligne ou serveur indisponible : on réessaiera au prochain retour
    })
    .finally(() => {
      renewing = undefined;
    });
  return renewing;
}

let refreshing: Promise<void> | undefined;

/**
 * Relit l'état du compte tant que l'adresse n'est pas confirmée (au lancement
 * et au retour sur l'onglet) : le lien a pu être ouvert sur un autre appareil.
 * Une fois confirmée, plus aucune requête.
 */
export function refreshAccount(): Promise<void> {
  const session = getSession();
  if (!session || session.emailVerified || refreshing) return refreshing ?? Promise.resolve();
  refreshing = reloadAccount()
    .catch(() => {
      // Hors ligne : on réessaiera au prochain retour
    })
    .finally(() => {
      refreshing = undefined;
    });
  return refreshing;
}

/** Relit le compte connecté et met la session à jour, sans attendre une lecture déjà en cours. */
export async function reloadAccount(): Promise<void> {
  const session = getSession();
  if (!session) return;
  const me = await auth.me();
  const current = getSession();
  // Toujours la même session (pas de déconnexion entre-temps)
  if (current?.token === session.token) {
    setSession({ ...current, displayName: me.displayName ?? current.displayName, isAdmin: !!me.isAdmin, emailVerified: !!me.emailVerified });
  }
}

/** Note dans la session locale que l'adresse est confirmée. */
export function markEmailVerified() {
  const current = getSession();
  if (current && !current.emailVerified) setSession({ ...current, emailVerified: true });
}

export const auth = {
  register: (email: string, password: string, displayName: string) =>
    request<{ userId: string; token: string; isAdmin?: boolean; emailVerified?: boolean }>("/auth/register", {
      method: "POST", body: json({ email, password, displayName }),
    }),
  login: (email: string, password: string) =>
    request<{ userId: string; token: string; displayName?: string; isAdmin?: boolean; emailVerified?: boolean }>("/auth/login", {
      method: "POST", body: json({ email, password }),
    }),
  /** Compte connecté (adresse confirmée…). */
  me: () =>
    request<{ userId: string; email?: string; displayName?: string; isAdmin?: boolean; emailVerified?: boolean }>("/auth/me"),
  /** Confirme l'adresse depuis le lien reçu par email (sans session nécessaire). */
  verifyEmail: (token: string) =>
    request<void>("/auth/email/verify", { method: "POST", body: json({ token }) }),
  /** Renvoie le lien de confirmation ; emailVerified si l'adresse l'était déjà. */
  resendVerification: () =>
    request<{ emailVerified?: boolean }>("/auth/email/resend", { method: "POST", body: json({}) }),
  /** Envoie un lien de réinitialisation si l'adresse a un compte (même réponse sinon). */
  forgotPassword: (email: string) =>
    request<void>("/auth/password/forgot", { method: "POST", body: json({ email }) }),
  /** Nouveau mot de passe depuis le lien reçu par email : ouvre une session. */
  resetPassword: (token: string, password: string) =>
    request<{ userId: string; token: string; displayName?: string; isAdmin?: boolean; emailVerified?: boolean }>("/auth/password/reset", {
      method: "POST", body: json({ token, password }),
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
  preview: (url: string) => request<LinkPreview>(`/links/preview${qs({ url })}`).then(withinLimits),
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
      // setChildIds : la liste vide efface bien « Pour qui »
      method: "PUT", body: json({ folderId: id, ...data, setChildIds: true }),
    }).then((r) => r.folder),
  delete: (id: string) => request<void>(`/folders/${encodeURIComponent(id)}`, { method: "DELETE" }),
  share: (id: string) =>
    request<{ shareToken: string; shareUrl: string }>(`/folders/${encodeURIComponent(id)}/share`, {
      method: "POST", body: "{}",
    }),
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
};

/** Connecter une application (Claude) : page de consentement OAuth. */
export const oauth = {
  consentInfo: (requestId: string) => request<OAuthConsent>("/oauth/consent/info", { method: "POST", body: json({ request: requestId }) }),
  /** Retourne l'adresse où renvoyer le navigateur (vers l'application, avec le code ou le refus). */
  decide: (requestId: string, approve: boolean) =>
    request<{ redirect: string }>("/oauth/consent", { method: "POST", body: json({ request: requestId, approve }) }).then((r) => r.redirect),
};

/** « Applications connectées » : accès donnés à Claude (serveur MCP). */
export const connectedApps = {
  list: () => request<{ apps?: ConnectedApp[] }>("/connected-apps").then((r) => r.apps ?? []),
  /** Retire l'accès ; deleteIdeas efface aussi les idées que l'application a ajoutées. */
  revoke: (id: string, deleteIdeas: boolean) =>
    request<{ deletedIdeas?: number }>(`/connected-apps/${encodeURIComponent(id)}${qs({ deleteIdeas: deleteIdeas ? "true" : undefined })}`, { method: "DELETE" })
      .then((r) => r.deletedIdeas ?? 0),
};

export const tribe = {
  get: () => request<{ tribe: Tribe }>("/tribe").then((r) => r.tribe),
  rename: (name: string) => request<{ tribe: Tribe }>("/tribe", { method: "PUT", body: json({ name }) }).then((r) => r.tribe),
  /** Envoie une invitation par email (même réponse que l'adresse ait un compte ou non). */
  invite: (email: string) =>
    request<{ tribe: Tribe }>("/tribe/members", { method: "POST", body: json({ email }) }).then((r) => r.tribe),
  revokeInvitation: (id: string) =>
    request<{ tribe: Tribe }>(`/tribe/invitations/${encodeURIComponent(id)}`, { method: "DELETE" }).then((r) => r.tribe),
  /** Lien d'invitation : accessible sans compte. */
  lookupInvitation: (token: string) =>
    request<TribeInvitationInfo>("/tribe/invitations/lookup", { method: "POST", body: json({ token }) }),
  acceptInvitation: (token: string) =>
    request<{ tribe: Tribe }>("/tribe/invitations/accept", { method: "POST", body: json({ token }) }).then((r) => r.tribe),
  /** Avec son propre ID : quitter la tribu. */
  removeMember: (userId: string) =>
    request<{ tribe: Tribe }>(`/tribe/members/${encodeURIComponent(userId)}`, { method: "DELETE" }).then((r) => r.tribe),
  setSharing: (sharing: boolean) =>
    request<{ tribe: Tribe }>("/tribe/sharing", { method: "PUT", body: json({ sharing }) }).then((r) => r.tribe),
};

/** Le cercle : proches invités par email à des dossiers choisis. */
export const circle = {
  /** Même réponse que l'adresse ait un compte ou non ; une invitation en attente reçoit les dossiers en plus. */
  invite: (email: string, folderIds: string[], role: CollaboratorRole) =>
    request<{ invitation: CircleInvitation }>("/circle/invitations", { method: "POST", body: json({ email, folderIds, role }) }).then((r) => r.invitation),
  invitations: () => request<{ invitations?: CircleInvitation[] }>("/circle/invitations").then((r) => r.invitations ?? []),
  revokeInvitation: (id: string) => request<void>(`/circle/invitations/${encodeURIComponent(id)}`, { method: "DELETE" }),
  /** Lien d'invitation : accessible sans compte. */
  lookupInvitation: (token: string) =>
    request<CircleInvitationInfo>("/circle/invitations/lookup", { method: "POST", body: json({ token }) }),
  acceptInvitation: (token: string) =>
    request<{ folders?: Folder[] }>("/circle/invitations/accept", { method: "POST", body: json({ token }) }).then((r) => r.folders ?? []),
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
  /** count : nombre d'idées (sinon déduit de la demande) ; exclude : titres déjà proposés. */
  generate: (prompt: string, opts: { count?: number; exclude?: string[]; signal?: AbortSignal } = {}) =>
    request<{ ideas?: AiIdea[] }>("/ai/generate", {
      method: "POST",
      body: json({ prompt, count: opts.count, exclude: opts.exclude }),
      signal: opts.signal,
    }).then((r) => r.ideas ?? []),
};

export const admin = {
  listUsers: () => request<{ users?: AdminUser[] }>("/admin/users").then((r) => r.users ?? []),
  setPremium: (userId: string, isPremium: boolean) =>
    request(`/admin/users/${encodeURIComponent(userId)}/premium`, { method: "PUT", body: json({ isPremium }) }),
  stats: (period: StatsPeriod, count = 12) => request<AdminStats>(`/admin/stats?period=${period}&count=${count}`),
};

/** Photos des idées : envoyées au serveur, qui les garde et renvoie leur adresse. */
export const photos = {
  upload: (photo: Blob) =>
    request<{ url: string }>("/photos", { method: "POST", body: photo, headers: { "Content-Type": photo.type || "image/jpeg" } }).then((r) => r.url),
};

/** Droits RGPD sur son compte : export de ses données, suppression définitive. */
export const account = {
  /** Télécharge toutes ses données dans un fichier JSON. */
  async exportData(): Promise<void> {
    const session = getSession();
    let res: Response;
    try {
      res = await fetch(`${BASE}/account/export`, { headers: session ? { Authorization: `Bearer ${session.token}` } : {} });
    } catch {
      throw new ApiError("Connexion impossible. Vérifiez votre réseau.", 0);
    }
    if (!res.ok) throw new ApiError(frenchMessage(res.status, ""), res.status);
    const name = /filename="([^"]+)"/.exec(res.headers.get("Content-Disposition") ?? "")?.[1] ?? "tribbae-mes-donnees.json";
    const url = URL.createObjectURL(await res.blob());
    const a = document.createElement("a");
    a.href = url;
    a.download = name;
    a.click();
    setTimeout(() => URL.revokeObjectURL(url), 10_000);
  },
  /** Supprime définitivement le compte (mot de passe exigé). */
  delete: (password: string) => request<{ deleted: boolean }>("/account/delete", { method: "POST", body: json({ password }) }),
};
