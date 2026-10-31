package data

/**
 * Interface de persistance. Implémentée côté Android avec SharedPreferences.
 */
interface Storage {
    fun saveLinks(links: List<Link>)
    fun loadLinks(): List<Link>
    fun saveFolders(folders: List<Folder>)
    fun loadFolders(): List<Folder>
    fun saveTags(tags: List<String>)
    fun loadTags(): List<String>
    fun saveChildren(children: List<Child>)
    fun loadChildren(): List<Child>
    fun saveToken(token: String)
    fun loadToken(): String?

    /** Date (ms) de la dernière recherche d'image par idée : évite de relancer les mêmes sites à chaque ouverture. */
    fun saveImageChecks(checks: Map<String, Long>) {}
    fun loadImageChecks(): Map<String, Long> = emptyMap()

    /** Changements pas encore reçus par le serveur (voir PendingChanges). */
    fun savePending(pending: PendingChanges) {}
    fun loadPending(): PendingChanges = PendingChanges()

    /** Liste de courses (voir ShoppingSelection). */
    fun saveShopping(selection: ShoppingSelection) {}
    fun loadShopping(): ShoppingSelection = ShoppingSelection()
}
