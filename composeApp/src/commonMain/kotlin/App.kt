package com.linkkeeper.app

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.viewmodel.compose.viewModel
import data.*
import ui.*
import viewmodel.LinkViewModel

enum class Tab(val label: String, val icon: ImageVector) {
    HOME("Accueil", Icons.Default.Cabin),
    FOLDERS("Listes", Icons.Default.Folder),
    EXPLORE("Explorer", Icons.Default.Explore),
    CALENDAR("Agenda", Icons.Default.CalendarMonth),
    SETTINGS("Plus", Icons.Default.MoreHoriz)
}

@Composable
fun App(vm: LinkViewModel = viewModel(), sharedUrl: String? = null) {
    val context = LocalContext.current
    val sessionManager = remember { SessionManager(context) }
    val authRepository = remember { AuthRepository() }
    val isLoggedIn by sessionManager.isLoggedIn.collectAsState()
    
    // Initialiser le client API authentifié si connecté
    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn) {
            vm.initAuthenticatedClient(sessionManager)
            vm.syncWithBackend(sessionManager)
        }
    }
    
    MainApp(vm = vm, sharedUrl = sharedUrl, sessionManager = sessionManager, authRepository = authRepository)
}

@Composable
private fun MainApp(
    vm: LinkViewModel, 
    sharedUrl: String?, 
    sessionManager: SessionManager,
    authRepository: AuthRepository
) {
    var currentTab by remember { mutableStateOf(Tab.HOME) }
    var subScreen by remember { mutableStateOf<SubScreen?>(
        if (sharedUrl != null) SubScreen.AddLink else null
    ) }
    var selectedLink by remember { mutableStateOf<Link?>(null) }
    var selectedFolder by remember { mutableStateOf<Folder?>(null) }
    val initialUrl = remember { sharedUrl }

    // Intercepte le geste/bouton retour système quand un sous-écran est actif
    BackHandler(enabled = subScreen != null) {
        when (subScreen) {
            is SubScreen.Edit -> {
                subScreen = SubScreen.Detail
            }
            else -> subScreen = null
        }
    }

    // Sub-screens (pushed on top)
    when (subScreen) {
        is SubScreen.AddLink -> {
            AddLinkScreen(viewModel = vm, onBack = { subScreen = null }, initialUrl = initialUrl)
            return
        }
        is SubScreen.AddFolder -> {
            AddFolderScreen(viewModel = vm, onBack = { subScreen = null })
            return
        }
        is SubScreen.Detail -> {
            selectedLink?.let { link ->
                // Recharger le lien depuis le repo (au cas où il a été modifié)
                val freshLink = vm.repository.links.value.find { it.id == link.id } ?: link
                LinkDetailScreen(
                    link = freshLink,
                    onBack = { subScreen = null },
                    onDelete = { vm.deleteLink(freshLink.id); subScreen = null },
                    onEdit = { subScreen = SubScreen.Edit },
                    onOpenUrl = vm.urlOpener
                )
            }
            return
        }
        is SubScreen.PublicDetail -> {
            selectedLink?.let { link ->
                val folderList by vm.folders.collectAsState()
                LinkDetailScreen(
                    link = link,
                    onBack = { subScreen = null },
                    onDelete = { },
                    onOpenUrl = vm.urlOpener,
                    readOnly = true,
                    onSaveToMyList = { linkToSave, folderId ->
                        vm.addLink(
                            title = linkToSave.title,
                            url = linkToSave.url,
                            description = linkToSave.description,
                            category = linkToSave.category,
                            folderId = folderId,
                            tags = linkToSave.tags,
                            ageRange = linkToSave.ageRange,
                            location = linkToSave.location,
                            price = linkToSave.price,
                            imageUrl = linkToSave.imageUrl,
                            eventDate = linkToSave.eventDate,
                            rating = linkToSave.rating,
                            ingredients = linkToSave.ingredients
                        )
                    },
                    folders = folderList
                )
            }
            return
        }
        is SubScreen.Edit -> {
            selectedLink?.let { link ->
                val freshLink = vm.repository.links.value.find { it.id == link.id } ?: link
                EditLinkScreen(
                    link = freshLink,
                    viewModel = vm,
                    onBack = {
                        // Retour vers le détail avec le lien mis à jour
                        selectedLink = vm.repository.links.value.find { it.id == link.id }
                        subScreen = SubScreen.Detail
                    }
                )
            }
            return
        }
        is SubScreen.AiGenerate -> {
            AiGenerateScreen(viewModel = vm, onBack = { subScreen = null })
            return
        }
        is SubScreen.Shopping -> {
            ShoppingListScreen(viewModel = vm, modifier = Modifier)
            return
        }
        is SubScreen.EditFolder -> {
            selectedFolder?.let { folder ->
                val freshFolder = vm.repository.folders.value.find { it.id == folder.id } ?: folder
                EditFolderScreen(
                    folder = freshFolder,
                    viewModel = vm,
                    onBack = { subScreen = null }
                )
            }
            return
        }
        is SubScreen.Tags -> {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { subScreen = null }) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Retour", tint = Orange)
                    }
                    Text("Tags", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = TextPrimary)
                }
                TagsTabScreen(
                    viewModel = vm,
                    modifier = Modifier,
                    onLinkClick = { link -> selectedLink = link; subScreen = SubScreen.Detail }
                )
            }
            return
        }
        null -> {} // continue to tabs
    }

    Scaffold(
        containerColor = SurfaceColor,
        bottomBar = {
            NavigationBar(
                containerColor = CardColor,
                tonalElevation = 16.dp
            ) {
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = currentTab == tab,
                        onClick = { currentTab = tab },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label, fontWeight = FontWeight.Medium) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Orange,
                            selectedTextColor = Orange,
                            indicatorColor = Orange.copy(alpha = 0.12f)
                        )
                    )
                }
            }
        }
    ) { padding ->
        when (currentTab) {
            Tab.HOME -> HomeScreen(
                viewModel = vm,
                modifier = Modifier.padding(padding),
                onAddClick = { subScreen = SubScreen.AddLink },
                onAiClick = { subScreen = SubScreen.AiGenerate },
                onLinkClick = { link -> selectedLink = link; subScreen = SubScreen.Detail }
            )
            Tab.FOLDERS -> FoldersTabScreen(
                viewModel = vm,
                modifier = Modifier.padding(padding),
                onAddFolderClick = { subScreen = SubScreen.AddFolder },
                onEditFolderClick = { folder -> selectedFolder = folder; subScreen = SubScreen.EditFolder },
                onLinkClick = { link -> selectedLink = link; subScreen = SubScreen.Detail }
            )
            Tab.EXPLORE -> CommunityScreen(
                modifier = Modifier.padding(padding),
                apiClient = vm.apiClient,
                onLinkClick = { link -> selectedLink = link; subScreen = SubScreen.PublicDetail }
            )
            Tab.CALENDAR -> CalendarScreen(
                viewModel = vm,
                modifier = Modifier.padding(padding),
                onLinkClick = { link -> selectedLink = link; subScreen = SubScreen.Detail }
            )
            Tab.SETTINGS -> SettingsScreen(
                viewModel = vm,
                modifier = Modifier.padding(padding),
                onShoppingClick = { subScreen = SubScreen.Shopping },
                onTagsClick = { subScreen = SubScreen.Tags },
                sessionManager = sessionManager,
                authRepository = authRepository,
                onLoginSuccess = {
                    vm.syncWithBackend(sessionManager)
                }
            )
        }
    }
}

sealed class SubScreen {
    object AddLink : SubScreen()
    object AddFolder : SubScreen()
    object Detail : SubScreen()
    object PublicDetail : SubScreen()
    object Edit : SubScreen()
    object EditFolder : SubScreen()
    object AiGenerate : SubScreen()
    object Shopping : SubScreen()
    object Tags : SubScreen()
}
