package data

/**
 * Suite d'idées : les idées d'un dossier, dans l'ordre où le dossier les montre
 * (tri et filtre compris). La fiche d'une idée ouverte depuis le dossier passe à
 * la précédente ou à la suivante d'un glissement sur le côté. Même logique sur le
 * web (frontend/src/lib/ideaSequence.ts) : IdeaSequenceTest ↔ ideaSequence.test.ts.
 */
object IdeaSequence {
    /** [position] à partir de 1. */
    data class Neighbors(val prev: String?, val next: String?, val position: Int, val total: Int)

    /** Les idées de la suite qui existent encore ([available]), sans doublon, dans l'ordre. */
    fun usable(ids: List<String>, id: String, available: (String) -> Boolean = { true }): List<String> =
        ids.distinct().filter { it == id || available(it) }

    /**
     * Voisines d'une idée dans la suite (une idée supprimée entre-temps est
     * sautée). null hors de la suite, ou si la suite n'a qu'une idée.
     */
    fun neighbors(ids: List<String>, id: String, available: (String) -> Boolean = { true }): Neighbors? {
        val list = usable(ids, id, available)
        val i = list.indexOf(id)
        if (i < 0 || list.size < 2) return null
        return Neighbors(list.getOrNull(i - 1), list.getOrNull(i + 1), i + 1, list.size)
    }
}
