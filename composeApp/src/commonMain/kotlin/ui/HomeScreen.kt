package ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
    val allLinks by viewModel.repository.links.collectAsState()
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
            // Si aucune catégorie sélectionnée : afficher la page d'accueil
            if (selectedCategory == null) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFFF9FAFB)),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    // Header avec dégradé
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(
                                            Color(0xFFFF6B35),
                                            Color(0xFFF97316),
                                            Color(0xFFFBBF24)
                                        ),
                                        start = Offset(0f, 0f),
                                        end = Offset(1000f, 1000f)
                                    )
                                )
                                .padding(top = 48.dp, bottom = 24.dp, start = 20.dp, end = 20.dp)
                        ) {
                            Column {
                                // Top bar
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            "Bonjour 👋",
                                            color = Color.White.copy(alpha = 0.8f),
                                            fontSize = 13.sp
                                        )
                                        Text(
                                            "Ma famille",
                                            color = Color.White,
                                            fontSize = 22.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(20.dp))

                                // Search bar
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { /* TODO: ouvrir recherche */ },
                                    shape = RoundedCornerShape(16.dp),
                                    color = Color.White,
                                    shadowElevation = 8.dp
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Search,
                                            contentDescription = null,
                                            tint = Color.Gray,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            "Chercher une idée, une activité…",
                                            color = Color.Gray,
                                            fontSize = 14.sp
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(20.dp))

                                // Stats
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    StatCard(
                                        label = "Idées sauvegardées",
                                        value = allLinks.size.toString(),
                                        modifier = Modifier.weight(1f)
                                    )
                                    StatCard(
                                        label = "Favoris",
                                        value = allLinks.count { it.favorite }.toString(),
                                        modifier = Modifier.weight(1f)
                                    )
                                    StatCard(
                                        label = "Listes",
                                        value = folders.size.toString(),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }

                    // Catégories
                    item {
                        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Catégories",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF111827)
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Grille 3 colonnes
                            val categories = LinkCategory.entries.toList()
                            val rows = categories.chunked(3)
                            
                            rows.forEach { rowCategories ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    rowCategories.forEach { category ->
                                        CategoryCard(
                                            category = category,
                                            linkCount = allLinks.count { it.category == category },
                                            onClick = { viewModel.selectCategory(category) },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    // Remplir les espaces vides
                                    repeat(3 - rowCategories.size) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                                if (rowCategories != rows.last()) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                }
                            }
                        }
                    }

                    // Tendances (carousel horizontal)
                    val trendingLinks = allLinks.sortedByDescending { it.likeCount }.take(10)
                    if (trendingLinks.isNotEmpty()) {
                        item {
                            Column(modifier = Modifier.padding(top = 24.dp)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 20.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Whatshot,
                                            contentDescription = null,
                                            tint = Color(0xFFF97316),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            "Tendances",
                                            fontSize = 17.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF111827)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                
                                // Carousel horizontal
                                Row(
                                    modifier = Modifier
                                        .horizontalScroll(rememberScrollState())
                                        .padding(horizontal = 20.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    trendingLinks.forEach { link ->
                                        Box(modifier = Modifier.width(240.dp)) {
                                            LinkCard(
                                                link = link,
                                                onClick = { onLinkClick(link) },
                                                onFavoriteToggle = { viewModel.toggleFavorite(link.id) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Favoris
                    val favoriteLinks = allLinks.filter { it.favorite }.take(4)
                    if (favoriteLinks.isNotEmpty()) {
                        item {
                            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Star,
                                            contentDescription = null,
                                            tint = Color(0xFFF59E0B),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            "Mes favoris",
                                            fontSize = 17.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF111827)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                            }
                        }

                        items(favoriteLinks) { link ->
                            Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
                                LinkCard(
                                    link = link,
                                    onClick = { onLinkClick(link) },
                                    onFavoriteToggle = { viewModel.toggleFavorite(link.id) }
                                )
                            }
                        }

                        item { Spacer(modifier = Modifier.height(16.dp)) }
                    }

                    // Récemment ajoutées
                    val recentLinks = allLinks.sortedByDescending { it.updatedAt }.take(3)
                    if (recentLinks.isNotEmpty()) {
                        item {
                            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Schedule,
                                        contentDescription = null,
                                        tint = Color(0xFF6B7280),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "Récemment ajoutées",
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF111827)
                                    )
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                            }
                        }

                        items(recentLinks) { link ->
                            Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
                                LinkCard(
                                    link = link,
                                    onClick = { onLinkClick(link) },
                                    onFavoriteToggle = { viewModel.toggleFavorite(link.id) }
                                )
                            }
                        }
                    }
                }
            } else {
                // Une catégorie est sélectionnée : afficher les filtres et la liste
                val currentCategory = selectedCategory ?: return@PullToRefreshBox
                
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFFF9FAFB)),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    // Header avec couleur de catégorie
                    item {
                        val categoryColor = CategoryColors[currentCategory.name] ?: Orange
                        val bgColor = categoryColor.copy(alpha = 0.1f)
                        
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(bgColor)
                                .padding(top = 48.dp, bottom = 24.dp, start = 20.dp, end = 20.dp)
                        ) {
                            Column {
                                // Bouton retour
                                Surface(
                                    onClick = { viewModel.selectCategory(null) },
                                    shape = CircleShape,
                                    color = Color.White,
                                    shadowElevation = 4.dp,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.ArrowBack,
                                            contentDescription = "Retour",
                                            tint = Color(0xFF374151),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                // Titre et icône
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(64.dp)
                                            .background(
                                                color = categoryColor.copy(alpha = 0.2f),
                                                shape = RoundedCornerShape(16.dp)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = categoryIcon(currentCategory),
                                            contentDescription = null,
                                            tint = categoryColor,
                                            modifier = Modifier.size(36.dp)
                                        )
                                    }

                                    Column {
                                        Text(
                                            currentCategory.label,
                                            fontSize = 24.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF111827)
                                        )
                                        Text(
                                            "${filteredLinks.size} idée${if (filteredLinks.size > 1) "s" else ""} disponible${if (filteredLinks.size > 1) "s" else ""}",
                                            fontSize = 13.sp,
                                            color = Color(0xFF6B7280)
                                        )
                                    }
                                }

                                // Tags de filtrage
                                val allTags = filteredLinks.flatMap { it.tags }.distinct().sorted()
                                if (allTags.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Row(
                                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        allTags.take(10).forEach { tag ->
                                            Surface(
                                                onClick = {
                                                    viewModel.updateSearchQuery(
                                                        if (searchQuery == tag) "" else tag
                                                    )
                                                },
                                                shape = RoundedCornerShape(20.dp),
                                                color = if (searchQuery == tag) categoryColor else Color.White,
                                                modifier = Modifier.height(28.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        tag,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = if (searchQuery == tag) Color.White else categoryColor
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item { Spacer(modifier = Modifier.height(20.dp)) }

                    // Liste des idées
                    if (filteredLinks.isEmpty()) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 64.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = categoryIcon(currentCategory),
                                    contentDescription = null,
                                    modifier = Modifier.size(72.dp),
                                    tint = (CategoryColors[currentCategory.name] ?: Orange).copy(alpha = 0.3f)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    "Aucune ${currentCategory.label.lowercase()} trouvée",
                                    color = Color(0xFF6B7280),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "Soyez le premier à en ajouter !",
                                    color = Color(0xFF9CA3AF),
                                    fontSize = 13.sp
                                )
                            }
                        }
                    } else {
                        items(filteredLinks) { link ->
                            Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
                                LinkCard(
                                    link = link,
                                    onClick = { onLinkClick(link) },
                                    onFavoriteToggle = { viewModel.toggleFavorite(link.id) }
                                )
                            }
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

// Composants helpers
@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = Color.White.copy(alpha = 0.2f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                value,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                label,
                fontSize = 10.sp,
                color = Color.White.copy(alpha = 0.8f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryCard(
    category: LinkCategory,
    linkCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val categoryColor = CategoryColors[category.name] ?: Orange
    val bgColor = categoryColor.copy(alpha = 0.1f)
    
    Card(
        onClick = onClick,
        modifier = modifier.aspectRatio(1f),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = categoryIcon(category),
                contentDescription = null,
                tint = categoryColor,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                category.label,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF111827),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = categoryColor.copy(alpha = 0.2f)
            ) {
                Text(
                    "$linkCount idée${if (linkCount > 1) "s" else ""}",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = categoryColor,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryTile(
    category: LinkCategory,
    linkCount: Int,
    onClick: () -> Unit
) {
    val categoryColor = CategoryColors[category.name] ?: Orange
    
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp,
            pressedElevation = 6.dp
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            categoryColor.copy(alpha = 0.08f),
                            categoryColor.copy(alpha = 0.15f)
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Icône en haut
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(
                            color = categoryColor.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(14.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = categoryIcon(category),
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = categoryColor
                    )
                }
                
                // Texte en bas
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        category.label,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        "$linkCount idée${if (linkCount > 1) "s" else ""}",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                }
            }
            
            // Badge de compteur en haut à droite
            if (linkCount > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .background(
                            color = categoryColor,
                            shape = CircleShape
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        linkCount.toString(),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryBlock(
    category: LinkCategory,
    linkCount: Int,
    onClick: () -> Unit
) {
    val categoryColor = CategoryColors[category.name] ?: Orange
    
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = categoryColor.copy(alpha = 0.1f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            categoryColor.copy(alpha = 0.15f),
                            categoryColor.copy(alpha = 0.05f)
                        )
                    )
                )
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        category.label,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = categoryColor
                    )
                    Text(
                        "$linkCount élément${if (linkCount > 1) "s" else ""}",
                        fontSize = 14.sp,
                        color = TextSecondary
                    )
                }
                
                Icon(
                    imageVector = categoryIcon(category),
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    tint = categoryColor.copy(alpha = 0.6f)
                )
            }
        }
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
