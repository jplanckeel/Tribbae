package data

/**
 * Demandes d'un assistant (Gemini, via les AppFunctions d'Android : voir
 * androidMain GeminiFunctions.kt) : lecture de ce qu'il envoie et choix
 * du dossier. Mêmes règles que le serveur MCP (backend/internal/mcpserver) :
 * idées toujours privées, jamais dans un dossier public (ce serait les publier),
 * seulement dans les dossiers où l'on peut écrire.
 */
object AssistantRequests {
    const val MAX_TITLE = 200
    const val MAX_DESCRIPTION = 2000
    const val MAX_FOLDER_NAME = 80
    const val MAX_TAGS = 10

    /** Demande refusée, avec la raison (en anglais, lue par l'assistant). */
    class Invalid(message: String) : IllegalArgumentException(message)

    /**
     * Catégorie depuis son nom (« RECETTE », « LINK_CATEGORY_RECETTE ») ou son
     * libellé (« Recette », « Sortie », « Déco »), sans tenir compte des
     * majuscules ni des accents. Vide : Idée.
     */
    fun category(raw: String?): LinkCategory {
        val key = norm(raw.orEmpty()).removePrefix("link_category_")
        if (key.isEmpty()) return LinkCategory.IDEE
        return LinkCategory.entries.find { norm(it.name) == key || norm(it.label) == key }
            ?: throw Invalid("unknown category: use one of ${LinkCategory.entries.joinToString { it.name }}")
    }

    fun title(raw: String): String {
        val t = raw.trim()
        if (t.isEmpty()) throw Invalid("title is required")
        if (t.length > MAX_TITLE) throw Invalid("title is too long (max $MAX_TITLE characters)")
        return t
    }

    /** Lien facultatif, http(s) seulement. */
    fun url(raw: String?): String {
        val u = raw.orEmpty().trim()
        if (u.isEmpty()) return ""
        val ok = Regex("^https?://[^\\s/?#]+\\.[^\\s/?#]+(\\S*)$", RegexOption.IGNORE_CASE).matches(u)
        if (!ok) throw Invalid("url must be an http(s) address")
        return u
    }

    fun description(raw: String?): String {
        val d = raw.orEmpty().trim()
        if (d.length > MAX_DESCRIPTION) throw Invalid("description is too long (max $MAX_DESCRIPTION characters)")
        return d
    }

    /** Étiquettes en minuscules, sans doublon. */
    fun tags(raw: List<String>): List<String> {
        val t = raw.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.distinct()
        if (t.size > MAX_TAGS) throw Invalid("too many tags (max $MAX_TAGS)")
        return t
    }

    fun folderName(raw: String): String {
        val n = raw.trim()
        if (n.isEmpty()) throw Invalid("folder name is required")
        if (n.length > MAX_FOLDER_NAME) throw Invalid("folder name is too long (max $MAX_FOLDER_NAME characters)")
        return n
    }

    /**
     * Dossiers où l'assistant peut ranger une idée : pas ceux où l'on n'est que
     * lecteur, ni les dossiers publics.
     */
    fun writableFolders(folders: List<Folder>, me: String?): List<Folder> =
        folders.filter { f ->
            f.visibility.uppercase() != "PUBLIC" &&
                f.collaborators.none { it.userId == me && it.role == "COLLABORATOR_ROLE_VIEWER" }
        }

    /** Dossier modifiable de ce nom (majuscules et accents ignorés). */
    fun findFolder(folders: List<Folder>, me: String?, name: String): Folder? {
        val key = norm(name)
        return writableFolders(folders, me).find { norm(it.name) == key }
    }

    private fun norm(s: String): String =
        java.text.Normalizer.normalize(s.trim().lowercase(), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
}
