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
data class ApiCollaborator(
    val userId: String = "",
    val email: String = "",
    val displayName: String = "",
    val role: String = "COLLABORATOR_ROLE_VIEWER"
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
    val ownerIsAdmin: Boolean = false,
    val linkCount: Int = 0,
    val bannerUrl: String = "",
    val tags: List<String> = emptyList(),
    val likeCount: Int = 0,
    val collaborators: List<ApiCollaborator> = emptyList(),
    val updatedAt: String = "",
    val description: String = "",
    val cover: String = "",
    val illustration: String = "",
    val keyDate: Long = 0,
    val keyDateType: String = "",
    val childIds: List<String> = emptyList(),
    val viewMode: String = "",
    val sort: String = "",
    val pinned: Boolean = false,
    val giftReservations: Boolean = false,
)

/** Membre d'une tribu (compte). `sharing` : son contenu est partagé avec la tribu. */
@Serializable
data class ApiTribeMember(
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val role: String = "TRIBE_ROLE_MEMBER",
    val sharing: Boolean = false,
    val addedAt: Long = 0
) {
    val isOwner: Boolean get() = role == "TRIBE_ROLE_OWNER"
}

/** Tribu : comptes d'une famille qui partagent idées, dossiers et enfants. */
@Serializable
data class ApiTribe(
    val id: String = "",
    val name: String = "",
    val members: List<ApiTribeMember> = emptyList(),
    /** Invitations envoyées par email, en attente de réponse. */
    val invitations: List<ApiTribeInvitation> = emptyList()
)

@Serializable
data class ApiTribeInvitation(
    val id: String = "",
    val email: String = "",
    val invitedByName: String = "",
    val createdAt: Long = 0,
    val expiresAt: Long = 0
)

@Serializable
private data class ApiTribeResponse(val tribe: ApiTribe = ApiTribe())

/** Invitation dans le cercle (dossiers choisis), en attente de réponse. */
@Serializable
data class ApiCircleInvitation(
    val id: String = "",
    val email: String = "",
    val role: String = "",
    val folderIds: List<String> = emptyList(),
    val folderNames: List<String> = emptyList(),
    /** Millisecondes (dernier envoi). */
    val createdAt: Long = 0,
    val expiresAt: Long = 0
) {
    val editor: Boolean get() = role == "COLLABORATOR_ROLE_EDITOR"
}

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
    val likedByMe: Boolean = false,
    val favorite: Boolean = false,
    val updatedAt: String = "",
    val createdAt: String = "",
    val ownerDisplayName: String = "",
    val ownerIsAdmin: Boolean = false,
    val visibility: String = "private",
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
)

@Serializable
data class DoneResult(val done: LinkDone = LinkDone(), val link: ApiLink = ApiLink())

@Serializable
data class DoneLinkResult(val link: ApiLink = ApiLink())

@Serializable
data class DoneList(val dones: List<LinkDone> = emptyList())

/** Application connectée (Claude) à laquelle le compte a donné accès. */
@Serializable
data class ApiConnectedApp(
    val id: String = "",
    val name: String = "",
    /** Site qui a reçu l'accès (claude.ai) */
    val site: String = "",
    val scopes: List<String> = emptyList(),
    val connectedAt: String = "",
    val lastUsedAt: String = "",
    val expiresAt: String = "",
    /** Idées ajoutées par cette application qui existent encore */
    val ideaCount: Int = 0
)

@Serializable
private data class ApiConnectedAppsResponse(val apps: List<ApiConnectedApp> = emptyList())

@Serializable
private data class ApiRevokeConnectedAppResponse(val deletedIdeas: Int = 0)

@Serializable
data class CreateFolderRequest(
    val name: String,
    val icon: String = "📁",
    val color: String = "ORANGE",
    val visibility: String = "PRIVATE",
    val bannerUrl: String = "",
    val tags: List<String> = emptyList(),
    val description: String = "",
    val cover: String = "",
    val illustration: String = "",
    val keyDate: Long = 0,
    val keyDateType: String = "",
    val childIds: List<String> = emptyList(),
    val viewMode: String = "",
    val sort: String = "",
    val pinned: Boolean = false,
)

