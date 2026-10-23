package data

import kotlin.test.Test
import kotlin.test.assertEquals

/** Mêmes règles que le web (frontend/src/lib/aiRequest.ts). */
class AiRequestTest {
    private fun at(iso: String) = java.time.LocalDateTime.parse(iso).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
    private val now = at("2026-10-23T12:00:00")
    private val leo = Child(id = "leo", name = "Léo", birthDate = at("2017-02-03T12:00:00"))
    private val emma = Child(id = "emma", name = "Emma", birthDate = at("2020-11-16T12:00:00"))
    private val kids = listOf(leo, emma)

    @Test
    fun buildsTheSentenceSentAsPrompt() {
        val r = AiRequests.defaultRequest(kids, "leo")
        assertEquals("Je cherche des activités pour Léo (9 ans) ce week-end, à la maison, budget gratuit.", AiRequests.promptOf(r, kids, now))
        val both = r.copy(who = listOf("leo", "emma"), detail = "ils adorent l'espace en ce moment !")
        assertEquals(
            "Je cherche des activités pour Léo (9 ans) et Emma (5 ans) ce week-end, à la maison, budget gratuit. Ils adorent l'espace en ce moment.",
            AiRequests.promptOf(both, kids, now)
        )
        assertEquals("Léo et Emma", AiRequests.blankLabel(AiRequests.Blank.Who, both, kids, now))
        assertEquals("Je cherche des activités pour toute la tribu ce week-end, à la maison, budget gratuit.", AiRequests.promptOf(AiRequests.defaultRequest(emptyList()), emptyList(), now))
    }

    @Test
    fun togglesWhoWithTheWholeTribeExclusive() {
        assertEquals(listOf("leo", "emma"), AiRequests.toggleWho(listOf("leo"), "emma"))
        assertEquals(listOf(AiRequests.ALL), AiRequests.toggleWho(listOf("leo", "emma"), AiRequests.ALL))
        assertEquals(listOf("emma"), AiRequests.toggleWho(listOf(AiRequests.ALL), "emma"))
        assertEquals(emptyList(), AiRequests.toggleWho(listOf("emma"), "emma"))
        assertEquals(listOf("Léo, 9 ans", "Emma, 5 ans", "toute la tribu"), AiRequests.whoOptions(kids, now).map { it.label })
    }

    @Test
    fun fillsTheSentenceFromAnInspiration() {
        val insp = AiRequests.inspirations(kids, now)
        assertEquals(listOf("Jour de pluie", "Anniversaire de Emma", "Goûter express", "Grand air"), insp.map { it.label })
        val r = insp[1].apply(AiRequests.defaultRequest(kids))
        assertEquals("Je cherche des idées cadeaux pour Emma (5 ans) pour un anniversaire, près de chez nous, budget moins de 20 €.", AiRequests.promptOf(r, kids, now))
        assertEquals("5 idées pour Emma pour un anniversaire", AiRequests.resultTitle(5, r, kids, now))
        assertEquals("idées cadeaux · près de chez nous · moins de 20 €", AiRequests.resultSubtitle(r))
        assertEquals("Cadeaux pour Emma", AiRequests.folderNameFor(r, kids, now = now))
        assertEquals("Jour de pluie", AiRequests.folderNameFor(r, kids, "Jour de pluie", now))
    }
}
