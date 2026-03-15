package ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(getCategoryEmoji(category), fontSize = 20.sp)
                        Text(getCategoryLabel(category), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, "Retour")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Search bar
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Rechercher dans ${getCategoryLabel(category)}...", fontSize = 14.sp) },
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
            }

            // Result count
            item {
                Text(
                    text = "${filteredLinks.size} idée${if (filteredLinks.size > 1) "s" else ""}",
                    fontSize = 14.sp,
                    color = Color(0xFF6B7280),
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }

            items(filteredLinks) { link ->
                IdeaCard(
                    link = link,
                    onClick = { onLinkClick(link) },
                    onSaveClick = { onSaveLink(link) }
                )
            }

            if (filteredLinks.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = if (searchQuery.isBlank()) getCategoryEmoji(category) else "🔍",
                            fontSize = 64.sp
                        )
                        Text(
                            text = if (searchQuery.isBlank()) "Aucune idée dans cette catégorie"
                                   else "Aucun résultat pour \"$searchQuery\"",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF6B7280)
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }
}
