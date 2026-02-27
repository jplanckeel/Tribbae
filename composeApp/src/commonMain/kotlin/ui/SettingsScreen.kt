package ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import viewmodel.LinkViewModel

// Couleurs pour les avatars enfants
private val childColors = listOf(
    Color(0xFF4FC3F7), Color(0xFFFF8C00), Color(0xFF81C784),
    Color(0xFFBA68C8), Color(0xFFFF7043), Color(0xFFFFD700)
)

@Composable
fun SettingsScreen(
    viewModel: LinkViewModel, 
    modifier: Modifier = Modifier, 
    onShoppingClick: () -> Unit = {},
    onCalendarClick: () -> Unit = {},
    sessionManager: data.SessionManager,
    authRepository: data.AuthRepository,
    onLoginSuccess: () -> Unit = {}
) {
    val children by viewModel.children.collectAsState()
    val isLoggedIn by sessionManager.isLoggedIn.collectAsState()
    val displayName by sessionManager.displayName.collectAsState()
    val syncStatus by viewModel.syncStatus.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var editingChild by remember { mutableStateOf<data.Child?>(null) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showAuthDialog by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize().padding(20.dp)) {
        // Header avec info utilisateur ou bouton de connexion
        if (isLoggedIn) {
            // Utilisateur connecté
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = CardColor),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(Orange),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            (displayName ?: "U").take(1).uppercase(),
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp,
                            color = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(displayName ?: "Utilisateur", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        if (syncStatus != null) {
                            Text(syncStatus!!, fontSize = 12.sp, color = TextSecondary)
                        } else {
                            Text("Compte synchronisé", fontSize = 12.sp, color = Color(0xFF4CAF50))
                        }
                    }
                    IconButton(onClick = { showLogoutDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Logout,
                            contentDescription = "Déconnexion",
                            tint = Color.LightGray
                        )
                    }
                }
            }
        } else {
            // Utilisateur non connecté - Invitation à se connecter
            Card(
                onClick = { showAuthDialog = true },
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Orange.copy(alpha = 0.1f)),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(Orange.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = Orange,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Mode hors ligne", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("Connectez-vous pour synchroniser vos données", fontSize = 12.sp, color = TextSecondary)
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = Orange
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(20.dp))
        
        Text("Tribbae", fontWeight = FontWeight.Bold, fontSize = 24.sp, color = TextPrimary)
        Text("Organisation familiale", color = TextSecondary, fontSize = 14.sp)

        Spacer(modifier = Modifier.height(20.dp))

        // Section Enfants
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = Icons.Default.ChildCare, contentDescription = null, tint = Orange, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Text("Mes enfants", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextPrimary)
            Spacer(modifier = Modifier.weight(1f))
            FilledIconButton(
                onClick = { showAddDialog = true },
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Orange)
            ) {
                Icon(imageVector = Icons.Default.PersonAdd, contentDescription = "Ajouter", tint = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "Sélectionnez un enfant sur l'accueil pour filtrer par âge",
            fontSize = 12.sp, color = TextSecondary
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (children.isEmpty()) {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = CardColor)
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.FamilyRestroom, contentDescription = null,
                            modifier = Modifier.size(56.dp), tint = OrangeLight.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("Ajoutez vos enfants", color = TextSecondary)
                        Text("pour filtrer les idées par âge", fontSize = 12.sp, color = TextSecondary)
                    }
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(children) { child ->
                    val colorIndex = children.indexOf(child) % childColors.size
                    val avatarColor = childColors[colorIndex]
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = CardColor),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(avatarColor),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    child.name.take(1).uppercase(),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp,
                                    color = Color.White
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(child.name, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                                Text(
                                    "${formatChildAge(child.birthDate)} · né le ${formatDate(child.birthDate)}",
                                    fontSize = 13.sp, color = TextSecondary
                                )
                            }
                            IconButton(onClick = { editingChild = child }) {
                                Icon(imageVector = Icons.Default.Edit, contentDescription = "Modifier",
                                    tint = Orange, modifier = Modifier.size(20.dp))
                            }
                            IconButton(onClick = { viewModel.deleteChild(child.id) }) {
                                Icon(imageVector = Icons.Default.Delete, contentDescription = "Supprimer",
                                    tint = Color.LightGray, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Bouton Courses
        Card(
            onClick = onShoppingClick,
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = CardColor),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(imageVector = Icons.Default.ShoppingCart, contentDescription = null, tint = Orange, modifier = Modifier.size(28.dp))
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Courses", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Text("Ma liste de courses", fontSize = 12.sp, color = TextSecondary)
                }
                Icon(imageVector = Icons.Default.ChevronRight, contentDescription = null, tint = Color.LightGray)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Bouton Agenda
        Card(
            onClick = onCalendarClick,
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = CardColor),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(imageVector = Icons.Default.CalendarMonth, contentDescription = null, tint = Color(0xFF4FC3F7), modifier = Modifier.size(28.dp))
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Agenda", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Text("Mes événements à venir", fontSize = 12.sp, color = TextSecondary)
                }
                Icon(imageVector = Icons.Default.ChevronRight, contentDescription = null, tint = Color.LightGray)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = CardColor)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Info, contentDescription = null, tint = Orange)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Version 1.0", fontWeight = FontWeight.Medium)
                }
            }
        }
    }

    if (showAddDialog) {
        ChildDialog(
            title = "Ajouter un enfant",
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
    
    // Dialog de confirmation de déconnexion
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Logout,
                    contentDescription = null,
                    tint = Orange
                )
            },
            title = { Text("Déconnexion", fontWeight = FontWeight.Bold) },
            text = { Text("Voulez-vous vraiment vous déconnecter ? Vos données locales seront conservées.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        sessionManager.clearSession()
                        showLogoutDialog = false
                    }
                ) {
                    Text("Déconnexion", color = Orange, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Annuler")
                }
            }
        )
    }
    
    // Dialog d'authentification (connexion/inscription)
    if (showAuthDialog) {
        AuthDialog(
            sessionManager = sessionManager,
            authRepository = authRepository,
            onDismiss = { showAuthDialog = false },
            onSuccess = {
                showAuthDialog = false
                onLoginSuccess()
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthDialog(
    sessionManager: data.SessionManager,
    authRepository: data.AuthRepository,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    var isLoginMode by remember { mutableStateOf(true) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    val scope = rememberCoroutineScope()
    
    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        modifier = Modifier.fillMaxWidth()
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = SurfaceColor,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Logo
                Icon(
                    imageVector = Icons.Default.Lightbulb,
                    contentDescription = "Tribbae",
                    modifier = Modifier.size(64.dp),
                    tint = Orange
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "Tribbae",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                
                Text(
                    text = "Synchronisez vos idées",
                    fontSize = 14.sp,
                    color = TextSecondary
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Tabs
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { isLoginMode = true; errorMessage = null },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isLoginMode) Orange else CardColor,
                            contentColor = if (isLoginMode) Color.White else TextSecondary
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Connexion", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    }
                    
                    Button(
                        onClick = { isLoginMode = false; errorMessage = null },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (!isLoginMode) Orange else CardColor,
                            contentColor = if (!isLoginMode) Color.White else TextSecondary
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Inscription", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    }
                }
                
                Spacer(modifier = Modifier.height(20.dp))
                
                // Form
                if (!isLoginMode) {
                    OutlinedTextField(
                        value = displayName,
                        onValueChange = { displayName = it; errorMessage = null },
                        label = { Text("Nom d'affichage", fontSize = 14.sp) },
                        leadingIcon = {
                            Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(20.dp))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Orange,
                            focusedLabelColor = Orange,
                            focusedLeadingIconColor = Orange
                        )
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                }
                
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it; errorMessage = null },
                    label = { Text("Email", fontSize = 14.sp) },
                    leadingIcon = {
                        Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(20.dp))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Orange,
                        focusedLabelColor = Orange,
                        focusedLeadingIconColor = Orange
                    )
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; errorMessage = null },
                    label = { Text("Mot de passe", fontSize = 14.sp) },
                    leadingIcon = {
                        Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(20.dp))
                    },
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Default.Visibility
                                else Icons.Default.VisibilityOff,
                                contentDescription = if (passwordVisible) "Masquer" else "Afficher",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    },
                    visualTransformation = if (passwordVisible) VisualTransformation.None
                    else PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Orange,
                        focusedLabelColor = Orange,
                        focusedLeadingIconColor = Orange
                    )
                )
                
                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Error,
                                contentDescription = null,
                                tint = Color(0xFFD32F2F),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = errorMessage ?: "",
                                color = Color(0xFFD32F2F),
                                fontSize = 13.sp
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(20.dp))
                
                Button(
                    onClick = {
                        scope.launch {
                            handleAuth(
                                isLoginMode = isLoginMode,
                                email = email,
                                password = password,
                                displayName = displayName,
                                authRepository = authRepository,
                                sessionManager = sessionManager,
                                onLoading = { isLoading = it },
                                onError = { errorMessage = it },
                                onSuccess = onSuccess
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    enabled = !isLoading && email.isNotBlank() && password.isNotBlank() &&
                            (isLoginMode || displayName.isNotBlank()),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Orange,
                        disabledContainerColor = Orange.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = if (isLoginMode) "Se connecter" else "S'inscrire",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                TextButton(
                    onClick = { if (!isLoading) onDismiss() }
                ) {
                    Text(
                        text = "Continuer hors ligne",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

private suspend fun handleAuth(
    isLoginMode: Boolean,
    email: String,
    password: String,
    displayName: String,
    authRepository: data.AuthRepository,
    sessionManager: data.SessionManager,
    onLoading: (Boolean) -> Unit,
    onError: (String) -> Unit,
    onSuccess: () -> Unit
) {
    onLoading(true)
    try {
        val response = if (isLoginMode) {
            authRepository.login(email, password)
        } else {
            authRepository.register(email, password, displayName)
        }
        
        sessionManager.saveSession(response.userId, response.token, response.displayName)
        onSuccess()
    } catch (e: Exception) {
        onError(
            when {
                e.message?.contains("401") == true || e.message?.contains("Invalid") == true ->
                    "Email ou mot de passe incorrect"
                e.message?.contains("409") == true || e.message?.contains("already exists") == true ->
                    "Cet email est déjà utilisé"
                e.message?.contains("Connection") == true ->
                    "Impossible de se connecter au serveur"
                else -> "Une erreur est survenue: ${e.message}"
            }
        )
    } finally {
        onLoading(false)
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
    var showDatePicker by remember { mutableStateOf(false) }
    var birthDate by remember { mutableStateOf(initialBirthDate) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        icon = { Icon(imageVector = Icons.Default.ChildCare, contentDescription = null, tint = Orange) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Prénom") },
                    leadingIcon = { Icon(imageVector = Icons.Default.Person, contentDescription = null) },
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Orange, focusedContainerColor = CardColor, unfocusedContainerColor = CardColor
                    )
                )
                OutlinedTextField(
                    value = if (birthDate != null) formatDate(birthDate!!) else "",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Date de naissance") },
                    leadingIcon = { Icon(imageVector = Icons.Default.Cake, contentDescription = null, tint = Orange) },
                    trailingIcon = {
                        IconButton(onClick = { showDatePicker = true }) {
                            Icon(imageVector = Icons.Default.EditCalendar, contentDescription = "Choisir")
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Orange, focusedContainerColor = CardColor, unfocusedContainerColor = CardColor
                    )
                )
                if (birthDate != null) {
                    val ageLabel = formatChildAge(birthDate!!)
                    Surface(shape = RoundedCornerShape(10.dp), color = BlueSky.copy(alpha = 0.12f)) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(imageVector = Icons.Default.ChildCare, contentDescription = null,
                                tint = BlueSky, modifier = Modifier.size(16.dp))
                            Text(ageLabel, color = BlueSky, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val bd = birthDate
                    if (name.isNotBlank() && bd != null) onConfirm(name.trim(), bd)
                }
            ) { Text("OK", color = Orange, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        }
    )

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = birthDate,
            yearRange = 2000..currentYear()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    birthDate = datePickerState.selectedDateMillis
                    showDatePicker = false
                }) { Text("OK", color = Orange) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Annuler") }
            }
        ) {
            DatePicker(
                state = datePickerState,
                colors = DatePickerDefaults.colors(
                    selectedDayContainerColor = Orange,
                    todayDateBorderColor = Orange
                )
            )
        }
    }
}
