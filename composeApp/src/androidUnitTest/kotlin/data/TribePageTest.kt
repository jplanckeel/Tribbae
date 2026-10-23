package data

import java.time.ZoneOffset
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
        fun link(id: String, c: LinkCategory, at: String) = Link(id = id, title = id, url = "", category = c, createdAt = at)
        val links = listOf(
            link("1", LinkCategory.EVENEMENT, "2026-07-02T12:00:00Z"),
            link("2", LinkCategory.ACTIVITE, "2026-07-14T12:00:00Z"),
            link("3", LinkCategory.RECETTE, "2026-03-01T12:00:00Z"),
            link("4", LinkCategory.CADEAU, "2025-12-24T12:00:00Z"),
            link("5", LinkCategory.IDEE, ""),
        )
        val y = TribePage.yearInIdeas(links, 2026, ZoneOffset.UTC)
        assertEquals(TribePage.YearInIdeas(outings = 2, recipes = 1, gifts = 0, total = 3, bestMonth = 6 to 2), y)
        assertEquals("juillet", TribePage.monthName(6))
        assertNull(TribePage.yearInIdeas(links, 2024, ZoneOffset.UTC).bestMonth)
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
