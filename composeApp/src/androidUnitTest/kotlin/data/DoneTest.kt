package data

import java.util.Calendar
import java.util.TimeZone
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Mêmes cas que le web (frontend/src/lib/done.test.ts), en Europe/Paris. */
class DoneTest {
    private lateinit var saved: TimeZone

    @BeforeTest
    fun paris() {
        saved = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Paris"))
    }

    @AfterTest
    fun restore() = TimeZone.setDefault(saved)

    private fun at(y: Int, m: Int, d: Int, h: Int = 12) = Calendar.getInstance().apply { clear(); set(y, m - 1, d, h, 0) }.timeInMillis

    private val now get() = at(2026, 11, 11, 15) // mercredi 11 novembre 2026

    private fun link(id: String, c: LinkCategory, eventDate: Long? = null, createdAt: String = "", title: String = id, location: String = "") =
        Link(id = id, title = title, url = "", category = c, eventDate = eventDate, createdAt = createdAt, location = location)

    private fun done(id: String, c: LinkCategory, day: String, count: Int = 1, createdAt: String = "") =
        link(id, c, createdAt = createdAt).copy(doneCount = count, lastDoneAt = "${day}T12:00:00Z")

    @Test
    fun givesTheButtonAndStateOfEachCategory() {
        assertEquals("On l'a fait !", Done.words(LinkCategory.ACTIVITE).verb)
        assertEquals("On y est allés !", Done.words(LinkCategory.EVENEMENT).verb)
        assertEquals("On l'a cuisinée", Done.words(LinkCategory.RECETTE).verb)
        assertEquals("On l'a lu", Done.words(LinkCategory.LIVRE).verb)
        assertEquals("Acheté", Done.words(LinkCategory.CADEAU).verb)
        assertEquals("C'est fait", Done.words(LinkCategory.IDEE).verb)
        assertEquals("C'est fait", Done.words(LinkCategory.DECORATION).verb)
        assertEquals("On y est retournés", Done.words(LinkCategory.EVENEMENT).again)

        assertEquals("Fait le 26 oct.", Done.label(done("1", LinkCategory.ACTIVITE, "2026-10-26"), now))
        assertEquals("Fait le 8 nov.", Done.label(done("2", LinkCategory.EVENEMENT, "2026-11-08"), now))
        assertEquals("Cuisinée le 3 nov.", Done.label(done("3", LinkCategory.RECETTE, "2026-11-03"), now))
        assertEquals("Cuisinée 3 fois", Done.label(done("3", LinkCategory.RECETTE, "2026-11-03", 3), now))
        assertEquals("Lu en octobre", Done.label(done("4", LinkCategory.LIVRE, "2026-10-12"), now))
        assertEquals("Acheté le 2 nov.", Done.label(done("5", LinkCategory.CADEAU, "2026-11-02"), now))
        assertEquals("Fait le 5 nov.", Done.label(done("6", LinkCategory.IDEE, "2026-11-05"), now))
        assertEquals("Fait le 1er nov.", Done.label(done("7", LinkCategory.DECORATION, "2026-11-01", 2), now))
        assertEquals("", Done.label(link("8", LinkCategory.ACTIVITE), now))
    }

    @Test
    fun addsTheYearWhenItIsNotTheCurrentOne() {
        assertEquals("26 oct.", Done.formatDay("2026-10-26", now))
        assertEquals("1er déc. 2025", Done.formatDay("2025-12-01", now))
        assertEquals("Lu en août 2025", Done.label(done("1", LinkCategory.LIVRE, "2025-08-20"), now))
        assertEquals("2025-08-20", Done.doneDay(done("1", LinkCategory.LIVRE, "2025-08-20")))
        assertEquals("Cuisinée ×3", Done.stamp(done("1", LinkCategory.RECETTE, "2026-11-03", 3)))
        assertEquals("Fait", Done.stamp(done("1", LinkCategory.ACTIVITE, "2026-11-03")))
    }

