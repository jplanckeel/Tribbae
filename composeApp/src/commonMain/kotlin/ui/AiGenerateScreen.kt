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

private val ALL_PROMPTS = listOf(
    // Anniversaires (15)
    "Anniversaire pirate pour un enfant de 2 ans",
    "Anniversaire princesse pour une fille de 4 ans",
    "Anniversaire dinosaures pour un garçon de 3 ans",
    "Anniversaire super-héros pour un enfant de 6 ans",
    "Anniversaire licorne pour une fille de 5 ans",
    "Anniversaire spatial pour un enfant de 7 ans",
    "Anniversaire sirène pour une fille de 6 ans",
    "Anniversaire Pokémon pour un enfant de 8 ans",
    "Anniversaire Harry Potter pour un enfant de 10 ans",
    "Anniversaire Reine des Neiges pour une fille de 4 ans",
    "Anniversaire football pour un garçon de 9 ans",
    "Anniversaire danse pour une fille de 8 ans",
    "Anniversaire scientifique pour un enfant de 11 ans",
    "Anniversaire jungle pour un enfant de 3 ans",
    "Anniversaire cirque pour un enfant de 5 ans",
    
    // Cadeaux (20)
    "Cadeaux de Noël pour une petite fille de 5 ans",
    "Cadeaux de Noël pour un garçon de 8 ans",
    "Idées cadeaux pour un bébé de 1 an",
    "Cadeaux d'anniversaire pour une adolescente de 13 ans",
    "Cadeaux éducatifs pour un enfant de 4 ans",
    "Cadeaux créatifs pour un enfant de 6 ans",
    "Cadeaux sportifs pour un garçon de 10 ans",
    "Cadeaux musicaux pour un enfant de 7 ans",
    "Cadeaux de naissance originaux",
    "Cadeaux pour enfant passionné de lecture",
    "Cadeaux écologiques pour enfants",
    "Cadeaux technologiques pour ado de 14 ans",
    "Cadeaux pour enfant qui aime cuisiner",
    "Cadeaux pour enfant qui aime les animaux",
    "Cadeaux pour enfant qui aime dessiner",
    "Cadeaux pour enfant qui aime la nature",
    "Cadeaux pour enfant qui aime les sciences",
    "Cadeaux pour enfant qui aime construire",
    "Cadeaux pour enfant qui aime la magie",
    "Cadeaux pour enfant qui aime les puzzles",
    
    // Activités intérieures (15)
    "Activités en famille pour un week-end pluvieux",
    "Activités créatives pour enfants de 3 à 6 ans",
    "Jeux de société pour toute la famille",
    "Activités manuelles pour enfants de 5 ans",
    "Bricolages de Noël avec les enfants",
    "Activités Montessori pour enfants de 2 ans",
    "Expériences scientifiques à faire à la maison",
    "Activités de peinture pour enfants",
    "Jeux d'intérieur pour anniversaire",
    "Activités de lecture pour enfants",
    "Ateliers cuisine avec les enfants",
    "Activités de yoga pour enfants",
    "Jeux de construction pour enfants",
    "Activités de musique pour enfants",
    "Théâtre et spectacles pour enfants à la maison",
    
    // Activités extérieures (15)
    "Sorties en famille autour de Lyon",
    "Activités nature pour enfants en été",
    "Parcs d'attractions en France pour familles",
    "Randonnées faciles avec enfants en bas âge",
    "Activités à la plage avec des enfants",
    "Sorties culturelles pour enfants à Paris",
    "Fermes pédagogiques près de chez moi",
    "Activités sportives en famille",
    "Balades à vélo avec enfants",
    "Zoos et aquariums à visiter en famille",
    "Châteaux à visiter avec des enfants",
    "Parcs et jardins pour pique-nique en famille",
    "Activités nautiques pour enfants",
    "Accrobranche et parcours aventure pour enfants",
    "Musées interactifs pour enfants",
    
    // Recettes (15)
    "Recettes faciles pour goûter d'anniversaire",
    "Recettes de gâteaux sans gluten pour enfants",
    "Idées de repas rapides pour toute la famille",
    "Recettes de smoothies pour les enfants",
    "Recettes de biscuits à faire avec les enfants",
    "Recettes de crêpes originales",
    "Recettes de légumes pour enfants difficiles",
    "Recettes de goûters sains pour enfants",
    "Recettes de petit-déjeuner équilibré",
    "Recettes de desserts sans sucre ajouté",
    "Recettes végétariennes pour enfants",
    "Recettes de pain maison avec les enfants",
    "Recettes de pizzas maison pour enfants",
    "Recettes de compotes et purées de fruits",
    "Recettes de snacks pour la boîte à lunch",
    
    // Voyages et vacances (15)
    "Idées de voyage en Europe avec des enfants",
    "Destinations vacances famille en France",
    "Activités à faire à Paris avec des enfants",
    "Vacances à la mer avec des enfants en bas âge",
    "Road trip en famille en Bretagne",
    "Week-end en famille en Normandie",
    "Vacances à la montagne en été avec enfants",
    "Destinations ski pour familles débutantes",
    "Camping en famille avec jeunes enfants",
    "Croisières adaptées aux familles",
    "Parcs d'attractions en Europe",
    "Vacances en Espagne avec enfants",
    "Séjour en Italie en famille",
    "Vacances nature en famille",
    "City trip avec enfants en Europe",
    
    // Événements et fêtes (10)
    "Organisation d'une fête de fin d'année scolaire",
    "Idées pour un pique-nique en famille",
    "Activités pour les vacances de Pâques",
    "Idées pour Halloween avec des enfants",
    "Activités pour la fête des mères",
    "Idées pour la fête des pères",
    "Organisation d'une chasse aux œufs de Pâques",
    "Activités pour le carnaval avec enfants",
    "Idées pour fêter le Nouvel An en famille",
    "Organisation d'une fête d'été dans le jardin",
    
    // Éducation et apprentissage (10)
    "Activités pour apprendre l'alphabet en s'amusant",
    "Jeux pour apprendre les chiffres aux enfants",
    "Activités pour développer la motricité fine",
    "Livres pour apprendre l'anglais aux enfants",
    "Applications éducatives pour enfants de 5 ans",
    "Activités pour apprendre les couleurs",
    "Jeux de mémoire pour enfants",
    "Activités pour apprendre à lire",
    "Jeux pour développer la logique",
    "Activités pour apprendre les saisons"
)

