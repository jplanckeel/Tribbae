package data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Mêmes cas que frontend/src/lib/gifts.test.ts. */
class GiftsTest {
    @Test
    fun statesNeverSayWho() {
        assertEquals("Libre", Gifts.state("").label)
        assertTrue(Gifts.state("").free)
        assertEquals("Réservé", Gifts.state("reserved").label)
        assertTrue(Gifts.state("reserved").dim)
        assertEquals("Offert", Gifts.state("bought").label)
        assertTrue(Gifts.state("bought").strike)
        assertEquals("Vous l'offrez", Gifts.state("mine").label)
        assertEquals("Acheté par vous", Gifts.state("mine_bought").label)
        assertEquals("À confirmer", Gifts.state("pending").label)
    }

    @Test
    fun progressOfTheList() {
        val p = Gifts.progress(listOf("", "reserved", "mine", "bought", "mine_bought"))
        assertEquals(2, p.done); assertEquals(2, p.taken); assertEquals(1, p.free)
        assertEquals(40, p.pDone); assertEquals(40, p.pTaken)
        assertEquals("2 offerts · 2 réservés", p.label)
        assertEquals("1 encore libre", p.freeLabel)
        assertEquals("0 offert · 0 réservé", Gifts.progress(listOf("", "")).label)
        assertEquals(0, Gifts.progress(emptyList()).pDone)
    }

    @Test
    fun filters() {
        assertTrue(Gifts.matches("", Gifts.Filter.FREE))
        assertFalse(Gifts.matches("reserved", Gifts.Filter.FREE))
        assertTrue(Gifts.matches("mine", Gifts.Filter.MINE))
        assertTrue(Gifts.matches("pending", Gifts.Filter.MINE))
        assertFalse(Gifts.matches("bought", Gifts.Filter.MINE))
        assertTrue(Gifts.matches("bought", Gifts.Filter.ALL))
    }

    @Test
    fun creatorOnlySeesHowMany() {
        assertEquals("Aucune idée n'a encore trouvé preneur.", Gifts.surpriseLabel(listOf("", "")))
        assertEquals("1 idée sur 3 a trouvé preneur.", Gifts.surpriseLabel(listOf("reserved", "", "")))
        assertEquals("2 idées sur 5 ont trouvé preneur.", Gifts.surpriseLabel(listOf("reserved", "bought", "", "", "")))
    }
}
