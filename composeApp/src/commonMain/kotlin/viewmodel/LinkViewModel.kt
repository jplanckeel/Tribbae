package viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import data.AiRepository
import data.AiSuggestedLink
import data.Collaborator
import data.Folder
import data.FolderColor
import data.FolderIcon
import data.Link
import data.LinkCategory
import data.LinkRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.async

class LinkViewModel(val repository: LinkRepository = LinkRepository()) : ViewModel() {
    val folders: StateFlow<List<Folder>> = repository.folders
    val tags: StateFlow<List<String>> = repository.tags
    val children: StateFlow<List<data.Child>> = repository.children

    /** Client API public (communauté) */
    val apiClient = data.ApiClient()

    /** Token JWT — injecté depuis MainActivity */
    var authToken: String? = null
    
    /** SessionManager pour accéder au displayName */
    private var sessionManager: data.SessionManager? = null

    /**
     * Adresse email du compte confirmée. Tant qu'elle ne l'est pas, le serveur
     * refuse de publier (idée ou dossier public) et d'écrire à d'autres
     * personnes (inviter dans la tribu, ajouter un collaborateur) : les écrans
     * l'expliquent au lieu de laisser la synchronisation échouer. Vrai sans
     * compte (rien à bloquer).
     */
    private val _emailVerified = MutableStateFlow(true)
    val emailVerified: StateFlow<Boolean> = _emailVerified.asStateFlow()
    private var emailVerifiedWatch: kotlinx.coroutines.Job? = null
    
    /** Client API authentifié */
    private var authenticatedClient: data.AuthenticatedApiClient? = null
    
    /** Follow repository */
    private var followRepository: data.FollowRepository? = null
    
    /** Comment repository */
    
    private val _syncStatus = MutableStateFlow<String?>(null)
    val syncStatus: StateFlow<String?> = _syncStatus.asStateFlow()
    
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _tribe = MutableStateFlow<data.ApiTribe?>(null)
    /** Tribu de l'utilisateur (nom affiché, membres) ; null hors connexion. */
    val tribe: StateFlow<data.ApiTribe?> = _tribe.asStateFlow()

    private val _circleInvitations = MutableStateFlow<List<data.ApiCircleInvitation>>(emptyList())
    /** Invitations dans le cercle envoyées par l'utilisateur, en attente (« Ma tribu » › Le cercle). */
    val circleInvitations: StateFlow<List<data.ApiCircleInvitation>> = _circleInvitations.asStateFlow()

    private val _communityFolders = MutableStateFlow<List<Folder>>(emptyList())
    val communityFolders: StateFlow<List<Folder>> = _communityFolders.asStateFlow()

    private val _communityLinks = MutableStateFlow<List<Link>>(emptyList())
    val communityLinks: StateFlow<List<Link>> = _communityLinks.asStateFlow()
    
    
    private val _followingCount = MutableStateFlow(0)
    val followingCount: StateFlow<Int> = _followingCount.asStateFlow()
    
    // Comment state
    

    /** Fonction pour récupérer l'image OG — injectée côté Android */
    var ogImageFetcher: (suspend (String) -> String?)? = null
    /** Aperçu (titre, images) lu sur le téléphone, quand le backend ne répond pas. */
    var localPreview: (suspend (String) -> data.LinkPreview?)? = null

    /** Fonction pour planifier un rappel — injectée côté Android */
    var reminderScheduler: ((Link) -> Unit)? = null

    /** Fonction pour annuler un rappel — injectée côté Android */
    var reminderCanceller: ((String) -> Unit)? = null

    /** Fonction pour ouvrir une URL dans le navigateur — injectée côté Android */
    var urlOpener: ((String) -> Unit)? = null

    /** Lance le picker d'image (galerie ou caméra) — injectée côté Android */
    var imagePickerGallery: ((onResult: (String) -> Unit) -> Unit)? = null
    var imagePickerCamera: ((onResult: (String) -> Unit) -> Unit)? = null

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow<LinkCategory?>(null)
    val selectedCategory: StateFlow<LinkCategory?> = _selectedCategory.asStateFlow()

    private val _selectedFolderId = MutableStateFlow<String?>(null)
    val selectedFolderId: StateFlow<String?> = _selectedFolderId.asStateFlow()

    private val _selectedChildId = MutableStateFlow<String?>(null)
    val selectedChildId: StateFlow<String?> = _selectedChildId.asStateFlow()

    private val _favoritesOnly = MutableStateFlow(false)
    val favoritesOnly: StateFlow<Boolean> = _favoritesOnly.asStateFlow()

    private val _filteredLinks = MutableStateFlow<List<Link>>(emptyList())
    val filteredLinks: StateFlow<List<Link>> = _filteredLinks.asStateFlow()

    init { updateFilteredLinks() }

    fun addLink(
        title: String, url: String, description: String,
        category: LinkCategory, folderId: String?,
        tags: List<String>, ageRange: String, location: String, price: String,
        eventDate: Long? = null, reminderEnabled: Boolean = false, rating: Int = 0,
        imageUrl: String = "", ingredients: List<String> = emptyList(),
        visibility: String = "private"
    ): Link {
        val ownerDisplayName = sessionManager?.getDisplayName() ?: ""
        val link = Link(
            id = kotlin.random.Random.nextLong().toString(),
            title = title, url = url, description = description,
            category = category, folderId = folderId,
            tags = tags, ageRange = ageRange, location = location, price = price,
            eventDate = eventDate, reminderEnabled = reminderEnabled, rating = rating,
            imageUrl = imageUrl, ingredients = ingredients,
            ownerDisplayName = ownerDisplayName,
            visibility = visibility,
            updatedAt = ""
        )
        repository.addLink(link)
        updateFilteredLinks()
        
        schedulePush()

        // Planifie le rappel si activé
        if (reminderEnabled && eventDate != null) {
            reminderScheduler?.invoke(link)
        }

        // Récupère l'image de la page en arrière-plan seulement si pas d'image manuelle
        if (url.isNotBlank() && imageUrl.isBlank()) {
            viewModelScope.launch {
                val ogUrl = resolveImage(url)
                if (!ogUrl.isNullOrBlank()) {
                    repository.updateLink(link.copy(imageUrl = ogUrl))
                    updateFilteredLinks()
                }
            }
        }
        return link
    }

    /**
     * Meilleure image d'une page : le scraper du backend (plus complet : JSON-LD,
     * lazy-loading, oEmbed, contournement des pages anti-bot) puis, hors ligne
     * ou sans session, l'extraction locale.
     */
    private suspend fun resolveImage(url: String): String? =
        previewLink(url)?.image?.takeIf { it.isNotBlank() } ?: ogImageFetcher?.invoke(url)

