package ui.campfire

import data.Link
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// java.time n'est disponible qu'à partir de l'API 26 (minSdk 24) : on reste sur java.text.

private val FR = Locale.FRENCH

private fun cap(s: String) = s.replaceFirstChar { it.titlecase(FR) }

/** « Vendredi 16 octobre » */
fun formatToday(): String = cap(SimpleDateFormat("EEEE d MMMM", FR).format(Date()))

/** « Sam. » et « 19 » pour la vignette calendrier. */
fun dayBadge(ms: Long): Pair<String, String> {
    val wd = SimpleDateFormat("EEE", FR).format(Date(ms)).removeSuffix(".")
    val day = SimpleDateFormat("d", FR).format(Date(ms))
    return "${cap(wd)}." to day
}

/** « Sam. 19 oct. » */
fun formatShortDate(ms: Long): String = cap(SimpleDateFormat("EEE d MMM", FR).format(Date(ms)))

/** « 10h », « 14h30 », vide à minuit (date sans heure). */
fun formatTime(ms: Long): String {
    val c = Calendar.getInstance().apply { timeInMillis = ms }
    val h = c.get(Calendar.HOUR_OF_DAY)
    val m = c.get(Calendar.MINUTE)
    if (h == 0 && m == 0) return ""
    return if (m == 0) "${h}h" else "${h}h${m.toString().padStart(2, '0')}"
}

/** Le mobile stocke des ms, d'anciennes données web des secondes. */
fun eventMs(link: Link): Long? = link.eventDate?.takeIf { it > 0 }?.let { if (it < 100_000_000_000L) it * 1000 else it }

private fun startOfToday(): Long = Calendar.getInstance().apply {
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

/** Idées datées à venir, de la plus proche à la plus lointaine. */
fun upcoming(links: List<Link>): List<Pair<Link, Long>> {
    val from = startOfToday()
    return links.mapNotNull { l -> eventMs(l)?.let { l to it } }.filter { it.second >= from }.sortedBy { it.second }
}

/** Rappels des 7 prochains jours (pastille de la cloche). */
fun soonReminders(links: List<Link>): Int {
    val limit = System.currentTimeMillis() + 7L * 24 * 3600 * 1000
    return upcoming(links).count { it.first.reminderEnabled && it.second <= limit }
}

/** Âge en mois à partir d'une date de naissance (ms). */
fun ageInMonths(birth: Long): Int {
    val b = Calendar.getInstance().apply { timeInMillis = birth }
    val n = Calendar.getInstance()
    var m = (n.get(Calendar.YEAR) - b.get(Calendar.YEAR)) * 12 + n.get(Calendar.MONTH) - b.get(Calendar.MONTH)
    if (n.get(Calendar.DAY_OF_MONTH) < b.get(Calendar.DAY_OF_MONTH)) m--
    return m.coerceAtLeast(0)
}

fun formatAge(birth: Long): String {
    val m = ageInMonths(birth)
    return if (m < 24) "$m mois" else "${m / 12} ans"
}

/** Tranche d'âge [min, max] en années depuis un texte libre (« 3–10 ans », « 18 mois », « 6+ »). */
fun parseAgeRange(text: String): Pair<Float, Float>? {
    val lower = text.lowercase()
    val nums = Regex("\\d+(?:[.,]\\d+)?").findAll(lower).map { it.value.replace(',', '.').toFloat() }.toList()
    if (nums.isEmpty()) return null
    val factor = if ("mois" in lower && "an" !in lower) 1f / 12 else 1f
    val min = nums.first() * factor
    val open = nums.size == 1 && Regex("\\+|dès|des |à partir|plus").containsMatchIn(lower)
    val max = when {
        nums.size > 1 -> nums.last() * factor
        open -> 99f
        else -> min
    }
    return minOf(min, max) to maxOf(min, max)
}

fun matchesAge(text: String, min: Float, max: Float): Boolean {
    val r = parseAgeRange(text) ?: return false
    return r.first <= max && r.second >= min
}
