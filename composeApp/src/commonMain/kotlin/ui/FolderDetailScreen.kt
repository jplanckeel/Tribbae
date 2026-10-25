package ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import data.Child
import data.Folder
import data.FolderStyle
import data.Link
import data.LinkCategory
import ui.campfire.*

/** Dossier : bandeau à sa couleur, membres, actions, idées en mosaïque. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderDetailScreen(
    folder: Folder,
    links: List<Link>,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onLinkClick: (String) -> Unit,
    onSaveLink: (Link) -> Unit,
    viewModel: viewmodel.LinkViewModel? = null,
    onAddIdea: () -> Unit = {},
    onAi: () -> Unit = {},
    highlightLinkId: String? = null,
) {
    val items = remember(links, folder.id, folder.sort) {
        FolderStyle.sortLinks(links.filter { it.folderId == folder.id }, folder.sort, CategoryOrder.map { it.category })
    }
    val children = viewModel?.children?.collectAsState()?.value.orEmpty()
    var showMenu by remember { mutableStateOf(false) }
    var showShare by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    val visibility = when {
        folder.visibility.uppercase().contains("PUBLIC") -> "Public"
        folder.collaborators.isNotEmpty() -> "Partagé"
        else -> "Privé"
    }

    CollectionScaffold(
        title = folder.name,
        subtitle = "${items.size} idée${if (items.size > 1) "s" else ""} · $visibility",
        bg = folderBg(folder.color),
        solid = folderSolid(folder.color),
        icon = folderIconVector(folder),
        onBack = onBack,
        folder = folder,
        children = children,
        highlight = highlightLinkId,
        actions = {
            if (viewModel != null) CircleIconButton(Icons.Outlined.PersonAdd, "Inviter et partager", { showShare = true }, background = PhotoVeil)
            CircleIconButton(Icons.Outlined.MoreHoriz, "Plus d'actions", { showMenu = true }, background = PhotoVeil)
        },
        extra = {
            if (folder.collaborators.isNotEmpty()) {
                AvatarStack(
                    listOf(folder.ownerDisplayName.ifBlank { "Moi" }) + folder.collaborators.map { it.displayName.ifBlank { it.email } },
                    "${folder.collaborators.size + 1} membres"
                )
            }
            if (folder.aiGenerated) Tag("IA", Prune, Sable, Icons.Outlined.AutoAwesome)
        },
        buttons = {
            CampButton("Ajouter une idée", onAddIdea, icon = Icons.Outlined.Add, height = 40.dp)
            CampButton("Compléter avec l'IA", onAi, kind = ButtonKind.Ai, icon = Icons.Outlined.AutoAwesome, height = 40.dp)
            CampButton("Personnaliser", onEdit, kind = ButtonKind.Ghost, icon = Icons.Outlined.Edit, height = 40.dp)
        },
        links = items,
        emptyTitle = "Ce dossier attend ses premières idées",
        emptyText = "Ajoutez un lien, une recette, une sortie… ou laissez l'IA le remplir.",
        onLinkClick = onLinkClick,
        onFavorite = onSaveLink
    )

    if (showMenu) {
        ModalBottomSheet(onDismissRequest = { showMenu = false }, containerColor = SheetColor, dragHandle = { SheetHandle() }) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Dossier", style = display(24.sp), modifier = Modifier.padding(bottom = 8.dp))
                MenuRow(Icons.Outlined.Edit, "Personnaliser") { showMenu = false; onEdit() }
                if (viewModel != null) {
                    MenuRow(Icons.Outlined.PushPin, if (folder.pinned) "Désépingler de Mes idées" else "Épingler en haut de Mes idées") {
                        showMenu = false
                        viewModel.updateFolder(folder.copy(pinned = !folder.pinned))
                    }
                }
                if (viewModel != null) MenuRow(Icons.Outlined.PersonAdd, "Inviter et partager") { showMenu = false; showShare = true }
                MenuRow(Icons.Outlined.Delete, "Supprimer le dossier", danger = true) { showMenu = false; showDelete = true }
            }
        }
    }
    if (showShare && viewModel != null) ShareFolderSheet(folder, viewModel) { showShare = false }
    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            containerColor = SheetColor,
            title = { Text("Supprimer « ${folder.name} » ?", style = display(20.sp, FontWeight.Bold)) },
            text = { Text("Le dossier disparaît, ses idées sont conservées dans « Non rangées ».", color = Ecorce2) },
            confirmButton = { TextButton(onClick = { showDelete = false; onDelete() }) { Text("Supprimer", color = Terracotta, fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text("Annuler", color = Ecorce) } }
        )
    }
}

/**
 * Dossier d'un autre membre, en lecture : dossier public de la communauté, ou
 * dossier ouvert depuis un lien de partage (https://…/share/<jeton>). Seules
 * les idées que le serveur autorise sont listées ; on peut aimer le dossier.
 */
