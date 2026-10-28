/** Droits qu'une application connectée (Claude) peut demander, en clair. */
export const SCOPE_LABELS: Record<string, string> = {
  "folders:read": "Voir la liste de vos dossiers",
  "links:read": "Voir les idées de vos dossiers (titre, lien, catégorie)",
  "links:write": "Ajouter des idées dans vos dossiers, toujours en privé",
};

/** Ce qu'une application connectée ne peut jamais faire, quels que soient ses droits. */
export const NEVER_ALLOWED = [
  "Modifier ou supprimer vos idées et vos dossiers",
  "Publier quoi que ce soit dans la communauté",
  "Voir votre mot de passe, votre tribu ou vos enfants",
];

/** Libellés des droits demandés, dans l'ordre d'affichage ; un droit inconnu n'est jamais masqué. */
export function scopeLabels(scopes: string[]): string[] {
  const known = Object.keys(SCOPE_LABELS).filter((s) => scopes.includes(s)).map((s) => SCOPE_LABELS[s]);
  const unknown = scopes.filter((s) => !(s in SCOPE_LABELS));
  return [...known, ...unknown];
}

/** Mention affichée sur une idée ajoutée par une application connectée. */
export function createdViaLabel(createdVia?: string): string | undefined {
  return createdVia === "mcp" ? "Ajoutée par Claude" : undefined;
}
