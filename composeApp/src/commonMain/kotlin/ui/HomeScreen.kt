package ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import data.Link
import data.LinkCategory
import viewmodel.LinkViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: LinkViewModel,
    modifier: Modifier = Modifier,
    onAddClick: () -> Unit,
    onAiClick: () -> Unit,
    onLinkClick: (Link) -> Unit
) {
    val filteredLinks by viewModel.filteredLinks.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val favoritesOnly by viewModel.favoritesOnly.collectAsState()
    val folders by viewModel.folders.collectAsState()
    val selectedFolderId by viewModel.selectedFolderId.collectAsState()
    val children by viewModel.children.collectAsState()
    val selectedChildId by viewModel.selectedChildId.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()

    var showFilterSheet by remember { mutableStateOf(false) }
    val activeFilters = viewModel.activeFilterCount()

    val pullRefreshState = rememberPullToRefreshState()

    Box(modifier = modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.forceSync() },
            state = pullRefreshState,
            modifier = Modifier.fillMaxSize()
        ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.updateSearchQuery(it) },
                placeholder = { Text("Rechercher une idée...", color = Color.Gray) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = Orange
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = null
                            )
                        }
                    }
                },
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Orange,
                    unfocusedBorderColor = Orange.copy(alpha = 0.3f),
                    focusedContainerColor = SurfaceColor,
                    unfocusedContainerColor = SurfaceColor
                )
            )

            // Chips rapides : Recettes, Activités, Cadeaux, Favoris, + Filtres
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Catégories principales
                QuickCategoryChip(
                    label = "Recettes",
                    icon = Icons.Default.Restaurant,
                    color = CategoryColors["RECETTE"] ?: Orange,
                    selected = selectedCategory == LinkCategory.RECETTE,
                    onClick = {
                        viewModel.selectCategory(
                            if (selectedCategory == LinkCategory.RECETTE) null
                            else LinkCategory.RECETTE
                        )
                    }
                )
                QuickCategoryChip(
                    label = "Activités",
                    icon = Icons.Default.DirectionsRun,
                    color = CategoryColors["ACTIVITE"] ?: Orange,
                    selected = selectedCategory == LinkCategory.ACTIVITE,
                    onClick = {
                        viewModel.selectCategory(
                            if (selectedCategory == LinkCategory.ACTIVITE) null
                            else LinkCategory.ACTIVITE
                        )
                    }
                )
                QuickCategoryChip(
                    label = "Cadeaux",
                    icon = Icons.Default.CardGiftcard,
                    color = CategoryColors["CADEAU"] ?: Orange,
                    selected = selectedCategory == LinkCategory.CADEAU,
                    onClick = {
                        viewModel.selectCategory(
                            if (selectedCategory == LinkCategory.CADEAU) null
                            else LinkCategory.CADEAU
                        )
                    }
                )

                // Favoris (rating >= 4)
                FilterChip(
                    selected = favoritesOnly,
                    onClick = { viewModel.toggleFavorites() },
                    label = { Text("Favoris") },
                    leadingIcon = {
                        Icon(
                            imageVector = if (favoritesOnly) Icons.Default.Star
                            else Icons.Default.StarOutline,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Yellow,
                        selectedLabelColor = Color.Black,
                        selectedLeadingIconColor = Color.Black
                    )
                )

                // + Filtres
                FilterChip(
                    selected = activeFilters > 0,
                    onClick = { showFilterSheet = true },
                    label = {
                        Text(
                            if (activeFilters > 1) "+ Filtres ($activeFilters)"
                            else "+ Filtres"
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Orange.copy(alpha = 0.15f),
                        selectedLabelColor = Orange,
                        selectedLeadingIconColor = Orange
                    )
                )
            }

            // Résultats
            if (filteredLinks.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Lightbulb,
                            contentDescription = null,
                            modifier = Modifier.size(72.dp),
                            tint = Yellow.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "Ajoutez votre première idée",
                            color = TextSecondary,
                            fontSize = 15.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(
                        start = 16.dp, end = 16.dp,
                        top = 4.dp, bottom = 80.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredLinks) { link ->
                        LinkCard(
                            link = link,
                            onClick = { onLinkClick(link) },
                            onFavoriteToggle = { viewModel.toggleFavorite(link.id) }
                        )
                    }
                }
            }
        }
        } // PullToRefreshBox

        // FABs
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.End
        ) {
            // FAB IA
            FloatingActionButton(
                onClick = onAiClick,
                containerColor = Purple,
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = "Générer avec l'IA",
                    modifier = Modifier.size(22.dp)
                )
            }
            // FAB Ajouter
            FloatingActionButton(
                onClick = onAddClick,
                containerColor = Orange,
                contentColor = Color.White,
                shape = CircleShape
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Ajouter"
                )
            }
        }
    }

    // Bottom sheet "Tous les filtres"
    if (showFilterSheet) {
        AllFiltersSheet(
            viewModel = viewModel,
            selectedCategory = selectedCategory,
            selectedFolderId = selectedFolderId,
            selectedChildId = selectedChildId,
            favoritesOnly = favoritesOnly,
            folders = folders,
            children = children,
            onDismiss = { showFilterSheet = false }
        )
    }
}


