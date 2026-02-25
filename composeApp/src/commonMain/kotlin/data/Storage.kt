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
}
