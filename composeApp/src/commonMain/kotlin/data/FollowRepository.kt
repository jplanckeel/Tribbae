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
data class ApiUserProfile(
    val id: String = "",
    val displayName: String = "",
    val email: String = "",
    val isAdmin: Boolean = false,
    val followerCount: Int = 0,
    val followingCount: Int = 0
)

@Serializable
data class ApiFollowersResponse(
    val followers: List<ApiUserProfile> = emptyList()
)

@Serializable
data class ApiFollowingResponse(
    val following: List<ApiUserProfile> = emptyList()
)

@Serializable
data class ApiIsFollowingResponse(
    val isFollowing: Boolean = false
)

class FollowRepository(
    private val baseUrl: String = "https://tribbae.bananaops.cloud",
    private val sessionManager: SessionManager
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private suspend fun <T> request(
        path: String,
        method: String = "GET",
        body: String? = null,
        deserializer: (String) -> T
    ): T = withContext(Dispatchers.IO) {
        val token = sessionManager.getToken() ?: throw Exception("Not authenticated")
        println("DEBUG: FollowRepository request - path=$path, method=$method")
        
        val conn = URL("$baseUrl$path").openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000

        if (body != null) {
            conn.doOutput = true
            OutputStreamWriter(conn.outputStream).use { it.write(body) }
        }

        try {
            val responseCode = conn.responseCode
            println("DEBUG: FollowRepository response code: $responseCode")
            
            if (responseCode in 200..299) {
                val response = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
                println("DEBUG: FollowRepository response: $response")
                deserializer(response)
            } else {
                val errorStream = conn.errorStream
                val error = if (errorStream != null) {
                    BufferedReader(InputStreamReader(errorStream)).use { it.readText() }
                } else {
                    "HTTP $responseCode"
                }
                println("ERROR: FollowRepository error: $error")
                throw Exception("API error ($responseCode): $error")
            }
        } catch (e: Exception) {
            println("ERROR: FollowRepository exception: ${e.message}")
            e.printStackTrace()
            throw e
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Follow a user
     * @param userId The ID of the user to follow
     */
    suspend fun follow(userId: String): Result<Unit> = runCatching {
        request("/v1/users/$userId/follow", "POST", "{}") { _ -> Unit }
    }

    /**
     * Unfollow a user
     * @param userId The ID of the user to unfollow
     */
    suspend fun unfollow(userId: String): Result<Unit> = runCatching {
        request("/v1/users/$userId/follow", "DELETE", null) { _ -> Unit }
    }

    /**
     * Check if the current user is following another user
     * @param userId The ID of the user to check
     * @return true if following, false otherwise
     */
    suspend fun isFollowing(userId: String): Result<Boolean> = runCatching {
        request("/v1/users/$userId/is-following", "GET") { response ->
            json.decodeFromString<ApiIsFollowingResponse>(response).isFollowing
        }
    }

    /**
     * Get the list of followers for a user
     * @param userId The ID of the user
     * @return List of UserProfile objects representing followers
     */
    suspend fun getFollowers(userId: String): Result<List<UserProfile>> = runCatching {
        request("/v1/users/$userId/followers", "GET") { response ->
            val apiResponse = json.decodeFromString<ApiFollowersResponse>(response)
            apiResponse.followers.map { it.toUserProfile() }
        }
    }

    /**
     * Get the list of users that a user is following
     * @param userId The ID of the user
     * @return List of UserProfile objects representing following
     */
    suspend fun getFollowing(userId: String): Result<List<UserProfile>> = runCatching {
        request("/v1/users/$userId/following", "GET") { response ->
            val apiResponse = json.decodeFromString<ApiFollowingResponse>(response)
            apiResponse.following.map { it.toUserProfile() }
        }
    }

    /**
     * Get the follower count for a user
     * @param userId The ID of the user
     * @return The number of followers
     */
    suspend fun getFollowerCount(userId: String): Result<Int> = runCatching {
        val followers = getFollowers(userId).getOrThrow()
        followers.size
    }

    /**
     * Get the following count for a user
     * @param userId The ID of the user
     * @return The number of users being followed
     */
    suspend fun getFollowingCount(userId: String): Result<Int> = runCatching {
        val following = getFollowing(userId).getOrThrow()
        following.size
    }

    private fun ApiUserProfile.toUserProfile(): UserProfile {
        return UserProfile(
            id = id,
            displayName = displayName,
            email = email,
            isAdmin = isAdmin,
            followerCount = followerCount,
            followingCount = followingCount
        )
    }
}

