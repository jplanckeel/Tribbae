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

        /**
         * Une idée suit la visibilité de son dossier, sauf dans un dossier public :
         * seules les idées de son propriétaire y sont visibles de tous. Un dossier
         * qu'on ne connaît pas (celui d'un autre compte) : le serveur a tranché.
         * Une idée pas encore envoyée n'a pas d'auteur : c'est celle du compte.
         */
        fun ofIdea(link: Link, folder: Folder?, isPeer: (String) -> Boolean): Audience = when {
            link.visibility == "public" -> PUBLIC
            // Retirée par la modération : plus exposée par son dossier public
            link.hidden -> PRIVATE
            folder == null -> if (link.publicViaFolder) PUBLIC else PRIVATE
            !folder.visibility.uppercase().contains("PUBLIC") -> ofFolder(folder, isPeer)
            link.ownerId == folder.ownerId || link.ownerId.isBlank() -> PUBLIC
            else -> SHARED
        }
    }
}
