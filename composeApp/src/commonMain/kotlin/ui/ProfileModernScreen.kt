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
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Lock
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
    var showMembers by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var showInvite by remember { mutableStateOf(false) }
    val tribe by viewModel.tribe.collectAsState()
    var tribeError by remember { mutableStateOf<String?>(null) }
    var tribeBusy by remember { mutableStateOf(false) }
    var shareFolder by remember { mutableStateOf<data.Folder?>(null) }
    var showFollowing by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showLogout by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var dataMessage by remember { mutableStateOf<String?>(null) }
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    val context = androidx.compose.ui.platform.LocalContext.current
    // Export RGPD : l'utilisateur choisit où enregistrer le fichier JSON
    val exportLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val data = viewModel.exportAccount()
            dataMessage = if (data == null) "Impossible de récupérer vos données, réessayez."
            else runCatching {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(data.toByteArray()) }
                }
                "Vos données sont enregistrées."
            }.getOrElse { "Impossible d'enregistrer le fichier." }
        }
    }

    LaunchedEffect(userId) {
        userId?.let { id -> followRepository.getFollowing(id).onSuccess { following = it } }
    }

    val name = displayName?.ifBlank { null } ?: "Moi"
    // Tribu du compte connecté (sinon : tribu d'un seul membre, nommée d'après lui)
    val accounts = tribe?.members?.takeIf { m -> m.any { it.userId == userId } }
        ?: listOf(data.ApiTribeMember(userId = userId.orEmpty(), displayName = name, role = "TRIBE_ROLE_OWNER", sharing = true))
    val me = accounts.find { it.userId == userId }
    val tribeName = tribe?.takeIf { t -> t.members.any { it.userId == userId } }?.name?.trim()?.ifEmpty { null } ?: name
    val myLinks = links.count { it.ownerId.isBlank() || it.ownerId == userId }
    val myFolders = folders.size
    val likesReceived = links.sumOf { it.likeCount } + folders.sumOf { it.likeCount }
    val members = remember(accounts, children, name) {
        buildList {
            accounts.sortedBy { if (it.userId == userId) 0 else 1 }.forEach { m ->
                val label = if (m.userId == userId) "${m.displayName.ifBlank { name }} (vous)" else m.displayName.ifBlank { m.email.ifBlank { "Membre" } }
                add(Member(m.userId, label, if (m.sharing) null else "en attente"))
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
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("La tribu $tribeName", style = display(24.sp))
                    Icon(
                        Icons.Outlined.Edit, "Renommer la tribu", tint = TextSec,
                        modifier = Modifier.size(32.dp).clip(CircleShape).clickable { showRename = true }.padding(7.dp)
                    )
                }
                Text("${members.size} membre${if (members.size > 1) "s" else ""}", fontSize = 14.sp, color = TextSec)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat(links.size, if (links.size > 1) "idées" else "idée", Modifier.weight(1f))
                Stat(folders.size, if (folders.size > 1) "dossiers" else "dossier", Modifier.weight(1f))
                Stat(likesReceived, "j'aime reçus", Modifier.weight(1f))
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionHeader("Les membres", "Gérer") { showMembers = true }
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

            // Nouveau membre : il voit la tribu et choisit d'y verser son propre contenu
            if (me != null && !me.sharing && accounts.size > 1) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Soleil).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Bienvenue dans la tribu $tribeName !", style = display(18.sp, FontWeight.Bold))
                    val what = listOfNotNull(
                        myLinks.takeIf { it > 0 }?.let { "$it idée${if (it > 1) "s" else ""}" },
                        children.size.takeIf { it > 0 }?.let { "$it enfant${if (it > 1) "s" else ""}" },
                    ).joinToString(", ")
                    Text(
                        "Vous voyez déjà tout ce que la tribu partage. Partagez aussi votre contenu" +
                            (if (what.isNotEmpty()) " ($what)" else "") +
                            " : les autres membres pourront le voir et le modifier. Vos idées restent signées de votre nom.",
                        fontSize = 14.sp, color = Ecorce2
                    )
                    CampButton("Partager avec la tribu", {
                        tribeBusy = true
                        viewModel.setTribeSharing(true) { err -> tribeBusy = false; tribeError = err }
                    }, icon = Icons.Outlined.Check, height = 44.dp)
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
                    Text("Partagez toute la tribu avec un proche, par email", fontSize = 13.sp, color = BarkSoft)
                }
                Icon(Icons.Outlined.ChevronRight, null, tint = BarkSoft)
            }

            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White)) {
                ListRow(Icons.Outlined.FavoriteBorder, "Tribus suivies", following?.size?.toString()) { showFollowing = true }
                ListRow(Icons.Outlined.ShoppingCart, "Liste de courses", onClick = onOpenShopping)
                ListRow(Icons.Outlined.HelpOutline, "Aide & FAQ", onClick = onFaq)
                ListRow(Icons.Outlined.Lock, "Confidentialité & mes données") { uriHandler.openUri(PRIVACY_URL) }
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
                var email by remember { mutableStateOf("") }
                Text(
                    "Votre proche rejoint la tribu et gère tout avec vous : idées, dossiers, enfants et liste de courses.",
                    fontSize = 15.sp, color = Ecorce2
                )
                CampField(
                    "Email de son compte Tribbae", email, { email = it; tribeError = null },
                    placeholder = "camille@exemple.fr", icon = Icons.Outlined.Email,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Email)
                )
                Text("Ses propres idées ne rejoindront la tribu qu'une fois qu'il l'aura confirmé depuis « Ma tribu ».", fontSize = 12.sp, color = TextSec)
                tribeError?.let { Text(it, color = Color(0xFF9A3420), fontSize = 14.sp, fontWeight = FontWeight.Medium) }
                CampButton(if (tribeBusy) "Ajout…" else "Ajouter à la tribu", {
                    if (email.isBlank()) { tribeError = "Saisissez l'email de son compte Tribbae."; return@CampButton }
                    tribeBusy = true
                    viewModel.addTribeMember(email) { err ->
                        tribeBusy = false
                        tribeError = err
                        if (err == null) { email = ""; showInvite = false }
                    }
                }, Modifier.fillMaxWidth(), icon = Icons.Outlined.PersonAdd)
                Text("Ou partager un seul dossier", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextSec, modifier = Modifier.padding(top = 16.dp))
                Text(
                    "Vos proches pourront le consulter par lien ou y ajouter leurs idées, sans accéder au reste de la tribu.",
                    fontSize = 14.sp, color = TextSec, modifier = Modifier.padding(bottom = 4.dp)
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
                        Text("Synchronisé avec tribbae.fr", fontSize = 12.sp, color = TextSec)
                    }
                }
                MenuRow(Icons.Outlined.Edit, "Nom de la tribu : $tribeName") { showSettings = false; showRename = true }
                Text("Mes données", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextSec)
                MenuRow(Icons.Outlined.Download, "Télécharger mes données") {
                    exportLauncher.launch("tribbae-mes-donnees-${java.time.LocalDate.now()}.json")
                }
                MenuRow(Icons.Outlined.Lock, "Politique de confidentialité") { uriHandler.openUri(PRIVACY_URL) }
                MenuRow(Icons.Outlined.DeleteForever, "Supprimer mon compte", danger = true) { showSettings = false; showDelete = true }
                dataMessage?.let { Text(it, fontSize = 13.sp, color = TextSec) }
                Text(
                    "Pour modifier votre mot de passe, écrivez à support@tribbae.com.",
                    fontSize = 14.sp, color = TextSec
                )
                MenuRow(Icons.Outlined.HelpOutline, "Aide & FAQ") { showSettings = false; onFaq() }
                MenuRow(Icons.Outlined.Logout, "Se déconnecter", danger = true) { showSettings = false; showLogout = true }
            }
        }
    }

    if (showRename) {
        var newName by remember { mutableStateOf(tribeName) }
        AlertDialog(
            onDismissRequest = { showRename = false },
            containerColor = Sable,
            title = { Text("Nom de la tribu", style = display(20.sp, FontWeight.Bold)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CampField("La tribu…", newName, { newName = it; tribeError = null }, placeholder = "Dupont", maxLength = 60)
                    Text("Affiché dans l'accueil et pour tous les membres. Vos idées restent signées de votre nom.", fontSize = 13.sp, color = TextSec)
                    tribeError?.let { Text(it, color = Color(0xFF9A3420), fontSize = 13.sp) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newName.isBlank()) { tribeError = "Donnez un nom à votre tribu."; return@TextButton }
                    viewModel.renameTribe(newName) { err -> tribeError = err; if (err == null) showRename = false }
                }) { Text("Enregistrer", color = Terracotta, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showRename = false; tribeError = null }) { Text("Annuler", color = Ecorce) } }
        )
    }

    if (showMembers) {
        var confirmRemove by remember { mutableStateOf<data.ApiTribeMember?>(null) }
        var confirmStopSharing by remember { mutableStateOf(false) }
        val iAmOwner = me?.isOwner == true
        ModalBottomSheet(onDismissRequest = { showMembers = false }, containerColor = Sable, dragHandle = { SheetHandle() }) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Les membres", style = display(24.sp))
                accounts.forEachIndexed { i, m ->
                    val self = m.userId == userId
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White).padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val label = m.displayName.ifBlank { m.email.ifBlank { "Membre" } }
                        InitialAvatar(label, i, 40.dp, 12.dp, 16.sp)
                        Column(Modifier.weight(1f)) {
                            Text(label + if (self) " (vous)" else "", fontWeight = FontWeight.Bold, color = Ecorce, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                listOfNotNull(
                                    if (m.isOwner) "Créateur" else "Membre",
                                    if (!m.sharing) "partage de son contenu en attente" else null,
                                    m.email.takeIf { !self && it.isNotBlank() },
                                ).joinToString(" · "),
                                fontSize = 12.sp, color = TextSec, maxLines = 2
                            )
                        }
                        if (accounts.size > 1 && (self || iAmOwner)) {
                            CampButton(if (self) "Quitter" else "Retirer", { confirmRemove = m }, kind = ButtonKind.Ghost, height = 38.dp)
                        }
                    }
                }
                tribeError?.let { Text(it, color = Color(0xFF9A3420), fontSize = 14.sp) }
                CampButton("Inviter un proche", { showMembers = false; showInvite = true }, Modifier.fillMaxWidth(), kind = ButtonKind.Secondary, icon = Icons.Outlined.PersonAdd)
                CampButton("Les enfants", { showMembers = false; showFamily = true }, Modifier.fillMaxWidth(), kind = ButtonKind.Ghost, icon = Icons.Outlined.ChildCare)
                if (me?.sharing == true && accounts.size > 1) {
                    Text(
                        "Ne plus partager mon contenu", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextSec,
                        modifier = Modifier.align(Alignment.CenterHorizontally).clip(RoundedCornerShape(10.dp)).clickable { confirmStopSharing = true }.padding(8.dp)
                    )
                }
            }
        }
        confirmRemove?.let { m ->
            val self = m.userId == userId
            AlertDialog(
                onDismissRequest = { confirmRemove = null },
                containerColor = Sable,
                title = { Text(if (self) "Quitter la tribu ?" else "Retirer ${m.displayName.ifBlank { m.email }} ?", style = display(20.sp, FontWeight.Bold)) },
                text = {
                    Text(
                        if (self) "Vous ne verrez plus le contenu de la tribu, et le vôtre ne sera plus partagé avec elle."
                        else "Cette personne ne verra plus le contenu de la tribu, et le sien ne sera plus partagé.",
                        color = Ecorce2
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        confirmRemove = null
                        viewModel.removeTribeMember(m.userId) { err -> tribeError = err; if (err == null && self) showMembers = false }
                    }) { Text(if (self) "Quitter" else "Retirer", color = Terracotta, fontWeight = FontWeight.Bold) }
                },
                dismissButton = { TextButton(onClick = { confirmRemove = null }) { Text("Annuler", color = Ecorce) } }
            )
        }
        if (confirmStopSharing) {
            AlertDialog(
                onDismissRequest = { confirmStopSharing = false },
                containerColor = Sable,
                title = { Text("Ne plus partager votre contenu ?", style = display(20.sp, FontWeight.Bold)) },
                text = { Text("Vos idées, dossiers et enfants ne seront plus visibles des autres membres. Vous continuerez à voir ceux de la tribu.", color = Ecorce2) },
                confirmButton = {
                    TextButton(onClick = {
                        confirmStopSharing = false
                        viewModel.setTribeSharing(false) { err -> tribeError = err }
                    }) { Text("Ne plus partager", color = Terracotta, fontWeight = FontWeight.Bold) }
                },
                dismissButton = { TextButton(onClick = { confirmStopSharing = false }) { Text("Annuler", color = Ecorce) } }
            )
        }
    }

    if (showLogout) {
        AlertDialog(
            onDismissRequest = { showLogout = false },
            containerColor = Sable,
            title = { Text("Se déconnecter ?", style = display(20.sp, FontWeight.Bold)) },
            text = { Text("Vos idées restent sur votre compte. Elles sont retirées de cet appareil jusqu'à votre prochaine connexion.", color = Ecorce2) },
            confirmButton = {
                TextButton(onClick = { viewModel.signOut(); showLogout = false }) {
                    Text("Se déconnecter", color = Terracotta, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { showLogout = false }) { Text("Annuler", color = Ecorce) } }
        )
    }

    if (showDelete) DeleteAccountDialog(viewModel, onDismiss = { showDelete = false })
}

