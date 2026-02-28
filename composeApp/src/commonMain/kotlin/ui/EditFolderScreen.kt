package ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import data.Collaborator
import data.Folder
import data.FolderColor
import data.FolderIcon
import kotlinx.coroutines.launch
import viewmodel.LinkViewModel

private enum class FolderVisibility(val label: String, val apiValue: String, val description: String) {
    PRIVATE("Privé", "PRIVATE", "Visible uniquement par vous"),
    PUBLIC("Public", "PUBLIC", "Visible par toute la communauté"),
    SHARED("Partagé", "SHARED", "Accessible via un lien de partage")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditFolderScreen(folder: Folder, viewModel: LinkViewModel, onBack: () -> Unit) {
    var name by remember { mutableStateOf(folder.name) }
    var selectedIcon by remember { mutableStateOf(folder.icon) }
    var selectedColor by remember { mutableStateOf(folder.color) }
    var selectedVisibility by remember {
        mutableStateOf(
            when (folder.visibility.uppercase()) {
                "PUBLIC", "VISIBILITY_PUBLIC" -> FolderVisibility.PUBLIC
                "SHARED", "VISIBILITY_SHARED" -> FolderVisibility.SHARED
                else -> FolderVisibility.PRIVATE
            }
        )
    }
    var shareUrl by remember { mutableStateOf("") }
    var isSharing by remember { mutableStateOf(false) }
    var showCopied by remember { mutableStateOf(false) }
    // Collaborator state
    var collabEmail by remember { mutableStateOf("") }
    var collabRole by remember { mutableStateOf("COLLABORATOR_ROLE_EDITOR") }
    var collabError by remember { mutableStateOf("") }
    var isAddingCollab by remember { mutableStateOf(false) }
    var currentCollaborators by remember { mutableStateOf(folder.collaborators) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Scaffold(
        containerColor = SurfaceColor,
        topBar = {
            TopAppBar(
                title = { Text("Modifier la liste", fontWeight = FontWeight.Bold) },
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
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Nom
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text("Nom de la liste") },
                leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null) },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Orange,
                    focusedContainerColor = CardColor,
                    unfocusedContainerColor = CardColor
                )
            )

            // Visibilité
            Text("Visibilité", fontWeight = FontWeight.SemiBold, color = TextSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FolderVisibility.entries.forEach { vis ->
                    val selected = selectedVisibility == vis
                    val icon = when (vis) {
                        FolderVisibility.PRIVATE -> Icons.Default.Lock
                        FolderVisibility.PUBLIC -> Icons.Default.Public
                        FolderVisibility.SHARED -> Icons.Default.Share
                    }
                    FilterChip(
                        selected = selected,
                        onClick = { selectedVisibility = vis },
                        label = { Text(vis.label, fontSize = 13.sp) },
                        leadingIcon = {
                            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Orange,
                            selectedLabelColor = Color.White,
                            selectedLeadingIconColor = Color.White
                        )
                    )
                }
            }
            Text(
                selectedVisibility.description,
                fontSize = 12.sp, color = TextSecondary
            )

