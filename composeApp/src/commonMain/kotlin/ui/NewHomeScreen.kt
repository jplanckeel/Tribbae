package ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import data.Link
import data.LinkCategory
import ui.components.IdeaCard
import ui.components.getCategoryColor
import ui.components.getCategoryEmoji

data class CategoryInfo(
    val id: LinkCategory,
    val name: String,
    val emoji: String,
    val color: Color,
    val bgColor: Color,
    val count: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewHomeScreen(
    links: List<Link>,
    onNavigateToExplore: () -> Unit,
    onNavigateToCategory: (LinkCategory) -> Unit,
    onNavigateToDetail: (String) -> Unit,
    onSaveLink: (Link) -> Unit,
    sessionManager: data.SessionManager? = null,
    onNavigateToAI: () -> Unit = {},
    viewModel: viewmodel.LinkViewModel,
    modifier: Modifier = Modifier
) {
    val displayName by sessionManager?.displayName?.collectAsState() ?: remember { mutableStateOf(null) }
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val pullRefreshState = rememberPullToRefreshState()
    val categories = remember(links) {
        listOf(
            CategoryInfo(
                LinkCategory.ACTIVITE,
                "Activités",
                "🏃",
                Color(0xFF4FC3F7),
                Color(0xFFEFF6FF),
                links.count { it.category == LinkCategory.ACTIVITE }
            ),
            CategoryInfo(
                LinkCategory.CADEAU,
                "Cadeaux",
                "🎁",
                Color(0xFFFF8C00),
                Color(0xFFFFF7ED),
                links.count { it.category == LinkCategory.CADEAU }
            ),
            CategoryInfo(
                LinkCategory.RECETTE,
                "Recettes",
                "🍳",
                Color(0xFF81C784),
                Color(0xFFF0FDF4),
                links.count { it.category == LinkCategory.RECETTE }
            ),
            CategoryInfo(
                LinkCategory.EVENEMENT,
                "Événements",
                "📅",
                Color(0xFFFF7043),
                Color(0xFFFDF2F8),
                links.count { it.category == LinkCategory.EVENEMENT }
            ),
            CategoryInfo(
                LinkCategory.IDEE,
                "Idées",
                "💡",
                Color(0xFFFFD700),
                Color(0xFFFFFBEB),
                links.count { it.category == LinkCategory.IDEE }
            ),
            CategoryInfo(
                LinkCategory.LIVRE,
                "Livres",
                "📚",
                Color(0xFF9C27B0),
                Color(0xFFF3E5F5),
                links.count { it.category == LinkCategory.LIVRE }
            ),
            CategoryInfo(
                LinkCategory.DECORATION,
                "Décorations",
                "🎨",
                Color(0xFFE91E63),
                Color(0xFFFCE4EC),
                links.count { it.category == LinkCategory.DECORATION }
            )
        )
    }

    val trendingLinks = remember(links) { links.sortedByDescending { it.updatedAt }.take(5) }
    val recentLinks = remember(links) { links.sortedByDescending { it.updatedAt }.take(3) }
    val savedCount = remember(links) { links.count { it.favorite } }
    val sharedCount = remember(links) { links.count { it.likedByMe } }
    // Note: tribeCount nécessiterait une liste de membres de la famille passée en paramètre
    val tribeCount = 0

    Box(modifier = modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.forceSync() },
            state = pullRefreshState,
            modifier = Modifier.fillMaxSize()
        ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize()
        ) {
        // Header avec gradient
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
                            )
                        )
                    )
                    .padding(top = 48.dp, bottom = 32.dp, start = 20.dp, end = 20.dp)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // Top bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Bonjour 👋",
                                fontSize = 13.sp,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                            Text(
                                text = if (displayName != null) "La famille $displayName" else "Bienvenue",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(Color.White.copy(alpha = 0.2f), CircleShape)
                                .clickable { },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Notifications,
                                contentDescription = "Notifications",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                            // Badge
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .align(Alignment.TopEnd)
                                    .offset(x = (-6).dp, y = 6.dp)
                                    .background(Color(0xFFFBBF24), CircleShape)
                            )
                        }
                    }

                    // Search bar
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onNavigateToExplore),
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White,
                        shadowElevation = 8.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = null,
                                tint = Color(0xFF9CA3AF),
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Chercher une idée, une activité…",
                                fontSize = 14.sp,
                                color = Color(0xFF9CA3AF)
                            )
                        }
                    }

                    // Stats
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        StatCard(
                            label = "Idées sauvegardées",
                            value = savedCount.toString(),
                            modifier = Modifier.weight(1f)
                        )
                        StatCard(
                            label = "Partagées",
                            value = sharedCount.toString(),
                            modifier = Modifier.weight(1f)
                        )
                        StatCard(
                            label = "Ma tribu",
                            value = tribeCount.toString(),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Categories
        item {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Catégories",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF111827)
                    )
                    TextButton(onClick = onNavigateToExplore) {
                        Text(
                            text = "Voir tout",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFF97316)
                        )
                        Icon(
                            imageVector = Icons.Filled.ChevronRight,
                            contentDescription = null,
                            tint = Color(0xFFF97316),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                // Grid de catégories
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    categories.chunked(3).forEach { rowCategories ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            rowCategories.forEach { category ->
                                CategoryCard(
                                    category = category,
                                    onClick = { onNavigateToCategory(category.id) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Tendances
        if (trendingLinks.isNotEmpty()) {
            item {
                Column(
                    modifier = Modifier.padding(bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Whatshot,
                            contentDescription = null,
                            tint = Color(0xFFF97316),
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Tendances",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF111827)
                        )
                    }

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(trendingLinks) { link ->
                            IdeaCard(
                                link = link,
                                compact = true,
                                onClick = { onNavigateToDetail(link.id) },
                                onSaveClick = { onSaveLink(link) },
                                sessionManager = sessionManager,
                                modifier = Modifier.width(240.dp)
                            )
                        }
                    }
                }
            }
        }

        // Récemment ajoutées
        if (recentLinks.isNotEmpty()) {
            item {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Schedule,
                            contentDescription = null,
                            tint = Color(0xFF6B7280),
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Récemment ajoutées",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF111827)
                        )
                    }

                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        recentLinks.forEach { link ->
                            IdeaCard(
                                link = link,
                                onClick = { onNavigateToDetail(link.id) },
                                onSaveClick = { onSaveLink(link) },
                                sessionManager = sessionManager
                            )
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
    
    // Bouton flottant IA
    FloatingActionButton(
        onClick = onNavigateToAI,
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(bottom = 100.dp, end = 20.dp),
        containerColor = Color(0xFF8B5CF6),
        contentColor = Color.White
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.AutoAwesome,
                contentDescription = "Générer avec l'IA",
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = "IA",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
    }
}

@Composable
private fun StatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.height(70.dp),
        shape = RoundedCornerShape(12.dp),
        color = Color.White.copy(alpha = 0.2f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = value,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = label,
                fontSize = 10.sp,
                color = Color.White.copy(alpha = 0.8f),
                maxLines = 1
            )
        }
    }
}

@Composable
fun CategoryCard(
    category: CategoryInfo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = category.bgColor,
        border = androidx.compose.foundation.BorderStroke(1.dp, category.color.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = category.emoji,
                fontSize = 28.sp
            )
            Text(
                text = category.name,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF1F2937)
            )
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = category.color.copy(alpha = 0.2f)
            ) {
                Text(
                    text = "${category.count} idées",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = category.color,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
    }
}

