package com.linkkeeper.app

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.appfunctions.AppFunctionDeclaration
import androidx.appfunctions.AppFunctionInvalidArgumentException
import androidx.appfunctions.AppFunctionSerializable
import androidx.appfunctions.AppFunctionService
import androidx.appfunctions.AppFunctionServiceEntryPoint
import data.AppGraph
import data.AssistantRequests
import data.Folder
import data.SessionManager

// Fonctions que Gemini (ou un autre agent autorisé par Android, permission
// EXECUTE_APP_FUNCTIONS) appelle sur le téléphone, Android 16+. Le compilateur
// AppFunctions (KSP) génère TribbaeAppFunctionService (déclaré dans le
// manifeste) et assets/tribbae_functions.xml. Les KDoc en anglais sont les
// descriptions lues par l'agent : texte fixe, jamais de contenu de l'utilisateur.
//
// Comme le serveur MCP : idées toujours privées, jamais dans un dossier public,
// pas de modification ni de suppression. Tout passe par LinkViewModel (création
// locale puis envoi au serveur), comme une idée ajoutée à la main ; sans
// connexion ou sans compte, elle attend sur l'appareil la prochaine synchro.

/** A Tribbae folder where ideas can be saved. */
@AppFunctionSerializable(isDescribedByKDoc = true)
class TribbaeFolder(
    /** Folder identifier. */
    val id: String,
    /** Folder name, to pass as folderName when creating an idea. */
    val name: String,
    /** Number of ideas already in the folder. */
    val ideaCount: Int,
)

/** The idea saved in Tribbae. */
@AppFunctionSerializable(isDescribedByKDoc = true)
class CreatedIdea(
    /** Idea identifier. */
    val id: String,
    /** Saved title. */
    val title: String,
    /** Category the idea was saved under. */
    val category: String,
    /** Name of the folder holding the idea, or null when it is in no folder. */
    val folderName: String?,
    /** True when the folder did not exist and was created for this idea. */
    val folderCreated: Boolean,
)

/** The Tribbae folder. */
@AppFunctionSerializable(isDescribedByKDoc = true)
class CreatedFolder(
    /** Folder identifier. */
    val id: String,
    /** Folder name. */
    val name: String,
    /** True when a folder with this name already existed and was returned instead of a new one. */
    val alreadyExisted: Boolean,
)

@RequiresApi(Build.VERSION_CODES.BAKLAVA)
@AppFunctionServiceEntryPoint(
    serviceName = "TribbaeAppFunctionService",
    appFunctionXmlFileName = "tribbae_functions",
)
abstract class GeminiFunctions : AppFunctionService() {

    /**
     * Lists the user's Tribbae folders where new ideas can be saved (family ideas: activities,
     * outings, gifts, recipes, books, decoration). Call it before createIdea to pick an existing
     * folder instead of creating a near duplicate.
     *
     * @return The folders, most recently updated first.
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    fun listFolders(): List<TribbaeFolder> {
        val repository = AppGraph.repository(this)
        val counts = repository.links.value.groupingBy { it.folderId }.eachCount()
        return AssistantRequests.writableFolders(repository.folders.value, SessionManager(this).getUserId())
            .sortedByDescending { it.updatedAt }
            .map { TribbaeFolder(id = it.id, name = it.name, ideaCount = counts[it.id] ?: 0) }
    }

    /**
     * Saves a new family idea in Tribbae. The idea is always private: only the user can publish
     * it from the app.
     *
     * @param title Short title of the idea (required, at most 200 characters).
     * @param url Optional web page about the idea (http or https).
     * @param description Optional description (at most 2000 characters).
     * @param category Optional category: IDEE (default), CADEAU (gift), ACTIVITE (activity),
     *   EVENEMENT (outing), RECETTE (recipe), LIVRE (book) or DECORATION.
     * @param folderName Optional folder to save the idea in. An existing folder with this name is
     *   used, otherwise a private folder is created. Without it, the idea is in no folder.
     * @param tags Optional tags (at most 10).
     * @return The saved idea.
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    fun createIdea(
        title: String,
        url: String? = null,
        description: String? = null,
        category: String? = null,
        folderName: String? = null,
        tags: List<String>? = null,
    ): CreatedIdea = invalidAsArgument {
        val cleanTitle = AssistantRequests.title(title)
        val cleanUrl = AssistantRequests.url(url)
        val cleanDescription = AssistantRequests.description(description)
        val cat = AssistantRequests.category(category)
        val cleanTags = AssistantRequests.tags(tags.orEmpty())
        val vm = viewModel()
        val (folder, created) = folderName?.takeIf { it.isNotBlank() }?.let { findOrCreateFolder(it) } ?: (null to false)
        val link = vm.addLink(
            title = cleanTitle, url = cleanUrl, description = cleanDescription,
            category = cat, folderId = folder?.id, tags = cleanTags,
            ageRange = "", location = "", price = "", visibility = "private",
        )
        CreatedIdea(id = link.id, title = link.title, category = cat.name, folderName = folder?.name, folderCreated = created)
    }

    /**
     * Creates a private Tribbae folder to group ideas. If a folder with this name already exists,
     * it is returned instead.
     *
     * @param name Folder name (required, at most 80 characters).
     * @return The folder.
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    fun createFolder(name: String): CreatedFolder = invalidAsArgument {
        val (folder, created) = findOrCreateFolder(name)
        CreatedFolder(id = folder.id, name = folder.name, alreadyExisted = !created)
    }

    /** Dossier modifiable de ce nom, sinon un nouveau dossier privé ; vrai s'il vient d'être créé. */
    private fun findOrCreateFolder(rawName: String): Pair<Folder, Boolean> {
        val name = AssistantRequests.folderName(rawName)
        val repository = AppGraph.repository(this)
        AssistantRequests.findFolder(repository.folders.value, SessionManager(this).getUserId(), name)
            ?.let { return it to false }
        return viewModel().addFolder(Folder(id = "", name = name, visibility = "PRIVATE")) to true
    }

    /** Session relue à chaque appel : l'utilisateur a pu se connecter ou se déconnecter entre-temps. */
    private fun viewModel() = AppGraph.assistantViewModel(this).also { it.attachSession(SessionManager(this)) }

    private inline fun <T> invalidAsArgument(block: () -> T): T =
        try {
            block()
        } catch (e: AssistantRequests.Invalid) {
            throw AppFunctionInvalidArgumentException(e.message)
        }
}
