package ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
        containerColor = Color(0xFFF9FAFB)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Header moderne
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.White,
                shadowElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFF3F4F6))
                            .clickable(onClick = { handleBack() }),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = "Retour",
                            tint = Color(0xFF6B7280),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Générer avec l'IA",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF111827)
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AutoAwesome,
                                contentDescription = null,
                                tint = Color(0xFF8B5CF6),
                                modifier = Modifier.size(11.dp)
                            )
                            Text(
                                text = "Propulsé par l'IA",
                                fontSize = 12.sp,
                                color = Color(0xFF6B7280)
                            )
                        }
                    }
                }
            }

            // Contenu scrollable
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Champ prompt
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    shadowElevation = 2.dp
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Décrivez ce que vous cherchez",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF374151)
                        )
                        OutlinedTextField(
                            value = prompt,
                            onValueChange = { prompt = it },
                            placeholder = { 
                                Text(
                                    "Ex: anniversaire pirate pour un enfant de 2 ans",
                                    fontSize = 14.sp,
                                    color = Color(0xFF9CA3AF)
                                ) 
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            minLines = 3,
                            maxLines = 5,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color(0xFFF9FAFB),
                                unfocusedContainerColor = Color(0xFFF9FAFB),
                                focusedBorderColor = Color(0xFF8B5CF6),
                                unfocusedBorderColor = Color(0xFFE5E7EB)
                            )
                        )
                    }
                }

                // Exemples rapides
                if (ideas.isEmpty() && !loading) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White,
                        shadowElevation = 2.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Lightbulb,
                                    contentDescription = null,
                                    tint = Color(0xFFFBBF24),
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Suggestions",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF374151)
                                )
                            }
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                examplePrompts.forEach { ex ->
                                    Surface(
                                        onClick = { prompt = ex },
                                        shape = RoundedCornerShape(20.dp),
                                        color = Color(0xFF8B5CF6).copy(alpha = 0.1f)
                                    ) {
                                        Text(
                                            text = ex,
                                            fontSize = 12.sp,
                                            color = Color(0xFF8B5CF6),
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Bouton générer
                Button(
                    onClick = { viewModel.generateAiIdeas(prompt) },
                    enabled = prompt.isNotBlank() && !loading,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF8B5CF6),
                        disabledContainerColor = Color(0xFFD1D5DB)
                    )
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
                        Text("Générer des idées ✨", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    }
                }

                // Erreur
                error?.let { err ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFFFFEBEE)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Error,
                                contentDescription = null,
                                tint = Color(0xFFEF4444),
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = if (err.contains("unreachable")) "Ollama inaccessible — lance `ollama serve`"
                                       else err,
                                color = Color(0xFFB71C1C),
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                // Résultats
                if (ideas.isNotEmpty()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White,
                        shadowElevation = 2.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        "${ideas.size} idées générées",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF111827)
                                    )
                                    Text(
                                        "${selectedIndices.size} sélectionnée${if (selectedIndices.size > 1) "s" else ""}",
                                        fontSize = 12.sp,
                                        color = Color(0xFF6B7280)
                                    )
                                }
                                TextButton(onClick = {
                                    selectedIndices = if (selectedIndices.size == ideas.size) emptySet()
                                    else ideas.indices.toSet()
                                }) {
                                    Text(
                                        if (selectedIndices.size == ideas.size) "Tout désélectionner" else "Tout sélectionner",
                                        color = Color(0xFF8B5CF6),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            // Sélecteur de liste
                            if (folders.isNotEmpty()) {
                                Divider(color = Color(0xFFF3F4F6), thickness = 1.dp)
                                Text(
                                    text = "Ajouter à une liste",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF6B7280)
                                )
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
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Filled.Lightbulb,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = Color(0xFF8B5CF6).copy(alpha = 0.15f),
                                            selectedLabelColor = Color(0xFF8B5CF6)
                                        )
                                    )
                                    folders.forEach { folder ->
                                        FilterChip(
                                            selected = selectedFolderId == folder.id,
                                            onClick = { selectedFolderId = folder.id },
                                            label = { Text(folder.name, fontSize = 12.sp) },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = folderIconVector(folder),
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = Color(0xFF8B5CF6).copy(alpha = 0.15f),
                                                selectedLabelColor = Color(0xFF8B5CF6)
                                            )
                                        )
                                    }
                                }
                            }

                            Divider(color = Color(0xFFF3F4F6), thickness = 1.dp)

                            // Liste des idées
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                ideas.forEachIndexed { index, idea ->
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
                }

                Spacer(modifier = Modifier.height(80.dp))
            }

            // Bouton sauvegarder fixe en bas
            if (ideas.isNotEmpty() && selectedIndices.isNotEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.White,
                    shadowElevation = 12.dp
                ) {
                    Button(
                        onClick = {
                            if (!saved) {
                                val toSave = selectedIndices.sorted().map { idx ->
                                    val idea = ideas[idx]
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                            .height(48.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (saved) Color(0xFF10B981) else Color(0xFF8B5CF6)
                        )
                    ) {
                        Icon(
                            imageVector = if (saved) Icons.Filled.Check else Icons.Filled.Add,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (saved) "Ajouté !"
                            else "Ajouter ${selectedIndices.size} idée${if (selectedIndices.size > 1) "s" else ""}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
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
                        tint = Color(0xFFF97316)
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
                        Text("Quitter", color = Color(0xFFF97316), fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showExitDialog = false }) {
                        Text("Annuler")
                    }
                }
            )
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

    val catColor = ui.components.getCategoryColor(category)
    val borderColor = if (selected) Color(0xFF8B5CF6) else Color.Transparent
    val bgColor = if (selected) Color(0xFF8B5CF6).copy(alpha = 0.05f) else Color(0xFFF9FAFB)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() },
        shape = RoundedCornerShape(12.dp),
        color = bgColor,
        border = androidx.compose.foundation.BorderStroke(2.dp, borderColor)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Checkbox
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(if (selected) Color(0xFF8B5CF6) else Color(0xFFE5E7EB)),
                contentAlignment = Alignment.Center
            ) {
                if (selected) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Titre et catégorie
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        idea.title,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = Color(0xFF111827),
                        modifier = Modifier.weight(1f)
                    )
                    
                    // Badge catégorie cliquable
                    Box {
                        Surface(
                            onClick = { showCategoryMenu = true },
                            shape = RoundedCornerShape(20.dp),
                            color = catColor.copy(alpha = 0.15f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    ui.components.getCategoryEmoji(category),
                                    fontSize = 10.sp
                                )
                                Text(
                                    category.label,
                                    fontSize = 11.sp,
                                    color = catColor,
                                    fontWeight = FontWeight.SemiBold
                                )
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
                                            Text(
                                                ui.components.getCategoryEmoji(cat),
                                                fontSize = 14.sp
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

                // Description
                if (idea.description.isNotBlank()) {
                    Text(
                        idea.description,
                        fontSize = 13.sp,
                        color = Color(0xFF6B7280),
                        maxLines = 2,
                        lineHeight = 18.2.sp
                    )
                }

                // Métadonnées
                val meta = buildList {
                    if (idea.ageRange.isNotBlank()) add("👶 ${idea.ageRange}")
                    if (idea.price.isNotBlank()) add("💰 ${idea.price}")
                    if (idea.location.isNotBlank()) add("📍 ${idea.location}")
                }
                if (meta.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        meta.forEach { 
                            Text(
                                it,
                                fontSize = 12.sp,
                                color = Color(0xFF6B7280)
                            ) 
                        }
                    }
                }

                // Tags
                if (idea.tags.isNotEmpty()) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        idea.tags.take(4).forEach { tag ->
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = catColor.copy(alpha = 0.1f)
                            ) {
                                Text(
                                    "#$tag",
                                    fontSize = 11.sp,
                                    color = catColor,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

