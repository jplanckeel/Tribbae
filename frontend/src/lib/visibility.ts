import type { Folder, Link } from "./types";

/**
 * Qui voit une idée ou un dossier (composant Visibility du design system). Le
 * partage de la tribu étant la règle, Privé veut dire « vous et votre tribu ».
 * Mêmes cas que data/Audience.kt (AudienceTest).
 */
export type Audience = "private" | "shared" | "public";

export const AUDIENCE_LABEL: Record<Audience, string> = { private: "Privé", shared: "Tribu", public: "Communauté" };

type FolderLike = Pick<Folder, "visibility" | "collaborators" | "ownerId">;

export function folderAudience(folder: FolderLike, peers: ReadonlySet<string>): Audience {
  if (folder.visibility === "VISIBILITY_PUBLIC") return "public";
  if (folder.collaborators?.length) return "shared";
  return folder.ownerId && !peers.has(folder.ownerId) ? "shared" : "private";
}

/**
 * Une idée suit la visibilité de son dossier, sauf dans un dossier public : seules
 * les idées de son propriétaire y sont visibles de tous (PublicFolderFilter). Un
 * dossier qu'on ne connaît pas (celui d'un autre compte) : le serveur a tranché.
 */
export function ideaAudience(link: Pick<Link, "visibility" | "ownerId" | "publicViaFolder" | "hidden">, folder: FolderLike | undefined, peers: ReadonlySet<string>): Audience {
  if (link.visibility === "public") return "public";
  // Retirée par la modération : plus exposée par son dossier public
  if (link.hidden) return "private";
  if (!folder) return link.publicViaFolder ? "public" : "private";
  if (folder.visibility !== "VISIBILITY_PUBLIC") return folderAudience(folder, peers);
  return link.ownerId === folder.ownerId ? "public" : "shared";
}
