package com.linkkeeper.app

import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.compose.runtime.withFrameNanos
import kotlin.coroutines.cancellation.CancellationException
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.key
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.layout.layout
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import data.*
import ui.*
import ui.components.ModernBottomNav
import ui.components.NavDestination
import ui.components.rememberBottomNavScrollState
import viewmodel.LinkViewModel

/**
 * Contenu reçu d'une autre application : lien ou texte partagé, lien de partage
 * de dossier, lien d'invitation dans une tribu, lien « nouveau mot de passe »
 * ou de confirmation d'adresse d'un email. `id` distingue deux partages
 * identiques successifs.
 */
data class SharedContent(
    val url: String? = null,
    val title: String? = null,
    val folderToken: String? = null,
    val inviteToken: String? = null,
    val resetToken: String? = null,
    val verifyToken: String? = null,
    val id: Long = System.nanoTime(),
)

@Composable
fun AppModern(vm: LinkViewModel = viewModel(), incoming: SharedContent? = null) {
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
            vm.refreshAccount(sessionManager)
        }
    }
    // Session glissante : jeton renouvelé aussi au retour sur l'app, et adresse
    // email relue tant qu'elle n'est pas confirmée (lien ouvert ailleurs)
    LifecycleResumeEffect(isLoggedIn) {
        if (isLoggedIn) {
            vm.renewSessionIfStale(sessionManager)
            vm.refreshAccount(sessionManager)
        }
        onPauseOrDispose { }
    }

    // Compte connecté : les cartes signent du nom de leur auteur les idées des autres (tribu, communauté)
    val currentUserId by sessionManager.userId.collectAsState()
    CompositionLocalProvider(ui.campfire.LocalCurrentUserId provides currentUserId) {
        ModernMainApp(vm, incoming, sessionManager, authRepository, followRepository, commentRepository)
    }
}

/** Écran du haut de la pile et sa profondeur (0 = onglets). */
private data class StackEntry(val depth: Int, val screen: Screen?)

/** Écrans empilés au-dessus des onglets (navigation par état, comme avant). */
private sealed class Screen {
    data class IdeaForm(
        val existing: Link? = null,
        val url: String? = null,
        val category: LinkCategory? = null,
        val folderId: String? = null,
        val title: String? = null,
    ) : Screen()
    data class FolderForm(val existing: Folder? = null) : Screen()
    /** `origin` : visuel de la carte touchée, d'où la fiche s'ouvre (et où elle revient). */
    data class Detail(val link: Link, val public: Boolean = false, val origin: Rect? = CardOrigins[link.id]) : Screen()
    /** `fromLinkId` : idée d'où l'on a « creusé » vers ce dossier, surlignée. */
    data class FolderDetail(val folder: Folder, val fromLinkId: String? = null) : Screen()
    data class Category(val category: LinkCategory?) : Screen()
    data class Ai(val folderId: String? = null, val prompt: String = "", val childId: String? = null) : Screen()
    object Shopping : Screen()
    object Faq : Screen()
    object Admin : Screen()
    /** Dossier d'un autre membre : public (folder) ou ouvert par lien de partage (shareToken). */
    data class PublicFolder(val folder: Folder? = null, val shareToken: String? = null) : Screen()
}

