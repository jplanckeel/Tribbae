package ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import data.Link
import data.currentTimeMillis
import viewmodel.LinkViewModel

@Composable
fun CalendarScreen(
    viewModel: LinkViewModel,
    modifier: Modifier = Modifier,
    onLinkClick: (Link) -> Unit
) {
    val linksWithDate = viewModel.getLinksWithDate()

    // État du mois affiché
    var displayYear by remember { mutableStateOf(currentYear()) }
    var displayMonth by remember { mutableStateOf(currentMonth()) }
    var selectedDay by remember { mutableStateOf<Int?>(null) }

    // Liens du mois courant
    val monthLinks = linksWithDate.filter { link ->
        val d = dateComponents(link.eventDate!!)
        d.year == displayYear && d.month == displayMonth
    }

    // Liens du jour sélectionné
    val dayLinks = if (selectedDay != null) {
        monthLinks.filter { link ->
            val d = dateComponents(link.eventDate!!)
            d.day == selectedDay
        }
    } else monthLinks

    // Jours qui ont des événements
    val eventDays = monthLinks.map { dateComponents(it.eventDate!!).day }.toSet()

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        // Header mois
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = {
                if (displayMonth == 1) { displayMonth = 12; displayYear-- }
                else displayMonth--
                selectedDay = null
            }) {
                Icon(imageVector = Icons.Default.ChevronLeft, contentDescription = "Mois précédent", tint = Orange)
            }
            Text(
                "${monthName(displayMonth)} $displayYear",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = TextPrimary
            )
            IconButton(onClick = {
                if (displayMonth == 12) { displayMonth = 1; displayYear++ }
                else displayMonth++
                selectedDay = null
            }) {
                Icon(imageVector = Icons.Default.ChevronRight, contentDescription = "Mois suivant", tint = Orange)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Jours de la semaine
        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("Lun", "Mar", "Mer", "Jeu", "Ven", "Sam", "Dim").forEach { day ->
                Text(
                    day, modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center, fontSize = 12.sp,
                    fontWeight = FontWeight.Medium, color = TextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Grille du calendrier
        val daysInMonth = daysInMonth(displayYear, displayMonth)
        val firstDayOfWeek = dayOfWeek(displayYear, displayMonth, 1) // 0=Lun
        val totalCells = firstDayOfWeek + daysInMonth
        val rows = (totalCells + 6) / 7

        for (row in 0 until rows) {
            Row(modifier = Modifier.fillMaxWidth().height(44.dp)) {
                for (col in 0..6) {
                    val cellIndex = row * 7 + col
                    val day = cellIndex - firstDayOfWeek + 1
                    if (day in 1..daysInMonth) {
                        val hasEvent = day in eventDays
                        val isSelected = day == selectedDay
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(CircleShape)
                                .then(
                                    if (isSelected) Modifier.background(Orange)
                                    else if (hasEvent) Modifier.background(Orange.copy(alpha = 0.12f))
                                    else Modifier
                                )
                                .clickable {
                                    selectedDay = if (isSelected) null else day
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "$day",
                                    fontSize = 14.sp,
                                    fontWeight = if (hasEvent) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) Color.White
                                           else if (hasEvent) Orange
                                           else TextPrimary
                                )
                                if (hasEvent && !isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .size(5.dp)
                                            .clip(CircleShape)
                                            .background(Orange)
                                    )
                                }
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Titre section
        Text(
            if (selectedDay != null) "Événements du $selectedDay ${monthName(displayMonth)}"
            else "${monthLinks.size} événement${if (monthLinks.size > 1) "s" else ""} en ${monthName(displayMonth)}",
            fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = TextSecondary
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (dayLinks.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(imageVector = Icons.Default.EventBusy, contentDescription = null,
                        modifier = Modifier.size(48.dp), tint = OrangeLight.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Aucun événement", color = TextSecondary, fontSize = 14.sp)
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 16.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(dayLinks) { link ->
                    LinkCard(link = link, onClick = { onLinkClick(link) })
                }
            }
        }
    }
}

// --- Fonctions utilitaires de date (sans java.time pour KMP) ---

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

fun currentYear(): Int = dateComponents(currentTimeMillis()).year
fun currentMonth(): Int = dateComponents(currentTimeMillis()).month

/** Nombre de jours dans un mois */
fun daysInMonth(year: Int, month: Int): Int = when (month) {
    1, 3, 5, 7, 8, 10, 12 -> 31
    4, 6, 9, 11 -> 30
    2 -> if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) 29 else 28
    else -> 30
}

/** Jour de la semaine (0=Lundi, 6=Dimanche) pour une date donnée — algorithme de Zeller simplifié */
fun dayOfWeek(year: Int, month: Int, day: Int): Int {
    var y = year; var m = month
    if (m < 3) { m += 12; y-- }
    val q = day
    val k = y % 100
    val j = y / 100
    val h = (q + (13 * (m + 1)) / 5 + k + k / 4 + j / 4 + 5 * j) % 7
    // h: 0=Samedi, 1=Dimanche, 2=Lundi...
    return (h + 5) % 7 // 0=Lundi
}

fun monthName(month: Int): String = when (month) {
    1 -> "Janvier"; 2 -> "Février"; 3 -> "Mars"; 4 -> "Avril"
    5 -> "Mai"; 6 -> "Juin"; 7 -> "Juillet"; 8 -> "Août"
    9 -> "Septembre"; 10 -> "Octobre"; 11 -> "Novembre"; 12 -> "Décembre"
    else -> ""
}

/** Calcule l'âge en années à partir d'une date de naissance (timestamp ms) */
fun calculateAge(birthDate: Long): Int {
    val birth = dateComponents(birthDate)
    val today = dateComponents(currentTimeMillis())
    var age = today.year - birth.year
    if (today.month < birth.month || (today.month == birth.month && today.day < birth.day)) age--
    return maxOf(0, age)
}

/** Calcule l'âge en mois à partir d'une date de naissance (timestamp ms) */
fun calculateAgeInMonths(birthDate: Long): Int {
    val birth = dateComponents(birthDate)
    val today = dateComponents(currentTimeMillis())
    var months = (today.year - birth.year) * 12 + (today.month - birth.month)
    if (today.day < birth.day) months--
    return maxOf(0, months)
}

/** Formate l'âge : "X mois" si < 3 ans, sinon "X an(s)" */
fun formatChildAge(birthDate: Long): String {
    val months = calculateAgeInMonths(birthDate)
    return if (months < 36) "$months mois"
    else {
        val years = months / 12
        "$years an${if (years > 1) "s" else ""}"
    }
}

/** Version courte pour les chips : "Xm" si < 3 ans, sinon "Xa" */
fun formatChildAgeShort(birthDate: Long): String {
    val months = calculateAgeInMonths(birthDate)
    return if (months < 36) "${months}m" else "${months / 12}a"
}
