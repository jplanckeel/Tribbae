package data

import java.util.Calendar
import kotlinx.serialization.Serializable

/**
 * « C'est fait » : une idée vécue (sortie faite, recette cuisinée, livre lu,
 * cadeau acheté), autant de fois qu'on la refait, avec un souvenir facultatif.
 * L'état appartient à la tribu ; le serveur ne l'envoie qu'à elle et aux
 * membres du dossier. Mêmes libellés et mêmes cas que
 * frontend/src/lib/done.ts (done.test.ts).
 */

/** Un souvenir : une fois où l'idée a été faite. */
@Serializable
data class LinkDone(
    val id: String = "",
    val linkId: String = "",
    val userId: String = "",
    /** Jour, sans heure : AAAA-MM-JJ */
    val doneOn: String = "",
    val withUserIds: List<String> = emptyList(),
    val withChildIds: List<String> = emptyList(),
    val rating: Int = 0,
    val note: String = "",
    val photoUrl: String = "",
    val createdAt: String = "",
)

/** Ce qu'on envoie pour marquer une idée ou corriger un souvenir. Tout est facultatif. */
@Serializable
data class DoneInput(
    val doneOn: String = "",
    val withUserIds: List<String> = emptyList(),
    val withChildIds: List<String> = emptyList(),
    val rating: Int = 0,
    val note: String = "",
    val photoUrl: String = "",
    /** « À refaire » ; null = inchangé */
    val redo: Boolean? = null,
)

/** « Notre année en idées » (GET /v1/done/summary) : chaque fois compte. */
@Serializable
data class DoneSummary(
    val year: Int = 0,
    val outings: Int = 0,
    val recipes: Int = 0,
    val books: Int = 0,
    val total: Int = 0,
    /** 1 à 12 ; 0 si rien n'a été fait */
    val bestMonth: Int = 0,
    val bestMonthCount: Int = 0,
)

object Done {
    data class Words(
        /** Bouton principal, idée à faire */
        val verb: String,
        /** Bouton d'une idée déjà faite */
        val again: String,
        /** Titre de la feuille souvenir */
        val ask: String,
        /** Tampon posé sur le visuel */
        val stamp: String,
        /** « Fait le », « Cuisinée le », « Lu en », « Acheté le » */
        val prefix: String,
    )

    private val DEFAULT = Words("C'est fait", "On l'a refait", "Comment c'était ?", "Fait", "Fait le")

    fun words(category: LinkCategory): Words = when (category) {
        LinkCategory.ACTIVITE -> Words("On l'a fait !", "On l'a refait", "Comment c'était ?", "Fait", "Fait le")
        LinkCategory.EVENEMENT -> Words("On y est allés !", "On y est retournés", "Comment c'était ?", "Fait", "Fait le")
        LinkCategory.RECETTE -> Words("On l'a cuisinée", "On l'a refaite", "C'était bon ?", "Cuisinée", "Cuisinée le")
        LinkCategory.LIVRE -> Words("On l'a lu", "On l'a relu", "Il vous a plu ?", "Lu", "Lu en")
        LinkCategory.CADEAU -> Words("Acheté", "Racheté", "C'est acheté ?", "Acheté", "Acheté le")
        else -> DEFAULT
    }

    fun isDone(l: Link): Boolean = l.doneCount > 0

    /** Dernier jour où l'idée a été faite (AAAA-MM-JJ), ou "" si elle ne l'est pas. */
    fun doneDay(l: Link): String = l.lastDoneAt.take(10)

    private val MONTHS_SHORT = listOf("janv.", "févr.", "mars", "avr.", "mai", "juin", "juil.", "août", "sept.", "oct.", "nov.", "déc.")
    private val MONTHS_LONG = listOf("janvier", "février", "mars", "avril", "mai", "juin", "juillet", "août", "septembre", "octobre", "novembre", "décembre")
    private val WEEKDAYS = listOf("Dimanche", "Lundi", "Mardi", "Mercredi", "Jeudi", "Vendredi", "Samedi")

    private fun parts(day: String): Triple<Int, Int, Int>? {
        val p = day.split("-").mapNotNull { it.toIntOrNull() }
        return if (p.size == 3 && p[1] in 1..12 && p[2] in 1..31) Triple(p[0], p[1], p[2]) else null
    }

    private fun cal(ms: Long): Calendar = Calendar.getInstance().apply { timeInMillis = ms }
    private fun yearOf(ms: Long) = cal(ms).get(Calendar.YEAR)

    /** Jour local d'un instant (AAAA-MM-JJ). */
    fun dayOf(ms: Long): String = cal(ms).let {
        "%04d-%02d-%02d".format(it.get(Calendar.YEAR), it.get(Calendar.MONTH) + 1, it.get(Calendar.DAY_OF_MONTH))
    }

