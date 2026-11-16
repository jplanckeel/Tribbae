package data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

/** Mêmes cas que le web (frontend/src/lib/shopping.test.ts). */
class ShoppingListTest {
    @Test
    fun picksRecipesAndThoseToComplete() {
        fun idea(id: String, category: LinkCategory, ingredients: List<String> = emptyList()) =
            Link(id = id, title = id, url = "", category = category, ingredients = ingredients)
        val (ready, missing) = ShoppingList.pick(
            listOf(
                idea("a", LinkCategory.RECETTE, listOf("200 g de farine")),
                idea("b", LinkCategory.IDEE, listOf("2 œufs")),
                idea("c", LinkCategory.RECETTE),
                idea("d", LinkCategory.RECETTE, listOf("  ")),
                idea("e", LinkCategory.ACTIVITE),
            )
        )
        assertEquals(listOf("a"), ready.map { it.id })
        assertEquals(listOf("c", "d"), missing.map { it.id })
    }

    private fun r(title: String, vararg ingredients: String) = ShoppingList.Recipe(title, ingredients.toList())
    private fun labels(vararg recipes: ShoppingList.Recipe) = ShoppingList.build(recipes.toList()).map { it.label }

    @Test
    fun parsesQuantityUnitAndName() {
        listOf(
            Triple("250 g de farine", 250.0, "g") to "farine",
            Triple("1 kg de pommes de terre", 1000.0, "g") to "pommes de terre",
            Triple("½ litre lait", 500.0, "ml") to "lait",
            Triple("50 cl de lait", 500.0, "ml") to "lait",
            Triple("1,5 l d'eau", 1500.0, "ml") to "eau",
            Triple("2 c. à soupe d'huile d'olive", 2.0, "c. à soupe") to "huile d'olive",
            Triple("1 cuillère à café de sel", 1.0, "c. à café") to "sel",
            Triple("30 ml (2 c. à soupe) de beurre", 30.0, "ml") to "beurre",
            Triple("1 oignon, haché", 1.0, "") to "oignon",
            Triple("1/2 citron", 0.5, "") to "citron",
            Triple("Blanc(s) de poulet", null, "") to "Blanc de poulet",
            Triple("Sel", null, "") to "Sel",
            Triple("2-3 tomates", null, "") to "2-3 tomates",
            Triple("1.5 cups all-purpose flour", 1.5, "tasse") to "all-purpose flour",
            Triple("4 gésiers de canard", 4.0, "") to "gésiers de canard",
            Triple("3 c. à s. de sucre", 3.0, "c. à soupe") to "sucre",
            Triple("2 gousses d'ail", 2.0, "gousse") to "ail",
            Triple("250 g de farine", 250.0, "g") to "farine",
        ).forEach { (case, name) ->
            val (line, qty, unit) = case
            val p = ShoppingList.parseIngredient(line)
            if (qty == null) assertNull(p.qty, line) else assertEquals(qty, p.qty!!, 1e-9, line)
            assertEquals(unit, p.unit, line)
            assertEquals(name, p.name, line)
        }
    }

    @Test
    fun keyIgnoresAccentsLigaturesParenthesesAndPlurals() {
        assertEquals(ShoppingList.ingredientKey("oeuf"), ShoppingList.ingredientKey("Œufs"))
        assertEquals(ShoppingList.ingredientKey("citron vert"), ShoppingList.ingredientKey("Citron(s) vert(s)"))
        assertEquals(ShoppingList.ingredientKey("creme fraiche"), ShoppingList.ingredientKey("Crème fraîche"))
    }

    @Test
    fun addsUpQuantitiesOfTheSameProduct() {
        assertEquals(
            listOf("350 g de farine", "1 l de lait", "6 oeufs"),
            labels(r("Crêpes", "250 g de farine", "4 oeufs", "½ litre lait"), r("Gâteau", "100 g de farine", "2 œufs", "50 cl de lait")),
        )
    }

    @Test
    fun keepsALineFromASingleRecipeAsWritten() {
        assertEquals(listOf("1 kg de pommes de terre, épluchées", "Sel"), labels(r("Gratin", "1 kg de pommes de terre, épluchées", "Sel")))
    }

    @Test
    fun convertsUnitsAndSwitchesToKiloOrLitre() {
        assertEquals(
            listOf("45 cl de crème", "45 ml d'huile", "1,1 kg de sucre"),
            labels(r("A", "600 g de sucre", "20 cl de crème", "30 ml d'huile"), r("B", "0,5 kg de sucre", "25 cl de crème", "15 ml d'huile")),
        )
    }

