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
import java.net.URLEncoder

@Serializable
data class ApiFolderListResponse(
    val folders: List<ApiAuthFolder> = emptyList()
)

@Serializable
data class ApiAuthFolder(
    val id: String = "",
    val ownerId: String = "",
    val name: String = "",
    val icon: String = "",
    val color: String = "",
    val visibility: String = "",
    val shareToken: String = "",
    val ownerDisplayName: String = "",
    val linkCount: Int = 0,
    val bannerUrl: String = "",
    val tags: List<String> = emptyList(),
    val likeCount: Int = 0
)

@Serializable
data class ApiLinkListResponse(
    val links: List<ApiAuthLink> = emptyList()
)

@Serializable
data class ApiAuthLink(
    val id: String = "",
    val ownerId: String = "",
    val folderId: String = "",
    val title: String = "",
    val url: String = "",
    val description: String = "",
    val category: String = "",
    val tags: List<String> = emptyList(),
    val ageRange: String = "",
    val location: String = "",
    val price: String = "",
    val imageUrl: String = "",
    val eventDate: Long = 0,
    val reminderEnabled: Boolean = false,
    val rating: Int = 0,
    val ingredients: List<String> = emptyList(),
    val likeCount: Int = 0,
    val likedByMe: Boolean = false
)

@Serializable
data class CreateFolderRequest(
    val name: String,
    val icon: String = "📁",
    val color: String = "ORANGE",
    val visibility: String = "PRIVATE",
    val bannerUrl: String = "",
    val tags: List<String> = emptyList()
)

@Serializable
data class CreateLinkRequest(
    val folderId: String = "",
    val title: String,
    val url: String = "",
    val description: String = "",
    val category: String,
    val tags: List<String> = emptyList(),
    val ageRange: String = "",
    val location: String = "",
    val price: String = "",
    val imageUrl: String = "",
    val eventDate: Long = 0,
    val reminderEnabled: Boolean = false,
    val rating: Int = 0,
    val ingredients: List<String> = emptyList()
)

@Serializable
data class UpdateLinkRequest(
    val linkId: String,
    val folderId: String = "",
    val title: String,
    val url: String = "",
    val description: String = "",
    val category: String,
    val tags: List<String> = emptyList(),
    val ageRange: String = "",
    val location: String = "",
    val price: String = "",
    val imageUrl: String = "",
    val eventDate: Long = 0,
    val reminderEnabled: Boolean = false,
    val rating: Int = 0,
    val ingredients: List<String> = emptyList()
)

class AuthenticatedApiClient(
    private val baseUrl: String = "http://10.0.2.2:8080",
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
            val response = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
            deserializer(response)
        } catch (e: Exception) {
            val errorStream = conn.errorStream
            if (errorStream != null) {
                val error = BufferedReader(InputStreamReader(errorStream)).use { it.readText() }
                throw Exception("API error (${conn.responseCode}): $error")
            }
            throw e
        } finally {
            conn.disconnect()
        }
    }

    // Folders
    suspend fun listFolders(): ApiFolderListResponse {
        return request("/v1/folders", "GET") { response ->
            json.decodeFromString(response)
        }
    }

    suspend fun createFolder(req: CreateFolderRequest): ApiAuthFolder {
        val body = json.encodeToString(CreateFolderRequest.serializer(), req)
        return request("/v1/folders", "POST", body) { response ->
            @Serializable
            data class FolderResponse(val folder: ApiAuthFolder)
            json.decodeFromString<FolderResponse>(response).folder
        }
    }

    suspend fun deleteFolder(folderId: String) {
        request("/v1/folders/$folderId", "DELETE", null) { _ -> Unit }
    }

    // Links
    suspend fun listLinks(folderId: String? = null): ApiLinkListResponse {
        val path = if (folderId != null) "/v1/links?folderId=$folderId" else "/v1/links"
        return request(path, "GET") { response ->
            json.decodeFromString(response)
        }
    }

    suspend fun createLink(req: CreateLinkRequest): ApiAuthLink {
        val body = json.encodeToString(CreateLinkRequest.serializer(), req)
        return request("/v1/links", "POST", body) { response ->
            @Serializable
            data class LinkResponse(val link: ApiAuthLink)
            json.decodeFromString<LinkResponse>(response).link
        }
    }

    suspend fun updateLink(req: UpdateLinkRequest): ApiAuthLink {
        val body = json.encodeToString(UpdateLinkRequest.serializer(), req)
        return request("/v1/links/${req.linkId}", "PUT", body) { response ->
            @Serializable
            data class LinkResponse(val link: ApiAuthLink)
            json.decodeFromString<LinkResponse>(response).link
        }
    }

    suspend fun deleteLink(linkId: String) {
        request("/v1/links/$linkId", "DELETE", null) { _ -> Unit }
    }
}

    // Link likes
    suspend fun likeLink(linkId: String): Int {
        return request("/v1/links/$linkId/like", "POST", "{}") { response ->
            @Serializable
            data class LikeResponse(val likeCount: Int)
            json.decodeFromString<LikeResponse>(response).likeCount
        }
    }

    suspend fun unlikeLink(linkId: String): Int {
        return request("/v1/links/$linkId/like", "DELETE", null) { response ->
            @Serializable
            data class LikeResponse(val likeCount: Int)
            json.decodeFromString<LikeResponse>(response).likeCount
        }
    }