    /**
     * Le contenu de ownerId appartient à l'utilisateur ou à un membre de sa tribu
     * qui a confirmé le partage : il le gère comme le sien.
     */
    fun isTribePeer(ownerId: String): Boolean {
        val me = currentUserId()
        if (ownerId.isBlank() || ownerId == me) return true
        return _tribe.value?.members?.any { it.userId == ownerId && it.sharing } == true
    }

    /**
     * Action sur la tribu : met à jour la tribu puis resynchronise (idées,
     * dossiers et enfants partagés changent avec elle). onDone(null) en cas de
     * succès, sinon un message d'erreur en français.
     */
    private fun tribeAction(onDone: (String?) -> Unit, call: suspend (data.AuthenticatedApiClient) -> data.ApiTribe) {
        val client = authenticatedClient ?: return onDone("Connectez-vous pour gérer votre tribu.")
        viewModelScope.launch {
            runCatching { call(client) }
                .onSuccess { t ->
                    _tribe.value = t
                    sessionManager?.let { syncWithBackend(it) }
                    onDone(null)
                }
                .onFailure { onDone(tribeErrorMessage(it.message.orEmpty())) }
        }
    }

    fun renameTribe(name: String, onDone: (String?) -> Unit) = tribeAction(onDone) { it.renameTribe(name.trim()) }
    fun inviteTribeMember(email: String, onDone: (String?) -> Unit) = tribeAction(onDone) { it.inviteTribeMember(email.trim()) }
    fun revokeTribeInvitation(id: String, onDone: (String?) -> Unit) = tribeAction(onDone) { it.revokeTribeInvitation(id) }
    fun acceptTribeInvitation(token: String, onDone: (String?) -> Unit) = tribeAction(onDone) { client ->
        client.acceptTribeInvitation(token).also {
            // Le lien est arrivé dans la boîte de l'adresse du compte : elle est confirmée
            sessionManager?.setEmailVerified(true)
        }
    }
    fun removeTribeMember(userId: String, onDone: (String?) -> Unit) = tribeAction(onDone) { it.removeTribeMember(userId) }
    fun setTribeSharing(sharing: Boolean, onDone: (String?) -> Unit) = tribeAction(onDone) { it.setTribeSharing(sharing) }

    // ── Le cercle : invitations par email à des dossiers choisis ──

    /** Invite un proche (compte ou non) ; onDone(null) une fois l'email envoyé (ou l'invitation mise à jour). */
    fun inviteToCircle(email: String, folderIds: List<String>, editor: Boolean, onDone: (String?) -> Unit) {
        val client = authenticatedClient ?: return onDone("Connectez-vous pour inviter un proche.")
        if (!_emailVerified.value) return onDone(data.EmailVerification.VERIFY_FIRST)
        val role = if (editor) "COLLABORATOR_ROLE_EDITOR" else "COLLABORATOR_ROLE_VIEWER"
        viewModelScope.launch {
            runCatching { client.inviteToCircle(email.trim(), folderIds, role) }
                .onSuccess { inv ->
                    _circleInvitations.value = _circleInvitations.value.filter { it.id != inv.id } + inv
                    onDone(null)
                }
                .onFailure { onDone(tribeErrorMessage(it.message.orEmpty())) }
        }
    }

    /** « Annuler » : le lien envoyé ne marche plus. */
    fun revokeCircleInvitation(id: String, onDone: (String?) -> Unit) {
        val client = authenticatedClient ?: return onDone("Connectez-vous d'abord.")
        viewModelScope.launch {
            runCatching { client.revokeCircleInvitation(id) }
                .onSuccess {
                    _circleInvitations.value = _circleInvitations.value.filter { it.id != id }
                    onDone(null)
                }
                .onFailure {
                    // Déjà acceptée ou expirée entre-temps : elle n'est plus en attente
                    if ("404" in it.message.orEmpty()) _circleInvitations.value = _circleInvitations.value.filter { inv -> inv.id != id }
                    onDone(tribeErrorMessage(it.message.orEmpty()))
                }
        }
    }

    /** Accepte le lien reçu par email ; onDone(erreur, premier dossier ouvert). */
    fun acceptCircleInvitation(token: String, onDone: (String?, Folder?) -> Unit) {
        val client = authenticatedClient ?: return onDone("Connectez-vous pour voir les dossiers.", null)
        viewModelScope.launch {
            runCatching { client.acceptCircleInvitation(token) }
                .onSuccess { folders ->
                    // Le lien est arrivé dans la boîte de l'adresse du compte : elle est confirmée
                    sessionManager?.setEmailVerified(true)
                    val opened = folders.map(::apiFolderToFolder)
                    opened.forEach { f -> if (repository.folders.value.any { it.id == f.id }) repository.updateFolder(f) else repository.addFolder(f) }
                    sessionManager?.let { syncWithBackend(it) }
                    onDone(null, opened.firstOrNull())
                }
                .onFailure { onDone(tribeErrorMessage(it.message.orEmpty()), null) }
        }
    }

    private fun tribeErrorMessage(raw: String): String = when {
        "invalid or expired invitation" in raw -> "Cette invitation n'est plus valable : elle a expiré, a été annulée ou a déjà servi."
        "invitation is for another email" in raw -> "Cette invitation a été envoyée à une autre adresse que celle de votre compte."
        "a valid email is required" in raw -> "Adresse email invalide."
        "already a member of this tribe" in raw -> "Cette personne fait déjà partie de votre tribu."
        "already belongs to another tribe" in raw -> "Ce compte fait déjà partie d'une autre tribu : la personne doit d'abord la quitter."
        "tribe is full" in raw -> "La tribu est complète (12 membres et invitations en attente au plus)."
        "only the tribe owner" in raw -> "Seul le créateur de la tribu peut retirer un membre."
        "name must be 1 to 60" in raw -> "Le nom de la tribu doit faire entre 1 et 60 caractères."
        "email is required" in raw -> "Saisissez l'adresse email de votre proche."
        "too many pending invitations" in raw -> "Trop d'invitations en attente (30 au plus) : annulez-en ou attendez qu'elles soient acceptées."
        "choose the folders to open" in raw -> "Choisissez au moins un dossier à ouvrir."
        "cannot invite yourself" in raw -> "C'est votre propre adresse."
        "API error (404)" in raw -> "Cette invitation n'est plus valable."
        data.EmailVerification.NOT_VERIFIED in raw -> data.EmailVerification.VERIFY_FIRST
        else -> "Impossible de joindre le serveur, réessayez."
    }

    /**
     * Relit l'état du compte tant que l'adresse n'est pas confirmée (le lien a
     * pu être ouvert sur un autre appareil). Une fois confirmée, la
     * synchronisation envoie ce qui attendait (idées à publier).
     */
    fun refreshAccount(sessionManager: data.SessionManager) {
        if (sessionManager.emailVerified.value) return
        val client = authenticatedClient ?: return
        viewModelScope.launch {
            val account = runCatching { client.getAccount() }.getOrNull() ?: return@launch
            if (account.emailVerified) markEmailVerified()
        }
    }

