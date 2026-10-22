package data

import java.util.Calendar
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Mêmes règles que le web (frontend/src/lib/folderStyle.ts). */
class FolderStyleTest {
    private fun at(y: Int, m: Int, d: Int, h: Int = 12) = Calendar.getInstance().apply { clear(); set(y, m - 1, d, h, 0) }.timeInMillis

    @Test
    fun picksIllustrationFromNameThenDate() {
        mapOf(
            "Noël 2026" to "noel", "Anniversaire Emma" to "anniv", "Cadeaux pour Léo" to "cadeaux", "Ma liste" to "cadeaux",
            "Jeux de société" to "jouets", "Été à la mer" to "plage", "Vacances Drôme" to "montagne", "Goûters" to "gouter",
            "Jours de pluie" to "pluie", "Automne" to "automne",
        ).forEach { (name, expected) -> assertEquals(expected, FolderStyle.autoIllustration(name), name) }
        assertNull(FolderStyle.autoIllustration("Divers"))
        assertNull(FolderStyle.autoIllustration("Compléments"), "« ete » au milieu d'un mot ne compte pas")
        assertEquals("anniv", FolderStyle.autoIllustration("Cadeaux pour Léo", at(2026, 11, 14), "anniv"))
        assertEquals("montagne", FolderStyle.autoIllustration("Divers", at(2026, 4, 2), "vacances"))
        assertEquals("noel", FolderStyle.autoIllustration("Divers", at(2026, 12, 2), "event"))
        assertEquals("automne", FolderStyle.autoIllustration("Divers", at(2026, 10, 2), "event"))
    }

    @Test
    fun resolvesCover() {
        assertEquals("illu" to "montagne", FolderStyle.resolvedCover(Folder(id = "1", name = "Vacances")))
        assertEquals("tipis" to null, FolderStyle.resolvedCover(Folder(id = "1", name = "Divers")))
        assertEquals("pois" to "montagne", FolderStyle.resolvedCover(Folder(id = "1", name = "Vacances", cover = "pois")))
        assertEquals("illu" to "plage", FolderStyle.resolvedCover(Folder(id = "1", name = "Vacances", cover = "illu", illustration = "plage")))
    }

    @Test
    fun countsDownToKeyDate() {
        val now = at(2026, 10, 21, 18)
        assertEquals("Anniv. dans 24 j", FolderStyle.countdown(at(2016, 11, 14, 0), "anniv", now)?.label)
        assertEquals("Anniv. aujourd'hui !", FolderStyle.countdown(at(2016, 10, 21), "anniv", now)?.label)
        assertEquals(364, FolderStyle.countdown(at(2016, 10, 20), "anniv", now)?.days)
        assertEquals("Départ J-12", FolderStyle.countdown(at(2026, 11, 2), "vacances", now)?.label)
        assertEquals("Sam. 24 · J-3", FolderStyle.countdown(at(2026, 10, 24), "event", now)?.label)
        assertEquals("J-65", FolderStyle.countdown(at(2026, 12, 25), "event", now)?.label)
        assertNull(FolderStyle.countdown(at(2026, 10, 20), "event", now))
        assertNull(FolderStyle.countdown(0, "event", now))
    }
}
