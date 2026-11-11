package data

/** Motifs de signalement : mêmes clés et libellés que frontend/src/lib/moderation.ts (ModerationTest). */
object Moderation {
    /**
     * Message d'un signalement refusé, d'après l'erreur du serveur (mêmes textes
     * que frenchMessage côté web).
     */
    fun reportError(serverMessage: String): String = when {
        serverMessage.contains("too many reports") || serverMessage.contains("(429)") -> "Vous avez envoyé beaucoup de signalements aujourd'hui : réessayez demain."
        serverMessage.contains("cannot report your own content") -> "Vous ne pouvez pas signaler votre contenu ni celui de votre tribu."
        serverMessage.contains("(404)") -> "Ce contenu n'existe plus."
        else -> "Le signalement n'a pas pu partir. Vérifiez votre connexion."
    }

    val REASONS = listOf(
        "inappropriate" to "Contenu inapproprié",
        "harassment" to "Harcèlement ou propos blessants",
        "spam" to "Spam ou publicité",
        "other" to "Autre raison",
    )
    const val DETAILS_MAX = 500

    fun title(targetType: String) = when (targetType) {
        "idea" -> "Signaler cette idée"
        "comment" -> "Signaler ce commentaire"
        else -> "Signaler ce compte"
    }

    fun reasonLabel(reason: String) = REASONS.firstOrNull { it.first == reason }?.second ?: "Autre raison"
}
