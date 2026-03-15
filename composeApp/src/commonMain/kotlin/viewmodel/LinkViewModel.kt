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
    private var commentRepository: data.CommentRepository? = null
    
    private val _syncStatus = MutableStateFlow<String?>(null)
    val syncStatus: StateFlow<String?> = _syncStatus.asStateFlow()
    
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()
    
    // Follow state
    private val _followStatus = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val followStatus: StateFlow<Map<String, Boolean>> = _followStatus.asStateFlow()
    
    private val _followingCount = MutableStateFlow(0)
    val followingCount: StateFlow<Int> = _followingCount.asStateFlow()
    
    // Comment state
    private val _comments = MutableStateFlow<Map<String, List<data.Comment>>>(emptyMap())
    val comments: StateFlow<Map<String, List<data.Comment>>> = _comments.asStateFlow()
    
    private val _commentCounts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val commentCounts: StateFlow<Map<String, Int>> = _commentCounts.asStateFlow()

    /** Fonction pour récupérer l'image OG — injectée côté Android */
    var ogImageFetcher: (suspend (String) -> String?)? = null

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
        imageUrl: String = "", ingredients: List<String> = emptyList()
    ) {
        val ownerDisplayName = sessionManager?.getDisplayName() ?: ""
        val link = Link(
            id = kotlin.random.Random.nextLong().toString(),
            title = title, url = url, description = description,
            category = category, folderId = folderId,
            tags = tags, ageRange = ageRange, location = location, price = price,
            eventDate = eventDate, reminderEnabled = reminderEnabled, rating = rating,
            imageUrl = imageUrl, ingredients = ingredients,
            ownerDisplayName = ownerDisplayName,
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

        // Récupère l'image OG en arrière-plan seulement si pas d'image manuelle
        if (url.isNotBlank() && imageUrl.isBlank()) {
            viewModelScope.launch {
                val ogUrl = ogImageFetcher?.invoke(url)
                if (!ogUrl.isNullOrBlank()) {
                    repository.updateLink(link.copy(imageUrl = ogUrl))
                    updateFilteredLinks()
                }
            }
        }
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

    fun getLinksForFolder(folderId: String): List<Link> =
        repository.links.value.filter { it.folderId == folderId }

    fun getLinksForTag(tag: String): List<Link> =
        repository.links.value.filter { tag in it.tags }

    fun getLinksWithDate(): List<Link> =
        repository.links.value.filter { it.eventDate != null }.sortedBy { it.eventDate }

    fun addFolder(name: String, icon: FolderIcon, color: FolderColor, visibility: String = "PRIVATE") {
        val folder = Folder(
            id = kotlin.random.Random.nextLong().toString(),
            name = name, icon = icon, color = color, visibility = visibility
        )
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

    fun searchTags(query: String): List<String> =
        if (query.isBlank()) repository.tags.value
        else repository.tags.value.filter { it.contains(query.trim(), ignoreCase = true) }

    fun updateSearchQuery(query: String) { _searchQuery.value = query; updateFilteredLinks() }
    fun selectCategory(category: LinkCategory?) { _selectedCategory.value = category; updateFilteredLinks() }
    fun selectFolder(folderId: String?) { _selectedFolderId.value = folderId; updateFilteredLinks() }

    fun selectChild(childId: String?) { _selectedChildId.value = childId; updateFilteredLinks() }

    fun toggleFavorites() { _favoritesOnly.value = !_favoritesOnly.value; updateFilteredLinks() }

    /** Toggle le favori d'un lien (cœur) — appelle l'API like/unlike du backend */
    fun toggleFavorite(linkId: String) {
        val link = repository.links.value.find { it.id == linkId } ?: return
        val newFavorite = !link.favorite
        repository.updateLink(link.copy(favorite = newFavorite))
        updateFilteredLinks()
        
        // Synchroniser avec le backend via like/unlike
        val client = authenticatedClient ?: return
        viewModelScope.launch {
            try {
                if (newFavorite) {
                    client.likeLink(linkId)
                } else {
                    client.unlikeLink(linkId)
                }
            } catch (e: Exception) {
                // Rollback en cas d'erreur
                repository.updateLink(link.copy(favorite = !newFavorite))
                updateFilteredLinks()
            }
        }
    }

    fun clearAllFilters() {
        _selectedCategory.value = null
        _selectedFolderId.value = null
        _selectedChildId.value = null
        _favoritesOnly.value = false
        _searchQuery.value = ""
        updateFilteredLinks()
    }

    /** Nombre de filtres actifs (hors recherche texte) */
    fun activeFilterCount(): Int {
        var count = 0
        if (_selectedCategory.value != null) count++
        if (_selectedFolderId.value != null) count++
        if (_selectedChildId.value != null) count++
        if (_favoritesOnly.value) count++
        return count
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

    private val _aiIdeas = MutableStateFlow<List<AiSuggestedLink>>(emptyList())
    val aiIdeas: StateFlow<List<AiSuggestedLink>> = _aiIdeas.asStateFlow()

    private val _aiLoading = MutableStateFlow(false)
    val aiLoading: StateFlow<Boolean> = _aiLoading.asStateFlow()

    private val _aiError = MutableStateFlow<String?>(null)
    val aiError: StateFlow<String?> = _aiError.asStateFlow()

    fun generateAiIdeas(prompt: String) {
        val token = authToken
        if (token == null) {
            _aiError.value = "Vous devez être connecté pour utiliser l'IA. Allez dans Profil pour vous connecter."
            return
        }
        viewModelScope.launch {
            _aiLoading.value = true
            _aiError.value = null
            _aiIdeas.value = emptyList()
            try {
                val result = aiRepository.generateIdeas(prompt, token)
                _aiIdeas.value = result.ideas
            } catch (e: Exception) {
                val errorMsg = when {
                    e.message?.contains("timeout") == true || e.message?.contains("timed out") == true ->
                        "Timeout : la génération prend trop de temps. Vérifiez que le backend et Ollama sont lancés."
                    e.message?.contains("Connection refused") == true ->
                        "Impossible de se connecter au backend. Vérifiez votre connexion internet."
                    e.message?.contains("401") == true || e.message?.contains("403") == true ->
                        "Session expirée. Reconnectez-vous dans Profil."
                    else -> e.message ?: "Erreur inconnue"
                }
                _aiError.value = errorMsg
            } finally {
                _aiLoading.value = false
            }
        }
    }

    fun clearAiIdeas() {
        _aiIdeas.value = emptyList()
        _aiError.value = null
    }

    fun saveAiIdeas(ideas: List<AiSuggestedLink>, folderId: String?) {
        ideas.forEach { idea ->
            val category = try {
                LinkCategory.valueOf(idea.category.removePrefix("LINK_CATEGORY_"))
            } catch (_: Exception) { LinkCategory.IDEE }
            addLink(
                title = idea.title,
                url = idea.url,
                description = idea.description,
                category = category,
                folderId = folderId,
                tags = idea.tags,
                ageRange = idea.ageRange,
                location = idea.location,
                price = idea.price,
                imageUrl = idea.imageUrl,
                ingredients = idea.ingredients
            )
        }
    }

    /** Récupère les images OG manquantes pour les liens existants (sans image manuelle) */
    fun fetchMissingImages() {
        val fetcher = ogImageFetcher ?: return
        viewModelScope.launch {
            repository.links.value
                .filter { it.imageUrl.isBlank() && it.url.isNotBlank() }
                .forEach { link ->
                    val imageUrl = fetcher(link.url)
                    if (!imageUrl.isNullOrBlank()) {
                        repository.updateLink(link.copy(imageUrl = imageUrl))
                        updateFilteredLinks()
                    }
                }
        }
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
     * Follow a user
     * @param userId The ID of the user to follow
     */
    fun followUser(userId: String) {
        val repo = followRepository ?: return
        viewModelScope.launch {
            try {
                repo.follow(userId).getOrThrow()
                // Update local state
                _followStatus.value = _followStatus.value + (userId to true)
                // Refresh following count
                loadFollowingCount()
            } catch (e: Exception) {
                println("ERROR: Failed to follow user: ${e.message}")
                _syncStatus.value = "Erreur: impossible de suivre l'utilisateur"
            }
        }
    }
    
    /**
     * Unfollow a user
     * @param userId The ID of the user to unfollow
     */
    fun unfollowUser(userId: String) {
        val repo = followRepository ?: return
        viewModelScope.launch {
            try {
                repo.unfollow(userId).getOrThrow()
                // Update local state
                _followStatus.value = _followStatus.value + (userId to false)
                // Refresh following count
                loadFollowingCount()
            } catch (e: Exception) {
                println("ERROR: Failed to unfollow user: ${e.message}")
                _syncStatus.value = "Erreur: impossible de ne plus suivre l'utilisateur"
            }
        }
    }
    
    /**
     * Check if the current user is following another user
     * @param userId The ID of the user to check
     */
    fun checkFollowStatus(userId: String) {
        val repo = followRepository ?: return
        viewModelScope.launch {
            try {
                val isFollowing = repo.isFollowing(userId).getOrThrow()
                _followStatus.value = _followStatus.value + (userId to isFollowing)
            } catch (e: Exception) {
                println("ERROR: Failed to check follow status: ${e.message}")
            }
        }
    }
    
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
    
    /**
     * Create a new comment on a link
     * @param linkId The ID of the link to comment on
     * @param text The comment text
     */
    fun createComment(linkId: String, text: String) {
        val repo = commentRepository ?: return
        viewModelScope.launch {
            try {
                val comment = repo.createComment(linkId, text).getOrThrow()
                // Update local state - add comment to the list
                val currentComments = _comments.value[linkId] ?: emptyList()
                _comments.value = _comments.value + (linkId to listOf(comment) + currentComments)
                // Update comment count
                val currentCount = _commentCounts.value[linkId] ?: 0
                _commentCounts.value = _commentCounts.value + (linkId to currentCount + 1)
            } catch (e: Exception) {
                println("ERROR: Failed to create comment: ${e.message}")
                _syncStatus.value = "Erreur: impossible de créer le commentaire"
            }
        }
    }
    
    /**
     * Load comments for a link
     * @param linkId The ID of the link
     */
    fun loadComments(linkId: String) {
        val repo = commentRepository ?: return
        viewModelScope.launch {
            try {
                val comments = repo.getComments(linkId).getOrThrow()
                _comments.value = _comments.value + (linkId to comments)
                _commentCounts.value = _commentCounts.value + (linkId to comments.size)
            } catch (e: Exception) {
                println("ERROR: Failed to load comments: ${e.message}")
            }
        }
    }
    
    /**
     * Delete a comment
     * @param commentId The ID of the comment to delete
     * @param linkId The ID of the link the comment belongs to
     */
    fun deleteComment(commentId: String, linkId: String) {
        val repo = commentRepository ?: return
        viewModelScope.launch {
            try {
                repo.deleteComment(commentId).getOrThrow()
                // Update local state - remove comment from the list
                val currentComments = _comments.value[linkId] ?: emptyList()
                _comments.value = _comments.value + (linkId to currentComments.filter { it.id != commentId })
                // Update comment count
                val currentCount = _commentCounts.value[linkId] ?: 0
                _commentCounts.value = _commentCounts.value + (linkId to maxOf(0, currentCount - 1))
            } catch (e: Exception) {
                println("ERROR: Failed to delete comment: ${e.message}")
                _syncStatus.value = "Erreur: impossible de supprimer le commentaire"
            }
        }
    }
    
    /**
     * Load comment count for a link
     * @param linkId The ID of the link
     */
    fun loadCommentCount(linkId: String) {
        val repo = commentRepository ?: return
        viewModelScope.launch {
            try {
                val count = repo.getCommentCount(linkId).getOrThrow()
                _commentCounts.value = _commentCounts.value + (linkId to count)
            } catch (e: Exception) {
                println("ERROR: Failed to load comment count: ${e.message}")
            }
        }
    }
    
    /**
     * Get comments for a link from state
     * @param linkId The ID of the link
     * @return List of comments
     */
    fun getCommentsForLink(linkId: String): List<data.Comment> {
        return _comments.value[linkId] ?: emptyList()
    }
    
    /**
     * Get comment count for a link from state
     * @param linkId The ID of the link
     * @return The number of comments
     */
    fun getCommentCountForLink(linkId: String): Int {
        return _commentCounts.value[linkId] ?: 0
    }
    
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
    
    /**
     * Update folder visibility
     * @param folderId The ID of the folder
     * @param visibility The new visibility value ("PRIVATE", "PUBLIC", or "SHARED")
     */
    fun updateFolderVisibility(folderId: String, visibility: String) {
        val folder = repository.folders.value.find { it.id == folderId } ?: return
        val updatedFolder = folder.copy(visibility = visibility)
        updateFolder(updatedFolder)
    }
    
    /**
     * Refresh data after visibility changes
     * This ensures the UI reflects the latest visibility state
     */
    fun refreshAfterVisibilityChange() {
        val client = authenticatedClient ?: return
        viewModelScope.launch {
            try {
                _syncStatus.value = "Actualisation..."
                performSync(client)
                updateFilteredLinks()
                _syncStatus.value = "Actualisé"
            } catch (e: Exception) {
                println("ERROR: Failed to refresh after visibility change: ${e.message}")
                _syncStatus.value = "Erreur: ${e.message}"
            }
        }
    }
    
    // ── Backend Sync ──────────────────────────────────────────────────────────
    
    fun initAuthenticatedClient(sessionManager: data.SessionManager) {
        this.sessionManager = sessionManager
        authenticatedClient = data.AuthenticatedApiClient(sessionManager = sessionManager)
        followRepository = data.FollowRepository(sessionManager = sessionManager)
        commentRepository = data.CommentRepository(sessionManager = sessionManager)
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
                performSync(client)
                _syncStatus.value = "Synchronisé"
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
                favorite = apiLink.likedByMe,
                updatedAt = apiLink.updatedAt,
                createdAt = apiLink.createdAt,
                ownerId = apiLink.ownerId,
                ownerDisplayName = apiLink.ownerDisplayName,
                ownerIsAdmin = apiLink.ownerIsAdmin
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
    
    fun saveToBackend(link: Link) {
        val client = authenticatedClient ?: return
        viewModelScope.launch {
            try {
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
                    ingredients = link.ingredients
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
    
    fun saveFolderToBackend(folder: Folder) {
        val client = authenticatedClient ?: return
        viewModelScope.launch {
            try {
                val req = data.CreateFolderRequest(
                    name = folder.name,
                    icon = folder.icon.name,
                    color = folder.color.name,
                    visibility = "PRIVATE",
                    bannerUrl = folder.bannerUrl,
                    tags = folder.tags
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

    private fun updateLinkOnBackend(link: Link) {
        val client = authenticatedClient ?: return
        viewModelScope.launch {
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
                    ingredients = link.ingredients
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
                    visibility = folder.visibility.ifBlank { "PRIVATE" },
                    bannerUrl = folder.bannerUrl,
                    tags = folder.tags
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
            icon = try { FolderIcon.valueOf(apiFolder.icon) } catch (_: Exception) { FolderIcon.FOLDER },
            color = try { FolderColor.valueOf(apiFolder.color) } catch (_: Exception) { FolderColor.ORANGE },
            bannerUrl = apiFolder.bannerUrl,
            tags = apiFolder.tags,
            visibility = apiFolder.visibility,
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
            updatedAt = apiFolder.updatedAt
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
}
