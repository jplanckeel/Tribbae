package viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
        repository.addFolder(Folder(
            id = kotlin.random.Random.nextLong().toString(),
            name = name, icon = icon, color = color
        ))
    }

    fun deleteFolder(id: String) { repository.deleteFolder(id); updateFilteredLinks() }

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
        repository.addChild(data.Child(
            id = kotlin.random.Random.nextLong().toString(),
            name = name, birthDate = birthDate
        ))
    }

    fun updateChild(child: data.Child) { repository.updateChild(child) }
    fun deleteChild(id: String) {
        if (_selectedChildId.value == id) _selectedChildId.value = null
        repository.deleteChild(id)
        updateFilteredLinks()
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
}
