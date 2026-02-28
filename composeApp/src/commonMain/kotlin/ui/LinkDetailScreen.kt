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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import data.Link
import data.LinkCategory

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
    val catColor = CategoryColors[link.category.name] ?: Orange
    var showSaveDialog by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = SurfaceColor,
        topBar = {
            TopAppBar(
                title = { Text("Détails", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Retour")
                    }
                },
                actions = {
                    if (!readOnly) {
                        IconButton(onClick = onEdit) {
                            Icon(imageVector = Icons.Default.Edit, contentDescription = "Modifier", tint = Color.White)
                        }
                        IconButton(onClick = onDelete) {
                            Icon(imageVector = Icons.Default.Delete, contentDescription = "Supprimer", tint = Color.White)
                        }
                    } else if (onSaveToMyList != null && !saved) {
                        IconButton(onClick = { showSaveDialog = true }) {
                            Icon(imageVector = Icons.Default.BookmarkAdd, contentDescription = "Ajouter à mes listes", tint = Color.White)
                        }
                    } else if (saved) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Ajouté",
                            tint = Color.White,
                            modifier = Modifier.padding(end = 12.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Orange,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize()
                .verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Image d'aperçu OG
            if (link.imageUrl.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(18.dp))
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
                            .height(60.dp)
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.4f))
                                )
                            )
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(64.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(catColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = categoryIcon(link.category),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(link.title, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                    if (link.ownerDisplayName.isNotBlank()) {
                        Text(
                            "par ${link.ownerDisplayName}",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                    Surface(shape = RoundedCornerShape(8.dp), color = catColor.copy(alpha = 0.2f)) {
                        Text(link.category.label, fontSize = 12.sp, color = catColor,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                    }
                    if (link.rating > 0) {
                        Spacer(Modifier.height(6.dp))
                        StarRating(rating = link.rating, starSize = 20)
                    }
                }
            }

            if (link.url.isNotEmpty()) {
                if (onOpenUrl != null) {
                    // URL cliquable
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = CardColor),
                        modifier = Modifier.clickable { onOpenUrl(link.url) }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(imageVector = Icons.Default.OpenInBrowser, contentDescription = null,
                                tint = catColor, modifier = Modifier.size(20.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Lien", fontSize = 11.sp, color = TextSecondary)
                                Text(
                                    link.url,
                                    fontWeight = FontWeight.Medium,
                                    color = catColor,
                                    textDecoration = TextDecoration.Underline,
                                    maxLines = 1
                                )
                            }
                            Icon(imageVector = Icons.Default.ChevronRight, contentDescription = null,
                                tint = Color.LightGray, modifier = Modifier.size(18.dp))
                        }
                    }
                } else {
                    InfoRow(Icons.Default.Link, "URL", link.url, catColor)
                }
            }

            if (link.description.isNotEmpty()) {
                Card(shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = CardColor)) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Notes, contentDescription = null, tint = catColor)
                            Spacer(Modifier.width(8.dp))
                            Text("Description", fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(link.description, color = TextSecondary)
                    }
                }
            }

            Card(shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = CardColor)) {
                Column(Modifier.padding(16.dp), Arrangement.spacedBy(10.dp)) {
                    if (link.eventDate != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            InfoRow(Icons.Default.CalendarMonth, "Date", formatDate(link.eventDate), catColor)
                            if (link.reminderEnabled) {
                                Spacer(Modifier.width(12.dp))
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Orange.copy(alpha = 0.15f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.NotificationsActive,
                                            contentDescription = null,
                                            tint = Orange,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text("Rappel", fontSize = 11.sp, color = Orange, fontWeight = FontWeight.Medium)
                                    }
                                }
                            }
                        }
                    }
                    if (link.price.isNotEmpty()) InfoRow(Icons.Default.Euro, "Prix", link.price, catColor)
                    if (link.location.isNotEmpty()) {
                        // Location cliquable → ouvre Google Maps
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = CardColor),
                            modifier = Modifier.clickable {
                                val encoded = java.net.URLEncoder.encode(link.location, "UTF-8")
                                onOpenUrl?.invoke("https://www.google.com/maps/search/?api=1&query=$encoded")
                            }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(imageVector = Icons.Default.LocationOn, contentDescription = null,
                                    tint = catColor, modifier = Modifier.size(20.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Lieu", fontSize = 11.sp, color = TextSecondary)
                                    Text(
                                        link.location,
                                        fontWeight = FontWeight.Medium,
                                        color = catColor,
                                        textDecoration = TextDecoration.Underline
                                    )
                                }
                                Icon(imageVector = Icons.Default.Map, contentDescription = "Ouvrir dans Maps",
                                    tint = catColor, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                    if (link.ageRange.isNotEmpty()) InfoRow(Icons.Default.Person, "Âge", link.ageRange, catColor)
                }
            }

            if (link.tags.isNotEmpty()) {
                Text("Tags", fontWeight = FontWeight.SemiBold, color = TextSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    link.tags.forEach { tag ->
                        Surface(shape = RoundedCornerShape(12.dp), color = catColor.copy(alpha = 0.12f)) {
                            Text("#$tag", color = catColor, fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
                        }
                    }
                }
            }

            // Ingrédients (recettes)
            if (link.category == LinkCategory.RECETTE && link.ingredients.isNotEmpty()) {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = CardColor)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.FormatListBulleted, contentDescription = null, tint = catColor)
                            Spacer(Modifier.width(8.dp))
                            Text("Ingrédients", fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.width(8.dp))
                            Surface(shape = CircleShape, color = catColor) {
                                Text(
                                    "${link.ingredients.size}",
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                    fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        link.ingredients.forEach { ingredient ->
                            Row(
                                modifier = Modifier.padding(vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(catColor))
                                Spacer(Modifier.width(10.dp))
                                Text(ingredient, fontSize = 14.sp, color = TextPrimary)
                            }
                        }
                    }
                }
            }

            // Bouton "Ajouter à mes listes" en bas pour les liens publics
            if (readOnly && onSaveToMyList != null && !saved) {
                Button(
                    onClick = { showSaveDialog = true },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Orange)
                ) {
                    Icon(imageVector = Icons.Default.BookmarkAdd, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Ajouter à mes listes", fontWeight = FontWeight.SemiBold)
                }
            }
            if (saved) {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(18.dp))
                        Text("Idée ajoutée à vos listes", color = Color(0xFF4CAF50), fontWeight = FontWeight.Medium, fontSize = 14.sp)
                    }
                }
            }
        }
    }

    // Dialog de sélection de dossier
    if (showSaveDialog && onSaveToMyList != null) {
        var selectedFolderId by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            icon = { Icon(imageVector = Icons.Default.BookmarkAdd, contentDescription = null, tint = Orange) },
            title = { Text("Ajouter à mes listes", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Choisissez une liste (optionnel)", fontSize = 14.sp, color = TextSecondary)
                    // Option "Sans liste"
                    FilterChip(
                        selected = selectedFolderId == null,
                        onClick = { selectedFolderId = null },
                        label = { Text("Mes idées (sans liste)") },
                        leadingIcon = { Icon(imageVector = Icons.Default.Lightbulb, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Orange,
                            selectedLabelColor = Color.White,
                            selectedLeadingIconColor = Color.White
                        )
                    )
                    folders.forEach { folder ->
                        FilterChip(
                            selected = selectedFolderId == folder.id,
                            onClick = { selectedFolderId = folder.id },
                            label = { Text(folder.name) },
                            leadingIcon = { Icon(imageVector = folderIconVector(folder), contentDescription = null, modifier = Modifier.size(16.dp)) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Orange,
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
                    Text("Ajouter", color = Orange, fontWeight = FontWeight.Bold)
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

@Composable
fun InfoRow(icon: ImageVector, label: String, value: String, color: Color = Orange) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Column {
            Text(label, fontSize = 11.sp, color = TextSecondary)
            Text(value, fontWeight = FontWeight.Medium, color = TextPrimary)
        }
    }
}