    @Test
    fun putsUnconvertibleUnitsSideBySide() {
        assertEquals(
            listOf("250 g + 2 c. à soupe de farine", "100 g + 1 oignon"),
            labels(r("A", "2 c. à soupe de farine"), r("B", "250 g de farine"), r("C", "1 oignon"), r("D", "100 g d'oignon")),
        )
    }

    @Test
    fun elidesBeforeAVowelOrMuteHButNotAspiratedH() {
        assertEquals(
            listOf("500 g de haricots verts", "3 c. à soupe d'huile"),
            labels(r("A", "200 g d'haricots verts", "1 c. à soupe d'huile"), r("B", "300 g de haricots verts", "2 c. à soupe d'huile")),
        )
    }

    @Test
    fun keepsOnlyTheNameWithoutQuantities() {
        assertEquals(listOf("Sel"), labels(r("A", "Sel"), r("B", "sel")))
    }

    @Test
    fun usesThePluralNameWhenARecipeWritesIt() {
        assertEquals(listOf("3 tomates"), labels(r("A", "1 tomate"), r("B", "2 tomates")))
    }

    @Test
    fun addsUpFractions() {
        assertEquals(listOf("1 citron"), labels(r("A", "⅓ citron"), r("B", "⅔ citron")))
    }

    @Test
    fun recordsSourceRecipesOnce() {
        val items = ShoppingList.build(listOf(r("Crêpes", "2 oeufs", "1 oeuf"), r("Gâteau", "3 oeufs")))
        assertEquals(1, items.size)
        assertEquals("6 oeufs", items[0].label)
        assertEquals(listOf("Crêpes", "Gâteau"), items[0].from)
    }

    @Test
    fun keyChangesWhenTheQuantityChanges() {
        val one = ShoppingList.build(listOf(r("A", "2 oeufs")))[0].key
        val two = ShoppingList.build(listOf(r("A", "2 oeufs"), r("B", "1 oeuf")))[0].key
        assertNotEquals(one, two)
    }

    @Test
    fun ignoresBlankLines() {
        assertEquals(listOf("Poivre"), labels(r("A", "", "  ", "Poivre")))
    }

    // « Les courses » partagées : mêmes cas que shopping.test.ts (web)
    private fun recipe(title: String, ingredients: List<String>, base: Int = 4, servings: Int = base) =
        ShoppingList.MenuRecipe(title, ingredients, base, servings)
    private fun show(lines: List<ShoppingList.Line>) = lines.map { if (it.qty.isNotEmpty()) "${it.name} · ${it.qty}" else it.name }
    private fun key(name: String) = ShoppingList.ingredientKey(name)

    @Test
    fun normalizesCurlyApostrophes() {
        assertEquals(key("huile d'olive"), key("Huile d’olive"))
    }

    @Test
    fun findsTheAisle() {
        listOf(
            "Pommes de terre" to "fruits-legumes",
            "pomme" to "fruits-legumes",
            "Lait" to "cremerie",
            "Lait de coco" to "epicerie",
            "Crème de coco" to "epicerie",
            "Crème fraîche" to "cremerie",
            "Œufs" to "cremerie",
            "Pain de campagne" to "boulangerie",
            "Blanc de poulet" to "boucherie",
            "Farine de châtaigne" to "epicerie",
            "Pâte feuilletée" to "cremerie",
            "Couches taille 4" to "bebe-hygiene",
            "Papier toilette" to "maison",
            "Gousse d'ail" to "fruits-legumes",
            "Quinoa soufflé" to "epicerie",
            "Bougie d'anniversaire" to "autres",
        ).forEach { (name, aisle) -> assertEquals(aisle, ShoppingList.aisleOf(key(name)), name) }
    }

    @Test
    fun completesTheTribeAisleOrder() {
        assertEquals(
            listOf("epicerie", "boulangerie", "fruits-legumes", "boucherie"),
            ShoppingList.aisleOrder(listOf("epicerie", "inconnu", "boulangerie", "epicerie")).take(4),
        )
        assertEquals(10, ShoppingList.aisleOrder().size)
    }

    @Test
    fun recognizesStaples() {
        assertTrue(ShoppingList.isStaple(key("Sel")))
        assertTrue(ShoppingList.isStaple(key("Huile d’olive")))
        assertTrue(ShoppingList.isStaple(key("Farine de blé")))
        assertFalse(ShoppingList.isStaple(key("Farine de châtaigne")))
        assertFalse(ShoppingList.isStaple(key("Beurre")))
    }