@Composable
fun PublicFolderScreen(
    folder: Folder?,
    shareToken: String?,
    viewModel: viewmodel.LinkViewModel,
    onBack: () -> Unit,
    onLinkClick: (Link) -> Unit,
) {
    var current by remember { mutableStateOf(folder) }
    var items by remember { mutableStateOf<List<Link>?>(null) }
    var failed by remember { mutableStateOf(false) }
    var liked by remember { mutableStateOf(folder?.likedByMe ?: false) }
    var likeCount by remember { mutableStateOf(folder?.likeCount ?: 0) }
    val loggedIn = viewModel.currentUserId() != null

    LaunchedEffect(folder?.id, shareToken) {
        if (shareToken != null) {
            val r = viewModel.loadSharedFolder(shareToken)
            if (r == null) failed = true else { current = r.first; items = r.second }
        } else if (folder != null) {
            val r = viewModel.loadPublicFolderLinks(folder.id)
            if (r == null) failed = true else items = r
        }
    }

    val f = current
    if (f == null) {
        // Lien de partage : chargement, ou lien invalide / révoqué
        Box(Modifier.fillMaxSize().background(Sable).statusBarsPadding().padding(20.dp)) {
            CircleIconButton(Icons.Outlined.ChevronLeft, "Retour", onBack, background = Sable2)
            Box(Modifier.align(Alignment.Center)) {
                if (failed) EmptyBlock(Icons.Outlined.LinkOff, "Lien de partage invalide", "Ce dossier n'est plus partagé, ou le lien est incomplet.")
                else CircularProgressIndicator(color = Terracotta)
            }
        }
        return
    }

    val list = items.orEmpty()
    val owner = f.ownerDisplayName.takeIf { it.isNotBlank() }
    CollectionScaffold(
        title = f.name,
        subtitle = if (items == null && !failed) "Chargement…"
            else "${list.size} idée${if (list.size > 1) "s" else ""}" + (owner?.let { " · par $it" } ?: ""),
        bg = folderBg(f.color),
        solid = folderSolid(f.color),
        icon = folderIconVector(f),
        onBack = onBack,
        folder = f,
        extra = {
            if (f.aiGenerated) Tag("IA", Prune, Sable, Icons.Outlined.AutoAwesome)
            if (shareToken != null) Tag("Partagé avec vous", CardColor, Ecorce, Icons.Outlined.Link)
            else Tag("Public", BlueBg, BlueInk, Icons.Outlined.Public)
        },
        buttons = {
            // Like réservé aux dossiers publics (un lien de partage n'est pas une publication)
            if (shareToken == null && loggedIn) {
                CampButton(
                    if (likeCount > 0) "J'aime · $likeCount" else "J'aime",
                    { viewModel.toggleFolderLike(f.id, liked) { l, c -> liked = l; likeCount = c } },
                    kind = if (liked) ButtonKind.Dark else ButtonKind.Ghost,
                    icon = if (liked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    height = 40.dp,
                    iconModifier = Modifier.likePop(liked),
                )
            }
        },
        links = remember(list, f.sort) { FolderStyle.sortLinks(list, f.sort, CategoryOrder.map { it.category }) },
        emptyTitle = when {
            failed -> "Impossible de charger ce dossier"
            items == null -> "Chargement…"
            else -> "Ce dossier est encore vide"
        },
        emptyText = if (failed) "Vérifiez votre connexion et réessayez." else null,
        onLinkClick = { id -> list.find { it.id == id }?.let(onLinkClick) },
        onFavorite = null
    )
}

/** Idées d'une catégorie (ou idées non rangées) avec le même gabarit que les dossiers. */
@Composable
fun CategoryDetailScreen(
    category: LinkCategory?,
    links: List<Link>,
    onBack: () -> Unit,
    onLinkClick: (Link) -> Unit,
    onSaveLink: (Link) -> Unit,
    onAdd: () -> Unit = {},
    onExplore: () -> Unit = {},
) {
    val cat = category?.let { categoryUi(it) }
    CollectionScaffold(
        title = cat?.plural ?: "Non rangées",
        subtitle = "${links.size} idée${if (links.size > 1) "s" else ""} dans la tribu",
        bg = cat?.bg ?: Sable2,
        solid = cat?.tint ?: TextSec,
        icon = cat?.icon ?: Icons.Outlined.BookmarkBorder,
        onBack = onBack,
        buttons = {
            CampButton("Ajouter", onAdd, icon = Icons.Outlined.Add, height = 40.dp)
            if (cat != null) CampButton("Voir la communauté", onExplore, kind = ButtonKind.Ghost, icon = Icons.Outlined.Explore, height = 40.dp)
        },
        links = links.sortedByDescending { it.createdAt.ifEmpty { it.updatedAt } },
        emptyTitle = if (cat != null) "Aucune ${cat.label.lowercase()} pour l'instant" else "Tout est rangé !",
        emptyText = null,
        onLinkClick = { id -> links.find { it.id == id }?.let(onLinkClick) },
        onFavorite = onSaveLink
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CollectionScaffold(
    title: String,
    subtitle: String,
    bg: Color,
    solid: Color,
    icon: ImageVector,
    onBack: () -> Unit,
    folder: Folder? = null,
    children: List<Child> = emptyList(),
    highlight: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
    extra: @Composable () -> Unit = {},
    buttons: @Composable () -> Unit,
    links: List<Link>,
    emptyTitle: String,
    emptyText: String?,
    onLinkClick: (String) -> Unit,
    onFavorite: ((Link) -> Unit)?,
) {
    Column(Modifier.fillMaxSize().background(Sable).verticalScroll(rememberScrollState()).padding(bottom = 40.dp)) {
        if (folder != null) {
            // Dossier : bannière (maquette « Bannières illustrées »), icône à cheval
            val cd = FolderStyle.countdown(folder)
            Box(Modifier.fillMaxWidth().height(184.dp)) {
                FolderCover(folder, links)
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 20.dp, end = 20.dp, top = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    CircleIconButton(Icons.Outlined.ChevronLeft, "Retour", onBack, background = PhotoVeil)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), content = actions)
                }
                if (cd != null) {
                    val vac = folder.keyDateType == "vacances"
                    Text(
                        cd.label, color = if (vac) Sable else Color.White, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 40.dp)
                            .clip(RoundedCornerShape(10.dp)).background(if (vac) Ecorce else solid).padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
            Column(
                Modifier.pullUp(28.dp).background(Sable, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        Modifier.pullUp(20.dp).size(64.dp).clip(RoundedCornerShape(20.dp)).background(Sable).padding(4.dp)
                            .clip(RoundedCornerShape(16.dp)).background(solid),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(icon, null, tint = Color.White, modifier = Modifier.size(30.dp))
                    }
                    val kids = folder.childIds.mapNotNull { id -> children.find { it.id == id } }
                    if (kids.isNotEmpty()) {
                        Row(Modifier.padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Pour", fontSize = 12.sp, color = TextSec)
                            ChildAvatars(folder, children, ring = Sable)
                            Text(kids.joinToString(", ") { it.name }, fontSize = 12.sp, color = TextSec, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, style = display(28.sp))
                    if (folder.description.isNotBlank()) Text(folder.description, fontSize = 15.sp, color = Ecorce2)
                    Text(subtitle, fontSize = 14.sp, color = TextSec)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { extra() }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { buttons() }
                Ideas(links, folder.viewMode, emptyTitle, emptyText, onLinkClick, onFavorite, highlight, solid)
            }
            return@Column
        }
        Box(Modifier.fillMaxWidth().background(bg)) {
            Box(Modifier.align(Alignment.BottomEnd).offset(x = 96.dp, y = 160.dp).size(320.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.45f)))
            Column(Modifier.statusBarsPadding().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    CircleIconButton(Icons.Outlined.ChevronLeft, "Retour", onBack, background = PhotoVeil)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), content = actions)
                }
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(Modifier.size(64.dp).clip(RoundedCornerShape(22.dp)).background(solid), contentAlignment = Alignment.Center) {
                        Icon(icon, null, tint = Color.White, modifier = Modifier.size(30.dp))
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(title, style = display(28.sp))
                        Text(subtitle, fontSize = 14.sp, color = Ecorce2)
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { extra() }
            }
        }
        Column(
            Modifier.offset(y = (-16).dp).clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(Sable).padding(start = 20.dp, end = 20.dp, top = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { buttons() }
            Ideas(links, "grid", emptyTitle, emptyText, onLinkClick, onFavorite)
        }
    }
}

/** Idées d'un dossier dans la vue choisie : grille (mosaïque de cartes), liste ou mosaïque de photos. */
@Composable
private fun Ideas(
    links: List<Link>,
    viewMode: String,
    emptyTitle: String,
    emptyText: String?,
    onLinkClick: (String) -> Unit,
    onFavorite: ((Link) -> Unit)?,
    highlight: String? = null,
    highlightColor: Color = Terracotta,
) {
    // Idée d'où l'on vient (dossier ouvert depuis sa fiche) : surlignée pour garder le fil
    fun Modifier.mark(l: Link, radius: Int = 22): Modifier =
        if (l.id == highlight) border(2.dp, highlightColor, RoundedCornerShape((radius + 3).dp)).padding(3.dp) else this
    if (links.isEmpty()) {
        EmptyBlock(Icons.Outlined.Cottage, emptyTitle, emptyText)
        return
    }
    when (viewMode) {
        "list" -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            links.forEach { l -> Box(Modifier.mark(l, 18)) { RowCard(l, onClick = { onLinkClick(l.id) }) } }
        }
        "mosaic" -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Par trois : une grande tuile et deux petites empilées, en alternant les côtés
            links.chunked(3).forEachIndexed { row, group ->
                Row(Modifier.fillMaxWidth().height(276.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val big: @Composable RowScope.() -> Unit = { MosaicTile(group[0], true, { onLinkClick(group[0].id) }, Modifier.weight(1f).fillMaxHeight().mark(group[0], 20)) }
                    val small: @Composable RowScope.() -> Unit = {
                        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            group.drop(1).forEach { l -> MosaicTile(l, false, { onLinkClick(l.id) }, Modifier.fillMaxWidth().weight(1f).mark(l, 20)) }
                            if (group.size < 3) Spacer(Modifier.weight((3 - group.size).toFloat()))
                        }
                    }
                    if (row % 2 == 0) { big(); small() } else { small(); big() }
                }
            }
        }
        else -> TwoColumnMosaic(links) { l, i ->
            Box(Modifier.mark(l, 20)) {
                MosaicCard(l, i, { onLinkClick(l.id) }, liked = l.favorite, onLike = onFavorite?.let { fav -> { fav(l) } })
            }
        }
    }
}

/** Tuile de la vue « Mosaïque » : visuel plein, catégorie, titre en surimpression. */
@Composable
private fun MosaicTile(link: Link, large: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val cat = categoryUi(link.category)
    Box(modifier.card(RoundedCornerShape(20.dp)).clickable(onClick = onClick)) {
        IdeaVisual(link, 400.dp, Modifier.fillMaxSize(), iconSize = if (large) 56.dp else 36.dp)
        Text(
            cat.label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = cat.tint,
            modifier = Modifier.padding(10.dp).clip(CircleShape).background(CardColor).padding(horizontal = 10.dp, vertical = 4.dp)
        )
        Box(
            Modifier.align(Alignment.BottomStart).fillMaxWidth()
                .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent, Color(0xC72A1612))))
                .padding(start = 12.dp, end = 12.dp, top = 28.dp, bottom = 10.dp)
        ) {
            Text(link.title, style = display(if (large) 17.sp else 14.sp, FontWeight.Bold, Color.White), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}
