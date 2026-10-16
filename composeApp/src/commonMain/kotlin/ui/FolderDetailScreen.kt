package ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import data.Folder
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
) {
    val items = remember(links, folder.id) { links.filter { it.folderId == folder.id }.sortedByDescending { it.createdAt.ifEmpty { it.updatedAt } } }
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
        actions = {
            if (viewModel != null) CircleIconButton(Icons.Outlined.PersonAdd, "Inviter et partager", { showShare = true }, background = Color.White.copy(alpha = 0.9f))
            CircleIconButton(Icons.Outlined.MoreHoriz, "Plus d'actions", { showMenu = true }, background = Color.White.copy(alpha = 0.9f))
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
        },
        links = items,
        emptyTitle = "Ce dossier attend ses premières idées",
        emptyText = "Ajoutez un lien, une recette, une sortie… ou laissez l'IA le remplir.",
        onLinkClick = onLinkClick,
        onFavorite = onSaveLink
    )

    if (showMenu) {
        ModalBottomSheet(onDismissRequest = { showMenu = false }, containerColor = Sable, dragHandle = { SheetHandle() }) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Dossier", style = display(24.sp), modifier = Modifier.padding(bottom = 8.dp))
                MenuRow(Icons.Outlined.Edit, "Modifier le nom, la couleur…") { showMenu = false; onEdit() }
                if (viewModel != null) MenuRow(Icons.Outlined.PersonAdd, "Inviter et partager") { showMenu = false; showShare = true }
                MenuRow(Icons.Outlined.Delete, "Supprimer le dossier", danger = true) { showMenu = false; showDelete = true }
            }
        }
    }
    if (showShare && viewModel != null) ShareFolderSheet(folder, viewModel) { showShare = false }
    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            containerColor = Sable,
            title = { Text("Supprimer « ${folder.name} » ?", style = display(20.sp, FontWeight.Bold)) },
            text = { Text("Le dossier disparaît, ses idées sont conservées dans « Non rangées ».", color = Ecorce2) },
            confirmButton = { TextButton(onClick = { showDelete = false; onDelete() }) { Text("Supprimer", color = Terracotta, fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text("Annuler", color = Ecorce) } }
        )
    }
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
    actions: @Composable RowScope.() -> Unit = {},
    extra: @Composable () -> Unit = {},
    buttons: @Composable () -> Unit,
    links: List<Link>,
    emptyTitle: String,
    emptyText: String?,
    onLinkClick: (String) -> Unit,
    onFavorite: (Link) -> Unit,
) {
    Column(Modifier.fillMaxSize().background(Sable).verticalScroll(rememberScrollState()).padding(bottom = 40.dp)) {
        Box(Modifier.fillMaxWidth().background(bg)) {
            Box(Modifier.align(Alignment.BottomEnd).offset(x = 96.dp, y = 160.dp).size(320.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.45f)))
            Column(Modifier.statusBarsPadding().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    CircleIconButton(Icons.Outlined.ChevronLeft, "Retour", onBack, background = Color.White.copy(alpha = 0.9f))
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
            if (links.isEmpty()) {
                EmptyBlock(Icons.Outlined.Cottage, emptyTitle, emptyText)
            } else {
                TwoColumnMosaic(links) { l, i ->
                    MosaicCard(l, i, { onLinkClick(l.id) }, liked = l.favorite, onLike = { onFavorite(l) })
                }
            }
        }
    }
}
