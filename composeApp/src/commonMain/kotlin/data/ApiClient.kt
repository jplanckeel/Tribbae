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
    val ownerIsAdmin: Boolean = false,
    val linkCount: Int = 0,
    val likeCount: Int = 0,
    val likedByMe: Boolean = false,
    val aiGenerated: Boolean = false,
    val tags: List<String> = emptyList(),
    val bannerUrl: String = "",
    val description: String = "",
    val cover: String = "",
    val illustration: String = "",
    val viewMode: String = "",
    val sort: String = "",
) {
    /** Dossier affichable (dossier d'un autre membre : communauté ou lien de partage). */
    fun toFolder(): Folder = Folder(
        id = id,
        name = name,
        icon = runCatching { FolderIcon.valueOf(icon) }.getOrDefault(FolderIcon.FOLDER),
        color = runCatching { FolderColor.valueOf(color) }.getOrDefault(FolderColor.ORANGE),
        bannerUrl = bannerUrl,
        tags = tags,
        visibility = if (visibility.uppercase().contains("PUBLIC")) "PUBLIC" else "PRIVATE",
        ownerDisplayName = ownerDisplayName,
        ownerIsAdmin = ownerIsAdmin,
        linkCount = linkCount,
        likeCount = likeCount,
        likedByMe = likedByMe,
        aiGenerated = aiGenerated,
        description = description,
        cover = cover,
        illustration = illustration,
        viewMode = viewMode,
        sort = sort,
    )
}

@Serializable
data class ApiLink(
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
    val likedByMe: Boolean = false,
    val ownerDisplayName: String = "",
    val ownerIsAdmin: Boolean = false,
    val visibility: String = "",
    val createdAt: String = "",
    val updatedAt: String = ""
) {
    /** Idée en lecture (communauté, dossier public, lien de partage). */
    fun toLink(): Link = Link(
        id = id,
        title = title,
        url = url,
        description = description,
        category = runCatching { LinkCategory.valueOf(category.removePrefix("LINK_CATEGORY_")) }.getOrDefault(LinkCategory.IDEE),
        folderId = folderId.ifBlank { null },
        tags = tags,
        ageRange = ageRange,
        location = location,
        price = price,
        imageUrl = imageUrl,
        eventDate = eventDate.takeIf { it > 0 },
        rating = rating,
        ingredients = ingredients,
        likeCount = likeCount,
        likedByMe = likedByMe,
        ownerId = ownerId,
        ownerDisplayName = ownerDisplayName,
        ownerIsAdmin = ownerIsAdmin,
        visibility = visibility.ifBlank { "private" },
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

/**
 * Aperçu d'une page (GET /v1/links/preview) : titre, description, meilleure
 * image et tous les candidats (`images`), plus ce que les données schema.org
 * de la page révèlent (catégorie, ingrédients, prix, lieu, date en ms).
 */
@Serializable
data class LinkPreview(
    val title: String = "",
    val description: String = "",
    val image: String = "",
    val images: List<String> = emptyList(),
    val siteName: String = "",
    val category: String = "",
    val ingredients: List<String> = emptyList(),
    val price: String = "",
    val location: String = "",
    val eventDate: Long = 0
) {
    /** Catégorie suggérée, ou null si la page n'en indique pas. */
    val suggestedCategory: LinkCategory?
        get() = category.takeIf { it.isNotBlank() }
            ?.let { runCatching { LinkCategory.valueOf(it.removePrefix("LINK_CATEGORY_")) }.getOrNull() }

    /** Images candidates, la meilleure d'abord. */
    val allImages: List<String>
        get() = images.ifEmpty { listOfNotNull(image.takeIf { it.isNotBlank() }) }
}

@Serializable
data class SharedFolderResponse(
    val folder: ApiFolder = ApiFolder(),
    val links: List<ApiLink> = emptyList()
)

class ApiClient(private val baseUrl: String = Backend.url) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private suspend fun get(path: String, token: String? = null): String = withContext(Dispatchers.IO) {
        val conn = URL("$baseUrl$path").openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        if (!token.isNullOrBlank()) conn.setRequestProperty("Authorization", "Bearer $token")
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        try {
            BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    suspend fun listCommunityFolders(search: String = "", pageSize: Int = 20, pageToken: String = "", token: String? = null): ApiFolderResponse {
        val params = mutableListOf<String>()
        if (search.isNotBlank()) params += "search=${URLEncoder.encode(search, "UTF-8")}"
        if (pageSize > 0) params += "pageSize=$pageSize"
        if (pageToken.isNotBlank()) params += "pageToken=$pageToken"
        val qs = if (params.isNotEmpty()) "?${params.joinToString("&")}" else ""
        // Jeton facultatif : il renseigne likedByMe
        val body = get("/v1/community/folders$qs", token)
        return json.decodeFromString(body)
    }

    /** Idées d'un dossier visible (public, ou dont on est membre si un jeton est fourni). */
    suspend fun listFolderLinks(folderId: String, token: String? = null): ApiLinksResponse {
        val body = get("/v1/links?folderId=${URLEncoder.encode(folderId, "UTF-8")}", token)
        return json.decodeFromString(body)
    }

    suspend fun getSharedFolder(token: String): SharedFolderResponse {
        val body = get("/v1/share/$token")
        return json.decodeFromString(body)
    }

    /** Jeton facultatif : il renseigne likedByMe. */
    suspend fun listCommunityLinks(category: String = "", limit: Int = 50, token: String? = null): ApiLinksResponse {
        val params = mutableListOf<String>()
        if (category.isNotBlank()) params += "category=$category"
        if (limit > 0) params += "limit=$limit"
        val qs = if (params.isNotEmpty()) "?${params.joinToString("&")}" else ""
        val body = get("/v1/community/links$qs", token)
        return json.decodeFromString(body)
    }

    /** Le serveur récupère la page (protection SSRF côté backend) : nécessite une session. */
    suspend fun previewLink(url: String, token: String): LinkPreview {
        val body = get("/v1/links/preview?url=${URLEncoder.encode(url, "UTF-8")}", token)
        return json.decodeFromString(body)
    }
}
