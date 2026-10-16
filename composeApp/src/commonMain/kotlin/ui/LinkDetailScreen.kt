package ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import data.Comment
import data.CommentRepository
import data.FollowRepository
import data.Link
import data.SessionManager
import kotlinx.coroutines.launch
import ui.campfire.*
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** Fiche idée « feu de camp » : visuel, infos, rappel, ingrédients, commentaires, barre d'actions. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinkDetailScreen(
    link: Link,
    onBack: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit = {},
    onOpenUrl: ((String) -> Unit)? = null,
    readOnly: Boolean = false,
    onSaveToMyList: ((Link, String?) -> Unit)? = null,
    folders: List<data.Folder> = emptyList(),
    followRepository: FollowRepository? = null,
    sessionManager: SessionManager? = null,
    commentRepository: CommentRepository? = null,
    viewModel: viewmodel.LinkViewModel? = null
) {
    val cat = categoryUi(link.category)
    val scope = rememberCoroutineScope()
    val scroll = rememberScrollState()
    val context = LocalContext.current
    val myId = sessionManager?.getUserId()
    val isLoggedIn = sessionManager?.hasToken() == true

    var liked by remember(link.id) { mutableStateOf(link.likedByMe) }
    var likeCount by remember(link.id) { mutableStateOf(link.likeCount) }
    var isFollowing by remember { mutableStateOf<Boolean?>(null) }
    var comments by remember { mutableStateOf<List<Comment>>(emptyList()) }
    var commentsLoading by remember { mutableStateOf(commentRepository != null) }
    var commentsY by remember { mutableStateOf(0) }
    var showMenu by remember { mutableStateOf(false) }
    var showSave by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var savedCopy by remember { mutableStateOf(false) }

    LaunchedEffect(link.ownerId) {
        if (followRepository != null && isLoggedIn && link.ownerId.isNotEmpty() && myId != link.ownerId) {
            followRepository.isFollowing(link.ownerId).onSuccess { isFollowing = it }
        }
    }
    LaunchedEffect(link.id) {
        commentRepository?.getComments(link.id)?.onSuccess { comments = it }
        commentsLoading = false
    }

    val folder = folders.find { it.id == link.folderId }
    val at = eventMs(link)
    val infos = buildList<Triple<ImageVector, String, String>> {
        if (at != null) add(Triple(Icons.Outlined.CalendarMonth, "Quand", listOf(formatShortDate(at), formatTime(at)).filter { it.isNotEmpty() }.joinToString(" · ")))
        if (link.ageRange.isNotBlank()) add(Triple(Icons.Outlined.Person, "Âge", link.ageRange))
        if (link.location.isNotBlank()) add(Triple(Icons.Outlined.Place, "Où", link.location))
        if (link.price.isNotBlank()) add(Triple(Icons.Outlined.Euro, "Prix", link.price))
    }

    val share = {
        val body = listOf(link.title, link.url.ifBlank { if (link.visibility == "public") "https://tribbae.bananaops.cloud/links/${link.id}" else "" })
            .filter { it.isNotBlank() }.joinToString("\n")
        context.startActivity(
            Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, body), "Partager l'idée")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    Box(Modifier.fillMaxSize().background(Sable)) {
        Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(bottom = 110.dp)) {
            // ── Visuel ──
            Box(Modifier.fillMaxWidth().height(300.dp).background(cat.bg)) {
                if (link.imageUrl.isNotBlank()) {
                    AsyncImage(link.imageUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Box(Modifier.align(Alignment.BottomCenter).offset(y = 120.dp).size(320.dp).clip(CircleShape).background(Soleil))
                    TipiMark(150.dp, stroke = 1.6f, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp))
                }
                Row(
                    Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    CircleIconButton(Icons.Outlined.ChevronLeft, "Retour", onBack, background = Color.White.copy(alpha = 0.92f))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircleIconButton(Icons.Outlined.IosShare, "Partager", share, background = Color.White.copy(alpha = 0.92f))
                        CircleIconButton(Icons.Outlined.MoreHoriz, "Plus d'actions", { showMenu = true }, background = Color.White.copy(alpha = 0.92f))
                    }
                }
            }

            // ── Contenu ──
            Column(
                Modifier
                    .offset(y = (-28).dp)
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(Sable)
                    .padding(start = 20.dp, end = 20.dp, top = 22.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        cat.label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = cat.ink,
                        modifier = Modifier.clip(CircleShape).background(cat.bg).padding(horizontal = 12.dp, vertical = 5.dp)
                    )
                    val by = when {
                        !readOnly -> "par vous"
                        link.ownerDisplayName.isNotBlank() -> "par ${link.ownerDisplayName}"
                        else -> ""
                    }
                    Text(
                        listOf(by, folder?.let { "dossier ${it.name}" }.orEmpty()).filter { it.isNotEmpty() }.joinToString(" · "),
                        fontSize = 13.sp, color = TextSec, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false)
                    )
                    if (link.favorite) Icon(Icons.Filled.Star, "Favori", tint = Braise, modifier = Modifier.size(16.dp))
                    if (link.visibility == "public") Icon(Icons.Outlined.Public, "Public", tint = TextSec, modifier = Modifier.size(16.dp))
                }

                Text(link.title, style = display(28.sp))

                if (readOnly && isFollowing != null && link.ownerId.isNotEmpty()) {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White).padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        InitialAvatar(link.ownerDisplayName, 0, 36.dp, 12.dp, 15.sp)
                        Text(link.ownerDisplayName.ifBlank { "Une tribu" }, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Ecorce, modifier = Modifier.weight(1f))
                        CampButton(
                            if (isFollowing == true) "Suivie" else "Suivre",
                            {
                                val now = isFollowing == true
                                isFollowing = !now
                                scope.launch {
                                    val r = if (now) followRepository!!.unfollow(link.ownerId) else followRepository!!.follow(link.ownerId)
                                    if (r.isFailure) isFollowing = now
                                }
                            },
                            kind = if (isFollowing == true) ButtonKind.Ghost else ButtonKind.Dark,
                            height = 40.dp
                        )
                    }
                }

                if (infos.isNotEmpty()) {
                    infos.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { (icon, label, value) -> InfoTile(icon, label, value, cat.tint, Modifier.weight(1f)) }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }

                if (at != null && !readOnly && viewModel != null) {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Soleil).padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Outlined.NotificationsNone, null, tint = Ecorce, modifier = Modifier.size(20.dp))
                        Text("Me le rappeler la veille", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Ecorce, modifier = Modifier.weight(1f))
                        CampSwitch(link.reminderEnabled, { viewModel.updateLink(link.copy(reminderEnabled = it)) }, "Rappel la veille")
                    }
                }

                if (link.rating > 0) {
                    Row(Modifier.semantics { contentDescription = "Note : ${link.rating} sur 5" }) {
                        repeat(5) { i ->
                            Icon(if (i < link.rating) Icons.Filled.Star else Icons.Outlined.StarBorder, null, tint = Braise, modifier = Modifier.size(20.dp))
                        }
                    }
                }

                if (link.description.isNotBlank()) {
                    Text(link.description, fontSize = 15.sp, lineHeight = 22.sp, color = Ecorce2)
                }

                if (link.ingredients.isNotEmpty()) {
                    val rc = categoryUi(data.LinkCategory.RECETTE)
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color.White).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Outlined.ShoppingCart, null, tint = rc.tint, modifier = Modifier.size(20.dp))
                            Text("Ingrédients", style = display(18.sp, FontWeight.Bold))
                        }
                        link.ingredients.forEach { ing ->
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(6.dp).clip(CircleShape).background(rc.tint))
                                Text(ing, fontSize = 15.sp, color = Ecorce2)
                            }
                        }
                    }
                }

                if (link.tags.isNotEmpty()) {
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        link.tags.forEach { t ->
                            Text("#$t", fontSize = 13.sp, color = TextSec, modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Sable2).padding(horizontal = 10.dp, vertical = 5.dp))
                        }
                    }
                }

                if (readOnly && onSaveToMyList != null) {
                    CampButton(
                        if (savedCopy) "Enregistrée dans mes idées" else "Enregistrer dans mes idées",
                        { if (!savedCopy) showSave = true },
                        kind = ButtonKind.Secondary,
                        icon = if (savedCopy) Icons.Outlined.Check else Icons.Outlined.BookmarkBorder
                    )
                }

                // ── Commentaires ──
                Column(
                    Modifier.fillMaxWidth().padding(top = 8.dp).onGloballyPositioned { commentsY = it.positionInParent().y.toInt() },
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Commentaires" + if (comments.isNotEmpty()) " (${comments.size})" else "", style = display(20.sp, FontWeight.Bold))
                    when {
                        commentsLoading -> CircularProgressIndicator(color = Terracotta, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        comments.isEmpty() -> Text("Pas encore de commentaire. Une astuce, un retour d'expérience ?", fontSize = 14.sp, color = TextSec)
                        else -> comments.forEachIndexed { i, c ->
                            CommentBubble(c, i, canDelete = c.userId == myId || (!readOnly)) {
                                scope.launch {
                                    commentRepository?.deleteComment(c.id)?.onSuccess { comments = comments.filterNot { it.id == c.id } }
                                }
                            }
                        }
                    }
                    if (commentRepository != null && isLoggedIn) {
                        CommentInput { text ->
                            scope.launch {
                                commentRepository.createComment(link.id, text).onSuccess { comments = comments + it }
                            }
                        }
                    } else if (!isLoggedIn) {
                        Text("Connectez-vous dans « Ma tribu » pour commenter.", fontSize = 13.sp, color = TextSec)
                    }
                }
            }
        }

        // ── Barre d'actions ──
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Sable)
                .drawBehind { drawLine(Line, androidx.compose.ui.geometry.Offset(0f, 0f), androidx.compose.ui.geometry.Offset(size.width, 0f), 1.dp.toPx()) }
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ActionChip(
                if (liked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder, "$likeCount", Terracotta,
                if (liked) "Je n'aime plus, $likeCount" else "J'aime, $likeCount"
            ) {
                if (viewModel == null || !isLoggedIn) return@ActionChip
                val was = liked
                liked = !was
                likeCount += if (was) -1 else 1
                viewModel.toggleLike(link.id, was) { l, c -> liked = l; likeCount = c }
            }
            ActionChip(Icons.Outlined.ChatBubbleOutline, "${comments.size}", Ecorce, "Commentaires, ${comments.size}") {
                scope.launch { scroll.animateScrollTo(commentsY) }
            }
            when {
                link.url.isNotBlank() -> CampButton(
                    "Voir le site", { onOpenUrl?.invoke(link.url) }, Modifier.weight(1f), trailingIcon = Icons.Outlined.ArrowOutward, height = 54.dp
                )
                !readOnly -> CampButton("Modifier", onEdit, Modifier.weight(1f), icon = Icons.Outlined.Edit, height = 54.dp)
                onSaveToMyList != null -> CampButton("Enregistrer", { showSave = true }, Modifier.weight(1f), icon = Icons.Outlined.BookmarkBorder, height = 54.dp)
            }
        }
    }

    // ── Menu « … » ──
    if (showMenu) {
        ModalBottomSheet(onDismissRequest = { showMenu = false }, containerColor = Sable, dragHandle = { SheetHandle() }) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Idée", style = display(24.sp), modifier = Modifier.padding(bottom = 8.dp))
                if (!readOnly) {
                    MenuRow(Icons.Outlined.Edit, "Modifier") { showMenu = false; onEdit() }
                    MenuRow(if (link.favorite) Icons.Filled.Star else Icons.Outlined.StarBorder, if (link.favorite) "Retirer des favoris" else "Ajouter aux favoris") {
                        showMenu = false; viewModel?.toggleFavorite(link.id)
                    }
                    MenuRow(
                        if (link.visibility == "public") Icons.Outlined.Lock else Icons.Outlined.Public,
                        if (link.visibility == "public") "Ne plus partager avec la communauté" else "Partager avec la communauté"
                    ) {
                        showMenu = false
                        viewModel?.updateLinkVisibility(link.id, if (link.visibility == "public") "private" else "public")
                    }
                } else if (onSaveToMyList != null) {
                    MenuRow(Icons.Outlined.BookmarkBorder, "Enregistrer dans mes idées") { showMenu = false; showSave = true }
                }
                MenuRow(Icons.Outlined.IosShare, "Partager") { showMenu = false; share() }
                if (!readOnly) MenuRow(Icons.Outlined.Delete, "Supprimer", danger = true) { showMenu = false; showDelete = true }
            }
        }
    }

    // ── Enregistrer une idée de la communauté ──
    if (showSave && onSaveToMyList != null) {
        ModalBottomSheet(onDismissRequest = { showSave = false }, containerColor = Sable, dragHandle = { SheetHandle() }) {
            Column(
                Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Ranger dans…", style = display(24.sp), modifier = Modifier.padding(bottom = 8.dp))
                MenuRow(Icons.Outlined.BookmarkBorder, "Mes idées (sans dossier)") {
                    onSaveToMyList(link, null); savedCopy = true; showSave = false
                }
                folders.forEach { f ->
                    MenuRow(folderIconVector(f), f.name, tint = folderSolid(f.color)) {
                        onSaveToMyList(link, f.id); savedCopy = true; showSave = false
                    }
                }
            }
        }
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            containerColor = Sable,
            title = { Text("Supprimer cette idée ?", style = display(20.sp, FontWeight.Bold)) },
            text = { Text("« ${link.title} » sera définitivement supprimée.", color = Ecorce2) },
            confirmButton = {
                TextButton(onClick = { showDelete = false; onDelete() }) { Text("Supprimer", color = Terracotta, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text("Annuler", color = Ecorce) } }
        )
    }
}

