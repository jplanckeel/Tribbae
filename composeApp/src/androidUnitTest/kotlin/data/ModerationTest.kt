package data

import kotlin.test.Test
import kotlin.test.assertEquals

/** Mêmes cas que frontend/src/lib/moderation.test.ts. */
class ModerationTest {
    @Test
    fun offersTheReasonsKnownToTheServer() {
        assertEquals(listOf("inappropriate", "harassment", "spam", "other"), Moderation.REASONS.map { it.first })
        assertEquals("Spam ou publicité", Moderation.reasonLabel("spam"))
        assertEquals("Autre raison", Moderation.reasonLabel("inconnu"))
        assertEquals(500, Moderation.DETAILS_MAX)
    }
}
