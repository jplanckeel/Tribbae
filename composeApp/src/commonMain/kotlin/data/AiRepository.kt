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
    val url: String = "",
    val imageUrl: String = "",
    val category: String = "LINK_CATEGORY_IDEE",
    val tags: List<String> = emptyList(),
    val ageRange: String = "",
    val price: String = "",
    val location: String = "",
    val ingredients: List<String> = emptyList()
)

@Serializable
data class AiGenerateResponse(
    val ideas: List<AiSuggestedLink> = emptyList()
)

class AiRepository(private val baseUrl: String = "http://10.0.2.2:8080") {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun generateIdeas(prompt: String, token: String): AiGenerateResponse =
        withContext(Dispatchers.IO) {
            val conn = URL("$baseUrl/v1/ai/generate").openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.doOutput = true
            conn.connectTimeout = 180_000 // 3 minutes - Ollama peut être très lent
            conn.readTimeout = 180_000 // 3 minutes

            val body = """{"prompt":${json.encodeToString(kotlinx.serialization.serializer(), prompt)}}"""
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
