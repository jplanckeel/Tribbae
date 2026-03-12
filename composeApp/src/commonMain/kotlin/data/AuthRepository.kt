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
data class LoginRequest(
    val email: String,
    val password: String
)

@Serializable
data class RegisterRequest(
    val email: String,
    val password: String,
    val displayName: String
)

@Serializable
data class AuthResponse(
    val userId: String = "",
    val token: String = "",
    val displayName: String = "",
    val isAdmin: Boolean = false
)

class AuthRepository(private val baseUrl: String = "https://tribbae.bananaops.cloud") {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun login(email: String, password: String): AuthResponse =
        withContext(Dispatchers.IO) {
            try {
                println("DEBUG: Tentative de connexion à $baseUrl/v1/auth/login")
                val conn = URL("$baseUrl/v1/auth/login").openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true
                conn.connectTimeout = 10_000
                conn.readTimeout = 10_000

                val body = json.encodeToString(LoginRequest.serializer(), LoginRequest(email, password))
                println("DEBUG: Body envoyé: $body")
                OutputStreamWriter(conn.outputStream).use { it.write(body) }

                val responseCode = conn.responseCode
                println("DEBUG: Code de réponse HTTP: $responseCode")
                
                if (responseCode == 200) {
                    val response = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
                    println("DEBUG: Réponse reçue: $response")
                    val authResponse = json.decodeFromString<AuthResponse>(response)
                    println("DEBUG: AuthResponse décodé: userId=${authResponse.userId}, token=${authResponse.token}, displayName=${authResponse.displayName}")
                    authResponse
                } else {
                    val errorStream = conn.errorStream
                    val error = if (errorStream != null) {
                        BufferedReader(InputStreamReader(errorStream)).use { it.readText() }
                    } else {
                        "HTTP $responseCode"
                    }
                    println("DEBUG: Erreur HTTP $responseCode: $error")
                    throw Exception("Login failed (HTTP $responseCode): $error")
                }
            } catch (e: Exception) {
                println("DEBUG: Exception lors du login: ${e.message}")
                e.printStackTrace()
                throw Exception("Login error: ${e.message}", e)
            }
        }

    suspend fun register(email: String, password: String, displayName: String): AuthResponse =
        withContext(Dispatchers.IO) {
            val conn = URL("$baseUrl/v1/auth/register").openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000

            val body = json.encodeToString(RegisterRequest.serializer(), RegisterRequest(email, password, displayName))
            OutputStreamWriter(conn.outputStream).use { it.write(body) }

            try {
                val response = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
                json.decodeFromString(response)
            } catch (e: Exception) {
                val errorStream = conn.errorStream
                if (errorStream != null) {
                    val error = BufferedReader(InputStreamReader(errorStream)).use { it.readText() }
                    throw Exception("Registration failed: $error")
                }
                throw e
            } finally {
                conn.disconnect()
            }
        }
}
