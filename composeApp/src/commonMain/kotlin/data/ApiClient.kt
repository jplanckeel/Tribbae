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
    /** Liste de cadeaux : ses idées Cadeau se réservent depuis le lien de partage */
    val giftReservations: Boolean = false,
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
        giftReservations = giftReservations,
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
    val updatedAt: String = "",
    val createdVia: String = "",
    val publicViaFolder: Boolean = false,
    val hidden: Boolean = false,
    val giftStatus: String = "",
    // « C'est fait » (data/Done.kt)
    val doneCount: Int = 0,
    val lastDoneAt: String = "",
    val redo: Boolean = false,
    val donePromptDismissed: Boolean = false,
    val doneFamilies: Int = 0,
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
        updatedAt = updatedAt,
        createdVia = createdVia,
        publicViaFolder = publicViaFolder,
        hidden = hidden,
        giftStatus = giftStatus,
        doneCount = doneCount,
        lastDoneAt = lastDoneAt,
        redo = redo,
        donePromptDismissed = donePromptDismissed,
        doneFamilies = doneFamilies,
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

    /**
     * Textes ramenés aux limites des champs et du serveur (titre 300, description
     * 5000, prix / lieu / ingrédient 200) : une page au titre trop long (ou lue sur
     * le téléphone, sans coupe) donnerait une idée qu'on ne peut plus modifier.
     */
    fun withinLimits(): LinkPreview = copy(
        title = title.limitTo(300), description = description.limitTo(5000),
        price = price.limitTo(200), location = location.limitTo(200),
        ingredients = ingredients.map { it.limitTo(200) },
    )
}

/** Coupe à [max] unités sans casser un émoji (paire de substitution). */
fun String.limitTo(max: Int): String {
    if (length <= max) return this
    val cut = if (this[max - 1].isHighSurrogate()) max - 1 else max
    return take(cut).trimEnd()
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

    private suspend fun post(path: String, body: String, token: String? = null): String = withContext(Dispatchers.IO) {
        val conn = URL("$baseUrl$path").openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        if (!token.isNullOrBlank()) conn.setRequestProperty("Authorization", "Bearer $token")
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        conn.doOutput = true
        try {
            conn.outputStream.use { it.write(body.toByteArray()) }
            BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
        } catch (e: Exception) {
            val error = conn.errorStream?.let { s -> BufferedReader(InputStreamReader(s)).use { it.readText() } }
            if (error != null) throw Exception("API error (${conn.responseCode}): $error")
            throw e
        } finally {
            conn.disconnect()
        }
    }

    /** Jeton de session facultatif : il dit quels cadeaux de la liste sont les miens. */
    suspend fun getSharedFolder(token: String, bearer: String? = null): SharedFolderResponse {
        val body = get("/v1/share/$token", bearer)
        return json.decodeFromString(body)
    }

    /**
     * « Je l'offre » depuis un lien de partage. Avec une session, c'est réservé
     * ("reserved") ; sans, il faut prénom et e-mail et la réservation attend le
     * lien de confirmation ("pending").
     */
    suspend fun reserveSharedGift(shareToken: String, linkId: String, bearer: String?, name: String = "", email: String = ""): String {
        val payload = kotlinx.serialization.json.buildJsonObject {
            put("name", kotlinx.serialization.json.JsonPrimitive(name))
            put("email", kotlinx.serialization.json.JsonPrimitive(email))
        }
        val body = post("/v1/share/$shareToken/links/$linkId/reservation", payload.toString(), bearer)
        val status = (json.parseToJsonElement(body) as? kotlinx.serialization.json.JsonObject)?.get("status") as? kotlinx.serialization.json.JsonPrimitive
        return status?.content.orEmpty()
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
