package ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import data.ApiClient
import data.Link
import data.LinkCategory
import kotlinx.coroutines.launch

@Composable
fun CommunityScreen(
    modifier: Modifier = Modifier,
    apiClient: ApiClient,
    onLinkClick: (Link) -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    var communityLinks by remember { mutableStateOf<List<Link>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<LinkCategory?>(null) }
    var loading by remember { mutableStateOf(false) }
    var viewMode by remember { mutableStateOf(LinkViewMode.LIST) }

    // Filtrage local
    val filteredLinks = remember(communityLinks, searchQuery, selectedCategory) {
        communityLinks.filter { link ->
            val matchesSearch = searchQuery.isBlank() ||
                link.title.contains(searchQuery, ignoreCase = true) ||
                link.description.contains(searchQuery, ignoreCase = true) ||
                link.tags.any { it.contains(searchQuery, ignoreCase = true) }
            val matchesCategory = selectedCategory == null || link.category == selectedCategory
            matchesSearch && matchesCategory
        }
    }

    fun fetchLinks() {
        scope.launch {
            loading = true
            try {
                val res = apiClient.listCommunityLinks(limit = 100)
                communityLinks = res.links.map { apiLink ->
                    val category = try {
                        LinkCategory.valueOf(apiLink.category.removePrefix("LINK_CATEGORY_"))
                    } catch (_: Exception) { LinkCategory.IDEE }
                    Link(
                        id = apiLink.id,
                        title = apiLink.title,
                        url = apiLink.url,
                        description = apiLink.description,
                        category = category,
                        tags = apiLink.tags,
                        ageRange = apiLink.ageRange,
                        location = apiLink.location,
                        price = apiLink.price,
                        imageUrl = apiLink.imageUrl,
                        eventDate = if (apiLink.eventDate > 0) apiLink.eventDate else null,
                        rating = apiLink.rating,
                        ingredients = apiLink.ingredients,
                        likeCount = apiLink.likeCount,
                        likedByMe = apiLink.likedByMe,
                        ownerDisplayName = apiLink.ownerDisplayName
                    )
                }
            } catch (_: Exception) { }
            loading = false
        }
    }

    LaunchedEffect(Unit) { fetchLinks() }

    Column(modifier = modifier.fillMaxSize()) {
        // Search bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Rechercher une idée publique...", color = Color.Gray) },
            leadingIcon = {
                Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = Orange)
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(imageVector = Icons.Default.Clear, contentDescription = null)
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

        // Category chips
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedCategory == null,
                onClick = { selectedCategory = null },
                label = { Text("Tout") },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Public, contentDescription = null, modifier = Modifier.size(16.dp))
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Orange,
                    selectedLabelColor = Color.White,
                    selectedLeadingIconColor = Color.White
                )
            )
            QuickExploreCategoryChip(
                label = "Recettes", icon = Icons.Default.Restaurant,
                color = CategoryColors["RECETTE"] ?: Orange,
                selected = selectedCategory == LinkCategory.RECETTE,
                onClick = { selectedCategory = if (selectedCategory == LinkCategory.RECETTE) null else LinkCategory.RECETTE }
            )
            QuickExploreCategoryChip(
                label = "Activités", icon = Icons.Default.DirectionsRun,
                color = CategoryColors["ACTIVITE"] ?: Orange,
                selected = selectedCategory == LinkCategory.ACTIVITE,
                onClick = { selectedCategory = if (selectedCategory == LinkCategory.ACTIVITE) null else LinkCategory.ACTIVITE }
            )
            QuickExploreCategoryChip(
                label = "Cadeaux", icon = Icons.Default.CardGiftcard,
                color = CategoryColors["CADEAU"] ?: Orange,
                selected = selectedCategory == LinkCategory.CADEAU,
                onClick = { selectedCategory = if (selectedCategory == LinkCategory.CADEAU) null else LinkCategory.CADEAU }
            )
            QuickExploreCategoryChip(
                label = "Événements", icon = Icons.Default.Event,
                color = CategoryColors["EVENEMENT"] ?: Orange,
                selected = selectedCategory == LinkCategory.EVENEMENT,
                onClick = { selectedCategory = if (selectedCategory == LinkCategory.EVENEMENT) null else LinkCategory.EVENEMENT }
            )
            QuickExploreCategoryChip(
                label = "Idées", icon = Icons.Default.Lightbulb,
                color = CategoryColors["IDEE"] ?: Orange,
                selected = selectedCategory == LinkCategory.IDEE,
                onClick = { selectedCategory = if (selectedCategory == LinkCategory.IDEE) null else LinkCategory.IDEE }
            )
        }

        // Loading state
        if (loading && communityLinks.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Orange)
            }
        } else if (filteredLinks.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Explore,
                        contentDescription = null,
                        modifier = Modifier.size(72.dp),
                        tint = Orange.copy(alpha = 0.3f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Aucune idée publique", color = TextSecondary, fontSize = 15.sp)
                }
            }
        } else {
            // Toggle vue liste / grille + count
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${filteredLinks.size} idée${if (filteredLinks.size > 1) "s" else ""} publique${if (filteredLinks.size > 1) "s" else ""}",
                    fontSize = 13.sp,
                    color = TextSecondary
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = { viewMode = LinkViewMode.LIST },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ViewList,
                            contentDescription = "Vue liste",
                            tint = if (viewMode == LinkViewMode.LIST) Orange else Color.LightGray,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    IconButton(
                        onClick = { viewMode = LinkViewMode.GRID },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.GridView,
                            contentDescription = "Vue vignettes",
                            tint = if (viewMode == LinkViewMode.GRID) Orange else Color.LightGray,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            when (viewMode) {
                LinkViewMode.LIST -> {
                    LazyColumn(
                        contentPadding = PaddingValues(
                            start = 16.dp, end = 16.dp,
                            top = 4.dp, bottom = 80.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filteredLinks) { link ->
                            LinkCard(link = link, onClick = { onLinkClick(link) })
                        }
                    }
                }
                LinkViewMode.GRID -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(
                            start = 12.dp, end = 12.dp,
                            top = 4.dp, bottom = 80.dp
                        ),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filteredLinks) { link ->
                            LinkCardGrid(link = link, onClick = { onLinkClick(link) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickExploreCategoryChip(
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
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(16.dp))
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = color,
            selectedLabelColor = Color.White,
            selectedLeadingIconColor = Color.White
        )
    )
}
