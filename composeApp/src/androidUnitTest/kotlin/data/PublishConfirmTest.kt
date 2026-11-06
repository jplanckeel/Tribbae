package data

import kotlin.test.Test
import kotlin.test.assertEquals

/** Mêmes cas que frontend/src/lib/publish.test.ts. */
class PublishConfirmTest {
    @Test
    fun saysWhatHappensToAnIdea() {
        assertEquals("Votre idée sera visible par toutes les familles dans Explorer. Les prénoms des enfants ne sont jamais publiés.", PublishConfirm.IDEA_TEXT)
    }

    @Test
    fun countsTheIdeasOfAFolder() {
        assertEquals("Les 12 idées de ce dossier seront visibles par toutes les familles dans Explorer. Les prénoms des enfants ne sont jamais publiés.", PublishConfirm.folderText(12))
        assertEquals("L'idée de ce dossier sera visible par toutes les familles dans Explorer. Les prénoms des enfants ne sont jamais publiés.", PublishConfirm.folderText(1))
        assertEquals("Ce dossier sera visible par toutes les familles dans Explorer. Les prénoms des enfants ne sont jamais publiés.", PublishConfirm.folderText(0))
    }
}
