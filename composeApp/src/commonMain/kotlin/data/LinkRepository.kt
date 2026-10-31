package data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex

class LinkRepository(private val storage: Storage? = null) {
    private val _links = MutableStateFlow<List<Link>>(emptyList())
    val links: StateFlow<List<Link>> = _links.asStateFlow()

    private val _folders = MutableStateFlow<List<Folder>>(emptyList())
    val folders: StateFlow<List<Folder>> = _folders.asStateFlow()

    private val _tags = MutableStateFlow<List<String>>(emptyList())
    val tags: StateFlow<List<String>> = _tags.asStateFlow()

    private val _children = MutableStateFlow<List<Child>>(emptyList())
    val children: StateFlow<List<Child>> = _children.asStateFlow()

    init {
        storage?.let {
            _links.value = it.loadLinks()
            _folders.value = it.loadFolders()
            _children.value = it.loadChildren()
            // Tags : union des tags sauvegardés + tags présents dans les liens existants
            val savedTags = it.loadTags().toMutableSet()
            _links.value.flatMap { link -> link.tags }.forEach { tag ->
                savedTags.add(tag.trim().lowercase())
            }
            _tags.value = savedTags.sorted()
            if (savedTags.isNotEmpty()) storage.saveTags(_tags.value)
        }
    }

    /**
     * Un seul envoi au serveur à la fois, même depuis deux ViewModel (l'app et
     * les fonctions appelées par Gemini partagent ce dépôt) : une idée créée
     * ici ne doit pas partir deux fois.
     */
    val pushLock = Mutex()

    private val _pending = MutableStateFlow(storage?.loadPending() ?: PendingChanges())
    /** Changements faits ici et pas encore reçus par le serveur. */
    val pending: PendingChanges get() = _pending.value

    fun updatePending(change: (PendingChanges) -> PendingChanges) {
        synchronized(_pending) {
            _pending.value = change(_pending.value)
            storage?.savePending(_pending.value)
        }
    }

    /** Remplace les listes après une synchronisation (sans les effets de bord de deleteFolder). */
    fun replaceAll(links: List<Link>, folders: List<Folder>, children: List<Child>) {
        _links.value = links
        _folders.value = folders
        _children.value = children
        storage?.saveLinks(links)
        storage?.saveFolders(folders)
        storage?.saveChildren(children)
        links.flatMap { it.tags }.forEach { addTag(it) }
    }

    /** Idée créée ici et reçue par le serveur : elle prend l'identifiant du serveur. */
    fun replaceLinkId(old: String, new: String) {
        _links.value = _links.value.map { if (it.id == old) it.copy(id = new) else it }
        storage?.saveLinks(_links.value)
        // Une recette choisie pour les courses reste choisie sous son nouvel identifiant
        if (old in _shopping.value.recipes) updateShopping { it.copy(recipes = it.recipes - old + new) }
    }

    private val _shopping = MutableStateFlow(storage?.loadShopping() ?: ShoppingSelection())
    /** Liste de courses : recettes choisies et lignes cochées. */
    val shopping: StateFlow<ShoppingSelection> = _shopping.asStateFlow()

    fun updateShopping(change: (ShoppingSelection) -> ShoppingSelection) {
        synchronized(_shopping) {
            _shopping.value = change(_shopping.value)
            storage?.saveShopping(_shopping.value)
        }
    }

    /** Dossier reçu par le serveur : ses idées le suivent sous son nouvel identifiant. */
    fun replaceFolderId(old: String, new: String) {
        _folders.value = _folders.value.map { if (it.id == old) it.copy(id = new) else it }
        _links.value = _links.value.map { if (it.folderId == old) it.copy(folderId = new) else it }
        storage?.saveFolders(_folders.value)
        storage?.saveLinks(_links.value)
    }

    /** Enfant reçu par le serveur : les dossiers « pour » lui le suivent. */
    fun replaceChildId(old: String, new: String) {
        _children.value = _children.value.map { if (it.id == old) it.copy(id = new) else it }
        _folders.value = _folders.value.map { f -> if (old in f.childIds) f.copy(childIds = f.childIds.map { if (it == old) new else it }) else f }
        storage?.saveChildren(_children.value)
        storage?.saveFolders(_folders.value)
    }

    private val imageChecks: MutableMap<String, Long> by lazy {
        storage?.loadImageChecks()?.toMutableMap() ?: mutableMapOf()
    }

