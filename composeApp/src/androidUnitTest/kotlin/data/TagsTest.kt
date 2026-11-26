package data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

// Mêmes cas que « filtre par tag d'une catégorie » dans frontend/src/lib/links.test.ts
class TagsTest {
    private fun link(id: String, vararg tags: String) = Link(id = id, title = id, url = "", category = LinkCategory.ACTIVITE, tags = tags.toList())

    private val links = listOf(link("1", "pluie", "été"), link("2", "été"), link("3"), link("4", "zoo"), link("5", "automne"))

    @Test
    fun listsTagsByUseThenInFrenchOrder() {
        assertEquals(listOf("été" to 2, "automne" to 1, "pluie" to 1, "zoo" to 1), Tags.filter(links, null).tags)
    }

    @Test
    fun keepsTheIdeasOfTheChosenTag() {
        val f = Tags.filter(links, "été")
        assertEquals("été", f.active)
        assertEquals(listOf("1", "2"), f.links.map { it.id })
    }

    @Test
    fun showsEverythingWithoutTagOrWithAStaleOne() {
        assertEquals(5, Tags.filter(links, null).links.size)
        val stale = Tags.filter(links, "noël")
        assertNull(stale.active)
        assertEquals(5, stale.links.size)
    }

    @Test
    fun hasNothingToOfferWithoutTags() {
        val plain = listOf(link("1"), link("2"))
        val f = Tags.filter(plain, "pluie")
        assertEquals(emptyList(), f.tags)
        assertNull(f.active)
        assertEquals(plain, f.links)
    }
}
