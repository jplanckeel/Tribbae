package ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.graphicsLayer
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinkCard(link: Link, onClick: () -> Unit, onFavoriteToggle: (() -> Unit)? = null) {
    val catColor = CategoryColors[link.category.name] ?: Color(0xFFE0E0E0)
    val hasImage = link.imageUrl.isNotBlank()

    Card(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            if (hasImage) {
                // Image d'aperçu OG
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                ) {
                    NetworkImage(
                        url = link.imageUrl,
                        contentDescription = link.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.5f))
                                )
                            )
                    )
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = catColor,
                        modifier = Modifier
                            .padding(10.dp)
                            .align(Alignment.TopEnd)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = categoryIcon(link.category),
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                link.category.label,
                                fontSize = 11.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            } else {
                // Pattern de fond avec icône catégorie en répétition
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                        .background(catColor.copy(alpha = 0.10f))
                ) {
                    val icon = categoryIcon(link.category)
                    val iconSize = 20.dp
                    val spacing = 12.dp
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.SpaceEvenly
                    ) {
                        for (row in 0..4) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .offset(x = if (row % 2 == 1) 16.dp else 0.dp),
                                horizontalArrangement = Arrangement.spacedBy(
                                    spacing,
                                    Alignment.Start
                                )
                            ) {
                                for (col in 0..10) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = catColor.copy(alpha = 0.18f),
                                        modifier = Modifier.size(iconSize).graphicsLayer { rotationZ = 45f }
                                    )
                                }
                            }
                        }
                    }
                    // Badge catégorie en overlay
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = catColor,
                        modifier = Modifier
                            .padding(10.dp)
                            .align(Alignment.TopEnd)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                link.category.label,
                                fontSize = 11.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // Contenu texte
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        link.title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp,
                        maxLines = 1, color = TextPrimary
                    )
                    if (link.description.isNotEmpty()) {
                        Text(
                            link.description, color = TextSecondary,
                            fontSize = 13.sp, maxLines = 2,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(top = 6.dp)
                    ) {
                        if (link.eventDate != null) MiniChip(Icons.Default.CalendarMonth, formatDate(link.eventDate), catColor)
                        if (link.price.isNotEmpty()) MiniChip(Icons.Default.Euro, link.price, catColor)
                        if (link.location.isNotEmpty()) MiniChip(Icons.Default.LocationOn, extractCityName(link.location), catColor)
                        if (link.ageRange.isNotEmpty()) MiniChip(Icons.Default.Person, link.ageRange, catColor)
                    }
                    if (link.tags.isNotEmpty()) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(top = 5.dp)
                        ) {
                            link.tags.take(3).forEach { tag ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = catColor.copy(alpha = 0.1f)
                                ) {
                                    Text(
                                        "#$tag", fontSize = 11.sp, color = catColor,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                    if (link.rating > 0) {
                        Box(modifier = Modifier.padding(top = 4.dp)) {
                            StarRating(rating = link.rating, starSize = 16)
                        }
                    }
                }
                Icon(imageVector = Icons.Default.ChevronRight, contentDescription = null, tint = Color.LightGray)
                if (onFavoriteToggle != null) {
                    IconButton(onClick = onFavoriteToggle) {
                        Icon(
                            imageVector = if (link.favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = if (link.favorite) "Retirer des favoris" else "Ajouter aux favoris",
                            tint = if (link.favorite) Color(0xFFE91E63) else Color.LightGray,
                            modifier = Modifier.size(22.dp)
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