    @Test
    fun scalesAndRoundsQuantities() {
        listOf(
            Triple(3.0, 0.75, "") to 3.0,
            Triple(1.0, 1.5, "gousse") to 2.0,
            Triple(1.0, 0.3, "") to 1.0,
            Triple(125.0, 1.5, "g") to 190.0,
            Triple(10.0, 1.24, "g") to 12.5,
            Triple(250.0, 4.0 / 3, "ml") to 335.0,
            Triple(1.0, 1.3, "c. à soupe") to 1.5,
            Triple(2.0, 3.0, "pincée") to 2.0,
            Triple(0.5, 1.0, "") to 0.5,
        ).forEach { (input, expected) ->
            val (qty, factor, unit) = input
            assertEquals(expected, ShoppingList.scaleQty(qty, factor, unit), 0.001, "$qty × $factor $unit")
        }
    }

    @Test
    fun recomputesQuantitiesForTheChosenServings() {
        val lines = ShoppingList.buildLines(listOf(recipe("Crêpes", listOf("250 g de farine", "3 oeufs", "50 cl de lait", "1 pincée de sel"), 6, 9)))
        assertEquals(listOf("Farine · 375 g", "Oeufs · 5", "Lait · 75 cl", "Sel · 1 pincée"), show(lines))
    }

    @Test
    fun keepsQuantitiesAtTheRecipeServings() {
        val lines = ShoppingList.buildLines(listOf(recipe("A", listOf("⅓ citron")), recipe("B", listOf("⅔ citron"))))
        assertEquals(listOf("Citron · 1"), show(lines))
    }

    private val menu = listOf(
        recipe("Crêpes", listOf("50 cl de lait", "30 g de beurre", "2 c. à soupe d'huile"), 6, 6),
        recipe("Gratin", listOf("50 cl de lait", "20 g de beurre", "1 gousse d'ail", "Sel"), 4, 4),
    )

    @Test
    fun addsUpByProductAndKeepsThePerRecipeDetail() {
        val lines = ShoppingList.buildLines(menu)
        assertEquals(listOf("Lait · 1 l", "Beurre · 50 g", "Huile · 2 c. à soupe", "Ail · 1 gousse", "Sel"), show(lines))
        assertEquals(listOf(ShoppingList.Portion("Crêpes", "50 cl"), ShoppingList.Portion("Gratin", "50 cl")), lines[0].from)
        assertEquals("lait|1 l", lines[0].key)
        assertEquals("cremerie", lines[0].aisle)
        assertEquals(true, lines.firstOrNull { it.product == "sel" }?.staple)
    }

    @Test
    fun addsUpAProductListedTwiceInOneRecipe() {
        val lines = ShoppingList.buildLines(listOf(recipe("Gâteau", listOf("2 oeufs", "1 oeuf"))))
        assertEquals(listOf(ShoppingList.Portion("Gâteau", "3")), lines[0].from)
    }

    @Test
    fun appliesHandPickedQuantityAndAisle() {
        val lines = ShoppingList.buildLines(menu, quantities = mapOf("lait" to "2 bouteilles"), aisles = mapOf("beurre" to "epicerie", "ail" to "pas-un-rayon"))
        assertEquals("2 bouteilles", lines[0].qty)
        assertEquals("lait|2 bouteilles", lines[0].key)
        assertTrue(lines[0].edited)
        assertEquals("epicerie", lines[1].aisle)
        assertEquals("fruits-legumes", lines.first { it.product == "ail" }.aisle)
    }

    @Test
    fun addsFreeItemsWithTheirAisle() {
        val lines = ShoppingList.buildLines(
            emptyList(),
            listOf(
                ShoppingList.FreeItem("a1", "Couches taille 4", "1 paquet", "", "u2"),
                ShoppingList.FreeItem("a2", "bougies", "", "maison", "u1"),
                ShoppingList.FreeItem("a3", "  ", "", "", "u1"),
            ),
        )
        assertEquals(
            listOf(
                listOf("item:a1", "Couches taille 4", "1 paquet", "bebe-hygiene", "u2"),
                listOf("item:a2", "Bougies", "", "maison", "u1"),
            ),
            lines.map { listOf(it.key, it.name, it.qty, it.aisle, it.addedBy) },
        )
    }

    private val gratin = ShoppingList.buildLines(
        listOf(recipe("Gratin", listOf("1 kg de pommes de terre", "50 cl de lait", "1 pincée de muscade", "Sel", "Pain"))),
        listOf(ShoppingList.FreeItem("x", "Lessive", "", "", "u1")),
    )

