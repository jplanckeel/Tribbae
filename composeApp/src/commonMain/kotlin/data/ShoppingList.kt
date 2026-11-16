package data

import java.text.Collator
import java.text.Normalizer
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToLong

/**
 * Liste de courses : ingrédients des recettes choisies, regroupés par produit
 * avec leurs quantités additionnées (« 250 g de farine » + « 100 g de farine »
 * = « 350 g de farine »), recalculées selon les parts, rangées par rayon.
 * Même logique sur le web : frontend/src/lib/shopping.ts (shopping.test.ts ↔
 * ShoppingListTest, garder les mêmes cas).
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
        Normalizer.normalize(name.lowercase().replace(Regex("[’‘`]"), "'").replace("œ", "oe").replace("æ", "ae"), Normalizer.Form.NFD)
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

    /**
     * Recettes de la liste de courses : celles qui ont des ingrédients, et à part
     * celles qui n'en ont pas encore, à compléter. Mêmes cas que `pickRecipes` du web.
     */
    fun pick(links: List<Link>): Pair<List<Link>, List<Link>> {
        val (ready, missing) = links.filter { it.category == LinkCategory.RECETTE }
            .partition { r -> r.ingredients.any { it.isNotBlank() } }
        return ready to missing
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

    // -----------------------------------------------------------------------
    // « Les courses » partagées : parts, rayons, placard, articles libres.
    // Le serveur garde les décisions de la tribu (menu, articles, cases cochées,
    // quantités et rayons choisis à la main, placard) ; la liste en est déduite ici.
    // -----------------------------------------------------------------------

    data class Aisle(val id: String, val name: String)

    /** Rayons, dans l'ordre d'un supermarché (ordre par défaut de la tribu). */
    val AISLES = listOf(
        Aisle("fruits-legumes", "Fruits et légumes"),
        Aisle("boulangerie", "Boulangerie"),
        Aisle("boucherie", "Boucherie, poisson"),
        Aisle("cremerie", "Crèmerie et œufs"),
        Aisle("epicerie", "Épicerie"),
        Aisle("surgeles", "Surgelés"),
        Aisle("boissons", "Boissons"),
        Aisle("bebe-hygiene", "Bébé et hygiène"),
        Aisle("maison", "Maison"),
        Aisle("autres", "Autres"),
    )

    fun isAisle(id: String) = AISLES.any { it.id == id }
    fun aisleName(id: String) = AISLES.firstOrNull { it.id == id }?.name ?: "Autres"

    // Petit dictionnaire de mots-clés. Le mot le plus long qui apparaît dans le nom
    // l'emporte : « lait de coco » (épicerie) avant « lait » (crèmerie).
    private val AISLE_WORDS = linkedMapOf(
        "fruits-legumes" to "fruit|légume|pomme|pomme de terre|patate douce|poire|banane|orange|citron|clémentine|mandarine|pamplemousse|fraise|framboise|myrtille|cerise|abricot|pêche|prune|raisin|kiwi|ananas|mangue|melon|pastèque|avocat|tomate|tomate cerise|carotte|courgette|aubergine|poivron|concombre|salade|laitue|mâche|roquette|épinard|chou|chou-fleur|brocoli|poireau|oignon|échalote|ail|potimarron|potiron|citrouille|courge|butternut|champignon|haricot vert|navet|radis|betterave|céleri|fenouil|endive|artichaut|asperge|panais|persil|coriandre|basilic|ciboulette|menthe|gingembre",
        "boulangerie" to "pain|baguette|brioche|pain de mie|pain de campagne|croissant|pain au chocolat|viennoiserie",
        "boucherie" to "viande|boeuf|veau|porc|agneau|poulet|blanc de poulet|dinde|canard|magret|lapin|lardon|jambon|bacon|saucisse|saucisson|chorizo|merguez|steak|steak haché|viande hachée|escalope|rôti|gésier|poisson|saumon|cabillaud|colin|truite|thon|crevette|moule|calamar|noix de saint-jacques",
        "cremerie" to "lait|beurre|crème|crème fraîche|oeuf|yaourt|fromage|fromage blanc|petit suisse|emmental|gruyère|parmesan|mozzarella|comté|chèvre|feta|ricotta|mascarpone|camembert|reblochon|raclette|pâte feuilletée|pâte brisée|pâte sablée|pâte à pizza",
        "epicerie" to "farine|sucre|sel|poivre|huile|vinaigre|moutarde|riz|pâtes|spaghetti|tagliatelle|coquillette|penne|lasagne|feuille de lasagne|semoule|quinoa|boulgour|lentille|pois chiche|haricot rouge|haricot blanc|conserve|coulis|coulis de tomate|concentré de tomate|sauce tomate|tomate pelée|bouillon|cube de bouillon|chocolat|cacao|café|thé|miel|confiture|pâte à tartiner|levure|vanille|sucre vanillé|cannelle|muscade|noix de muscade|cumin|curry|paprika|épice|herbes de provence|thym|laurier|origan|amande|noisette|noix|cerneau de noix|cacahuète|raisin sec|biscuit|céréale|flocon d'avoine|compote|lait de coco|crème de coco|maïzena|fécule|chapelure|olive|cornichon|câpre|sauce soja|ketchup|mayonnaise|gélatine|sirop|bicarbonate",
        "surgeles" to "surgelé|glace|sorbet|frite|poêlée",
        "boissons" to "eau|eau gazeuse|jus|jus d'orange|soda|limonade|vin|vin blanc|vin rouge|bière|cidre",
        "bebe-hygiene" to "couche|lingette|lait infantile|petit pot|savon|shampoing|gel douche|dentifrice|brosse à dents|déodorant|coton|mouchoir|crème solaire",
        "maison" to "papier toilette|essuie-tout|sopalin|liquide vaisselle|pastille lave-vaisselle|lessive|adoucissant|éponge|sac poubelle|nettoyant|javel|film alimentaire|papier aluminium|papier cuisson|pile|ampoule",
    )

    private val AISLE_RULES: List<Pair<String, String>> by lazy {
        AISLE_WORDS.flatMap { (aisle, words) -> words.split("|").map { ingredientKey(it) to aisle } }.filter { it.first.isNotEmpty() }
    }

    // « d'ail » se lit « d' ail » : un mot-clé se trouve aussi après une élision
    private fun spaced(s: String) = " ${s.replace("'", "' ")} "
    private fun hasWords(product: String, words: String) = spaced(product).contains(spaced(words))

    /** Rayon d'un produit (clé de ingredientKey) d'après le dictionnaire ; « autres » sinon. */
    fun aisleOf(product: String): String {
        var best = "autres"
        var length = 0
        for ((w, aisle) in AISLE_RULES) {
            if (w.length > length && hasWords(product, w)) {
                best = aisle
                length = w.length
            }
        }
        return best
    }

    // Produits de base qu'on a souvent déjà : la liste demande « Déjà dans le placard ? »
    private val STAPLES: Set<String> by lazy {
        "sel|gros sel|fleur de sel|sel et poivre|poivre|poivre noir|huile|huile d'olive|huile de tournesol|huile de colza|huile neutre|vinaigre|vinaigre balsamique|farine|farine de blé|sucre|sucre en poudre|sucre roux|sucre glace|sucre vanillé|cassonade|levure|levure chimique|levure boulangère|bicarbonate|maïzena|muscade|noix de muscade|cannelle|cumin|curry|paprika|piment d'espelette|herbes de provence|thym|laurier|origan|moutarde|eau|eau tiède|eau froide|eau chaude"
            .split("|").map { ingredientKey(it) }.toSet()
    }

    /** Produit de base (sel, huile, farine, épices…) qu'on a probablement au placard. */
    fun isStaple(product: String) = product in STAPLES

    // Unités qu'on achète à la pièce : arrondies au-dessus quand les parts changent
    private val WHOLE_UNITS = setOf("", "sachet", "tranche", "gousse", "boîte", "pot", "botte", "brin", "feuille", "morceau")

    /**
     * Quantité recalculée pour d'autres parts (factor = parts choisies / parts de la
     * recette), arrondie : à la pièce au-dessus, poids et volumes au 5 près au-delà
     * de 50, le reste à la demie. Une pincée reste une pincée.
     */
    fun scaleQty(qty: Double, factor: Double, unit: String): Double {
        if (factor == 1.0 || unit == "pincée") return qty
        val q = qty * factor
        return when {
            unit in WHOLE_UNITS -> maxOf(1.0, ceil(q - 0.01))
            (unit == "g" || unit == "ml") && q >= 50 -> (q / 5).roundToLong() * 5.0
            else -> maxOf(0.5, (q * 2).roundToLong() / 2.0)
        }
    }

    /** Une recette au menu, avec les parts choisies et celles prévues par la recette. */
    data class MenuRecipe(val title: String, val ingredients: List<String>, val baseServings: Int, val servings: Int)

    /** Un article ajouté à la main. */
    data class FreeItem(val id: String, val label: String, val quantity: String = "", val aisle: String = "", val addedBy: String = "")

    data class Portion(val recipe: String, val qty: String)

    /**
     * Une ligne de la liste. [key] la coche : produit + quantité (« item:<id> » pour
     * un article libre) ; [product] (ingredientKey du nom) est la clé des quantités,
     * rayons, placard et retraits.
     */
    data class Line(
        val key: String,
        val product: String,
        val name: String,
        val qty: String,
        val aisle: String,
        val from: List<Portion>,
        val itemId: String? = null,
        val addedBy: String? = null,
        val staple: Boolean = false,
        val edited: Boolean = false,
    )

    private class Totals(val product: String) {
        val names = mutableListOf<String>()
        val totals = linkedMapOf<String, Double>()
        // Recette → quantités par unité, dans l'ordre du menu
        val perRecipe = linkedMapOf<String, LinkedHashMap<String, Double>>()
    }

    private fun totalsLabel(totals: Map<String, Double>) =
        totals.entries.sortedBy { unitRank(it.key) }.joinToString(" + ") { formatQty(it.value, it.key) }

    private fun capitalize(s: String) = s.replaceFirstChar { it.uppercase() }

    /**
     * Lignes de la liste, avant le rangement : ingrédients des recettes du menu
     * recalculés pour leurs parts et additionnés par produit, puis articles libres.
     * Les rayons et quantités choisis à la main par la tribu s'appliquent.
     */
    fun buildLines(
        menu: List<MenuRecipe>,
        items: List<FreeItem> = emptyList(),
        quantities: Map<String, String> = emptyMap(),
        aisles: Map<String, String> = emptyMap(),
    ): List<Line> {
        fun aisleFor(product: String, own: String = ""): String {
            val picked = own.ifEmpty { aisles[product] ?: "" }
            return if (isAisle(picked)) picked else aisleOf(product)
        }
        val groups = linkedMapOf<String, Totals>()
        for (r in menu) {
            val factor = if (r.baseServings > 0 && r.servings > 0) r.servings.toDouble() / r.baseServings else 1.0
            for (raw in r.ingredients) {
                val line = raw.trim()
                if (line.isEmpty()) continue
                val p = parseIngredient(line)
                val product = ingredientKey(p.name).ifEmpty { ingredientKey(line) }
                if (product.isEmpty()) continue
                val g = groups.getOrPut(product) { Totals(product) }
                g.names += p.name
                val mine = g.perRecipe.getOrPut(r.title) { linkedMapOf() }
                p.qty?.let {
                    val qty = scaleQty(it, factor, p.unit)
                    g.totals[p.unit] = (g.totals[p.unit] ?: 0.0) + qty
                    mine[p.unit] = (mine[p.unit] ?: 0.0) + qty
                }
            }
        }
        val lines = groups.values.map { g ->
            val count = g.totals[""] ?: 0.0
            // À l'unité, au pluriel : la variante du nom au pluriel si la recette l'écrit ainsi
            val picked = if (count > 1) g.names.firstOrNull { it.lowercase().endsWith("s") || it.lowercase().endsWith("x") } ?: g.names[0] else g.names[0]
            val typed = quantities[g.product]
            val qty = typed ?: totalsLabel(g.totals)
            Line(
                key = "${g.product}|$qty",
                product = g.product,
                name = capitalize(picked),
                qty = qty,
                aisle = aisleFor(g.product),
                from = g.perRecipe.map { (recipe, totals) -> Portion(recipe, totalsLabel(totals)) },
                staple = isStaple(g.product),
                edited = typed != null,
            )
        }.toMutableList()
        for (it in items) {
            val name = it.label.trim()
            if (name.isEmpty()) continue
            val product = ingredientKey(name)
            lines += Line(
                key = "item:${it.id}",
                product = product,
                name = capitalize(name),
                qty = it.quantity.trim(),
                aisle = aisleFor(product, it.aisle),
                from = emptyList(),
                itemId = it.id,
                addedBy = it.addedBy,
            )
        }
        return lines
    }

    data class AisleGroup(val aisle: String, val name: String, val lines: List<Line>)

    data class Arranged(
        /** À prendre, par rayon dans l'ordre de la tribu. */
        val groups: List<AisleGroup>,
        /** Dans le panier (cochées), dans l'ordre des rayons. */
        val cart: List<Line>,
        /** Produits de base des recettes, pour « Déjà dans le placard ? ». */
        val staples: List<Line>,
        /** Produits de base qu'on a : sortis de la liste. */
        val have: List<Line>,
        /** Ingrédients de recette retirés de la liste, à rétablir. */
        val removed: List<Line>,
        val remaining: Int,
        val total: Int,
    )

    /** Ordre des rayons : celui de la tribu, complété par l'ordre par défaut. */
    fun aisleOrder(order: List<String> = emptyList()): List<String> {
        val own = order.filter { isAisle(it) }.distinct()
        return own + AISLES.map { it.id }.filter { it !in own }
    }

    /**
     * Range les lignes : retire celles qu'on a au placard ou qu'on a retirées,
     * sépare le panier (lignes cochées) et groupe le reste par rayon, triées par
     * produit dans chaque rayon.
     */
    fun arrange(
        lines: List<Line>,
        checked: Set<String> = emptySet(),
        pantry: Set<String> = emptySet(),
        removed: Set<String> = emptySet(),
        aisleOrder: List<String> = emptyList(),
    ): Arranged {
        val order = aisleOrder(aisleOrder)
        val sorted = lines.sortedWith(compareBy<Line>({ order.indexOf(it.aisle) }, { it.product }))
        fun gone(l: Line) = l.itemId == null && l.product in removed
        val kept = sorted.filterNot(::gone)
        val staples = kept.filter { it.staple }
        val onList = kept.filterNot { it.staple && it.product in pantry }
        val todo = onList.filter { it.key !in checked }
        val groups = mutableListOf<AisleGroup>()
        for (l in todo) {
            val last = groups.lastOrNull()
            if (last?.aisle == l.aisle) groups[groups.lastIndex] = last.copy(lines = last.lines + l)
            else groups += AisleGroup(l.aisle, aisleName(l.aisle), listOf(l))
        }
        return Arranged(
            groups = groups,
            cart = onList.filter { it.key in checked },
            staples = staples,
            have = staples.filter { it.product in pantry },
            removed = sorted.filter(::gone),
            remaining = todo.size,
            total = onList.size,
        )
    }

    data class Suggestion(val label: String, val aisle: String)

    // Articles qu'on achète souvent sans recette : proposés tant qu'ils ne sont pas sur la liste
    private val FREQUENT = listOf("Lait", "Pain", "Œufs", "Yaourts", "Beurre", "Café", "Pommes", "Bananes", "Fromage", "Papier toilette", "Couches", "Liquide vaisselle")

    /** Suggestions « Souvent » : articles fréquents absents de la liste, avec leur rayon. */
    fun suggestions(lines: List<Line>, max: Int = 4): List<Suggestion> {
        val present = lines.map { it.product }.toSet()
        return FREQUENT.filter { ingredientKey(it) !in present }.take(max).map { Suggestion(it, aisleOf(ingredientKey(it))) }
    }

    /** La liste en texte, pour quelqu'un sans Tribbae : un rayon par ligne. */
    fun shareText(list: Arranged): String {
        val head = "Courses Tribbae — ${list.remaining} ${if (list.remaining > 1) "articles" else "article"}"
        val body = list.groups.map { g ->
            "${g.name} : " + g.lines.joinToString(", ") { l -> l.name.lowercase() + if (l.qty.isNotEmpty()) " (${l.qty})" else "" }
        }
        return (listOf(head) + body).joinToString("\n")
    }
}