@Composable
private fun ModernMainApp(
    vm: LinkViewModel,
    incoming: SharedContent?,
    sessionManager: SessionManager,
    authRepository: AuthRepository,
    followRepository: FollowRepository,
    commentRepository: CommentRepository
) {
    var tab by remember { mutableStateOf(NavDestination.HOME) }
    // Onglets déjà ouverts : gardés en mémoire (voir keptHidden)
    val visitedTabs = remember { mutableStateListOf(NavDestination.HOME) }
    // Idées de la communauté (accueil, Explorer) : rechargées en revenant sur
    // l'onglet seulement si elles datent, pas à chaque visite
    LaunchedEffect(tab) { if (tab == NavDestination.HOME || tab == NavDestination.EXPLORE) vm.refreshCommunityIfStale() }
    // Pile d'écrans : retour = dépiler
    val stack = remember { mutableStateListOf<Screen>() }
    // Lien d'invitation dans une tribu ; masqué le temps de se connecter
    var inviteToken by remember { mutableStateOf<String?>(null) }
    var inviteHidden by remember { mutableStateOf(false) }
    val loggedIn by sessionManager.isLoggedIn.collectAsState()
    LaunchedEffect(loggedIn) { if (loggedIn) inviteHidden = false }
    // Lien « nouveau mot de passe » ouvert depuis l'email
    var resetToken by remember { mutableStateOf<String?>(null) }
    // Lien de confirmation de l'adresse email
    var verifyToken by remember { mutableStateOf<String?>(null) }
    // Partage reçu (au lancement ou app déjà ouverte) : on ouvre l'écran correspondant
    LaunchedEffect(incoming?.id) {
        val s = incoming ?: return@LaunchedEffect
        if (s.url != null || s.title != null) stack.add(Screen.IdeaForm(url = s.url, title = s.title))
        if (s.folderToken != null) stack.add(Screen.PublicFolder(shareToken = s.folderToken))
        if (s.inviteToken != null) { inviteToken = s.inviteToken; inviteHidden = false }
        if (s.resetToken != null) resetToken = s.resetToken
        if (s.verifyToken != null) verifyToken = s.verifyToken
    }
    var showAdd by remember { mutableStateOf(false) }
    var exploreQuery by remember { mutableStateOf("") }
    var exploreCategory by remember { mutableStateOf<LinkCategory?>(null) }

    val links by vm.repository.links.collectAsState()
    val folders by vm.folders.collectAsState()
    val communityLinks by vm.communityLinks.collectAsState()

    // Barre masquée pendant le défilement ; elle réapparaît à chaque changement d'écran
    val navScroll = rememberBottomNavScrollState()
    fun push(s: Screen) { stack.add(s); navScroll.hidden = false }
    fun pop() { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex); navScroll.hidden = false }
    // Un dossier de la communauté dont je suis membre s'ouvre en mode normal
    fun openCommunityFolder(f: Folder) {
        push(folders.find { it.id == f.id }?.let { Screen.FolderDetail(it) } ?: Screen.PublicFolder(folder = f))
    }
    fun openLink(id: String) { links.find { it.id == id }?.let { push(Screen.Detail(it)) } }
    fun openExplore(query: String = "", category: LinkCategory? = null) {
        exploreQuery = query; exploreCategory = category
        stack.clear(); tab = NavDestination.EXPLORE
    }

    BackHandler(enabled = stack.isNotEmpty() && stack.lastOrNull() !is Screen.Detail) { pop() }
    // Fiche d'une idée : retour prédictif (Android 14+) — en glissant depuis le bord,
    // la fiche rétrécit à 92 % et arrondit ses coins avant de repartir vers sa carte
    var backProgress by remember { mutableFloatStateOf(0f) }
    PredictiveBackHandler(enabled = stack.lastOrNull() is Screen.Detail) { events ->
        try {
            events.collect { backProgress = it.progress }
            pop()
        } catch (e: CancellationException) {
            backProgress = 0f
            throw e
        }
    }

    Box(Modifier.fillMaxSize().background(ui.Sable).nestedScroll(navScroll.connection)) {
        val top = stack.lastOrNull()
        val current = if (tab == NavDestination.ADD) NavDestination.HOME else tab
        LaunchedEffect(current) { if (current !in visitedTabs) visitedTabs.add(current) }
        // Changer d'onglet : fondu + 26 dp dans le sens de l'onglet choisi.
        // L'état de départ est fixé pendant la composition (pas dans l'effet) : la
        // première image du nouvel onglet est déjà décalée, sans saut à sa place finale.
        var tabFrom by remember { mutableStateOf(current) }
        val tabDir = remember(current) { if (current.ordinal >= tabFrom.ordinal) 1f else -1f }
        val tabEnter = remember(current) { Animatable(if (current == tabFrom) 1f else 0f) }
        LaunchedEffect(current) {
            tabFrom = current
            if (tabEnter.value < 1f) {
                // Laisse passer l'image où l'onglet se compose (première visite) avant d'animer
                withFrameNanos { }
                tabEnter.animateTo(1f, tween(TAB_MS, easing = EaseSoft))
            }
        }

        // Pile d'écrans animée : la fiche sort de sa carte, les autres pages arrivent par la droite
        val nav = updateTransition(StackEntry(stack.size, top), label = "pile")
        val covering = nav.targetState.screen ?: nav.currentState.screen
        val heroCover = covering is Screen.Detail && covering.origin != null &&
            (nav.targetState.depth <= 1 && nav.currentState.depth <= 1)
        val baseCover by nav.animateFloat(
            transitionSpec = { tween(if (heroCover) HERO_MS else PUSH_MS, easing = EaseSoft) }, label = "onglets"
        ) { if (it.screen == null) 0f else 1f }
        // Onglets : chacun est composé à sa première visite puis gardé, simplement
        // masqué quand un autre onglet ou écran est devant. Y revenir est instantané
        // et conserve le défilement (avant, l'écran était reconstruit à chaque fois).
        Box(
            Modifier.fillMaxSize()
                // Masqués seulement une fois entièrement recouverts (pas pendant l'ouverture d'un écran)
                .keptHidden(nav.currentState.screen != null && nav.targetState.screen != null)
                .graphicsLayer { if (!heroCover) translationX = -0.3f * size.width * baseCover }
                .drawWithContent {
                    drawContent()
                    if (baseCover > 0f) drawRect(Color(0xFF2A1612), alpha = (if (heroCover) 0.25f else 0.35f) * baseCover)
                }
        ) {
            NavDestination.entries.filter { it == current || it in visitedTabs }.forEach { t ->
                key(t) {
                Box(
                    Modifier.fillMaxSize().keptHidden(t != current).graphicsLayer {
                        if (t == current && tabEnter.value < 1f) {
                            alpha = tabEnter.value
                            translationX = (1f - tabEnter.value) * tabDir * 26.dp.toPx()
                        }
                    }
                ) {
                when (t) {
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
                            onOpenFolder = ::openCommunityFolder,
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
                        onOpenAdmin = { push(Screen.Admin) },
                        onOpenAi = { push(Screen.Ai(childId = it)) },
                        onOpenFolder = { push(Screen.FolderDetail(it)) },
                    )
                }
                }
                }
            }
                val invite = inviteToken
                if (invite != null && !inviteHidden) {
                    InvitationDialog(
                        token = invite,
                        loggedIn = loggedIn,
                        onAccept = { done ->
                            vm.acceptTribeInvitation(invite) { err ->
                                done(err)
                                if (err == null) { inviteToken = null; stack.clear(); tab = NavDestination.PROFILE }
                            }
                        },
                        // Connexion depuis l'onglet « Ma tribu » ; la boîte revient une fois connecté
                        onSignIn = { inviteHidden = true; stack.clear(); tab = NavDestination.PROFILE },
                        onDismiss = { inviteToken = null },
                    )
                }
                verifyToken?.let { token ->
                    VerifyEmailDialog(
                        token = token,
                        authRepository = authRepository,
                        viewModel = vm,
                        loggedIn = loggedIn,
                        onDismiss = { verifyToken = null },
                    )
                }
                resetToken?.let { token ->
                    NewPasswordDialog(
                        token = token,
                        authRepository = authRepository,
                        onDismiss = { resetToken = null },
                        onDone = { r ->
                            // Lien d'un autre compte que celui connecté : on ne mélange pas leurs données
                            if (sessionManager.isLoggedIn.value && sessionManager.userId.value != r.userId) vm.signOut()
                            sessionManager.saveSession(r.userId, r.token, r.displayName, r.isAdmin, r.emailVerified)
                            vm.initAuthenticatedClient(sessionManager)
                            vm.syncWithBackend(sessionManager)
                            resetToken = null
                        },
                    )
                }
                ModernBottomNav(
                    currentDestination = tab,
                    onNavigate = { d ->
                        navScroll.hidden = false
                        if (d == NavDestination.ADD) showAdd = true else tab = d
                    },
                    // La barre descend quand une fiche s'ouvre depuis sa carte
                    modifier = Modifier.align(Alignment.BottomCenter).graphicsLayer { if (heroCover) translationY = baseCover * 130.dp.toPx() },
                    hidden = navScroll.hidden
                )
        }

        val screenContent: @Composable (Screen) -> Unit = { screen -> when (screen) {
            is Screen.IdeaForm -> IdeaFormScreen(
                viewModel = vm,
                onBack = ::pop,
                existing = screen.existing?.let { e -> links.find { it.id == e.id } ?: e },
                initialUrl = screen.url,
                initialCategory = screen.category,
                initialFolderId = screen.folderId,
                initialTitle = screen.title,
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
                    viewModel = vm,
                    onOpenFolder = { f -> push(Screen.FolderDetail(f, fromLinkId = fresh.id)) },
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
                    highlightLinkId = screen.fromLinkId,
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
                childId = screen.childId,
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
            is Screen.PublicFolder -> PublicFolderScreen(
                folder = screen.folder,
                shareToken = screen.shareToken,
                viewModel = vm,
                onBack = ::pop,
                onLinkClick = { l ->
                    val mine = links.find { it.id == l.id }
                    push(if (mine != null) Screen.Detail(mine) else Screen.Detail(l, public = true))
                },
            )
            Screen.Admin -> AdminScreen(
                adminRepository = remember { AdminRepository(tokenProvider = { sessionManager.getToken() }) },
                modifier = Modifier.fillMaxSize().statusBarsPadding(),
                onBack = ::pop
            )
        } }

        nav.AnimatedContent(
            transitionSpec = {
                val forward = targetState.depth > initialState.depth
                val heroIn = forward && (targetState.screen as? Screen.Detail)?.origin != null
                val heroOut = !forward && (initialState.screen as? Screen.Detail)?.origin != null
                val slide = tween<IntOffset>(PUSH_MS, easing = EaseSoft)
                when {
                    // La fiche grandit depuis sa carte (ou y revient) : animée dans son contenu
                    heroIn -> EnterTransition.None togetherWith ExitTransition.KeepUntilTransitionsFinished
                    heroOut -> (EnterTransition.None togetherWith ExitTransition.KeepUntilTransitionsFinished).apply { targetContentZIndex = -1f }
                    // Creuser : la page arrive par la droite, la précédente recule de 30 %
                    forward -> slideInHorizontally(slide) { it } togetherWith slideOutHorizontally(slide) { -(it * 0.3f).toInt() }
                    else -> (slideInHorizontally(slide) { -(it * 0.3f).toInt() } togetherWith slideOutHorizontally(slide) { it })
                        .apply { targetContentZIndex = -1f }
                }.using(SizeTransform(clip = false))
            },
            contentKey = { "${it.depth}:${it.screen?.let { s -> s::class.simpleName }}" },
        ) { entry ->
            val screen = entry.screen ?: return@AnimatedContent Box(Modifier.fillMaxSize())
            val other = if (nav.targetState.depth == entry.depth) nav.currentState else nav.targetState
            val lower = entry.depth < other.depth
            val hero = (screen as? Screen.Detail)?.origin?.takeIf { lower.not() }
            // Page recouverte : elle s'assombrit (voile plus léger sous une fiche qui s'ouvre)
            val dim by transition.animateFloat(transitionSpec = { tween(PUSH_MS, easing = EaseSoft) }, label = "voile") {
                if (it == EnterExitState.Visible || !lower) 0f else if ((other.screen as? Screen.Detail)?.origin != null) 0.25f else 0.35f
            }
            val open by transition.animateFloat(transitionSpec = { tween(HERO_MS, easing = EaseHero) }, label = "ouverture") {
                if (it == EnterExitState.Visible || hero == null) 1f else 0f
            }
            val back = if (screen is Screen.Detail && entry.depth == stack.size) backProgress else 0f
            Box(
                Modifier.fillMaxSize()
                    .graphicsLayer {
                        if (back > 0f) {
                            val s = 1f - 0.08f * back
                            scaleX = s; scaleY = s
                            shape = RoundedCornerShape(32.dp * back)
                            clip = true
                        }
                    }
                    .then(if (hero != null) Modifier.heroFrom(hero) { open } else Modifier)
                    .drawWithContent {
                        drawContent()
                        if (dim > 0f) drawRect(Color(0xFF2A1612), alpha = dim)
                    }
            ) {
                CompositionLocalProvider(LocalOpenProgress provides { open }) { screenContent(screen) }
            }
        }
        // Une fois la fiche refermée, le retour prédictif repart de zéro
        LaunchedEffect(nav.isRunning, stack.size) { if (!nav.isRunning && stack.lastOrNull() !is Screen.Detail) backProgress = 0f }

        if (showAdd) {
            val folderId = (stack.lastOrNull() as? Screen.FolderDetail)?.folder?.id
            AddFlowSheet(
                viewModel = vm,
                presetFolderId = folderId,
                onDismiss = { showAdd = false },
                onAi = { showAdd = false; push(Screen.Ai(folderId = folderId)) },
                onMore = { d -> showAdd = false; push(Screen.IdeaForm(url = d.url, category = d.category, folderId = d.folderId, title = d.title)) },
                onNewFolder = { showAdd = false; push(Screen.FolderForm()) },
                onOpenLink = { id -> showAdd = false; openLink(id) },
            )
        }
    }
}