/**
 * Personnalisation sans valeurs par défaut : toujours envoyée (le JSON n'écrit
 * pas les valeurs par défaut, et le serveur garde un champ absent).
 */
@Serializable
data class UpdateFolderRequest(
    val folderId: String,
    val name: String,
    val icon: String = "FOLDER",
    val color: String = "ORANGE",
    val visibility: String = "PRIVATE",
    val bannerUrl: String = "",
    val tags: List<String> = emptyList(),
    val description: String,
    val cover: String,
    val illustration: String,
    val keyDate: Long,
    val keyDateType: String,
    val childIds: List<String>,
    val setChildIds: Boolean,
    val viewMode: String,
    val sort: String,
    val pinned: Boolean,
    val giftReservations: Boolean,
)

@Serializable
data class ShareFolderResponse(
    val shareToken: String = "",
    val shareUrl: String = ""
)

@Serializable
data class AddCollaboratorResponse(
    val folder: ApiAuthFolder = ApiAuthFolder()
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
    val ingredients: List<String> = emptyList(),
    val visibility: String = "private"
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
    val ingredients: List<String> = emptyList(),
    val visibility: String = "private"
)

class AuthenticatedApiClient(
    private val baseUrl: String = Backend.url,
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

    /**
     * Envoie une photo de l'appareil (JPEG déjà réduit par ImagePickerHelper) ;
     * renvoie son adresse sur le serveur, à mettre comme image de l'idée.
     */
    suspend fun uploadPhoto(bytes: ByteArray): String = withContext(Dispatchers.IO) {
        val token = sessionManager.getToken() ?: throw Exception("Not authenticated")
        val conn = URL("$baseUrl/v1/photos").openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "image/jpeg")
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.connectTimeout = 10_000
        conn.readTimeout = 30_000
        conn.doOutput = true
        conn.setFixedLengthStreamingMode(bytes.size)
        try {
            conn.outputStream.use { it.write(bytes) }
            val response = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
            @Serializable
            data class PhotoResponse(val url: String)
            json.decodeFromString<PhotoResponse>(response).url
        } catch (e: Exception) {
            val error = conn.errorStream?.let { s -> BufferedReader(InputStreamReader(s)).use { it.readText() } }
            if (error != null) throw Exception("API error (${conn.responseCode}): $error")
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

    suspend fun updateFolder(folderId: String, req: UpdateFolderRequest): ApiAuthFolder {
        val body = json.encodeToString(UpdateFolderRequest.serializer(), req)
        return request("/v1/folders/$folderId", "PUT", body) { response ->
            @Serializable
            data class FolderResponse(val folder: ApiAuthFolder)
            json.decodeFromString<FolderResponse>(response).folder
        }
    }

    suspend fun shareFolder(folderId: String): ShareFolderResponse {
        return request("/v1/folders/$folderId/share", "POST", "{}") { response ->
            json.decodeFromString(response)
        }
    }

    suspend fun revokeShare(folderId: String) {
        request("/v1/folders/$folderId/share", "DELETE", null) { _ -> Unit }
    }

    // Collaborators
    suspend fun removeCollaborator(folderId: String, userId: String): AddCollaboratorResponse {
        return request("/v1/folders/$folderId/collaborators/$userId", "DELETE", null) { response ->
            json.decodeFromString(response)
        }
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

    // Link likes
    suspend fun listLikedLinks(): ApiLinkListResponse {
        return request("/v1/liked-links", "GET") { response -> json.decodeFromString(response) }
    }

    suspend fun likeLink(linkId: String): Int {
        return request("/v1/links/$linkId/like", "POST", "{}") { response ->
            @Serializable
            data class LikeResponse(val likeCount: Int = 0)
            json.decodeFromString<LikeResponse>(response).likeCount
        }
    }

    suspend fun unlikeLink(linkId: String): Int {
        return request("/v1/links/$linkId/like", "DELETE", null) { response ->
            @Serializable
            data class LikeResponse(val likeCount: Int = 0)
            json.decodeFromString<LikeResponse>(response).likeCount
        }
    }

    // Folder likes (dossiers de la communauté)
    suspend fun likeFolder(folderId: String): Int {
        return request("/v1/folders/$folderId/like", "POST", "{}") { response ->
            @Serializable
            data class LikeResponse(val likeCount: Int = 0)
            json.decodeFromString<LikeResponse>(response).likeCount
        }
    }

    suspend fun unlikeFolder(folderId: String): Int {
        return request("/v1/folders/$folderId/like", "DELETE", null) { response ->
            @Serializable
            data class LikeResponse(val likeCount: Int = 0)
            json.decodeFromString<LikeResponse>(response).likeCount
        }
    }


    // Droits RGPD sur le compte
    /** Toutes les données du compte, en JSON (export complet). */
    suspend fun exportAccount(): String = request("/v1/account/export", "GET") { it }

    // Modération : signaler un contenu, bloquer un compte
    /** Liste de cadeaux : reserve | bought | unbought | release ; renvoie le statut (pending/reserved/bought, vide une fois libéré). */
    suspend fun giftAction(linkId: String, action: String): String =
        request("/v1/links/$linkId/gift", "POST", json.encodeToString(kotlinx.serialization.json.JsonObject.serializer(),
            kotlinx.serialization.json.buildJsonObject { put("action", kotlinx.serialization.json.JsonPrimitive(action)) })) { body ->
            val gift = json.parseToJsonElement(body).let { it as? kotlinx.serialization.json.JsonObject }?.get("gift") as? kotlinx.serialization.json.JsonObject
            (gift?.get("status") as? kotlinx.serialization.json.JsonPrimitive)?.content.orEmpty()
        }

    // « C'est fait » : marquer une idée comme faite et garder un souvenir (data/Done.kt)
    private fun doneResult(body: String): DoneResult = json.decodeFromString(body)

    suspend fun markDone(linkId: String, input: DoneInput): DoneResult =
        request("/v1/links/$linkId/done", "POST", json.encodeToString(DoneInput.serializer(), input), ::doneResult)

    suspend fun updateDone(linkId: String, doneId: String, input: DoneInput): DoneResult =
        request("/v1/links/$linkId/done/$doneId", "PATCH", json.encodeToString(DoneInput.serializer(), input), ::doneResult)

    /** « Plus fait », ou « Annuler » du toast ; renvoie l'idée à jour. */
    suspend fun unmarkDone(linkId: String, doneId: String): ApiLink =
        request("/v1/links/$linkId/done/$doneId", "DELETE", null) { body -> json.decodeFromString<DoneLinkResult>(body).link }

    /** Souvenirs d'une idée, du plus récent au plus ancien. */
    suspend fun listDones(linkId: String): List<LinkDone> =
        request("/v1/links/$linkId/done", "GET") { body -> json.decodeFromString<DoneList>(body).dones }

    /** « Pas cette fois » : ne plus demander si la sortie passée a été faite. */
    suspend fun dismissDonePrompt(linkId: String): ApiLink =
        request("/v1/links/$linkId/done-prompt/dismiss", "POST", "{}") { body -> json.decodeFromString<DoneLinkResult>(body).link }

    suspend fun doneSummary(year: Int): DoneSummary =
        request("/v1/done/summary?year=$year", "GET") { body -> json.decodeFromString(body) }

    /** Remet à zéro les réservations d'une liste de cadeaux (sa tribu). */
    suspend fun resetGifts(folderId: String) {
        request("/v1/folders/$folderId/reservations", "DELETE", null) { _ -> Unit }
    }

    suspend fun report(targetType: String, targetId: String, reason: String, details: String) {
        request("/v1/reports", "POST", json.encodeToString(kotlinx.serialization.json.JsonObject.serializer(),
            kotlinx.serialization.json.buildJsonObject {
                put("targetType", kotlinx.serialization.json.JsonPrimitive(targetType))
                put("targetId", kotlinx.serialization.json.JsonPrimitive(targetId))
                put("reason", kotlinx.serialization.json.JsonPrimitive(reason))
                put("details", kotlinx.serialization.json.JsonPrimitive(details))
            })) { it }
    }

    suspend fun blockUser(userId: String) {
        request("/v1/blocks", "POST", json.encodeToString(kotlinx.serialization.json.JsonObject.serializer(),
            kotlinx.serialization.json.buildJsonObject { put("userId", kotlinx.serialization.json.JsonPrimitive(userId)) })) { it }
    }

    suspend fun unblockUser(userId: String) {
        request("/v1/blocks/$userId", "DELETE") { it }
    }

    suspend fun blockedUsers(): List<ApiBlockedUser> =
        request("/v1/blocks", "GET") { json.decodeFromString<ApiBlockedUsers>(it).users }

    /** Suppression définitive du compte ; le mot de passe est exigé. */
    suspend fun deleteAccount(password: String) {
        request("/v1/account/delete", "POST", json.encodeToString(kotlinx.serialization.json.JsonObject.serializer(),
            kotlinx.serialization.json.buildJsonObject { put("password", kotlinx.serialization.json.JsonPrimitive(password)) })) { it }
    }

    // Applications connectées (Claude, serveur MCP)
    suspend fun listConnectedApps(): List<ApiConnectedApp> =
        request("/v1/connected-apps", "GET") { json.decodeFromString<ApiConnectedAppsResponse>(it).apps }

    /** Retire l'accès ; deleteIdeas efface aussi les idées que l'application a ajoutées. Renvoie leur nombre. */
    suspend fun revokeConnectedApp(id: String, deleteIdeas: Boolean): Int =
        request("/v1/connected-apps/${java.net.URLEncoder.encode(id, "UTF-8")}" + (if (deleteIdeas) "?deleteIdeas=true" else ""), "DELETE") {
            json.decodeFromString<ApiRevokeConnectedAppResponse>(it).deletedIdeas
        }

    // Tribu
    suspend fun getTribe(): ApiTribe = request("/v1/tribe", "GET") { json.decodeFromString<ApiTribeResponse>(it).tribe }

    suspend fun renameTribe(name: String): ApiTribe =
        request("/v1/tribe", "PUT", json.encodeToString(kotlinx.serialization.json.JsonObject.serializer(),
            kotlinx.serialization.json.buildJsonObject { put("name", kotlinx.serialization.json.JsonPrimitive(name)) })) {
            json.decodeFromString<ApiTribeResponse>(it).tribe
        }

    /** Envoie une invitation par email (même réponse que l'adresse ait un compte ou non). */
    suspend fun inviteTribeMember(email: String): ApiTribe =
        request("/v1/tribe/members", "POST", json.encodeToString(kotlinx.serialization.json.JsonObject.serializer(),
            kotlinx.serialization.json.buildJsonObject { put("email", kotlinx.serialization.json.JsonPrimitive(email)) })) {
            json.decodeFromString<ApiTribeResponse>(it).tribe
        }

    suspend fun revokeTribeInvitation(id: String): ApiTribe =
        request("/v1/tribe/invitations/$id", "DELETE") { json.decodeFromString<ApiTribeResponse>(it).tribe }

    // Le cercle : proches invités par email à des dossiers choisis

    /** Même réponse que l'adresse ait un compte ou non ; une invitation en attente reçoit les dossiers en plus. */
    suspend fun inviteToCircle(email: String, folderIds: List<String>, role: String): ApiCircleInvitation {
        @Serializable data class InviteRequest(val email: String, val folderIds: List<String>, val role: String)
        @Serializable data class InviteResponse(val invitation: ApiCircleInvitation = ApiCircleInvitation())
        val body = json.encodeToString(InviteRequest.serializer(), InviteRequest(email, folderIds, role))
        return request("/v1/circle/invitations", "POST", body) { json.decodeFromString<InviteResponse>(it).invitation }
    }

    suspend fun listCircleInvitations(): List<ApiCircleInvitation> {
        @Serializable data class ListResponse(val invitations: List<ApiCircleInvitation> = emptyList())
        return request("/v1/circle/invitations", "GET") { json.decodeFromString<ListResponse>(it).invitations }
    }

    suspend fun revokeCircleInvitation(id: String) {
        request("/v1/circle/invitations/$id", "DELETE") { }
    }

    /** Accepte le lien reçu par email : le compte doit utiliser l'adresse invitée. */
    suspend fun acceptCircleInvitation(token: String): List<ApiAuthFolder> {
        @Serializable data class AcceptRequest(val token: String)
        @Serializable data class AcceptResponse(val folders: List<ApiAuthFolder> = emptyList())
        val body = json.encodeToString(AcceptRequest.serializer(), AcceptRequest(token))
        return request("/v1/circle/invitations/accept", "POST", body) { json.decodeFromString<AcceptResponse>(it).folders }
    }

    // Compte

    @Serializable
    data class ApiAccount(
        val userId: String = "",
        val email: String = "",
        val displayName: String = "",
        val isAdmin: Boolean = false,
        val emailVerified: Boolean = false
    )

    /** Compte connecté (adresse confirmée…). */
    suspend fun getAccount(): ApiAccount =
        request("/v1/auth/me", "GET") { json.decodeFromString<ApiAccount>(it) }

    /** Renvoie le lien de confirmation de l'adresse ; vrai si elle l'était déjà. */
    suspend fun resendEmailVerification(): Boolean {
        @Serializable data class ResendResponse(val emailVerified: Boolean = false)
        return request("/v1/auth/email/resend", "POST", "{}") { json.decodeFromString<ResendResponse>(it).emailVerified }
    }

    /** Accepte le lien d'invitation reçu par email : le compte doit utiliser l'adresse invitée. */
    suspend fun acceptTribeInvitation(token: String): ApiTribe =
        request("/v1/tribe/invitations/accept", "POST", json.encodeToString(kotlinx.serialization.json.JsonObject.serializer(),
            kotlinx.serialization.json.buildJsonObject { put("token", kotlinx.serialization.json.JsonPrimitive(token)) })) {
            json.decodeFromString<ApiTribeResponse>(it).tribe
        }

    /** Avec son propre ID : quitter la tribu. */
    suspend fun removeTribeMember(userId: String): ApiTribe =
        request("/v1/tribe/members/$userId", "DELETE") { json.decodeFromString<ApiTribeResponse>(it).tribe }

    suspend fun setTribeSharing(sharing: Boolean): ApiTribe =
        request("/v1/tribe/sharing", "PUT", """{"sharing":$sharing}""") { json.decodeFromString<ApiTribeResponse>(it).tribe }

    // Children
    suspend fun listChildren(): ApiChildrenListResponse {
        return request("/v1/children", "GET") { response ->
            json.decodeFromString(response)
        }
    }

    suspend fun createChild(name: String, birthDate: Long): ApiChild {
        @Serializable
        data class CreateChildRequest(val name: String, val birthDate: Long)
        val body = json.encodeToString(CreateChildRequest.serializer(), CreateChildRequest(name, birthDate))
        return request("/v1/children", "POST", body) { response ->
            @Serializable
            data class ChildResponse(val child: ApiChild)
            json.decodeFromString<ChildResponse>(response).child
        }
    }

    suspend fun updateChild(childId: String, name: String, birthDate: Long): ApiChild {
        @Serializable
        data class UpdateChildRequest(val childId: String, val name: String, val birthDate: Long, val updatedAt: Long = 0)
        val body = json.encodeToString(UpdateChildRequest.serializer(), UpdateChildRequest(childId, name, birthDate, System.currentTimeMillis()))
        return request("/v1/children/$childId", "PUT", body) { response ->
            @Serializable
            data class ChildResponse(val child: ApiChild)
            json.decodeFromString<ChildResponse>(response).child
        }
    }

    suspend fun deleteChild(childId: String) {
        request("/v1/children/$childId", "DELETE", null) { _ -> Unit }
    }
}

@Serializable
data class ApiChildrenListResponse(
    val children: List<ApiChild> = emptyList()
)

@Serializable
data class ApiChild(
    val id: String = "",
    val ownerId: String = "",
    val name: String = "",
    val birthDate: Long = 0,
    val createdAt: String = "",
    val updatedAt: String = ""
)

@Serializable
data class ApiBlockedUser(val userId: String = "", val displayName: String = "")

@Serializable
data class ApiBlockedUsers(val users: List<ApiBlockedUser> = emptyList())