@Composable
private fun QuickCategoryChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = color,
            selectedLabelColor = Color.White,
            selectedLeadingIconColor = Color.White
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AllFiltersSheet(
    viewModel: LinkViewModel,
    selectedCategory: LinkCategory?,
    selectedFolderId: String?,
    selectedChildId: String?,
    favoritesOnly: Boolean,
    folders: List<data.Folder>,
    children: List<data.Child>,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SurfaceColor,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Tous les filtres",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = TextPrimary
                )
                if (viewModel.activeFilterCount() > 0) {
                    TextButton(onClick = { viewModel.clearAllFilters() }) {
                        Text("Réinitialiser", color = Orange)
                    }
                }
            }

            // Catégories
            Text(
                "Catégorie",
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = TextSecondary
            )
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedCategory == null,
                    onClick = { viewModel.selectCategory(null) },
                    label = { Text("Tout") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Apps,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Orange,
                        selectedLabelColor = Color.White,
                        selectedLeadingIconColor = Color.White
                    )
                )
                LinkCategory.entries.forEach { cat ->
                    val catColor = CategoryColors[cat.name] ?: Orange
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = {
                            viewModel.selectCategory(
                                if (selectedCategory == cat) null else cat
                            )
                        },
                        label = { Text(cat.label) },
                        leadingIcon = {
                            Icon(
                                imageVector = categoryIcon(cat),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = catColor,
                            selectedLabelColor = Color.White,
                            selectedLeadingIconColor = Color.White
                        )
                    )
                }
            }

            // Dossiers
            if (folders.isNotEmpty()) {
                Text(
                    "Liste",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = TextSecondary
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FolderChip(
                        label = "Tous",
                        icon = Icons.Default.Folder,
                        color = if (selectedFolderId == null) Orange else Color.LightGray,
                        onClick = { viewModel.selectFolder(null) }
                    )
                    folders.forEach { folder ->
                        val folderColor = FolderColors[folder.color.name] ?: Orange
                        FolderChip(
                            label = folder.name,
                            icon = folderIconVector(folder),
                            color = if (selectedFolderId == folder.id) folderColor
                            else folderColor.copy(alpha = 0.4f),
                            onClick = { viewModel.selectFolder(folder.id) }
                        )
                    }
                }
            }

            // Enfants
            if (children.isNotEmpty()) {
                Text(
                    "Enfant",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = TextSecondary
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedChildId == null,
                        onClick = { viewModel.selectChild(null) },
                        label = { Text("Tous") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.FamilyRestroom,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = BlueSky,
                            selectedLabelColor = Color.White,
                            selectedLeadingIconColor = Color.White
                        )
                    )
                    children.forEach { child ->
                        FilterChip(
                            selected = selectedChildId == child.id,
                            onClick = {
                                viewModel.selectChild(
                                    if (selectedChildId == child.id) null else child.id
                                )
                            },
                            label = {
                                Text(
                                    "${child.name} (${formatChildAgeShort(child.birthDate)})"
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.ChildCare,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = BlueSky,
                                selectedLabelColor = Color.White,
                                selectedLeadingIconColor = Color.White
                            )
                        )
                    }
                }
            }

            // Favoris
            Text(
                "Autres",
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = TextSecondary
            )
            FilterChip(
                selected = favoritesOnly,
                onClick = { viewModel.toggleFavorites() },
                label = { Text("Favoris uniquement ♥") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Yellow,
                    selectedLabelColor = Color.Black,
                    selectedLeadingIconColor = Color.Black
                )
            )

            // Bouton appliquer
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Orange)
            ) {
                Text("Appliquer", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
