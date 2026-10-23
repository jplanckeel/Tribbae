package data

import java.util.Calendar

/**
 * « Idées avec l'IA v2 » : la demande est une phrase à compléter
 * (« Je cherche [des activités] pour [Léo] [ce week-end], [à la maison],
 * budget [gratuit]. »), envoyée telle quelle comme prompt. Dupliqué dans
 * frontend/src/lib/aiRequest.ts : garder les deux en phase (AiRequestTest).
 */
object AiRequests {
    enum class Blank { What, Who, When, Where, Budget }
    data class Option(val key: String, val label: String)

    val WHAT = listOf(
        Option("activites", "des activités"), Option("sorties", "des sorties"), Option("recettes", "des recettes"),
        Option("cadeaux", "des idées cadeaux"), Option("livres", "des livres"),
    )
    val WHEN = listOf(
        Option("weekend", "ce week-end"), Option("mercredi", "mercredi après-midi"), Option("vacances", "pendant les vacances"),
        Option("soir", "un soir de semaine"), Option("anniv", "pour un anniversaire"),
    )
    val WHERE = listOf(Option("maison", "à la maison"), Option("dehors", "dehors"), Option("pres", "près de chez nous"), Option("voiture", "en voiture"))
    val BUDGET = listOf(Option("gratuit", "gratuit"), Option("20", "moins de 20 €"), Option("libre", "peu importe"))

    /** « Toute la tribu » : exclusif avec les enfants. */
    const val ALL = "tous"

    /** Sans enfant enregistré : des tranches d'âge à la place des prénoms. */
    private val GENERIC_WHO = listOf(
        Option("petits", "des tout-petits (0–3 ans)"), Option("enfants", "des enfants (3–10 ans)"), Option("ados", "des ados"),
    )

    val HINTS = mapOf(
        Blank.What to "Vous cherchez plutôt…", Blank.Who to "Pour qui ? (plusieurs choix possibles)",
        Blank.When to "Quand ?", Blank.Where to "Où ?", Blank.Budget to "Quel budget ?",
    )

    data class Request(
        val what: String = "activites",
        /** Ids d'enfants, ALL, ou une tranche générique. */
        val who: List<String> = listOf(ALL),
        val whenKey: String = "weekend",
        val where: String = "maison",
        val budget: String = "gratuit",
        /** Le détail facultatif (« Léo adore l'espace en ce moment »). */
        val detail: String = "",
    ) {
        fun valueOf(b: Blank): String = when (b) {
            Blank.What -> what; Blank.When -> whenKey; Blank.Where -> where; Blank.Budget -> budget; Blank.Who -> who.firstOrNull().orEmpty()
        }

        fun with(b: Blank, key: String): Request = when (b) {
            Blank.What -> copy(what = key); Blank.When -> copy(whenKey = key); Blank.Where -> copy(where = key)
            Blank.Budget -> copy(budget = key); Blank.Who -> copy(who = toggleWho(who, key))
        }
    }

    private fun labelOf(opts: List<Option>, key: String) = opts.find { it.key == key }?.label.orEmpty()

    /** Âge affiché d'un enfant (« 18 mois », « 9 ans »), comme ui.campfire.formatAge. */
    private fun ageLabel(birth: Long, now: Long): String {
        val b = Calendar.getInstance().apply { timeInMillis = birth }
        val n = Calendar.getInstance().apply { timeInMillis = now }
        var m = (n.get(Calendar.YEAR) - b.get(Calendar.YEAR)) * 12 + n.get(Calendar.MONTH) - b.get(Calendar.MONTH)
        if (n.get(Calendar.DAY_OF_MONTH) < b.get(Calendar.DAY_OF_MONTH)) m--
        m = m.coerceAtLeast(0)
        return if (m < 24) "$m mois" else "${m / 12} ans"
    }

    /** Choix « pour qui » : les enfants de la tribu (« Léo, 9 ans »), puis toute la tribu. */
    fun whoOptions(children: List<Child>, now: Long = System.currentTimeMillis()): List<Option> =
        (if (children.isEmpty()) GENERIC_WHO else children.map { Option(it.id, "${it.name}, ${ageLabel(it.birthDate, now)}") }) +
            Option(ALL, "toute la tribu")

    fun optionsFor(b: Blank, children: List<Child>, now: Long = System.currentTimeMillis()): List<Option> = when (b) {
        Blank.What -> WHAT; Blank.Who -> whoOptions(children, now); Blank.When -> WHEN; Blank.Where -> WHERE; Blank.Budget -> BUDGET
    }

    /** Demande de départ : les activités du week-end, pour l'enfant choisi (ou toute la tribu). */
    fun defaultRequest(children: List<Child>, childId: String? = null, detail: String = ""): Request =
        Request(who = if (childId != null && children.any { it.id == childId }) listOf(childId) else listOf(ALL), detail = detail)

    /** Coche ou décoche un choix « pour qui » (toute la tribu exclut les autres). */
    fun toggleWho(who: List<String>, key: String): List<String> {
        if (key == ALL) return listOf(ALL)
        val rest = who.filter { it != ALL }
        return if (key in rest) rest - key else rest + key
    }

    private fun join(parts: List<String>) =
        if (parts.size < 2) parts.joinToString("") else parts.dropLast(1).joinToString(", ") + " et " + parts.last()

