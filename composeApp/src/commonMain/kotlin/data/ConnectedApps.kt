package data

/**
 * Applications connectées (Claude, serveur MCP) : libellés partagés avec le
 * web (frontend/src/lib/oauth.ts, mêmes cas dans ConnectedAppsTest et oauth.test.ts).
 */
object ConnectedApps {
    /** Droits qu'une application peut demander, en clair, dans l'ordre d'affichage. */
    private val SCOPE_LABELS = linkedMapOf(
        "folders:read" to "Voir la liste de vos dossiers",
        "links:read" to "Voir les idées de vos dossiers (titre, lien, catégorie)",
        "links:write" to "Ajouter des idées dans vos dossiers, toujours en privé",
    )

    /** Libellés des droits accordés ; un droit inconnu n'est jamais masqué. */
    fun scopeLabels(scopes: List<String>): List<String> =
        SCOPE_LABELS.filterKeys { it in scopes }.values.toList() + scopes.filter { it !in SCOPE_LABELS }

    /** Mention affichée sur une idée ajoutée par une application connectée. */
    fun createdViaLabel(createdVia: String?): String? = if (createdVia == "mcp") "Ajoutée par Claude" else null
}
