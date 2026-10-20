package data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Mêmes cas que le web (frontend/src/lib/age.ts) : lecture puis écriture. */
class AgeRangesTest {
    private fun roundTrip(text: String) = AgeRanges.parse(text)?.let { AgeRanges.format(it) }

    @Test
    fun readsSelectorAndLegacyValues() {
        mapOf(
            "3–6 ans" to "3–6 ans", "3-10 ans" to "3–10 ans", "18 mois – 3 ans" to "18 mois – 3 ans",
            "6–18 mois" to "6–18 mois", "18 mois" to "18 mois", "dès 4 ans" to "4 ans et +", "10+" to "10 ans et +",
            "Tous âges" to "Tous âges", "8 ans" to "8 ans", "1 an" to "1 an", "0–3 ans" to "0–3 ans",
        ).forEach { (input, expected) -> assertEquals(expected, roundTrip(input), input) }
        assertNull(AgeRanges.parse("trois ans"))
        assertEquals(1.5f to 3f, AgeRanges.parse("18 mois – 3 ans"))
    }

    @Test
    fun allAgesMatchEveryFilterBand() {
        AgeRanges.BANDS.forEach { (_, b) -> kotlin.test.assertTrue(AgeRanges.matches("Tous âges", b.first, b.second)) }
        kotlin.test.assertFalse(AgeRanges.matches("6–18 mois", 3f, 6f))
    }

    @Test
    fun snapsChildAgeToStops() {
        assertEquals(8f, AgeRanges.snap(8.7f))
        assertEquals(1.5f, AgeRanges.snap(1.9f))
        assertEquals(0f, AgeRanges.snap(0.3f))
    }
}
