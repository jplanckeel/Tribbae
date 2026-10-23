package data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

@Serializable
data class AiSuggestedLink(
    val title: String = "",
    val description: String = "",
    val category: String = "LINK_CATEGORY_IDEE",
    val tags: List<String> = emptyList(),
    val ageRange: String = "",
    /** Pourquoi l'idée colle à la demande (« Parfait pour un jour de pluie »). */
    val why: String = "",
)

@Serializable
data class AiGenerateResponse(
    val ideas: List<AiSuggestedLink> = emptyList()
)

class AiRepository(private val baseUrl: String = Backend.url) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** count : nombre d'idées (0 : déduit de la demande) ; exclude : titres déjà proposés. */
    suspend fun generateIdeas(prompt: String, token: String, count: Int = 0, exclude: List<String> = emptyList()): AiGenerateResponse =
        withContext(Dispatchers.IO) {
            val conn = URL("$baseUrl/v1/ai/generate").openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.doOutput = true
            conn.connectTimeout = 180_000 // 3 minutes - Ollama peut être très lent
            conn.readTimeout = 180_000 // 3 minutes

            val body = kotlinx.serialization.json.buildJsonObject {
                put("prompt", kotlinx.serialization.json.JsonPrimitive(prompt))
                if (count > 0) put("count", kotlinx.serialization.json.JsonPrimitive(count))
                if (exclude.isNotEmpty()) put("exclude", kotlinx.serialization.json.JsonArray(exclude.map { kotlinx.serialization.json.JsonPrimitive(it) }))
            }.toString()
            OutputStreamWriter(conn.outputStream).use { it.write(body) }

            try {
                val response = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
                json.decodeFromString(response)
            } catch (e: Exception) {
                val errorStream = conn.errorStream
                if (errorStream != null) {
                    val error = BufferedReader(InputStreamReader(errorStream)).use { it.readText() }
                    throw Exception("AI generation failed (${conn.responseCode}): $error")
                }
                throw e
            } finally {
                conn.disconnect()
            }
        }
}
