package data

import kotlin.test.Test
import kotlin.test.assertEquals

/** Mêmes cas que le web (frontend/src/lib/preview.ts). */
class LinkPreviewLimitsTest {
    @Test
    fun cutsAtTheLimitWithoutBreakingAnEmoji() {
        assertEquals("court", "court".limitTo(300))
        assertEquals(300, "x".repeat(400).limitTo(300).length)
        assertEquals("a".repeat(299), ("a".repeat(299) + "😀b").limitTo(300))
    }

    @Test
    fun previewTextsFitTheFields() {
        val p = LinkPreview(title = "t".repeat(1000), description = "d".repeat(6000), price = "p".repeat(300), ingredients = listOf("i".repeat(250)))
            .withinLimits()
        assertEquals(300, p.title.length)
        assertEquals(5000, p.description.length)
        assertEquals(200, p.price.length)
        assertEquals(200, p.ingredients.single().length)
    }
}
