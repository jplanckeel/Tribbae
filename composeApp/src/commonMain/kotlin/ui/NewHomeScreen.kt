package ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import data.Link
import data.LinkCategory
import ui.campfire.*

/** Accueil « feu de camp » : salutation, recherche + IA, prochaine sortie, catégories, tendances. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewHomeScreen(
    links: List<Link>,
    onNavigateToExplore: () -> Unit,
    onNavigateToCategory: (LinkCategory) -> Unit,
    onNavigateToDetail: (String) -> Unit,
    onSaveLink: (Link) -> Unit,
    sessionManager: data.SessionManager? = null,
    onNavigateToAI: () -> Unit = {},
    viewModel: viewmodel.LinkViewModel,
    onSearch: (String) -> Unit = { onNavigateToExplore() },
    onOpenCommunityLink: (Link) -> Unit = {},
    onAddWithCategory: (LinkCategory) -> Unit = {},
    onNavigateToTribe: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val displayName by sessionManager?.displayName?.collectAsState() ?: remember { mutableStateOf<String?>(null) }
    val isLoggedIn by sessionManager?.isLoggedIn?.collectAsState() ?: remember { mutableStateOf(false) }
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val community by viewModel.communityLinks.collectAsState()
    var query by remember { mutableStateOf("") }
    var showUpcoming by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { if (community.isEmpty()) viewModel.loadCommunityLinks() }

    val next = remember(links) { upcoming(links) }
    val reminders = remember(links) { soonReminders(links) }
    val counts = remember(links) { links.groupingBy { it.category }.eachCount() }
    val popular = remember(community) { community.sortedByDescending { it.likeCount }.take(10) }
    val recent = remember(links) { links.sortedByDescending { it.createdAt.ifEmpty { it.updatedAt } }.take(6) }
    // « La tribu Dupont » : nom choisi pour la tribu (si c'est bien celle du compte connecté), à défaut celui du compte
    val tribe by viewModel.tribe.collectAsState()
    val userId by sessionManager?.userId?.collectAsState() ?: remember { mutableStateOf<String?>(null) }
    val name = tribe?.takeIf { t -> t.members.any { it.userId == userId } }?.name?.trim()?.ifEmpty { null }
        ?: displayName?.trim().orEmpty()

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { viewModel.forceSync(); viewModel.loadCommunityLinks() },
        modifier = modifier.fillMaxSize().background(Sable)
    ) {
        Box(Modifier.fillMaxSize()) {
            SunDecor()
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .statusBarsPadding()
                    .padding(top = 20.dp, bottom = ui.components.bottomNavClearance()),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Logo()
                    CircleIconButton(
                        Icons.Outlined.NotificationsNone,
                        if (reminders > 0) "À venir, $reminders rappels cette semaine" else "À venir",
                        onClick = { showUpcoming = true },
                        badge = reminders > 0
                    )
                }

                Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(formatToday(), fontSize = 15.sp, color = TextSec)
                    Text(
                        if (name.isNotEmpty()) "La tribu $name,\non fait quoi ce week‑end ?" else "Bienvenue dans la tribu,\non fait quoi ce week‑end ?",
                        style = display(32.sp)
                    )
                }

                Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SearchField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = "Une sortie, une recette, un cadeau…",
                        onSearch = { onSearch(query.trim()) },
                        modifier = Modifier.weight(1f)
                    )
                    Box(
                        Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Prune)
                            .clickable(role = Role.Button, onClick = onNavigateToAI)
                            .semantics { contentDescription = "Générer des idées avec l'IA" },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.AutoAwesome, null, tint = Soleil, modifier = Modifier.size(22.dp))
                    }
                }

                Box(Modifier.padding(horizontal = 20.dp)) {
                    val event = next.firstOrNull()
                    when {
                        event != null -> NextEventCard(event.first, event.second) { onNavigateToDetail(event.first.id) }
                        isLoggedIn || links.isNotEmpty() -> NoEventCard { onAddWithCategory(LinkCategory.EVENEMENT) }
                        else -> JoinCard(onNavigateToTribe)
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.padding(horizontal = 20.dp)) {
                        SectionHeader("Nos catégories", "Tout voir", onNavigateToExplore)
                    }
                    HorizontalRail {
                        CategoryOrder.forEach { c ->
                            CategoryTile(c, counts[c.category] ?: 0) { onNavigateToCategory(c.category) }
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.padding(horizontal = 20.dp)) {
                        SectionHeader("Ça plaît aux tribus", "Explorer", onNavigateToExplore)
                    }
                    if (popular.isEmpty()) {
                        Text(
                            if (isRefreshing) "Chargement…" else "La communauté n'a encore rien partagé. Soyez la première tribu !",
                            fontSize = 14.sp, color = TextSec, modifier = Modifier.padding(horizontal = 20.dp)
                        )
                    } else {
                        HorizontalRail {
                            popular.forEach { l -> RailCard(l) { onOpenCommunityLink(l) } }
                        }
                    }
                }

                Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionHeader("Ajouté récemment")
                    if (recent.isEmpty()) {
                        EmptyBlock(
                            Icons.Outlined.Cottage,
                            "Votre tipi est vide",
                            "Ajoutez une première idée avec le losange « + » ou laissez l'IA vous inspirer."
                        )
                    } else {
                        recent.forEach { l -> RowCard(l, onClick = { onNavigateToDetail(l.id) }) }
                    }
                }
            }
        }
    }

    if (showUpcoming) {
        UpcomingSheet(next, onDismiss = { showUpcoming = false }) { id ->
            showUpcoming = false
            onNavigateToDetail(id)
        }
    }
}

@Composable
private fun NextEventCard(link: Link, at: Long, onClick: () -> Unit) {
    val (wd, day) = dayBadge(at)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Ecorce)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column(
            Modifier.size(width = 58.dp, height = 64.dp).clip(RoundedCornerShape(16.dp)).background(Braise),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(wd.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Ecorce, letterSpacing = 0.6.sp)
            Text(day, style = display(26.sp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                ("À venir" + if (link.reminderEnabled) " · rappel activé" else "").uppercase(),
                fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = SceneGround, letterSpacing = 0.8.sp
            )
            Text(link.title, style = display(17.sp, FontWeight.Bold, Sable), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOf(formatTime(at), metaLine(link)).filter { it.isNotEmpty() }.joinToString(" · ").ifEmpty { formatShortDate(at) },
                fontSize = 13.sp, color = BarkSoft, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        Icon(Icons.Outlined.ChevronRight, null, tint = BarkSoft, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun NoEventCard(onAdd: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .dashedBorder(Dash, 22.dp)
            .clickable(onClick = onAdd)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        val c = categoryUi(LinkCategory.EVENEMENT)
        Box(Modifier.size(width = 58.dp, height = 64.dp).clip(RoundedCornerShape(16.dp)).background(c.bg), contentAlignment = Alignment.Center) {
            Icon(c.icon, null, tint = c.tint, modifier = Modifier.size(26.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Rien de prévu pour l'instant", style = display(17.sp, FontWeight.Bold))
            Text("Ajoutez une sortie datée pour la voir ici et recevoir un rappel.", fontSize = 13.sp, color = TextSec)
        }
        Icon(Icons.Outlined.Add, null, tint = Terracotta, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun JoinCard(onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Ecorce)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(Braise), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Groups, null, tint = Ecorce, modifier = Modifier.size(24.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Créez votre tribu", style = display(17.sp, FontWeight.Bold, Sable))
            Text("Rangez vos idées, partagez-les avec vos proches.", fontSize = 13.sp, color = BarkSoft)
        }
        Icon(Icons.Outlined.ChevronRight, null, tint = BarkSoft, modifier = Modifier.size(20.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UpcomingSheet(items: List<Pair<Link, Long>>, onDismiss: () -> Unit, onOpen: (String) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = SheetColor, dragHandle = { SheetHandle() }) {
        Column(
            Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("À venir", style = display(24.sp))
            if (items.isEmpty()) {
                EmptyBlock(Icons.Outlined.CalendarMonth, "Rien de prévu", "Ajoutez une date à une sortie ou une activité pour la retrouver ici.")
            } else {
                items.take(20).forEach { (l, at) ->
                    RowCard(l, onClick = { onOpen(l.id) }) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(formatShortDate(at), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Ecorce)
                            if (l.reminderEnabled) Text("rappel", fontSize = 12.sp, color = TextSec)
                        }
                    }
                }
            }
        }
    }
}
