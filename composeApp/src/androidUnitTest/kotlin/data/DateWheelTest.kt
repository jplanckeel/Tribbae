package data

import data.DateWheel.Kind
import data.DateWheel.Parts
import java.util.Calendar
import java.util.TimeZone
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Mêmes cas que le web (frontend/src/lib/dateWheel.test.ts), en Europe/Paris. */
class DateWheelTest {
    private lateinit var saved: TimeZone

    @BeforeTest
    fun paris() {
        saved = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Paris"))
    }

    @AfterTest
    fun restore() = TimeZone.setDefault(saved)

    private fun at(y: Int, m: Int, d: Int, h: Int = 0, min: Int = 0) =
        Calendar.getInstance().apply { clear(); set(y, m - 1, d, h, min) }.timeInMillis

    private val now get() = at(2026, 10, 28, 15, 30) // mercredi 28 octobre 2026

    @Test
    fun daysFollowMonthsAndLeapYears() {
        assertEquals(listOf(31, 28, 30, 31), listOf(1, 2, 4, 12).map { DateWheel.daysInMonth(2026, it) })
        assertEquals(29, DateWheel.daysInMonth(2024, 2))
        assertEquals(29, DateWheel.daysInMonth(2000, 2))
        assertEquals(28, DateWheel.daysInMonth(2100, 2))
    }

    @Test
    fun readsAnInstantInLocalTime() {
        assertEquals(Parts(2026, 3, 29, 3, 30), DateWheel.partsOf(at(2026, 3, 29, 2, 30))) // passage à l'heure d'été
        assertEquals(Parts(2026, 12, 31, 23, 45), DateWheel.partsOf(at(2026, 12, 31, 23, 45)))
    }

    @Test
    fun storesTheTimeOfTheUsage() {
        val p = Parts(2027, 3, 14, 15, 30)
        assertEquals(at(2027, 3, 14, 15, 30), DateWheel.toMs(p, Kind.EVENT))
        assertEquals(at(2027, 3, 14), DateWheel.toMs(p, Kind.KEY))
        assertEquals(at(2027, 3, 14), DateWheel.toMs(p, Kind.ANNIV))
        assertEquals(at(2027, 3, 14, 12), DateWheel.toMs(p, Kind.BIRTH))
        assertEquals(p, DateWheel.partsOf(DateWheel.toMs(p, Kind.EVENT)))
    }

    @Test
    fun birthIsAtMostTodayAndThirtyYearsBack() {
        val b = DateWheel.bounds(Kind.BIRTH, now)
        assertEquals(listOf(1996, 1, 1), listOf(b.min.year, b.min.month, b.min.day))
        assertEquals(listOf(2026, 10, 28), listOf(b.max.year, b.max.month, b.max.day))
    }

    @Test
    fun keyDateStartsTodayUnlessAlreadyPast() {
        assertEquals(Parts(2026, 10, 28), DateWheel.bounds(Kind.KEY, now).min)
        assertEquals(Parts(2025, 6, 1), DateWheel.bounds(Kind.KEY, now, at(2025, 6, 1)).min)
        assertEquals(1926, DateWheel.bounds(Kind.ANNIV, now).min.year)
    }

    @Test
    fun eventSpansFiveYearsBackAndTenAhead() {
        val b = DateWheel.bounds(Kind.EVENT, now)
        assertEquals(listOf(2021, 2036, 12, 31), listOf(b.min.year, b.max.year, b.max.month, b.max.day))
    }

    @Test
    fun columnsStayWithinBounds() {
        val b = DateWheel.bounds(Kind.BIRTH, now)
        assertEquals(31, DateWheel.yearOptions(b).size)
        assertEquals((1..10).toList(), DateWheel.monthOptions(2026, b))
        assertEquals(12, DateWheel.monthOptions(2025, b).size)
        assertEquals(28, DateWheel.dayOptions(2026, 10, b).size)
        assertEquals(29, DateWheel.dayOptions(2024, 2, b).size)
    }

    @Test
    fun keepsTheSavedMinuteBetweenSteps() {
        assertEquals(12, DateWheel.minuteOptions(30).size)
        assertEquals(listOf(0, 5, 10, 15, 17, 20, 25, 30, 35, 40, 45, 50, 55), DateWheel.minuteOptions(17))
    }

    @Test
    fun bringsTheDayBackToTheEndOfTheMonth() {
        val b = DateWheel.bounds(Kind.EVENT, now)
        val jan31 = Parts(2027, 1, 31, 10, 0)
        assertEquals(Parts(2027, 2, 28, 10, 0), DateWheel.clamp(jan31.copy(month = 2), b))
        assertEquals(Parts(2028, 2, 29, 10, 0), DateWheel.clamp(jan31.copy(year = 2028, month = 2), b))
        assertEquals(28, DateWheel.clamp(Parts(2027, 2, 29), b).day)
    }

    @Test
    fun staysWithinBounds() {
        val b = DateWheel.bounds(Kind.BIRTH, now)
        val moved = DateWheel.clamp(Parts(2026, 10, 30), b)
        assertEquals(listOf(2026, 10, 28), listOf(moved.year, moved.month, moved.day))
        val future = DateWheel.clamp(Parts(2026, 12, 25), b)
        assertEquals(listOf(2026, 10, 28), listOf(future.year, future.month, future.day))
    }

    private fun dates(kind: Kind, now: Long) = DateWheel.shortcuts(kind, now).map { "${it.label} ${it.date.day}/${it.date.month}" }

    @Test
    fun eventShortcuts() {
        assertEquals(listOf("Aujourd'hui 28/10", "Demain 29/10", "Ce week-end 31/10"), dates(Kind.EVENT, now))
        // Un même jour une seule fois : samedi, dimanche, vendredi (le week-end est demain)
        assertEquals(listOf("Aujourd'hui 31/10", "Demain 1/11"), dates(Kind.EVENT, at(2026, 10, 31)))
        assertEquals(listOf("Aujourd'hui 1/11", "Demain 2/11"), dates(Kind.EVENT, at(2026, 11, 1)))
        assertEquals(listOf("Aujourd'hui 30/10", "Demain 31/10"), dates(Kind.EVENT, at(2026, 10, 30)))
        assertEquals("Demain 1/1", dates(Kind.EVENT, at(2026, 12, 31))[1])
    }

    @Test
    fun keyDateShortcuts() {
        assertEquals(listOf("Ce week-end 31/10", "Dans un mois 28/11"), dates(Kind.KEY, now))
        assertEquals("Dans un mois 28/2", dates(Kind.KEY, at(2027, 1, 31))[1])
        assertEquals("Dans un mois 15/1", dates(Kind.KEY, at(2026, 12, 15))[1])
    }

    @Test
    fun noShortcutForABirth() {
        assertEquals(emptyList(), DateWheel.shortcuts(Kind.BIRTH, now))
    }

    @Test
    fun describesTheDateInWords() {
        assertEquals("14 mars 2027 à 15 h 05", DateWheel.describe(Parts(2027, 3, 14, 15, 5), true))
        assertEquals("1er janvier 2027", DateWheel.describe(Parts(2027, 1, 1), false))
    }
}
