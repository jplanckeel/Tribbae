package ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import data.LinkCategory
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ui.components.getCategoryColor
import ui.components.getCategoryEmoji
import viewmodel.LinkViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModernAddLinkScreen(
    viewModel: LinkViewModel,
    onBack: () -> Unit,
    initialUrl: String? = null,
    onNavigateToAI: () -> Unit = {}
) {
    var title by remember { mutableStateOf("") }
    var url by remember { mutableStateOf(initialUrl ?: "") }
    var description by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<LinkCategory?>(null) }
    var tags by remember { mutableStateOf("") }
    var tagInput by remember { mutableStateOf("") }
    var tagsList by remember { mutableStateOf<List<String>>(emptyList()) }
    var isPublic by remember { mutableStateOf(false) }
    var price by remember { mutableStateOf("") }
    var ageRange by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var rating by remember { mutableStateOf(0) }
    var selectedFolderId by remember { mutableStateOf<String?>(null) }
    var expandedFolder by remember { mutableStateOf(false) }
    var submitted by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val folders by viewModel.folders.collectAsState()

    val categories = listOf(
        LinkCategory.ACTIVITE to "Activités",
        LinkCategory.CADEAU to "Cadeaux",
        LinkCategory.RECETTE to "Recettes",
        LinkCategory.EVENEMENT to "Événements",
        LinkCategory.IDEE to "Idées",
        LinkCategory.LIVRE to "Livres",
        LinkCategory.DECORATION to "Décorations"
    )

    // Écran de confirmation
    if (submitted) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF9FAFB)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(Color(0xFFFF6B35), Color(0xFFF97316))
                            ),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }
                Text(
                    text = "Idée ajoutée ! 🎉",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF111827)
                )
                Text(
                    text = "Votre idée a bien été sauvegardée dans votre tribu.",
                    fontSize = 14.sp,
                    color = Color(0xFF6B7280)
                )
            }
        }
        return
    }

    Scaffold(
        containerColor = Color(0xFFF9FAFB)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Header
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
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFF3F4F6))
                                .clickable(onClick = onBack),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ArrowBack,
                                contentDescription = "Retour",
                                tint = Color(0xFF6B7280),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Text(
                            text = "Nouvelle idée",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF111827)
                        )
                    }
                    
                    // Bouton IA
                    Surface(
                        onClick = onNavigateToAI,
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF8B5CF6)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AutoAwesome,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "IA",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
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
                // Upload photo
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(176.dp)
                        .clickable { /* TODO: Image picker */ },
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFFFFF7ED),
                    border = androidx.compose.foundation.BorderStroke(
                        width = 2.dp,
                        color = Color(0xFFF97316)
                    )
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(Color(0xFFF97316), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CameraAlt,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Text(
                            text = "Ajouter une photo",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFF97316)
                        )
                        Text(
                            text = "PNG, JPG jusqu'à 10MB",
                            fontSize = 12.sp,
                            color = Color(0xFF9CA3AF)
                        )
                    }
                }

                // Titre
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Titre *",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF374151)
                    )
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        placeholder = { Text("Ex : Randonnée au Mont Blanc…", fontSize = 14.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White,
                            focusedBorderColor = Color(0xFFF97316),
                            unfocusedBorderColor = Color(0xFFF3F4F6)
                        )
                    )
                }

                // Catégorie
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Catégorie *",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF374151)
                    )
                    
                    // Grille 3 colonnes
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        categories.chunked(3).forEach { rowCategories ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowCategories.forEach { (category, label) ->
                                    val isSelected = selectedCategory == category
                                    val categoryColor = getCategoryColor(category)
                                    val bgColor = if (isSelected) categoryColor else categoryColor.copy(alpha = 0.1f)

                                    Surface(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { selectedCategory = category },
                                        shape = RoundedCornerShape(16.dp),
                                        color = bgColor,
                                        border = androidx.compose.foundation.BorderStroke(
                                            width = 2.dp,
                                            color = if (isSelected) categoryColor else Color.Transparent
                                        )
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(10.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = getCategoryEmoji(category),
                                                fontSize = 22.sp
                                            )
                                            Text(
                                                text = label,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (isSelected) Color.White else categoryColor
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Description
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Description",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF374151)
                    )
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        placeholder = { Text("Décrivez votre idée en quelques mots…", fontSize = 14.sp) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White,
                            focusedBorderColor = Color(0xFFF97316),
                            unfocusedBorderColor = Color(0xFFF3F4F6)
                        )
                    )
                }

                // URL
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Lien URL",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF374151)
                    )
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        placeholder = { Text("https://...", fontSize = 14.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White,
                            focusedBorderColor = Color(0xFFF97316),
                            unfocusedBorderColor = Color(0xFFF3F4F6)
                        )
                    )
                }

                // Tags
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Tags",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF374151)
                    )
                    
                    // Tags actuels (chips)
                    if (tagsList.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            tagsList.forEach { tag ->
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = Color(0xFFFFF7ED)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = tag,
                                            fontSize = 13.sp,
                                            color = Color(0xFFF97316),
                                            fontWeight = FontWeight.Medium
                                        )
                                        Icon(
                                            imageVector = Icons.Filled.Close,
                                            contentDescription = "Supprimer",
                                            tint = Color(0xFFF97316),
                                            modifier = Modifier
                                                .size(14.dp)
                                                .clickable {
                                                    tagsList = tagsList.filter { it != tag }
                                                    tags = tagsList.joinToString(", ")
                                                }
                                        )
                                    }
                                }
                            }
                        }
                    }
                    
                    // Champ de saisie avec bouton Ajouter
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = tagInput,
                            onValueChange = { tagInput = it },
                            placeholder = { Text("Ajouter un tag", fontSize = 14.sp) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color.White,
                                focusedBorderColor = Color(0xFFF97316),
                                unfocusedBorderColor = Color(0xFFF3F4F6)
                            ),
                            singleLine = true
                        )
                        Button(
                            onClick = {
                                if (tagInput.isNotBlank() && !tagsList.contains(tagInput.trim())) {
                                    tagsList = tagsList + tagInput.trim()
                                    tags = tagsList.joinToString(", ")
                                    tagInput = ""
                                }
                            },
                            enabled = tagInput.isNotBlank(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFF97316)
                            ),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    
                    // Suggestions de tags
                    val suggestedTags = listOf("famille", "enfants", "nature", "vacances", "weekend", "anniversaire")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        suggestedTags.take(4).forEach { suggestion ->
                            if (!tagsList.contains(suggestion)) {
                                Surface(
                                    onClick = {
                                        tagsList = tagsList + suggestion
                                        tags = tagsList.joinToString(", ")
                                    },
                                    shape = RoundedCornerShape(16.dp),
                                    color = Color(0xFFF3F4F6)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Add,
                                            contentDescription = null,
                                            tint = Color(0xFF6B7280),
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Text(
                                            text = suggestion,
                                            fontSize = 11.sp,
                                            color = Color(0xFF6B7280)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Dossier (menu déroulant)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Dossier",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF374151)
                    )
                    
                    val selectedFolder = folders.find { it.id == selectedFolderId }
                    
                    Surface(
                        onClick = { expandedFolder = true },
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White,
                        border = androidx.compose.foundation.BorderStroke(
                            width = 1.dp,
                            color = Color(0xFFF3F4F6)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (selectedFolder != null) Icons.Filled.Folder else Icons.Filled.FolderOpen,
                                    contentDescription = null,
                                    tint = if (selectedFolder != null) Color(0xFFF97316) else Color(0xFF9CA3AF),
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = selectedFolder?.name ?: "Mes idées (sans dossier)",
                                    fontSize = 14.sp,
                                    color = if (selectedFolder != null) Color(0xFF111827) else Color(0xFF9CA3AF)
                                )
                            }
                            Icon(
                                imageVector = Icons.Filled.ArrowDropDown,
                                contentDescription = null,
                                tint = Color(0xFF9CA3AF)
                            )
                        }
                        
                        DropdownMenu(
                            expanded = expandedFolder,
                            onDismissRequest = { expandedFolder = false }
                        ) {
                            // Option "Sans dossier"
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.FolderOpen,
                                            contentDescription = null,
                                            tint = Color(0xFF9CA3AF),
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Text("Mes idées (sans dossier)", fontSize = 14.sp)
                                    }
                                },
                                onClick = {
                                    selectedFolderId = null
                                    expandedFolder = false
                                }
                            )
                            
                            if (folders.isNotEmpty()) {
                                HorizontalDivider()
                            }
                            
                            // Liste des dossiers
                            folders.forEach { folder ->
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Folder,
                                                contentDescription = null,
                                                tint = Color(0xFFF97316),
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Text(folder.name, fontSize = 14.sp)
                                        }
                                    },
                                    onClick = {
                                        selectedFolderId = folder.id
                                        expandedFolder = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Informations complémentaires
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
                            text = "Informations complémentaires",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF374151)
                        )
                        
                        // Prix
                        OutlinedTextField(
                            value = price,
                            onValueChange = { price = it },
                            label = { Text("Prix", fontSize = 14.sp) },
                            placeholder = { Text("Ex: 25€, Gratuit…", fontSize = 14.sp) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.Euro,
                                    contentDescription = null,
                                    tint = Color(0xFF9CA3AF),
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color(0xFFF9FAFB),
                                unfocusedContainerColor = Color(0xFFF9FAFB),
                                focusedBorderColor = Color(0xFFF97316),
                                unfocusedBorderColor = Color(0xFFE5E7EB)
                            )
                        )
                        
                        // Âge
                        OutlinedTextField(
                            value = ageRange,
                            onValueChange = { ageRange = it },
                            label = { Text("Tranche d'âge", fontSize = 14.sp) },
                            placeholder = { Text("Ex: 3-6 ans, Adultes…", fontSize = 14.sp) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.ChildCare,
                                    contentDescription = null,
                                    tint = Color(0xFF9CA3AF),
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color(0xFFF9FAFB),
                                unfocusedContainerColor = Color(0xFFF9FAFB),
                                focusedBorderColor = Color(0xFFF97316),
                                unfocusedBorderColor = Color(0xFFE5E7EB)
                            )
                        )
                        
                        // Lieu
                        LocationSearchField(
                            value = location,
                            onValueChange = { location = it },
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        // Note (étoiles)
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Note",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF6B7280)
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                (1..5).forEach { star ->
                                    Icon(
                                        imageVector = if (star <= rating) Icons.Filled.Star else Icons.Filled.StarBorder,
                                        contentDescription = "Note $star étoiles",
                                        tint = if (star <= rating) Color(0xFFFBBF24) else Color(0xFFD1D5DB),
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clickable { rating = star }
                                    )
                                }
                                if (rating > 0) {
                                    TextButton(onClick = { rating = 0 }) {
                                        Text("Effacer", fontSize = 12.sp, color = Color(0xFF9CA3AF))
                                    }
                                }
                            }
                        }
                    }
                }

                // Visibilité
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
                            text = "Visibilité",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF374151)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Privé
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { isPublic = false },
                                shape = RoundedCornerShape(12.dp),
                                color = if (!isPublic) Color(0xFFFFF7ED) else Color(0xFFF9FAFB),
                                border = androidx.compose.foundation.BorderStroke(
                                    width = 2.dp,
                                    color = if (!isPublic) Color(0xFFF97316) else Color(0xFFF3F4F6)
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Lock,
                                        contentDescription = null,
                                        tint = if (!isPublic) Color(0xFFF97316) else Color(0xFF9CA3AF),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = "Privé",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (!isPublic) Color(0xFFF97316) else Color(0xFF6B7280)
                                    )
                                    Text(
                                        text = "Ma famille uniquement",
                                        fontSize = 10.sp,
                                        color = Color(0xFF9CA3AF)
                                    )
                                }
                            }

                            // Public
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { isPublic = true },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isPublic) Color(0xFFFFF7ED) else Color(0xFFF9FAFB),
                                border = androidx.compose.foundation.BorderStroke(
                                    width = 2.dp,
                                    color = if (isPublic) Color(0xFFF97316) else Color(0xFFF3F4F6)
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Public,
                                        contentDescription = null,
                                        tint = if (isPublic) Color(0xFFF97316) else Color(0xFF9CA3AF),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = "Public",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isPublic) Color(0xFFF97316) else Color(0xFF6B7280)
                                    )
                                    Text(
                                        text = "Toute la communauté",
                                        fontSize = 10.sp,
                                        color = Color(0xFF9CA3AF)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(80.dp))
            }

            // Bouton submit fixe en bas
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.White,
                shadowElevation = 12.dp
            ) {
                Button(
                    onClick = {
                        if (title.isNotEmpty() && selectedCategory != null) {
                            val tagsList = tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                            viewModel.addLink(
                                title = title,
                                url = url,
                                description = description,
                                category = selectedCategory!!,
                                folderId = selectedFolderId,
                                tags = tagsList,
                                ageRange = ageRange,
                                location = location,
                                price = price,
                                rating = rating
                            )
                            submitted = true
                            scope.launch {
                                delay(1500)
                                onBack()
                            }
                        }
                    },
                    enabled = title.isNotEmpty() && selectedCategory != null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                        .height(48.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFF97316),
                        disabledContainerColor = Color(0xFFD1D5DB)
                    )
                ) {
                    Text(
                        text = "Sauvegarder l'idée ✨",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
