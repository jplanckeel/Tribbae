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

/** Ce qu'affiche un lien d'invitation dans une tribu, avant de l'accepter. */
@Serializable
data class TribeInvitationInfo(
    val email: String = "",
    val tribeName: String = "",
    val invitedByName: String = "",
    val memberNames: List<String> = emptyList(),
    val expiresAt: Long = 0
)

/** Lecture d'un lien d'invitation : accessible sans compte (le jeton tient lieu d'accès). */
class InvitationRepository(private val baseUrl: String = Backend.url) {
    private val json = Json { ignoreUnknownKeys = true }

    /** null si l'invitation n'est plus valable (expirée, annulée, déjà utilisée). */
    suspend fun lookup(token: String): TribeInvitationInfo? = withContext(Dispatchers.IO) {
        val conn = URL("$baseUrl/v1/tribe/invitations/lookup").openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            val body = json.encodeToString(kotlinx.serialization.json.JsonObject.serializer(),
                kotlinx.serialization.json.buildJsonObject { put("token", kotlinx.serialization.json.JsonPrimitive(token)) })
            OutputStreamWriter(conn.outputStream).use { it.write(body) }
            when (val code = conn.responseCode) {
                200 -> json.decodeFromString<TribeInvitationInfo>(BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() })
                404 -> null
                else -> throw Exception("HTTP $code")
            }
        } finally {
            conn.disconnect()
        }
    }
}
