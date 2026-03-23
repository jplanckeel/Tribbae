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
data class ApiComment(
    val id: String = "",
    val linkId: String = "",
    val userId: String = "",
    val userDisplayName: String = "",
    val userIsAdmin: Boolean = false,
    val text: String = "",
    val createdAt: String = "",
    val updatedAt: String = ""
)

@Serializable
data class ApiCreateCommentRequest(
    val linkId: String,
    val text: String
)

@Serializable
data class ApiCreateCommentResponse(
    val comment: ApiComment
)

@Serializable
data class ApiGetCommentsResponse(
    val comments: List<ApiComment> = emptyList()
)

@Serializable
data class ApiGetCommentCountResponse(
    val count: Int = 0
)

class CommentRepository(
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
        println("DEBUG: CommentRepository request - path=$path, method=$method")
        
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
            println("DEBUG: CommentRepository response code: $responseCode")
            
            if (responseCode in 200..299) {
                val response = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
                println("DEBUG: CommentRepository response: $response")
                deserializer(response)
            } else {
                val errorStream = conn.errorStream
                val error = if (errorStream != null) {
                    BufferedReader(InputStreamReader(errorStream)).use { it.readText() }
                } else {
                    "HTTP $responseCode"
                }
                println("ERROR: CommentRepository error: $error")
                throw Exception("API error ($responseCode): $error")
            }
        } catch (e: Exception) {
            println("ERROR: CommentRepository exception: ${e.message}")
            e.printStackTrace()
            throw e
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Create a new comment on a link
     * @param linkId The ID of the link to comment on
     * @param text The comment text
     * @return The created Comment
     */
    suspend fun createComment(linkId: String, text: String): Result<Comment> = runCatching {
        val requestBody = json.encodeToString(
            ApiCreateCommentRequest.serializer(),
            ApiCreateCommentRequest(linkId = linkId, text = text)
        )
        request("/v1/links/$linkId/comments", "POST", requestBody) { response ->
            val apiResponse = json.decodeFromString<ApiCreateCommentResponse>(response)
            apiResponse.comment.toComment()
        }
    }

    /**
     * Get all comments for a link
     * @param linkId The ID of the link
     * @return List of Comment objects sorted by creation date (newest first)
     */
    suspend fun getComments(linkId: String): Result<List<Comment>> = runCatching {
        request("/v1/links/$linkId/comments", "GET") { response ->
            val apiResponse = json.decodeFromString<ApiGetCommentsResponse>(response)
            apiResponse.comments.map { it.toComment() }
        }
    }

    /**
     * Delete a comment
     * @param commentId The ID of the comment to delete
     */
    suspend fun deleteComment(commentId: String): Result<Unit> = runCatching {
        request("/v1/comments/$commentId", "DELETE", null) { _ -> Unit }
    }

    /**
     * Get the comment count for a link
     * @param linkId The ID of the link
     * @return The number of comments
     */
    suspend fun getCommentCount(linkId: String): Result<Int> = runCatching {
        request("/v1/links/$linkId/comments/count", "GET") { response ->
            val apiResponse = json.decodeFromString<ApiGetCommentCountResponse>(response)
            apiResponse.count
        }
    }

    private fun ApiComment.toComment(): Comment {
        return Comment(
            id = id,
            linkId = linkId,
            userId = userId,
            userDisplayName = userDisplayName,
            userIsAdmin = userIsAdmin,
            text = text,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }
}

