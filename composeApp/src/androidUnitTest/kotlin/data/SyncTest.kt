package data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Synchronisation : ce qui a été fait hors ligne ne doit pas être effacé par le serveur. */
class SyncTest {
    private val srv1 = "6a00000000000000000000a1"
    private val srv2 = "6a00000000000000000000a2"
    private val srv3 = "6a00000000000000000000a3"

    private fun link(id: String, title: String = id, folderId: String? = null) = Link(id = id, title = title, url = "", category = LinkCategory.IDEE, folderId = folderId)

    @Test
    fun recognizesLocalIds() {
        assertFalse(SyncIds.isLocal(srv1))
        assertTrue(SyncIds.isLocal("-4815162342"))
        assertTrue(SyncIds.isLocal(""))
    }

    @Test
    fun keepsWhatWasDoneOffline() {
        // srv2 a été supprimée ici hors ligne : elle n'est plus dans la liste locale
        val local = listOf(link(srv1, "modifiée hors ligne"), link("-42", "créée hors ligne"))
        val server = listOf(link(srv1, "version serveur"), link(srv2, "encore sur le serveur"), link(srv3, "nouvelle d'un proche"))

        val merged = SyncIds.merge(local, server, { it.id }, pendingUpdates = setOf(srv1), pendingDeletes = setOf(srv2))

        assertEquals(listOf("modifiée hors ligne", "nouvelle d'un proche", "créée hors ligne"), merged.map { it.title })
    }

    @Test
    fun serverWinsWithoutPendingChanges() {
        val local = listOf(link(srv1, "ancienne"), link(srv2, "supprimée par un proche"))
        val server = listOf(link(srv1, "à jour"))
        assertEquals(listOf("à jour"), SyncIds.merge(local, server, { it.id }).map { it.title })
    }

    @Test
    fun newIdsFollowFoldersAndChildren() {
        val repo = LinkRepository()
        repo.addChild(Child(id = "-1", name = "Léo", birthDate = 0))
        repo.addFolder(Folder(id = "-2", name = "Noël", childIds = listOf("-1")))
        repo.addLink(link("-3", folderId = "-2"))

        repo.replaceChildId("-1", srv1)
        repo.replaceFolderId("-2", srv2)
        repo.replaceLinkId("-3", srv3)

        assertEquals(listOf(srv1), repo.folders.value.single().childIds)
        assertEquals(srv2, repo.folders.value.single().id)
        assertEquals(srv2, repo.links.value.single().folderId, "l'idée reste dans son dossier")
        assertEquals(srv3, repo.links.value.single().id)
    }

    @Test
    fun pendingChangesSurviveARestart() {
        val storage = object : Storage {
            var saved = PendingChanges()
            override fun saveLinks(links: List<Link>) {}
            override fun loadLinks() = emptyList<Link>()
            override fun saveFolders(folders: List<Folder>) {}
            override fun loadFolders() = emptyList<Folder>()
            override fun saveTags(tags: List<String>) {}
            override fun loadTags() = emptyList<String>()
            override fun saveChildren(children: List<Child>) {}
            override fun loadChildren() = emptyList<Child>()
            override fun saveToken(token: String) {}
            override fun loadToken(): String? = null
            override fun savePending(pending: PendingChanges) { saved = pending }
            override fun loadPending() = saved
        }
        LinkRepository(storage).updatePending { it.copy(linkDeletes = setOf(srv1)) }
        assertEquals(setOf(srv1), LinkRepository(storage).pending.linkDeletes)
    }
}
