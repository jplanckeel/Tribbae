package data

/**
 * Qui voit une idée ou un dossier (composant Visibility du design system). Le
 * partage de la tribu étant la règle, Privé veut dire « vous et votre tribu ».
 * Mêmes cas que frontend/src/lib/visibility.ts (AudienceTest).
 */
enum class Audience(val label: String) {
    PRIVATE("Privé"), SHARED("Tribu"), PUBLIC("Communauté");

    companion object {
        fun ofFolder(folder: Folder, isPeer: (String) -> Boolean): Audience = when {
            folder.visibility.uppercase().contains("PUBLIC") -> PUBLIC
            folder.collaborators.isNotEmpty() -> SHARED
            folder.ownerId.isNotBlank() && !isPeer(folder.ownerId) -> SHARED
            else -> PRIVATE
        }

        /** Une idée rangée dans un dossier suit au moins la visibilité de ce dossier. */
        fun ofIdea(link: Link, folder: Folder?, isPeer: (String) -> Boolean): Audience = when {
            link.visibility == "public" -> PUBLIC
            folder != null -> ofFolder(folder, isPeer)
            else -> PRIVATE
        }
    }
}
