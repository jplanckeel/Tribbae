package data

import java.text.Normalizer
import java.util.Calendar

/**
 * Personnalisation des dossiers (maquettes « Bannières illustrées » et
 * « Personnaliser un dossier ») : couverture, dessin choisi automatiquement,
 * compte à rebours de la date clé, tri des idées. Même logique sur le web :
 * frontend/src/lib/folderStyle.ts (garder les deux alignés ; FolderStyleTest
 * fige les cas).
 */
object FolderStyle {
    val COVERS = listOf("illu" to "Dessin", "uni" to "Uni", "tipis" to "Tipis", "pois" to "Pois", "rayures" to "Rayures", "photo" to "Photo")
    val ILLUSTRATIONS = listOf(
        "anniv" to "Anniversaire", "cadeaux" to "Cadeaux", "noel" to "Noël", "jouets" to "Jouets", "montagne" to "Montagne",
        "plage" to "Plage", "gouter" to "Goûter", "pluie" to "Jour de pluie", "automne" to "Automne",
    )
    val KEY_DATE_TYPES = listOf("anniv" to "Anniversaire", "vacances" to "Vacances", "event" to "Événement")
    val VIEW_MODES = listOf("grid" to "Grille", "list" to "Liste", "mosaic" to "Mosaïque")
    val SORTS = listOf("recent" to "Plus récentes", "date" to "Par date", "category" to "Par catégorie")

    private fun fold(s: String) = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")

    // Mots du nom → dessin, dans l'ordre de priorité (mêmes expressions que le web)
    private val KEYWORDS = listOf(
        "noel" to Regex("\\bnoel\\b|\\bsapin"),
        "anniv" to Regex("\\banniv"),
        "cadeaux" to Regex("\\bcadeau|\\bliste\\b"),
        "jouets" to Regex("\\bjouet|\\bjeux\\b|\\bjeu\\b"),
        "plage" to Regex("\\bplage|\\bmer\\b|\\bete\\b|\\bpiscine"),
        "montagne" to Regex("\\bvacances|\\bbalade|\\bmontagne|\\brando|\\bcamping|\\bnature\\b|\\bforet"),
        "gouter" to Regex("\\brecette|\\bgouter|\\bcuisine|\\brepas\\b|\\bcrepe|\\bgateau"),
        "pluie" to Regex("\\bpluie|\\binterieur|\\bbricol"),
        "automne" to Regex("\\bautomne|\\bhalloween|\\bchataigne"),
    )

    /**
     * Dessin choisi selon le nom du dossier ou sa date clé (anniversaire,
     * vacances ; décembre → Noël, septembre–novembre → automne, été → plage),
     * ou null si rien ne correspond.
     */
    fun autoIllustration(name: String, keyDate: Long = 0, keyDateType: String = ""): String? {
        val n = fold(name)
        if (keyDate > 0 && keyDateType == "anniv") return "anniv"
        KEYWORDS.firstOrNull { (_, re) -> re.containsMatchIn(n) }?.let { return it.first }
        if (keyDate > 0 && keyDateType == "vacances") return "montagne"
        if (keyDate > 0) {
            val m = Calendar.getInstance().apply { timeInMillis = keyDate }.get(Calendar.MONTH)
            if (m == Calendar.DECEMBER) return "noel"
            if (m in Calendar.SEPTEMBER..Calendar.NOVEMBER) return "automne"
            if (m in Calendar.JUNE..Calendar.AUGUST) return "plage"
        }
        return null
    }

    /** Couverture effective (cover, dessin) : choisie, sinon un dessin auto, sinon les tipis. */
    fun resolvedCover(f: Folder): Pair<String, String?> {
        val auto = autoIllustration(f.name, f.keyDate, f.keyDateType)
        val illustration = f.illustration.takeIf { k -> ILLUSTRATIONS.any { it.first == k } } ?: auto
        val cover = f.cover.takeIf { k -> COVERS.any { it.first == k } }
        return when {
            cover == "illu" -> cover to (illustration ?: "anniv")
            cover != null -> cover to illustration
            illustration != null -> "illu" to illustration
            else -> "tipis" to null
        }
    }

    /** Image de la couverture « Photo » : celle choisie, sinon la première idée illustrée du dossier. */
    fun coverPhoto(f: Folder, links: List<Link>): String? =
        f.bannerUrl.takeIf { it.startsWith("http") } ?: links.firstOrNull { it.imageUrl.startsWith("http") }?.imageUrl

    data class Countdown(val label: String, val days: Int)

    private val WEEKDAYS = listOf("Dim.", "Lun.", "Mar.", "Mer.", "Jeu.", "Ven.", "Sam.")

    private fun startOfDay(ms: Long): Calendar = Calendar.getInstance().apply {
        timeInMillis = ms
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }

    /**
     * Compte à rebours de la date clé (« Anniv. dans 24 j », « Départ J-12 »,
     * « Sam. 24 · J-3 »…). Un anniversaire revient chaque année ; une date
     * passée ne s'affiche plus.
     */
    fun countdown(keyDate: Long, type: String, now: Long = System.currentTimeMillis()): Countdown? {
        if (keyDate <= 0) return null
        val today = startOfDay(now)
        val target = startOfDay(keyDate)
        if (type == "anniv") {
            target.set(Calendar.YEAR, today.get(Calendar.YEAR))
            if (target.before(today)) target.add(Calendar.YEAR, 1)
        }
        val days = Math.round((target.timeInMillis - today.timeInMillis) / 86_400_000.0).toInt()
        if (days < 0) return null
        return when (type) {
            "anniv" -> Countdown(if (days == 0) "Anniv. aujourd'hui !" else if (days == 1) "Anniv. demain" else "Anniv. dans $days j", days)
            "vacances" -> Countdown(if (days == 0) "C'est le départ !" else "Départ J-$days", days)
            else -> Countdown(
                when {
                    days == 0 -> "Aujourd'hui"
                    days <= 7 -> "${WEEKDAYS[target.get(Calendar.DAY_OF_WEEK) - 1]} ${target.get(Calendar.DAY_OF_MONTH)} · J-$days"
                    else -> "J-$days"
                },
                days
            )
        }
    }

    fun countdown(f: Folder, now: Long = System.currentTimeMillis()) = countdown(f.keyDate, f.keyDateType, now)

    /** Idées du dossier dans l'ordre choisi (« Par date » : sorties à venir d'abord). */
    fun sortLinks(links: List<Link>, sort: String, categoryOrder: List<LinkCategory>): List<Link> {
        val recent = compareByDescending<Link> { it.createdAt.ifEmpty { it.updatedAt } }
        return when (sort) {
            "date" -> links.sortedWith(compareBy<Link> { it.eventDate?.takeIf { d -> d > 0 } == null }.thenBy { it.eventDate ?: 0 }.then(recent))
            "category" -> links.sortedWith(compareBy<Link> { categoryOrder.indexOf(it.category) }.then(recent))
            else -> links.sortedWith(recent)
        }
    }
}
