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

    // ── « Vous y êtes allés ? » : le lendemain d'une sortie datée, à 10 h ──

    private fun at(y: Int, m: Int, d: Int, h: Int = 0, min: Int = 0) =
        java.util.Calendar.getInstance().apply { clear(); set(y, m - 1, d, h, min) }.timeInMillis

    private fun outing(id: String, eventAt: Long?, category: LinkCategory = LinkCategory.EVENEMENT) =
        Link(id = id, title = "Fête de la courge", url = "", category = category, eventDate = eventAt)

    private val serverId = "64b7f3a2c9e1d4f5a6b7c8d9"

    @Test
    fun asksTheDayAfterAtTen() {
        val now = at(2026, 11, 7, 9)
        // Samedi 7 novembre à 14 h 30 → dimanche 8 à 10 h ; un événement sans heure (minuit) aussi
        assertEquals(at(2026, 11, 8, 10), DonePrompts.triggerAt(outing("a", at(2026, 11, 7, 14, 30)), now))
        assertEquals(at(2026, 11, 8, 10), DonePrompts.triggerAt(outing("a", at(2026, 11, 7)), now))
        assertEquals(at(2026, 11, 8, 10), DonePrompts.triggerAt(outing("a", at(2026, 11, 7, 15), LinkCategory.ACTIVITE), now))
        // Jamais en retard : passé 10 h le lendemain, le dossier pose la question
        assertNull(DonePrompts.triggerAt(outing("a", at(2026, 11, 7, 14)), at(2026, 11, 8, 10, 1)))
        // Déjà faite, « Pas cette fois », sans date, autre catégorie
        assertNull(DonePrompts.triggerAt(outing("a", at(2026, 11, 7, 14)).copy(doneCount = 1), now))
        assertNull(DonePrompts.triggerAt(outing("a", at(2026, 11, 7, 14)).copy(donePromptDismissed = true), now))
        assertNull(DonePrompts.triggerAt(outing("a", null), now))
        assertNull(DonePrompts.triggerAt(outing("a", at(2026, 11, 7, 14), LinkCategory.RECETTE), now))
    }

    @Test
    fun plansOneQuestionPerOuting() {
        val now = at(2026, 11, 7, 9)
        val other = "64b7f3a2c9e1d4f5a6b7c8da"
        val plan = DonePrompts.plan(
            links = listOf(
                outing(serverId, at(2026, 11, 7, 14)),
                outing(other, at(2026, 11, 20, 14)).copy(doneCount = 1),
                // Pas encore reçue par le serveur : on ne pourrait pas la marquer depuis la notification
                outing("local-1", at(2026, 11, 7, 14)),
            ),
            previouslyScheduled = setOf(serverId, other, "supprimee"),
            now = now,
        )
        assertEquals(listOf(serverId), plan.schedule.map { it.linkId })
        assertEquals(at(2026, 11, 8, 10), plan.schedule.first().at)
        assertEquals(at(2026, 11, 7, 14), plan.schedule.first().eventAt)
        // Faite entre-temps, ou supprimée : l'alarme est annulée
        assertEquals(setOf(other, "supprimee"), plan.cancel)
    }

    @Test
    fun notificationSaysWhenItWas() {
        val link = outing(serverId, at(2026, 11, 7, 14))
        assertEquals("C'était hier. Un toucher pour garder le souvenir.", DonePrompts.body(link, at(2026, 11, 8, 10)))
        assertEquals("C'était samedi. Un toucher pour garder le souvenir.", DonePrompts.body(link, at(2026, 11, 10, 10)))
        assertEquals("C'était le 7 nov.. Un toucher pour garder le souvenir.".replace("..", "."), DonePrompts.body(link, at(2026, 11, 20, 10)))
    }

    @Test
    fun clearsQuestionsOnceAnswered() {
        val now = at(2026, 11, 13, 11)
        val asked = outing(serverId, at(2026, 11, 12, 14))
        assertEquals(emptySet(), DonePrompts.answered(listOf(asked), now), "pas encore répondu : la question reste")
        assertEquals(setOf(serverId), DonePrompts.answered(listOf(asked.copy(doneCount = 1)), now), "faite (ici, sur le web ou par la tribu)")
        assertEquals(setOf(serverId), DonePrompts.answered(listOf(asked.copy(donePromptDismissed = true)), now), "« Pas cette fois »")
        // Sortie ancienne, à venir, ou autre catégorie : rien à retirer
        assertEquals(emptySet(), DonePrompts.answered(listOf(outing("vieille", at(2026, 10, 1, 14)).copy(doneCount = 1)), now))
        assertEquals(emptySet(), DonePrompts.answered(listOf(outing("future", at(2026, 11, 20, 14)).copy(doneCount = 1)), now))
        assertEquals(emptySet(), DonePrompts.answered(listOf(outing("recette", at(2026, 11, 12, 14), LinkCategory.RECETTE).copy(doneCount = 1)), now))
        assertEquals("done:abc".hashCode(), DonePrompts.notificationId("abc"))
    }
}
