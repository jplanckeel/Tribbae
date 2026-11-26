package data

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// Mêmes cas que frontend/src/lib/categories.test.ts
class LinkCategoryTest {
    @Test
    fun ideaCategoryIsMergedIntoActivity() {
        assertFalse(LinkCategory.entries.any { it.name == "IDEE" })
        assertEquals(LinkCategory.ACTIVITE, LinkCategory.parse("LINK_CATEGORY_IDEE"))
        assertEquals(LinkCategory.ACTIVITE, LinkCategory.parse("IDEE"))
        assertEquals(LinkCategory.ACTIVITE, LinkCategory.parse("LINK_CATEGORY_UNSPECIFIED"))
        assertEquals(LinkCategory.ACTIVITE, LinkCategory.parse(""))
        assertEquals(LinkCategory.ACTIVITE, LinkCategory.parse(null))
        assertEquals(LinkCategory.RECETTE, LinkCategory.parse("LINK_CATEGORY_RECETTE"))
        assertEquals(LinkCategory.EVENEMENT, LinkCategory.parse("EVENEMENT"))
    }

    /** Idées gardées sur le téléphone par une version précédente de l'app. */
    @Test
    fun storedIdeaCategoryIsReadAsActivity() {
        val json = Json { ignoreUnknownKeys = true }
        val stored = """[{"id":"1","title":"Escape game","url":"","category":"IDEE"},{"id":"2","title":"Crêpes","url":"","category":"RECETTE"}]"""
        val links = json.decodeFromString<List<Link>>(stored)
        assertEquals(listOf(LinkCategory.ACTIVITE, LinkCategory.RECETTE), links.map { it.category })
        // Réenregistrée sous son nouveau nom
        assertTrue(json.encodeToString(links).contains("\"category\":\"ACTIVITE\""))
    }
}