    /** « Léo et Emma », « toute la tribu », « qui ? » — avec les âges pour le prompt. */
    private fun whoText(r: Request, children: List<Child>, withAges: Boolean, now: Long): String {
        if (r.who.isEmpty()) return if (withAges) "toute la tribu" else "qui ?"
        if (ALL in r.who) return "toute la tribu"
        return join(r.who.mapNotNull { k ->
            val c = children.find { it.id == k }
            when {
                c == null -> labelOf(GENERIC_WHO, k).ifEmpty { null }
                withAges -> "${c.name} (${ageLabel(c.birthDate, now)})"
                else -> c.name
            }
        })
    }

    /** Texte de chaque mot à toucher. */
    fun blankLabel(b: Blank, r: Request, children: List<Child>, now: Long = System.currentTimeMillis()): String =
        if (b == Blank.Who) whoText(r, children, false, now) else labelOf(optionsFor(b, children, now), r.valueOf(b))

    /** À qui s'adressent les idées, en court (« Léo », « la tribu »). */
    fun whoShort(r: Request, children: List<Child>, now: Long = System.currentTimeMillis()): String =
        if (r.who.isEmpty() || ALL in r.who) "la tribu" else whoText(r, children, false, now)

    /** La phrase envoyée à l'IA, telle qu'affichée (avec l'âge des enfants nommés) et le détail. */
    fun promptOf(r: Request, children: List<Child>, now: Long = System.currentTimeMillis()): String {
        val sentence = "Je cherche ${labelOf(WHAT, r.what)} pour ${whoText(r, children, true, now)} ${labelOf(WHEN, r.whenKey)}, " +
            "${labelOf(WHERE, r.where)}, budget ${labelOf(BUDGET, r.budget)}."
        val detail = r.detail.trim().replace(Regex("[.!\\s]*$"), "")
        return if (detail.isEmpty()) sentence else "$sentence ${detail.replaceFirstChar { it.uppercase() }}."
    }

    /** « 5 idées pour Léo ce week-end » */
    fun resultTitle(n: Int, r: Request, children: List<Child>, now: Long = System.currentTimeMillis()): String =
        "$n idée${if (n > 1) "s" else ""} pour ${whoShort(r, children, now)} ${labelOf(WHEN, r.whenKey)}"

    /** « activités · à la maison · gratuit » */
    fun resultSubtitle(r: Request): String =
        listOf(labelOf(WHAT, r.what).removePrefix("des "), labelOf(WHERE, r.where), labelOf(BUDGET, r.budget)).joinToString(" · ")

    /** Nom du dossier créé pour ranger les idées : l'inspiration, sinon « Activités pour Léo ». */
    fun folderNameFor(r: Request, children: List<Child>, inspiration: String? = null, now: Long = System.currentTimeMillis()): String {
        if (!inspiration.isNullOrEmpty()) return inspiration
        val what = labelOf(WHAT, r.what).removePrefix("des ").removePrefix("idées ")
        return "${what.replaceFirstChar { it.uppercase() }} pour ${whoShort(r, children, now)}".take(80)
    }

    /** Inspiration qui remplit la phrase d'un coup ; `d` : icône (chemin SVG 24×24). */
    data class Inspiration(val label: String, val d: String, val bg: Long, val ink: Long, val apply: (Request) -> Request)

    /** L'anniversaire vise l'enfant dont l'anniversaire arrive le plus tôt. */
    fun inspirations(children: List<Child>, now: Long = System.currentTimeMillis()): List<Inspiration> {
        val next = children.minByOrNull { FolderStyle.countdown(it.birthDate, "anniv", now)?.days ?: 999 }
        return listOf(
            Inspiration("Jour de pluie", "M7 15a4 4 0 0 1 .5-8A6 6 0 0 1 19 9a3 3 0 0 1 0 6z M9 19l-1 2M13 19l-1 2M17 19l-1 2", 0xFFECE1EA, 0xFF5E4558) {
                it.copy(what = "activites", where = "maison", budget = "gratuit", whenKey = "weekend")
            },
            Inspiration(next?.let { "Anniversaire de ${it.name}" } ?: "Un anniversaire", "M3 8h18v5H3z M5 13v8h14v-8M12 8v13", 0xFFFCE3D0, 0xFFA04812) {
                it.copy(what = "cadeaux", who = listOf(next?.id ?: ALL), budget = "20", whenKey = "anniv", where = "pres")
            },
            Inspiration("Goûter express", "M7 3v8M5 3v5a2 2 0 0 0 4 0V3M7 11v10M17 21V3c-2 1.5-3 4-3 7h3", 0xFFE2EDD9, 0xFF3E6630) {
                it.copy(what = "recettes", who = listOf(ALL), whenKey = "mercredi", where = "maison", budget = "gratuit")
            },
            Inspiration("Grand air", "M3 20l6-11 4 6 3-4 5 9z", 0xFFDCEDF3, 0xFF235C77) {
                it.copy(what = "sorties", where = "dehors", whenKey = "weekend", budget = "libre")
            },
        )
    }

    /** Messages qui défilent pendant que l'IA rédige (elle ne cherche pas sur le web). */
    fun waitingMessages(r: Request, children: List<Child>, now: Long = System.currentTimeMillis()): List<String> = listOf(
        "On rassemble vos envies…",
        "On écarte les idées trop compliquées…",
        "On choisit le meilleur pour ${whoShort(r, children, now)}…",
        "On rédige chaque idée…",
        "Encore quelques secondes…",
    )
}
