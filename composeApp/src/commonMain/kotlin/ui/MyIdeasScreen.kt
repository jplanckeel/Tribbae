package ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import data.Folder
import data.Link
import data.LinkCategory
import ui.components.IdeaCard
import ui.components.getCategoryColor
import ui.components.getCategoryEmoji

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyIdeasScreen(
    links: List<Link>,
    folders: List<Folder>,
    onNavigateToDetail: (String) -> Unit,
    onSaveLink: (Link) -> Unit,
    onAddFolderClick: () -> Unit,
    onFolderClick: (Folder) -> Unit,
    onNavigateToAdd: () -> Unit = {},
    onNavigateToCategory: (LinkCategory) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Favoris", "Mes idées", "Dossiers", "Partagées")

    val savedLinks = links.filter { it.favorite }
    val myLinks = links // Toutes mes idées
    val sharedLinks = links.filter { it.likedByMe } // Idées partagées

    val currentList = when (selectedTab) {
        0 -> savedLinks
        1 -> myLinks
        3 -> sharedLinks
        else -> emptyList()
    }

    val categories = listOf(
        LinkCategory.ACTIVITE to "Activités",
        LinkCategory.CADEAU to "Cadeaux",
        LinkCategory.RECETTE to "Recettes",
        LinkCategory.EVENEMENT to "Événements"
    )

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // Header avec gradient
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFFFF6B35),
                            Color(0xFFF97316)
                        )
                    )
                )
                .padding(top = 48.dp, bottom = 20.dp, start = 20.dp, end = 20.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Mes idées",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color.White.copy(alpha = 0.2f), CircleShape)
                            .clickable(onClick = onNavigateToAdd),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "Ajouter",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Bookmark,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(11.dp)
                    )
                    Text(
                        text = "${savedLinks.size} idées sauvegardées",
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
            }
        }

        // Tabs
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color.White,
            shadowElevation = 4.dp
        ) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.White,
                contentColor = Color(0xFFF97316)
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                text = title,
                                fontSize = 12.sp,
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1
                            )
                        }
                    )
                }
            }
        }

        // Contenu
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF9FAFB)),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Onglet "Mes dossiers"
            if (selectedTab == 2) {
                if (folders.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 80.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "📁",
                                fontSize = 56.sp
                            )
                            Text(
                                text = "Aucun dossier",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF374151)
                            )
                            Text(
                                text = "Créez des dossiers pour organiser vos idées",
                                fontSize = 13.sp,
                                color = Color(0xFF9CA3AF)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = onAddFolderClick,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFF97316)
                                ),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Créer un dossier",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                } else {
                    // Header avec bouton ajouter
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${folders.size} dossier${if (folders.size > 1) "s" else ""}",
                                fontSize = 14.sp,
                                color = Color(0xFF6B7280)
                            )
                            Button(
                                onClick = onAddFolderClick,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFF97316)
                                ),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Nouveau",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    // Grille de dossiers en vignettes
                    items(folders.chunked(2)) { rowFolders ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            rowFolders.forEach { folder ->
                                FolderThumbnailCard(
                                    folder = folder,
                                    linkCount = links.count { it.folderId == folder.id },
                                    onClick = { onFolderClick(folder) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            // Remplir l'espace si nombre impair
                            if (rowFolders.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
            // État vide pour les autres onglets
            else if (currentList.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 80.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "💡",
                            fontSize = 56.sp
                        )
                        Text(
                            text = "Aucune idée ici",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF374151)
                        )
                        Text(
                            text = "Commencez à sauvegarder vos idées préférées !",
                            fontSize = 13.sp,
                            color = Color(0xFF9CA3AF)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { /* Navigate to explore */ },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFF97316)
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text(
                                text = "Explorer des idées",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            } else {
                // Collections (uniquement pour "Mes idées")
                if (selectedTab == 1) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            // Grille de catégories
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                categories.take(2).forEach { (category, label) ->
                                    CategoryCollectionCard(
                                        category = category,
                                        label = label,
                                        count = myLinks.count { it.category == category },
                                        onClick = { onNavigateToCategory(category) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                categories.drop(2).take(2).forEach { (category, label) ->
                                    CategoryCollectionCard(
                                        category = category,
                                        label = label,
                                        count = myLinks.count { it.category == category },
                                        onClick = { onNavigateToCategory(category) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            Text(
                                text = "Dernièrement ajoutées",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF374151),
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                }

                // Liste des idées
                items(currentList) { link ->
                    Box {
                        IdeaCard(
                            link = link,
                            onClick = { onNavigateToDetail(link.id) },
                            onSaveClick = { onSaveLink(link) }
                        )

                        // Badge visibilité (uniquement pour "Mes idées")
                        if (selectedTab == 1) {
                            Surface(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(top = 12.dp, end = 52.dp),
                                shape = RoundedCornerShape(20.dp),
                                color = Color.Black.copy(alpha = 0.3f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (link.likedByMe) Icons.Filled.Public else Icons.Filled.Lock,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(10.dp)
                                    )
                                    Text(
                                        text = if (link.likedByMe) "Public" else "Privé",
                                        fontSize = 9.sp,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Padding pour la bottom nav
            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
}

@Composable
fun CategoryCollectionCard(
    category: LinkCategory,
    label: String,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val categoryColor = getCategoryColor(category)
    val bgColor = categoryColor.copy(alpha = 0.1f)

    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = bgColor,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = categoryColor.copy(alpha = 0.2f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = getCategoryEmoji(category),
                fontSize = 28.sp
            )
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF1F2937)
            )
            Text(
                text = "$count idées",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = categoryColor
            )
        }
    }
}


@Composable
fun FolderThumbnailCard(
    folder: Folder,
    linkCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val folderColor = FolderColors[folder.color.name] ?: Color(0xFFF97316)
    val folderIcon = folderIconVector(folder)
    
    // Déterminer l'icône et le texte de visibilité
    val (visibilityIcon, visibilityText) = when (folder.visibility.uppercase()) {
        "PUBLIC", "VISIBILITY_PUBLIC" -> Icons.Filled.Public to "Public"
        "SHARED", "VISIBILITY_SHARED" -> Icons.Filled.Share to "Partagé"
        else -> Icons.Filled.Lock to "Privé"
    }
    
    Surface(
        modifier = modifier
            .aspectRatio(1f)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        shadowElevation = 2.dp
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Icône du dossier avec couleur personnalisée
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(folderColor.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = folderIcon,
                        contentDescription = null,
                        tint = folderColor,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Infos du dossier
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = folder.name,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF111827),
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Link,
                            contentDescription = null,
                            tint = Color(0xFF9CA3AF),
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "$linkCount idée${if (linkCount > 1) "s" else ""}",
                            fontSize = 12.sp,
                            color = Color(0xFF6B7280)
                        )
                    }
                }
            }
            
            // Badge de visibilité en haut à droite
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
                shape = RoundedCornerShape(12.dp),
                color = when (folder.visibility.uppercase()) {
                    "PUBLIC", "VISIBILITY_PUBLIC" -> Color(0xFF10B981).copy(alpha = 0.15f)
                    "SHARED", "VISIBILITY_SHARED" -> Color(0xFF3B82F6).copy(alpha = 0.15f)
                    else -> Color(0xFF6B7280).copy(alpha = 0.15f)
                }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = visibilityIcon,
                        contentDescription = null,
                        tint = when (folder.visibility.uppercase()) {
                            "PUBLIC", "VISIBILITY_PUBLIC" -> Color(0xFF10B981)
                            "SHARED", "VISIBILITY_SHARED" -> Color(0xFF3B82F6)
                            else -> Color(0xFF6B7280)
                        },
                        modifier = Modifier.size(10.dp)
                    )
                    Text(
                        text = visibilityText,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium,
                        color = when (folder.visibility.uppercase()) {
                            "PUBLIC", "VISIBILITY_PUBLIC" -> Color(0xFF10B981)
                            "SHARED", "VISIBILITY_SHARED" -> Color(0xFF3B82F6)
                            else -> Color(0xFF6B7280)
                        }
                    )
                }
            }
        }
    }
}
