package data

import java.text.Collator
import java.text.Normalizer
import java.util.Locale
import kotlin.math.roundToLong

/**
 * Liste de courses : ingrédients des recettes choisies, regroupés par produit
 * avec leurs quantités additionnées (« 250 g de farine » + « 100 g de farine »
 * = « 350 g de farine »). Même logique sur le web : frontend/src/lib/shopping.ts
 * (shopping.test.ts ↔ ShoppingListTest, garder les mêmes cas).
 */
/** Liste de courses : recettes choisies et lignes cochées (gardées sur l'appareil). */
data class ShoppingSelection(val recipes: Set<String> = emptySet(), val checked: Set<String> = emptySet())

object ShoppingList {
    data class Recipe(val title: String, val ingredients: List<String>)

    /** [key] identifie la ligne pour la cocher ; il change quand la quantité change. */
    data class Item(val key: String, val label: String, val from: List<String>)

    data class Parsed(val qty: Double?, val unit: String, val name: String)

    // Fin de mot explicite (comme sur le web) : « 4 gésiers » n'est pas « 4 g »
    private const val END = "(?=[\\s.,;:()]|$)"
    private fun unit(pattern: String) = Regex("^(?:$pattern)$END")

    // Unités : famille (g et ml se convertissent entre elles) et facteur vers la base
    private val UNITS: List<Triple<Regex, String, Double>> = listOf(
        Triple(unit("kg|kilos?|kilogrammes?"), "g", 1000.0),
        Triple(unit("mg|milligrammes?"), "g", 0.001),
        Triple(unit("g|gr|grammes?"), "g", 1.0),
        Triple(unit("dl|décilitres?"), "ml", 100.0),
        Triple(unit("cl|centilitres?"), "ml", 10.0),
        Triple(unit("ml|millilitres?"), "ml", 1.0),
        Triple(unit("l|litres?"), "ml", 1000.0),
        Triple(unit("c\\.?\\s*à\\s*soupe|c\\.?\\s*à\\s*s\\.?|càs|cs|cuil(?:\\.|lères?|lerées?)?\\s*à\\s*soupe|tbsp|tablespoons?"), "c. à soupe", 1.0),
        Triple(unit("c\\.?\\s*à\\s*café|c\\.?\\s*à\\s*c\\.?|càc|cc|cuil(?:\\.|lères?|lerées?)?\\s*à\\s*café|tsp|teaspoons?"), "c. à café", 1.0),
        Triple(unit("pincées?"), "pincée", 1.0),
        Triple(unit("sachets?"), "sachet", 1.0),
        Triple(unit("tranches?"), "tranche", 1.0),
        Triple(unit("gousses?"), "gousse", 1.0),
        Triple(unit("boîtes?|boites?"), "boîte", 1.0),
        Triple(unit("verres?"), "verre", 1.0),
        Triple(unit("tasses?|cups?"), "tasse", 1.0),
        Triple(unit("pots?"), "pot", 1.0),
        Triple(unit("bottes?"), "botte", 1.0),
        Triple(unit("brins?"), "brin", 1.0),
        Triple(unit("feuilles?"), "feuille", 1.0),
        Triple(unit("morceaux|morceau"), "morceau", 1.0),
    )

    private val PLURAL_UNITS = mapOf("morceau" to "morceaux", "c. à soupe" to "c. à soupe", "c. à café" to "c. à café")
    // h aspiré : pas d'élision (« de haricots »)
    private val H_ASPIRE = listOf("haricot", "hachis", "homard", "hareng", "houblon")
    private val FRACTIONS = mapOf('½' to 0.5, '¼' to 0.25, '¾' to 0.75, '⅓' to 1.0 / 3, '⅔' to 2.0 / 3, '⅛' to 0.125)

    private val quantity = Regex("^(\\d+\\s*[½¼¾⅓⅔⅛]|\\d+\\s*/\\s*\\d+|\\d+(?:[.,]\\d+)?|[½¼¾⅓⅔⅛])\\s*")
    private val range = Regex("^[-–]|^à\\s*\\d")
    private val linkWord = Regex("^\\s*(?:de |d'|d’|du |des )", RegexOption.IGNORE_CASE)
    private val leadingParens = Regex("^\\s*\\([^)]*\\)")
    private val parens = Regex("\\([^)]*\\)")
    private val spaces = Regex("\\s+")
    private val collator: Collator = Collator.getInstance(Locale.FRENCH)

    /** Enlève accents, ligatures, parenthèses et pluriels simples pour comparer les noms. */
    fun ingredientKey(name: String): String =
        Normalizer.normalize(name.lowercase().replace("œ", "oe").replace("æ", "ae"), Normalizer.Form.NFD)
            .replace(Regex("[\\u0300-\\u036f]"), "")
            .replace(parens, " ")
            .replace(Regex("[^a-z0-9' -]"), " ")
            .split(spaces)
            .filter { it.isNotEmpty() }
            .joinToString(" ") { w -> if (w.length > 3 && (w.endsWith("s") || w.endsWith("x"))) w.dropLast(1) else w }

