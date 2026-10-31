package data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class AssistantRequestsTest {
    private val me = "u1"
    private val folders = listOf(
        Folder(id = "a", name = "Vacances d'été", visibility = "PRIVATE"),
        Folder(id = "b", name = "Recettes", visibility = "PUBLIC"),
        Folder(
            id = "c", name = "Anniversaire", visibility = "SHARED",
            collaborators = listOf(Collaborator(userId = me, role = "COLLABORATOR_ROLE_VIEWER")),
        ),
        Folder(
            id = "d", name = "Noël", visibility = "SHARED",
            collaborators = listOf(Collaborator(userId = me, role = "COLLABORATOR_ROLE_EDITOR")),
        ),
    )

    @Test
    fun categoryFromNameOrLabel() {
        assertEquals(LinkCategory.IDEE, AssistantRequests.category(null))
        assertEquals(LinkCategory.IDEE, AssistantRequests.category(" "))
        assertEquals(LinkCategory.RECETTE, AssistantRequests.category("recette"))
        assertEquals(LinkCategory.RECETTE, AssistantRequests.category("LINK_CATEGORY_RECETTE"))
        assertEquals(LinkCategory.EVENEMENT, AssistantRequests.category("Sortie"))
        assertEquals(LinkCategory.DECORATION, AssistantRequests.category("deco"))
        assertEquals(LinkCategory.ACTIVITE, AssistantRequests.category("Activité"))
        assertFailsWith<AssistantRequests.Invalid> { AssistantRequests.category("film") }
    }

    @Test
    fun onlyFoldersWhereTheIdeaStaysPrivateAndCanBeWritten() {
        assertEquals(listOf("a", "d"), AssistantRequests.writableFolders(folders, me).map { it.id })
    }

    @Test
    fun folderFoundIgnoringCaseAndAccents() {
        assertEquals("a", AssistantRequests.findFolder(folders, me, " vacances D'ETE ")?.id)
        assertEquals("d", AssistantRequests.findFolder(folders, me, "noel")?.id)
        // Public ou en lecture seule : jamais choisi, un dossier privé sera créé
        assertNull(AssistantRequests.findFolder(folders, me, "Recettes"))
        assertNull(AssistantRequests.findFolder(folders, me, "Anniversaire"))
    }

    @Test
    fun fieldsAreCheckedAndCleaned() {
        assertEquals("Pique-nique", AssistantRequests.title("  Pique-nique "))
        assertFailsWith<AssistantRequests.Invalid> { AssistantRequests.title(" ") }
        assertFailsWith<AssistantRequests.Invalid> { AssistantRequests.title("x".repeat(201)) }
        assertEquals("", AssistantRequests.url(null))
        assertEquals("https://exemple.fr/page?x=1", AssistantRequests.url(" https://exemple.fr/page?x=1 "))
        assertFailsWith<AssistantRequests.Invalid> { AssistantRequests.url("javascript:alert(1)") }
        assertFailsWith<AssistantRequests.Invalid> { AssistantRequests.url("exemple.fr") }
        assertEquals(listOf("plage", "été"), AssistantRequests.tags(listOf(" Plage", "plage", "", "Été")))
        assertFailsWith<AssistantRequests.Invalid> { AssistantRequests.tags((1..11).map { "t$it" }) }
        assertFailsWith<AssistantRequests.Invalid> { AssistantRequests.folderName("x".repeat(81)) }
    }
}
