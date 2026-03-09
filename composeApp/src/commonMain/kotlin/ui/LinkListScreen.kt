package ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import data.Link
import data.LinkCategory

// Shared composables

@Composable
fun FolderChip(label: String, icon: ImageVector, color: Color, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = color,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
    }
}

/**
 * Charge et affiche une image depuis une URL avec Coil.
 */
@Composable
fun NetworkImage(
    url: String,
    contentDescription: String?,
    modifier: Modifier,
    contentScale: ContentScale
) {
    SubcomposeAsyncImage(
        model = url,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        loading = {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color(0xFFF5F5F5)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Image,
                    contentDescription = null,
                    tint = Color.LightGray,
                    modifier = Modifier.size(40.dp)
                )
            }
        },
        error = {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color(0xFFF5F5F5)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Image,
                    contentDescription = null,
                    tint = Color.LightGray,
                    modifier = Modifier.size(40.dp)
                )
            }
        }
    )
}

/** Mode d'affichage des liens */
enum class LinkViewMode { LIST, GRID }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinkCard(link: Link, onClick: () -> Unit, onFavoriteToggle: (() -> Unit)? = null) {
    val catColor = CategoryColors[link.category.name] ?: Color(0xFFE0E0E0)
    val hasImage = link.imageUrl.isNotBlank()

    Card(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            // Image avec overlay
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            ) {
                if (hasImage) {
                    NetworkImage(
                        url = link.imageUrl,
                        contentDescription = link.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    // Gradient overlay
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.4f)
                                    )
                                )
                            )
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(catColor.copy(alpha = 0.08f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = categoryIcon(link.category),
                            contentDescription = null,
                            tint = catColor.copy(alpha = 0.3f),
                            modifier = Modifier.size(64.dp)
                        )
                    }
                }
                
                // Badge catégorie en haut à gauche
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = catColor.copy(alpha = 0.95f),
                    modifier = Modifier.padding(12.dp).align(Alignment.TopStart)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            when (link.category) {
                                LinkCategory.IDEE -> "💡"
                                LinkCategory.CADEAU -> "🎁"
                                LinkCategory.ACTIVITE -> "🏃"
                                LinkCategory.EVENEMENT -> "📅"
                                LinkCategory.RECETTE -> "🍳"
                                LinkCategory.LIVRE -> "📚"
                            },
                            fontSize = 11.sp
                        )
                        Text(
                            link.category.label,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                
                // Bouton favori en haut à droite
                if (onFavoriteToggle != null) {
                    Surface(
                        onClick = onFavoriteToggle,
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.padding(12.dp).align(Alignment.TopEnd).size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (link.favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = null,
                                tint = if (link.favorite) Color(0xFFEF4444) else Color(0xFF6B7280),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
                
                // Rating en bas à gauche
                if (link.rating > 0) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.padding(12.dp).align(Alignment.BottomStart)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = Color(0xFFF59E0B),
                                modifier = Modifier.size(11.dp)
                            )
                            Text(
                                link.rating.toString(),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF1F2937)
                            )
                        }
                    }
                }
            }

            // Contenu texte
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    link.title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    maxLines = 2,
                    color = Color(0xFF111827),
                    lineHeight = 18.sp
                )
                
                if (link.description.isNotEmpty()) {
                    Text(
                        link.description,
                        color = Color(0xFF6B7280),
                        fontSize = 12.sp,
                        maxLines = 2,
                        lineHeight = 16.sp
                    )
                }
                
                // Footer avec auteur et tags
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Auteur
                    if (link.ownerDisplayName.isNotBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = catColor,
                                modifier = Modifier.size(24.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        link.ownerDisplayName.take(1).uppercase(),
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Text(
                                link.ownerDisplayName,
                                fontSize = 12.sp,
                                color = Color(0xFF6B7280)
                            )
                        }
                    }
                    
                    // Likes (si disponible)
                    if (link.likeCount > 0) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = if (link.likedByMe) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = null,
                                tint = if (link.likedByMe) Color(0xFFEF4444) else Color(0xFF9CA3AF),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                link.likeCount.toString(),
                                fontSize = 12.sp,
                                color = Color(0xFF9CA3AF)
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Carte vignette compacte pour le mode grille */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinkCardGrid(link: Link, onClick: () -> Unit, onFavoriteToggle: (() -> Unit)? = null) {
    val catColor = CategoryColors[link.category.name] ?: Color(0xFFE0E0E0)
    val hasImage = link.imageUrl.isNotBlank()

    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            // Image avec overlay
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            ) {
                if (hasImage) {
                    NetworkImage(
                        url = link.imageUrl,
                        contentDescription = link.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.3f))
                                )
                            )
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(catColor.copy(alpha = 0.08f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = categoryIcon(link.category),
                            contentDescription = null,
                            tint = catColor.copy(alpha = 0.3f),
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }
                
                // Badge catégorie
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = catColor.copy(alpha = 0.95f),
                    modifier = Modifier.padding(8.dp).align(Alignment.TopStart)
                ) {
                    Text(
                        when (link.category) {
                            LinkCategory.IDEE -> "💡"
                            LinkCategory.CADEAU -> "🎁"
                            LinkCategory.ACTIVITE -> "🏃"
                            LinkCategory.EVENEMENT -> "📅"
                            LinkCategory.RECETTE -> "🍳"
                            LinkCategory.LIVRE -> "📚"
                        },
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                    )
                }
                
                // Favori
                if (onFavoriteToggle != null) {
                    Surface(
                        onClick = onFavoriteToggle,
                        shape = RoundedCornerShape(10.dp),
                        color = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.padding(8.dp).align(Alignment.TopEnd).size(28.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (link.favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = null,
                                tint = if (link.favorite) Color(0xFFEF4444) else Color(0xFF6B7280),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
                
                // Rating
                if (link.rating > 0) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.padding(8.dp).align(Alignment.BottomStart)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = Color(0xFFF59E0B),
                                modifier = Modifier.size(10.dp)
                            )
                            Text(
                                link.rating.toString(),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF1F2937)
                            )
                        }
                    }
                }
            }

            // Texte en bas
            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    link.title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    maxLines = 2,
                    color = Color(0xFF111827),
                    lineHeight = 15.sp
                )
                
                if (link.ownerDisplayName.isNotBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = catColor,
                            modifier = Modifier.size(18.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    link.ownerDisplayName.take(1).uppercase(),
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Text(
                            link.ownerDisplayName,
                            fontSize = 10.sp,
                            color = Color(0xFF6B7280),
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MiniChip(icon: ImageVector, text: String, color: Color = TextSecondary) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(13.dp), tint = color.copy(alpha = 0.7f))
        Spacer(modifier = Modifier.width(3.dp))
        Text(text, fontSize = 11.sp, color = color.copy(alpha = 0.7f))
    }
}

