package ui

import data.currentTimeMillis

// Âge d'un enfant pour filtrer les idées par âge (LinkViewModel), sans java.time.

data class DateComponents(val year: Int, val month: Int, val day: Int)

fun dateComponents(millis: Long): DateComponents {
    val days = millis / 86400000L
    val totalDays = days + 719468L
    val era = (if (totalDays >= 0) totalDays else totalDays - 146096) / 146097
    val doe = totalDays - era * 146097
    val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
    val y = yoe + era * 400
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    val d = (doy - (153 * mp + 2) / 5 + 1).toInt()
    val m = (mp + (if (mp < 10) 3 else -9)).toInt()
    val year = (y + (if (m <= 2) 1 else 0)).toInt()
    return DateComponents(year, m, d)
}

/** Calcule l'âge en mois à partir d'une date de naissance (timestamp ms) */
fun calculateAgeInMonths(birthDate: Long): Int {
    val birth = dateComponents(birthDate)
    val today = dateComponents(currentTimeMillis())
    var months = (today.year - birth.year) * 12 + (today.month - birth.month)
    if (today.day < birth.day) months--
    return maxOf(0, months)
}
