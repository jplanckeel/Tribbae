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

    @Test
    fun reportErrorsAreExplained() {
        assertEquals("Vous avez envoyé beaucoup de signalements aujourd'hui : réessayez demain.", Moderation.reportError("HTTP 429 (429): too many reports, try again tomorrow"))
        assertEquals("Vous ne pouvez pas signaler votre contenu ni celui de votre tribu.", Moderation.reportError("(400) cannot report your own content"))
        assertEquals("Ce contenu n'existe plus.", Moderation.reportError("Request failed (404)"))
        assertEquals("Le signalement n'a pas pu partir. Vérifiez votre connexion.", Moderation.reportError("timeout"))
    }
}
