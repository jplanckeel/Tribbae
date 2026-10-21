package data

import kotlin.test.Test
import kotlin.test.assertEquals

/** Mêmes règles que le web (frontend/src/lib/addFlow.ts). */
class AddFlowTest {
    private val now = java.time.Instant.parse("2026-10-21T12:00:00Z").toEpochMilli()

    @Test
    fun suggestsFoldersWithReasons() {
        val sorties = Folder(id = "a", name = "Sorties d'automne", updatedAt = "2026-10-01")
        val drome = Folder(id = "b", name = "Vacances Drôme", updatedAt = "2026-10-10", collaborators = listOf(Collaborator(displayName = "Inès")))
        val noel = Folder(id = "c", name = "Noël", updatedAt = "2026-10-05")
        val links = listOf(
            Link(id = "1", title = "x", url = "", category = LinkCategory.EVENEMENT, folderId = "a", createdAt = "2026-10-01T10:00:00Z"),
            Link(id = "2", title = "y", url = "", category = LinkCategory.RECETTE, folderId = "c", createdAt = "2026-10-21T09:00:00Z"),
        )
        val s = AddFlow.suggestFolders(LinkCategory.EVENEMENT, listOf(sorties, drome, noel), links, now = now)
        assertEquals(listOf("a" to "Vous y rangez vos sorties", "c" to "Utilisé il y a 3 h", "b" to "Partagé avec Inès"), s.map { it.folder.id to it.reason })
        assertEquals("b", AddFlow.suggestFolders(LinkCategory.EVENEMENT, listOf(sorties, drome, noel), links, preset = "b", now = now).first().folder.id)
    }

    @Test
    fun describesWhoSeesAndAges() {
        assertEquals("Inès la verra", AddFlow.whoSees(Folder(id = "b", name = "", collaborators = listOf(Collaborator(displayName = "Inès"))), false))
        assertEquals("Visible par toute la communauté", AddFlow.whoSees(null, true))
        assertEquals("3–10 ans", AddFlow.bandsToAge(listOf("3–6", "6–10")))
        assertEquals(listOf("3–6", "6–10"), AddFlow.selectedBands("3–10 ans"))
        assertEquals("", AddFlow.bandsToAge(emptyList()))
        assertEquals("hier", AddFlow.ago("2026-10-20T10:00:00Z", now))
    }
}
