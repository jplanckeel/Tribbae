package data

/**
 * « Offrir une idée » : liste de cadeaux partagée. Personne ne voit qui offre
 * quoi ; chacun voit si un cadeau est libre, réservé ou offert, et les siens.
 * Mêmes libellés et mêmes cas que frontend/src/lib/gifts.ts (gifts.test.ts).
 */
object Gifts {
    enum class Tone { FREE, MINE, TAKEN, DONE }

    data class State(
        val label: String,
        val long: String,
        val sub: String,
        val tone: Tone,
        val free: Boolean,
        val mine: Boolean,
        val dim: Boolean,
        val strike: Boolean,
    )

    fun state(status: String?): State = when (status) {
        "mine" -> State("Vous l'offrez", "Vous l'offrez", "Pas encore acheté", Tone.MINE, free = false, mine = true, dim = false, strike = false)
        "mine_bought" -> State("Acheté par vous", "Acheté, il n'y a plus qu'à l'emballer", "Les autres le voient comme offert", Tone.DONE, free = false, mine = true, dim = false, strike = false)
        "pending" -> State("À confirmer", "Confirmez dans votre e-mail", "Sans confirmation sous 2 h, il redevient libre.", Tone.MINE, free = false, mine = true, dim = false, strike = false)
        "reserved" -> State("Réservé", "Quelqu'un s'en occupe", "Personne ne sait qui, pas même la personne qui a fait la liste.", Tone.TAKEN, free = false, mine = false, dim = true, strike = false)
        "bought" -> State("Offert", "Déjà acheté", "Rien à faire de ce côté", Tone.DONE, free = false, mine = false, dim = true, strike = true)
        else -> State("Libre", "Personne ne l'offre encore", "Réservez-le pour qu'il ne soit pas offert deux fois.", Tone.FREE, free = true, mine = false, dim = false, strike = false)
    }

    data class Progress(val done: Int, val taken: Int, val free: Int, val pDone: Int, val pTaken: Int, val label: String, val freeLabel: String)

    private fun plural(n: Int, one: String, many: String) = "$n ${if (n > 1) many else one}"

    fun progress(statuses: List<String>): Progress {
        val total = statuses.size
        val done = statuses.count { it == "bought" || it == "mine_bought" }
        val taken = statuses.count { it == "reserved" || it == "mine" || it == "pending" }
        val free = total - done - taken
        fun pct(n: Int) = if (total == 0) 0 else Math.round(n * 100f / total)
        return Progress(
            done, taken, free, pct(done), pct(taken),
            "${plural(done, "offert", "offerts")} · ${plural(taken, "réservé", "réservés")}",
            plural(free, "encore libre", "encore libres"),
        )
    }

    enum class Filter(val label: String) { ALL("Tout"), FREE("À offrir"), MINE("Mes cadeaux") }

    fun matches(status: String?, filter: Filter): Boolean {
        val s = state(status)
        return when (filter) {
            Filter.ALL -> true
            Filter.FREE -> s.free
            Filter.MINE -> s.mine
        }
    }

    /** Créatrice de la liste : seulement le nombre d'idées qui ont trouvé preneur. */
    fun surpriseLabel(statuses: List<String>): String {
        val total = statuses.size
        val taken = statuses.count { it.isNotEmpty() }
        if (taken == 0) return "Aucune idée n'a encore trouvé preneur."
        return "$taken ${if (taken > 1) "idées" else "idée"} sur $total ${if (taken > 1) "ont" else "a"} trouvé preneur."
    }

    /** Statut renvoyé par le serveur après une action de l'offreur (compte). */
    fun mineFrom(serverStatus: String): String = when (serverStatus) {
        "bought" -> "mine_bought"
        "reserved" -> "mine"
        else -> ""
    }
}
