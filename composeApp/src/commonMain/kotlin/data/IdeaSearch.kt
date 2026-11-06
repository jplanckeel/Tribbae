package data

/** Recherche dans ses propres idées : mêmes cas que `searchMine` de frontend/src/lib/links.ts. */
object IdeaSearch {
    fun matches(link: Link, query: String): Boolean =
        (listOf(link.title, link.description, link.location) + link.tags).any { it.contains(query, ignoreCase = true) }

    /** Les idées trouvées, les plus récentes d'abord. Rien tant que le champ est vide. */
    fun mine(links: List<Link>, query: String): List<Link> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        return links.filter { matches(it, q) }.sortedByDescending { it.createdAt.ifEmpty { it.updatedAt } }
    }
}
