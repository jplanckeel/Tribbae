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

/** Une idée rangée dans un dossier suit au moins la visibilité de ce dossier. */
export function ideaAudience(link: Pick<Link, "visibility">, folder: FolderLike | undefined, peers: ReadonlySet<string>): Audience {
  if (link.visibility === "public") return "public";
  return folder ? folderAudience(folder, peers) : "private";
}
