package data

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Mêmes cas que canEditFolder dans frontend/src/lib/links.test.ts. */
class RightsTest {
    private val folder = Folder(
        id = "f", name = "Noël", ownerId = "alice",
        collaborators = listOf(
            Collaborator(userId = "mamie", role = "COLLABORATOR_ROLE_EDITOR"),
            Collaborator(userId = "papi", role = "COLLABORATOR_ROLE_VIEWER"),
        ),
    )

    @Test
    fun ownerTribeAndEditorsOnly() {
        assertTrue(Rights.canEditFolder(folder, "alice"))
        assertTrue(Rights.canEditFolder(folder, "bob") { it == "alice" })
        assertTrue(Rights.canEditFolder(folder, "mamie"))
        assertFalse(Rights.canEditFolder(folder, "papi"))
        assertFalse(Rights.canEditFolder(folder, "inconnu"))
        assertFalse(Rights.canEditFolder(folder, null))
        // Dossier créé sur l'appareil, pas encore synchronisé : le sien
        assertTrue(Rights.canEditFolder(folder.copy(ownerId = ""), "papi"))
    }

    @Test
    fun ideasFollowTheirAuthorThenTheirFolder() {
        val idea = Link(id = "l", title = "Marché de Noël", url = "", category = LinkCategory.EVENEMENT, folderId = "f", ownerId = "alice")
        assertTrue(Rights.canEditIdea(idea, folder, "alice"))
        assertTrue(Rights.canEditIdea(idea, folder, "bob") { it == "alice" })
        assertTrue(Rights.canEditIdea(idea, folder, "mamie"))
        assertFalse(Rights.canEditIdea(idea, folder, "papi"))
        assertFalse(Rights.canEditIdea(idea, null, "papi"))
        // L'idée du lecteur lui-même reste la sienne, même dans le dossier d'un autre
        assertTrue(Rights.canEditIdea(idea.copy(ownerId = "papi"), folder, "papi"))
        assertTrue(Rights.canEditIdea(idea.copy(ownerId = ""), null, "papi"))
        assertFalse(Rights.canEditIdea(idea, folder, null))
    }
}