    @Test
    fun arrangesByTheTribeAisleOrder() {
        val list = ShoppingList.arrange(gratin, aisleOrder = listOf("maison", "cremerie"))
        assertEquals(
            listOf(
                "Maison" to listOf("Lessive"),
                "Crèmerie et œufs" to listOf("Lait"),
                "Fruits et légumes" to listOf("Pommes de terre"),
                "Boulangerie" to listOf("Pain"),
                "Épicerie" to listOf("Muscade", "Sel"),
            ),
            list.groups.map { g -> g.name to g.lines.map { it.name } },
        )
        assertEquals(6, list.remaining)
        assertEquals(6, list.total)
        assertEquals(listOf("Muscade", "Sel"), list.staples.map { it.name })
    }

    @Test
    fun leavesOutWhatWeHaveAndRemovedAndPutsCheckedInTheCart() {
        val list = ShoppingList.arrange(gratin, checked = setOf("lait|50 cl", "item:x"), pantry = setOf("sel"), removed = setOf("pain", "lessive"))
        assertEquals(listOf("Lait", "Lessive"), list.cart.map { it.name })
        assertEquals(listOf("Sel"), list.have.map { it.name })
        assertEquals(listOf("Pain"), list.removed.map { it.name })
        assertEquals(listOf("Pommes de terre", "Muscade"), list.groups.flatMap { g -> g.lines.map { it.name } })
        assertEquals(2, list.remaining)
        assertEquals(4, list.total)
    }

    @Test
    fun unchecksALineWhoseQuantityChanged() {
        val more = ShoppingList.buildLines(listOf(recipe("Gratin", listOf("50 cl de lait"), 4, 8)))
        assertEquals(emptyList(), ShoppingList.arrange(more, checked = setOf("lait|50 cl")).cart)
    }

    @Test
    fun writesTheListAsText() {
        assertEquals(
            "Courses Tribbae — 4 articles\n" +
                "Fruits et légumes : pommes de terre (1 kg)\n" +
                "Boulangerie : pain\n" +
                "Crèmerie et œufs : lait (50 cl)\n" +
                "Maison : lessive",
            ShoppingList.shareText(ShoppingList.arrange(gratin, pantry = setOf("sel", "muscade"))),
        )
    }

    @Test
    fun suggestsFrequentItemsMissingFromTheList() {
        val lines = ShoppingList.buildLines(listOf(recipe("A", listOf("1 l de lait", "2 oeufs"))))
        assertEquals(
            listOf(
                ShoppingList.Suggestion("Pain", "boulangerie"),
                ShoppingList.Suggestion("Yaourts", "cremerie"),
                ShoppingList.Suggestion("Beurre", "cremerie"),
                ShoppingList.Suggestion("Café", "epicerie"),
            ),
            ShoppingList.suggestions(lines),
        )
    }

    // applyOp : mêmes cas que shopping.test.ts (web) et internal/shopping (serveur)
    private val recipes = mapOf(
        "r1" to ShoppingList.RecipeInfo("Crêpes", listOf("250 g de farine", "50 cl de lait"), servings = 6),
        "r2" to ShoppingList.RecipeInfo("Soupe", listOf("1 potimarron")),
    )
    private fun op(type: String, linkId: String = "", servings: Int = 0, id: String = "", label: String = "", quantity: String = "", aisle: String = "", key: String = "", value: String = "", order: List<String> = emptyList()) =
        ShoppingList.Op(type, linkId, servings, id, label, quantity, aisle, key, value, order)
    private fun run(vararg ops: ShoppingList.Op, from: ShoppingList.Shared = ShoppingList.Shared()) =
        ops.fold(from) { l, o -> ShoppingList.applyOp(l, o, "u1", 100) { recipes[it] } }

    @Test
    fun appliesOpsAddsRecipesWithServingsOnce() {
        val l = run(op("menu_add", "r1"), op("menu_add", "r2", servings = 2), op("menu_add", "r1"), op("menu_add", "inconnue"))
        assertEquals(listOf(listOf("r1", 6, 6, "u1"), listOf("r2", 4, 2, "u1")), l.menu.map { listOf(it.linkId, it.baseServings, it.servings, it.addedBy) })
        assertEquals(3, run(op("menu_add", "r1", servings = 3), from = l).menu[0].servings)
    }

    @Test
    fun appliesOpsChangesServingsAndRemovesRecipes() {
        val l = run(op("menu_add", "r1"), op("menu_servings", "r1", servings = 9), op("menu_servings", "r1", servings = 0))
        assertEquals(9, l.menu[0].servings)
        assertEquals(emptyList(), run(op("menu_remove", "r1"), from = l).menu)
    }

