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
data class User(
    val id: String,
    val email: String,
    val displayName: String,
    val isAdmin: Boolean,
    val isPremium: Boolean,
    val createdAt: Long
)

@Serializable
data class ListUsersResponse(
    val users: List<User>
)

@Serializable
data class UpdateUserPremiumRequest(
    val isPremium: Boolean
)

@Serializable
data class UpdateUserPremiumResponse(
    val user: User
)

class AdminRepository(
    private val baseUrl: String = "https://tribbae.bananaops.cloud",
    private val tokenProvider: () -> String?
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun listUsers(): List<User> = withContext(Dispatchers.IO) {
        val conn = URL("$baseUrl/v1/admin/users").openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("Content-Type", "application/json")
        val token = tokenProvider()
        if (token != null) {
            conn.setRequestProperty("Authorization", "Bearer $token")
        }
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000

        try {
            val response = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
            val result = json.decodeFromString<ListUsersResponse>(response)
            result.users
        } catch (e: Exception) {
            val errorStream = conn.errorStream
            if (errorStream != null) {
                val error = BufferedReader(InputStreamReader(errorStream)).use { it.readText() }
                throw Exception("List users failed: $error")
            }
            throw e
        } finally {
            conn.disconnect()
        }
    }

    suspend fun updateUserPremium(userId: String, isPremium: Boolean): User = withContext(Dispatchers.IO) {
        val conn = URL("$baseUrl/v1/admin/users/$userId/premium").openConnection() as HttpURLConnection
        conn.requestMethod = "PUT"
        conn.setRequestProperty("Content-Type", "application/json")
        val token = tokenProvider()
        if (token != null) {
            conn.setRequestProperty("Authorization", "Bearer $token")
        }
        conn.doOutput = true
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000

        val body = json.encodeToString(UpdateUserPremiumRequest.serializer(), UpdateUserPremiumRequest(isPremium))
        OutputStreamWriter(conn.outputStream).use { it.write(body) }

        try {
            val response = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
            val result = json.decodeFromString<UpdateUserPremiumResponse>(response)
            result.user
        } catch (e: Exception) {
            val errorStream = conn.errorStream
            if (errorStream != null) {
                val error = BufferedReader(InputStreamReader(errorStream)).use { it.readText() }
                throw Exception("Update user premium failed: $error")
            }
            throw e
        } finally {
            conn.disconnect()
        }
    }
}
