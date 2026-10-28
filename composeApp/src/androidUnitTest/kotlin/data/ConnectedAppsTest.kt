package data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Mêmes cas que frontend/src/lib/oauth.test.ts. */
class ConnectedAppsTest {
    @Test
    fun knownScopesInAFixedOrder() {
        assertEquals(
            listOf("Voir la liste de vos dossiers", "Ajouter des idées dans vos dossiers, toujours en privé"),
            ConnectedApps.scopeLabels(listOf("links:write", "folders:read"))
        )
    }

    @Test
    fun unknownScopeIsNeverHidden() {
        assertEquals(
            listOf("Voir les idées de vos dossiers (titre, lien, catégorie)", "admin"),
            ConnectedApps.scopeLabels(listOf("links:read", "admin"))
        )
    }

    @Test
    fun ideasAddedByAConnectedAppAreLabelled() {
        assertEquals("Ajoutée par Claude", ConnectedApps.createdViaLabel("mcp"))
        assertNull(ConnectedApps.createdViaLabel(null))
        assertNull(ConnectedApps.createdViaLabel(""))
    }
}
