package ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import data.AiSuggestedLink
import data.LinkCategory
import viewmodel.LinkViewModel

private val EXAMPLE_PROMPTS = listOf(
    "Anniversaire pirate pour un enfant de 2 ans",
    "Cadeaux de Noël pour une fille de 5 ans",
    "Activités en famille pour un week-end pluvieux",
    "Recettes faciles pour goûter d'anniversaire",
    "Idées de voyage en Europe avec des enfants"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiGenerateScreen(
    viewModel: LinkViewModel,
    onBack: () -> Unit
) {
    val ideas by viewModel.aiIdeas.collectAsState()
    val loading by viewModel.aiLoading.collectAsState()
    val error by viewModel.aiError.collectAsState()
    val folders by viewModel.folders.collectAsState()

    var prompt by remember { mutableStateOf("") }
    var selectedIndices by remember { mutableStateOf(emptySet<Int>()) }
    var selectedFolderId by remember { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf(false) }

    // Tout sélectionner quand les idées arrivent
    LaunchedEffect(ideas) {
        if (ideas.isNotEmpty()) {
            selectedIndices = ideas.indices.toSet()
            saved = false
        }
    }

    Scaffold(
        containerColor = SurfaceColor,
        topBar = {
            TopAppBar(
                title = { Text("Générer avec l'IA ✨", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.clearAiIdeas(); onBack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Retour")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceColor)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            // Champ prompt
            OutlinedTextField(
                value = prompt,
                onValueChange = { prompt = it },
                placeholder = { Text("Ex: anniversaire pirate pour un enfant de 2 ans", color = Color.Gray, fontSize = 13.sp) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                minLines = 2,
                maxLines = 3,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Purple,
                    unfocusedBorderColor = Purple.copy(alpha = 0.3f),
                    focusedContainerColor = CardColor,
                    unfocusedContainerColor = CardColor
                )
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Exemples rapides (seulement si pas encore de résultats)
            if (ideas.isEmpty() && !loading) {
                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    EXAMPLE_PROMPTS.forEach { ex ->
                        SuggestionChip(
                            onClick = { prompt = ex },
                            label = { Text(ex, fontSize = 11.sp) },
                            shape = RoundedCornerShape(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            // Bouton générer
            Button(
                onClick = { viewModel.generateAiIdeas(prompt) },
                enabled = prompt.isNotBlank() && !loading,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Purple)
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Génération en cours...", fontWeight = FontWeight.SemiBold)
                } else {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Générer des idées", fontWeight = FontWeight.SemiBold)
                }
            }

            // Erreur
            error?.let { err ->
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = if (err.contains("unreachable")) "Ollama inaccessible — lance `ollama serve`"
                               else err,
                        color = Color(0xFFB71C1C),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            // Résultats
            if (ideas.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${ideas.size} idées · ${selectedIndices.size} sélectionnées",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                    TextButton(onClick = {
                        selectedIndices = if (selectedIndices.size == ideas.size) emptySet()
                        else ideas.indices.toSet()
                    }) {
                        Text(
                            if (selectedIndices.size == ideas.size) "Tout désélectionner" else "Tout sélectionner",
                            color = Purple,
                            fontSize = 12.sp
                        )
                    }
                }

                // Sélecteur de liste
                if (folders.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedFolderId == null,
                            onClick = { selectedFolderId = null },
                            label = { Text("Aucune liste", fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Purple.copy(alpha = 0.15f),
                                selectedLabelColor = Purple
                            )
                        )
                        folders.forEach { folder ->
                            FilterChip(
                                selected = selectedFolderId == folder.id,
                                onClick = { selectedFolderId = folder.id },
                                label = { Text(folder.name, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Purple.copy(alpha = 0.15f),
                                    selectedLabelColor = Purple
                                )
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 100.dp)
                ) {
                    itemsIndexed(ideas) { index, idea ->
                        AiIdeaCard(
                            idea = idea,
                            selected = index in selectedIndices,
                            onToggle = {
                                selectedIndices = if (index in selectedIndices)
                                    selectedIndices - index
                                else
                                    selectedIndices + index
                            }
                        )
                    }
                }
            }
        }

        // FAB Sauvegarder
        if (ideas.isNotEmpty() && selectedIndices.isNotEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                ExtendedFloatingActionButton(
                    onClick = {
                        if (!saved) {
                            val toSave = selectedIndices.sorted().map { ideas[it] }
                            viewModel.saveAiIdeas(toSave, selectedFolderId)
                            saved = true
                            viewModel.clearAiIdeas()
                            onBack()
                        }
                    },
                    containerColor = if (saved) Color(0xFF4CAF50) else Purple,
                    contentColor = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(20.dp),
                    icon = {
                        Icon(
                            if (saved) Icons.Default.Check else Icons.Default.Add,
                            contentDescription = null
                        )
                    },
                    text = {
                        Text(
                            if (saved) "Ajouté !"
                            else "Ajouter ${selectedIndices.size} idée${if (selectedIndices.size > 1) "s" else ""}",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun AiIdeaCard(
    idea: AiSuggestedLink,
    selected: Boolean,
    onToggle: () -> Unit
) {
    val category = try {
        LinkCategory.valueOf(idea.category.removePrefix("LINK_CATEGORY_"))
    } catch (_: Exception) { LinkCategory.IDEE }

    val catColor = CategoryColors[category.name] ?: Orange
    val borderColor = if (selected) Purple else Color.Transparent
    val bgColor = if (selected) Purple.copy(alpha = 0.04f) else CardColor

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(2.dp, borderColor, RoundedCornerShape(16.dp))
            .clickable { onToggle() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (selected) 2.dp else 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Image ou icône catégorie
            if (idea.imageUrl.isNotBlank()) {
                NetworkImage(
                    url = idea.imageUrl,
                    contentDescription = idea.title,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(catColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = categoryIcon(category),
                        contentDescription = null,
                        tint = catColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        idea.title,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    // Badge catégorie
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(catColor.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(category.label, fontSize = 10.sp, color = catColor, fontWeight = FontWeight.Medium)
                    }
                }

                if (idea.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(idea.description, fontSize = 12.sp, color = TextSecondary, maxLines = 2)
                }

                // Métadonnées
                val meta = buildList {
                    if (idea.url.isNotBlank()) add("🔗 source")
                    if (idea.ageRange.isNotBlank()) add("👶 ${idea.ageRange}")
                    if (idea.price.isNotBlank()) add("💰 ${idea.price}")
                    if (idea.location.isNotBlank()) add("📍 ${idea.location}")
                }
                if (meta.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        meta.forEach { Text(it, fontSize = 11.sp, color = TextSecondary) }
                    }
                }

                // Tags
                if (idea.tags.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        idea.tags.take(4).forEach { tag ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFFF0F0F0))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("#$tag", fontSize = 10.sp, color = TextSecondary)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Checkbox
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(if (selected) Purple else Color(0xFFE0E0E0)),
                contentAlignment = Alignment.Center
            ) {
                if (selected) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

