package ui

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import viewmodel.LinkViewModel

@Composable
fun ProfileModernScreen(
    viewModel: LinkViewModel,
    modifier: Modifier = Modifier,
    sessionManager: data.SessionManager,
    authRepository: data.AuthRepository,
    onLoginSuccess: () -> Unit = {}
) {
    val children by viewModel.children.collectAsState()
    val links by viewModel.repository.links.collectAsState()
    val isLoggedIn by sessionManager.isLoggedIn.collectAsState()
    val displayName by sessionManager.displayName.collectAsState()
    
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showAuthDialog by remember { mutableStateOf(false) }
    var showFamilyDialog by remember { mutableStateOf(false) }

    val savedCount = links.count { it.favorite }
    val familyCount = children.size + 1

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF9FAFB))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 100.dp)
        ) {
            // Header avec gradient
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color(0xFFFF6B35), Color(0xFFF97316))
                        )
                    )
                    .padding(top = 48.dp, bottom = 32.dp, start = 20.dp, end = 20.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(CircleShape)
                                    .background(Color.White)
                                    .clickable { /* TODO: Choisir avatar */ },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = (displayName?.firstOrNull()?.uppercase() ?: "U"),
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFF97316)
                                )
                            }

                            Column {
                                Text(
                                    text = displayName ?: "Utilisateur",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = "@${(displayName ?: "user").lowercase().replace(" ", ".")}",
                                    fontSize = 13.sp,
                                    color = Color.White.copy(alpha = 0.8f)
                                )
                            }
                        }
                        
                        IconButton(
                            onClick = { /* TODO: Éditer profil */ },
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.2f))
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Edit,
                                contentDescription = "Éditer profil",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // Stats
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        StatCard(
                            icon = Icons.Filled.Bookmark,
                            value = links.size.toString(),
                            label = "Idées",
                            modifier = Modifier.weight(1f)
                        )
                        StatCard(
                            icon = Icons.Filled.Star,
                            value = savedCount.toString(),
                            label = "Sauvegardées",
                            modifier = Modifier.weight(1f)
                        )
                        StatCard(
                            icon = Icons.Filled.People,
                            value = familyCount.toString(),
                            label = "Tribu",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Content
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Ma famille section
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Ma famille",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF111827)
                        )
                        
                        if (children.isNotEmpty()) {
                            TextButton(
                                onClick = { showFamilyDialog = true },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "Gérer",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFFF97316)
                                )
                                Icon(
                                    imageVector = Icons.Filled.ChevronRight,
                                    contentDescription = null,
                                    tint = Color(0xFFF97316),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                    
                    if (children.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            children.take(4).forEach { child ->
                                Column(
                                    modifier = Modifier.weight(1f),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(56.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFFFF7ED)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = child.name.firstOrNull()?.uppercase() ?: "?",
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFF97316)
                                        )
                                    }
                                    Text(
                                        text = child.name,
                                        fontSize = 11.sp,
                                        color = Color(0xFF6B7280),
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    } else {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFFFF7ED)
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "👨‍👩‍👧‍👦",
                                    fontSize = 32.sp
                                )
                                Text(
                                    text = "Ajoutez les membres de votre famille",
                                    fontSize = 13.sp,
                                    color = Color(0xFF6B7280),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                                Button(
                                    onClick = { showFamilyDialog = true },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFF97316)
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Add,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text("Ajouter", fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }

                // Paramètres section
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Paramètres",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF111827)
                    )
                    
                    SettingItem(
                        icon = Icons.Filled.Person,
                        title = "Mon compte",
                        subtitle = "Gérer mes informations",
                        onClick = { }
                    )
                    
                    SettingItem(
                        icon = Icons.Filled.Notifications,
                        title = "Notifications",
                        subtitle = "Gérer les alertes",
                        onClick = { }
                    )
                    
                    SettingItem(
                        icon = Icons.Filled.Lock,
                        title = "Confidentialité",
                        subtitle = "Paramètres de confidentialité",
                        onClick = { }
                    )
                    
                    SettingItem(
                        icon = Icons.Filled.Help,
                        title = "Aide & Support",
                        subtitle = "FAQ et contact",
                        onClick = { }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Logout or Login button
                if (isLoggedIn) {
                    Button(
                        onClick = { showLogoutDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFEF2F2)
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Logout,
                            contentDescription = null,
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Déconnexion",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFEF4444)
                        )
                    }
                } else {
                    Button(
                        onClick = { showAuthDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFF97316)
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Login,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Se connecter",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Text(
                    text = "Tribbae v1.0.0 • Fait avec ❤️ pour les familles",
                    fontSize = 11.sp,
                    color = Color(0xFFD1D5DB),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Filled.Logout,
                    contentDescription = null,
                    tint = Color(0xFFF97316)
                )
            },
            title = { Text("Déconnexion", fontWeight = FontWeight.Bold) },
            text = { Text("Voulez-vous vraiment vous déconnecter ?") },
            confirmButton = {
                TextButton(onClick = {
                    sessionManager.clearSession()
                    showLogoutDialog = false
                }) {
                    Text("Déconnexion", color = Color(0xFFF97316), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Annuler")
                }
            }
        )
    }

    if (showAuthDialog) {
        SimpleAuthDialog(
            sessionManager = sessionManager,
            authRepository = authRepository,
            onDismiss = { showAuthDialog = false },
            onSuccess = {
                showAuthDialog = false
                onLoginSuccess()
            }
        )
    }
    
    if (showFamilyDialog) {
        FamilyManagementDialog(
            viewModel = viewModel,
            onDismiss = { showFamilyDialog = false }
        )
    }
}

