package data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * FAQ partagée avec le site : `frontend/public/faq.json`, copiée dans les
 * assets de l'app à la compilation (tâche `syncFaq` du build Gradle). Une seule
 * source, la même que la page /aide et la FAQPage servie aux moteurs.
 */
@Serializable
data class FaqSection(val title: String, val icon: String = "", val items: List<List<String>> = emptyList()) {
    /** Questions complètes seulement : [question, réponse]. */
    val questions: List<Pair<String, String>> get() = items.filter { it.size >= 2 }.map { it[0] to it[1] }
}

object Faq {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(text: String): List<FaqSection> = runCatching { json.decodeFromString<List<FaqSection>>(text) }.getOrDefault(emptyList())
}