    fun refreshAccount() {
        sessionManager?.let { refreshAccount(it) }
    }

    /** Adresse confirmée (lien ouvert dans l'app, ailleurs, invitation…) : on envoie ce qui attendait. */
    fun markEmailVerified() {
        val sm = sessionManager ?: return
        if (sm.emailVerified.value) return
        sm.setEmailVerified(true)
        syncWithBackend(sm)
    }

    /** « Renvoyer l'email » de confirmation ; onDone(null) si envoyé (ou déjà confirmée). */
    fun resendEmailVerification(onDone: (String?) -> Unit) {
        val client = authenticatedClient ?: return onDone("Connectez-vous d'abord.")
        viewModelScope.launch {
            runCatching { client.resendEmailVerification() }
                .onSuccess { alreadyVerified ->
                    if (alreadyVerified) markEmailVerified()
                    onDone(null)
                }
                .onFailure { onDone(if ("429" in it.message.orEmpty()) "Trop de tentatives, réessayez dans une minute." else "Impossible de joindre le serveur, réessayez.") }
        }
    }

    /**
     * Déconnexion : la session ET les données du compte gardées sur le téléphone
     * (idées, dossiers, enfants) sont effacées, pour qu'un autre utilisateur de
     * l'appareil ne les voie pas.
     */
    fun signOut() {
        repository.clearAll()
        _tribe.value = null
        _circleInvitations.value = emptyList()
        sessionManager?.clearSession()
    }

    /** Export RGPD de toutes les données du compte (JSON), ou null en cas d'échec. */
    suspend fun exportAccount(): String? = authenticatedClient?.let { runCatching { it.exportAccount() }.getOrNull() }

    /** Suppression définitive du compte, puis déconnexion. onDone(null) si réussi, sinon le message d'erreur. */
    fun deleteAccount(password: String, onDone: (String?) -> Unit) {
        val client = authenticatedClient ?: return onDone("Connectez-vous pour supprimer votre compte.")
        viewModelScope.launch {
            runCatching { client.deleteAccount(password) }
                .onSuccess { signOut(); onDone(null) }
                .onFailure { e ->
                    val raw = e.message.orEmpty()
                    onDone(when {
                        "wrong password" in raw -> "Mot de passe incorrect."
                        "(429)" in raw -> "Trop de tentatives, réessayez dans une minute."
                        "deletion failed" in raw -> "La suppression n'a pas abouti. Réessayez : elle reprendra là où elle s'est arrêtée."
                        else -> "Impossible de joindre le serveur, réessayez."
                    })
                }
        }
    }

    /** Identifiant de l'utilisateur connecté, ou null. */
    fun currentUserId(): String? = sessionManager?.getUserId()?.takeIf { it.isNotBlank() }

    /** Titre, description et image d'une page web, via le backend. Null si hors ligne ou non connecté. */
    suspend fun previewLink(url: String): data.LinkPreview? {
        val normalized = if ("://" in url) url.trim() else "https://${url.trim()}"
        val token = sessionManager?.getToken() ?: authToken
        val remote = token?.let { runCatching { apiClient.previewLink(normalized, it) }.getOrNull() }
        if (remote != null && (remote.title.isNotBlank() || remote.image.isNotBlank())) return remote.withinLimits()
        // Sans compte, hors ligne ou serveur en échec : lecture de la page sur le téléphone
        return (localPreview?.invoke(normalized) ?: remote)?.withinLimits()
    }

    fun deleteLink(id: String) {
        reminderCanceller?.invoke(id)
        repository.deleteLink(id)
        updateFilteredLinks()
        
        if (!data.SyncIds.isLocal(id)) repository.updatePending { it.copy(linkDeletes = it.linkDeletes + id, linkUpdates = it.linkUpdates - id) }
        schedulePush()
    }

    fun updateLink(link: Link) {
        val updatedLink = link.copy(updatedAt = "")
        repository.updateLink(updatedLink)
        updateFilteredLinks()
        // Met à jour le rappel si nécessaire
        if (updatedLink.reminderEnabled && updatedLink.eventDate != null) {
            reminderScheduler?.invoke(updatedLink)
        } else {
            reminderCanceller?.invoke(updatedLink.id)
        }

        markLinkChanged(updatedLink.id)
        schedulePush()
    }

    fun addFolder(name: String, icon: FolderIcon, color: FolderColor, visibility: String = "PRIVATE") =
        addFolder(Folder(id = "", name = name, icon = icon, color = color, visibility = visibility))

    /** Nouveau dossier (id local remplacé par celui du serveur après synchro). */
    fun addFolder(draft: Folder) {
        val folder = draft.copy(id = kotlin.random.Random.nextLong().toString())
        repository.addFolder(folder)
        
        schedulePush()
    }

    fun deleteFolder(id: String, onSuccess: () -> Unit = {}) {
        val client = authenticatedClient
        if (client == null || data.SyncIds.isLocal(id)) {
            // Hors connexion, ou dossier pas encore reçu par le serveur : suppression locale uniquement
            repository.deleteFolder(id)
            updateFilteredLinks()
            onSuccess()
            return
        }

        // Appel backend d'abord, suppression locale uniquement en cas de succès
        viewModelScope.launch {
            try {
                client.deleteFolder(id)
                repository.deleteFolder(id)
                updateFilteredLinks()
                println("DEBUG: Folder $id deleted successfully")
                onSuccess()
            } catch (e: Exception) {
                println("ERROR: Failed to delete folder $id - ${e.message}")
                _syncStatus.value = "Erreur suppression dossier: ${e.message}"
            }
        }
    }

    fun updateFolder(folder: Folder) {
        val updatedFolder = folder.copy(updatedAt = "")
        repository.updateFolder(updatedFolder)
        updateFilteredLinks()
        
        if (!data.SyncIds.isLocal(updatedFolder.id)) repository.updatePending { it.copy(folderUpdates = it.folderUpdates + updatedFolder.id) }
        schedulePush()
    }

    fun addTag(tag: String) { repository.addTag(tag) }
    fun deleteTag(tag: String) { repository.deleteTag(tag) }

