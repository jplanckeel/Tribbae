package com.linkkeeper.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import data.*
import ui.*
import ui.components.ModernBottomNav
import ui.components.NavDestination
import viewmodel.LinkViewModel

@Composable
fun AppModern(vm: LinkViewModel = viewModel(), sharedUrl: String? = null) {
    val context = LocalContext.current
    val sessionManager = remember { SessionManager(context) }
    val authRepository = remember { AuthRepository() }
    val followRepository = remember { FollowRepository(sessionManager = sessionManager) }
    val commentRepository = remember { CommentRepository(sessionManager = sessionManager) }
    val isLoggedIn by sessionManager.isLoggedIn.collectAsState()

    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn) {
            vm.initAuthenticatedClient(sessionManager)
            vm.syncWithBackend(sessionManager)
        }
    }

    ModernMainApp(vm, sharedUrl, sessionManager, authRepository, followRepository, commentRepository)
}

/** Écrans empilés au-dessus des onglets (navigation par état, comme avant). */
private sealed class Screen {
    data class IdeaForm(
        val existing: Link? = null,
        val url: String? = null,
        val category: LinkCategory? = null,
        val folderId: String? = null,
    ) : Screen()
    data class FolderForm(val existing: Folder? = null) : Screen()
    data class Detail(val link: Link, val public: Boolean = false) : Screen()
    data class FolderDetail(val folder: Folder) : Screen()
    data class Category(val category: LinkCategory?) : Screen()
    data class Ai(val folderId: String? = null, val prompt: String = "") : Screen()
    object Shopping : Screen()
    object Faq : Screen()
}