private fun getRandomPrompts(count: Int = 5) = ALL_PROMPTS.shuffled().take(count)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
    var editedCategories by remember { mutableStateOf(mapOf<Int, String>()) }
    var showExitDialog by remember { mutableStateOf(false) }
    val examplePrompts = remember { getRandomPrompts(5) }

    // Tout sélectionner quand les idées arrivent
    LaunchedEffect(ideas) {
        if (ideas.isNotEmpty()) {
            selectedIndices = ideas.indices.toSet()
            saved = false
            editedCategories = emptyMap()
        }
    }

    // Gestion du retour
    fun handleBack() {
        if (ideas.isNotEmpty() && !saved) {
            showExitDialog = true
        } else {
            viewModel.clearAiIdeas()
            onBack()
        }
    }

    Scaffold(
        containerColor = SurfaceColor,
        topBar = {
            TopAppBar(
                title = { 
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Générer avec l'IA ✨", fontWeight = FontWeight.Bold)
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Orange.copy(alpha = 0.15f)
                        ) {
                            Text(
                                "Expérimental",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = Orange,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { handleBack() }) {
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
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    examplePrompts.forEach { ex ->
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
                            editedCategory = editedCategories[index],
                            onToggle = {
                                selectedIndices = if (index in selectedIndices)
                                    selectedIndices - index
                                else
                                    selectedIndices + index
                            },
                            onCategoryChange = { newCategory ->
                                editedCategories = editedCategories + (index to newCategory)
                            }
                        )
                    }
                }
            }
        }

        // Dialog de confirmation de sortie
        if (showExitDialog) {
            AlertDialog(
                onDismissRequest = { showExitDialog = false },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = Orange
                    )
                },
                title = { Text("Quitter sans sauvegarder ?", fontWeight = FontWeight.Bold) },
                text = { Text("Les idées générées seront perdues si vous ne les sauvegardez pas.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showExitDialog = false
                            viewModel.clearAiIdeas()
                            onBack()
                        }
                    ) {
                        Text("Quitter", color = Orange, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showExitDialog = false }) {
                        Text("Annuler")
                    }
                }
            )
        }

        // FAB Sauvegarder
        if (ideas.isNotEmpty() && selectedIndices.isNotEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                ExtendedFloatingActionButton(
                    onClick = {
                        if (!saved) {
                            val toSave = selectedIndices.sorted().map { idx ->
                                val idea = ideas[idx]
                                // Appliquer la catégorie éditée si elle existe
                                if (editedCategories.containsKey(idx)) {
                                    idea.copy(category = editedCategories[idx]!!)
                                } else {
                                    idea
                                }
                            }
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
    editedCategory: String?,
    onToggle: () -> Unit,
    onCategoryChange: (String) -> Unit
) {
    val displayCategoryStr = editedCategory ?: idea.category
    val category = try {
        LinkCategory.valueOf(displayCategoryStr.removePrefix("LINK_CATEGORY_"))
    } catch (_: Exception) { LinkCategory.IDEE }

    var showCategoryMenu by remember { mutableStateOf(false) }

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
                    // Badge catégorie cliquable
                    Box {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(catColor.copy(alpha = 0.15f))
                                .clickable(onClick = { showCategoryMenu = true })
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(category.label, fontSize = 10.sp, color = catColor, fontWeight = FontWeight.Medium)
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = "Modifier",
                                    tint = catColor,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                        
                        DropdownMenu(
                            expanded = showCategoryMenu,
                            onDismissRequest = { showCategoryMenu = false }
                        ) {
                            LinkCategory.entries.forEach { cat ->
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = categoryIcon(cat),
                                                contentDescription = null,
                                                tint = CategoryColors[cat.name] ?: Orange,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Text(cat.label, fontSize = 13.sp)
                                        }
                                    },
                                    onClick = {
                                        onCategoryChange("LINK_CATEGORY_${cat.name}")
                                        showCategoryMenu = false
                                    }
                                )
                            }
                        }
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

