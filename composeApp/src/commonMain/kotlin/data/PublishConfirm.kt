package data

/** Confirmation avant de publier : mêmes textes que frontend/src/lib/publish.ts (publish.test.ts). */
object PublishConfirm {
    private const val NO_NAMES = "Les prénoms des enfants ne sont jamais publiés."

    const val IDEA_TITLE = "Publier cette idée ?"
    const val IDEA_TEXT = "Votre idée sera visible par toutes les familles dans Explorer. $NO_NAMES"
    const val FOLDER_TITLE = "Publier ce dossier ?"

    /** Idée rendue visible de tous par son dossier public (link.PublicFolderFilter). */
    fun exposedBy(link: Link, folder: Folder): Boolean = link.folderId == folder.id &&
        (link.ownerId == folder.ownerId || link.ownerId.isBlank() || link.visibility == "public")

    fun folderText(ideas: Int): String {
        val what = when {
            ideas > 1 -> "Les $ideas idées de ce dossier seront visibles"
            ideas == 1 -> "L'idée de ce dossier sera visible"
            else -> "Ce dossier sera visible"
        }
        return "$what par toutes les familles dans Explorer. $NO_NAMES"
    }
}
