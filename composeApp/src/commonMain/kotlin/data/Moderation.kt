package data

/** Motifs de signalement : mêmes clés et libellés que frontend/src/lib/moderation.ts (ModerationTest). */
object Moderation {
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
