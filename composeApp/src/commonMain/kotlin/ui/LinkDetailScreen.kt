package ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import data.Link
import ui.components.getCategoryColor
import ui.components.getCategoryEmoji
import ui.components.getCategoryLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinkDetailScreen(
    link: Link,
    onBack: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit = {},
    onOpenUrl: ((String) -> Unit)? = null,
    readOnly: Boolean = false,
    onSaveToMyList: ((Link, String?) -> Unit)? = null,
    folders: List<data.Folder> = emptyList()
) {
    val categoryColor = getCategoryColor(link.category)
    var showSaveDialog by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(link.favorite) }
    var liked by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF9FAFB))
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Hero image avec overlay
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(288.dp)
            ) {
                AsyncImage(
                    model = link.imageUrl.ifEmpty { "https://images.unsplash.com/photo-1516450360452-9312f5e86fc7?w=400" },
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
                                    Color.Black.copy(alpha = 0.1f),
                                    Color.Black.copy(alpha = 0.6f)
                                )
                            )
                        )
                )

                // Back button
                Box(
                    modifier = Modifier
                        .padding(start = 20.dp, top = 48.dp)
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.3f))
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.ArrowBack,
                        contentDescription = "Retour",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Action buttons
                Row(
                    modifier = Modifier
                        .padding(end = 20.dp, top = 48.dp)
                        .align(Alignment.TopEnd),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.3f))
                            .clickable { saved = !saved },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (saved) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                            contentDescription = "Save",
                            tint = if (saved) Color(0xFFF97316) else Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    if (!readOnly) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.3f))
                                .clickable(onClick = onEdit),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Edit,
                                contentDescription = "Modifier",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // Category & title overlay
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = categoryColor
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = getCategoryEmoji(link.category),
                                fontSize = 12.sp
                            )
                            Text(
                                text = getCategoryLabel(link.category),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }
                    }
                    Text(
                        text = link.title,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        lineHeight = 28.6.sp
                    )
                }
            }

            // Content avec rounded top
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .offset(y = (-16).dp),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                color = Color.White
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 100.dp)
                ) {
                    // Meta info
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (link.rating > 0) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Star,
                                    contentDescription = null,
                                    tint = Color(0xFFF59E0B),
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = link.rating.toString(),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1F2937)
                                )
                            }
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (readOnly) Icons.Filled.Public else Icons.Filled.Lock,
                                contentDescription = null,
                                tint = Color(0xFF6B7280),
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = if (readOnly) "Public" else "Privé",
                                fontSize = 13.sp,
                                color = Color(0xFF6B7280)
                            )
                        }
                    }

                    Divider(color = Color(0xFFF3F4F6), thickness = 1.dp)

                    // Author
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(categoryColor, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = (link.ownerDisplayName.firstOrNull()?.uppercase() ?: "?"),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = link.ownerDisplayName.ifBlank { "Anonyme" },
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF111827)
                            )
                            Text(
                                text = "Partagé récemment",
                                fontSize = 12.sp,
                                color = Color(0xFF9CA3AF)
                            )
                        }
                        Button(
                            onClick = { },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = categoryColor),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "Suivre",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Divider(color = Color(0xFFF3F4F6), thickness = 1.dp)

                    // Description
                    if (link.description.isNotEmpty()) {
                        Column(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Description",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF111827)
                            )
                            Text(
                                text = link.description,
                                fontSize = 14.sp,
                                color = Color(0xFF6B7280),
                                lineHeight = 22.4.sp
                            )
                        }
                    }

                    // Tags
                    if (link.tags.isNotEmpty()) {
                        Row(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            link.tags.forEach { tag ->
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = categoryColor.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = "#$tag",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = categoryColor,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Bottom CTA
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter),
            color = Color.White,
            shadowElevation = 12.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    modifier = Modifier
                        .size(48.dp)
                        .clickable { liked = !liked },
                    shape = RoundedCornerShape(16.dp),
                    color = if (liked) Color(0xFFFEF2F2) else Color.White,
                    border = androidx.compose.foundation.BorderStroke(
                        width = 2.dp,
                        color = if (liked) Color(0xFFEF4444) else Color(0xFFE5E7EB)
                    )
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = "Like",
                            tint = if (liked) Color(0xFFEF4444) else Color(0xFF6B7280),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Surface(
                    modifier = Modifier
                        .size(48.dp)
                        .clickable { },
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    border = androidx.compose.foundation.BorderStroke(
                        width = 2.dp,
                        color = Color(0xFFE5E7EB)
                    )
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.ChatBubble,
                            contentDescription = "Comment",
                            tint = Color(0xFF6B7280),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Button(
                    onClick = { saved = !saved },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (saved) Color(0xFF9CA3AF) else Color(0xFFF97316)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Filled.Bookmark,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = Color.White
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (saved) "Sauvegardé ✓" else "Sauvegarder",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }

    // Dialog de sélection de dossier (conservé pour compatibilité)
    if (showSaveDialog && onSaveToMyList != null) {
        var selectedFolderId by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            icon = { Icon(imageVector = Icons.Default.BookmarkAdd, contentDescription = null, tint = Color(0xFFF97316)) },
            title = { Text("Ajouter à mes listes", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Choisissez une liste (optionnel)", fontSize = 14.sp, color = Color(0xFF6B7280))
                    FilterChip(
                        selected = selectedFolderId == null,
                        onClick = { selectedFolderId = null },
                        label = { Text("Mes idées (sans liste)") },
                        leadingIcon = { Icon(imageVector = Icons.Default.Lightbulb, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFFF97316),
                            selectedLabelColor = Color.White,
                            selectedLeadingIconColor = Color.White)
                    )
                    folders.forEach { folder ->
                        FilterChip(
                            selected = selectedFolderId == folder.id,
                            onClick = { selectedFolderId = folder.id },
                            label = { Text(folder.name) },
                            leadingIcon = { Icon(imageVector = folderIconVector(folder), contentDescription = null, modifier = Modifier.size(16.dp)) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFFF97316),
                                selectedLabelColor = Color.White,
                                selectedLeadingIconColor = Color.White
                            )
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onSaveToMyList(link, selectedFolderId)
                    showSaveDialog = false
                    saved = true
                }) {
                    Text("Ajouter", color = Color(0xFFF97316), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) {
                    Text("Annuler")
                }
            }
        )
    }
}