    /** Dernière recherche d'image pour cette idée (ms), 0 si jamais tentée. */
    fun imageCheckedAt(linkId: String): Long = synchronized(imageChecks) { imageChecks[linkId] ?: 0L }

    fun markImageChecked(linkId: String, at: Long = System.currentTimeMillis()) {
        synchronized(imageChecks) {
            imageChecks[linkId] = at
            // On n'en garde que pour les idées existantes
            val ids = _links.value.mapTo(HashSet()) { it.id }
            imageChecks.keys.retainAll(ids + linkId)
            storage?.saveImageChecks(imageChecks.toMap())
        }
    }

    fun addLink(link: Link) {
        _links.value = _links.value + link
        storage?.saveLinks(_links.value)
        link.tags.forEach { addTag(it) }
    }

    fun deleteLink(id: String) {
        _links.value = _links.value.filter { it.id != id }
        storage?.saveLinks(_links.value)
    }

    fun updateLink(updated: Link) {
        _links.value = _links.value.map { if (it.id == updated.id) updated else it }
        storage?.saveLinks(_links.value)
        updated.tags.forEach { addTag(it) }
    }

    fun addFolder(folder: Folder) {
        _folders.value = _folders.value + folder
        storage?.saveFolders(_folders.value)
    }

    fun deleteFolder(id: String) {
        _folders.value = _folders.value.filter { it.id != id }
        _links.value = _links.value.map { if (it.folderId == id) it.copy(folderId = null) else it }
        storage?.saveFolders(_folders.value)
        storage?.saveLinks(_links.value)
    }

    fun updateFolder(folder: Folder) {
        _folders.value = _folders.value.map { if (it.id == folder.id) folder else it }
        storage?.saveFolders(_folders.value)
    }

    fun addTag(tag: String) {
        val trimmed = tag.trim().lowercase()
        if (trimmed.isNotEmpty() && !_tags.value.contains(trimmed)) {
            _tags.value = (_tags.value + trimmed).sorted()
            storage?.saveTags(_tags.value)
        }
    }

    fun deleteTag(tag: String) {
        _tags.value = _tags.value.filter { it != tag }
        storage?.saveTags(_tags.value)
    }

    fun addChild(child: Child) {
        _children.value = _children.value + child
        storage?.saveChildren(_children.value)
    }

    fun updateChild(updated: Child) {
        _children.value = _children.value.map { if (it.id == updated.id) updated else it }
        storage?.saveChildren(_children.value)
    }

    fun deleteChild(id: String) {
        _children.value = _children.value.filter { it.id != id }
        storage?.saveChildren(_children.value)
    }

    /** Vide toutes les données locales (pour forcer un refresh complet) */
    fun clearAll() {
        _links.value = emptyList()
        _folders.value = emptyList()
        _children.value = emptyList()
        _tags.value = emptyList()
        _pending.value = PendingChanges()
        storage?.savePending(PendingChanges())
        storage?.saveLinks(emptyList())
        storage?.saveFolders(emptyList())
        storage?.saveChildren(emptyList())
        storage?.saveTags(emptyList())
    }

    fun searchLinks(
        query: String = "",
        category: LinkCategory? = null,
        folderId: String? = null,
        maxAgeMonths: Int? = null
    ): List<Link> {
        return _links.value.filter { link ->
            val matchesQuery = query.isEmpty() ||
                link.title.contains(query, ignoreCase = true) ||
                link.description.contains(query, ignoreCase = true) ||
                link.location.contains(query, ignoreCase = true) ||
                link.tags.any { it.contains(query, ignoreCase = true) }
            val matchesCategory = category == null || link.category == category
            val matchesFolder = folderId == null || link.folderId == folderId
            val matchesAge = maxAgeMonths == null || link.ageRange.isBlank() ||
                parseMaxAgeMonths(link.ageRange) >= maxAgeMonths
            matchesQuery && matchesCategory && matchesFolder && matchesAge
        }.reversed() // plus récents en premier
    }

    /**
     * Extrait l'âge max en MOIS d'une chaîne comme :
     * "3-6 mois", "18 mois", "3-6 ans", "6+", "12", "3 ans"
     */
    private fun parseMaxAgeMonths(ageRange: String): Int {
        val lower = ageRange.lowercase()
        val isMois = lower.contains("mois")
        val numbers = Regex("\\d+").findAll(lower).map { it.value.toInt() }.toList()
        val maxNum = numbers.maxOrNull() ?: return Int.MAX_VALUE
        // Si "mois" est mentionné, la valeur est déjà en mois
        return if (isMois) maxNum else maxNum * 12
    }
}