    private fun parseNumber(s: String): Double? {
        if (s.length == 1) FRACTIONS[s[0]]?.let { return it }
        Regex("^(\\d+)([½¼¾⅓⅔⅛])$").find(s)?.let { return it.groupValues[1].toDouble() + FRACTIONS.getValue(it.groupValues[2][0]) }
        Regex("^(\\d+)/(\\d+)$").find(s)?.let {
            val d = it.groupValues[2].toDouble()
            return if (d != 0.0) it.groupValues[1].toDouble() / d else null
        }
        return s.replace(",", ".").toDoubleOrNull()
    }

    fun parseIngredient(line: String): Parsed {
        // Espaces insécables (fréquentes sur les sites de recettes) : des espaces simples
        val text = line.replace(' ', ' ').replace(' ', ' ').trim()
        val m = quantity.find(text)
        var qty: Double? = null
        var unit = ""
        var rest = text
        // Une fourchette (« 2-3 », « 2 à 3 ») reste telle quelle
        if (m != null && !range.containsMatchIn(text.substring(m.value.length))) {
            qty = parseNumber(m.groupValues[1].replace(spaces, ""))
            rest = text.substring(m.value.length)
            val lower = rest.lowercase()
            var factor = 1.0
            for ((re, u, f) in UNITS) {
                val um = re.find(lower) ?: continue
                unit = u
                factor = f
                rest = rest.substring(um.value.length)
                break
            }
            qty = qty?.times(factor)
            rest = linkWord.replaceFirst(leadingParens.replaceFirst(rest, ""), "")
        }
        val name = rest.split(",")[0].replace(parens, " ").replace(spaces, " ").trim()
        return Parsed(qty, unit, name.ifEmpty { text })
    }

    private fun formatNumber(n: Double): String {
        val r = (n * 100).roundToLong() / 100.0
        return (if (r == Math.floor(r)) r.toLong().toString() else r.toString()).replace(".", ",")
    }

    private fun formatQty(qty: Double, unit: String): String = when (unit) {
        "g" -> if (qty >= 1000) "${formatNumber(qty / 1000)} kg" else "${formatNumber(qty)} g"
        "ml" -> when {
            qty >= 1000 -> "${formatNumber(qty / 1000)} l"
            qty >= 10 && qty.roundToLong() % 10 == 0L -> "${formatNumber(qty / 10)} cl"
            else -> "${formatNumber(qty)} ml"
        }
        "" -> formatNumber(qty)
        else -> "${formatNumber(qty)} ${if (qty > 1) PLURAL_UNITS[unit] ?: "${unit}s" else unit}"
    }

    private fun elides(s: String): Boolean {
        val k = ingredientKey(s)
        return k.isNotEmpty() && k[0] in "aeiouyh" && H_ASPIRE.none { k.startsWith(it) }
    }

    // Ordre des quantités : poids et volumes, autres unités, puis à l'unité
    private fun unitRank(unit: String) = when {
        unit == "g" || unit == "ml" -> 0
        unit.isNotEmpty() -> 1
        else -> 2
    }

    private class Group(val key: String) {
        val lines = mutableListOf<String>()
        val names = mutableListOf<String>()
        val totals = linkedMapOf<String, Double>() // famille → quantité (dans l'ordre d'apparition)
        val from = mutableListOf<String>()
    }

    /** Regroupe les ingrédients des recettes choisies, triés par nom. */
    fun build(recipes: List<Recipe>): List<Item> {
        val groups = linkedMapOf<String, Group>()
        for (r in recipes) {
            for (raw in r.ingredients) {
                val line = raw.trim()
                if (line.isEmpty()) continue
                val p = parseIngredient(line)
                val key = ingredientKey(p.name).ifEmpty { ingredientKey(line) }
                if (key.isEmpty()) continue
                val g = groups.getOrPut(key) { Group(key) }
                g.lines += line
                g.names += p.name
                p.qty?.let { g.totals[p.unit] = (g.totals[p.unit] ?: 0.0) + it }
                if (r.title !in g.from) g.from += r.title
            }
        }
        return groups.values.map { g ->
            val label = when {
                // Une seule ligne : telle qu'écrite dans la recette
                g.lines.size == 1 -> g.lines[0]
                g.totals.isEmpty() -> g.names[0]
                else -> {
                    val count = g.totals[""] ?: 0.0
                    // À l'unité, au pluriel : la variante du nom au pluriel si la recette l'écrit ainsi
                    val picked = if (count > 1) g.names.firstOrNull { it.lowercase().endsWith("s") || it.lowercase().endsWith("x") } ?: g.names[0] else g.names[0]
                    val name = picked.replaceFirstChar { it.lowercase() }
                    // « 250 g + 2 c. à soupe de farine », « 100 g + 1 oignon »
                    val parts = g.totals.entries.sortedBy { unitRank(it.key) }.map { formatQty(it.value, it.key) }
                    val glue = if (count > 0) " " else if (elides(name)) " d'" else " de "
                    parts.joinToString(" + ") + glue + name
                }
            }.replaceFirstChar { it.uppercase() }
            Item("${g.key}|$label", label, g.from.toList())
        }.sortedWith { a, b -> collator.compare(a.key, b.key) }
    }
}