@Composable
private fun InfoTile(icon: ImageVector, label: String, value: String, tint: Color, modifier: Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(16.dp)).background(Color.White).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        Column {
            Text(label, fontSize = 12.sp, color = TextSec)
            Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Ecorce, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ActionChip(icon: ImageVector, text: String, tint: Color, label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .height(54.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White)
            .border(ThinBorder, RoundedCornerShape(18.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = label }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        Text(text, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = tint)
    }
}

@Composable
fun MenuRow(icon: ImageVector, label: String, danger: Boolean = false, tint: Color? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(18.dp)).background(Color.White).clickable(onClick = onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val c = if (danger) Color(0xFF9A3420) else Ecorce
        Icon(icon, null, tint = tint ?: c, modifier = Modifier.size(20.dp))
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c)
    }
}

@Composable
private fun CommentBubble(c: Comment, index: Int, canDelete: Boolean, onDelete: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        InitialAvatar(c.userDisplayName, index, 34.dp, 12.dp, 14.sp)
        Column(
            Modifier.weight(1f).clip(RoundedCornerShape(topStart = 6.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 16.dp)).background(Color.White).padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(c.userDisplayName.ifBlank { "Membre" }, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Ecorce)
                Text(relativeTime(c.createdAt), fontSize = 12.sp, color = TextMuted, modifier = Modifier.weight(1f))
                if (canDelete) {
                    Icon(
                        Icons.Outlined.Delete, "Supprimer le commentaire", tint = TextMuted,
                        modifier = Modifier.size(18.dp).clip(CircleShape).clickable(onClick = onDelete)
                    )
                }
            }
            Text(c.text, fontSize = 14.sp, lineHeight = 20.sp, color = Ecorce2)
        }
    }
}

