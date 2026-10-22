package ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * L'ordre « À la une » doit être le même sur Android et sur le web : valeurs
 * de référence calculées avec rotationScore de frontend/src/lib/links.ts.
 */
class RotationScoreTest {
    private val now = java.time.Instant.parse("2026-10-20T09:00:00Z").toEpochMilli()

    @Test
    fun matchesWebScores() {
        assertEquals(0.864783130593835, rotationScore("6ad510ad50e6b1b0dd36abcd", 3, "2026-10-15T08:00:00Z", now), 1e-12)
        assertEquals(0.754857371677645, rotationScore("64f0c2aa11b2c3d4e5f60718", 0, "2025-01-01T00:00:00Z", now), 1e-12)
        assertEquals(0.536940287357240, rotationScore("crêpe-é", 12, "", now), 1e-12)
    }

    @Test
    fun changesWithThePeriodButNotWithin() {
        val id = "6ad510ad50e6b1b0dd36abcd"
        val a = rotationScore(id, 3, "", now)
        assertEquals(a, rotationScore(id, 3, "", now + 3600_000L), "même période de 6 h : même score")
        assertNotEquals(a, rotationScore(id, 3, "", now + 6 * 3600_000L), "période suivante : ordre rebattu")
    }
}