@Composable
private fun ModernMainApp(
    vm: LinkViewModel,
    sharedUrl: String?,
    sessionManager: SessionManager,
    authRepository: AuthRepository,
    followRepository: FollowRepository,
    commentRepository: CommentRepository
) {
    var tab by remember { mutableStateOf(NavDestination.HOME) }
    // Pile d'écrans : retour = dépiler
    val stack = remember { mutableStateListOf<Screen>().apply { if (sharedUrl != null) add(Screen.IdeaForm(url = sharedUrl)) } }
    var showAdd by remember { mutableStateOf(false) }
    var exploreQuery by remember { mutableStateOf("") }
    var exploreCategory by remember { mutableStateOf<LinkCategory?>(null) }

    val links by vm.repository.links.collectAsState()
    val folders by vm.folders.collectAsState()
    val communityLinks by vm.communityLinks.collectAsState()

    fun push(s: Screen) { stack.add(s) }
    fun pop() { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) }
    fun openLink(id: String) { links.find { it.id == id }?.let { push(Screen.Detail(it)) } }
    fun openExplore(query: String = "", category: LinkCategory? = null) {
        exploreQuery = query; exploreCategory = category
        stack.clear(); tab = NavDestination.EXPLORE
    }

    BackHandler(enabled = stack.isNotEmpty()) { pop() }

    Box(Modifier.fillMaxSize().background(ui.Sable)) {
        when (val screen = stack.lastOrNull()) {
            null -> {
                when (tab) {
                    NavDestination.HOME, NavDestination.ADD -> NewHomeScreen(
                        links = links,
                        onNavigateToExplore = { openExplore() },
                        onNavigateToCategory = { push(Screen.Category(it)) },
                        onNavigateToDetail = ::openLink,
                        onSaveLink = { vm.toggleFavorite(it.id) },
                        sessionManager = sessionManager,
                        onNavigateToAI = { push(Screen.Ai()) },
                        viewModel = vm,
                        onSearch = { openExplore(query = it) },
                        onOpenCommunityLink = { push(Screen.Detail(it, public = it.ownerId != sessionManager.getUserId())) },
                        onAddWithCategory = { push(Screen.IdeaForm(category = it)) },
                        onNavigateToTribe = { tab = NavDestination.PROFILE },
                    )
                    NavDestination.EXPLORE -> {
                        LaunchedEffect(Unit) { vm.loadCommunityLinks() }
                        ExploreScreen(
                            links = communityLinks,
                            onNavigateBack = { tab = NavDestination.HOME },
                            onNavigateToDetail = { id ->
                                communityLinks.find { it.id == id }?.let { l ->
                                    // Ses propres idées publiques s'ouvrent en mode édition
                                    val mine = links.find { it.id == id }
                                    push(if (mine != null) Screen.Detail(mine) else Screen.Detail(l, public = true))
                                }
                            },
                            onSaveLink = { vm.toggleFavorite(it.id) },
                            viewModel = vm,
                            initialQuery = exploreQuery,
                            initialCategory = exploreCategory,
                            onNavigateToAI = { push(Screen.Ai(prompt = exploreQuery)) },
                        )
                    }
                    NavDestination.MY_IDEAS -> MyIdeasScreen(
                        links = links,
                        folders = folders,
                        onNavigateToDetail = ::openLink,
                        onSaveLink = { vm.toggleFavorite(it.id) },
                        onAddFolderClick = { push(Screen.FolderForm()) },
                        onFolderClick = { push(Screen.FolderDetail(it)) },
                        onNavigateToAdd = { showAdd = true },
                        onNavigateToCategory = { push(Screen.Category(it)) },
                        viewModel = vm,
                        onOpenShopping = { push(Screen.Shopping) },
                        onOpenLoose = { push(Screen.Category(null)) },
                    )
                    NavDestination.PROFILE -> ProfileModernScreen(
                        viewModel = vm,
                        modifier = Modifier.fillMaxSize(),
                        sessionManager = sessionManager,
                        authRepository = authRepository,
                        onLoginSuccess = { vm.syncWithBackend(sessionManager) },
                        onNavigateToFaq = { push(Screen.Faq) },
                        onOpenShopping = { push(Screen.Shopping) },
                    )
                }
                ModernBottomNav(
                    currentDestination = tab,
                    onNavigate = { d -> if (d == NavDestination.ADD) showAdd = true else tab = d },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }

            is Screen.IdeaForm -> IdeaFormScreen(
                viewModel = vm,
                onBack = ::pop,
                existing = screen.existing?.let { e -> links.find { it.id == e.id } ?: e },
                initialUrl = screen.url,
                initialCategory = screen.category,
                initialFolderId = screen.folderId,
                onSaved = {
                    pop()
                    // Après une modification, la fiche affichée reprend la version à jour
                    val top = stack.lastOrNull()
                    if (top is Screen.Detail) links.find { it.id == top.link.id }?.let { stack[stack.lastIndex] = top.copy(link = it) }
                },
            )

            is Screen.FolderForm -> FolderFormScreen(vm, ::pop, screen.existing?.let { e -> folders.find { it.id == e.id } ?: e })

            is Screen.Detail -> {
                val fresh = if (screen.public) screen.link else links.find { it.id == screen.link.id } ?: screen.link
                LinkDetailScreen(
                    link = fresh,
                    onBack = ::pop,
                    onDelete = { vm.deleteLink(fresh.id); pop() },
                    onEdit = { push(Screen.IdeaForm(existing = fresh)) },
                    onOpenUrl = vm.urlOpener,
                    readOnly = screen.public,
                    onSaveToMyList = if (screen.public) { l, folderId ->
                        vm.addLink(
                            title = l.title, url = l.url, description = l.description, category = l.category,
                            folderId = folderId, tags = l.tags, ageRange = l.ageRange, location = l.location,
                            price = l.price, imageUrl = l.imageUrl, rating = l.rating, ingredients = l.ingredients
                        )
                    } else null,
                    folders = folders,
                    followRepository = followRepository,
                    sessionManager = sessionManager,
                    commentRepository = commentRepository,
                    viewModel = vm
                )
            }

            is Screen.FolderDetail -> {
                val fresh = folders.find { it.id == screen.folder.id } ?: screen.folder
                FolderDetailScreen(
                    folder = fresh,
                    links = links,
                    onBack = ::pop,
                    onEdit = { push(Screen.FolderForm(fresh)) },
                    onDelete = { vm.deleteFolder(fresh.id) { pop() } },
                    onLinkClick = ::openLink,
                    onSaveLink = { vm.toggleFavorite(it.id) },
                    viewModel = vm,
                    onAddIdea = { push(Screen.IdeaForm(folderId = fresh.id)) },
                    onAi = { push(Screen.Ai(folderId = fresh.id, prompt = fresh.name)) },
                )
            }

            is Screen.Category -> {
                val ids = folders.map { it.id }.toSet()
                CategoryDetailScreen(
                    category = screen.category,
                    links = if (screen.category != null) links.filter { it.category == screen.category }
                    else links.filter { it.folderId == null || it.folderId !in ids },
                    onBack = ::pop,
                    onLinkClick = { push(Screen.Detail(it)) },
                    onSaveLink = { vm.toggleFavorite(it.id) },
                    onAdd = { push(Screen.IdeaForm(category = screen.category)) },
                    onExplore = { openExplore(category = screen.category) },
                )
            }

            is Screen.Ai -> AiGenerateScreen(
                viewModel = vm,
                onBack = ::pop,
                initialFolderId = screen.folderId,
                initialPrompt = screen.prompt,
                onSavedInFolder = { id ->
                    pop()
                    val target = folders.find { it.id == id }
                    if (id != null && stack.lastOrNull() !is Screen.FolderDetail) {
                        push(Screen.FolderDetail(target ?: Folder(id = id, name = "Idées de l'IA")))
                    }
                },
            )

            Screen.Shopping -> ShoppingListScreen(vm, onBack = ::pop, onAddRecipe = { push(Screen.IdeaForm(category = LinkCategory.RECETTE)) })

            Screen.Faq -> FaqScreen(onBack = ::pop)
        }

        if (showAdd) {
            AddSheet(onDismiss = { showAdd = false }) { choice ->
                showAdd = false
                val folderId = (stack.lastOrNull() as? Screen.FolderDetail)?.folder?.id
                when (choice) {
                    is AddChoice.FromUrl -> push(Screen.IdeaForm(url = choice.url, folderId = folderId))
                    is AddChoice.FromCategory -> push(Screen.IdeaForm(category = choice.category, folderId = folderId))
                    AddChoice.Folder -> push(Screen.FolderForm())
                    AddChoice.Ai -> push(Screen.Ai(folderId = folderId))
                }
            }
        }
    }
}
