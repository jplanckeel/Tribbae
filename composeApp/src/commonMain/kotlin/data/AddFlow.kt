package data

/**
 * Parcours « Ajouter une idée » (maquette « Ajouter une idée v2 ») : dossiers
 * suggérés, « qui la verra », tranches d'âge. Même logique sur le web :
 * frontend/src/lib/addFlow.ts (garder les deux alignés ; AddFlowTest fige les cas).
 */
object AddFlow {
    data class Suggestion(val folder: Folder, val reason: String)

    private const val MIN = 60_000L
    private const val HOUR = 60 * MIN
    private const val DAY = 24 * HOUR

    private fun parseIso(iso: String): Long? =
        if (iso.isBlank()) null else runCatching { java.time.Instant.parse(iso).toEpochMilli() }.getOrNull()

    /** « à l'instant », « il y a 3 h », « hier », « il y a 2 jours », « il y a 3 semaines ». */
    fun ago(iso: String, now: Long = System.currentTimeMillis()): String {
        val t = parseIso(iso) ?: return "récemment"
        val d = (now - t).coerceAtLeast(0)
        return when {
            d < 2 * MIN -> "à l'instant"
            d < HOUR -> "il y a ${d / MIN} min"
            d < DAY -> "il y a ${d / HOUR} h"
            d / DAY == 1L -> "hier"
            d / DAY < 14 -> "il y a ${d / DAY} jours"
            else -> "il y a ${d / DAY / 7} semaines"
        }
    }

    /** Version courte (« 2 h », « 3 j »). */
    fun agoShort(iso: String, now: Long = System.currentTimeMillis()): String {
        val t = parseIso(iso) ?: return ""
        val d = (now - t).coerceAtLeast(0)
        return when {
            d < HOUR -> "${(d / MIN).coerceAtLeast(1)} min"
            d < DAY -> "${d / HOUR} h"
            else -> "${d / DAY} j"
        }
    }

    private fun firstName(c: Collaborator) = c.displayName.ifBlank { c.email.ifBlank { "un proche" } }

    /**
     * Trois dossiers proposés pour ranger une idée de catégorie `cat` : celui où
     * l'on range ce genre d'idées, la date clé proche (cadeau), le dernier
     * utilisé, un dossier partagé, puis les plus récents. `preset` passe en premier.
     */
    fun suggestFolders(
        cat: LinkCategory, folders: List<Folder>, links: List<Link>, preset: String? = null,
        now: Long = System.currentTimeMillis(),
    ): List<Suggestion> {
        val out = mutableListOf<Suggestion>()
        fun add(f: Folder?, reason: String) {
            if (f != null && out.size < 3 && out.none { it.folder.id == f.id }) out += Suggestion(f, reason)
        }
        val byId = folders.associateBy { it.id }
        add(preset?.let { byId[it] }, "Le dossier ouvert")

        links.filter { it.folderId in byId && it.category == cat }.groupingBy { it.folderId!! }.eachCount()
            .maxByOrNull { it.value }?.let { add(byId[it.key], "Vous y rangez vos ${pluralOf(cat).lowercase()}") }

        if (cat == LinkCategory.CADEAU) {
            folders.mapNotNull { f -> FolderStyle.countdown(f, now)?.takeIf { it.days <= 90 }?.let { f to it } }
                .minByOrNull { it.second.days }?.let { (f, cd) -> add(f, cd.label) }
        }

        links.filter { it.folderId in byId }.maxByOrNull { it.createdAt }?.let { add(byId[it.folderId], "Utilisé ${ago(it.createdAt, now)}") }

        folders.filter { it.collaborators.isNotEmpty() }.maxByOrNull { it.updatedAt }
            ?.let { add(it, "Partagé avec ${firstName(it.collaborators.first())}") }

        folders.sortedByDescending { it.updatedAt }.forEach { add(it, "Modifié récemment") }
        return out
    }

    private fun pluralOf(c: LinkCategory) = when (c) {
        LinkCategory.IDEE -> "Idées"; LinkCategory.CADEAU -> "Cadeaux"; LinkCategory.ACTIVITE -> "Activités"
        LinkCategory.EVENEMENT -> "Sorties"; LinkCategory.RECETTE -> "Recettes"; LinkCategory.LIVRE -> "Livres"
        LinkCategory.DECORATION -> "Déco"
    }

    /** « Inès la verra », « Inès, Camille et 1 autre la verront », « Visible par toute la communauté »… */
    fun whoSees(folder: Folder?, isPublic: Boolean): String {
        if (folder == null) return if (isPublic) "Visible par toute la communauté" else "Visible par vous et votre tribu"
        if (folder.visibility.uppercase().contains("PUBLIC")) return "Visible par toute la communauté"
        val names = folder.collaborators.map(::firstName)
        return when (names.size) {
            0 -> "Visible par vous et votre tribu"
            1 -> "${names[0]} la verra"
            2 -> "${names[0]} et ${names[1]} la verront"
            else -> "${names[0]}, ${names[1]} et ${names.size - 2} autre${if (names.size > 3) "s" else ""} la verront"
        }
    }

    /** Tranches cochées d'un âge (« 3–10 ans » → 3–6 et 6–10). */
    fun selectedBands(ageRange: String): List<String> {
        val r = AgeRanges.parse(ageRange) ?: return emptyList()
        return AgeRanges.BANDS.filter { AgeRanges.covers(r, it.second) }.map { it.first }
    }

    /** Âge écrit à partir des tranches cochées (leur enveloppe), "" si aucune. */
    fun bandsToAge(keys: List<String>): String {
        val ranges = AgeRanges.BANDS.filter { it.first in keys }.map { it.second }
        if (ranges.isEmpty()) return ""
        return AgeRanges.format(ranges.reduce { a, b -> AgeRanges.hull(a, b) })
    }
}
