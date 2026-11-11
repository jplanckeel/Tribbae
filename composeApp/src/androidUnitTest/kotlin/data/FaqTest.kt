package data

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FaqTest {
    @Test
    fun readsTheSiteFaq() {
        // Le fichier même que lit le site : l'app ne peut plus diverger
        val sections = Faq.parse(File("../frontend/public/faq.json").readText())
        assertTrue(sections.isNotEmpty())
        assertTrue(sections.all { it.title.isNotBlank() && it.questions.isNotEmpty() })
        assertTrue(sections.flatMap { it.questions }.none { (q, a) -> q.isBlank() || a.isBlank() })
    }

    @Test
    fun skipsIncompleteItemsAndBadJson() {
        assertEquals(listOf("Q" to "R"), Faq.parse("""[{"title":"T","items":[["Q","R"],["seule"]]}]""").single().questions)
        assertEquals(emptyList(), Faq.parse("pas du json"))
    }
}