    /** « 26 oct. », « 1er nov. », avec l'année si ce n'est pas l'année en cours. */
    fun formatDay(day: String, now: Long): String {
        val (y, m, d) = parts(day) ?: return ""
        return "${if (d == 1) "1er" else d} ${MONTHS_SHORT[m - 1]}${if (y == yearOf(now)) "" else " $y"}"
    }

    /** « octobre », pour un livre : le mois seul, avec l'année si ce n'est pas l'année en cours. */
    fun formatMonth(day: String, now: Long): String {
        val (y, m, _) = parts(day) ?: return ""
        return "${MONTHS_LONG[m - 1]}${if (y == yearOf(now)) "" else " $y"}"
    }

    /** État d'une idée faite : « Fait le 26 oct. », « Cuisinée 3 fois », « Lu en octobre », « Acheté le 2 nov. ». */
    fun label(l: Link, now: Long): String {
        if (!isDone(l)) return ""
        val w = words(l.category)
        if (l.category == LinkCategory.RECETTE && l.doneCount > 1) return "Cuisinée ${l.doneCount} fois"
        val day = doneDay(l)
        if (parts(day) == null) return w.stamp
        return "${w.prefix} ${if (l.category == LinkCategory.LIVRE) formatMonth(day, now) else formatDay(day, now)}"
    }

    /** Tampon de la fiche : « Fait », « Cuisinée ×3 ». */
    fun stamp(l: Link): String = words(l.category).stamp.let { if (l.doneCount > 1) "$it ×${l.doneCount}" else it }

    /** Idée Cadeau d'une liste de cadeaux : son achat est une réservation, jamais un « fait ». */
    fun inGiftList(l: Link, folder: Folder?): Boolean = l.category == LinkCategory.CADEAU && folder?.giftReservations == true

    /** Seul un cadeau acheté s'estompe (60 %, titre barré) : une activité faite reste un souvenir. */
    fun dimmed(l: Link): Boolean = l.category == LinkCategory.CADEAU && isDone(l)

    private fun startOfDay(ms: Long): Long = cal(ms).apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    /** Date d'événement en ms (d'anciennes données sont en secondes). */
    fun eventMs(l: Link): Long? = l.eventDate?.takeIf { it > 0 }?.let { if (it < 100_000_000_000L) it * 1000 else it }

    private val DATED = setOf(LinkCategory.ACTIVITE, LinkCategory.EVENEMENT)

    /**
     * La sortie ou l'activité datée est passée (la veille ou avant), n'est pas
     * faite et personne n'a répondu « Pas cette fois » : on demande « Fait ? ».
     */
    fun awaitingConfirmation(l: Link, now: Long): Boolean {
        val at = eventMs(l) ?: return false
        return l.category in DATED && !isDone(l) && !l.donePromptDismissed && at < startOfDay(now)
    }

    /** « Samedi » si c'était dans la semaine, sinon « 26 oct. ». */
    fun promptDay(l: Link, now: Long): String {
        val at = eventMs(l) ?: return ""
        val days = Math.round((startOfDay(now) - startOfDay(at)) / 86_400_000.0).toInt()
        if (days == 1) return "Hier"
        return if (days in 1..6) WEEKDAYS[cal(at).get(Calendar.DAY_OF_WEEK) - 1] else formatDay(dayOf(at), now)
    }

    /** Méta d'une vignette en attente : « Samedi · c'était bien ? ». */
    fun awaitingLabel(l: Link, now: Long): String = "${promptDay(l, now)} · c'était bien ?"

    /** Question du bandeau et de la notification. */
    fun promptQuestion(category: LinkCategory, title: String): String =
        if (category == LinkCategory.EVENEMENT) "Vous êtes allés à « $title » ?" else "Vous avez fait « $title » ?"

    /** Sur-titre du bandeau : « Samedi · Ferme du Mas ». */
    fun promptKicker(l: Link, now: Long): String = listOf(promptDay(l, now), l.location.trim()).filter { it.isNotEmpty() }.joinToString(" · ")

    private val COUNTED = setOf(LinkCategory.ACTIVITE, LinkCategory.EVENEMENT, LinkCategory.RECETTE, LinkCategory.LIVRE)

    data class Progress(val done: Int, val total: Int)

    /**
     * Avancement d'un dossier, sur ses activités, sorties, recettes et livres.
     * null (ni anneau ni barre) s'il en compte moins de 3 ou si c'est une liste de cadeaux.
     */
    fun folderProgress(links: List<Link>, giftList: Boolean = false): Progress? {
        if (giftList) return null
        val counted = links.filter { it.category in COUNTED }
        if (counted.size < 3) return null
        return Progress(counted.count(::isDone), counted.size)
    }

