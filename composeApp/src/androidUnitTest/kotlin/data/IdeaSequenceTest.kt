package data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Mêmes cas que le web (frontend/src/lib/ideaSequence.test.ts). */
class IdeaSequenceTest {
    private val seq = listOf("a", "b", "c")

    @Test
    fun givesPreviousNextAndPosition() {
        assertEquals(IdeaSequence.Neighbors("a", "c", 2, 3), IdeaSequence.neighbors(seq, "b"))
        assertEquals(IdeaSequence.Neighbors(null, "b", 1, 3), IdeaSequence.neighbors(seq, "a"))
        assertEquals(IdeaSequence.Neighbors("b", null, 3, 3), IdeaSequence.neighbors(seq, "c"))
    }

    @Test
    fun skipsIdeasThatNoLongerExist() {
        assertEquals(IdeaSequence.Neighbors(null, "c", 1, 2), IdeaSequence.neighbors(seq, "a") { it != "b" })
    }

    @Test
    fun nothingOutsideTheSequenceOrWithASingleIdea() {
        assertNull(IdeaSequence.neighbors(seq, "z"))
        assertNull(IdeaSequence.neighbors(listOf("a"), "a"))
        assertNull(IdeaSequence.neighbors(emptyList(), "a"))
        assertNull(IdeaSequence.neighbors(seq, "a") { false })
    }

    @Test
    fun ignoresDuplicates() {
        assertEquals(IdeaSequence.Neighbors("a", null, 2, 2), IdeaSequence.neighbors(listOf("a", "b", "a"), "b"))
    }
}
