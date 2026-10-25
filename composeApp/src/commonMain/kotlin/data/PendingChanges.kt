package data

import kotlinx.serialization.Serializable

/**
 * Changements faits sur l'appareil et pas encore reçus par le serveur (hors
 * ligne, serveur injoignable). Gardés sur l'appareil et renvoyés à la
 * synchronisation suivante, avant de reprendre les données du serveur : sans
 * cela, la synchronisation (où le serveur fait foi) les effacerait.
 *
 * Les créations n'y figurent pas : un élément créé sur l'appareil garde un
 * identifiant local (voir [SyncIds.isLocal]) tant que le serveur ne lui en a
 * pas donné un, et c'est ce qui le désigne comme « à envoyer ».
 */
@Serializable
data class PendingChanges(
    val linkUpdates: Set<String> = emptySet(),
    val linkDeletes: Set<String> = emptySet(),
    val folderUpdates: Set<String> = emptySet(),
    val childUpdates: Set<String> = emptySet(),
    val childDeletes: Set<String> = emptySet(),
)

object SyncIds {
    private val serverId = Regex("^[0-9a-f]{24}$")

    /** Identifiant donné par l'appareil (pas encore un ObjectId du serveur). */
    fun isLocal(id: String): Boolean = !serverId.matches(id)

    /**
     * Liste après synchronisation : celle du serveur, sauf ce qui a été
     * supprimé ici sans que le serveur le sache encore, avec la version locale
     * de ce qui a été modifié ici, puis ce qui a été créé ici et pas encore
     * envoyé.
     */
    fun <T> merge(
        local: List<T>,
        server: List<T>,
        id: (T) -> String,
        pendingUpdates: Set<String> = emptySet(),
        pendingDeletes: Set<String> = emptySet(),
    ): List<T> {
        val localById = local.associateBy(id)
        val kept = server
            .filter { id(it) !in pendingDeletes }
            .map { s -> if (id(s) in pendingUpdates) localById[id(s)] ?: s else s }
        return kept + local.filter { isLocal(id(it)) }
    }
}
