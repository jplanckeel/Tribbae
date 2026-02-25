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
import data.FolderColor
import data.FolderIcon
import viewmodel.LinkViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFolderScreen(viewModel: LinkViewModel, onBack: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var selectedIcon by remember { mutableStateOf(FolderIcon.FOLDER) }
    var selectedColor by remember { mutableStateOf(FolderColor.ORANGE) }

    Scaffold(
        containerColor = SurfaceColor,
        topBar = {
            TopAppBar(
                title = { Text("Nouvelle liste", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Retour")
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
            modifier = Modifier.padding(padding).fillMaxSize().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text("Nom de la liste") },
                leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null) },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Orange, focusedContainerColor = CardColor, unfocusedContainerColor = CardColor)
            )

            Text("Icône", fontWeight = FontWeight.SemiBold, color = TextSecondary)
            // Grille d'icônes scrollable
            LazyVerticalGrid(
                columns = GridCells.Fixed(6),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.heightIn(max = 280.dp)
            ) {
                items(FolderIcon.entries) { icon ->
                    val sel = selectedIcon == icon
                    Box(
                        modifier = Modifier.size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (sel) Orange else OrangeLight.copy(alpha = 0.2f))
                            .border(if (sel) 2.dp else 0.dp, Orange, RoundedCornerShape(12.dp))
                            .clickable { selectedIcon = icon },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = folderIconVector(data.Folder("", "", icon)),
                            contentDescription = null,
                            tint = if (sel) Color.White else TextSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            Text("Couleur", fontWeight = FontWeight.SemiBold, color = TextSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FolderColor.entries.forEach { color ->
                    val c = FolderColors[color.name] ?: Orange
                    val sel = selectedColor == color
                    Box(
                        modifier = Modifier.size(36.dp).clip(CircleShape).background(c)
                            .border(if (sel) 3.dp else 0.dp, Color.White, CircleShape)
                            .clickable { selectedColor = color }
                    ) {
                        if (sel) Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.align(Alignment.Center).size(18.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            Button(
                onClick = {
                    if (name.isNotBlank()) { viewModel.addFolder(name.trim(), selectedIcon, selectedColor); onBack() }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Orange)
            ) {
                Icon(Icons.Default.CreateNewFolder, null)
                Spacer(Modifier.width(8.dp))
                Text("Créer la liste", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
