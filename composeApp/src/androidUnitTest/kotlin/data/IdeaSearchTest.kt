package data

import kotlin.test.Test
import kotlin.test.assertEquals

/** Mêmes cas que « recherche dans ses idées » de frontend/src/lib/links.test.ts. */
class IdeaSearchTest {
    private fun idea(id: String, title: String = "", createdAt: String = "", tags: List<String> = emptyList(), location: String = "") =
        Link(id = id, title = title, url = "", category = LinkCategory.IDEE, createdAt = createdAt, tags = tags, location = location)

    private val links = listOf(
        idea("a", "Crêpes de Mamie", "2026-10-01"),
        idea("b", "Sortie au parc", "2026-10-03", tags = listOf("crêpes")),
        idea("c", "Goûter", "2026-10-02", location = "Crêperie du port"),
        idea("d", "Lego", "2026-10-04"),
    )

    @Test
    fun findsTitleTagAndPlaceLatestFirst() {
        assertEquals(listOf("b", "c", "a"), IdeaSearch.mine(links, "crêpe").map { it.id })
    }

    @Test
    fun ignoresCaseAndSpaces() {
        assertEquals(listOf("d"), IdeaSearch.mine(links, "  LEGO ").map { it.id })
    }

    @Test
    fun emptyQueryFindsNothing() {
        assertEquals(emptyList(), IdeaSearch.mine(links, "  "))
    }
}
