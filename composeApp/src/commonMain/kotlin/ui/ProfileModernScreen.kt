package ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linkkeeper.app.R
import data.FolderStyle
import data.FollowRepository
import data.TribePage
import data.UserProfile
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ui.campfire.*
import viewmodel.LinkViewModel

/**
 * « Ma tribu » (maquette « Ma tribu v2 ») : la tribu autour du feu, le foyer
 * (comptes et enfants), le cercle (proches qui ont accès à des dossiers), les
 * invitations, l'année en idées et les réglages. Sans compte : écran Bienvenue.
 */
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
    /** Idées de l'IA pour un enfant (son id). */
    onOpenAi: (String) -> Unit = {},
    onOpenFolder: (data.Folder) -> Unit = {},
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
        TribeContent(viewModel, sessionManager, modifier, onNavigateToFaq, onOpenShopping, onOpenAdmin, onOpenAi, onOpenFolder)
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
private val CardShape = RoundedCornerShape(22.dp)
private val RecetteBg: Color get() = GreenBg
private val RecetteInk: Color get() = GreenInk
private val CadeauInk: Color get() = PeachInk
private val SortieInk: Color get() = CoralInk
private val YearSky: Color get() = SceneSky
private val BraiseSoft: Color get() = SceneGround

/** Carte blanche de la maquette (ombre douce). */
private fun Modifier.tribeCard(shape: androidx.compose.ui.graphics.Shape = CardShape) =
    shadow(10.dp, shape, ambientColor = Ecorce.copy(alpha = 0.06f), spotColor = Ecorce.copy(alpha = 0.10f)).clip(shape).background(CardColor)

/** Entrée de la maquette : « rise » (ressort, depuis 60 %) ou « fadeup » (10 dp plus bas). */
private fun Modifier.entrance(delayMs: Int, rise: Boolean = false): Modifier = composed {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(delayMs.toLong())
        p.animateTo(1f, if (rise) spring(dampingRatio = 0.55f, stiffness = 280f) else tween(500, easing = EaseSoft))
    }
    graphicsLayer {
        val v = p.value
        alpha = v.coerceIn(0f, 1f)
        translationY = (1f - v) * (if (rise) 18 else 10).dp.toPx()
        if (rise) { val s = 0.6f + 0.4f * v; scaleX = s; scaleY = s }
    }
}

/** Une place autour du feu : un compte de la tribu ou un enfant. */
private data class Seat(val key: String, val name: String, val colors: Pair<Color, Color>, val badge: String?, val onClick: () -> Unit, val label: String)