    @Test
    fun asksTheDayAfterADatedOuting() {
        val past = link("1", LinkCategory.EVENEMENT, at(2026, 11, 7, 14), title = "Fête de la courge", location = "Ferme du Mas")
        assertTrue(Done.awaitingConfirmation(past, now))
        assertEquals("Samedi · c'était bien ?", Done.awaitingLabel(past, now))
        assertEquals("Samedi · Ferme du Mas", Done.promptKicker(past, now))
        assertEquals("Vous êtes allés à « Fête de la courge » ?", Done.promptQuestion(past.category, past.title))
        assertEquals("Vous avez fait « Cabane » ?", Done.promptQuestion(LinkCategory.ACTIVITE, "Cabane"))
        assertEquals("Hier · c'était bien ?", Done.awaitingLabel(link("2", LinkCategory.ACTIVITE, at(2026, 11, 10, 9)), now))
        assertEquals("3 oct. · c'était bien ?", Done.awaitingLabel(link("3", LinkCategory.ACTIVITE, at(2026, 10, 3, 9)), now))

        // Le jour même, à venir, déjà faite, « Pas cette fois », sans date, autre catégorie
        assertFalse(Done.awaitingConfirmation(link("4", LinkCategory.EVENEMENT, at(2026, 11, 11, 9)), now))
        assertFalse(Done.awaitingConfirmation(link("5", LinkCategory.EVENEMENT, at(2026, 11, 22, 9)), now))
        assertFalse(Done.awaitingConfirmation(past.copy(doneCount = 1), now))
        assertFalse(Done.awaitingConfirmation(past.copy(donePromptDismissed = true), now))
        assertFalse(Done.awaitingConfirmation(link("6", LinkCategory.ACTIVITE), now))
        assertFalse(Done.awaitingConfirmation(link("7", LinkCategory.RECETTE, at(2026, 11, 7, 14)), now))
    }

    @Test
    fun measuresTheProgressOfAFolder() {
        val links = listOf(
            done("1", LinkCategory.ACTIVITE, "2026-10-26"), done("2", LinkCategory.RECETTE, "2026-11-08", 3), link("3", LinkCategory.EVENEMENT),
            link("4", LinkCategory.LIVRE), link("5", LinkCategory.CADEAU), done("6", LinkCategory.IDEE, "2026-11-05"),
        )
        val p = Done.folderProgress(links)
        assertEquals(Done.Progress(2, 4), p)
        assertEquals("2 sur 4 faites", Done.progressLabel(p!!))
        assertEquals("Encore 2 à faire", Done.progressHint(p))
        assertEquals("Plus qu'une à faire", Done.progressHint(Done.Progress(3, 4)))
        assertEquals("Bravo, tout est fait !", Done.progressHint(Done.Progress(4, 4)))
        // Moins de 3 idées comptées, ou liste de cadeaux : ni anneau ni barre
        assertNull(Done.folderProgress(links.take(2)))
        assertNull(Done.folderProgress(listOf(link("a", LinkCategory.CADEAU), link("b", LinkCategory.IDEE), link("c", LinkCategory.DECORATION))))
        assertNull(Done.folderProgress(links, giftList = true))
        assertEquals("2 sur 4 faites", Done.toast(p))
        assertEquals("C'est noté", Done.toast(null))
    }

