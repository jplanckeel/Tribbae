package ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import data.FolderColor
import data.FolderIcon
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import viewmodel.LinkViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModernAddFolderScreen(viewModel: LinkViewModel, onBack: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var selectedIcon by remember { mutableStateOf(FolderIcon.FOLDER) }
    var selectedColor by remember { mutableStateOf(FolderColor.ORANGE) }
    var selectedVisibility by remember { mutableStateOf("PRIVATE") }
    var submitted by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

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
                        .background(Color(0xFFF97316), CircleShape),
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
                    text = "Dossier créé ! ✨",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF111827)
                )
                Text(
                    text = "Votre nouveau dossier est prêt.",
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
                        text = "Nouveau dossier",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF111827)
                    )
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
                // Nom
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Nom du dossier *",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF374151)
                    )
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        placeholder = { Text("Ex : Vacances d'été…", fontSize = 14.sp) },
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

                // Icône
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Icône",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF374151)
                    )
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(6),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.heightIn(max = 280.dp)
                    ) {
                        items(FolderIcon.entries) { icon ->
                            val isSelected = selectedIcon == icon
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isSelected) Color(0xFFF97316) 
                                        else Color(0xFFF9FAFB)
                                    )
                                    .border(
                                        width = 1.5.dp,
                                        color = if (isSelected) Color(0xFFF97316) 
                                               else Color(0xFFE5E7EB),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .clickable { selectedIcon = icon },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = folderIconVector(data.Folder("", "", icon)),
                                    contentDescription = null,
                                    tint = if (isSelected) Color.White 
                                           else Color(0xFF6B7280),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }

                // Couleur
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Couleur",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF374151)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FolderColor.entries.forEach { color ->
                            val colorValue = FolderColors[color.name] ?: Color(0xFFF97316)
                            val isSelected = selectedColor == color
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(colorValue)
                                    .border(
                                        width = if (isSelected) 3.dp else 0.dp,
                                        color = Color.White,
                                        shape = CircleShape
                                    )
                                    .clickable { selectedColor = color },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Visibilité
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Visibilité",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF374151)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Privé
                        FilterChip(
                            selected = selectedVisibility == "PRIVATE",
                            onClick = { selectedVisibility = "PRIVATE" },
                            label = { Text("Privé", fontSize = 13.sp) },
                            leadingIcon = {
                                Icon(
                                    Icons.Filled.Lock,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF6B7280),
                                selectedLabelColor = Color.White,
                                selectedLeadingIconColor = Color.White
                            )
                        )
                        // Public
                        FilterChip(
                            selected = selectedVisibility == "PUBLIC",
                            onClick = { selectedVisibility = "PUBLIC" },
                            label = { Text("Public", fontSize = 13.sp) },
                            leadingIcon = {
                                Icon(
                                    Icons.Filled.Public,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF10B981),
                                selectedLabelColor = Color.White,
                                selectedLeadingIconColor = Color.White
                            )
                        )
                        // Partagé
                        FilterChip(
                            selected = selectedVisibility == "SHARED",
                            onClick = { selectedVisibility = "SHARED" },
                            label = { Text("Partagé", fontSize = 13.sp) },
                            leadingIcon = {
                                Icon(
                                    Icons.Filled.Share,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF3B82F6),
                                selectedLabelColor = Color.White,
                                selectedLeadingIconColor = Color.White
                            )
                        )
                    }
                    Text(
                        text = when (selectedVisibility) {
                            "PUBLIC" -> "Visible par toute la communauté"
                            "SHARED" -> "Accessible via un lien de partage"
                            else -> "Visible uniquement par vous"
                        },
                        fontSize = 12.sp,
                        color = Color(0xFF6B7280)
                    )
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
                        if (name.isNotBlank()) {
                            viewModel.addFolder(name.trim(), selectedIcon, selectedColor, selectedVisibility)
                            submitted = true
                            scope.launch {
                                delay(1500)
                                onBack()
                            }
                        }
                    },
                    enabled = name.isNotBlank(),
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
                        text = "Créer le dossier ✨",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