@Composable
private fun CommentInput(onSend: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier.weight(1f).heightIn(min = 48.dp).clip(RoundedCornerShape(18.dp)).background(Color.White)
                .border(ThinBorder, RoundedCornerShape(18.dp)).padding(horizontal = 16.dp, vertical = 13.dp)
        ) {
            if (text.isEmpty()) Text("Ajouter un commentaire…", color = TextMuted, fontSize = 15.sp)
            BasicTextField(
                value = text,
                onValueChange = { if (it.length <= 1000) text = it },
                textStyle = TextStyle(fontSize = 15.sp, color = Ecorce),
                cursorBrush = SolidColor(Terracotta),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Votre commentaire" }
            )
        }
        Box(
            Modifier.size(48.dp).clip(RoundedCornerShape(18.dp)).background(if (text.isBlank()) Dash else Ecorce)
                .clickable(enabled = text.isNotBlank(), role = Role.Button) { onSend(text.trim()); text = "" }
                .semantics { contentDescription = "Envoyer" },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.Send, null, tint = Sable, modifier = Modifier.size(20.dp))
        }
    }
}

/** « il y a 5 min », « hier », « 12 oct. » à partir d'un horodatage ISO 8601. */
fun relativeTime(iso: String): String {
    if (iso.isBlank()) return ""
    val ms = runCatching {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(iso.take(19))!!.time
    }.getOrNull() ?: return ""
    val diff = (System.currentTimeMillis() - ms) / 1000
    return when {
        diff < 60 -> "à l'instant"
        diff < 3600 -> "il y a ${diff / 60} min"
        diff < 86400 -> "il y a ${diff / 3600} h"
        diff < 172800 -> "hier"
        else -> SimpleDateFormat("d MMM", Locale.FRENCH).format(java.util.Date(ms))
    }
}
