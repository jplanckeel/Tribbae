package com.linkkeeper.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    val isLoggedIn by sessionManager.isLoggedIn.collectAsState()
    
    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn) {
            vm.initAuthenticatedClient(sessionManager)
            vm.syncWithBackend(sessionManager)
        }
    }
    
    ModernMainApp(
        vm = vm, 
        sharedUrl = sharedUrl, 
        sessionManager = sessionManager, 
        authRepository = authRepository
    )
}

@Composable
private fun ModernMainApp(
    vm: LinkViewModel, 
    sharedUrl: String?, 
    sessionManager: SessionManager,
    authRepository: AuthRepository
) {
    var currentDestination by remember { mutableStateOf(NavDestination.HOME) }
    var subScreen by remember { mutableStateOf<SubScreen?>(
        if (sharedUrl != null) SubScreen.AddLink else null
    ) }
    var selectedLink by remember { mutableStateOf<Link?>(null) }
    var selectedFolder by remember { mutableStateOf<Folder?>(null) }
    var selectedCategory by remember { mutableStateOf<LinkCategory?>(null) }
    val initialUrl = remember { sharedUrl }

    val links by vm.repository.links.collectAsState()
    val folders by vm.folders.collectAsState()

    BackHandler(enabled = subScreen != null) {
        when (subScreen) {
            is SubScreen.Edit -> subScreen = SubScreen.Detail
            else -> subScreen = null
        }
    }

    // Gestion des sous-écrans
    when (subScreen) {
        is SubScreen.AddLink -> {
            ModernAddLinkScreen(viewModel = vm, onBack = { subScreen = null }, initialUrl = initialUrl)
            return
        }
        is SubScreen.AddFolder -> {
            ModernAddFolderScreen(viewModel = vm, onBack = { subScreen = null })
            return
        }
        is SubScreen.Detail -> {
            selectedLink?.let { link ->
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
                    folders = folders
                )
            }
            return
        }
        is SubScreen.Edit -> {
            selectedLink?.let { link ->
                val freshLink = vm.repository.links.value.find { it.id == link.id } ?: link
                ModernEditLinkScreen(
                    link = freshLink,
                    viewModel = vm,
                    onBack = {
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
                ModernEditFolderScreen(
                    folder = freshFolder,
                    viewModel = vm,
                    onBack = { subScreen = null }
                )
            }
            return
        }
        is SubScreen.FolderDetail -> {
            selectedFolder?.let { folder ->
                val freshFolder = vm.repository.folders.value.find { it.id == folder.id } ?: folder
                FolderDetailScreen(
                    folder = freshFolder,
                    links = links,
                    onBack = { subScreen = null },
                    onEdit = { subScreen = SubScreen.EditFolder },
                    onLinkClick = { linkId ->
                        selectedLink = links.find { it.id == linkId }
                        subScreen = SubScreen.Detail
                    },
                    onSaveLink = { link -> vm.toggleFavorite(link.id) }
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
        is SubScreen.Category -> {
            selectedCategory?.let { category ->
                CategoryDetailScreen(
                    category = category,
                    links = links.filter { link: Link -> link.category == category },
                    onBack = { subScreen = null },
                    onLinkClick = { link -> selectedLink = link; subScreen = SubScreen.Detail },
                    onSaveLink = { link -> vm.toggleFavorite(link.id) }
                )
            }
            return
        }
        null -> {} // Continue vers les écrans principaux
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Contenu principal
        when (currentDestination) {
            NavDestination.HOME -> {
                NewHomeScreen(
                    links = links,
                    onNavigateToExplore = { currentDestination = NavDestination.EXPLORE },
                    onNavigateToCategory = { category ->
                        selectedCategory = category
                        subScreen = SubScreen.Category
                    },
                    onNavigateToDetail = { linkId ->
                        selectedLink = links.find { link: Link -> link.id == linkId }
                        subScreen = SubScreen.Detail
                    },
                    onSaveLink = { link -> vm.toggleFavorite(link.id) },
                    sessionManager = sessionManager
                )
            }
            NavDestination.EXPLORE -> {
                ExploreScreen(
                    links = links,
                    onNavigateBack = { currentDestination = NavDestination.HOME },
                    onNavigateToDetail = { linkId ->
                        selectedLink = links.find { link: Link -> link.id == linkId }
                        subScreen = SubScreen.Detail
                    },
                    onSaveLink = { link -> vm.toggleFavorite(link.id) }
                )
            }
            NavDestination.ADD -> {
                subScreen = SubScreen.AddLink
            }
            NavDestination.MY_IDEAS -> {
                MyIdeasScreen(
                    links = links,
                    folders = folders,
                    onNavigateToDetail = { linkId ->
                        selectedLink = links.find { link: Link -> link.id == linkId }
                        subScreen = SubScreen.Detail
                    },
                    onSaveLink = { link -> vm.toggleFavorite(link.id) },
                    onAddFolderClick = { subScreen = SubScreen.AddFolder },
                    onFolderClick = { folder ->
                        selectedFolder = folder
                        subScreen = SubScreen.FolderDetail
                    },
                    onNavigateToAdd = { subScreen = SubScreen.AddLink },
                    onNavigateToCategory = { category ->
                        selectedCategory = category
                        subScreen = SubScreen.Category
                    }
                )
            }
            NavDestination.PROFILE -> {
                ProfileModernScreen(
                    viewModel = vm,
                    modifier = Modifier.fillMaxSize(),
                    sessionManager = sessionManager,
                    authRepository = authRepository,
                    onLoginSuccess = {
                        vm.syncWithBackend(sessionManager)
                    }
                )
            }
        }

        // Bottom Navigation
        ModernBottomNav(
            currentDestination = currentDestination,
            onNavigate = { destination ->
                if (destination == NavDestination.ADD) {
                    subScreen = SubScreen.AddLink
                } else {
                    currentDestination = destination
                }
            },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}
