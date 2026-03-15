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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import data.Link
import data.LinkCategory
import ui.components.CategoryPatternBackground
import ui.components.IdeaCard
import ui.components.getCategoryColor
import ui.components.getCategoryEmoji
import ui.components.getCategoryLabel

enum class SortOption { POPULAR, RECENT, RATED }
enum class ViewMode { GRID, LIST }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreen(
    links: List<Link>,
    onNavigateBack: () -> Unit,
    onNavigateToDetail: (String) -> Unit,
    onSaveLink: (Link) -> Unit,
    viewModel: viewmodel.LinkViewModel,
    sessionManager: data.SessionManager? = null,
    followRepository: data.FollowRepository? = null,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<LinkCategory?>(null) }
    var sortOption by remember { mutableStateOf(SortOption.RECENT) }
    var viewMode by remember { mutableStateOf(ViewMode.GRID) }
    var showFilters by remember { mutableStateOf(false) }
    var minRating by remember { mutableStateOf(0f) }
    var activeTags by remember { mutableStateOf<List<String>>(emptyList()) }
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val pullRefreshState = rememberPullToRefreshState()
    val currentUserId by sessionManager?.userId?.collectAsState() ?: remember { mutableStateOf(null) }

    // All unique tags from links
    val allTags = remember(links) {
        links.flatMap { it.tags }.distinct().sorted().take(20)
    }

    // Tag suggestions while typing
    val tagSuggestions = remember(searchQuery, activeTags) {
        if (searchQuery.length >= 1) {
            allTags.filter { t ->
                t.contains(searchQuery, ignoreCase = true) && !activeTags.contains(t)
            }.take(6)
        } else emptyList()
    }

    val filteredLinks = remember(links, searchQuery, selectedCategory, sortOption, minRating, activeTags) {
        val filtered = links.filter { link ->
            val matchesSearch = searchQuery.isEmpty() ||
                link.title.contains(searchQuery, ignoreCase = true) ||
                link.description.contains(searchQuery, ignoreCase = true) ||
                link.tags.any { it.contains(searchQuery, ignoreCase = true) }
            val matchesCategory = selectedCategory == null || link.category == selectedCategory
            val matchesRating = minRating == 0f || link.rating >= minRating
            val matchesTags = activeTags.isEmpty() || activeTags.all { tag -> link.tags.contains(tag) }
            matchesSearch && matchesCategory && matchesRating && matchesTags
        }
        when (sortOption) {
            SortOption.POPULAR -> filtered.sortedByDescending { it.likeCount }
            SortOption.RECENT -> filtered.sortedByDescending { it.updatedAt }
            SortOption.RATED -> filtered.sortedWith(compareByDescending<Link> { it.rating }.thenByDescending { it.updatedAt })
        }
    }

    val categories = listOf(
        LinkCategory.ACTIVITE to "Activités",
        LinkCategory.CADEAU to "Cadeaux",
        LinkCategory.RECETTE to "Recettes",
        LinkCategory.EVENEMENT to "Événements",
        LinkCategory.IDEE to "Idées",
        LinkCategory.LIVRE to "Livres",
        LinkCategory.DECORATION to "Décorations"
    )

    val activeFiltersCount = (if (selectedCategory != null) 1 else 0) +
        (if (minRating > 0f) 1 else 0) +
        activeTags.size

    fun toggleTag(tag: String) {
        activeTags = if (activeTags.contains(tag)) activeTags - tag else activeTags + tag
    }

    fun clearAll() {
        selectedCategory = null
        minRating = 0f
        activeTags = emptyList()
        searchQuery = ""
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF9FAFB))
    ) {
        // Sticky header
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color.White,
            shadowElevation = 4.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 16.dp)
                    .padding(top = 12.dp, bottom = 10.dp)
            ) {
                // Title row + view toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Explorer",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF111827)
                    )
                    // View mode toggle
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFF3F4F6)
                    ) {
                        Row(modifier = Modifier.padding(4.dp)) {
                            ViewToggleButton(
                                icon = Icons.Filled.GridView,
                                selected = viewMode == ViewMode.GRID,
                                onClick = { viewMode = ViewMode.GRID }
                            )
                            ViewToggleButton(
                                icon = Icons.Filled.ViewList,
                                selected = viewMode == ViewMode.LIST,
                                onClick = { viewMode = ViewMode.LIST }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Search bar
                Box {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFFF3F4F6)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = null,
                                tint = Color(0xFF9CA3AF),
                                modifier = Modifier.size(16.dp)
                            )
                            TextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = {
                                    Text("Idée, tag, auteur…", fontSize = 14.sp, color = Color(0xFF9CA3AF))
                                },
                                modifier = Modifier.weight(1f),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                ),
                                singleLine = true,
                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp)
                            )
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(20.dp)) {
                                    Icon(Icons.Filled.Close, contentDescription = "Effacer",
                                        tint = Color(0xFF9CA3AF), modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                    // Tag suggestions dropdown
                    if (tagSuggestions.isNotEmpty()) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 52.dp),
                            shape = RoundedCornerShape(16.dp),
                            shadowElevation = 8.dp,
                            color = Color.White
                        ) {
                            Column {
                                tagSuggestions.forEach { tag ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { toggleTag(tag); searchQuery = "" }
                                            .padding(horizontal = 16.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("#", fontSize = 12.sp, color = Color(0xFFF97316), fontWeight = FontWeight.Bold)
                                        Text(tag, fontSize = 13.sp, color = Color(0xFF374151))
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Category chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CategoryChip(
                        label = "Toutes",
                        emoji = null,
                        color = Color(0xFFF97316),
                        selected = selectedCategory == null,
                        onClick = { selectedCategory = null }
                    )
                    categories.forEach { (category, label) ->
                        CategoryChip(
                            label = label,
                            emoji = getCategoryEmoji(category),
                            color = getCategoryColor(category),
                            selected = selectedCategory == category,
                            onClick = { selectedCategory = if (selectedCategory == category) null else category }
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Sort pills + filter button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SortPill(
                        label = "Populaire",
                        icon = "🔥",
                        selected = sortOption == SortOption.POPULAR,
                        onClick = { sortOption = SortOption.POPULAR }
                    )
                    SortPill(
                        label = "Récent",
                        icon = "🕐",
                        selected = sortOption == SortOption.RECENT,
                        onClick = { sortOption = SortOption.RECENT }
                    )
                    SortPill(
                        label = "Mieux noté",
                        icon = "⭐",
                        selected = sortOption == SortOption.RATED,
                        onClick = { sortOption = SortOption.RATED }
                    )
                    Spacer(Modifier.weight(1f))
                    // Advanced filter button
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (showFilters || activeFiltersCount > 0) Color(0xFFFFF7ED) else Color(0xFFF3F4F6),
                        modifier = Modifier.clickable { showFilters = !showFilters }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Tune,
                                contentDescription = null,
                                tint = if (showFilters || activeFiltersCount > 0) Color(0xFFF97316) else Color(0xFF9CA3AF),
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "Filtres",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (showFilters || activeFiltersCount > 0) Color(0xFFF97316) else Color(0xFF9CA3AF)
                            )
                            if (activeFiltersCount > 0) {
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFF97316)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = activeFiltersCount.toString(),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.Filled.KeyboardArrowDown,
                                contentDescription = null,
                                tint = if (showFilters || activeFiltersCount > 0) Color(0xFFF97316) else Color(0xFF9CA3AF),
                                modifier = Modifier
                                    .size(12.dp)
                                    .rotate(if (showFilters) 180f else 0f)
                            )
                        }
                    }
                }

                // Advanced filter panel
                if (showFilters) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                    ) {
                        HorizontalDivider(color = Color(0xFFF3F4F6), thickness = 1.dp)
                        Spacer(Modifier.height(12.dp))

                        // Min rating
                        Text(
                            text = "NOTE MINIMALE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF9CA3AF),
                            letterSpacing = 0.8.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(0f, 3f, 4f, 4.5f).forEach { val_ ->
                                val selected = minRating == val_
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = if (selected) Color(0xFFF97316) else Color(0xFFF3F4F6),
                                    modifier = Modifier.clickable { minRating = val_ }
                                ) {
                                    Text(
                                        text = if (val_ == 0f) "Toutes" else "⭐ ${if (val_ == val_.toInt().toFloat()) val_.toInt() else val_}+",
                                        fontSize = 11.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (selected) Color.White else Color(0xFF6B7280),
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }

                        if (allTags.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = "TAGS POPULAIRES",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF9CA3AF),
                                letterSpacing = 0.8.sp
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                allTags.take(12).forEach { tag ->
                                    val selected = activeTags.contains(tag)
                                    Surface(
                                        shape = RoundedCornerShape(20.dp),
                                        color = if (selected) Color(0xFFF97316) else Color(0xFFF3F4F6),
                                        modifier = Modifier.clickable { toggleTag(tag) }
                                    ) {
                                        Text(
                                            text = "#$tag",
                                            fontSize = 11.sp,
                                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                            color = if (selected) Color.White else Color(0xFF6B7280),
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Content
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.forceSync() },
            state = pullRefreshState,
            modifier = Modifier.fillMaxSize()
        ) {
            if (viewMode == ViewMode.GRID) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(top = 12.dp, bottom = 100.dp)
                ) {
                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
                        ResultsBar(
                            count = filteredLinks.size,
                            selectedCategory = selectedCategory,
                            minRating = minRating,
                            activeTags = activeTags,
                            categories = categories,
                            onClearCategory = { selectedCategory = null },
                            onClearRating = { minRating = 0f },
                            onClearTag = { toggleTag(it) },
                            onClearAll = { clearAll() }
                        )
                    }
                    if (filteredLinks.isEmpty()) {
                        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
                            EmptyState(onClearAll = { clearAll() })
                        }
                    } else {
                        items(filteredLinks) { link ->
                            IdeaGridCard(
                                link = link,
                                onClick = { onNavigateToDetail(link.id) },
                                onSaveClick = { onSaveLink(link) }
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(top = 12.dp, bottom = 100.dp)
                ) {
                    item {
                        ResultsBar(
                            count = filteredLinks.size,
                            selectedCategory = selectedCategory,
                            minRating = minRating,
                            activeTags = activeTags,
                            categories = categories,
                            onClearCategory = { selectedCategory = null },
                            onClearRating = { minRating = 0f },
                            onClearTag = { toggleTag(it) },
                            onClearAll = { clearAll() }
                        )
                    }
                    if (filteredLinks.isEmpty()) {
                        item { EmptyState(onClearAll = { clearAll() }) }
                    } else {
                        items(filteredLinks) { link ->
                            IdeaListCard(
                                link = link,
                                onClick = { onNavigateToDetail(link.id) },
                                onSaveClick = { onSaveLink(link) },
                                sessionManager = sessionManager,
                                followRepository = followRepository,
                                currentUserId = currentUserId
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Results bar ──────────────────────────────────────────────────────

@Composable
private fun ResultsBar(
    count: Int,
    selectedCategory: LinkCategory?,
    minRating: Float,
    activeTags: List<String>,
    categories: List<Pair<LinkCategory, String>>,
    onClearCategory: () -> Unit,
    onClearRating: () -> Unit,
    onClearTag: (String) -> Unit,
    onClearAll: () -> Unit
) {
    val activeFiltersCount = (if (selectedCategory != null) 1 else 0) +
        (if (minRating > 0f) 1 else 0) + activeTags.size

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.TrendingUp,
            contentDescription = null,
            tint = Color(0xFFF97316),
            modifier = Modifier.size(13.dp)
        )
        Text(
            text = "$count idée${if (count > 1) "s" else ""}",
            fontSize = 12.sp,
            color = Color(0xFF6B7280)
        )

        // Active filter chips
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (selectedCategory != null) {
                ActiveFilterChip(
                    label = "${getCategoryEmoji(selectedCategory)} ${categories.find { it.first == selectedCategory }?.second ?: ""}",
                    onRemove = onClearCategory
                )
            }
            if (minRating > 0f) {
                ActiveFilterChip(
                    label = "⭐ ${if (minRating == minRating.toInt().toFloat()) minRating.toInt() else minRating}+",
                    onRemove = onClearRating
                )
            }
            activeTags.forEach { tag ->
                ActiveFilterChip(label = "#$tag", onRemove = { onClearTag(tag) })
            }
            if (activeFiltersCount > 1) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFFFEE2E2),
                    modifier = Modifier.clickable(onClick = onClearAll)
                ) {
                    Text(
                        text = "Tout effacer",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFEF4444),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ActiveFilterChip(label: String, onRemove: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFFFFF7ED),
        modifier = Modifier.clickable(onClick = onRemove)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = label, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFF97316))
            Icon(Icons.Filled.Close, contentDescription = null, tint = Color(0xFFF97316), modifier = Modifier.size(9.dp))
        }
    }
}

// ── Empty state ───────────────────────────────────────────────────────────────

@Composable
private fun EmptyState(onClearAll: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = "🔍", fontSize = 52.sp)
        Text(text = "Aucune idée trouvée", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF374151))
        Text(text = "Essayez d'autres mots-clés ou réinitialisez les filtres", fontSize = 13.sp, color = Color(0xFF9CA3AF))
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFFF97316),
            modifier = Modifier.clickable(onClick = onClearAll)
        ) {
            Text(
                text = "Réinitialiser",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
            )
        }
    }
}