    /** Toggle le favori d'un lien (étoile personnelle), synchronisé avec le backend */
    fun toggleFavorite(linkId: String) {
        val link = repository.links.value.find { it.id == linkId } ?: return
        val newFavorite = !link.favorite
        repository.updateLink(link.copy(favorite = newFavorite))
        updateFilteredLinks()
        
        // Favori personnel, synchronisé via /favorite (le like est un geste communautaire distinct)
        val client = authenticatedClient ?: return
        viewModelScope.launch {
            try {
                val serverFavorite = client.toggleFavorite(linkId)
                if (serverFavorite != newFavorite) {
                    repository.links.value.find { it.id == linkId }?.let { repository.updateLink(it.copy(favorite = serverFavorite)) }
                    updateFilteredLinks()
                }
            } catch (e: Exception) {
                // Rollback en cas d'erreur
                repository.updateLink(link.copy(favorite = !newFavorite))
                updateFilteredLinks()
            }
        }
    }

    /** Like/unlike direct sur l'API — pour les liens communautaires non présents dans le repository local */
    fun toggleLike(linkId: String, currentlyLiked: Boolean, onResult: (liked: Boolean, likeCount: Int) -> Unit) {
        val client = authenticatedClient ?: return
        viewModelScope.launch {
            try {
                val newCount = if (currentlyLiked) {
                    client.unlikeLink(linkId)
                } else {
                    client.likeLink(linkId)
                }
                onResult(!currentlyLiked, newCount)
            } catch (e: Exception) {
                println("ERROR: Failed to toggle like for link $linkId - ${e.message}")
            }
        }
    }

    fun addChild(name: String, birthDate: Long) {
        val child = data.Child(
            id = kotlin.random.Random.nextLong().toString(),
            name = name, birthDate = birthDate,
            updatedAt = ""
        )
        repository.addChild(child)
        
        schedulePush()
    }

    fun updateChild(child: data.Child) {
        val updatedChild = child.copy(updatedAt = "")
        repository.updateChild(updatedChild)
        
        if (!data.SyncIds.isLocal(updatedChild.id)) repository.updatePending { it.copy(childUpdates = it.childUpdates + updatedChild.id) }
        schedulePush()
    }
    
    fun deleteChild(id: String) {
        if (_selectedChildId.value == id) _selectedChildId.value = null
        repository.deleteChild(id)
        updateFilteredLinks()
        
        if (!data.SyncIds.isLocal(id)) repository.updatePending { it.copy(childDeletes = it.childDeletes + id, childUpdates = it.childUpdates - id) }
        schedulePush()
    }

    // ── IA ────────────────────────────────────────────────────────────────────

    private val aiRepository = AiRepository()

    /**
     * Demande des idées à l'IA pour la phrase composée. count : combien (0 :
     * selon la demande) ; exclude : titres déjà proposés, à ne pas répéter.
     * Renvoie les idées, ou un message d'erreur en français.
     */
    suspend fun requestAiIdeas(prompt: String, count: Int = 0, exclude: List<String> = emptyList()): Result<List<AiSuggestedLink>> {
        val token = authToken ?: return Result.failure(Exception("Connectez-vous pour demander des idées à l'IA."))
        return runCatching { aiRepository.generateIdeas(prompt, token, count, exclude).ideas }
            .recoverCatching { e -> if (e is kotlinx.coroutines.CancellationException) throw e else throw Exception(aiErrorMessage(e.message.orEmpty())) }
    }

    private fun aiErrorMessage(m: String): String = when {
        m.contains("timeout") || m.contains("timed out") -> "La génération prend trop de temps, réessayez dans un instant."
        m.contains("Connection refused") || m.contains("Unable to resolve host") -> "Impossible de joindre Tribbae. Vérifiez votre connexion internet."
        m.contains("401") || m.contains("403") -> "Session expirée. Reconnectez-vous dans « Ma tribu »."
        m.contains("AI is busy", ignoreCase = true) -> "L'IA prépare déjà des idées pour une autre tribu. Réessayez dans une trentaine de secondes."
        m.contains("(429)") -> "Trop de demandes, réessayez dans une minute."
        m.contains("idea generation failed") -> "L'IA n'a pas réussi à préparer d'idées. Réessayez ou changez un mot de la phrase."
        else -> m.ifEmpty { "Erreur inconnue" }
    }

    fun saveAiIdeas(ideas: List<AiSuggestedLink>, folderId: String?) {
        ideas.forEach { idea ->
            val category = try {
                LinkCategory.valueOf(idea.category.removePrefix("LINK_CATEGORY_"))
            } catch (_: Exception) { LinkCategory.IDEE }
            addLink(
                title = idea.title,
                url = "",
                description = idea.description,
                category = category,
                folderId = folderId,
                tags = idea.tags,
                ageRange = idea.ageRange,
                location = "",
                price = ""
            )
        }
    }

    /**
     * Range les idées de l'IA dans un nouveau dossier. Le dossier est d'abord créé
     * sur le serveur (quand on est connecté) pour que les idées y pointent avec
     * son identifiant définitif, et non un identifiant local provisoire.
     */
    fun saveAiIdeasInNewFolder(ideas: List<AiSuggestedLink>, name: String, onDone: (String) -> Unit = {}) {
        viewModelScope.launch {
            val folder = createPrivateFolder(Folder(
                id = kotlin.random.Random.nextLong().toString(),
                name = name.take(80).ifBlank { "Idées de l'IA" },
                icon = FolderIcon.MAGIC, color = FolderColor.PURPLE, visibility = "PRIVATE", aiGenerated = true
            ))
            saveAiIdeas(ideas, folder.id)
            onDone(folder.id)
        }
    }

