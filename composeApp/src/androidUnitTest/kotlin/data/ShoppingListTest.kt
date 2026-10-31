package data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

/** Mêmes cas que le web (frontend/src/lib/shopping.test.ts). */
class ShoppingListTest {
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
}