/** Fiche enfant ouverte : un enfant existant ou un ajout. */
private sealed interface KidTarget {
    data object New : KidTarget
    data class Existing(val id: String) : KidTarget
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun TribeContent(
    viewModel: LinkViewModel,
    sessionManager: data.SessionManager,
    modifier: Modifier,
    onFaq: () -> Unit,
    onOpenShopping: () -> Unit,
    onOpenAdmin: () -> Unit,
    onOpenAi: (String) -> Unit,
    onOpenFolder: (data.Folder) -> Unit,
) {
    val links by viewModel.repository.links.collectAsState()
    val folders by viewModel.folders.collectAsState()
    val children by viewModel.children.collectAsState()
    val displayName by sessionManager.displayName.collectAsState()
    val userId by sessionManager.userId.collectAsState()
    val followRepository = remember { FollowRepository(sessionManager = sessionManager) }
    var following by remember { mutableStateOf<List<UserProfile>?>(null) }
    val scope = rememberCoroutineScope()

    var tab by remember { mutableIntStateOf(0) } // 0 = le foyer, 1 = le cercle
    var showMembers by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var showInvite by remember { mutableStateOf(false) }
    var kidTarget by remember { mutableStateOf<KidTarget?>(null) }
    val tribe by viewModel.tribe.collectAsState()
    var tribeError by remember { mutableStateOf<String?>(null) }
    var tribeBusy by remember { mutableStateOf(false) }
    var shareFolder by remember { mutableStateOf<data.Folder?>(null) }
    var showFollowing by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showLogout by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var dataMessage by remember { mutableStateOf<String?>(null) }
    val now = remember { System.currentTimeMillis() }
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
    // Tribu du compte connecté (sinon : tribu d'un seul membre, nommée d'après lui), moi d'abord
    val accounts = (tribe?.members?.takeIf { m -> m.any { it.userId == userId } }
        ?: listOf(data.ApiTribeMember(userId = userId.orEmpty(), displayName = name, role = "TRIBE_ROLE_OWNER", sharing = true)))
        .sortedBy { if (it.userId == userId) 0 else 1 }
    val me = accounts.find { it.userId == userId }
    val tribeName = tribe?.takeIf { t -> t.members.any { it.userId == userId } }?.name?.trim()?.ifEmpty { null } ?: name
    val invitations = tribe?.invitations.orEmpty()
    fun accountName(m: data.ApiTribeMember) =
        if (m.userId == userId) m.displayName.ifBlank { name } else m.displayName.ifBlank { m.email.ifBlank { "Membre" } }
    val isMine = { ownerId: String -> ownerId.isBlank() || ownerId == userId }
    val myLinks = links.count { isMine(it.ownerId) }
    val myFolders = folders.filter { isMine(it.ownerId) }
    val likesReceived = links.filter { isMine(it.ownerId) }.sumOf { it.likeCount } + myFolders.sumOf { it.likeCount }
    val circle = remember(folders, accounts, userId) {
        TribePage.circleOf(folders) { id -> id == userId || accounts.any { it.userId == id } || viewModel.isTribePeer(id) }
    }
    val year = remember(now) { java.util.Calendar.getInstance().apply { timeInMillis = now }.get(java.util.Calendar.YEAR) }
    val yearStats = remember(links, year) { TribePage.yearInIdeas(links, year) }
    val foyerCount = accounts.size + children.size

    // Autour du feu : les comptes, puis les enfants
    val seats = buildList {
        accounts.forEachIndexed { i, m ->
            add(Seat(m.userId, accountName(m), AvatarColors[i % AvatarColors.size], null, { showMembers = true }, "${accountName(m)}, gérer les membres"))
        }
        children.forEach { c ->
            val cd = FolderStyle.countdown(c.birthDate, "anniv", now)
            val badge = cd?.takeIf { it.days <= 30 }?.let { if (it.days == 0) "Anniv." else "J-${it.days}" }
            add(Seat("child-${c.id}", c.name, childColors(children, c.id), badge, { kidTarget = KidTarget.Existing(c.id) }, "Voir le profil de ${c.name}"))
        }
    }
    val shown = if (seats.size > TribePage.RING_SLOTS) seats.take(TribePage.RING_SLOTS - 1) else seats
    val extra = seats.size - shown.size

    Column(modifier.fillMaxSize().background(Sable).verticalScroll(rememberScrollState()).padding(bottom = ui.components.bottomNavClearance())) {
        PageHeader(
            "Ma tribu",
            Modifier.statusBarsPadding().padding(start = 20.dp, end = 20.dp, top = 20.dp),
            subtitle = "$foyerCount au foyer · ${circle.size} dans le cercle" +
                (if (invitations.isNotEmpty()) " · ${invitations.size} invitation${if (invitations.size > 1) "s" else ""} en attente" else "") +
                (if (sessionManager.isAdmin()) " · administrateur" else ""),
            action = { CircleIconButton(Icons.Outlined.Settings, "Paramètres", { showSettings = true }, background = CardColor) },
        )

        // ── La tribu installée autour du feu ──
        Box(Modifier.fillMaxWidth().padding(top = 12.dp).height(246.dp).clipToBounds()) {
            val sun = remember { Animatable(0f) }
            LaunchedEffect(Unit) { sun.animateTo(1f, tween(1100, easing = EaseSoft)) }
            Box(
                Modifier.align(Alignment.TopCenter).offset(y = 42.dp).size(340.dp)
                    .graphicsLayer { translationY = (1f - sun.value) * 60.dp.toPx() }
                    .clip(CircleShape).background(SunDisc)
            )
            CampfireTipi(Modifier.align(Alignment.TopCenter).offset(y = 86.dp).size(104.dp).entrance(150))
            CampfireFlame(Modifier.align(Alignment.TopCenter).offset(x = 70.dp, y = 152.dp).size(26.dp, 30.dp))

            val total = shown.size + if (extra > 0) 1 else 0
            (0 until total).forEach { i ->
                val (dx, dy) = TribePage.ringPosition(i, total)
                val seat = shown.getOrNull(i)
                Column(
                    Modifier.align(Alignment.TopCenter).offset(x = dx.dp, y = (151 + dy - 40).dp).width(64.dp)
                        .entrance(200 + i * 90, rise = true)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(role = Role.Button, onClickLabel = seat?.label ?: "Voir le foyer") { seat?.onClick?.invoke() ?: run { tab = 0 } },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box {
                        Box(
                            Modifier.size(56.dp)
                                .shadow(8.dp, RoundedCornerShape(20.dp), spotColor = Ecorce.copy(alpha = 0.25f))
                                .clip(RoundedCornerShape(20.dp))
                                .background(seat?.colors?.first ?: Sable2)
                                .border(3.dp, Sable, RoundedCornerShape(20.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                seat?.name?.trim()?.take(1)?.uppercase()?.ifEmpty { "?" } ?: "+$extra",
                                style = display(22.sp, color = seat?.colors?.second ?: TextSec)
                            )
                        }
                        seat?.badge?.let {
                            Text(
                                it, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1,
                                modifier = Modifier.align(Alignment.TopEnd).offset(x = 8.dp, y = (-8).dp)
                                    .clip(RoundedCornerShape(8.dp)).background(CadeauInk).padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(seat?.name ?: "la suite", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Ecorce, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            Row(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp, start = 20.dp, end = 20.dp).entrance(350),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text("La tribu $tribeName", style = display(24.sp), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Icon(
                    Icons.Outlined.Edit, "Renommer la tribu", tint = TextMuted,
                    modifier = Modifier.size(32.dp).clip(CircleShape).clickable { showRename = true }.padding(8.dp)
                )
            }
        }

        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            // ── Onglets ──
            BoxWithConstraints(Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(18.dp)).background(Sable2).padding(4.dp)) {
                val half = maxWidth / 2
                val x by androidx.compose.animation.core.animateDpAsState(
                    if (tab == 0) 0.dp else half, spring(dampingRatio = 0.6f, stiffness = 400f), label = "tribe-tab"
                )
                Box(Modifier.offset(x = x).width(half).fillMaxHeight().shadow(3.dp, RoundedCornerShape(14.dp)).clip(RoundedCornerShape(14.dp)).background(CardColor))
                Row(Modifier.fillMaxSize()) {
                    listOf("Le foyer" to foyerCount, "Le cercle" to circle.size).forEachIndexed { i, (label, n) ->
                        Row(
                            Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(14.dp)).clickable(role = Role.Tab) { tab = i },
                            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(label, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (tab == i) Ecorce else TextSec)
                            Text("$n", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextMuted)
                        }
                    }
                }
            }

            if (tab == 0) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Nouveau membre : il voit la tribu et choisit d'y verser son propre contenu
                    if (me != null && !me.sharing && accounts.size > 1) {
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(SoleilPanel).padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("Bienvenue dans la tribu $tribeName !", style = display(18.sp, FontWeight.Bold))
                            val what = listOfNotNull(
                                myLinks.takeIf { it > 0 }?.let { "$it idée${if (it > 1) "s" else ""}" },
                                myFolders.size.takeIf { it > 0 }?.let { "$it dossier${if (it > 1) "s" else ""}" },
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
                    accounts.forEachIndexed { i, m ->
                        val (bg, ink) = AvatarColors[i % AvatarColors.size]
                        val count = links.count { it.ownerId == m.userId || (m.userId == userId && it.ownerId.isBlank()) }
                        Row(
                            Modifier.fillMaxWidth().entrance(i * 60).tribeCard().clickable(role = Role.Button) { showMembers = true }.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(Modifier.size(48.dp).clip(RoundedCornerShape(17.dp)).background(bg), contentAlignment = Alignment.Center) {
                                Text(accountName(m).trim().take(1).uppercase().ifEmpty { "?" }, style = display(20.sp, color = ink))
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(accountName(m) + if (m.userId == userId) " (vous)" else "", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Ecorce, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    if (m.sharing) "$count idée${if (count > 1) "s" else ""} ajoutée${if (count > 1) "s" else ""}" else "Partage de son contenu en attente",
                                    fontSize = 13.sp, color = TextSec, maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                if (m.isOwner) "Créateur" else "Parent", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextSec,
                                modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Sable2).padding(horizontal = 9.dp, vertical = 4.dp)
                            )
                        }
                    }
                    invitations.forEach { inv -> PendingInvitationRow(inv, now, viewModel) }

                    Text("Les enfants", style = display(20.sp, FontWeight.Bold), modifier = Modifier.padding(top = 10.dp))
                    if (children.isEmpty()) {
                        Text("L'âge des enfants sert à filtrer les idées adaptées dans l'Explorer et à guider l'IA.", fontSize = 14.sp, color = TextSec)
                    }
                    children.chunked(2).forEachIndexed { row, pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            pair.forEachIndexed { j, c ->
                                KidCard(
                                    c, childColors(children, c.id), TribePage.ideasForChild(links, folders, c, now), now,
                                    Modifier.weight(1f).entrance(120 + (row * 2 + j) * 60)
                                ) { kidTarget = KidTarget.Existing(c.id) }
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(18.dp)).dashedBorder(Dash, 18.dp)
                            .clickable(role = Role.Button) { kidTarget = KidTarget.New },
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.Add, null, tint = TextSec, modifier = Modifier.size(18.dp))
                        Text("Ajouter un enfant", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextSec)
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Grands-parents, parrains, cousins : ils voient seulement les dossiers que vous leur ouvrez.",
                        fontSize = 14.sp, lineHeight = 20.sp, color = TextSec
                    )
                    circle.forEachIndexed { i, c ->
                        val (bg, ink) = AvatarColors[(i + 3) % AvatarColors.size]
                        Column(
                            Modifier.fillMaxWidth().entrance(i * 70).tribeCard().padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Box(Modifier.size(44.dp).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
                                    Text(c.name.trim().take(1).uppercase().ifEmpty { "?" }, style = display(18.sp, color = ink))
                                }
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(c.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Ecorce, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    val n = c.folders.size
                                    Text("$n dossier${if (n > 1) "s" else ""} ouvert${if (n > 1) "s" else ""}", fontSize = 13.sp, color = TextSec)
                                }
                                Text(
                                    if (c.editor) "Éditeur" else "Lecteur", fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                    color = if (c.editor) RecetteInk else TextSec,
                                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(if (c.editor) RecetteBg else Sable2).padding(horizontal = 9.dp, vertical = 4.dp)
                                )
                            }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                c.folders.forEach { f ->
                                    val manage = isMine(f.ownerId)
                                    Text(
                                        f.name, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = folderSolid(f.color), maxLines = 1,
                                        modifier = Modifier.height(28.dp).clip(RoundedCornerShape(10.dp)).background(folderBg(f.color))
                                            .then(if (manage) Modifier.clickable(role = Role.Button, onClickLabel = "Gérer l'accès à ${f.name}") { shareFolder = f } else Modifier)
                                            .wrapContentHeight().padding(horizontal = 10.dp)
                                    )
                                }
                            }
                        }
                    }
                    if (circle.isEmpty()) {
                        Row(
                            Modifier.fillMaxWidth().clip(CardShape).dashedBorder(Dash, 22.dp).padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(Modifier.size(44.dp).clip(CircleShape).background(Sable2), contentAlignment = Alignment.Center) {
                                Icon(Icons.Outlined.Folder, null, tint = TextMuted, modifier = Modifier.size(20.dp))
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("Personne dans le cercle", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Ecorce)
                                Text("Ouvrez un dossier à un proche : il ne verra que celui-là.", fontSize = 13.sp, color = TextSec)
                            }
                        }
                    }
                }
            }
            tribeError?.takeIf { !showMembers && !showInvite && !showRename }?.let { Text(it, color = SortieInk, fontSize = 14.sp, fontWeight = FontWeight.Medium) }

            // ── Inviter autour du feu ──
            Row(
                Modifier.fillMaxWidth().clip(CardShape).background(Ecorce).clickable(role = Role.Button) { tribeError = null; showInvite = true }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(Modifier.size(46.dp).clip(RoundedCornerShape(15.dp)).background(Braise), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.PersonAdd, null, tint = Ecorce, modifier = Modifier.size(22.dp))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text("Inviter autour du feu", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Sable)
                    Text("Un parent au foyer, ou un proche dans le cercle", fontSize = 13.sp, color = BarkSoft)
                }
                Icon(Icons.Outlined.ChevronRight, null, tint = Sable)
            }

            // ── Notre année en idées ──
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                    Text("Notre année en idées", style = display(20.sp, FontWeight.Bold))
                    Text("$year", fontSize = 13.sp, color = TextSec)
                }
                Box(Modifier.fillMaxWidth().clip(CardShape).background(YearSky)) {
                    Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(26.dp).background(BraiseSoft))
                    Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp).fillMaxWidth().height(2.dp).background(Ecorce))
                    TipiMark(34.dp, stroke = 2.4f, modifier = Modifier.align(Alignment.BottomEnd).padding(end = 18.dp, bottom = 22.dp))
                    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 18.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            YearTile(yearStats.outings, if (yearStats.outings > 1) "sorties gardées" else "sortie gardée", SortieInk, Modifier.weight(1f))
                            YearTile(yearStats.recipes, if (yearStats.recipes > 1) "recettes gardées" else "recette gardée", RecetteInk, Modifier.weight(1f))
                            YearTile(yearStats.gifts, if (yearStats.gifts > 1) "idées cadeaux" else "idée cadeau", CadeauInk, Modifier.weight(1f))
                        }
                        Text(
                            yearStats.bestMonth?.let { (m, n) -> "Votre meilleur mois : ${TribePage.monthName(m)}, $n idée${if (n > 1) "s" else ""} gardée${if (n > 1) "s" else ""}." }
                                ?: "Gardez vos premières idées : elles rempliront votre année.",
                            fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Ecorce,
                            modifier = Modifier.padding(top = 12.dp, end = 56.dp, bottom = 18.dp)
                        )
                    }
                }
                Text(
                    "En tout : ${links.size} idée${if (links.size > 1) "s" else ""} · ${folders.size} dossier${if (folders.size > 1) "s" else ""} · $likesReceived j'aime reçus",
                    fontSize = 13.sp, color = TextSec
                )
            }

            // ── Réglages ──
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Réglages", style = display(20.sp, FontWeight.Bold))
                Column(Modifier.fillMaxWidth().clip(CardShape).background(CardColor)) {
                    AppearanceRow()
                    ListRow(Icons.Outlined.FavoriteBorder, "Tribus suivies", following?.size?.toString()) { showFollowing = true }
                    ListRow(Icons.Outlined.ShoppingCart, "Liste de courses", onClick = onOpenShopping)
                    ListRow(Icons.Outlined.Lock, "Confidentialité & mes données") { uriHandler.openUri(PRIVACY_URL) }
                    ListRow(Icons.Outlined.HelpOutline, "Aide & FAQ", onClick = onFaq)
                    ListRow(Icons.Outlined.Info, "À propos de Tribbae", last = !sessionManager.isAdmin()) { uriHandler.openUri(ABOUT_URL) }
                    if (sessionManager.isAdmin()) ListRow(Icons.Outlined.AdminPanelSettings, "Administration", last = true, onClick = onOpenAdmin)
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Text("Connecté en tant que $name · ", fontSize = 12.sp, color = TextMuted)
                    Text(
                        "Se déconnecter", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextSec,
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(role = Role.Button) { showLogout = true }.padding(4.dp)
                    )
                }
            }
        }
    }

    kidTarget?.let { target ->
        KidSheet(
            target = target,
            viewModel = viewModel,
            now = now,
            onDismiss = { kidTarget = null },
            onIdeas = { c -> kidTarget = null; onOpenAi(c.id) },
            onOpenFolder = { f -> kidTarget = null; onOpenFolder(f) },
        )
    }

    if (showInvite) {
        ModalBottomSheet(onDismissRequest = { showInvite = false }, containerColor = SheetColor, dragHandle = { SheetHandle() }) {
            var role by remember { mutableIntStateOf(0) } // 0 = au foyer, 1 = dans le cercle
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Inviter autour du feu", style = display(22.sp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.selectableGroup()) {
                    listOf(
                        "Au foyer" to "Un parent : voit et modifie tout.",
                        "Dans le cercle" to "Un proche : seulement les dossiers choisis.",
                    ).forEachIndexed { i, (title, desc) ->
                        val on = role == i
                        val scale by androidx.compose.animation.core.animateFloatAsState(if (on) 1.02f else 1f, spring(dampingRatio = 0.5f), label = "invite-role")
                        Column(
                            Modifier.weight(1f).graphicsLayer { scaleX = scale; scaleY = scale }
                                .clip(RoundedCornerShape(18.dp)).background(CardColor)
                                .border(if (on) 2.dp else 1.dp, if (on) Terracotta else Line, RoundedCornerShape(18.dp))
                                .selectable(selected = on, role = Role.RadioButton) { role = i; tribeError = null }
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(title, style = display(16.sp))
                            Text(desc, fontSize = 12.sp, lineHeight = 16.sp, color = TextSec)
                        }
                    }
                }
                if (role == 0) {
                    var email by remember { mutableStateOf("") }
                    Text(
                        "Votre proche reçoit un lien pour rejoindre la tribu et tout gérer avec vous : idées, dossiers, enfants et liste de courses.",
                        fontSize = 15.sp, color = Ecorce2
                    )
                    CampField(
                        "Son adresse email", email, { email = it; tribeError = null },
                        placeholder = "camille@exemple.fr", icon = Icons.Outlined.Email,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Email)
                    )
                    tribeError?.let { Text(it, color = SortieInk, fontSize = 14.sp, fontWeight = FontWeight.Medium) }
                    CampButton(if (tribeBusy) "Envoi…" else "Envoyer l'invitation", {
                        if (email.isBlank()) { tribeError = "Saisissez l'adresse email de votre proche."; return@CampButton }
                        tribeBusy = true
                        viewModel.inviteTribeMember(email) { err ->
                            tribeBusy = false
                            tribeError = err
                            if (err == null) {
                                android.widget.Toast.makeText(context, "Invitation envoyée", android.widget.Toast.LENGTH_SHORT).show()
                                email = ""; showInvite = false; tab = 0
                            }
                        }
                    }, Modifier.fillMaxWidth(), kind = ButtonKind.Ai, icon = Icons.Outlined.Share)
                    Text(
                        "Le lien expire dans 7 jours. Pas encore de compte ? Il pourra en créer un depuis le lien. Ses propres idées ne rejoindront la tribu qu'une fois qu'il l'aura confirmé depuis « Ma tribu ».",
                        fontSize = 12.sp, color = TextMuted, textAlign = TextAlign.Center
                    )
                } else if (myFolders.isEmpty()) {
                    EmptyBlock(Icons.Filled.CreateNewFolder, "Aucun dossier à ouvrir", "Le cercle voit seulement les dossiers que vous lui ouvrez : créez-en un d'abord dans « Mes idées ».")
                } else {
                    CircleInviteForm(myFolders, viewModel) {
                        android.widget.Toast.makeText(context, "Dossiers ouverts à votre proche", android.widget.Toast.LENGTH_SHORT).show()
                        showInvite = false; tab = 1
                    }
                    Text("Ou partager un dossier par lien", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextSec, modifier = Modifier.padding(top = 8.dp))
                    myFolders.forEach { f ->
                        MenuRow(folderIconVector(f), f.name, tint = folderSolid(f.color)) { showInvite = false; shareFolder = f }
                    }
                }
            }
        }
    }
    shareFolder?.let { ShareFolderSheet(it, viewModel) { shareFolder = null } }

    if (showFollowing) {
        ModalBottomSheet(onDismissRequest = { showFollowing = false }, containerColor = SheetColor, dragHandle = { SheetHandle() }) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Tribus suivies", style = display(24.sp))
                val list = following.orEmpty()
                if (list.isEmpty()) {
                    EmptyBlock(Icons.Filled.FavoriteBorder, "Vous ne suivez personne", "Depuis la fiche d'une idée de la communauté, suivez la tribu qui l'a partagée.")
                } else {
                    list.forEachIndexed { i, u ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(CardColor).padding(12.dp),
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
        ModalBottomSheet(onDismissRequest = { showSettings = false }, containerColor = SheetColor, dragHandle = { SheetHandle() }) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Paramètres", style = display(24.sp))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(CardColor).padding(14.dp),
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
                    "Pour modifier votre mot de passe, écrivez à contact@tribbae.fr.",
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
            containerColor = SheetColor,
            title = { Text("Nom de la tribu", style = display(20.sp, FontWeight.Bold)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CampField("La tribu…", newName, { newName = it; tribeError = null }, placeholder = "Dupont", maxLength = 60)
                    Text("Affiché dans l'accueil et pour tous les membres. Vos idées restent signées de votre nom.", fontSize = 13.sp, color = TextSec)
                    tribeError?.let { Text(it, color = SortieInk, fontSize = 13.sp) }
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
        ModalBottomSheet(onDismissRequest = { showMembers = false }, containerColor = SheetColor, dragHandle = { SheetHandle() }) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Le foyer", style = display(24.sp))
                accounts.forEachIndexed { i, m ->
                    val self = m.userId == userId
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(CardColor).padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val label = m.displayName.ifBlank { m.email.ifBlank { "Membre" } }
                        InitialAvatar(label, i, 40.dp, 12.dp, 16.sp)
                        Column(Modifier.weight(1f)) {
                            Text(label + if (self) " (vous)" else "", fontWeight = FontWeight.Bold, color = Ecorce, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                listOfNotNull(
                                    if (m.isOwner) "Créateur" else "Parent",
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
                if (invitations.isNotEmpty()) {
                    Text("Invitations en attente", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextSec, modifier = Modifier.padding(top = 6.dp))
                    invitations.forEach { inv ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(CardColor).padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Sable2), contentAlignment = Alignment.Center) {
                                Icon(Icons.Outlined.Email, null, tint = TextSec, modifier = Modifier.size(18.dp))
                            }
                            Column(Modifier.weight(1f)) {
                                Text(inv.email, fontWeight = FontWeight.Bold, color = Ecorce, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    listOfNotNull(
                                        inv.invitedByName.takeIf { it.isNotBlank() }?.let { "invité par $it" },
                                        inv.expiresAt.takeIf { it > 0 }?.let {
                                            "jusqu'au " + java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault())
                                                .format(java.time.format.DateTimeFormatter.ofPattern("d MMM", java.util.Locale.FRENCH))
                                        },
                                    ).joinToString(" · "),
                                    fontSize = 12.sp, color = TextSec, maxLines = 1
                                )
                            }
                            CampButton("Annuler", {
                                viewModel.revokeTribeInvitation(inv.id) { err -> tribeError = err }
                            }, kind = ButtonKind.Ghost, height = 38.dp)
                        }
                    }
                }
                tribeError?.let { Text(it, color = SortieInk, fontSize = 14.sp) }
                CampButton("Inviter un proche", { showMembers = false; showInvite = true }, Modifier.fillMaxWidth(), kind = ButtonKind.Secondary, icon = Icons.Outlined.PersonAdd)
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
                containerColor = SheetColor,
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
                containerColor = SheetColor,
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
            containerColor = SheetColor,
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

/** Le tipi au centre du feu (SVG de la maquette, viewBox 100×100). */
@Composable
private fun CampfireTipi(modifier: Modifier) {
    val paths = remember {
        fun p(d: String) = PathParser().parsePathString(d).toPath()
        listOf(p("M50 24L81 80H19Z"), p("M50 50L64 80H36Z"), p("M34 52H50L42 68Z"), p("M43 13L54 30M57 13L46 30"),
            p("M49 43l2.7-3 2.7 3 2.6-3M58 62l2.7-3 2.7 3 2.6-3"), p("M6 80H94"))
    }
    Canvas(modifier) {
        val s = size.width / 100f
        scale(s, s, pivot = Offset.Zero) {
            fun stroke(w: Float) = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round)
            drawPath(paths[0], Tipi); drawPath(paths[0], Ecorce, style = stroke(4f))
            drawPath(paths[3], Ecorce, style = stroke(4f))
            drawPath(paths[1], Prune); drawPath(paths[1], Ecorce, style = stroke(4f))
            drawPath(paths[2], Braise); drawPath(paths[2], Ecorce, style = stroke(3.5f))
            drawPath(paths[4], Ecorce, style = stroke(2.6f))
            drawPath(paths[5], Ecorce, style = stroke(4f))
        }
    }
}

/** La flamme qui danse à côté du tipi. */
@Composable
private fun CampfireFlame(modifier: Modifier) {
    val flame = remember { PathParser().parsePathString("M13 2c4 6 9 9 9 16a9 9 0 0 1-18 0c0-4 2-6 4-8 0 3 1 5 3 5 0-5-1-9 2-13z").toPath() }
    val t = rememberInfiniteTransition(label = "flame")
    val sy by t.animateFloat(1f, 1.12f, infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "flame-scale")
    Canvas(modifier) {
        val s = size.width / 26f
        scale(s, s, pivot = Offset.Zero) {
            scale(1f, sy, pivot = Offset(13f, 28f)) {
                drawPath(flame, Braise)
                drawPath(flame, Ecorce, style = Stroke(2f, join = StrokeJoin.Round))
            }
            drawLine(Ecorce, Offset(3f, 28f), Offset(23f, 28f), 2.4f, StrokeCap.Round)
        }
    }
}

@Composable
private fun YearTile(value: Int, label: String, color: Color, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(CardColor).padding(vertical = 10.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text("$value", style = display(24.sp, color = color))
        Text(label, fontSize = 12.sp, lineHeight = 15.sp, color = TextSec, textAlign = TextAlign.Center)
    }
}

/** Invitation au foyer en attente : « Relancer » renvoie l'email (au plus toutes les 10 min côté serveur). */
@Composable
private fun PendingInvitationRow(inv: data.ApiTribeInvitation, now: Long, viewModel: LinkViewModel) {
    var state by remember(inv.id) { mutableIntStateOf(0) } // 0 = à relancer, 1 = envoi, 2 = relancé
    val context = androidx.compose.ui.platform.LocalContext.current
    Row(
        Modifier.fillMaxWidth().clip(CardShape).dashedBorder(Dash, 22.dp).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(Sable2), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Email, null, tint = TextMuted, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(inv.email, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Ecorce, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val days = if (inv.createdAt > 0) ((now - inv.createdAt) / 86_400_000L).toInt() else -1
            Text(
                when {
                    days < 0 -> "Invitation envoyée"
                    days == 0 -> "Invitation envoyée aujourd'hui"
                    days == 1 -> "Invitation envoyée hier"
                    else -> "Invitation envoyée il y a $days jours"
                },
                fontSize = 13.sp, color = TextSec, maxLines = 1
            )
        }
        Text(
            when (state) { 2 -> "Relancé ✓"; 1 -> "Envoi…"; else -> "Relancer" },
            fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (state == 2) RecetteInk else Sable,
            modifier = Modifier.height(36.dp).clip(RoundedCornerShape(12.dp)).background(if (state == 2) RecetteBg else Ecorce)
                .clickable(enabled = state == 0, role = Role.Button) {
                    state = 1
                    viewModel.inviteTribeMember(inv.email) { err ->
                        state = if (err == null) 2 else 0
                        android.widget.Toast.makeText(context, err ?: "Invitation renvoyée", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
                .wrapContentHeight().padding(horizontal = 12.dp)
        )
    }
}

@Composable
private fun KidCard(child: data.Child, colors: Pair<Color, Color>, ideas: Int, now: Long, modifier: Modifier, onClick: () -> Unit) {
    val (bg, ink) = colors
    val cd = FolderStyle.countdown(child.birthDate, "anniv", now)
    val soon = cd != null && cd.days <= 30
    Column(modifier.tribeCard().clickable(role = Role.Button, onClickLabel = "Voir le profil de ${child.name}", onClick = onClick)) {
        Box(Modifier.fillMaxWidth().height(74.dp).background(bg)) {
            cd?.let {
                Text(
                    it.label, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = if (soon) OnSolid else ink, maxLines = 1,
                    modifier = Modifier.align(Alignment.TopEnd).padding(10.dp).clip(RoundedCornerShape(9.dp))
                        .background(if (soon) CadeauInk else CardColor).padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
        Box {
            Box(
                Modifier.offset(x = 12.dp, y = (-20).dp).size(48.dp).clip(RoundedCornerShape(17.dp)).background(CardColor).padding(3.dp)
                    .clip(RoundedCornerShape(14.dp)).background(ink),
                contentAlignment = Alignment.Center
            ) {
                Text(child.name.trim().take(1).uppercase().ifEmpty { "?" }, style = display(20.sp, color = Color.White))
            }
            Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 26.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(child.name, style = display(17.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${formatAge(child.birthDate)} · $ideas idée${if (ideas > 1) "s" else ""}", fontSize = 13.sp, color = TextSec)
            }
        }
    }
}

/** Ouvre des dossiers à un proche qui a déjà un compte Tribbae. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CircleInviteForm(folders: List<data.Folder>, viewModel: LinkViewModel, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var picked by remember { mutableStateOf(folders.take(1).map { it.id }.toSet()) }
    var editor by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Dossiers ouverts", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Ecorce)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            folders.forEach { f ->
                val on = f.id in picked
                Text(
                    (if (on) "✓ " else "+ ") + f.name, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (on) Sable else TextSec, maxLines = 1,
                    modifier = Modifier.height(34.dp).clip(RoundedCornerShape(12.dp)).background(if (on) Ecorce else CardColor)
                        .then(if (on) Modifier else Modifier.border(1.dp, Line, RoundedCornerShape(12.dp)))
                        .toggleable(on, role = Role.Checkbox) { picked = if (it) picked + f.id else picked - f.id; error = null }
                        .wrapContentHeight().padding(horizontal = 12.dp)
                )
            }
        }
        Row(Modifier.clip(RoundedCornerShape(12.dp)).background(Sable2).padding(3.dp).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            listOf(false to "Lecteur", true to "Éditeur").forEach { (value, label) ->
                Text(
                    label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (editor == value) Ecorce else TextSec,
                    modifier = Modifier.height(30.dp).clip(RoundedCornerShape(9.dp)).background(if (editor == value) CardColor else Color.Transparent)
                        .selectable(editor == value, role = Role.RadioButton) { editor = value }
                        .wrapContentHeight().padding(horizontal = 12.dp)
                )
            }
        }
        CampField(
            "Son adresse email", email, { email = it; error = null },
            placeholder = "mamie.jo@exemple.fr", icon = Icons.Outlined.Email,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Email)
        )
        error?.let { Text(it, color = SortieInk, fontSize = 14.sp, fontWeight = FontWeight.Medium) }
        CampButton(if (busy) "Envoi…" else "Ouvrir les dossiers", {
            if (picked.isEmpty()) { error = "Choisissez au moins un dossier à ouvrir."; return@CampButton }
            if (!email.contains("@")) { error = "Saisissez l'adresse email de votre proche."; return@CampButton }
            // Ajouter quelqu'un attend la confirmation de l'adresse
            if (!viewModel.emailVerified.value) { error = data.EmailVerification.VERIFY_FIRST; return@CampButton }
            busy = true
            scope.launch {
                val role = if (editor) "COLLABORATOR_ROLE_EDITOR" else "COLLABORATOR_ROLE_VIEWER"
                val failed = folders.filter { it.id in picked }.filter { viewModel.addCollaborator(it.id, email.trim(), role) == null }
                busy = false
                when {
                    failed.size == picked.size -> error = "Invitation impossible : vérifiez l'email (compte Tribbae requis)."
                    failed.isNotEmpty() -> error = "Pas ajouté à : ${failed.joinToString { it.name }}."
                    else -> onDone()
                }
            }
        }, Modifier.fillMaxWidth(), kind = ButtonKind.Ai, icon = Icons.Outlined.Share, enabled = !busy)
        Text(
            "Votre proche doit avoir un compte Tribbae. " + (if (editor) "Il pourra ajouter ses idées" else "Il pourra consulter") +
                " sans voir le reste de la tribu. Vous pourrez changer les accès à tout moment.",
            fontSize = 12.sp, color = TextMuted, textAlign = TextAlign.Center
        )
    }
}

/** Fiche d'un enfant (anniversaire, ses dossiers, idées de l'IA) ou formulaire d'ajout / de modification. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun KidSheet(
    target: KidTarget,
    viewModel: LinkViewModel,
    now: Long,
    onDismiss: () -> Unit,
    onIdeas: (data.Child) -> Unit,
    onOpenFolder: (data.Folder) -> Unit,
) {
    val children by viewModel.children.collectAsState()
    val folders by viewModel.folders.collectAsState()
    val child = (target as? KidTarget.Existing)?.let { t -> children.find { it.id == t.id } }
    var editing by remember { mutableStateOf(child == null) }
    var name by remember { mutableStateOf(child?.name.orEmpty()) }
    var birth by remember { mutableStateOf(child?.birthDate) }
    var error by remember { mutableStateOf<String?>(null) }
    var showDate by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }
    val dateFmt = remember { java.text.SimpleDateFormat("d MMMM yyyy", java.util.Locale.FRENCH) }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = SheetColor, dragHandle = { SheetHandle() }) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (child == null || editing) {
                Text(if (child != null) "Modifier ${child.name}" else "Ajouter un enfant", style = display(22.sp))
                CampField("Prénom", name, { name = it; error = null }, placeholder = "Emma", maxLength = 60)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Date de naissance", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextSec)
                    Row(
                        Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(18.dp)).background(CardColor)
                            .border(1.5.dp, Line, RoundedCornerShape(18.dp))
                            .clickable(role = Role.Button) { showDate = true }.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Outlined.Cake, null, tint = TextMuted, modifier = Modifier.size(19.dp))
                        Text(birth?.let { dateFmt.format(java.util.Date(it)) } ?: "Choisir une date", fontSize = 15.sp, color = if (birth != null) Ecorce else TextMuted)
                    }
                }
                Text("L'âge sert à filtrer l'Explorer et à guider les idées de l'IA. Rien n'est public.", fontSize = 12.sp, color = TextMuted)
                error?.let { Text(it, color = SortieInk, fontSize = 14.sp, fontWeight = FontWeight.Medium) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CampButton("Annuler", { if (child != null) editing = false else onDismiss() }, kind = ButtonKind.Ghost)
                    CampButton(if (child != null) "Enregistrer" else "Ajouter", {
                        val b = birth
                        when {
                            name.isBlank() || b == null -> error = "Le prénom et la date de naissance sont nécessaires."
                            b > System.currentTimeMillis() -> error = "La date de naissance est dans le futur."
                            child != null -> { viewModel.updateChild(child.copy(name = name.trim(), birthDate = b)); editing = false }
                            else -> { viewModel.addChild(name.trim(), b); onDismiss() }
                        }
                    }, Modifier.weight(1f))
                }
                if (child != null) {
                    Text(
                        "Retirer ${child.name} de la tribu", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextSec,
                        modifier = Modifier.align(Alignment.CenterHorizontally).clip(RoundedCornerShape(10.dp)).clickable(role = Role.Button) { confirmRemove = true }.padding(8.dp)
                    )
                }
            } else {
                val (bg, ink) = childColors(children, child.id)
                val cd = FolderStyle.countdown(child.birthDate, "anniv", now)
                val pop = remember { Animatable(0.7f) }
                LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 400f)) }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(
                        Modifier.size(64.dp).graphicsLayer { scaleX = pop.value; scaleY = pop.value }.clip(RoundedCornerShape(22.dp)).background(ink),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(child.name.trim().take(1).uppercase().ifEmpty { "?" }, style = display(28.sp, color = Color.White))
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(child.name, style = display(24.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${formatAge(child.birthDate)} · né·e le ${dateFmt.format(java.util.Date(child.birthDate))}", fontSize = 14.sp, color = TextSec)
                    }
                    CircleIconButton(Icons.Outlined.Close, "Fermer", onDismiss, background = Sable2)
                }
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(bg).padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Outlined.Cake, null, tint = ink, modifier = Modifier.size(22.dp))
                    Text(TribePage.birthdayLabel(child.birthDate), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = ink, modifier = Modifier.weight(1f))
                    cd?.let { Text(it.label, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = ink) }
                }
                val theirFolders = folders.filter { child.id in it.childIds }
                if (theirFolders.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Ses dossiers", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Ecorce)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            theirFolders.forEach { f ->
                                Row(
                                    Modifier.height(34.dp).clip(RoundedCornerShape(12.dp)).background(folderBg(f.color))
                                        .clickable(role = Role.Button) { onOpenFolder(f) }.padding(horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(folderIconVector(f), null, tint = folderSolid(f.color), modifier = Modifier.size(15.dp))
                                    Text(f.name, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = folderSolid(f.color), maxLines = 1)
                                }
                            }
                        }
                    }
                }
                Text("L'âge sert à filtrer l'Explorer et à guider les idées de l'IA. Rien n'est public.", fontSize = 12.sp, color = TextMuted)
                CampButton("Des idées pour ${child.name}", { onIdeas(child) }, Modifier.fillMaxWidth(), icon = Icons.Outlined.AutoAwesome)
                CampButton("Modifier le profil", { editing = true }, Modifier.fillMaxWidth(), kind = ButtonKind.Ghost, icon = Icons.Outlined.Edit)
            }
        }
    }

    if (showDate) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = birth?.let { local ->
                // Le sélecteur travaille en minuit UTC
                val c = java.util.Calendar.getInstance().apply { timeInMillis = local }
                java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply { clear(); set(c.get(java.util.Calendar.YEAR), c.get(java.util.Calendar.MONTH), c.get(java.util.Calendar.DAY_OF_MONTH)) }.timeInMillis
            },
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= System.currentTimeMillis()
            }
        )
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { utc ->
                        val day = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply { timeInMillis = utc }
                        // Midi local, comme le web : la date ne glisse pas d'un jour selon le fuseau
                        birth = java.util.Calendar.getInstance().apply {
                            clear(); set(day.get(java.util.Calendar.YEAR), day.get(java.util.Calendar.MONTH), day.get(java.util.Calendar.DAY_OF_MONTH), 12, 0)
                        }.timeInMillis
                        error = null
                    }
                    showDate = false
                }) { Text("Valider", color = Terracotta, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("Annuler", color = Ecorce) } }
        ) { DatePicker(state) }
    }

    if (confirmRemove && child != null) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            containerColor = SheetColor,
            title = { Text("Retirer ${child.name} ?", style = display(20.sp, FontWeight.Bold)) },
            confirmButton = {
                TextButton(onClick = { confirmRemove = false; viewModel.deleteChild(child.id); onDismiss() }) {
                    Text("Retirer", color = Terracotta, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Annuler", color = Ecorce) } }
        )
    }
}

/** Politique de confidentialité, publiée sur le site. */
private const val PRIVACY_URL = "${data.Backend.PRODUCTION}/confidentialite"

/** Page À propos (pourquoi Tribbae, principes), publiée sur le site. */
private const val ABOUT_URL = "${data.Backend.PRODUCTION}/a-propos"

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
        containerColor = SheetColor,
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
                error?.let { Text(it, color = CoralInk, fontSize = 13.sp, fontWeight = FontWeight.Medium) }
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
            ) { Text(if (busy) "Suppression…" else "Supprimer définitivement", color = CoralInk, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Annuler", color = Ecorce) } }
    )
}

/** « Apparence » : jour, soir (mode sombre « veillée ») ou comme l'appareil. Retenue sur cet appareil. */
@Composable
private fun AppearanceRow() {
    Column {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Outlined.LightMode, null, tint = TextSec, modifier = Modifier.size(20.dp))
            Text("Apparence", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ecorce, modifier = Modifier.weight(1f))
            Row(
                Modifier.clip(RoundedCornerShape(12.dp)).background(Sable2).padding(3.dp).selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                listOf("jour" to "Jour", "soir" to "Soir", "auto" to "Auto").forEach { (key, label) ->
                    val on = Appearance.pref == key
                    Text(
                        label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (on) Ecorce else TextSec,
                        modifier = Modifier.height(30.dp).clip(RoundedCornerShape(9.dp))
                            .background(if (on) (if (Appearance.dark) Line else CardColor) else Color.Transparent)
                            .selectable(on, role = Role.RadioButton) { Appearance.set(key) }
                            .wrapContentHeight().padding(horizontal = 10.dp)
                    )
                }
            }
        }
        HorizontalDivider(color = Sable2)
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
                    color = Ecorce
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
                                containerColor = if (isLoginMode == mode) Color(0xFFC8492C) else Sable2,
                                contentColor = if (isLoginMode == mode) Color.White else TextSec
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
                        color = CoralBg,
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
                        fontSize = 12.sp, color = TextSec,
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
                                sessionManager.saveSession(response.userId, response.token, response.displayName, response.isAdmin, response.emailVerified)
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
                    Text("Annuler", color = TextSec)
                }
            }
        }
    }
}
