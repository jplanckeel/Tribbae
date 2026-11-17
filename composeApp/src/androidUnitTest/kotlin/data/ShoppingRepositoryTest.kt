package data

import kotlinx.coroutines.runBlocking
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** « Les courses » hors ligne : changements gardés, envoyés dans l'ordre, liste du serveur. */
class ShoppingRepositoryTest {
    private class FakeStorage : Storage {
        var saved: String? = null
        override fun saveLinks(links: List<Link>) {}
        override fun loadLinks(): List<Link> = emptyList()
        override fun saveFolders(folders: List<Folder>) {}
        override fun loadFolders(): List<Folder> = emptyList()
        override fun saveTags(tags: List<String>) {}
        override fun loadTags(): List<String> = emptyList()
        override fun saveChildren(children: List<Child>) {}
        override fun loadChildren(): List<Child> = emptyList()
        override fun saveToken(token: String) {}
        override fun loadToken(): String? = null
        override fun saveShoppingList(json: String) { saved = json }
        override fun loadShoppingList(): String? = saved
    }

    private val serverId = "6afb733d08e669b7ade76974"
    private fun op(type: String, linkId: String = "", key: String = "", id: String = "", label: String = "") =
        ShoppingList.Op(type, linkId = linkId, key = key, id = id, label = label)

    @Test
    fun showsAChangeAtOnceAndKeepsItOffline() = runBlocking {
        val storage = FakeStorage()
        val repo = ShoppingRepository(storage).apply { useAccount("u1") }
        repo.change(op("item_add", id = "a", label = "Lait"))
        assertEquals(listOf("Lait"), repo.state.value.list.items.map { it.label })

        repo.sync({ throw IOException("offline") }, { throw IOException("offline") })
        assertTrue(repo.state.value.offline)
        assertEquals(1, repo.state.value.pending)

        // Relancée, l'application retrouve le changement en attente
        val again = ShoppingRepository(storage)
        assertEquals(1, again.state.value.pending)
        assertEquals(listOf("Lait"), again.state.value.list.items.map { it.label })
    }

    @Test
    fun sendsInOrderAndTakesTheServerList() = runBlocking {
        val repo = ShoppingRepository(FakeStorage()).apply { useAccount("u1") }
        repo.change(op("check", key = "lait|1 l"))
        repo.change(op("pantry_have", key = "sel"))
        var sent: List<String> = emptyList()
        val server = ShoppingList.Shared(version = 7, pantry = listOf("sel"), checked = listOf(ShoppingList.Check("lait|1 l", "u1", 5)))
        repo.sync({ ops -> sent = ops.map { it.type }; server }, { null })
        assertEquals(listOf("check", "pantry_have"), sent)
        assertEquals(0, repo.state.value.pending)
        assertEquals(7, repo.state.value.list.version)
        assertTrue(repo.state.value.loaded)
    }

    @Test
    fun waitsForALocalRecipeAndFollowsItsServerId() = runBlocking {
        val repo = ShoppingRepository(FakeStorage()).apply { useAccount("u1") }
        repo.change(op("menu_add", linkId = "12345"), ShoppingList.RecipeInfo("Soupe", listOf("1 potimarron")))
        repo.change(op("check", key = "potimarron|1"))
        var calls = 0
        repo.sync({ calls++; ShoppingList.Shared() }, { null })
        assertEquals(0, calls)
        assertEquals(2, repo.state.value.pending)
        assertEquals("Soupe", repo.state.value.list.menu.single().title)

        repo.replaceLinkId("12345", serverId)
        var sent: List<ShoppingList.Op> = emptyList()
        repo.sync({ ops -> sent = ops; ShoppingList.Shared(version = 2) }, { null })
        assertEquals(listOf(serverId, ""), sent.map { it.linkId })
    }

    @Test
    fun dropsARefusedChange() = runBlocking {
        val repo = ShoppingRepository(FakeStorage()).apply { useAccount("u1") }
        repo.change(op("menu_add", linkId = serverId), ShoppingList.RecipeInfo("Soupe", emptyList()))
        val message = repo.sync({ throw Exception("API error (400): invalid") }, { ShoppingList.Shared(version = 3) })
        assertEquals("Ce changement des courses n'a pas pu être enregistré.", message)
        assertEquals(0, repo.state.value.pending)
        assertEquals(emptyList(), repo.state.value.list.menu)
    }

    @Test
    fun forgetsTheListOfAnotherAccount() = runBlocking {
        val repo = ShoppingRepository(FakeStorage()).apply { useAccount("u1") }
        repo.change(op("item_add", id = "a", label = "Lait"))
        repo.useAccount("u2")
        assertEquals(0, repo.state.value.pending)
        repo.change(op("item_add", id = "b", label = "Pain"))
        repo.clear()
        assertEquals(ShoppingList.Shared(), repo.state.value.list)
        assertNull(repo.sync({ ShoppingList.Shared() }, { null }))
    }
}