fun categoryIcon(category: LinkCategory): ImageVector = when (category) {
    LinkCategory.IDEE -> Icons.Default.Lightbulb
    LinkCategory.CADEAU -> Icons.Default.CardGiftcard
    LinkCategory.ACTIVITE -> Icons.Default.DirectionsRun
    LinkCategory.EVENEMENT -> Icons.Default.Event
    LinkCategory.RECETTE -> Icons.Default.Restaurant
    LinkCategory.LIVRE -> Icons.Default.MenuBook
}

fun folderIconVector(folder: data.Folder): ImageVector = when (folder.icon) {
    data.FolderIcon.FOLDER -> Icons.Default.Folder
    data.FolderIcon.STAR -> Icons.Default.Star
    data.FolderIcon.HEART -> Icons.Default.Favorite
    data.FolderIcon.BOOKMARK -> Icons.Default.Bookmark
    data.FolderIcon.HOME -> Icons.Default.Home
    data.FolderIcon.WORK -> Icons.Default.Work
    data.FolderIcon.TRAVEL -> Icons.Default.Flight
    data.FolderIcon.SHOPPING -> Icons.Default.ShoppingCart
    // Nature / Outdoor
    data.FolderIcon.CAMPING -> Icons.Default.Cabin
    data.FolderIcon.PARK -> Icons.Default.Park
    data.FolderIcon.BEACH -> Icons.Default.BeachAccess
    data.FolderIcon.MOUNTAIN -> Icons.Default.Landscape
    data.FolderIcon.FOREST -> Icons.Default.Forest
    // Activités
    data.FolderIcon.SPORTS -> Icons.Default.SportsSoccer
    data.FolderIcon.MUSIC -> Icons.Default.MusicNote
    data.FolderIcon.GAMES -> Icons.Default.SportsEsports
    data.FolderIcon.PALETTE -> Icons.Default.Palette
    data.FolderIcon.MOVIE -> Icons.Default.Movie
    // Nourriture
    data.FolderIcon.RESTAURANT -> Icons.Default.Restaurant
    data.FolderIcon.CAKE -> Icons.Default.Cake
    data.FolderIcon.COFFEE -> Icons.Default.Coffee
    // Ambiance
    data.FolderIcon.CANDLE -> Icons.Default.LocalFireDepartment
    data.FolderIcon.PIRATE -> Icons.Default.Sailing
    data.FolderIcon.MAGIC -> Icons.Default.AutoAwesome
    data.FolderIcon.PARTY -> Icons.Default.Celebration
    data.FolderIcon.ROCKET -> Icons.Default.RocketLaunch
    // Famille
    data.FolderIcon.BABY -> Icons.Default.ChildCare
    data.FolderIcon.PETS -> Icons.Default.Pets
    data.FolderIcon.SCHOOL -> Icons.Default.School
}

@Composable
fun StarRating(
    rating: Int,
    onRatingChange: ((Int) -> Unit)? = null,
    starSize: Int = 20,
    activeColor: Color = Yellow
) {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        for (i in 1..5) {
            val icon = if (i <= rating) Icons.Default.Star else Icons.Default.StarOutline
            if (onRatingChange != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = "$i étoile${if (i > 1) "s" else ""}",
                    tint = if (i <= rating) activeColor else Color.LightGray,
                    modifier = Modifier.size(starSize.dp).clickable {
                        onRatingChange(if (rating == i) 0 else i)
                    }
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (i <= rating) activeColor else Color.LightGray,
                    modifier = Modifier.size(starSize.dp)
                )
            }
        }
    }
}

/**
 * Extrait le nom de la ville d'une adresse Nominatim.
 * Ex: "Lyon, Métropole de Lyon, Auvergne-Rhône-Alpes, France" → "Lyon"
 * Ex: "12 Rue de la Paix, Paris, France" → "Paris" (2e segment si le 1er ressemble à une adresse)
 */
fun extractCityName(fullLocation: String): String {
    val parts = fullLocation.split(",").map { it.trim() }
    if (parts.size <= 1) return fullLocation.trim()
    // Si le premier segment contient un numéro (adresse), prendre le 2e
    val first = parts[0]
    return if (first.any { it.isDigit() } && parts.size > 1) parts[1] else first
}
