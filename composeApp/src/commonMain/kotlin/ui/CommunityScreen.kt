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
import data.ApiClient
import data.ApiFolder
import data.ApiLink
import kotlinx.coroutines.launch

@Composable
fun CommunityScreen(
    modifier: Modifier = Modifier,
    apiClient: ApiClient
) {
    val scope = rememberCoroutineScope()
    var folders by remember { mutableStateOf<List<ApiFolder>>(emptyList()) }
    var search by remember { mutableStateOf("") }
    var nextToken by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var selectedFolder by remember { mutableStateOf<ApiFolder?>(null) }
    var folderLinks by remember { mutableStateOf<List<ApiLink>>(emptyList()) }

    fun fetchFolders(append: Boolean = false) {
        scope.launch {
            loading = true
            try {
                val res = apiClient.listCommunityFolders(
                    search = search,
                    pageToken = if (append) nextToken else ""
                )
                folders = if (append) folders + res.folders else res.folders
                nextToken = res.nextPageToken
            } catch (_: Exception) { }
            loading = false
        }
    }

    LaunchedEffect(Unit) { fetchFolders() }

    // Vue détail d'un dossier communautaire
    if (selectedFolder != null) {
        Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { selectedFolder = null }) {
                    Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Retour", tint = Orange)
                }
                Column {
                    Text(selectedFolder!!.name, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = TextPrimary)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(imageVector = Icons.Default.Public, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(14.dp))
                        if (selectedFolder!!.ownerDisplayName.isNotBlank()) {
                            Text("par ${selectedFolder!!.ownerDisplayName}", fontSize = 12.sp, color = TextSecondary)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            if (folderLinks.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Liste vide", color = TextSecondary)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(folderLinks) { link ->
                        CommunityLinkCard(link = link)
                    }
                }
            }
        }
        return
    }

    // Vue liste communautaire
    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = Icons.Default.Public, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Communauté", fontWeight = FontWeight.Bold, fontSize = 24.sp, color = TextPrimary)
        }
        Spacer(modifier = Modifier.height(12.dp))

        // Barre de recherche
        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            placeholder = { Text("Rechercher une liste...", color = Color.Gray) },
            leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = Color(0xFF4CAF50)) },
            trailingIcon = {
                if (search.isNotEmpty()) {
                    IconButton(onClick = { search = ""; fetchFolders() }) {
                        Icon(imageVector = Icons.Default.Clear, contentDescription = null)
                    }
                }
            },
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF4CAF50),
                unfocusedBorderColor = Color(0xFF4CAF50).copy(alpha = 0.3f),
                focusedContainerColor = SurfaceColor,
                unfocusedContainerColor = SurfaceColor
            )
        )

        // Bouton rechercher
        if (search.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = { fetchFolders() }) {
                Text("Rechercher", color = Color(0xFF4CAF50), fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (folders.isEmpty() && !loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(imageVector = Icons.Default.Public, contentDescription = null,
                        modifier = Modifier.size(64.dp), tint = Color(0xFF4CAF50).copy(alpha = 0.3f))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Aucune liste communautaire", color = TextSecondary)
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 16.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(folders) { folder ->
                    Card(
                        onClick = {
                            selectedFolder = folder
                            // Charger les liens via share token
                            scope.launch {
                                try {
                                    if (folder.shareToken.isNotBlank()) {
                                        val res = apiClient.getSharedFolder(folder.shareToken)
                                        folderLinks = res.links
                                    } else {
                                        folderLinks = emptyList()
                                    }
                                } catch (_: Exception) {
                                    folderLinks = emptyList()
                                }
                            }
                        },
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = CardColor),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("🌍", fontSize = 28.sp)
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(folder.name, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (folder.ownerDisplayName.isNotBlank()) {
                                        Text("par ${folder.ownerDisplayName}", fontSize = 12.sp, color = TextSecondary)
                                    }
                                    if (folder.linkCount > 0) {
                                        Text("${folder.linkCount} idée${if (folder.linkCount > 1) "s" else ""}",
                                            fontSize = 12.sp, color = TextSecondary)
                                    }
                                }
                            }
                            Icon(imageVector = Icons.Default.ChevronRight, contentDescription = null, tint = Color.LightGray)
                        }
                    }
                }

                // Bouton "Voir plus"
                if (nextToken.isNotBlank()) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                            Button(
                                onClick = { fetchFolders(append = true) },
                                enabled = !loading,
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                            ) {
                                Text(if (loading) "Chargement..." else "Voir plus")
                            }
                        }
                    }
                }
            }
        }

        if (loading && folders.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFF4CAF50))
            }
        }
    }
}

@Composable
private fun CommunityLinkCard(link: ApiLink) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardColor),
        elevation = CardDefaults.cardElevation(2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(link.title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = TextPrimary)
            if (link.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(link.description, fontSize = 13.sp, color = TextSecondary, maxLines = 2)
            }
            if (link.url.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(link.url, fontSize = 11.sp, color = Orange, maxLines = 1)
            }
        }
    }
}
