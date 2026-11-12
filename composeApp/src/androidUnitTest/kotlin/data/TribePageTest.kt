package data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Mêmes règles que le web (frontend/src/lib/tribe.ts). */
class TribePageTest {
    @Test
    fun placesTheTribeAroundTheFire() {
        // Valeurs de référence calculées par ringPosition() côté web
        assertEquals(0 to -105, TribePage.ringPosition(0, 1))
        assertEquals(listOf(-95 to -44, -39 to -98, 39 to -98, 95 to -44), (0 until 4).map { TribePage.ringPosition(it, 4) })
        assertEquals(0 to -105, TribePage.ringPosition(2, 5))
    }

    @Test
    fun groupsTheCircleByPerson() {
        val jo = Collaborator(userId = "jo", displayName = "Mamie Jo", role = "COLLABORATOR_ROLE_EDITOR")
        val michel = Collaborator(userId = "mi", displayName = "Papi Michel")
        val camille = Collaborator(userId = "cam", displayName = "Camille", role = "COLLABORATOR_ROLE_EDITOR")
        val folders = listOf(
            Folder(id = "a", name = "Cadeaux Léo", collaborators = listOf(jo, michel, camille)),
            Folder(id = "b", name = "Noël", collaborators = listOf(michel.copy(role = "COLLABORATOR_ROLE_VIEWER"), jo.copy(role = "COLLABORATOR_ROLE_VIEWER"))),
            Folder(id = "c", name = "Vacances", collaborators = listOf(Collaborator(userId = "ines", email = "ines@exemple.fr"))),
        )
        val circle = TribePage.circleOf(folders) { it == "cam" }
        assertEquals(listOf("Mamie Jo", "Papi Michel", "ines@exemple.fr"), circle.map { it.name })
        assertEquals(listOf(true, false, false), circle.map { it.editor })
        assertEquals(listOf("a", "b"), circle.first().folders.map { it.id })
    }

    @Test
    fun summarizesTheYear() {
        val y = TribePage.yearInIdeas(DoneSummary(year = 2026, outings = 14, recipes = 24, books = 9, total = 52, bestMonth = 7, bestMonthCount = 12))
        assertEquals(TribePage.YearInIdeas(outings = 14, recipes = 24, books = 9, total = 52, bestMonth = 6 to 12), y)
        assertEquals(TribePage.YearLabels("sorties faites", "recettes cuisinées", "livres lus"), TribePage.yearLabels(y))
        assertEquals("Votre meilleur mois : juillet, 12 idées faites.", TribePage.bestMonthLine(y))
        assertEquals("juillet", TribePage.monthName(6))

        val one = TribePage.yearInIdeas(DoneSummary(year = 2026, outings = 1, recipes = 1, books = 1, total = 3, bestMonth = 3, bestMonthCount = 1))
        assertEquals(TribePage.YearLabels("sortie faite", "recette cuisinée", "livre lu"), TribePage.yearLabels(one))
        assertEquals("Votre meilleur mois : mars, 1 idée faite.", TribePage.bestMonthLine(one))

        // Rien de fait, ou le bilan n'est pas encore arrivé
        val none = TribePage.yearInIdeas(null)
        assertEquals(TribePage.YearInIdeas(), none)
        assertNull(TribePage.yearInIdeas(DoneSummary(year = 2024)).bestMonth)
        assertEquals("Marquez vos premières idées faites : elles rempliront votre année.", TribePage.bestMonthLine(none))
    }

    @Test
    fun countsIdeasForAChild() {
        val now = java.time.Instant.parse("2026-10-23T12:00:00Z").toEpochMilli()
        val emma = Child(id = "e", name = "Emma", birthDate = java.time.Instant.parse("2020-11-16T12:00:00Z").toEpochMilli())
        val folders = listOf(Folder(id = "anniv", name = "Anniv. Emma", childIds = listOf("e")))
        val links = listOf(
            Link(id = "1", title = "", url = "", category = LinkCategory.IDEE, folderId = "anniv"),
            Link(id = "2", title = "", url = "", category = LinkCategory.IDEE, ageRange = "3–6 ans"),
            Link(id = "3", title = "", url = "", category = LinkCategory.IDEE, ageRange = "10 ans et +"),
        )
        assertEquals(2, TribePage.ideasForChild(links, folders, emma, now))
        assertEquals("Anniversaire le 16 novembre", TribePage.birthdayLabel(emma.birthDate))
    }
}
