package data

import java.time.Instant
import java.time.ZoneId
import java.util.Calendar
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Logique de « Ma tribu » (maquette « Ma tribu v2 »), dupliquée dans
 * frontend/src/lib/tribe.ts : garder les deux en phase (TribePageTest).
 */
object TribePage {
    /** Places autour du feu dans l'en-tête : au-delà, une pastille « +N ». */
    const val RING_SLOTS = 5

    /**
     * Position d'un avatar sur l'arc au-dessus du tipi, en dp depuis le centre
     * du feu (x vers la droite, y vers le bas). L'arc va de 155° (gauche) à 25° (droite).
     */
    fun ringPosition(index: Int, count: Int, radius: Int = 105): Pair<Int, Int> {
        val angle = if (count <= 1) 90.0 else 155.0 - index * 130.0 / (count - 1)
        val a = Math.toRadians(angle)
        return (radius * cos(a)).roundToInt() to (-radius * sin(a)).roundToInt()
    }

    /** Un proche du cercle : il a accès à un ou plusieurs dossiers de la tribu. */
    data class CircleMember(
        val userId: String,
        val name: String,
        val email: String,
        /** Peut ajouter des idées dans au moins un dossier. */
        val editor: Boolean,
        val folders: List<Folder>,
    )

    /**
     * Le cercle : les collaborateurs des dossiers de la tribu, regroupés par
     * personne. Les membres du foyer (isPeer) n'en font pas partie.
     */
    fun circleOf(folders: List<Folder>, isPeer: (String) -> Boolean): List<CircleMember> {
        val byId = LinkedHashMap<String, CircleMember>()
        for (f in folders) {
            for (c in f.collaborators) {
                if (c.userId.isBlank() || isPeer(c.userId)) continue
                val m = byId[c.userId] ?: CircleMember(c.userId, c.displayName.ifBlank { c.email.ifBlank { "Proche" } }, c.email, false, emptyList())
                byId[c.userId] = m.copy(
                    editor = m.editor || c.role == "COLLABORATOR_ROLE_EDITOR",
                    folders = if (m.folders.any { it.id == f.id }) m.folders else m.folders + f,
                )
            }
        }
        val collator = java.text.Collator.getInstance(java.util.Locale.FRENCH)
        return byId.values.sortedWith(compareByDescending<CircleMember> { it.folders.size }.thenComparator { a, b -> collator.compare(a.name, b.name) })
    }

    data class YearInIdeas(
        val outings: Int = 0,
        val recipes: Int = 0,
        val gifts: Int = 0,
        val total: Int = 0,
        /** Mois (0-11) où la tribu a gardé le plus d'idées, et combien. */
        val bestMonth: Pair<Int, Int>? = null,
    )

    /** « Notre année en idées » : ce que la tribu a gardé pendant l'année civile. */
    fun yearInIdeas(links: List<Link>, year: Int, zone: ZoneId = ZoneId.systemDefault()): YearInIdeas {
        var outings = 0; var recipes = 0; var gifts = 0; var total = 0
        val months = IntArray(12)
        for (l in links) {
            val d = runCatching { Instant.parse(l.createdAt).atZone(zone) }.getOrNull() ?: continue
            if (d.year != year) continue
            total++
            months[d.monthValue - 1]++
            when (l.category) {
                LinkCategory.EVENEMENT, LinkCategory.ACTIVITE -> outings++
                LinkCategory.RECETTE -> recipes++
                LinkCategory.CADEAU -> gifts++
                else -> {}
            }
        }
        val max = months.max()
        return YearInIdeas(outings, recipes, gifts, total, if (max > 0) months.indexOf(max) to max else null)
    }

    private val MONTHS = listOf("janvier", "février", "mars", "avril", "mai", "juin", "juillet", "août", "septembre", "octobre", "novembre", "décembre")
    fun monthName(m: Int): String = MONTHS.getOrElse(m) { "" }

    private fun ageYears(birth: Long, now: Long): Float {
        val b = Calendar.getInstance().apply { timeInMillis = birth }
        val n = Calendar.getInstance().apply { timeInMillis = now }
        var m = (n.get(Calendar.YEAR) - b.get(Calendar.YEAR)) * 12 + n.get(Calendar.MONTH) - b.get(Calendar.MONTH)
        if (n.get(Calendar.DAY_OF_MONTH) < b.get(Calendar.DAY_OF_MONTH)) m--
        return m.coerceAtLeast(0) / 12f
    }

    /**
     * Idées pour un enfant : celles des dossiers « Pour qui » qui le citent, et
     * celles dont la tranche d'âge lui correspond.
     */
    fun ideasForChild(links: List<Link>, folders: List<Folder>, child: Child, now: Long = System.currentTimeMillis()): Int {
        val forKid = folders.filter { child.id in it.childIds }.map { it.id }.toSet()
        val years = ageYears(child.birthDate, now)
        return links.count { (it.folderId != null && it.folderId in forKid) || AgeRanges.matches(it.ageRange, years, years) }
    }

    /** « Anniversaire le 3 février » */
    fun birthdayLabel(birthDate: Long): String {
        val d = Calendar.getInstance().apply { timeInMillis = birthDate }
        val day = d.get(Calendar.DAY_OF_MONTH)
        return "Anniversaire le ${if (day == 1) "1er" else day} ${monthName(d.get(Calendar.MONTH))}"
    }
}
