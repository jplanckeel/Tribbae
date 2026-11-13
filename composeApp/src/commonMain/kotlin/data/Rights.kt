package data

/**
 * Qui peut ajouter ou modifier des idées : même règle que canEditFolder
 * (frontend/src/lib/links.ts, links.test.ts ↔ RightsTest). Le serveur reste
 * juge ; ceci évite d'offrir à un lecteur du cercle des actions qu'il refuserait.
 */
object Rights {
    const val EDITOR = "COLLABORATOR_ROLE_EDITOR"

    /** Le dossier du compte (ou pas encore synchronisé), celui de sa tribu, ou un dossier où il est éditeur. */
    fun canEditFolder(folder: Folder, userId: String?, isPeer: (String) -> Boolean = { false }): Boolean {
        if (userId.isNullOrBlank()) return false
        if (folder.ownerId.isBlank() || folder.ownerId == userId || isPeer(folder.ownerId)) return true
        return folder.collaborators.any { it.userId == userId && it.role == EDITOR }
    }

    /** Une idée de sa tribu, ou rangée dans un dossier qu'il peut modifier (comme la fiche web). */
    fun canEditIdea(link: Link, folder: Folder?, userId: String?, isPeer: (String) -> Boolean = { false }): Boolean {
        if (userId.isNullOrBlank()) return false
        if (link.ownerId.isBlank() || link.ownerId == userId || isPeer(link.ownerId)) return true
        return folder != null && canEditFolder(folder, userId, isPeer)
    }
}
