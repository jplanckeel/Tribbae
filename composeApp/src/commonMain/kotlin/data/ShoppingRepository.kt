package data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * « Les courses » sur Android, hors ligne d'abord : la liste de la tribu reçue
 * du serveur et les changements faits ici, gardés sur l'appareil jusqu'à leur
 * envoi. Chaque changement s'affiche tout de suite (ShoppingList.applyOp) ; à
 * l'envoi, la réponse du serveur remplace la liste. Même principe que
 * lib/shoppingStore.ts sur le web.
 */
class ShoppingRepository(private val storage: Storage? = null) {

    /** Un changement pas encore reçu par le serveur. */
    @Serializable
    data class Queued(
        val op: ShoppingList.Op,
        /** Recette ajoutée au menu : de quoi l'afficher avant la réponse. */
        val recipe: ShoppingList.RecipeInfo? = null,
        val at: Long = 0,
    )

    /** Ce qui est gardé sur l'appareil. [owner] : compte à qui appartient la liste. */
    @Serializable
    data class Saved(
        val owner: String = "",
        val server: ShoppingList.Shared = ShoppingList.Shared(),
        val queue: List<Queued> = emptyList(),
        val loaded: Boolean = false,
    )

    data class State(
        /** Liste affichée : celle du serveur, plus les changements pas encore envoyés. */
        val list: ShoppingList.Shared = ShoppingList.Shared(),
        val loaded: Boolean = false,
        /** Le dernier envoi n'a pas pu partir : les changements attendent le réseau. */
        val offline: Boolean = false,
        val pending: Int = 0,
    )

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private var saved: Saved = storage?.loadShoppingList()?.let { runCatching { json.decodeFromString<Saved>(it) }.getOrNull() } ?: Saved()
    private var offline = false
    private val sending = Mutex()

    private val _state = MutableStateFlow(view())
    val state: StateFlow<State> = _state.asStateFlow()

    private fun view(): State {
        val list = saved.queue.fold(saved.server) { l, q -> ShoppingList.applyOp(l, q.op, saved.owner, q.at) { q.recipe } }
        return State(list, saved.loaded, offline, saved.queue.size)
    }

    private fun publish(persist: Boolean = true) {
        if (persist) storage?.saveShoppingList(json.encodeToString(Saved.serializer(), saved))
        _state.value = view()
    }

    /** Autre compte (ou première ouverture) : la liste d'avant ne le concerne pas. */
    @Synchronized
    fun useAccount(userId: String) {
        if (saved.owner == userId) return
        saved = Saved(owner = userId)
        offline = false
        publish()
    }

    /** Change la liste : affiché tout de suite, gardé jusqu'à l'envoi. [now] en secondes. */
    @Synchronized
    fun change(op: ShoppingList.Op, recipe: ShoppingList.RecipeInfo? = null, now: Long = System.currentTimeMillis() / 1000) {
        saved = saved.copy(queue = saved.queue + Queued(op, recipe, now))
        publish()
    }

    /** Idée créée ici et reçue par le serveur : les changements qui la citent suivent son identifiant. */
    @Synchronized
    fun replaceLinkId(old: String, new: String) {
        if (saved.queue.none { it.op.linkId == old }) return
        saved = saved.copy(queue = saved.queue.map { if (it.op.linkId == old) it.copy(op = it.op.copy(linkId = new)) else it })
        publish()
    }

    /** Recette supprimée ici avant d'avoir atteint le serveur : ses changements n'ont plus d'objet. */
    @Synchronized
    fun forgetLink(id: String) {
        if (saved.queue.none { it.op.linkId == id }) return
        saved = saved.copy(queue = saved.queue.filter { it.op.linkId != id })
        publish()
    }

    /** Déconnexion : rien ne reste sur l'appareil. */
    @Synchronized
    fun clear() {
        saved = Saved()
        offline = false
        publish()
    }

    /**
     * Envoie les changements en attente, dans l'ordre, puis relit la liste. Un changement qui cite une recette encore locale (pas encore
     * envoyée) attend, avec ceux qui le suivent. Hors ligne : tout est gardé.
     * Refusé par le serveur : le lot est abandonné et le message renvoyé.
     */
    suspend fun sync(
        send: suspend (List<ShoppingList.Op>) -> ShoppingList.Shared,
        fetch: suspend (Long) -> ShoppingList.Shared?,
    ): String? = sending.withLock {
        var refused: String? = null
        while (true) {
            val batch = synchronized(this) { saved.queue.takeWhile { !(it.op.linkId.isNotEmpty() && SyncIds.isLocal(it.op.linkId)) }.take(100) }
            if (batch.isEmpty()) break
            try {
                val fresh = send(batch.map { it.op })
                synchronized(this) {
                    saved = saved.copy(server = fresh, queue = saved.queue.drop(batch.size), loaded = true)
                    offline = false
                    publish()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!isRefusal(e)) {
                    synchronized(this) { offline = true; publish(persist = false) }
                    return@withLock null
                }
                synchronized(this) {
                    saved = saved.copy(queue = saved.queue.drop(batch.size))
                    publish()
                }
                refused = "Ce changement des courses n'a pas pu être enregistré."
            }
        }
        // Relecture : les changements des proches arrivent ; ceux qui attendent
        // encore ici (recette pas encore envoyée) restent appliqués par-dessus
        try {
            val fresh = fetch(if (saved.loaded) saved.server.version else 0)
            synchronized(this) {
                if (fresh != null) saved = saved.copy(server = fresh)
                saved = saved.copy(loaded = true)
                offline = false
                publish()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            synchronized(this) { offline = true; publish(persist = false) }
        }
        refused
    }

    // Erreur 4xx du serveur (AuthenticatedApiClient : « API error (400): … ») : le
    // changement ne passera jamais, inutile de le garder
    private fun isRefusal(e: Exception) = e.message?.let { Regex("^API error \\(4\\d\\d\\)").containsMatchIn(it) && !it.startsWith("API error (401)") } == true
}