            // Partage (visible si partagé ou public)
            if (selectedVisibility != FolderVisibility.PRIVATE) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = BlueSkyLight)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Link, contentDescription = null, tint = BlueSky,
                                modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Lien de partage", fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp, color = TextPrimary)
                        }

                        if (shareUrl.isNotEmpty()) {
                            Text(shareUrl, fontSize = 12.sp, color = TextSecondary, maxLines = 2)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("Tribbae", shareUrl))
                                        showCopied = true
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Orange),
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    Icon(if (showCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                                        null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(if (showCopied) "Copié" else "Copier", fontSize = 13.sp)
                                }
                                OutlinedButton(
                                    onClick = {
                                        val intent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, "Découvre ma liste sur Tribbae : $shareUrl")
                                        }
                                        context.startActivity(Intent.createChooser(intent, "Partager la liste"))
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    Icon(Icons.Default.Share, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Partager", fontSize = 13.sp)
                                }
                            }
                        } else {
                            Button(
                                onClick = {
                                    isSharing = true
                                    scope.launch {
                                        val result = viewModel.shareFolder(folder.id)
                                        if (result != null) {
                                            shareUrl = result.shareUrl
                                        }
                                        isSharing = false
                                    }
                                },
                                enabled = !isSharing,
                                colors = ButtonDefaults.buttonColors(containerColor = BlueSky),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                if (isSharing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = Color.White, strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(Icons.Default.Link, null, modifier = Modifier.size(16.dp))
                                }
                                Spacer(Modifier.width(8.dp))
                                Text("Générer un lien de partage", fontSize = 13.sp)
                            }
                        }
                    }
                }
            }

            // Collaborateurs
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardColor)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Group, contentDescription = null, tint = Orange,
                            modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Collaborateurs", fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp, color = TextPrimary)
                    }

                    // Liste des collaborateurs existants
                    if (currentCollaborators.isNotEmpty()) {
                        currentCollaborators.forEach { collab ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(OrangeLight.copy(alpha = 0.15f))
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    if (collab.role == "COLLABORATOR_ROLE_EDITOR") Icons.Default.Edit
                                    else Icons.Default.Visibility,
                                    contentDescription = null,
                                    tint = if (collab.role == "COLLABORATOR_ROLE_EDITOR") Orange else BlueSky,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        collab.displayName.ifBlank { collab.email },
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = TextPrimary
                                    )
                                    if (collab.displayName.isNotBlank() && collab.email.isNotBlank()) {
                                        Text(collab.email, fontSize = 11.sp, color = TextSecondary)
                                    }
                                }
                                Text(
                                    if (collab.role == "COLLABORATOR_ROLE_EDITOR") "Éditeur" else "Lecteur",
                                    fontSize = 11.sp, color = TextSecondary
                                )
                                Spacer(Modifier.width(4.dp))
                                IconButton(
                                    onClick = {
                                        scope.launch {
                                            val updated = viewModel.removeCollaborator(folder.id, collab.userId)
                                            if (updated != null) {
                                                currentCollaborators = updated.collaborators
                                            }
                                        }
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Retirer",
                                        tint = Color.Red.copy(alpha = 0.6f),
                                        modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    } else {
                        Text("Aucun collaborateur", fontSize = 12.sp, color = TextSecondary)
                    }

                    // Ajout d'un collaborateur
                    OutlinedTextField(
                        value = collabEmail,
                        onValueChange = { collabEmail = it; collabError = "" },
                        label = { Text("Email de l'utilisateur") },
                        leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Orange,
                            focusedContainerColor = SurfaceColor,
                            unfocusedContainerColor = SurfaceColor
                        )
                    )

                    // Sélection du rôle
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = collabRole == "COLLABORATOR_ROLE_EDITOR",
                            onClick = { collabRole = "COLLABORATOR_ROLE_EDITOR" },
                            label = { Text("Éditeur", fontSize = 13.sp) },
                            leadingIcon = {
                                Icon(Icons.Default.Edit, contentDescription = null,
                                    modifier = Modifier.size(16.dp))
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Orange,
                                selectedLabelColor = Color.White,
                                selectedLeadingIconColor = Color.White
                            )
                        )
                        FilterChip(
                            selected = collabRole == "COLLABORATOR_ROLE_VIEWER",
                            onClick = { collabRole = "COLLABORATOR_ROLE_VIEWER" },
                            label = { Text("Lecteur", fontSize = 13.sp) },
                            leadingIcon = {
                                Icon(Icons.Default.Visibility, contentDescription = null,
                                    modifier = Modifier.size(16.dp))
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = BlueSky,
                                selectedLabelColor = Color.White,
                                selectedLeadingIconColor = Color.White
                            )
                        )
                    }

                    if (collabError.isNotEmpty()) {
                        Text(collabError, fontSize = 12.sp, color = Color.Red)
                    }

                    Button(
                        onClick = {
                            if (collabEmail.isBlank()) {
                                collabError = "Veuillez saisir un email"
                                return@Button
                            }
                            isAddingCollab = true
                            scope.launch {
                                val updated = viewModel.addCollaborator(
                                    folder.id, collabEmail.trim(), collabRole
                                )
                                if (updated != null) {
                                    currentCollaborators = updated.collaborators
                                    collabEmail = ""
                                    collabError = ""
                                } else {
                                    collabError = "Erreur lors de l'ajout"
                                }
                                isAddingCollab = false
                            }
                        },
                        enabled = !isAddingCollab,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Orange)
                    ) {
                        if (isAddingCollab) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color.White, strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.PersonAdd, null, modifier = Modifier.size(16.dp))
                        }
                        Spacer(Modifier.width(8.dp))
                        Text("Inviter", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    }
                }
            }

            // Icône
            Text("Icône", fontWeight = FontWeight.SemiBold, color = TextSecondary)
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
                            imageVector = folderIconVector(Folder("", "", icon)),
                            contentDescription = null,
                            tint = if (sel) Color.White else TextSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            // Couleur
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
                            Icons.Default.Check, contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.align(Alignment.Center).size(18.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Bouton enregistrer
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        viewModel.updateFolder(
                            folder.copy(
                                name = name.trim(),
                                icon = selectedIcon,
                                color = selectedColor,
                                visibility = selectedVisibility.apiValue
                            )
                        )
                        onBack()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Orange)
            ) {
                Icon(Icons.Default.Save, null)
                Spacer(Modifier.width(8.dp))
                Text("Enregistrer", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
