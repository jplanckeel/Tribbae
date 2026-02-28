package data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

@Serializable
data class ApiFolderResponse(
    val folders: List<ApiFolder> = emptyList(),
    val nextPageToken: String = ""
)

@Serializable
data class ApiLinksResponse(
    val links: List<ApiLink> = emptyList()
)

@Serializable
data class ApiFolder(
    val id: String = "",
    val ownerId: String = "",
    val name: String = "",
    val icon: String = "",
    val color: String = "",
    val visibility: String = "",
    val shareToken: String = "",
    val ownerDisplayName: String = "",
    val linkCount: Int = 0
)

@Serializable
data class ApiLink(
    val id: String = "",
    val ownerId: String = "",
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
    val likedByMe: Boolean = false,
    val ownerDisplayName: String = ""
)

@Serializable
data class SharedFolderResponse(
    val folder: ApiFolder = ApiFolder(),
    val links: List<ApiLink> = emptyList()
)

class ApiClient(private val baseUrl: String = "https://tribbae.bananaops.cloud") {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private suspend fun get(path: String): String = withContext(Dispatchers.IO) {
        val conn = URL("$baseUrl$path").openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        try {
            BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    suspend fun listCommunityFolders(search: String = "", pageSize: Int = 20, pageToken: String = ""): ApiFolderResponse {
        val params = mutableListOf<String>()
        if (search.isNotBlank()) params += "search=${URLEncoder.encode(search, "UTF-8")}"
        if (pageSize > 0) params += "pageSize=$pageSize"
        if (pageToken.isNotBlank()) params += "pageToken=$pageToken"
        val qs = if (params.isNotEmpty()) "?${params.joinToString("&")}" else ""
        val body = get("/v1/community/folders$qs")
        return json.decodeFromString(body)
    }

    suspend fun getSharedFolder(token: String): SharedFolderResponse {
        val body = get("/v1/share/$token")
        return json.decodeFromString(body)
    }

    suspend fun listCommunityLinks(category: String = "", limit: Int = 50): ApiLinksResponse {
        val params = mutableListOf<String>()
        if (category.isNotBlank()) params += "category=$category"
        if (limit > 0) params += "limit=$limit"
        val qs = if (params.isNotEmpty()) "?${params.joinToString("&")}" else ""
        val body = get("/v1/community/links$qs")
        return json.decodeFromString(body)
    }
}