    @Test
    fun filtersAndSortsTodoFirst() {
        val links = listOf(
            done("faite-nov", LinkCategory.ACTIVITE, "2026-11-08", createdAt = "2026-09-01T10:00:00Z"),
            link("recente", LinkCategory.RECETTE, createdAt = "2026-11-01T10:00:00Z"),
            link("a-venir", LinkCategory.EVENEMENT, at(2026, 11, 22), "2026-08-01T10:00:00Z"),
            done("faite-oct", LinkCategory.LIVRE, "2026-10-12", createdAt = "2026-09-02T10:00:00Z"),
            link("ancienne", LinkCategory.IDEE, createdAt = "2026-06-01T10:00:00Z"),
            link("passee", LinkCategory.EVENEMENT, at(2026, 11, 7), "2026-07-01T10:00:00Z"),
        )
        assertEquals(listOf("passee", "a-venir", "recente", "ancienne", "faite-oct", "faite-nov"), Done.sortTodoFirst(links, now).map { it.id })
        assertEquals(mapOf(Done.Filter.ALL to 6, Done.Filter.TODO to 4, Done.Filter.DONE to 2), Done.counts(links))
        assertEquals(listOf("faite-nov", "faite-oct"), links.filter { Done.matches(it, Done.Filter.DONE) }.map { it.id })
        assertEquals(4, links.count { Done.matches(it, Done.Filter.TODO) })
        assertEquals(6, links.count { Done.matches(it, Done.Filter.ALL) })
    }

    @Test
    fun keepsGiftListsSecretAndOnlyDimsGifts() {
        val gift = link("1", LinkCategory.CADEAU)
        assertTrue(Done.inGiftList(gift, Folder(id = "f", name = "Noël", giftReservations = true)))
        assertFalse(Done.inGiftList(gift, Folder(id = "f", name = "Noël")))
        assertFalse(Done.inGiftList(gift, null))
        assertFalse(Done.inGiftList(link("2", LinkCategory.LIVRE), Folder(id = "f", name = "Noël", giftReservations = true)))
        assertTrue(Done.dimmed(done("1", LinkCategory.CADEAU, "2026-11-02")))
        assertFalse(Done.dimmed(gift))
        assertFalse(Done.dimmed(done("2", LinkCategory.ACTIVITE, "2026-11-02")))
    }

    @Test
    fun writesTheMemoryTheCelebrationAndThePublicCounter() {
        assertEquals("Avec Léo, Emma et Camille · 26 oct.", Done.memoryLine(listOf("Léo", "Emma", "Camille"), "2026-10-26", 1, now))
        assertEquals("Avec Léo · 3 fois, dernière le 8 nov.", Done.memoryLine(listOf("Léo"), "2026-11-08", 3, now))
        assertEquals("26 oct.", Done.memoryLine(emptyList(), "2026-10-26", 1, now))
        assertEquals("Touchez une étoile", Done.starWord(0))
        assertEquals("Génial !", Done.starWord(5))
        assertEquals("Miam, c'est noté !", Done.celebrationTitle(LinkCategory.RECETTE, false))
        assertEquals("C'est fait !", Done.celebrationTitle(LinkCategory.ACTIVITE, false))
        assertEquals("On l'a relu !", Done.celebrationTitle(LinkCategory.LIVRE, true))
        assertEquals("120 familles l'ont fait", Done.familiesLabel(120))
        assertEquals("", Done.familiesLabel(0))
    }

    @Test
    fun offersTodayAndYesterday() {
        assertEquals("2026-11-11", Done.dayOf(now))
        assertEquals(listOf("Aujourd'hui" to "2026-11-11", "Hier" to "2026-11-10"), Done.dayChoices(now))
        // Passage à l'heure d'hiver (25 octobre 2026) : hier reste la veille
        assertEquals(listOf("2026-10-26", "2026-10-25"), Done.dayChoices(at(2026, 10, 26, 0)).map { it.second })
    }

    @Test
    fun refusalsAreExplained() {
        assertEquals("Vous avez marqué beaucoup d'idées aujourd'hui : réessayez demain.", Done.errorMessage("API error (429): too many ideas marked done today"))
        assertEquals(
            "Dans une liste de cadeaux, c'est la réservation qui dit qu'un cadeau est acheté.",
            Done.errorMessage("API error (400): gift list ideas are marked through their reservation"),
        )
    }
}