/**
 * Ouverture d'une fiche depuis sa carte (« container transform ») : la fiche,
 * mise à l'échelle de la carte, grandit jusqu'au plein écran depuis `origin`
 * (coordonnées de la fenêtre) en arrondissant puis redressant ses coins.
 * `progress` : 0 = carte, 1 = fiche.
 */
private fun Modifier.heroFrom(origin: Rect, progress: () -> Float): Modifier = graphicsLayer {
    val t = progress()
    if (t >= 1f || size.width <= 0f) return@graphicsLayer
    val s = lerp(origin.width / size.width, 1f, t)
    transformOrigin = TransformOrigin(0f, 0f)
    scaleX = s; scaleY = s
    translationX = lerp(origin.left, 0f, t)
    translationY = lerp(origin.top, 0f, t)
    val visible = (lerp(origin.height, size.height, t) / s).coerceAtMost(size.height)
    val radius = lerp(22.dp.toPx(), 0f, t.coerceIn(0f, 1f)) / s
    shape = GenericShape { sz, _ -> addRoundRect(RoundRect(0f, 0f, sz.width, visible, CornerRadius(radius))) }
    clip = true
    alpha = (t / 0.15f).coerceIn(0f, 1f)
}

/**
 * Garde un contenu composé (état, défilement, images chargées) sans le placer :
 * il n'est ni dessiné, ni touchable, ni lu par TalkBack, et ne coûte rien tant
 * qu'il ne change pas.
 */
private fun Modifier.keptHidden(hidden: Boolean): Modifier =
    if (!hidden) this
    else this.layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        layout(placeable.width, placeable.height) {}
    }.clearAndSetSemantics {}
