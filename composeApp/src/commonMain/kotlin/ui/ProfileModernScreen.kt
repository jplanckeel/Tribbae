package ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.ChildCare
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linkkeeper.app.R
import data.FollowRepository
import data.UserProfile
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ui.campfire.*
import viewmodel.LinkViewModel

private data class Member(val key: String, val name: String, val sub: String? = null)

/** « Ma tribu » : profil, statistiques, membres, invitations, réglages. Sans compte : écran Bienvenue. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileModernScreen(
    viewModel: LinkViewModel,
    modifier: Modifier = Modifier,
    sessionManager: data.SessionManager,
    authRepository: data.AuthRepository,
    onLoginSuccess: () -> Unit = {},
    onNavigateToFaq: () -> Unit = {},
    onOpenShopping: () -> Unit = {},
    onOpenAdmin: () -> Unit = {},
) {
    val isLoggedIn by sessionManager.isLoggedIn.collectAsState()
    var authMode by remember { mutableStateOf<Boolean?>(null) } // true = inscription

    if (!isLoggedIn) {
        WelcomeScreen(
            modifier = modifier,
            onRegister = { authMode = true },
            onLogin = { authMode = false },
            onFaq = onNavigateToFaq
        )
    } else {
        TribeContent(viewModel, sessionManager, modifier, onNavigateToFaq, onOpenShopping, onOpenAdmin)
    }

    authMode?.let { register ->
        SimpleAuthDialog(
            sessionManager = sessionManager,
            authRepository = authRepository,
            onDismiss = { authMode = null },
            onSuccess = { authMode = null; onLoginSuccess() },
            startInRegister = register
        )
    }
}

@Composable
private fun WelcomeScreen(modifier: Modifier, onRegister: () -> Unit, onLogin: () -> Unit, onFaq: () -> Unit) {
    Column(modifier.fillMaxSize().background(Color(0xFFF7955A))) {
        Box(Modifier.weight(1f).fillMaxWidth().statusBarsPadding().padding(start = 10.dp, end = 10.dp, top = 24.dp), contentAlignment = Alignment.BottomCenter) {
            Image(
                painterResource(R.drawable.welcome),
                contentDescription = "Le tipi Tribbae entouré de la famille, d'un cadeau, d'une ampoule, de couverts et d'un ballon",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                .background(Sable)
                .padding(start = 24.dp, end = 24.dp, top = 30.dp, bottom = ui.components.bottomNavClearance()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Tribbae", style = display(38.sp))
            Text(
                "Toutes les idées de la tribu au même endroit : sorties, recettes, cadeaux, activités.",
                fontSize = 17.sp, lineHeight = 24.sp, color = Ecorce2
            )
            Spacer(Modifier.height(4.dp))
            CampButton("Créer ma tribu", onRegister, Modifier.fillMaxWidth(), height = 56.dp)
            CampButton("J'ai déjà un compte", onLogin, Modifier.fillMaxWidth(), kind = ButtonKind.Secondary, height = 56.dp)
            Text(
                "Invité par un proche ? Ouvrez simplement son lien.",
                fontSize = 13.sp, color = TextSec, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
            )
            Text(
                "Aide & FAQ", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Terracotta, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(onClick = onFaq).padding(4.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TribeContent(
    viewModel: LinkViewModel,
    sessionManager: data.SessionManager,
    modifier: Modifier,
    onFaq: () -> Unit,
    onOpenShopping: () -> Unit,
    onOpenAdmin: () -> Unit,
) {
    val links by viewModel.repository.links.collectAsState()
    val folders by viewModel.folders.collectAsState()
    val children by viewModel.children.collectAsState()
    val displayName by sessionManager.displayName.collectAsState()
    val userId by sessionManager.userId.collectAsState()
    val followRepository = remember { FollowRepository(sessionManager = sessionManager) }
    var following by remember { mutableStateOf<List<UserProfile>?>(null) }
    val scope = rememberCoroutineScope()

    var showFamily by remember { mutableStateOf(false) }
    var showInvite by remember { mutableStateOf(false) }
    var shareFolder by remember { mutableStateOf<data.Folder?>(null) }
    var showFollowing by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showLogout by remember { mutableStateOf(false) }

    LaunchedEffect(userId) {
        userId?.let { id -> followRepository.getFollowing(id).onSuccess { following = it } }
    }

    val name = displayName?.ifBlank { null } ?: "Moi"
    val likesReceived = links.sumOf { it.likeCount } + folders.sumOf { it.likeCount }
    val members = remember(folders, children, name) {
        val seen = mutableSetOf<String>()
        buildList {
            add(Member("me", name))
            folders.flatMap { it.collaborators }.forEach { c ->
                if (seen.add(c.userId)) add(Member(c.userId, c.displayName.ifBlank { c.email.ifBlank { "Membre" } }))
            }
            children.forEach { add(Member("child-${it.id}", it.name, formatAge(it.birthDate))) }
        }
    }

    Column(modifier.fillMaxSize().background(Sable).verticalScroll(rememberScrollState()).padding(bottom = ui.components.bottomNavClearance())) {
        // ── Bandeau orange : soleil + tipi ──
        Box(Modifier.fillMaxWidth().background(Braise)) {
            Box(Modifier.align(Alignment.BottomCenter).offset(y = 170.dp).size(360.dp).clip(CircleShape).background(Soleil))
            Column(Modifier.fillMaxWidth().statusBarsPadding()) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Ma tribu", style = display(26.sp))
                    CircleIconButton(Icons.Outlined.Settings, "Paramètres", { showSettings = true }, background = Color.White.copy(alpha = 0.6f))
                }
                Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.BottomCenter) {
                    TipiMark(110.dp, stroke = 1.8f)
                }
            }
        }

        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("La tribu $name", style = display(24.sp))
                Text("${members.size} membre${if (members.size > 1) "s" else ""}", fontSize = 14.sp, color = TextSec)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat(links.size, if (links.size > 1) "idées" else "idée", Modifier.weight(1f))
                Stat(folders.size, if (folders.size > 1) "dossiers" else "dossier", Modifier.weight(1f))
                Stat(likesReceived, "j'aime reçus", Modifier.weight(1f))
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionHeader("Les membres", "Gérer") { showFamily = true }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    members.forEachIndexed { i, m ->
                        Column(Modifier.width(70.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            InitialAvatar(m.name, i)
                            Text(
                                m.name + (m.sub?.let { " · $it" } ?: ""),
                                fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Ecorce, textAlign = TextAlign.Center,
                                maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 16.sp
                            )
                        }
                    }
                    Column(
                        Modifier.width(70.dp).clip(RoundedCornerShape(16.dp)).clickable { showFamily = true },
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(Modifier.size(58.dp).clip(RoundedCornerShape(20.dp)).dashedBorder(Dash, 20.dp), contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.ChildCare, null, tint = Terracotta, modifier = Modifier.size(24.dp))
                        }
                        Text("Enfant", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextSec)
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Ecorce).clickable { showInvite = true }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Braise), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.PersonAdd, null, tint = Ecorce, modifier = Modifier.size(22.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text("Inviter dans la tribu", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Sable)
                    Text("Partagez dossiers et idées en un lien", fontSize = 13.sp, color = BarkSoft)
                }
                Icon(Icons.Outlined.ChevronRight, null, tint = BarkSoft)
            }

            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White)) {
                ListRow(Icons.Outlined.FavoriteBorder, "Tribus suivies", following?.size?.toString()) { showFollowing = true }
                ListRow(Icons.Outlined.ShoppingCart, "Liste de courses", onClick = onOpenShopping)
                ListRow(Icons.Outlined.HelpOutline, "Aide & FAQ", onClick = onFaq)
                if (sessionManager.isAdmin()) ListRow(Icons.Outlined.AdminPanelSettings, "Administration", onClick = onOpenAdmin)
                ListRow(Icons.Outlined.Logout, "Se déconnecter", last = true) { showLogout = true }
            }
        }
    }

    if (showFamily) FamilyManagementDialog(viewModel = viewModel, onDismiss = { showFamily = false })

    if (showInvite) {
        ModalBottomSheet(onDismissRequest = { showInvite = false }, containerColor = Sable, dragHandle = { SheetHandle() }) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Inviter dans la tribu", style = display(24.sp))
                Text(
                    "Choisissez le dossier à partager : vos proches pourront le consulter par lien ou y ajouter leurs idées.",
                    fontSize = 15.sp, color = Ecorce2, modifier = Modifier.padding(bottom = 8.dp)
                )
                if (folders.isEmpty()) {
                    EmptyBlock(Icons.Filled.CreateNewFolder, "Aucun dossier à partager", "Créez d'abord un dossier dans « Mes idées ».")
                }
                folders.forEach { f ->
                    MenuRow(folderIconVector(f), f.name, tint = folderSolid(f.color)) { showInvite = false; shareFolder = f }
                }
            }
        }
    }
    shareFolder?.let { ShareFolderSheet(it, viewModel) { shareFolder = null } }

    if (showFollowing) {
        ModalBottomSheet(onDismissRequest = { showFollowing = false }, containerColor = Sable, dragHandle = { SheetHandle() }) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Tribus suivies", style = display(24.sp))
                val list = following.orEmpty()
                if (list.isEmpty()) {
                    EmptyBlock(Icons.Filled.FavoriteBorder, "Vous ne suivez personne", "Depuis la fiche d'une idée de la communauté, suivez la tribu qui l'a partagée.")
                } else {
                    list.forEachIndexed { i, u ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White).padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            InitialAvatar(u.displayName, i, 40.dp, 12.dp, 16.sp)
                            Column(Modifier.weight(1f)) {
                                Text(u.displayName.ifBlank { "Tribu" }, fontWeight = FontWeight.Bold, color = Ecorce)
                                Text("${u.followerCount} abonné${if (u.followerCount > 1) "s" else ""}", fontSize = 12.sp, color = TextSec)
                            }
                            CampButton("Ne plus suivre", {
                                scope.launch {
                                    followRepository.unfollow(u.id).onSuccess { following = following?.filterNot { it.id == u.id } }
                                }
                            }, kind = ButtonKind.Ghost, height = 40.dp)
                        }
                    }
                }
            }
        }
    }

    if (showSettings) {
        ModalBottomSheet(onDismissRequest = { showSettings = false }, containerColor = Sable, dragHandle = { SheetHandle() }) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Paramètres", style = display(24.sp))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    InitialAvatar(name, 0, 48.dp, 16.dp, 20.sp)
                    Column {
                        Text(name, fontWeight = FontWeight.Bold, color = Ecorce)
                        Text("Synchronisé avec tribbae.bananaops.cloud", fontSize = 12.sp, color = TextSec)
                    }
                }
                Text(
                    "Pour modifier votre mot de passe ou supprimer votre compte, écrivez à support@tribbae.com.",
                    fontSize = 14.sp, color = TextSec
                )
                MenuRow(Icons.Outlined.HelpOutline, "Aide & FAQ") { showSettings = false; onFaq() }
                MenuRow(Icons.Outlined.Logout, "Se déconnecter", danger = true) { showSettings = false; showLogout = true }
            }
        }
    }

    if (showLogout) {
        AlertDialog(
            onDismissRequest = { showLogout = false },
            containerColor = Sable,
            title = { Text("Se déconnecter ?", style = display(20.sp, FontWeight.Bold)) },
            text = { Text("Vos idées restent sur cet appareil et sur votre compte.", color = Ecorce2) },
            confirmButton = {
                TextButton(onClick = { sessionManager.clearSession(); showLogout = false }) {
                    Text("Se déconnecter", color = Terracotta, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { showLogout = false }) { Text("Annuler", color = Ecorce) } }
        )
    }
}

@Composable
private fun Stat(value: Int, label: String, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(Color.White).padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("$value", style = display(22.sp))
        Text(label, fontSize = 12.sp, color = TextSec)
    }
}

@Composable
private fun ListRow(icon: ImageVector, label: String, value: String? = null, last: Boolean = false, onClick: () -> Unit) {
    Column {
        Row(
            Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(icon, null, tint = Ecorce, modifier = Modifier.size(20.dp))
            Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ecorce, modifier = Modifier.weight(1f))
            if (value != null) Text(value, fontSize = 13.sp, color = TextSec)
            Icon(Icons.Outlined.ChevronRight, null, tint = TextMuted, modifier = Modifier.size(18.dp))
        }
        if (!last) HorizontalDivider(color = Sable2)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SimpleAuthDialog(
    sessionManager: data.SessionManager,
    authRepository: data.AuthRepository,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit,
    startInRegister: Boolean = false
) {
    var isLoginMode by remember { mutableStateOf(!startInRegister) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    AlertDialog(onDismissRequest = { if (!isLoading) onDismiss() }) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color.White
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = if (isLoginMode) "Connexion" else "Inscription",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF3A211B)
                )

                // Tabs connexion / inscription
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(true to "Connexion", false to "Inscription").forEach { (mode, label) ->
                        Button(
                            onClick = { isLoginMode = mode; errorMessage = null },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isLoginMode == mode) Color(0xFFC8492C) else Color(0xFFF1E3D1),
                                contentColor = if (isLoginMode == mode) Color.White else Color(0xFF6B5047)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(vertical = 10.dp)
                        ) {
                            Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                // Pseudo (inscription uniquement)
                if (!isLoginMode) {
                    OutlinedTextField(
                        value = displayName,
                        onValueChange = { displayName = it; errorMessage = null },
                        label = { Text("Pseudo") },
                        leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFC8492C),
                            focusedLabelColor = Color(0xFFC8492C)
                        )
                    )
                }

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it; errorMessage = null },
                    label = { Text("Email") },
                    leadingIcon = { Icon(Icons.Filled.Email, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Email
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFC8492C),
                        focusedLabelColor = Color(0xFFC8492C)
                    )
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; errorMessage = null },
                    label = { Text("Mot de passe") },
                    leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                contentDescription = null
                            )
                        }
                    },
                    visualTransformation = if (passwordVisible)
                        androidx.compose.ui.text.input.VisualTransformation.None
                    else
                        androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFC8492C),
                        focusedLabelColor = Color(0xFFC8492C)
                    )
                )

                if (errorMessage != null) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFFF8DCD3),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = errorMessage ?: "",
                            color = Color(0xFFB2412A),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                Button(
                    onClick = {
                        scope.launch {
                            isLoading = true
                            errorMessage = null
                            try {
                                val response = if (isLoginMode) {
                                    authRepository.login(email, password)
                                } else {
                                    authRepository.register(email, password, displayName)
                                }
                                sessionManager.saveSession(response.userId, response.token, response.displayName, response.isAdmin)
                                onSuccess()
                            } catch (e: Exception) {
                                println("ERROR: Auth failed - ${e.message}")
                                errorMessage = when {
                                    e.message?.contains("401") == true || e.message?.contains("invalid credentials") == true ->
                                        "Email ou mot de passe incorrect"
                                    e.message?.contains("409") == true || e.message?.contains("already exists") == true ->
                                        "Cet email est déjà utilisé"
                                    e.message?.contains("timeout") == true || e.message?.contains("Connection") == true ->
                                        "Impossible de se connecter au serveur"
                                    else -> "Erreur: ${e.message}"
                                }
                            } finally {
                                isLoading = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading && email.isNotBlank() && password.isNotBlank() &&
                            (isLoginMode || displayName.isNotBlank()),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC8492C)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text(
                            text = if (isLoginMode) "Se connecter" else "S'inscrire",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                TextButton(onClick = { if (!isLoading) onDismiss() }) {
                    Text("Annuler", color = Color(0xFF6B5047))
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
                            .background(Color(0xFFC8492C))
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
                            color = Color(0xFF8A6F64)
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        children.forEach { child ->
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFFFBF3E7)
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
                                            .background(Color(0xFFFDE6BD)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = child.name.firstOrNull()?.uppercase() ?: "?",
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFC8492C)
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = child.name,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFF3A211B)
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
                                            color = Color(0xFF8A6F64)
                                        )
                                    }
                                    IconButton(
                                        onClick = { editingChild = child },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Edit,
                                            contentDescription = "Modifier",
                                            tint = Color(0xFF6B5047),
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
                                            tint = Color(0xFFB2412A),
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
                    color = Color(0xFF6B5047)
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
                            containerColor = Color(0xFFC8492C)
                        )
                    ) {
                        Text("Confirmer")
                    }
                }
            }
        }
    }
}
