package ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.*
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
import ui.components.getCategoryLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryDetailScreen(
    category: LinkCategory,
    links: List<Link>,
    onBack: () -> Unit,
    onLinkClick: (Link) -> Unit,
    onSaveLink: (Link) -> Unit,
    modifier: Modifier = Modifier
) {
    val categoryColor = getCategoryColor(category)
    var searchQuery by remember { mutableStateOf("") }
    var isGridView by remember { mutableStateOf(true) }

    val filteredLinks = remember(links, searchQuery) {
        val sorted = links.sortedByDescending { it.updatedAt }
        if (searchQuery.isBlank()) sorted
        else sorted.filter { link ->
            link.title.contains(searchQuery, ignoreCase = true) ||
                link.description.contains(searchQuery, ignoreCase = true) ||
                link.tags.any { it.contains(searchQuery, ignoreCase = true) } ||
                link.location.contains(searchQuery, ignoreCase = true)
        }
    }

    Box(modifier = modifier.fillMaxSize().background(Color(0xFFF9FAFB))) {

        LazyColumn(modifier = Modifier.fillMaxSize()) {

            // ── Hero header — hauteur fixe, le contenu est décalé sous la status bar ──
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .background(
                            Brush.linearGradient(listOf(categoryColor, categoryColor.copy(alpha = 0.7f)))
                        )
                ) {
                    // Emoji + title + count en bas du hero
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 24.dp, bottom = 32.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(getCategoryEmoji(category), fontSize = 40.sp)
                        Text(
                            getCategoryLabel(category),
                            fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White
                        )
                        Text(
                            "${links.size} idée${if (links.size > 1) "s" else ""}",
                            fontSize = 13.sp, color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            // ── White card with search + content ──
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(y = (-20).dp),
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    color = Color.White
                ) {
                    Column(modifier = Modifier.padding(top = 20.dp, start = 16.dp, end = 16.dp)) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = {
                                Text("Rechercher dans ${getCategoryLabel(category)}...", fontSize = 14.sp)
                            },
                            leadingIcon = { Icon(Icons.Filled.Search, null, tint = Color(0xFF9CA3AF)) },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Filled.Clear, "Effacer", tint = Color(0xFF9CA3AF))
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = categoryColor,
                                unfocusedBorderColor = Color(0xFFE5E7EB),
                                unfocusedContainerColor = Color(0xFFF9FAFB),
                                focusedContainerColor = Color.White
                            ),
                            singleLine = true
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "${filteredLinks.size} résultat${if (filteredLinks.size > 1) "s" else ""}",
                            fontSize = 13.sp, color = Color(0xFF9CA3AF),
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                }
            }

            // ── Content ──
            if (filteredLinks.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            if (searchQuery.isBlank()) getCategoryEmoji(category) else "🔍",
                            fontSize = 56.sp
                        )
                        Text(
                            if (searchQuery.isBlank()) "Aucune idée dans cette catégorie"
                            else "Aucun résultat pour \"$searchQuery\"",
                            fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Color(0xFF6B7280)
                        )
                    }
                }
            } else if (isGridView) {
                val rows = filteredLinks.chunked(2)
                items(rows) { rowLinks ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        rowLinks.forEach { link ->
                            Box(modifier = Modifier.weight(1f)) {
                                IdeaCard(link = link, onClick = { onLinkClick(link) }, onSaveClick = { onSaveLink(link) })
                            }
                        }
                        if (rowLinks.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            } else {
                items(filteredLinks) { link ->
                    Box(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
                        IdeaCard(link = link, onClick = { onLinkClick(link) }, onSaveClick = { onSaveLink(link) })
                    }
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }

        // ── Boutons overlay (back + toggle) au-dessus de tout ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.25f))
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.ArrowBack, "Retour", tint = Color.White, modifier = Modifier.size(20.dp))
            }
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.25f))
                    .clickable { isGridView = !isGridView },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (isGridView) Icons.Filled.ViewList else Icons.Filled.GridView,
                    "Changer vue", tint = Color.White, modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