    @Test
    fun appliesOpsOnFreeItems() {
        var l = run(
            op("item_add", id = "a", label = "  Couches   taille 4 ", quantity = "1 paquet"),
            op("item_add", id = "a", label = "Doublon"),
            op("item_add", id = "b", label = "   "),
            op("check", key = "item:a"),
        )
        assertEquals(listOf(listOf("a", "Couches taille 4", "1 paquet", "u1")), l.items.map { listOf(it.id, it.label, it.quantity, it.addedBy) })
        l = run(op("item_update", id = "a", label = "Couches taille 5", quantity = "", aisle = "bebe-hygiene"), from = l)
        assertEquals(Triple("Couches taille 5", "", "bebe-hygiene"), l.items[0].let { Triple(it.label, it.quantity, it.aisle) })
        l = run(op("item_remove", id = "a"), from = l)
        assertEquals(emptyList(), l.items)
        assertEquals(emptyList(), l.checked)
    }

    @Test
    fun appliesOpsOnChecksValuesPantryAndRemovals() {
        val l = run(
            op("check", key = "lait|50 cl"),
            op("check", key = "farine|250 g"),
            op("uncheck", key = "farine|250 g"),
            op("quantity", key = "lait", value = "1 brique"),
            op("aisle", key = "lait", value = "epicerie"),
            op("aisle", key = "lait", value = ""),
            op("pantry_have", key = "sel"),
            op("pantry_have", key = "sel"),
            op("pantry_have", key = "huile"),
            op("pantry_out", key = "huile"),
            op("remove", key = "pain"),
            op("aisle_order", order = listOf("epicerie")),
        )
        assertEquals(listOf(ShoppingList.Check("lait|50 cl", "u1", 100)), l.checked)
        assertEquals(listOf(ShoppingList.Value("lait", "1 brique")), l.quantities)
        assertEquals(emptyList(), l.aisles)
        assertEquals(listOf("sel"), l.pantry)
        assertEquals(listOf("pain"), l.removed)
        assertEquals(listOf("epicerie"), l.aisleOrder)
        assertEquals(emptyList(), run(op("restore", key = "pain"), from = l).removed)
    }

    @Test
    fun appliesOpsOnlyTheShopperStopsShopping() {
        val l = run(op("shopping_start"))
        assertEquals("u1" to 100L, l.shopperId to l.shoppingSince)
        assertEquals("u1", ShoppingList.applyOp(l, op("shopping_stop"), "u2", 200).shopperId)
        assertEquals("", run(op("shopping_stop"), from = l).shopperId)
    }

    @Test
    fun appliesOpsFinishEmptiesTheCartAndKeepsMenuAndPantry() {
        val l = run(
            op("menu_add", "r2"),
            op("item_add", id = "a", label = "Lait"),
            op("item_add", id = "b", label = "Pain"),
            op("check", key = "item:a"),
            op("check", key = "potimarron|1"),
            op("pantry_have", key = "sel"),
            op("shopping_start"),
            op("finish"),
        )
        assertEquals(listOf(1, listOf("b"), emptyList<ShoppingList.Check>(), listOf("sel"), ""), listOf(l.menu.size, l.items.map { it.id }, l.checked, l.pantry, l.shopperId))
    }

    @Test
    fun appliesOpsClearKeepsPantryAndAisles() {
        val l = run(
            op("menu_add", "r1"),
            op("item_add", id = "a", label = "Lait"),
            op("quantity", key = "lait", value = "2"),
            op("aisle", key = "lait", value = "epicerie"),
            op("pantry_have", key = "sel"),
            op("remove", key = "farine"),
            op("aisle_order", order = listOf("maison")),
            op("clear"),
        )
        assertEquals(listOf(emptyList<Any>(), emptyList(), emptyList(), emptyList()), listOf(l.menu, l.items, l.quantities, l.removed))
        assertEquals(listOf(ShoppingList.Value("lait", "epicerie")), l.aisles)
        assertEquals(listOf("sel"), l.pantry)
        assertEquals(listOf("maison"), l.aisleOrder)
    }

    @Test
    fun showsTheResultingList() {
        val l = run(op("menu_add", "r1", servings = 3), op("check", key = "lait|25 cl"))
        val arranged = ShoppingList.viewOf(l).arranged
        assertEquals(listOf("Farine · 125 g"), arranged.groups.flatMap { show(it.lines) })
        assertEquals(listOf("Lait · 25 cl"), show(arranged.cart))
    }
}
