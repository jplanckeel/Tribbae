package data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RemindersTest {
    private val now = 1_800_000_000_000L
    private val day = Reminders.DAY_MS

    private fun idea(id: String, inDays: Double?, reminder: Boolean = true) = Link(
        id = id, title = "Idée $id", url = "", category = LinkCategory.EVENEMENT,
        eventDate = inDays?.let { now + (it * day).toLong() }, reminderEnabled = reminder,
    )

    @Test
    fun remindsTheDayBeforeAndNeverLate() {
        assertEquals(now + 2 * day, Reminders.triggerAt(now + 3 * day, now))
        assertNull(Reminders.triggerAt(now + day / 2, now), "dans moins de 24 h : trop tard pour « la veille »")
        assertNull(Reminders.triggerAt(now - day, now), "événement passé : pas de rappel")
        assertNull(Reminders.triggerAt(null, now))
    }

    @Test
    fun plansFromTheIdeaList() {
        val plan = Reminders.plan(
            links = listOf(idea("a", 3.0), idea("b", 3.0, reminder = false), idea("c", -2.0), idea("d", null), idea("e", 10.0)),
            previouslyScheduled = setOf("a", "b", "c", "old-local-id"),
            now = now,
        )
        assertEquals(listOf("a", "e"), plan.schedule.map { it.linkId })
        assertEquals("Idée a", plan.schedule.first().title)
        // Rappel désactivé, événement passé, idée supprimée ou renommée (nouvel identifiant)
        assertEquals(setOf("b", "c", "old-local-id"), plan.cancel)
    }
}
