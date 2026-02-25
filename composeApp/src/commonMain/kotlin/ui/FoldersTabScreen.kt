package ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import data.Link
import viewmodel.LinkViewModel

@Composable
fun FoldersTabScreen(
    viewModel: LinkViewModel,
    modifier: Modifier = Modifier,
    onAddFolderClick: () -> Unit,
    onLinkClick: (Link) -> Unit = {}
) {
    val folders by viewModel.folders.collectAsState()
    var selectedFolderId by remember { mutableStateOf<String?>(null) }

    // Si un dossier est sélectionné, afficher ses liens
    val selectedFolder = folders.find { it.id == selectedFolderId }
    if (selectedFolder != null) {
        val folderLinks = viewModel.getLinksForFolder(selectedFolder.id)
        val folderColor = FolderColors[selectedFolder.color.name] ?: Orange

        Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { selectedFolderId = null }) {
                    Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Retour", tint = Orange)
                }
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(folderColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = folderIconVector(selectedFolder), contentDescription = null,
                        tint = Color.White, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(selectedFolder.name, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = TextPrimary)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("${folderLinks.size} élément${if (folderLinks.size > 1) "s" else ""}",
                color = TextSecondary, fontSize = 13.sp,
                modifier = Modifier.padding(start = 48.dp))
            Spacer(modifier = Modifier.height(12.dp))

            if (folderLinks.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(imageVector = Icons.Default.FolderOpen, contentDescription = null,
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
    Column(modifier = modifier.fillMaxSize().padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Listes", fontWeight = FontWeight.Bold, fontSize = 24.sp, color = TextPrimary)
            Spacer(modifier = Modifier.weight(1f))
            FilledIconButton(
                onClick = onAddFolderClick,
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Orange)
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Ajouter", tint = Color.White)
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        if (folders.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(imageVector = Icons.Default.FolderOpen, contentDescription = null,
                        modifier = Modifier.size(64.dp), tint = OrangeLight.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Aucune liste", color = TextSecondary)
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(folders) { folder ->
                    val folderColor = FolderColors[folder.color.name] ?: Orange
                    val linkCount = viewModel.getLinksForFolder(folder.id).size
                    Card(
                        onClick = { selectedFolderId = folder.id },
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = CardColor),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(folderColor),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(imageVector = folderIconVector(folder), contentDescription = null,
                                    tint = Color.White, modifier = Modifier.size(24.dp))
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(folder.name, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                                Text("$linkCount élément${if (linkCount > 1) "s" else ""}",
                                    fontSize = 12.sp, color = TextSecondary)
                            }
                            Icon(imageVector = Icons.Default.ChevronRight, contentDescription = null, tint = Color.LightGray)
                            IconButton(onClick = { viewModel.deleteFolder(folder.id) }) {
                                Icon(imageVector = Icons.Default.Delete, contentDescription = "Supprimer",
                                    tint = Color.LightGray, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
