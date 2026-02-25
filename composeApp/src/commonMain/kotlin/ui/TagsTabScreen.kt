package ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import data.Link
import viewmodel.LinkViewModel

@Composable
fun TagsTabScreen(
    viewModel: LinkViewModel,
    modifier: Modifier = Modifier,
    onLinkClick: (Link) -> Unit = {}
) {
    val tags by viewModel.tags.collectAsState()
    var input by remember { mutableStateOf("") }
    var selectedTag by remember { mutableStateOf<String?>(null) }

    if (selectedTag != null) {
        TagDetailView(viewModel, selectedTag!!, modifier, onLinkClick) { selectedTag = null }
        return
    }

    TagListView(viewModel, tags, input, { input = it }, modifier) { selectedTag = it }
}

@Composable
private fun TagDetailView(
    viewModel: LinkViewModel,
    tag: String,
    modifier: Modifier,
    onLinkClick: (Link) -> Unit,
    onBack: () -> Unit
) {
    val allLinks by viewModel.repository.links.collectAsState()
    val tagLinks = allLinks.filter { tag in it.tags }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Retour", tint = Orange)
            }
            Surface(shape = RoundedCornerShape(10.dp), color = Orange.copy(alpha = 0.15f)) {
                Text(
                    "#$tag", color = Orange, fontWeight = FontWeight.Bold,
                    fontSize = 18.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "${tagLinks.size} élément${if (tagLinks.size > 1) "s" else ""}",
            color = TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(start = 48.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))

        if (tagLinks.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Tag, contentDescription = null,
                        modifier = Modifier.size(48.dp), tint = OrangeLight.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Aucun élément avec ce tag", color = TextSecondary)
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 16.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(tagLinks) { link ->
                    LinkCard(link = link, onClick = { onLinkClick(link) })
                }
            }
        }
    }
}

@Composable
private fun TagListView(
    viewModel: LinkViewModel,
    tags: List<String>,
    input: String,
    onInputChange: (String) -> Unit,
    modifier: Modifier,
    onTagClick: (String) -> Unit
) {
    val allLinks by viewModel.repository.links.collectAsState()

    Column(modifier = modifier.fillMaxSize().padding(20.dp)) {
        Text("Tags", fontWeight = FontWeight.Bold, fontSize = 24.sp, color = TextPrimary)
        Spacer(modifier = Modifier.height(16.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = onInputChange,
                placeholder = { Text("Nouveau tag...") },
                leadingIcon = { Icon(imageVector = Icons.Default.Tag, contentDescription = null, tint = Orange) },
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.weight(1f),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Orange,
                    focusedContainerColor = CardColor,
                    unfocusedContainerColor = CardColor
                )
            )
            FilledIconButton(
                onClick = {
                    if (input.isNotBlank()) { viewModel.addTag(input); onInputChange("") }
                },
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Orange)
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Ajouter", tint = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text("${tags.size} tag${if (tags.size > 1) "s" else ""}", color = TextSecondary, fontSize = 13.sp)
        Spacer(modifier = Modifier.height(8.dp))

        if (tags.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Tag, contentDescription = null,
                        modifier = Modifier.size(56.dp), tint = OrangeLight.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Aucun tag configuré", color = TextSecondary)
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 16.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(tags) { tag ->
                    val linkCount = allLinks.count { tag in it.tags }
                    Card(
                        onClick = { onTagClick(tag) },
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = CardColor),
                        elevation = CardDefaults.cardElevation(1.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Orange.copy(alpha = 0.12f)
                            ) {
                                Text(
                                    "#$tag", color = Orange, fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                "$linkCount élément${if (linkCount > 1) "s" else ""}",
                                fontSize = 12.sp, color = TextSecondary
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null, tint = Color.LightGray
                            )
                            IconButton(onClick = { viewModel.deleteTag(tag) }) {
                                Icon(
                                    imageVector = Icons.Default.Delete, contentDescription = "Supprimer",
                                    tint = Color.LightGray, modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