    /**
     * Crée un dossier privé à la volée (depuis le choix du dossier d'une idée)
     * et renvoie son identifiant : celui du serveur si on est en ligne, pour que
     * l'idée enregistrée juste après y soit bien rattachée.
     */
    fun createFolderQuick(name: String, onDone: (String) -> Unit) {
        val trimmed = name.trim().take(80)
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val folder = createPrivateFolder(Folder(id = kotlin.random.Random.nextLong().toString(), name = trimmed, visibility = "PRIVATE"))
            onDone(folder.id)
        }
    }

    private suspend fun createPrivateFolder(local: Folder): Folder {
        val folder = authenticatedClient?.let { client ->
            runCatching {
                client.createFolder(
                    data.CreateFolderRequest(
                        name = local.name, icon = local.icon.name, color = local.color.name,
                        visibility = "VISIBILITY_PRIVATE", bannerUrl = "", tags = emptyList()
                    )
                )
            }.getOrNull()?.let { local.copy(id = it.id) }
        } ?: local
        repository.addFolder(folder)
        return folder
    }

    private var imageBackfillJob: kotlinx.coroutines.Job? = null

    /**
     * Rattrape l'image des idées qui n'en ont pas : au démarrage et après chaque
     * synchronisation. Une même idée n'est retentée qu'une fois par jour (les
     * sites qui bloquent ne doivent pas être sollicités à chaque ouverture) ; le
     * serveur fait aussi ce rattrapage de son côté.
     */
    fun fetchMissingImages() {
        if (imageBackfillJob?.isActive == true) return
        imageBackfillJob = viewModelScope.launch {
            val now = System.currentTimeMillis()
            repository.links.value
                .filter { it.imageUrl.isBlank() && it.url.isNotBlank() && now - repository.imageCheckedAt(it.id) > IMAGE_RETRY_MS }
                .take(IMAGE_BACKFILL_BATCH)
                .forEach { link ->
                    findAndSaveImage(link)
                    kotlinx.coroutines.delay(500)
                }
        }
    }

    /**
     * Fiche d'une idée sans image : on tente tout de suite (au plus une fois par
     * heure pour une même idée) plutôt que d'attendre le prochain rattrapage.
     */
    fun ensureImage(linkId: String) {
        val link = repository.links.value.find { it.id == linkId } ?: return
        if (link.imageUrl.isNotBlank() || link.url.isBlank()) return
        if (System.currentTimeMillis() - repository.imageCheckedAt(link.id) < IMAGE_RETRY_ON_OPEN_MS) return
        viewModelScope.launch { findAndSaveImage(link) }
    }

    /**
     * Cherche l'image de la page et l'enregistre. Pour ses propres idées, elle
     * part aussi au serveur : sans cela, la synchronisation suivante l'effacerait
     * (le serveur fait foi) et la recherche recommencerait à chaque ouverture.
     */
    private suspend fun findAndSaveImage(link: Link): String? {
        repository.markImageChecked(link.id)
        val image = resolveImage(link.url)?.takeIf { it.isNotBlank() } ?: return null
        val fresh = repository.links.value.find { it.id == link.id } ?: return null
        if (fresh.imageUrl.isNotBlank()) return fresh.imageUrl // choisie entre-temps
        val mine = fresh.ownerId.isBlank() || fresh.ownerId == currentUserId()
        if (mine) {
            updateLink(fresh.copy(imageUrl = image))
        } else {
            repository.updateLink(fresh.copy(imageUrl = image))
            updateFilteredLinks()
        }
        return image
    }

    private fun updateFilteredLinks() {
        val childAgeMonths = _selectedChildId.value?.let { id ->
            repository.children.value.find { it.id == id }?.let { ui.calculateAgeInMonths(it.birthDate) }
        }
        var results = repository.searchLinks(
            query = _searchQuery.value,
            category = _selectedCategory.value,
            folderId = _selectedFolderId.value,
            maxAgeMonths = childAgeMonths
        )
        if (_favoritesOnly.value) {
            results = results.filter { it.favorite }
        }
        _filteredLinks.value = results
    }
    
    // ── Follow Management ─────────────────────────────────────────────────────
    
    /**
     * Load the following count for the current user
     */
    private fun loadFollowingCount() {
        val repo = followRepository ?: return
        val userId = sessionManager?.getUserId() ?: return
        viewModelScope.launch {
            try {
                val count = repo.getFollowingCount(userId).getOrThrow()
                _followingCount.value = count
            } catch (e: Exception) {
                println("ERROR: Failed to load following count: ${e.message}")
            }
        }
    }
    
    /**
     * Get the following count for the current user
     * @return The number of users being followed
     */
    suspend fun getFollowingCount(): Int {
        val repo = followRepository ?: return 0
        val userId = sessionManager?.getUserId() ?: return 0
        return try {
            repo.getFollowingCount(userId).getOrThrow()
        } catch (e: Exception) {
            println("ERROR: Failed to get following count: ${e.message}")
            0
        }
    }
    
    // ── Comment Management ────────────────────────────────────────────────────
    
    // ── Visibility Management ─────────────────────────────────────────────────
    
    /**
     * Update link visibility
     * @param linkId The ID of the link
     * @param visibility The new visibility value ("private" or "public")
     */
    fun updateLinkVisibility(linkId: String, visibility: String) {
        val link = repository.links.value.find { it.id == linkId } ?: return
        val updatedLink = link.copy(visibility = visibility)
        updateLink(updatedLink)
    }
    
    // ── Backend Sync ──────────────────────────────────────────────────────────

    private fun bearer(): String? = sessionManager?.getToken() ?: authToken

    /** Dossiers publics de la communauté (onglet « Dossiers » de l'Explorer). */
    fun loadCommunityFolders(search: String = "") {
        viewModelScope.launch {
            runCatching { apiClient.listCommunityFolders(search = search, pageSize = 50, token = bearer()) }
                .onSuccess { r -> _communityFolders.value = r.folders.map { it.toFolder() } }
                .onFailure { println("ERROR: Failed to load community folders - ${it.message}") }
        }
    }

    /** Idées visibles d'un dossier public (seulement celles que le serveur autorise). */
    suspend fun loadPublicFolderLinks(folderId: String): List<Link>? =
        runCatching { apiClient.listFolderLinks(folderId, bearer()).links.map { it.toLink() } }.getOrNull()

    /** Dossier ouvert depuis un lien de partage (https://…/share/<jeton>). */
    suspend fun loadSharedFolder(token: String): Pair<Folder, List<Link>>? = runCatching {
        val r = apiClient.getSharedFolder(token)
        val folder = r.folder.copy(linkCount = r.links.size).toFolder()
        folder to r.links.map { it.toLink().copy(folderId = folder.id) }
    }.getOrNull()

    /** Like / unlike d'un dossier de la communauté. */
    fun toggleFolderLike(folderId: String, currentlyLiked: Boolean, onResult: (liked: Boolean, likeCount: Int) -> Unit) {
        val client = authenticatedClient ?: return
        viewModelScope.launch {
            runCatching { if (currentlyLiked) client.unlikeFolder(folderId) else client.likeFolder(folderId) }
                .onSuccess { count ->
                    onResult(!currentlyLiked, count)
                    _communityFolders.value = _communityFolders.value.map {
                        if (it.id == folderId) it.copy(likedByMe = !currentlyLiked, likeCount = count) else it
                    }
                }
        }
    }

    private var communityLoadedAt = 0L
    private var communityLoading = false

    /** Recharge les idées de la communauté si elles ont plus de 5 minutes (ou jamais chargées). */
    fun refreshCommunityIfStale(maxAgeMs: Long = 5 * 60_000L) {
        if (!communityLoading && System.currentTimeMillis() - communityLoadedAt > maxAgeMs) loadCommunityLinks()
    }

    /** Charge les liens publics de la communauté depuis l'API publique (sans auth) */
    fun loadCommunityLinks() {
        communityLoading = true
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                // Comme le web : jusqu'à 100 idées par catégorie (l'API plafonne à 100 par
                // appel), avec le jeton pour savoir lesquelles on a déjà aimées
                val token = sessionManager?.getToken() ?: authToken
                val lists = coroutineScope {
                    LinkCategory.entries.map { c ->
                        async { runCatching { apiClient.listCommunityLinks("LINK_CATEGORY_${c.name}", 100, token).links }.getOrDefault(emptyList()) }
                    }.awaitAll()
                }
                _communityLinks.value = lists.flatten().distinctBy { it.id }.map { it.toLink().copy(visibility = "public") }
                communityLoadedAt = System.currentTimeMillis()
                println("DEBUG: Loaded ${_communityLinks.value.size} community links")
            } catch (e: Exception) {
                println("ERROR: Failed to load community links - ${e.message}")
            } finally {
                _isRefreshing.value = false
                communityLoading = false
            }
        }
    }

    fun initAuthenticatedClient(sessionManager: data.SessionManager) {
        this.sessionManager = sessionManager
        emailVerifiedWatch?.cancel()
        emailVerifiedWatch = viewModelScope.launch {
            // Sans session (déconnexion), rien n'est bloqué
            sessionManager.emailVerified.combine(sessionManager.isLoggedIn) { verified, loggedIn -> verified || !loggedIn }
                .collect { _emailVerified.value = it }
        }
        authenticatedClient = data.AuthenticatedApiClient(sessionManager = sessionManager)
        followRepository = data.FollowRepository(sessionManager = sessionManager)
        authToken = sessionManager.getToken()
        
        // Mettre à jour les liens existants avec le displayName si vide
        updateExistingLinksOwner()
        
        // Charger le nombre de following
        loadFollowingCount()
    }
    
    private fun updateExistingLinksOwner() {
        val displayName = sessionManager?.getDisplayName() ?: return
        if (displayName.isBlank()) return
        
        viewModelScope.launch {
            val links = repository.links.value
            links.forEach { link ->
                if (link.ownerDisplayName.isBlank()) {
                    repository.updateLink(link.copy(ownerDisplayName = displayName))
                }
            }
            updateFilteredLinks()
        }
    }
    
    fun syncWithBackend(sessionManager: data.SessionManager) {
        val client = authenticatedClient ?: return
        viewModelScope.launch {
            renewTokenIfStale(sessionManager)
            try {
                _syncStatus.value = "Synchronisation..."
                runCatching { client.getTribe() }.onSuccess { _tribe.value = it }
                runCatching { client.listCircleInvitations() }.onSuccess { _circleInvitations.value = it }
                performSync(client)
                _syncStatus.value = "Synchronisé"
                // Photos prises sur ce téléphone avant leur envoi au serveur
                uploadPendingPhotos(client)
                // Les idées arrivées du serveur sans image : on tente de la récupérer
                fetchMissingImages()
            } catch (e: Exception) {
                _syncStatus.value = "Erreur: ${e.message}"
            }
        }
    }
    
    /**
     * Session glissante (voir data.TokenRenewal) : au plus une fois par jour, le
     * jeton est échangé contre un neuf. Hors ligne ou jeton déjà refusé : rien,
     * la prochaine requête dira si la session a expiré.
     */
    /** Au retour sur l'app (elle peut rester des jours en arrière-plan sans relancer de synchro). */
    fun renewSessionIfStale(sessionManager: data.SessionManager) {
        viewModelScope.launch { renewTokenIfStale(sessionManager) }
    }

    private suspend fun renewTokenIfStale(sessionManager: data.SessionManager) {
        val token = sessionManager.getToken() ?: return
        if (!data.TokenRenewal.shouldRenew(token)) return
        val fresh = runCatching { data.AuthRepository().refreshToken(token) }.getOrNull()?.takeIf { it.isNotBlank() } ?: return
        if (sessionManager.getToken() != token) return // déconnexion ou nouvelle session entre-temps
        sessionManager.updateToken(fresh)
        authToken = fresh
    }

    /** Force la synchronisation (pull-to-refresh) — fusionne intelligemment */
    fun forceSync() {
        val client = authenticatedClient ?: return
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                _syncStatus.value = "Synchronisation..."
                performSync(client)
                _syncStatus.value = "Synchronisé"
            } catch (e: Exception) {
                _syncStatus.value = "Erreur: ${e.message}"
            } finally {
                _isRefreshing.value = false
            }
        }
    }
    
    private suspend fun performSync(client: data.AuthenticatedApiClient) {
        // D'abord ce qui a été fait ici sans atteindre le serveur : la reprise des
        // données du serveur (qui fait foi) l'effacerait sinon. Sans réseau,
        // l'envoi échoue et la synchronisation s'arrête là, sans rien perdre.
        pushPending(client)

        val backendFolders = client.listFolders().folders.map { apiFolderToFolder(it) }
        val backendLinks = client.listLinks().links.map { apiLink ->
            val category = try {
                LinkCategory.valueOf(apiLink.category.removePrefix("LINK_CATEGORY_"))
            } catch (_: Exception) { LinkCategory.IDEE }
            Link(
                id = apiLink.id,
                title = apiLink.title,
                url = apiLink.url,
                description = apiLink.description,
                category = category,
                folderId = apiLink.folderId.ifBlank { null },
                tags = apiLink.tags,
                ageRange = apiLink.ageRange,
                location = apiLink.location,
                price = apiLink.price,
                imageUrl = apiLink.imageUrl,
                eventDate = if (apiLink.eventDate > 0) apiLink.eventDate else null,
                reminderEnabled = apiLink.reminderEnabled,
                rating = apiLink.rating,
                ingredients = apiLink.ingredients,
                favorite = apiLink.favorite,
                likeCount = apiLink.likeCount,
                likedByMe = apiLink.likedByMe,
                updatedAt = apiLink.updatedAt,
                createdAt = apiLink.createdAt,
                ownerId = apiLink.ownerId,
                ownerDisplayName = apiLink.ownerDisplayName,
                ownerIsAdmin = apiLink.ownerIsAdmin,
                visibility = apiLink.visibility.ifBlank { "private" }
            )
        }
        val backendChildren = client.listChildren().children.map { apiChild ->
            data.Child(id = apiChild.id, name = apiChild.name, birthDate = apiChild.birthDate, updatedAt = apiChild.updatedAt)
        }

        // Le serveur fait foi, sauf pour ce qui a été changé ici entre-temps
        pushLock.withLock {
            val p = repository.pending
            repository.replaceAll(
                links = data.SyncIds.merge(repository.links.value, backendLinks, { it.id }, p.linkUpdates, p.linkDeletes),
                folders = data.SyncIds.merge(repository.folders.value, backendFolders, { it.id }, p.folderUpdates),
                children = data.SyncIds.merge(repository.children.value, backendChildren, { it.id }, p.childUpdates, p.childDeletes),
            )
        }
        updateFilteredLinks()
    }
    
    // ── Envoi des changements faits ici ──────────────────────────────────────

    /** Un seul envoi à la fois : une idée créée ici ne doit pas partir deux fois. */
    private val pushLock = Mutex()

    /** Idée modifiée ici : à envoyer (une idée encore locale part entière à sa création). */
    private fun markLinkChanged(id: String) {
        if (!data.SyncIds.isLocal(id)) repository.updatePending { it.copy(linkUpdates = it.linkUpdates + id) }
    }

    /** Envoie tout de suite ce qui attend ; hors ligne, ce sera à la prochaine synchronisation. */
    private fun schedulePush() {
        val client = authenticatedClient ?: return
        viewModelScope.launch {
            try {
                pushPending(client)
            } catch (e: Exception) {
                _syncStatus.value = "Hors ligne : les changements partiront à la prochaine synchronisation"
            }
        }
    }

    /**
     * Envoie au serveur tout ce qui ne l'a pas encore atteint, dans l'ordre des
     * dépendances : enfants (cités par les dossiers), dossiers (cités par les
     * idées), idées, puis modifications et suppressions. Lève une exception au
     * premier échec réseau : ce qui reste attend la fois suivante.
     */
    private suspend fun pushPending(client: data.AuthenticatedApiClient) = pushLock.withLock {
        repository.children.value.filter { data.SyncIds.isLocal(it.id) }.forEach { local ->
            val saved = client.createChild(local.name, local.birthDate)
            val current = repository.children.value.find { it.id == local.id }
            if (current == null) {
                client.deleteChild(saved.id) // supprimé ici pendant l'envoi
            } else {
                repository.replaceChildId(local.id, saved.id)
                if (current != local) repository.updatePending { it.copy(childUpdates = it.childUpdates + saved.id) }
            }
        }
        repository.folders.value.filter { data.SyncIds.isLocal(it.id) }.forEach { local ->
            // Dossier public d'un compte à l'adresse non confirmée : il attend ici
            // la confirmation (refreshAccount relance la synchronisation)
            val saved = waitingForVerification { client.createFolder(createFolderRequest(local)) } ?: return@forEach
            val current = repository.folders.value.find { it.id == local.id }
            if (current == null) {
                client.deleteFolder(saved.id)
            } else {
                repository.replaceFolderId(local.id, saved.id)
                if (current != local) repository.updatePending { it.copy(folderUpdates = it.folderUpdates + saved.id) }
            }
        }
        repository.links.value
            .filter { data.SyncIds.isLocal(it.id) && it.folderId?.let(data.SyncIds::isLocal) != true }
            .forEach { local ->
                val sent = uploadLocalPhoto(client, local)
                val saved = waitingForVerification { client.createLink(createLinkRequest(sent)) } ?: return@forEach
                val current = repository.links.value.find { it.id == local.id }
                if (current == null) {
                    client.deleteLink(saved.id)
                } else {
                    repository.replaceLinkId(local.id, saved.id)
                    if (current != sent) markLinkChanged(saved.id)
                    // Le rappel suit l'idée sous son nouvel identifiant
                    if (current.reminderEnabled && current.eventDate != null) {
                        reminderCanceller?.invoke(local.id)
                        reminderScheduler?.invoke(current.copy(id = saved.id))
                    }
                }
            }
        updateFilteredLinks()

        // Modifications : retirées de la file avant l'envoi (une modification
        // faite pendant l'envoi les y remet), remises en cas d'échec
        repository.pending.childUpdates.forEach { id ->
            repository.updatePending { it.copy(childUpdates = it.childUpdates - id) }
            val child = repository.children.value.find { it.id == id } ?: return@forEach
            sendOrRequeue({ it.copy(childUpdates = it.childUpdates + id) }) { client.updateChild(id, child.name, child.birthDate) }
        }
        repository.pending.folderUpdates.forEach { id ->
            repository.updatePending { it.copy(folderUpdates = it.folderUpdates - id) }
            val folder = repository.folders.value.find { it.id == id } ?: return@forEach
            sendOrRequeue({ it.copy(folderUpdates = it.folderUpdates + id) }) { client.updateFolder(id, updateFolderRequest(folder)) }
        }
        repository.pending.linkUpdates.forEach { id ->
            repository.updatePending { it.copy(linkUpdates = it.linkUpdates - id) }
            val local = repository.links.value.find { it.id == id } ?: return@forEach
            val link = uploadLocalPhoto(client, local)
            sendOrRequeue({ it.copy(linkUpdates = it.linkUpdates + id) }) { client.updateLink(updateLinkRequest(link)) }
        }

        // Suppressions (déjà faites ici)
        repository.pending.linkDeletes.forEach { id ->
            sendOrRequeue(null) { client.deleteLink(id) }
            repository.updatePending { it.copy(linkDeletes = it.linkDeletes - id) }
        }
        repository.pending.childDeletes.forEach { id ->
            sendOrRequeue(null) { client.deleteChild(id) }
            repository.updatePending { it.copy(childDeletes = it.childDeletes - id) }
        }
    }

    /**
     * Envoie un changement. Introuvable sur le serveur (supprimé par un autre
     * membre de la tribu) : rien à faire, la synchronisation l'enlèvera d'ici.
     * Autre échec : remis dans la file (requeue) puis relancé.
     */
    private suspend fun sendOrRequeue(requeue: ((data.PendingChanges) -> data.PendingChanges)?, send: suspend () -> Unit) {
        try {
            send()
        } catch (e: Exception) {
            val m = e.message.orEmpty()
            if (m.contains("API error (404)") || m.contains("not found", ignoreCase = true)) return
            requeue?.let { repository.updatePending(it) }
            // Publication refusée tant que l'adresse n'est pas confirmée : elle
            // attend dans la file, sans bloquer le reste de la synchronisation
            if (data.EmailVerification.NOT_VERIFIED in m) return markWaitingForVerification()
            throw e
        }
    }

    /** null (et la création attend ici) si le serveur la refuse faute d'adresse confirmée. */
    private suspend fun <T> waitingForVerification(send: suspend () -> T): T? =
        try {
            send()
        } catch (e: Exception) {
            if (data.EmailVerification.NOT_VERIFIED !in e.message.orEmpty()) throw e
            markWaitingForVerification()
            null
        }

    private fun markWaitingForVerification() {
        sessionManager?.setEmailVerified(false)
        _syncStatus.value = data.EmailVerification.VERIFY_FIRST
    }

    private fun visibilityParam(v: String) = when (v.uppercase()) {
        "PUBLIC" -> "VISIBILITY_PUBLIC"
        "SHARED" -> "VISIBILITY_SHARED"
        else -> "VISIBILITY_PRIVATE"
    }

    private fun createFolderRequest(f: Folder) = data.CreateFolderRequest(
        name = f.name, icon = f.icon.name, color = f.color.name, visibility = visibilityParam(f.visibility),
        bannerUrl = f.bannerUrl, tags = f.tags, description = f.description, cover = f.cover,
        illustration = f.illustration, keyDate = f.keyDate, keyDateType = f.keyDateType, childIds = f.childIds,
        viewMode = f.viewMode, sort = f.sort, pinned = f.pinned,
    )

    private fun updateFolderRequest(f: Folder) = data.UpdateFolderRequest(
        folderId = f.id, name = f.name, icon = f.icon.name, color = f.color.name, visibility = visibilityParam(f.visibility),
        bannerUrl = f.bannerUrl, tags = f.tags, description = f.description, cover = f.cover,
        illustration = f.illustration, keyDate = f.keyDate, keyDateType = f.keyDateType, childIds = f.childIds,
        setChildIds = true, viewMode = f.viewMode, sort = f.sort, pinned = f.pinned,
    )

    private fun createLinkRequest(l: Link) = data.CreateLinkRequest(
        folderId = l.folderId ?: "", title = l.title, url = l.url, description = l.description,
        category = "LINK_CATEGORY_${l.category.name}", tags = l.tags, ageRange = l.ageRange, location = l.location,
        price = l.price, imageUrl = l.imageUrl, eventDate = l.eventDate ?: 0, reminderEnabled = l.reminderEnabled,
        rating = l.rating, ingredients = l.ingredients, visibility = l.visibility.ifBlank { "private" }.lowercase(),
    )

    private fun updateLinkRequest(l: Link) = data.UpdateLinkRequest(
        linkId = l.id, folderId = l.folderId ?: "", title = l.title, url = l.url, description = l.description,
        category = "LINK_CATEGORY_${l.category.name}", tags = l.tags, ageRange = l.ageRange, location = l.location,
        price = l.price, imageUrl = l.imageUrl, eventDate = l.eventDate ?: 0, reminderEnabled = l.reminderEnabled,
        rating = l.rating, ingredients = l.ingredients, visibility = l.visibility.ifBlank { "private" }.lowercase(),
    )

    /**
     * Photo prise ou choisie sur ce téléphone (file://, voir ImagePickerHelper) :
     * envoyée au serveur, dont l'adresse remplace le chemin local pour que la
     * tribu et le web la voient. En cas d'échec, le chemin local reste : le
     * prochain enregistrement ou la prochaine synchronisation retentera.
     */
    private suspend fun uploadLocalPhoto(client: data.AuthenticatedApiClient, link: Link): Link {
        if (!link.imageUrl.startsWith("file://")) return link
        val file = runCatching { java.io.File(java.net.URI(link.imageUrl)) }.getOrNull()?.takeIf { it.isFile } ?: return link
        val url = try {
            client.uploadPhoto(withContext(Dispatchers.IO) { file.readBytes() })
        } catch (e: Exception) {
            _syncStatus.value = "Erreur envoi photo: ${e.message}"
            return link
        }
        repository.links.value.find { it.id == link.id }?.let { repository.updateLink(it.copy(imageUrl = url)) }
        updateFilteredLinks()
        // La copie locale ne sert plus ; on la garde le temps que l'écran « C'est rangé ! » l'affiche
        viewModelScope.launch(Dispatchers.IO) {
            kotlinx.coroutines.delay(60_000)
            file.delete()
        }
        return link.copy(imageUrl = url)
    }

    /** Ses idées dont la photo n'est encore que sur ce téléphone (hors ligne, ou d'avant l'envoi des photos). */
    private suspend fun uploadPendingPhotos(client: data.AuthenticatedApiClient) {
        val me = currentUserId()
        repository.links.value
            .filter { it.imageUrl.startsWith("file://") && (it.ownerId.isBlank() || it.ownerId == me) }
            .forEach { link ->
                val sent = uploadLocalPhoto(client, link)
                if (sent.imageUrl != link.imageUrl) {
                    markLinkChanged(sent.id)
                    schedulePush()
                }
            }
    }

    suspend fun shareFolder(folderId: String): data.ShareFolderResponse? {
        val client = authenticatedClient ?: return null
        return try {
            client.shareFolder(folderId)
        } catch (e: Exception) {
            _syncStatus.value = "Erreur partage: ${e.message}"
            null
        }
    }

    suspend fun removeCollaborator(folderId: String, userId: String): Folder? {
        val client = authenticatedClient ?: return null
        return try {
            val resp = client.removeCollaborator(folderId, userId)
            val apiFolder = resp.folder
            val updatedFolder = apiFolderToFolder(apiFolder)
            repository.updateFolder(updatedFolder)
            updatedFolder
        } catch (e: Exception) {
            _syncStatus.value = "Erreur suppression collaborateur: ${e.message}"
            null
        }
    }

    private fun apiFolderToFolder(apiFolder: data.ApiAuthFolder): Folder {
        return Folder(
            id = apiFolder.id,
            name = apiFolder.name,
            ownerId = apiFolder.ownerId,
            icon = try { FolderIcon.valueOf(apiFolder.icon) } catch (_: Exception) { FolderIcon.FOLDER },
            color = try { FolderColor.valueOf(apiFolder.color) } catch (_: Exception) { FolderColor.ORANGE },
            bannerUrl = apiFolder.bannerUrl,
            tags = apiFolder.tags,
            visibility = when (apiFolder.visibility.uppercase()) {
                "PUBLIC", "VISIBILITY_PUBLIC" -> "PUBLIC"
                "SHARED", "VISIBILITY_SHARED" -> "SHARED"
                else -> "PRIVATE"
            },
            ownerDisplayName = apiFolder.ownerDisplayName,
            linkCount = apiFolder.linkCount,
            likeCount = apiFolder.likeCount,
            collaborators = apiFolder.collaborators.map { c ->
                Collaborator(
                    userId = c.userId,
                    email = c.email,
                    displayName = c.displayName,
                    role = c.role
                )
            },
            updatedAt = apiFolder.updatedAt,
            description = apiFolder.description,
            cover = apiFolder.cover,
            illustration = apiFolder.illustration,
            keyDate = apiFolder.keyDate,
            keyDateType = apiFolder.keyDateType,
            childIds = apiFolder.childIds,
            viewMode = apiFolder.viewMode,
            sort = apiFolder.sort,
            pinned = apiFolder.pinned,
        )
    }
    
    // ── Children Backend Sync ─────────────────────────────────────────────────
    
    private companion object {
        /** Une même idée sans image n'est retentée qu'une fois par jour… */
        const val IMAGE_RETRY_MS = 24L * 60 * 60 * 1000
        /** …ou une fois par heure quand l'utilisateur ouvre sa fiche. */
        const val IMAGE_RETRY_ON_OPEN_MS = 60L * 60 * 1000
        const val IMAGE_BACKFILL_BATCH = 30
    }
}
