package com.linkkeeper.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.viewmodel.compose.viewModel
import data.ApiClient
import data.Link
import ui.*
import viewmodel.LinkViewModel

enum class Tab(val label: String, val icon: ImageVector) {
    HOME("Accueil", Icons.Default.Home),
    FOLDERS("Listes", Icons.Default.Folder),
    CALENDAR("Agenda", Icons.Default.CalendarMonth),
    TAGS("Tags", Icons.Default.Tag),
    SHOPPING("Courses", Icons.Default.ShoppingCart),
    SETTINGS("Plus", Icons.Default.MoreHoriz)
}

@Composable
fun App(vm: LinkViewModel = viewModel(), sharedUrl: String? = null) {
    var currentTab by remember { mutableStateOf(Tab.HOME) }
    var subScreen by remember { mutableStateOf<SubScreen?>(
        if (sharedUrl != null) SubScreen.AddLink else null
    ) }
    var selectedLink by remember { mutableStateOf<Link?>(null) }
    val initialUrl = remember { sharedUrl }

    // Intercepte le geste/bouton retour système quand un sous-écran est actif
    BackHandler(enabled = subScreen != null) {
        when (subScreen) {
            is SubScreen.Edit -> {
                subScreen = SubScreen.Detail
            }
            is SubScreen.Community -> {
                subScreen = null
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
        is SubScreen.Community -> {
            CommunityScreen(apiClient = ApiClient(), modifier = Modifier)
            return
        }
        is SubScreen.AiGenerate -> {
            AiGenerateScreen(viewModel = vm, onBack = { subScreen = null })
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
                onLinkClick = { link -> selectedLink = link; subScreen = SubScreen.Detail }
            )
            Tab.CALENDAR -> CalendarScreen(
                viewModel = vm,
                modifier = Modifier.padding(padding),
                onLinkClick = { link -> selectedLink = link; subScreen = SubScreen.Detail }
            )
            Tab.TAGS -> TagsTabScreen(
                viewModel = vm,
                modifier = Modifier.padding(padding),
                onLinkClick = { link -> selectedLink = link; subScreen = SubScreen.Detail }
            )
            Tab.SHOPPING -> ShoppingListScreen(
                viewModel = vm,
                modifier = Modifier.padding(padding)
            )
            Tab.SETTINGS -> SettingsScreen(
                viewModel = vm,
                modifier = Modifier.padding(padding),
                onCommunityClick = { subScreen = SubScreen.Community }
            )
        }
    }
}

sealed class SubScreen {
    object AddLink : SubScreen()
    object AddFolder : SubScreen()
    object Detail : SubScreen()
    object Edit : SubScreen()
    object Community : SubScreen()
    object AiGenerate : SubScreen()
}
