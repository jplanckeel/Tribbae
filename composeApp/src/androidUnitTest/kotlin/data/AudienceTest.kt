package data

import kotlin.test.Test
import kotlin.test.assertEquals

/** Mêmes cas que frontend/src/lib/visibility.test.ts. */
class AudienceTest {
    private val peers = setOf("moi", "tribu")
    private val isPeer: (String) -> Boolean = { it in peers }
    private val collaborator = Collaborator(userId = "camille", displayName = "Camille")
    private fun folder(visibility: String = "VISIBILITY_PRIVATE", collaborators: List<Collaborator> = emptyList(), ownerId: String = "moi") =
        Folder(id = "1", name = "Dossier", visibility = visibility, collaborators = collaborators, ownerId = ownerId)
    private fun link(visibility: String = "private", ownerId: String = "moi") =
        Link(id = "1", title = "Idée", url = "", category = LinkCategory.IDEE, visibility = visibility, ownerId = ownerId)

    @Test
    fun folderOfTheAccountOrTribeIsPrivate() {
        assertEquals(Audience.PRIVATE, Audience.ofFolder(folder(), isPeer))
        assertEquals(Audience.PRIVATE, Audience.ofFolder(folder(ownerId = "tribu"), isPeer))
        assertEquals(Audience.PRIVATE, Audience.ofFolder(folder(ownerId = ""), isPeer))
    }

    @Test
    fun collaboratorsOrAnOutsideOwnerMeanTribe() {
        assertEquals(Audience.SHARED, Audience.ofFolder(folder(collaborators = listOf(collaborator)), isPeer))
        assertEquals(Audience.SHARED, Audience.ofFolder(folder(ownerId = "tata"), isPeer))
    }

    @Test
    fun publicFolderIsCommunityEvenWithCollaborators() {
        assertEquals(Audience.PUBLIC, Audience.ofFolder(folder("VISIBILITY_PUBLIC", listOf(collaborator)), isPeer))
    }

    @Test
    fun ideaFollowsItselfThenItsFolder() {
        assertEquals(Audience.PUBLIC, Audience.ofIdea(link("public"), null, isPeer))
        assertEquals(Audience.PRIVATE, Audience.ofIdea(link("private"), null, isPeer))
        assertEquals(Audience.PRIVATE, Audience.ofIdea(link("private"), folder(), isPeer))
        assertEquals(Audience.SHARED, Audience.ofIdea(link("private"), folder(collaborators = listOf(collaborator)), isPeer))
    }

    @Test
    fun ideaInItsAuthorsPublicFolderIsCommunityEvenMarkedPrivate() {
        assertEquals(Audience.PUBLIC, Audience.ofIdea(link(), folder("VISIBILITY_PUBLIC"), isPeer))
        assertEquals(Audience.PUBLIC, Audience.ofIdea(link(ownerId = ""), folder("VISIBILITY_PUBLIC"), isPeer))
    }

    @Test
    fun ideaInSomeoneElsesPublicFolderStaysTribe() {
        assertEquals(Audience.SHARED, Audience.ofIdea(link(ownerId = "tribu"), folder("VISIBILITY_PUBLIC"), isPeer))
    }

    @Test
    fun unknownFolderFollowsTheServer() {
        assertEquals(Audience.PUBLIC, Audience.ofIdea(link(ownerId = "tata").copy(publicViaFolder = true), null, isPeer))
    }
}
