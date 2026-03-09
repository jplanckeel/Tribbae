package ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
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

@Composable
fun IdeaCard(
    link: Link,
    compact: Boolean = false,
    onClick: () -> Unit,
    onSaveClick: () -> Unit,
    sessionManager: data.SessionManager? = null,
    modifier: Modifier = Modifier
) {
    var liked by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(link.favorite) }
    val categoryColor = getCategoryColor(link.category)
    
    // Utiliser le displayName de l'utilisateur connecté si ownerDisplayName est vide
    val displayName by sessionManager?.displayName?.collectAsState() ?: remember { mutableStateOf(null) }
    val ownerName = link.ownerDisplayName.ifBlank { displayName ?: "Anonyme" }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 8.dp,
        color = Color.White
    ) {
        Column {
            // Image avec overlay
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (compact) 144.dp else 192.dp)
            ) {
                if (link.imageUrl.isNotEmpty()) {
                    AsyncImage(
                        model = link.imageUrl,
                        contentDescription = link.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    // Motif avec icône de catégorie répétée
                    CategoryPatternBackground(
                        category = link.category,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                
                // Gradient overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.4f))
                            )
                        )
                )

                // Category badge
                Surface(
                    modifier = Modifier
                        .padding(12.dp)
                        .align(Alignment.TopStart),
                    shape = RoundedCornerShape(20.dp),
                    color = categoryColor.copy(alpha = 0.9f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = getCategoryEmoji(link.category),
                            fontSize = 11.sp
                        )
                        Text(
                            text = getCategoryLabel(link.category),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }
                }

                // Save button
                Box(
                    modifier = Modifier
                        .padding(12.dp)
                        .size(32.dp)
                        .align(Alignment.TopEnd)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.9f))
                        .clickable {
                            saved = !saved
                            onSaveClick()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (saved) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                        contentDescription = "Save",
                        tint = if (saved) Color(0xFFF97316) else Color(0xFF374151),
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Rating
                if (link.rating > 0) {
                    Surface(
                        modifier = Modifier
                            .padding(12.dp)
                            .align(Alignment.BottomStart),
                        shape = RoundedCornerShape(20.dp),
                        color = Color.White.copy(alpha = 0.9f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Star,
                                contentDescription = null,
                                tint = Color(0xFFF59E0B),
                                modifier = Modifier.size(11.dp)
                            )
                            Text(
                                text = link.rating.toString(),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF1F2937)
                            )
                        }
                    }
                }
            }

            // Content
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = link.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF111827),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 19.6.sp
                )

                if (!compact && link.description.isNotEmpty()) {
                    Text(
                        text = if (link.description.length > 80) {
                            link.description.take(80) + "…"
                        } else {
                            link.description
                        },
                        fontSize = 12.sp,
                        color = Color(0xFF6B7280),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 18.sp
                    )
                }

                // Localisation
                if (link.location.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.LocationOn,
                            contentDescription = null,
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = link.location,
                            fontSize = 12.sp,
                            color = Color(0xFF6B7280),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .background(categoryColor, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = (ownerName.firstOrNull()?.uppercase() ?: "?"),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        Text(
                            text = ownerName,
                            fontSize = 12.sp,
                            color = Color(0xFF6B7280)
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { liked = !liked }
                    ) {
                        Icon(
                            imageVector = if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = "Like",
                            tint = if (liked) Color(0xFFEF4444) else Color(0xFF9CA3AF),
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (liked) "1" else "0",
                            fontSize = 12.sp,
                            color = Color(0xFF9CA3AF)
                        )
                    }
                }
            }
        }
    }
}

fun getCategoryColor(category: LinkCategory): Color {
    return when (category) {
        LinkCategory.ACTIVITE -> Color(0xFF4FC3F7)
        LinkCategory.CADEAU -> Color(0xFFFF8C00)
        LinkCategory.RECETTE -> Color(0xFF81C784)
        LinkCategory.EVENEMENT -> Color(0xFFFF7043)
        LinkCategory.IDEE -> Color(0xFFFFD700)
        LinkCategory.LIVRE -> Color(0xFF9C27B0)
        LinkCategory.DECORATION -> Color(0xFFE91E63)
    }
}

fun getCategoryEmoji(category: LinkCategory): String {
    return when (category) {
        LinkCategory.ACTIVITE -> "🏃"
        LinkCategory.CADEAU -> "🎁"
        LinkCategory.RECETTE -> "🍳"
        LinkCategory.EVENEMENT -> "📅"
        LinkCategory.IDEE -> "💡"
        LinkCategory.LIVRE -> "📚"
        LinkCategory.DECORATION -> "🎨"
    }
}

fun getCategoryLabel(category: LinkCategory): String {
    return when (category) {
        LinkCategory.ACTIVITE -> "Activité"
        LinkCategory.CADEAU -> "Cadeau"
        LinkCategory.RECETTE -> "Recette"
        LinkCategory.EVENEMENT -> "Événement"
        LinkCategory.IDEE -> "Idée"
        LinkCategory.LIVRE -> "Livre"
        LinkCategory.DECORATION -> "Décoration"
    }
}

@Composable
fun CategoryPatternBackground(
    category: LinkCategory,
    modifier: Modifier = Modifier
) {
    val categoryColor = getCategoryColor(category)
    val emoji = getCategoryEmoji(category)
    
    Box(
        modifier = modifier
            .background(categoryColor.copy(alpha = 0.15f))
            .clip(RoundedCornerShape(0.dp))
    ) {
        // Motif répété en diagonale
        Box(
            modifier = Modifier
                .fillMaxSize()
                .rotate(45f)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(32.dp)
            ) {
                repeat(6) { rowIndex ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(32.dp)
                    ) {
                        repeat(6) { colIndex ->
                            Text(
                                text = emoji,
                                fontSize = 32.sp,
                                color = categoryColor.copy(alpha = 0.2f),
                                modifier = Modifier.offset(
                                    x = (colIndex * 16).dp,
                                    y = (rowIndex * 16).dp
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}
