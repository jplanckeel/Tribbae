package ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import data.Link
import viewmodel.LinkViewModel

@Composable
fun FoldersTabScreen(
    viewModel: LinkViewModel,
    modifier: Modifier = Modifier,
    onAddFolderClick: () -> Unit,
    onEditFolderClick: (data.Folder) -> Unit = {},
    onLinkClick: (Link) -> Unit = {}
) {
    val folders by viewModel.folders.collectAsState()
    var selectedFolderId by remember { mutableStateOf<String?>(null) }
    var viewMode by remember { mutableStateOf(LinkViewMode.LIST) }

    // Si un dossier est sélectionné, afficher ses liens
    val selectedFolder = folders.find { it.id == selectedFolderId }
    if (selectedFolder != null) {
        val folderLinks = viewModel.getLinksForFolder(selectedFolder.id)
        val folderColor = FolderColors[selectedFolder.color.name] ?: Orange

        Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { selectedFolderId = null }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Retour", tint = Orange)
                }
                Box(
                    modifier = Modifier.size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(folderColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(folderIconVector(selectedFolder), contentDescription = null,
                        tint = Color.White, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(selectedFolder.name, fontWeight = FontWeight.Bold, fontSize = 22.sp,
                    color = TextPrimary, modifier = Modifier.weight(1f))
                IconButton(onClick = { onEditFolderClick(selectedFolder) }) {
                    Icon(Icons.Default.Edit, contentDescription = "Modifier",
                        tint = Orange, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("${folderLinks.size} élément${if (folderLinks.size > 1) "s" else ""}",
                color = TextSecondary, fontSize = 13.sp,
                modifier = Modifier.padding(start = 48.dp))
            Spacer(modifier = Modifier.height(12.dp))

            if (folderLinks.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null,
                            modifier = Modifier.size(56.dp), tint = folderColor.copy(alpha = 0.4f))
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("Liste vide", color = TextSecondary)
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(folderLinks) { link ->
                        LinkCard(link = link, onClick = { onLinkClick(link) })
                    }
                }
            }
        }
        return
    }

    // Vue liste des dossiers
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Listes", fontWeight = FontWeight.Bold, fontSize = 24.sp, color = TextPrimary)
            Spacer(modifier = Modifier.weight(1f))
            FilledIconButton(
                onClick = onAddFolderClick,
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Orange)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Ajouter", tint = Color.White)
            }
        }

        if (folders.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null,
                        modifier = Modifier.size(64.dp), tint = OrangeLight.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Aucune liste", color = TextSecondary)
                }
            }
        } else {
            // Toggle + compteur
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("${folders.size} liste${if (folders.size > 1) "s" else ""}",
                    fontSize = 13.sp, color = TextSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = { viewMode = LinkViewMode.LIST }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.ViewList, contentDescription = "Vue liste",
                            tint = if (viewMode == LinkViewMode.LIST) Orange else Color.LightGray,
                            modifier = Modifier.size(22.dp))
                    }
                    IconButton(onClick = { viewMode = LinkViewMode.GRID }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.GridView, contentDescription = "Vue vignettes",
                            tint = if (viewMode == LinkViewMode.GRID) Orange else Color.LightGray,
                            modifier = Modifier.size(22.dp))
                    }
                }
            }

            when (viewMode) {
                LinkViewMode.LIST -> {
                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(folders) { folder ->
                            FolderCardList(
                                folder = folder,
                                linkCount = viewModel.getLinksForFolder(folder.id).size,
                                onClick = { selectedFolderId = folder.id },
                                onEdit = { onEditFolderClick(folder) },
                                onDelete = { viewModel.deleteFolder(folder.id) }
                            )
                        }
                    }
                }
                LinkViewMode.GRID -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(folders) { folder ->
                            FolderCardGrid(
                                folder = folder,
                                linkCount = viewModel.getLinksForFolder(folder.id).size,
                                onClick = { selectedFolderId = folder.id },
                                onEdit = { onEditFolderClick(folder) }
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Carte dossier compacte — mode liste (même style que LinkCard) */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FolderCardList(
    folder: data.Folder,
    linkCount: Int,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val folderColor = FolderColors[folder.color.name] ?: Orange

    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardColor),
        elevation = CardDefaults.cardElevation(2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.height(80.dp)) {
            // Icône à gauche
            Box(
                modifier = Modifier.width(80.dp).fillMaxHeight()
                    .clip(RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
                    .background(folderColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = folderIconVector(folder),
                    contentDescription = null,
                    tint = folderColor,
                    modifier = Modifier.size(32.dp)
                )
            }

            // Texte
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(folder.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, color = TextPrimary)
                Spacer(modifier = Modifier.height(2.dp))
                Text("$linkCount élément${if (linkCount > 1) "s" else ""}",
                    fontSize = 12.sp, color = TextSecondary)
            }

            // Actions
            Column(
                modifier = Modifier.fillMaxHeight().padding(end = 2.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row {
                    IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "Modifier",
                            tint = Color.LightGray, modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Supprimer",
                            tint = Color.LightGray, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

/** Carte dossier — mode grille (même style que LinkCardGrid) */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FolderCardGrid(
    folder: data.Folder,
    linkCount: Int,
    onClick: () -> Unit,
    onEdit: () -> Unit
) {
    val folderColor = FolderColors[folder.color.name] ?: Orange

    Card(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CardColor),
        elevation = CardDefaults.cardElevation(2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.height(150.dp)) {
            // Zone icône en haut
            Box(
                modifier = Modifier.fillMaxWidth().height(90.dp)
                    .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                    .background(folderColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = folderIconVector(folder),
                    contentDescription = null,
                    tint = folderColor.copy(alpha = 0.5f),
                    modifier = Modifier.size(44.dp)
                )
                // Edit button
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.size(28.dp).align(Alignment.TopEnd).padding(4.dp)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = "Modifier",
                        tint = folderColor.copy(alpha = 0.6f), modifier = Modifier.size(14.dp))
                }
            }

            // Texte en bas
            Column(
                modifier = Modifier.fillMaxWidth().weight(1f)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(folder.name, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, color = TextPrimary)
                Text("$linkCount élément${if (linkCount > 1) "s" else ""}",
                    fontSize = 11.sp, color = TextSecondary)
            }
        }
    }
}
