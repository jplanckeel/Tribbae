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
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
        
        // Sauvegarder sur le backend si authentifié
        if (authenticatedClient != null) {
            saveToBackend(link)
        }

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
    fun acceptTribeInvitation(token: String, onDone: (String?) -> Unit) = tribeAction(onDone) { it.acceptTribeInvitation(token) }
    fun removeTribeMember(userId: String, onDone: (String?) -> Unit) = tribeAction(onDone) { it.removeTribeMember(userId) }
    fun setTribeSharing(sharing: Boolean, onDone: (String?) -> Unit) = tribeAction(onDone) { it.setTribeSharing(sharing) }

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
        else -> "Impossible de joindre le serveur, réessayez."
    }

    /**
     * Déconnexion : la session ET les données du compte gardées sur le téléphone
     * (idées, dossiers, enfants) sont effacées, pour qu'un autre utilisateur de
     * l'appareil ne les voie pas.
     */
    fun signOut() {
        repository.clearAll()
        _tribe.value = null
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
        
        // Supprimer du backend si authentifié
        if (authenticatedClient != null) {
            deleteFromBackend(id)
        }
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

        // Mettre à jour sur le backend si authentifié
        if (authenticatedClient != null) {
            updateLinkOnBackend(updatedLink)
        }
    }

    fun addFolder(name: String, icon: FolderIcon, color: FolderColor, visibility: String = "PRIVATE") =
        addFolder(Folder(id = "", name = name, icon = icon, color = color, visibility = visibility))

    /** Nouveau dossier (id local remplacé par celui du serveur après synchro). */
    fun addFolder(draft: Folder) {
        val folder = draft.copy(id = kotlin.random.Random.nextLong().toString())
        repository.addFolder(folder)
        
        // Sauvegarder sur le backend si authentifié
        if (authenticatedClient != null) {
            saveFolderToBackend(folder)
        }
    }

    fun deleteFolder(id: String, onSuccess: () -> Unit = {}) {
        val client = authenticatedClient
        if (client == null) {
            // Mode hors-ligne : suppression locale uniquement
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
        
        if (authenticatedClient != null) {
            updateFolderOnBackend(updatedFolder)
        }
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
        
        // Sauvegarder sur le backend si authentifié
        if (authenticatedClient != null) {
            saveChildToBackend(child)
        }
    }

    fun updateChild(child: data.Child) {
        val updatedChild = child.copy(updatedAt = "")
        repository.updateChild(updatedChild)
        
        // Mettre à jour sur le backend si authentifié
        if (authenticatedClient != null) {
            updateChildOnBackend(updatedChild)
        }
    }
    
    fun deleteChild(id: String) {
        if (_selectedChildId.value == id) _selectedChildId.value = null
        repository.deleteChild(id)
        updateFilteredLinks()
        
        // Supprimer du backend si authentifié
        if (authenticatedClient != null) {
            deleteChildFromBackend(id)
        }
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
            try {
                _syncStatus.value = "Synchronisation..."
                runCatching { client.getTribe() }.onSuccess { _tribe.value = it }
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
        // Le backend est la source de vérité : on remplace tout localement

        // Dossiers : remplacer la liste locale par celle du backend
        val foldersResponse = client.listFolders()
        val backendFolders = foldersResponse.folders.map { apiFolderToFolder(it) }
        val backendFolderIds = backendFolders.map { it.id }.toSet()

        // Supprimer les dossiers locaux absents du backend (ex: supprimés)
        repository.folders.value
            .filter { it.id !in backendFolderIds }
            .forEach { repository.deleteFolder(it.id) }

        // Ajouter ou mettre à jour les dossiers du backend
        backendFolders.forEach { folder ->
            if (repository.folders.value.none { it.id == folder.id }) {
                repository.addFolder(folder)
            } else {
                repository.updateFolder(folder)
            }
        }

        // Liens : remplacer la liste locale par celle du backend
        val linksResponse = client.listLinks()
        val backendLinks = linksResponse.links.map { apiLink ->
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
        val backendLinkIds = backendLinks.map { it.id }.toSet()

        // Supprimer les liens locaux absents du backend
        repository.links.value
            .filter { it.id !in backendLinkIds }
            .forEach { repository.deleteLink(it.id) }

        // Ajouter ou mettre à jour les liens du backend
        backendLinks.forEach { link ->
            if (repository.links.value.none { it.id == link.id }) {
                repository.addLink(link)
            } else {
                repository.updateLink(link)
            }
        }
        
        updateFilteredLinks()
        
        // Charger les enfants
        val childrenResponse = client.listChildren()
        childrenResponse.children.forEach { apiChild ->
            val localChild = repository.children.value.find { it.id == apiChild.id }
            
            // Toujours synchroniser (le backend est la source de vérité)
            val child = data.Child(
                id = apiChild.id,
                name = apiChild.name,
                birthDate = apiChild.birthDate,
                updatedAt = apiChild.updatedAt
            )
            if (localChild == null) {
                repository.addChild(child)
            } else {
                repository.updateChild(child)
            }
        }
    }
    
    fun saveToBackend(local: Link) {
        val client = authenticatedClient ?: return
        viewModelScope.launch {
            try {
                val link = uploadLocalPhoto(client, local)
                val req = data.CreateLinkRequest(
                    folderId = link.folderId ?: "",
                    title = link.title,
                    url = link.url,
                    description = link.description,
                    category = "LINK_CATEGORY_${link.category.name}",
                    tags = link.tags,
                    ageRange = link.ageRange,
                    location = link.location,
                    price = link.price,
                    imageUrl = link.imageUrl,
                    eventDate = link.eventDate ?: 0,
                    reminderEnabled = link.reminderEnabled,
                    rating = link.rating,
                    ingredients = link.ingredients,
                    visibility = link.visibility.ifBlank { "private" }.lowercase()
                )
                val savedLink = client.createLink(req)
                // Supprimer l'ancien lien local et ajouter le nouveau avec l'ID du backend
                repository.deleteLink(link.id)
                repository.addLink(link.copy(id = savedLink.id))
                updateFilteredLinks()
            } catch (e: Exception) {
                _syncStatus.value = "Erreur sauvegarde: ${e.message}"
            }
        }
    }
    
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
                if (sent.imageUrl != link.imageUrl) updateLinkOnBackend(sent)
            }
    }

    fun saveFolderToBackend(folder: Folder) {
        val client = authenticatedClient ?: return
        viewModelScope.launch {
            try {
                val req = data.CreateFolderRequest(
                    name = folder.name,
                    icon = folder.icon.name,
                    color = folder.color.name,
                    visibility = when (folder.visibility.uppercase()) {
                        "PUBLIC" -> "VISIBILITY_PUBLIC"
                        "SHARED" -> "VISIBILITY_SHARED"
                        else -> "VISIBILITY_PRIVATE"
                    },
                    bannerUrl = folder.bannerUrl,
                    tags = folder.tags,
                    description = folder.description,
                    cover = folder.cover,
                    illustration = folder.illustration,
                    keyDate = folder.keyDate,
                    keyDateType = folder.keyDateType,
                    childIds = folder.childIds,
                    viewMode = folder.viewMode,
                    sort = folder.sort,
                    pinned = folder.pinned,
                )
                val savedFolder = client.createFolder(req)
                // Mettre à jour l'ID local avec l'ID du backend
                val updatedFolder = folder.copy(id = savedFolder.id)
                repository.deleteFolder(folder.id)
                repository.addFolder(updatedFolder)
            } catch (e: Exception) {
                _syncStatus.value = "Erreur sauvegarde dossier: ${e.message}"
            }
        }
    }
    
    fun deleteFromBackend(linkId: String) {
        val client = authenticatedClient ?: return
        viewModelScope.launch {
            try {
                client.deleteLink(linkId)
            } catch (e: Exception) {
                _syncStatus.value = "Erreur suppression: ${e.message}"
            }
        }
    }

    private fun updateLinkOnBackend(local: Link) {
        val client = authenticatedClient ?: return
        viewModelScope.launch {
            val link = uploadLocalPhoto(client, local)
            try {
                val req = data.UpdateLinkRequest(
                    linkId = link.id,
                    folderId = link.folderId ?: "",
                    title = link.title,
                    url = link.url,
                    description = link.description,
                    category = "LINK_CATEGORY_${link.category.name}",
                    tags = link.tags,
                    ageRange = link.ageRange,
                    location = link.location,
                    price = link.price,
                    imageUrl = link.imageUrl,
                    eventDate = link.eventDate ?: 0,
                    reminderEnabled = link.reminderEnabled,
                    rating = link.rating,
                    ingredients = link.ingredients,
                    visibility = link.visibility.ifBlank { "private" }.lowercase()
                )
                client.updateLink(req)
            } catch (e: Exception) {
                // Si le lien n'existe pas sur le backend (créé localement), le créer
                if (e.message?.contains("404") == true || e.message?.contains("not found") == true) {
                    saveToBackend(link)
                } else {
                    _syncStatus.value = "Erreur mise à jour: ${e.message}"
                }
            }
        }
    }
    
    private fun updateFolderOnBackend(folder: Folder) {
        val client = authenticatedClient ?: return
        viewModelScope.launch {
            try {
                val req = data.UpdateFolderRequest(
                    folderId = folder.id,
                    name = folder.name,
                    icon = folder.icon.name,
                    color = folder.color.name,
                    visibility = when (folder.visibility.uppercase()) {
                        "PUBLIC" -> "VISIBILITY_PUBLIC"
                        "SHARED" -> "VISIBILITY_SHARED"
                        else -> "VISIBILITY_PRIVATE"
                    },
                    bannerUrl = folder.bannerUrl,
                    tags = folder.tags,
                    description = folder.description,
                    cover = folder.cover,
                    illustration = folder.illustration,
                    keyDate = folder.keyDate,
                    keyDateType = folder.keyDateType,
                    childIds = folder.childIds,
                    setChildIds = true,
                    viewMode = folder.viewMode,
                    sort = folder.sort,
                    pinned = folder.pinned,
                )
                client.updateFolder(folder.id, req)
            } catch (e: Exception) {
                // Si le dossier n'existe pas sur le backend (créé localement), le créer
                if (e.message?.contains("404") == true || e.message?.contains("not found") == true) {
                    saveFolderToBackend(folder)
                } else {
                    _syncStatus.value = "Erreur mise à jour dossier: ${e.message}"
                }
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

    suspend fun addCollaborator(folderId: String, email: String, role: String): Folder? {
        val client = authenticatedClient ?: return null
        return try {
            val resp = client.addCollaborator(folderId, email, role)
            val apiFolder = resp.folder
            val updatedFolder = apiFolderToFolder(apiFolder)
            repository.updateFolder(updatedFolder)
            updatedFolder
        } catch (e: Exception) {
            _syncStatus.value = "Erreur ajout collaborateur: ${e.message}"
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
    
    private fun saveChildToBackend(child: data.Child) {
        val client = authenticatedClient ?: return
        viewModelScope.launch {
            try {
                val savedChild = client.createChild(child.name, child.birthDate)
                // Mettre à jour l'ID local avec l'ID du backend
                repository.deleteChild(child.id)
                repository.addChild(data.Child(
                    id = savedChild.id,
                    name = savedChild.name,
                    birthDate = savedChild.birthDate
                ))
            } catch (e: Exception) {
                _syncStatus.value = "Erreur sauvegarde enfant: ${e.message}"
            }
        }
    }
    
    private fun updateChildOnBackend(child: data.Child) {
        val client = authenticatedClient ?: return
        viewModelScope.launch {
            try {
                client.updateChild(child.id, child.name, child.birthDate)
            } catch (e: Exception) {
                _syncStatus.value = "Erreur mise à jour enfant: ${e.message}"
            }
        }
    }
    
    private fun deleteChildFromBackend(childId: String) {
        val client = authenticatedClient ?: return
        viewModelScope.launch {
            try {
                client.deleteChild(childId)
            } catch (e: Exception) {
                _syncStatus.value = "Erreur suppression enfant: ${e.message}"
            }
        }
    }

    private companion object {
        /** Une même idée sans image n'est retentée qu'une fois par jour… */
        const val IMAGE_RETRY_MS = 24L * 60 * 60 * 1000
        /** …ou une fois par heure quand l'utilisateur ouvre sa fiche. */
        const val IMAGE_RETRY_ON_OPEN_MS = 60L * 60 * 1000
        const val IMAGE_BACKFILL_BATCH = 30
    }
}