    fun progressLabel(p: Progress): String = "${p.done} sur ${p.total} faites"

    fun progressHint(p: Progress): String = when (val left = p.total - p.done) {
        in Int.MIN_VALUE..0 -> "Bravo, tout est fait !"
        1 -> "Plus qu'une à faire"
        else -> "Encore $left à faire"
    }

    enum class Filter(val label: String) { ALL("Toutes"), TODO("À faire"), DONE("Faites") }

    fun matches(l: Link, filter: Filter): Boolean = filter == Filter.ALL || isDone(l) == (filter == Filter.DONE)

    fun counts(links: List<Link>): Map<Filter, Int> {
        val done = links.count(::isDone)
        return mapOf(Filter.ALL to links.size, Filter.TODO to links.size - done, Filter.DONE to done)
    }

    private fun created(l: Link) = l.createdAt.ifEmpty { l.updatedAt }

    /**
     * Tri « À faire d'abord » : les idées en attente de confirmation, puis celles
     * à faire (par date d'événement, puis les plus récentes), puis les faites
     * (la dernière faite en bas).
     */
    fun sortTodoFirst(links: List<Link>, now: Long): List<Link> {
        fun rank(l: Link) = if (awaitingConfirmation(l, now)) 0 else if (isDone(l)) 2 else 1
        return links.sortedWith { a, b ->
            val ra = rank(a); val rb = rank(b)
            when {
                ra != rb -> ra - rb
                ra == 2 -> doneDay(a).compareTo(doneDay(b)).takeIf { it != 0 } ?: created(b).compareTo(created(a))
                else -> {
                    val da = eventMs(a); val db = eventMs(b)
                    when {
                        da != null && db != null && da != db -> da.compareTo(db)
                        (da != null) != (db != null) -> if (da != null) -1 else 1
                        else -> created(b).compareTo(created(a))
                    }
                }
            }
        }
    }

    /** « Léo, Emma et Camille » */
    fun joinNames(names: List<String>): String =
        if (names.size <= 1) names.firstOrNull().orEmpty() else "${names.dropLast(1).joinToString(", ")} et ${names.last()}"

    /** Ligne du souvenir : « Avec Léo, Emma et Camille · 26 oct. », « … · 3 fois, dernière le 8 nov. ». */
    fun memoryLine(names: List<String>, day: String, count: Int, now: Long): String {
        val date = formatDay(day, now)
        val whenText = if (count > 1) "$count fois, dernière le $date" else date
        return if (names.isNotEmpty()) "Avec ${joinNames(names)} · $whenText" else whenText
    }

    private val STAR_WORDS = listOf("Touchez une étoile", "Bof", "Pas mal", "Bien", "Super", "Génial !")
    fun starWord(rating: Int): String = STAR_WORDS.getOrElse(rating) { STAR_WORDS[0] }

    /** Titre de la célébration. */
    fun celebrationTitle(category: LinkCategory, again: Boolean): String = when {
        again -> "${words(category).again} !"
        category == LinkCategory.RECETTE -> "Miam, c'est noté !"
        else -> "C'est fait !"
    }

    /** Toast après un marquage : « 5 sur 8 faites », ou « C'est noté » hors d'un dossier suivi. */
    fun toast(p: Progress?): String = p?.let(::progressLabel) ?: "C'est noté"

    /** Explorer : « 120 familles l'ont fait » (le serveur n'envoie le nombre qu'à partir de 5). */
    fun familiesLabel(n: Int): String = if (n > 0) "$n familles l'ont fait" else ""

    /** Choix « Quand ? » de la feuille souvenir : aujourd'hui et hier. */
    fun dayChoices(now: Long): List<Pair<String, String>> =
        listOf("Aujourd'hui" to dayOf(now), "Hier" to dayOf(startOfDay(now) - 12 * 3_600_000L))

    /** Message d'un refus du serveur (mêmes textes que frenchMessage dans lib/api.ts). */
    fun errorMessage(raw: String): String = when {
        "too many ideas marked done" in raw -> "Vous avez marqué beaucoup d'idées aujourd'hui : réessayez demain."
        "gift list ideas are marked" in raw -> "Dans une liste de cadeaux, c'est la réservation qui dit qu'un cadeau est acheté."
        "doneOn cannot be in the future" in raw -> "La date ne peut pas être dans le futur."
        "(403)" in raw -> "Vous n'avez pas les droits pour cette action."
        "(404)" in raw -> "Introuvable — l'élément a peut-être été supprimé."
        else -> "Connexion impossible : l'idée n'a pas pu être marquée comme faite."
    }
}