// ── Header composables ────────────────────────────────────────────────────────

@Composable
private fun ViewToggleButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) Color.White else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) Color(0xFFF97316) else Color(0xFF9CA3AF),
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun SortPill(label: String, icon: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (selected) Color(0xFFFFF7ED) else Color(0xFFF3F4F6),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = icon, fontSize = 10.sp)
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) Color(0xFFF97316) else Color(0xFF9CA3AF)
            )
        }
    }
}

@Composable
private fun CategoryChip(
    label: String,
    emoji: String?,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (selected) color else color.copy(alpha = 0.12f),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (emoji != null) Text(text = emoji, fontSize = 12.sp)
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) Color.White else color
            )
        }
    }
}

// ── Card composables ──────────────────────────────────────────────────────────

@Composable
fun IdeaGridCard(
    link: Link,
    onClick: () -> Unit,
    onSaveClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var saved by remember { mutableStateOf(link.favorite) }
    val categoryColor = getCategoryColor(link.category)
    val ownerName = link.ownerDisplayName.ifBlank { "Anonyme" }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 4.dp,
        color = Color.White
    ) {
        Column {
            // Image
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
            ) {
                if (link.imageUrl.isNotEmpty()) {
                    AsyncImage(
                        model = link.imageUrl,
                        contentDescription = link.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    CategoryPatternBackground(category = link.category, modifier = Modifier.fillMaxSize())
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.5f))))
                )
                // Save button
                Box(
                    modifier = Modifier
                        .padding(6.dp)
                        .size(24.dp)
                        .align(Alignment.TopEnd)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.9f))
                        .clickable { saved = !saved; onSaveClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (saved) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                        contentDescription = null,
                        tint = if (saved) Color(0xFFF97316) else Color(0xFF374151),
                        modifier = Modifier.size(12.dp)
                    )
                }
                // Rating
                if (link.rating > 0) {
                    Surface(
                        modifier = Modifier.padding(6.dp).align(Alignment.BottomStart),
                        shape = RoundedCornerShape(20.dp),
                        color = Color.White.copy(alpha = 0.9f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Star, null, tint = Color(0xFFF59E0B), modifier = Modifier.size(9.dp))
                            Text(link.rating.toString(), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1F2937))
                        }
                    }
                }
            }

            // Content
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(getCategoryEmoji(link.category), fontSize = 10.sp)
                    Text(
                        getCategoryLabel(link.category),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = categoryColor
                    )
                }
                Text(
                    text = link.title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF111827),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 16.sp
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .background(categoryColor, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = ownerName.firstOrNull()?.uppercase() ?: "?",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                lineHeight = 8.sp
                            )
                        }
                        Text(ownerName, fontSize = 9.sp, color = Color(0xFF9CA3AF), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.FavoriteBorder, null, tint = Color(0xFFD1D5DB), modifier = Modifier.size(11.dp))
                        Text(link.likeCount.toString(), fontSize = 9.sp, color = Color(0xFF9CA3AF))
                    }
                }
            }
        }
    }
}

@Composable
fun IdeaListCard(
    link: Link,
    onClick: () -> Unit,
    onSaveClick: () -> Unit,
    sessionManager: data.SessionManager? = null,
    followRepository: data.FollowRepository? = null,
    currentUserId: String? = null,
    modifier: Modifier = Modifier
) {
    IdeaCard(
        link = link,
        compact = true,
        onClick = onClick,
        onSaveClick = onSaveClick,
        sessionManager = sessionManager,
        followRepository = followRepository,
        currentUserId = currentUserId,
        modifier = modifier
    )
}

