package data

import android.content.Context
import viewmodel.LinkViewModel

/**
 * Données de l'app partagées par tout le processus : l'écran (MainActivity) et
 * les fonctions appelées par Gemini (GeminiFunctions) lisent et écrivent le même
 * dépôt. Avec deux dépôts, chacun écraserait sur le disque ce que l'autre vient
 * d'ajouter, et l'app ne verrait pas les idées créées par Gemini.
 */
object AppGraph {
    @Volatile private var storage: AndroidStorage? = null
    @Volatile private var repository: LinkRepository? = null
    @Volatile private var assistant: LinkViewModel? = null

    fun storage(context: Context): AndroidStorage =
        storage ?: synchronized(this) {
            storage ?: AndroidStorage(context.applicationContext).also { storage = it }
        }

    fun repository(context: Context): LinkRepository =
        repository ?: synchronized(this) {
            repository ?: LinkRepository(storage(context)).also { repository = it }
        }

    /**
     * ViewModel des créations demandées par Gemini, hors de tout écran : mêmes
     * règles que l'app (création locale, puis envoi au serveur sous le verrou
     * du dépôt). Gardé pour le processus, comme le dépôt, pour qu'un envoi
     * lancé ne soit pas interrompu.
     */
    fun assistantViewModel(context: Context): LinkViewModel =
        assistant ?: synchronized(this) {
            assistant ?: LinkViewModel(repository(context)).also { vm ->
                vm.ogImageFetcher = { url -> OgImageFetcher.fetch(url) }
                assistant = vm
            }
        }
}