@Composable
private fun StatCard(
    icon: ImageVector,
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = Color.White.copy(alpha = 0.2f)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = value,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = label,
                fontSize = 10.sp,
                color = Color.White.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
private fun SettingItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = Color.White
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFFFF7ED)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color(0xFFF97316),
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF111827)
                )
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = Color(0xFF9CA3AF)
                )
            }
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = Color(0xFF9CA3AF),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SimpleAuthDialog(
    sessionManager: data.SessionManager,
    authRepository: data.AuthRepository,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    
    AlertDialog(onDismissRequest = { if (!isLoading) onDismiss() }) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color.White
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Connexion",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it; errorMessage = null },
                    label = { Text("Email") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; errorMessage = null },
                    label = { Text("Mot de passe") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
                )
                
                if (errorMessage != null) {
                    Text(
                        text = errorMessage ?: "",
                        color = Color(0xFFEF4444),
                        fontSize = 12.sp
                    )
                }
                
                Button(
                    onClick = {
                        scope.launch {
                            isLoading = true
                            try {
                                val response = authRepository.login(email, password)
                                sessionManager.saveSession(response.userId, response.token, response.displayName)
                                onSuccess()
                            } catch (e: Exception) {
                                errorMessage = "Erreur de connexion"
                            } finally {
                                isLoading = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading && email.isNotBlank() && password.isNotBlank()
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Se connecter")
                    }
                }
                
                TextButton(onClick = { if (!isLoading) onDismiss() }) {
                    Text("Annuler")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FamilyManagementDialog(
    viewModel: LinkViewModel,
    onDismiss: () -> Unit
) {
    val children by viewModel.children.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var editingChild by remember { mutableStateOf<data.Child?>(null) }
    val scope = rememberCoroutineScope()
    
    AlertDialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
               verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Ma famille",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(
                        onClick = { showAddDialog = true },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFF97316))
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "Ajouter",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                
                if (children.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "👨‍👩‍👧‍👦",
                            fontSize = 48.sp
                        )
                        Text(
                            text = "Aucun membre pour l'instant",
                            fontSize = 14.sp,
                            color = Color(0xFF9CA3AF)
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        children.forEach { child ->
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFFF9FAFB)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFFFF7ED)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = child.name.firstOrNull()?.uppercase() ?: "?",
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFF97316)
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = child.name,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFF111827)
                                        )
                                        val age = remember(child.birthDate) {
                                            val now = System.currentTimeMillis()
                                            val diff = now - child.birthDate
                                            val years = diff / (365.25 * 24 * 60 * 60 * 1000)
                                            years.toInt()
                                        }
                                        Text(
                                            text = "$age ans",
                                            fontSize = 12.sp,
                                            color = Color(0xFF9CA3AF)
                                        )
                                    }
                                    IconButton(
                                        onClick = { editingChild = child },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Edit,
                                            contentDescription = "Modifier",
                                            tint = Color(0xFF6B7280),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            scope.launch {
                                                viewModel.deleteChild(child.id)
                                            }
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Delete,
                                            contentDescription = "Supprimer",
                                            tint = Color(0xFFEF4444),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Fermer")
                }
            }
        }
    }
    
    if (showAddDialog) {
        ChildDialog(
            title = "Ajouter un membre",
            onDismiss = { showAddDialog = false },
            onConfirm = { name, birthDate ->
                viewModel.addChild(name, birthDate)
                showAddDialog = false
            }
        )
    }
    
    editingChild?.let { child ->
        ChildDialog(
            title = "Modifier",
            initialName = child.name,
            initialBirthDate = child.birthDate,
            onDismiss = { editingChild = null },
            onConfirm = { name, birthDate ->
                viewModel.updateChild(child.copy(name = name, birthDate = birthDate))
                editingChild = null
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChildDialog(
    title: String,
    initialName: String = "",
    initialBirthDate: Long? = null,
    onDismiss: () -> Unit,
    onConfirm: (String, Long) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var birthDate by remember { mutableStateOf(initialBirthDate ?: System.currentTimeMillis()) }
    
    AlertDialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color.White
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Prénom") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
                
                Text(
                    text = "Date de naissance: ${java.text.SimpleDateFormat("dd/MM/yyyy").format(java.util.Date(birthDate))}",
                    fontSize = 14.sp,
                    color = Color(0xFF6B7280)
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Annuler")
                    }
                    Button(
                        onClick = {
                            if (name.isNotBlank()) {
                                onConfirm(name, birthDate)
                            }
                        },
                        enabled = name.isNotBlank(),
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFF97316)
                        )
                    ) {
                        Text("Confirmer")
                    }
                }
            }
        }
    }
}

