package data

import java.util.Calendar

/**
 * Sélecteur de date à roulettes : colonnes jour / mois / année (+ heure / minute
 * pour une sortie). Mêmes règles sur le web : frontend/src/lib/dateWheel.ts
 * (dateWheel.test.ts ↔ DateWheelTest, garder les mêmes cas).
 */
object DateWheel {
    /**
     * Usage de la date, qui fixe les bornes et l'heure enregistrée :
     * EVENT (idée, heure locale choisie), KEY (date clé d'un dossier, à venir, minuit),
     * ANNIV (date clé « anniversaire », passée ou future, minuit),
     * BIRTH (naissance, au plus aujourd'hui, midi : la date ne glisse pas d'un fuseau à l'autre).
     */
    enum class Kind { EVENT, KEY, ANNIV, BIRTH }

    /** [month] de 1 à 12. */
    data class Parts(val year: Int, val month: Int, val day: Int, val hour: Int = 0, val minute: Int = 0)

    data class Bounds(val min: Parts, val max: Parts)

    data class Shortcut(val label: String, val date: Parts)

    val MONTHS = listOf("janvier", "février", "mars", "avril", "mai", "juin", "juillet", "août", "septembre", "octobre", "novembre", "décembre")

    fun daysInMonth(year: Int, month: Int): Int =
        Calendar.getInstance().apply { clear(); set(year, month - 1, 1) }.getActualMaximum(Calendar.DAY_OF_MONTH)

    /** Parties d'un instant, en heure locale. */
    fun partsOf(ms: Long): Parts {
        val c = Calendar.getInstance().apply { timeInMillis = ms }
        return Parts(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH), c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
    }

    private fun local(year: Int, month: Int, day: Int, hour: Int = 0, minute: Int = 0): Long =
        Calendar.getInstance().apply { clear(); set(year, month - 1, day, hour, minute) }.timeInMillis

    /** Instant enregistré pour ces parties, selon l'usage (voir Kind). */
    fun toMs(p: Parts, kind: Kind): Long = when (kind) {
        Kind.EVENT -> local(p.year, p.month, p.day, p.hour, p.minute)
        Kind.BIRTH -> local(p.year, p.month, p.day, 12)
        else -> local(p.year, p.month, p.day)
    }

    private fun rank(p: Parts): Long = ((((p.year * 100L + p.month) * 100 + p.day) * 100 + p.hour) * 100) + p.minute

    /**
     * Bornes de l'usage. Une valeur déjà enregistrée hors des bornes (vieille sortie,
     * date clé passée) les élargit, pour rester affichable telle quelle.
     */
    fun bounds(kind: Kind, now: Long, value: Long? = null): Bounds {
        val n = partsOf(now)
        val today = Parts(n.year, n.month, n.day)
        var min: Parts
        var max: Parts
        when (kind) {
            Kind.BIRTH -> { min = Parts(n.year - 30, 1, 1); max = Parts(n.year, n.month, n.day, 23, 59) }
            Kind.KEY -> { min = today; max = Parts(n.year + 10, 12, 31, 23, 59) }
            Kind.ANNIV -> { min = Parts(n.year - 100, 1, 1); max = Parts(n.year + 10, 12, 31, 23, 59) }
            Kind.EVENT -> { min = Parts(n.year - 5, 1, 1); max = Parts(n.year + 10, 12, 31, 23, 59) }
        }
        if (value != null && value > 0) {
            val v = partsOf(value)
            if (rank(v) < rank(min)) min = Parts(v.year, v.month, v.day)
            if (rank(v) > rank(max)) max = Parts(v.year, v.month, v.day, 23, 59)
        }
        return Bounds(min, max)
    }

    /** Ramène le jour dans le mois, puis la date entre les bornes. */
    fun clamp(p: Parts, b: Bounds): Parts {
        val fixed = p.copy(day = minOf(p.day, daysInMonth(p.year, p.month)))
        return when {
            rank(fixed) < rank(b.min) -> b.min
            rank(fixed) > rank(b.max) -> b.max
            else -> fixed
        }
    }

    fun yearOptions(b: Bounds): List<Int> = (b.min.year..b.max.year).toList()

    fun monthOptions(year: Int, b: Bounds): List<Int> =
        ((if (year == b.min.year) b.min.month else 1)..(if (year == b.max.year) b.max.month else 12)).toList()

    fun dayOptions(year: Int, month: Int, b: Bounds): List<Int> {
        val first = if (year == b.min.year && month == b.min.month) b.min.day else 1
        val last = if (year == b.max.year && month == b.max.month) b.max.day else daysInMonth(year, month)
        return (first..last).toList()
    }

    val HOURS: List<Int> = (0..23).toList()

    /** Minutes de 5 en 5, plus la minute déjà enregistrée si elle tombe entre deux. */
    fun minuteOptions(current: Int): List<Int> {
        val steps = (0..11).map { it * 5 }
        return if (current in steps) steps else (steps + current).sorted()
    }

    /** Raccourcis au-dessus des roulettes (date seule : l'heure déjà choisie est gardée). */
    fun shortcuts(kind: Kind, now: Long): List<Shortcut> {
        if (kind != Kind.EVENT && kind != Kind.KEY) return emptyList()
        fun at(offset: Int) = partsOf(Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_MONTH, offset)
        }.timeInMillis)
        // Samedi qui vient ; le week-end en cours s'il a commencé
        val dow = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.DAY_OF_WEEK) - 1 // 0 = dimanche
        val weekend = if (dow == 6 || dow == 0) 0 else 6 - dow
        val list = listOf(Shortcut("Aujourd'hui", at(0)), Shortcut("Demain", at(1)), Shortcut("Ce week-end", at(weekend)))
        // Un même jour une seule fois (samedi : « Ce week-end » est « Aujourd'hui »)
        if (kind == Kind.EVENT) return list.distinctBy { it.date.day to it.date.month }
        // Même jour le mois suivant, ramené au dernier jour du mois (31 janvier → 28 ou 29 février)
        val n = partsOf(now)
        val (y, m) = if (n.month == 12) n.year + 1 to 1 else n.year to n.month + 1
        return listOf(list[2], Shortcut("Dans un mois", Parts(y, m, minOf(n.day, daysInMonth(y, m)))))
    }

    /** « 14 mars 2027 », « 14 mars 2027 à 15 h 30 » (lecteurs d'écran, résumé du sélecteur). */
    fun describe(p: Parts, withTime: Boolean): String {
        val date = "${if (p.day == 1) "1er" else p.day} ${MONTHS[p.month - 1]} ${p.year}"
        return if (withTime) "$date à ${p.hour} h ${p.minute.toString().padStart(2, '0')}" else date
    }
}
