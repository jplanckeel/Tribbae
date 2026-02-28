package viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import data.AiRepository
import data.AiSuggestedLink
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

    /** Token JWT — injecté depuis MainActivity */
    var authToken: String? = null
    
    /** Client API authentifié */
    private var authenticatedClient: data.AuthenticatedApiClient? = null
    
    private val _syncStatus = MutableStateFlow<String?>(null)
    val syncStatus: StateFlow<String?> = _syncStatus.asStateFlow()
    
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

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
        val link = Link(
            id = kotlin.random.Random.nextLong().toString(),
            title = title, url = url, description = description,
            category = category, folderId = folderId,
            tags = tags, ageRange = ageRange, location = location, price = price,
            eventDate = eventDate, reminderEnabled = reminderEnabled, rating = rating,
            imageUrl = imageUrl, ingredients = ingredients
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
        repository.updateLink(link)
        updateFilteredLinks()
        // Met à jour le rappel si nécessaire
        if (link.reminderEnabled && link.eventDate != null) {
            reminderScheduler?.invoke(link)
        } else {
            reminderCanceller?.invoke(link.id)
        }
    }

    fun getLinksForFolder(folderId: String): List<Link> =
        repository.links.value.filter { it.folderId == folderId }

    fun getLinksForTag(tag: String): List<Link> =
        repository.links.value.filter { tag in it.tags }

    fun getLinksWithDate(): List<Link> =
        repository.links.value.filter { it.eventDate != null }.sortedBy { it.eventDate }

    fun addFolder(name: String, icon: FolderIcon, color: FolderColor) {
        val folder = Folder(
            id = kotlin.random.Random.nextLong().toString(),
            name = name, icon = icon, color = color
        )
        repository.addFolder(folder)
        
        // Sauvegarder sur le backend si authentifié
        if (authenticatedClient != null) {
            saveFolderToBackend(folder)
        }
    }

    fun deleteFolder(id: String) { 
        repository.deleteFolder(id)
        updateFilteredLinks()
        
        // Supprimer du backend si authentifié
        if (authenticatedClient != null) {
            deleteFolderFromBackend(id)
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

    /** Toggle le favori d'un lien (cœur) */
    fun toggleFavorite(linkId: String) {
        val link = repository.links.value.find { it.id == linkId } ?: return
        repository.updateLink(link.copy(favorite = !link.favorite))
        updateFilteredLinks()
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
            name = name, birthDate = birthDate
        )
        repository.addChild(child)
        
        // Sauvegarder sur le backend si authentifié
        if (authenticatedClient != null) {
            saveChildToBackend(child)
        }
    }

    fun updateChild(child: data.Child) {
        repository.updateChild(child)
        
        // Mettre à jour sur le backend si authentifié
        if (authenticatedClient != null) {
            updateChildOnBackend(child)
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
    
    // ── Backend Sync ──────────────────────────────────────────────────────────
    
    fun initAuthenticatedClient(sessionManager: data.SessionManager) {
        authenticatedClient = data.AuthenticatedApiClient(sessionManager = sessionManager)
        authToken = sessionManager.getToken()
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
    
    /** Force la synchronisation (pull-to-refresh) — remplace les données locales par celles du backend */
    fun forceSync() {
        val client = authenticatedClient ?: return
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                _syncStatus.value = "Synchronisation..."
                
                // Vider les données locales avant de recharger
                repository.clearAll()
                
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
        // Charger les dossiers
        val foldersResponse = client.listFolders()
        foldersResponse.folders.forEach { apiFolder ->
            val folder = Folder(
                id = apiFolder.id,
                name = apiFolder.name,
                icon = try { FolderIcon.valueOf(apiFolder.icon) } catch (_: Exception) { FolderIcon.FOLDER },
                color = try { FolderColor.valueOf(apiFolder.color) } catch (_: Exception) { FolderColor.ORANGE },
                bannerUrl = apiFolder.bannerUrl,
                tags = apiFolder.tags
            )
            if (repository.folders.value.none { it.id == folder.id }) {
                repository.addFolder(folder)
            }
        }
        
        // Charger les liens
        val linksResponse = client.listLinks()
        linksResponse.links.forEach { apiLink ->
            val category = try {
                LinkCategory.valueOf(apiLink.category.removePrefix("LINK_CATEGORY_"))
            } catch (_: Exception) { LinkCategory.IDEE }
            
            val link = Link(
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
                favorite = false
            )
            if (repository.links.value.none { it.id == link.id }) {
                repository.addLink(link)
            }
        }
        
        updateFilteredLinks()
        
        // Charger les enfants
        val childrenResponse = client.listChildren()
        childrenResponse.children.forEach { apiChild ->
            val child = data.Child(
                id = apiChild.id,
                name = apiChild.name,
                birthDate = apiChild.birthDate
            )
            if (repository.children.value.none { it.id == child.id }) {
                repository.addChild(child)
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
    
    fun deleteFolderFromBackend(folderId: String) {
        val client = authenticatedClient ?: return
        viewModelScope.launch {
            try {
                client.deleteFolder(folderId)
            } catch (e: Exception) {
                _syncStatus.value = "Erreur suppression dossier: ${e.message}"
            }
        }
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
