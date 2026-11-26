package data

import java.text.Collator
import java.util.Locale

/**
 * Tags des idées : compte et filtre (catégorie ouverte depuis l'accueil, onglet
 * Tags de « Mes idées »). Mêmes règles que tagCounts / tagFilter dans
 * frontend/src/lib/links.ts (TagsTest ↔ links.test.ts).
 */
object Tags {
    data class Filter(
        /** Tags des idées, du plus utilisé au moins utilisé. */
        val tags: List<Pair<String, Int>>,
        /** Tag retenu : null si aucun, ou s'il ne figure plus sur ces idées. */
        val active: String?,
        /** Idées portant le tag retenu, toutes sinon. */
        val links: List<Link>,
    )

    // Ordre alphabétique français, comme localeCompare(…, "fr") sur le web : « été » avant « zoo »
    private val french = Collator.getInstance(Locale.FRENCH)

    fun counts(links: List<Link>): List<Pair<String, Int>> =
        links.flatMap { it.tags }.groupingBy { it }.eachCount().toList()
            .sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenComparator { a, b -> french.compare(a.first, b.first) })

    fun filter(links: List<Link>, selected: String?): Filter {
        val tags = counts(links)
        val active = selected?.takeIf { s -> tags.any { it.first == s } }
        return Filter(tags, active, if (active == null) links else links.filter { active in it.tags })
    }
}
