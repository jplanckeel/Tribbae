package data

import kotlin.test.Test
import kotlin.test.assertEquals

/** Mêmes cas que « adresse dans Google Maps » de frontend/src/lib/format.test.ts. */
class MapsTest {
    @Test
    fun encodesTheAddress() {
        assertEquals(
            "https://www.google.com/maps/search/?api=1&query=Parc%20de%20la%20T%C3%AAte%20d'Or%2C%20Lyon",
            Maps.searchUrl("  Parc de la Tête d'Or, Lyon "),
        )
        assertEquals("https://www.google.com/maps/search/?api=1&query=12%20rue%20%26%20place", Maps.searchUrl("12 rue & place"))
        assertEquals("https://www.google.com/maps/search/?api=1&query=Gare%20(Nord)%20!%20~", Maps.searchUrl("Gare (Nord) ! ~"))
    }
}