/** Politique de confidentialité, publiée sur le site. */
private const val PRIVACY_URL = "${data.Backend.PRODUCTION}/confidentialite"

/**
 * Suppression définitive du compte (RGPD, droit à l'effacement) : ce qui
 * disparaît, ce qui reste aux autres, puis confirmation par mot de passe.
 */
@Composable
private fun DeleteAccountDialog(viewModel: LinkViewModel, onDismiss: () -> Unit) {
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = Sable,
        title = { Text("Supprimer mon compte ?", style = display(20.sp, FontWeight.Bold)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Seront définitivement effacés : votre compte, vos idées et dossiers (y compris ceux partagés avec la communauté), " +
                        "les profils de vos enfants, vos commentaires, « j'aime » et abonnements.",
                    color = Ecorce2, fontSize = 14.sp
                )
                Text(
                    "Les idées que vos proches ont ajoutées dans vos dossiers leur restent. Si vous avez créé la tribu, elle revient au membre le plus ancien. " +
                        "Pensez à télécharger vos données avant : c'est irréversible.",
                    color = TextSec, fontSize = 13.sp
                )
                OutlinedTextField(
                    value = password, onValueChange = { password = it; error = null },
                    label = { Text("Mot de passe") }, singleLine = true,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let { Text(it, color = Color(0xFF9A3420), fontSize = 13.sp, fontWeight = FontWeight.Medium) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = password.isNotEmpty() && !busy,
                onClick = {
                    busy = true
                    viewModel.deleteAccount(password) { err ->
                        busy = false
                        if (err == null) onDismiss() else error = err
                    }
                }
            ) { Text(if (busy) "Suppression…" else "Supprimer définitivement", color = Color(0xFF9A3420), fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Annuler", color = Ecorce) } }
    )
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
    var showForgot by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    if (showForgot) {
        ForgotPasswordDialog(authRepository = authRepository, initialEmail = email.trim(), onDismiss = { showForgot = false })
        return
    }

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

                if (isLoginMode) {
                    TextButton(
                        onClick = { if (!isLoading) showForgot = true },
                        modifier = Modifier.align(Alignment.End),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                    ) {
                        Text("Mot de passe oublié ?", color = Color(0xFFC8492C), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

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

                if (!isLoginMode) {
                    val uri = androidx.compose.ui.platform.LocalUriHandler.current
                    Text(
                        "Vos idées restent privées tant que vous ne les partagez pas. Ce que nous conservons et comment supprimer votre compte : politique de confidentialité.",
                        fontSize = 12.sp, color = Color(0xFF6B5047),
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { uri.openUri(PRIVACY_URL) }.padding(4.dp)
                    )
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
