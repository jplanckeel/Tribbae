package data

import kotlin.math.roundToInt

/**
 * Tranches d'âge des idées. La valeur reste un texte (« 3–6 ans ») pour ne rien
 * changer côté serveur ni aux idées existantes, mais le sélecteur l'écrit
 * toujours de la même façon, et ce module sait la relire. Même logique sur le
 * web : frontend/src/lib/age.ts (garder les deux alignés).
 */
object AgeRanges {
    /** Âge maximal « ouvert » (« 10 ans et + », « Tous âges »). */
    const val OPEN = 99f

    /** Tranches proposées en un toucher : les mêmes que les filtres de l'Explorer. */
    val BANDS = listOf("0–3" to (0f to 3f), "3–6" to (3f to 6f), "6–10" to (6f to 10f), "10+" to (10f to OPEN))

    /** Crans des réglages précis : par 6 mois jusqu'à 2 ans, puis par an. */
    val STOPS: List<Float> = listOf(0f, 0.5f, 1f, 1.5f) + (2..18).map { it.toFloat() } + OPEN

    private val allAges = Regex("tous\\s+(les\\s+)?âges|tout\\s+âge|toute la famille")
    private val token = Regex("(\\d+(?:[.,]\\d+)?)\\s*(mois|ans?)?")
    private val openEnded = Regex("\\+|dès|des |à partir|plus")

    /**
     * Relit une tranche, du sélecteur ou d'une saisie libre plus ancienne :
     * « 3–6 ans », « 18 mois – 3 ans », « 6–18 mois », « dès 4 ans », « 10+ »,
     * « Tous âges ». Chaque nombre prend l'unité qui le suit (ou celle du nombre
     * suivant). Null si rien d'exploitable.
     */
    fun parse(text: String): Pair<Float, Float>? {
        val lower = text.lowercase()
        if (allAges.containsMatchIn(lower)) return 0f to OPEN
        val tokens = token.findAll(lower).map { it.groupValues[1].replace(',', '.').toFloat() to it.groupValues[2].ifEmpty { null } }.toMutableList()
        if (tokens.isEmpty()) return null
        for (i in tokens.size - 2 downTo 0) if (tokens[i].second == null) tokens[i] = tokens[i].first to tokens[i + 1].second
        val years = tokens.map { (n, unit) -> if (unit == "mois") n / 12f else n }
        val min = years.first()
        val max = when {
            years.size > 1 -> years.last()
            openEnded.containsMatchIn(lower) -> OPEN
            else -> min
        }
        return minOf(min, max) to maxOf(min, max)
    }

    fun matches(text: String, min: Float, max: Float): Boolean {
        val r = parse(text) ?: return false
        return r.first <= max && r.second >= min
    }

    private fun isMonths(y: Float) = y < 2f && y != y.toInt().toFloat()
    private fun num(y: Float) = if (y == y.toInt().toFloat()) y.toInt().toString() else y.toString()

    /** Un âge seul : « 6 mois », « 1 an », « 18 mois », « 8 ans ». */
    fun label(y: Float): String = when {
        isMonths(y) || y == 0f -> "${(y * 12).roundToInt()} mois"
        y == 1f -> "1 an"
        else -> "${num(y)} ans"
    }

    /** Écrit une tranche comme le sélecteur l'affiche et l'enregistre. */
    fun format(r: Pair<Float, Float>): String {
        val (min, max) = r
        return when {
            max >= OPEN -> if (min <= 0f) "Tous âges" else "${label(min)} et +"
            min == max -> label(min)
            max < 2f && (isMonths(min) || isMonths(max) || min == 0f) -> "${(min * 12).roundToInt()}–${(max * 12).roundToInt()} mois"
            !isMonths(min) && !isMonths(max) -> "${num(min)}–${num(max)} ans"
            else -> "${label(min)} – ${label(max)}"
        }
    }

    /** Cran le plus proche (en dessous) d'un âge réel : celui d'un enfant. */
    fun snap(years: Float): Float = STOPS.filter { it <= years && it < OPEN }.maxOrNull() ?: 0f

    fun hull(a: Pair<Float, Float>, b: Pair<Float, Float>) = minOf(a.first, b.first) to maxOf(a.second, b.second)
    fun covers(a: Pair<Float, Float>, b: Pair<Float, Float>) = a.first <= b.first && a.second >= b.second
}
