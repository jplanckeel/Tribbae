package ui

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import data.AddFlow
import data.Folder
import data.Link
import data.LinkCategory
import data.LinkPreview
import data.AgeRanges
import data.extractUrl
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ui.campfire.*
import viewmodel.LinkViewModel
import java.text.SimpleDateFormat
import java.util.Locale

// Parcours « Ajouter une idée » (maquette « Ajouter une idée v2 »), comme le web
// (frontend/src/components/AddFlow.tsx) : point de départ → (lien lu) → ranger → c'est rangé !

private enum class AddStep { Choose, Link, Sort, Done }
private enum class Mode { Link, Write, Photo }
private enum class LinkRead { Idle, Loading, Done, Failed }

/** Brouillon de l'idée en cours d'ajout. */
private data class Draft(
    val title: String = "", val url: String = "", val description: String = "",
    val category: LinkCategory = LinkCategory.IDEE, val imageUrl: String = "",
    val price: String = "", val location: String = "", val eventDate: Long? = null,
    val ingredients: List<String> = emptyList(), val ageRange: String = "", val isPublic: Boolean = false,
)

/** Brouillon passé au formulaire complet (« Plus de détails »). */
data class AddDraft(val url: String?, val title: String?, val category: LinkCategory, val folderId: String?)

private val SPRING = spring<Float>(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddFlowSheet(
    viewModel: LinkViewModel,
    presetFolderId: String?,
    onDismiss: () -> Unit,
    onAi: () -> Unit,
    onMore: (AddDraft) -> Unit,
    onNewFolder: () -> Unit,
    onOpenLink: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val links by viewModel.repository.links.collectAsState()
    val folders by viewModel.folders.collectAsState()
    val me = viewModel.currentUserId()

    var step by remember { mutableStateOf(AddStep.Choose) }
    var mode by remember { mutableStateOf(Mode.Link) }
    var draft by remember { mutableStateOf(Draft()) }
    var catChosen by remember { mutableStateOf(false) }
    var folderId by remember { mutableStateOf(presetFolderId) }
    var folderPicked by remember { mutableStateOf(presetFolderId != null) }
    var status by remember { mutableStateOf(LinkRead.Idle) }
    var guessed by remember { mutableStateOf(false) }
    var requestId by remember { mutableIntStateOf(0) }
    var picker by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var created by remember { mutableStateOf<Link?>(null) }

    // « Lien copié à l'instant »
    val clip = remember {
        runCatching {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            extractUrl(cm.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString())
        }.getOrNull()
    }

    // Un dossier en lecture seule (invité en lecteur) ne reçoit pas d'idées
    val editable = remember(folders, me) {
        folders.filter { f -> f.collaborators.none { it.userId == me && it.role == "COLLABORATOR_ROLE_VIEWER" } }
    }
    val suggestions = remember(draft.category, editable, links) { AddFlow.suggestFolders(draft.category, editable, links, presetFolderId) }
    val folder = folders.find { it.id == folderId }
    val recent = remember(links, me) {
        links.filter { it.ownerId.isNotBlank() && it.ownerId != me && viewModel.isTribePeer(it.ownerId) }.maxByOrNull { it.createdAt }
    }

    fun readLink(raw: String) {
        val url = extractUrl(raw) ?: run { status = LinkRead.Idle; return }
        draft = draft.copy(url = url)
        status = LinkRead.Loading
        val id = ++requestId
        scope.launch {
            val p: LinkPreview? = viewModel.previewLink(url)
            if (id != requestId) return@launch
            if (p == null) { status = LinkRead.Failed; return@launch }
            guessed = p.suggestedCategory != null
            draft = draft.copy(
                title = p.title.ifBlank { draft.title }, description = p.description.ifBlank { draft.description },
                imageUrl = p.image.ifBlank { draft.imageUrl }, price = p.price.ifBlank { draft.price },
                location = p.location.ifBlank { draft.location }, eventDate = p.eventDate.takeIf { it > 0 } ?: draft.eventDate,
                ingredients = p.ingredients.ifEmpty { draft.ingredients },
                category = if (!catChosen) p.suggestedCategory ?: draft.category else draft.category,
            )
            status = LinkRead.Done
        }
    }

    fun toSort() {
        // Premier passage : le dossier le mieux placé est présélectionné
        if (!folderPicked) { folderId = suggestions.firstOrNull()?.folder?.id; folderPicked = true }
        step = AddStep.Sort
    }

    fun start(m: Mode, url: String? = null) {
        mode = m; error = null
        if (m == Mode.Link) { step = AddStep.Link; url?.let(::readLink) } else toSort()
    }

    fun submit() {
        val title = draft.title.ifBlank { runCatching { java.net.URI(draft.url).host?.removePrefix("www.") }.getOrNull().orEmpty() }.trim()
        if (title.isEmpty()) { error = "Donnez un titre à votre idée."; return }
        created = viewModel.addLink(
            title = title, url = draft.url, description = draft.description, category = draft.category,
            folderId = folderId, tags = emptyList(), ageRange = draft.ageRange, location = draft.location, price = draft.price,
            eventDate = draft.eventDate, imageUrl = draft.imageUrl, ingredients = draft.ingredients,
            visibility = if (draft.isPublic) "public" else "private",
        )
        step = AddStep.Done
    }

    fun again() {
        draft = Draft(); catChosen = false; folderId = presetFolderId; folderPicked = presetFolderId != null
        status = LinkRead.Idle; created = null; error = null; step = AddStep.Choose
    }

    // L'idée créée change d'identifiant une fois enregistrée sur le serveur
    val current = created?.let { c -> links.find { it.id == c.id } ?: links.filter { it.title == c.title && it.url == c.url }.lastOrNull() ?: c }

    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = SheetColor, dragHandle = { SheetHandle() }) {
        Column(Modifier.fillMaxWidth().heightIn(max = 720.dp).fillMaxHeight(0.92f)) {
            // En-tête : retour, étapes (tipis), fermer
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                if (step == AddStep.Link || step == AddStep.Sort) {
                    CircleIconButton(Icons.Outlined.ChevronLeft, "Étape précédente", { step = if (step == AddStep.Sort && mode == Mode.Link) AddStep.Link else AddStep.Choose }, background = Sable2)
                } else Spacer(Modifier.size(44.dp))
                StepDots(step, mode)
                CircleIconButton(Icons.Outlined.Close, "Fermer", onDismiss, background = Sable2)
            }

            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    if (targetState == AddStep.Choose || targetState == AddStep.Done) fadeIn(tween(250)) togetherWith fadeOut(tween(150))
                    else (slideInHorizontally(tween(450, easing = EaseSoft)) { (it * 0.07f).toInt() } + fadeIn(tween(300))) togetherWith fadeOut(tween(150))
                },
                modifier = Modifier.weight(1f),
                label = "étapes",
            ) { s ->
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    when (s) {
                        AddStep.Choose -> ChooseStep(clip, recent, ::start, onAi, onNewFolder, onOpenLink)
                        AddStep.Link -> LinkStep(draft, status, guessed, ::readLink)
                        AddStep.Sort -> SortStep(
                            viewModel, draft, mode, suggestions, folder, folderId, folders.size, error,
                            onDraft = { draft = it; error = null },
                            onCategory = { draft = draft.copy(category = it); catChosen = true },
                            onFolder = { folderId = it },
                            onPicker = { picker = true },
                            onMore = { onMore(AddDraft(draft.url.ifBlank { null }, draft.title.ifBlank { null }, draft.category, folderId)) },
                        )
                        AddStep.Done -> current?.let { l ->
                            DoneStep(l, folders.find { it.id == l.folderId }) { viewModel.updateLink(l.copy(reminderEnabled = !l.reminderEnabled)) }
                        }
                    }
                }
            }

            // Bouton principal
            if (step != AddStep.Choose) {
                HorizontalDivider(color = Line)
                Row(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    when (step) {
                        AddStep.Link -> CampButton("Ranger cette idée", ::toSort, Modifier.fillMaxWidth(), trailingIcon = Icons.Outlined.ArrowForward, enabled = draft.url.isNotBlank(), height = 56.dp)
                        AddStep.Sort -> CampButton("Ajouter à la tribu", ::submit, Modifier.fillMaxWidth(), trailingIcon = Icons.Outlined.Check, height = 56.dp)
                        else -> {
                            CampButton("Une autre", ::again, kind = ButtonKind.Secondary, height = 56.dp)
                            CampButton("Voir l'idée", { current?.let { onOpenLink(it.id) } }, Modifier.weight(1f), kind = ButtonKind.Dark, trailingIcon = Icons.Outlined.ArrowForward, height = 56.dp)
                        }
                    }
                }
            }
        }
    }

    if (picker) {
        FolderPickerSheet(
            folders = folders, links = links, current = folderId, me = me,
            onDismiss = { picker = false },
            onPick = { folderId = it; picker = false },
            onCreate = { name -> viewModel.createFolderQuick(name) { id -> folderId = id; picker = false } },
        )
    }
}

// ── Morceaux ───────────────────────────────────────────────────────────────────

/** Apparition « up » de la maquette : monte de 18 dp avec un léger rebond, après `delayMs`. */
@Composable
private fun Modifier.appear(delayMs: Int = 0): Modifier {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) { delay(delayMs.toLong()); p.animateTo(1f, spring(0.62f, Spring.StiffnessLow)) }
    return graphicsLayer {
        alpha = p.value.coerceIn(0f, 1f)
        translationY = (1f - p.value) * 18.dp.toPx()
        val s = 0.96f + 0.04f * p.value
        scaleX = s; scaleY = s
    }
}

/** Éclat « pop » : 0.3 → 1.2 → 1. */
@Composable
private fun Modifier.pop(delayMs: Int = 0): Modifier {
    val p = remember { Animatable(0.3f) }
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(delayMs.toLong()); shown = true
        p.animateTo(1f, keyframes { durationMillis = 450; 1.2f at 270 using FastOutSlowInEasing })
    }
    return graphicsLayer { alpha = if (shown) 1f else 0f; scaleX = p.value; scaleY = p.value }
}

@Composable
private fun StepDots(step: AddStep, mode: Mode) {
    val order = listOf(AddStep.Choose, AddStep.Link, AddStep.Sort, AddStep.Done)
    val here = order.indexOf(step)
    Row(Modifier.semantics { contentDescription = "Étape ${here.coerceAtLeast(1)} sur 3" }, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
        listOf("Trouver" to AddStep.Link, "Ranger" to AddStep.Sort, "Rangé" to AddStep.Done).forEach { (label, at) ->
            val i = order.indexOf(at)
            val on = here == i
            val done = here > i || (at == AddStep.Link && mode != Mode.Link && here >= 2)
            val sc by animateFloatAsState(if (on) 1.25f else 1f, SPRING, label = "étape")
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Canvas(Modifier.size(22.dp, 20.dp).scale(sc)) {
                    val k = size.width / 22f
                    val tri = Path().apply { moveTo(11f * k, 2f * k); lineTo(20f * k, 18f * k); lineTo(2f * k, 18f * k); close() }
                    if (on || done) drawPath(tri, Color(0xFFD45A43))
                    drawPath(tri, if (on || done) Ecorce else Color(0xFFCDB8AA), style = Stroke(2.dp.toPx(), join = StrokeJoin.Round))
                }
                Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (on) Ecorce else TextMuted)
            }
        }
    }
}

@Composable
private fun ChooseStep(
    clip: String?, recent: Link?, start: (Mode, String?) -> Unit, onAi: () -> Unit, onNewFolder: () -> Unit, onOpenLink: (String) -> Unit,
) {
    Text("Une nouvelle idée\npour la tribu ? ✨", style = display(28.sp), modifier = Modifier.appear())

    if (clip != null) {
        Row(
            Modifier.appear(80).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(PeachTint).border(2.dp, Braise, RoundedCornerShape(20.dp))
                .clickable(role = Role.Button) { start(Mode.Link, clip) }.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(Modifier.size(46.dp).clip(RoundedCornerShape(15.dp)).background(Braise), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.ContentPaste, null, tint = Ecorce, modifier = Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Lien copié à l'instant", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PeachInk)
                Text(clip.removePrefix("https://").removePrefix("http://").removePrefix("www."), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Ecorce, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text("Utiliser", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Sable, modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Ecorce).padding(horizontal = 12.dp, vertical = 8.dp))
        }
    }

    Text("Ou partir de…", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextSec, modifier = Modifier.appear(140).padding(top = 4.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StartTile(Icons.Outlined.Link, BlueBg, BlueInk, "Un lien", 180, Modifier.weight(1f)) { start(Mode.Link, null) }
        StartTile(Icons.Outlined.Edit, HoneyBg, HoneyInk, "Je l'écris", 230, Modifier.weight(1f)) { start(Mode.Write, null) }
        StartTile(Icons.Outlined.PhotoCamera, GreenBg, GreenInk, "Une photo", 280, Modifier.weight(1f)) { start(Mode.Photo, null) }
    }

    Row(
        Modifier.appear(340).fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Prune).clickable(role = Role.Button, onClick = onAi).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(Modifier.size(46.dp).clip(RoundedCornerShape(15.dp)).background(Soleil), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.AutoAwesome, null, tint = Prune, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Pas d'idée ? On en trouve ensemble", style = display(17.sp, FontWeight.Bold, OnDark))
            Text("« Un samedi pluvieux avec un enfant de 6 ans »", fontSize = 13.sp, color = PruneSoft)
        }
    }

    if (recent != null) {
        Column(Modifier.appear(400).padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Ajoutée récemment par la tribu", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextSec)
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(CardColor).clickable { onOpenLink(recent.id) }.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                InitialAvatar(recent.ownerDisplayName, 3, 32.dp, 11.dp, 13.sp)
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(recent.ownerDisplayName.ifBlank { "La tribu" }) }
                        append(" · ${recent.title}")
                    },
                    fontSize = 13.sp, color = Ecorce, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                )
                Text(AddFlow.agoShort(recent.createdAt), fontSize = 12.sp, color = TextSec)
            }
        }
    }

    Text("Ou créer un dossier", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Terracotta, modifier = Modifier.appear(450).clickable(role = Role.Button, onClick = onNewFolder).padding(vertical = 4.dp))
}

@Composable
private fun StartTile(icon: ImageVector, bg: Color, ink: Color, label: String, delayMs: Int, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.appear(delayMs).height(112.dp).card(RoundedCornerShape(20.dp), 3.dp).clickable(role = Role.Button, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(bg), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = ink, modifier = Modifier.size(22.dp))
        }
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Ecorce)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LinkStep(draft: Draft, status: LinkRead, guessed: Boolean, readLink: (String) -> Unit) {
    var text by remember { mutableStateOf(draft.url.removePrefix("https://").removePrefix("http://")) }
    Text(if (status == LinkRead.Idle) "Collez le lien" else "On regarde ce lien…", style = display(26.sp))
    Row(
        Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(16.dp)).background(CardColor).border(2.dp, Terracotta, RoundedCornerShape(16.dp)).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(Icons.Outlined.Link, null, tint = Terracotta, modifier = Modifier.size(18.dp))
        Box(Modifier.weight(1f)) {
            if (text.isEmpty()) Text("Coller l'adresse d'une page", fontSize = 14.sp, color = TextMuted)
            BasicTextField(
                text, { v ->
                    // Un collage (texte qui grandit d'un coup) lance la lecture tout de suite
                    val pasted = v.length - text.length > 5
                    text = v
                    if (pasted && extractUrl(v) != null) readLink(v)
                },
                singleLine = true, textStyle = TextStyle(fontSize = 14.sp, color = Ecorce2), cursorBrush = SolidColor(Terracotta),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { readLink(text) }),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Adresse de la page" }
            )
        }
    }

    when (status) {
        LinkRead.Loading -> Row(Modifier.fillMaxWidth().card(RoundedCornerShape(22.dp), 4.dp).padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Shimmer(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)))
            Column(Modifier.weight(1f).padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Shimmer(Modifier.fillMaxWidth(0.8f).height(12.dp).clip(RoundedCornerShape(6.dp)))
                Shimmer(Modifier.fillMaxWidth(0.5f).height(12.dp).clip(RoundedCornerShape(6.dp)))
            }
        }
        LinkRead.Done, LinkRead.Failed -> {
            val cat = categoryUi(draft.category)
            Column(Modifier.appear().fillMaxWidth().card(RoundedCornerShape(22.dp), 6.dp)) {
                Box(Modifier.fillMaxWidth().height(120.dp).background(cat.bg)) {
                    if (draft.imageUrl.isNotBlank()) AsyncImage(draft.imageUrl, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    else {
                        Box(Modifier.align(Alignment.BottomCenter).offset(y = 90.dp).size(200.dp).clip(CircleShape).background(SunDisc))
                        TipiMark(70.dp, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp))
                    }
                }
                Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (status == LinkRead.Failed) {
                        Text("Impossible de lire cette page : on complète ensemble à l'étape suivante.", fontSize = 13.sp, color = TextSec)
                    } else {
                        Text(draft.title.ifBlank { draft.url }, style = display(18.sp, FontWeight.Bold), modifier = Modifier.appear(100))
                        if (draft.description.isNotBlank()) Text(draft.description, fontSize = 13.sp, lineHeight = 18.sp, color = TextSec, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.appear(170))
                    }
                }
            }
            if (status == LinkRead.Done) {
                val chips = buildList<Pair<String, ImageVector?>> {
                    draft.eventDate?.let { add(SimpleDateFormat("EEE d MMM · HH'h'mm", Locale.FRENCH).format(it).replace("·00h00", "").replace("h00", "h").replaceFirstChar { c -> c.uppercase() } to Icons.Outlined.CalendarMonth) }
                    if (draft.price.isNotBlank()) add(draft.price to null)
                    if (draft.location.isNotBlank()) add(draft.location to null)
                    if (draft.ingredients.isNotEmpty()) add("${draft.ingredients.size} ingrédients" to null)
                }
                if (guessed || chips.isNotEmpty()) {
                    Column(Modifier.appear(250), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Outlined.AutoAwesome, null, tint = Terracotta, modifier = Modifier.size(14.dp))
                            Text("Tribbae a trouvé tout seul", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextSec)
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (guessed) Chip(cat.label, null, cat.bg, cat.ink, Modifier.pop(330))
                            chips.forEachIndexed { i, (label, icon) -> Chip(label, icon, CardColor, Ecorce, Modifier.pop(410 + i * 80)) }
                        }
                    }
                }
            }
        }
        LinkRead.Idle -> {}
    }
}

@Composable
private fun Chip(label: String, icon: ImageVector?, bg: Color, fg: Color, modifier: Modifier) {
    Row(modifier.clip(RoundedCornerShape(12.dp)).background(bg).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (icon != null) Icon(icon, null, tint = fg, modifier = Modifier.size(14.dp))
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = fg)
    }
}

@Composable
private fun Shimmer(modifier: Modifier) {
    val t = remember { Animatable(0f) }
    LaunchedEffect(Unit) { while (true) { t.snapTo(0f); t.animateTo(1f, tween(1200)) } }
    Box(modifier.drawWithShimmer(t.value))
}

private fun Modifier.drawWithShimmer(t: Float): Modifier = this.then(
    Modifier.background(
        Brush.linearGradient(
            listOf(Sable2, Sable, Sable2),
            start = Offset(-600f + 1200f * t, 0f), end = Offset(-200f + 1200f * t, 0f)
        )
    )
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SortStep(
    viewModel: LinkViewModel, draft: Draft, mode: Mode, suggestions: List<AddFlow.Suggestion>, folder: Folder?, folderId: String?,
    folderTotal: Int, error: String?,
    onDraft: (Draft) -> Unit, onCategory: (LinkCategory) -> Unit, onFolder: (String?) -> Unit, onPicker: () -> Unit, onMore: () -> Unit,
) {
    Text("Où on la range ?", style = display(26.sp))

    if (mode != Mode.Link || draft.title.isBlank()) {
        CampField("Le titre", draft.title, { onDraft(draft.copy(title = it)) }, placeholder = "Cabane dans les bois", maxLength = 200)
        if (mode == Mode.Photo) ImagePickerSection(imageUrl = draft.imageUrl, viewModel = viewModel, onImageSelected = { onDraft(draft.copy(imageUrl = it)) })
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Dans quel dossier", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextSec)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Outlined.AutoAwesome, null, tint = Terracotta, modifier = Modifier.size(12.dp))
                val label = categoryUi(draft.category).label.lowercase()
                Text("suggérés pour ${if (draft.category == LinkCategory.CADEAU || draft.category == LinkCategory.LIVRE) "un" else "une"} $label", fontSize = 12.sp, color = TextMuted)
            }
        }
        val shown = (if (folder != null && suggestions.none { it.folder.id == folder.id }) listOf(AddFlow.Suggestion(folder, "Choisi dans la liste")) else emptyList()) + suggestions
        shown.take(3).forEachIndexed { i, s -> FolderChoice(s.folder, s.reason, folderId == s.folder.id, i * 60) { onFolder(s.folder.id) } }
        Row(
            Modifier.appear(200).fillMaxWidth().height(48.dp).clip(RoundedCornerShape(16.dp)).dashedBorder(Color(0xFFD9C2AE), 16.dp)
                .clickable(role = Role.Button, onClick = onPicker).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Outlined.Search, null, tint = TextSec, modifier = Modifier.size(18.dp))
            Text((if (folderId == null && folder == null) "Sans dossier · " else "") + "Chercher parmi mes $folderTotal dossiers", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Ecorce, modifier = Modifier.weight(1f))
            Icon(Icons.Outlined.ChevronRight, null, tint = TextMuted, modifier = Modifier.size(18.dp))
        }
        val names = folder?.collaborators.orEmpty().map { it.displayName.ifBlank { it.email } }
        val bg by animateColorAsState(if (names.isNotEmpty()) PeachTint else Sable2, label = "qui")
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(bg).padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (names.isNotEmpty()) {
                AvatarStack(names)
                Spacer(Modifier.width(4.dp))
            }
            Text(AddFlow.whoSees(folder, draft.isPublic), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Ecorce)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("C'est plutôt…", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextSec)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CategoryOrder.forEach { c ->
                val on = draft.category == c.category
                val sc by animateFloatAsState(if (on) 1.05f else 1f, SPRING, label = "catégorie")
                Row(
                    Modifier.scale(sc).height(40.dp).clip(RoundedCornerShape(13.dp)).background(if (on) Ecorce else c.bg)
                        .clickable(role = Role.RadioButton) { onCategory(c.category) }.semantics { selected = on }.padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(c.icon, null, tint = if (on) Sable else c.ink, modifier = Modifier.size(16.dp))
                    Text(c.label, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (on) Sable else c.ink)
                }
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Pour quels âges", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextSec)
        val sel = AddFlow.selectedBands(draft.ageRange)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AgeRanges.BANDS.forEach { (key, _) ->
                val on = key in sel
                Box(
                    Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(13.dp)).background(if (on) Blush else Color.Transparent)
                        .border(2.dp, if (on) Terracotta else Line, RoundedCornerShape(13.dp))
                        .clickable(role = Role.Checkbox) { onDraft(draft.copy(ageRange = AddFlow.bandsToAge(if (on) sel - key else sel + key))) }
                        .semantics { selected = on },
                    contentAlignment = Alignment.Center
                ) { Text(key, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (on) Color(0xFF9E3520) else Ecorce) }
            }
        }
    }

    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(CardColor).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val canPublish by viewModel.emailVerified.collectAsState()
        Icon(Icons.Outlined.Public, null, tint = BlueInk, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f)) {
            Text("Inspirer d'autres familles", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Ecorce)
            Text("Visible aussi dans Explorer", fontSize = 12.sp, color = TextSec)
            if (!canPublish) PublishLockedNote(Modifier.padding(top = 4.dp))
        }
        // Publier attend la confirmation de l'adresse ; dépublier reste toujours possible
        CampSwitch(draft.isPublic, { if (!it || canPublish) onDraft(draft.copy(isPublic = it)) }, "Inspirer d'autres familles")
    }

    Text("Plus de détails (date, prix, lieu, tags…)", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Terracotta, modifier = Modifier.clickable(role = Role.Button, onClick = onMore).padding(vertical = 4.dp))
    error?.let { Text(it, color = CoralInk, fontSize = 14.sp) }
}

@Composable
private fun FolderChoice(folder: Folder, reason: String, on: Boolean, delayMs: Int, onClick: () -> Unit) {
    val sc by animateFloatAsState(if (on) 1.02f else 1f, SPRING, label = "dossier")
    Row(
        Modifier.appear(delayMs).scale(sc).fillMaxWidth().card(RoundedCornerShape(18.dp), if (on) 8.dp else 2.dp)
            .border(2.dp, if (on) Ecorce else Color.Transparent, RoundedCornerShape(18.dp))
            .clickable(role = Role.RadioButton, onClick = onClick).semantics { selected = on }.padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(13.dp)).background(folderBg(folder.color)), contentAlignment = Alignment.Center) {
            Icon(folderGlyph(folder.icon), null, tint = folderSolid(folder.color), modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(folder.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Ecorce, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(reason, fontSize = 12.sp, color = TextSec, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(
            Modifier.size(24.dp).clip(CircleShape).background(if (on) Ecorce else Color.Transparent).border(2.dp, if (on) Ecorce else Color(0xFFD9C2AE), CircleShape),
            contentAlignment = Alignment.Center
        ) { if (on) Icon(Icons.Outlined.Check, null, tint = Sable, modifier = Modifier.size(13.dp).pop()) }
    }
}

/** « Choisir un dossier » : recherche, création à la volée, dossiers par groupe. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FolderPickerSheet(
    folders: List<Folder>, links: List<Link>, current: String?, me: String?,
    onDismiss: () -> Unit, onPick: (String?) -> Unit, onCreate: (String) -> Unit,
) {
    var q by remember { mutableStateOf("") }
    val counts = remember(links) { links.filter { it.folderId != null }.groupingBy { it.folderId!! }.eachCount() }
    fun norm(t: String) = java.text.Normalizer.normalize(t.lowercase(), java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
    val query = q.trim()
    val match = folders.filter { query.isEmpty() || norm(it.name).contains(norm(query)) }
    fun group(f: Folder) = when {
        f.visibility.uppercase().contains("PUBLIC") -> 2
        f.collaborators.isNotEmpty() -> 0
        else -> 1
    }
    val canCreate = query.length > 1 && folders.none { norm(it.name) == norm(query) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = SheetColor, dragHandle = { SheetHandle() }) {
        Column(
            Modifier.fillMaxWidth().fillMaxHeight(0.92f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CircleIconButton(Icons.Outlined.ChevronLeft, "Revenir aux suggestions", onDismiss, background = Sable2)
                Text("Choisir un dossier", style = display(22.sp))
            }
            CampField("Rechercher ou créer un dossier", q, { q = it }, placeholder = "Vacances, cadeaux…", icon = Icons.Outlined.Search, maxLength = 100)
            if (canCreate) {
                Row(
                    Modifier.appear().fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Blush).dashedBorder(Terracotta, 16.dp)
                        .clickable(role = Role.Button) { onCreate(query) }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(Terracotta), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Add, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                    Text("Créer « $query »", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF9E3520))
                }
            }
            if (query.isEmpty()) {
                Column(Modifier.clip(RoundedCornerShape(18.dp)).background(CardColor)) {
                    PickRow("Sans dossier", "Dans « Non rangées »", current == null, false, Icons.Outlined.BookmarkBorder, Sable2, TextSec) { onPick(null) }
                }
            }
            listOf("Partagés", "Privés", "Publics").forEachIndexed { gi, title ->
                val items = match.filter { group(it) == gi }
                if (items.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("${title.uppercase()} · ${items.size}", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.8.sp, color = TextSec)
                        Column(Modifier.clip(RoundedCornerShape(18.dp)).background(CardColor)) {
                            items.forEach { f ->
                                val ro = f.collaborators.any { it.userId == me && it.role == "COLLABORATOR_ROLE_VIEWER" }
                                val n = counts[f.id] ?: f.linkCount
                                val sub = when {
                                    ro -> (f.ownerDisplayName.takeIf { it.isNotBlank() }?.let { "par $it · " } ?: "") + "lecture seule"
                                    f.collaborators.isNotEmpty() -> "avec ${f.collaborators.first().displayName.ifBlank { f.collaborators.first().email }}" +
                                        if (f.collaborators.size > 1) " +${f.collaborators.size - 1}" else ""
                                    else -> "$n idée${if (n > 1) "s" else ""}"
                                }
                                PickRow(f.name, sub, current == f.id, ro, folderGlyph(f.icon), folderBg(f.color), folderSolid(f.color)) { onPick(f.id) }
                            }
                        }
                    }
                }
            }
            if (query.isNotEmpty() && match.isEmpty() && !canCreate) {
                Text("Aucun dossier ne s'appelle comme ça… pour l'instant !", fontSize = 14.sp, color = TextSec, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp))
            }
        }
    }
}

@Composable
private fun PickRow(label: String, sub: String, on: Boolean, disabled: Boolean, icon: ImageVector, bg: Color, ink: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(if (on) PeachTint else CardColor).graphicsLayer { alpha = if (disabled) 0.45f else 1f }
            .clickable(enabled = !disabled, role = Role.RadioButton, onClick = onClick).semantics { selected = on }.padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(bg), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = ink, modifier = Modifier.size(17.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Ecorce, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, fontSize = 12.sp, color = TextSec, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (on) Box(Modifier.pop().size(22.dp).clip(CircleShape).background(Ecorce), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Check, null, tint = Sable, modifier = Modifier.size(12.dp))
        }
    }
}

/** « C'est rangé ! » : la carte s'envole dans son dossier, avec quelques éclats. */
@Composable
private fun DoneStep(link: Link, folder: Folder?, onReminder: () -> Unit) {
    val ink = folder?.let { folderSolid(it.color) } ?: TextSec
    val cat = categoryUi(link.category)
    val fly = remember { Animatable(0f) }
    val bump = remember { Animatable(1f) }
    val sun = remember { Animatable(0f) }
    val spark = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch { sun.animateTo(1f, tween(1100, easing = EaseSoft)) }
        launch { delay(250); fly.animateTo(1f, tween(850, easing = androidx.compose.animation.core.CubicBezierEasing(0.5f, 0f, 0.3f, 1f))) }
        launch { delay(870); bump.animateTo(1f, keyframes { durationMillis = 480; 1.22f at 240 }) }
        launch { delay(1000); spark.animateTo(1f, tween(750, easing = EaseSoft)) }
    }
    Box(Modifier.fillMaxWidth().height(230.dp).clip(RoundedCornerShape(26.dp)).background(SceneSky)) {
        Box(
            Modifier.align(Alignment.TopEnd).padding(top = 34.dp, end = 58.dp).graphicsLayer { translationY = (1f - sun.value) * 60.dp.toPx() }
                .size(74.dp).clip(CircleShape).background(Color(0x40FFC857)).padding(10.dp).clip(CircleShape).background(Color(0xFFFFC857))
        )
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(52.dp).background(Ecorce).padding(top = 3.dp).background(SceneGround))
        // La carte de l'idée : monte, puis plonge dans le dossier
        Column(
            Modifier.align(Alignment.TopCenter).offset(x = (-75).dp, y = 70.dp).width(100.dp).graphicsLayer {
                val t = fly.value
                val a = (t / 0.55f).coerceAtMost(1f)
                val b = ((t - 0.55f) / 0.45f).coerceIn(0f, 1f)
                translationX = (70f * a + 42f * b).dp.toPx()
                translationY = (-46f * a + 68f * b).dp.toPx()
                val s = if (t < 0.55f) 1f - 0.45f * a else 0.55f - 0.45f * b
                scaleX = s; scaleY = s
                rotationZ = -8f * a
                alpha = if (t < 0.55f) 1f else 1f - b
            }.card(RoundedCornerShape(14.dp), 8.dp)
        ) {
            Box(Modifier.fillMaxWidth().height(46.dp).background(cat.bg)) {
                if (link.imageUrl.isNotBlank()) AsyncImage(link.imageUrl, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
            Text(link.title, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Ecorce, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp))
        }
        // Le dossier qui la reçoit, et ses éclats
        Box(Modifier.align(Alignment.BottomCenter).offset(x = 58.dp, y = (-40).dp).size(76.dp, 66.dp).scale(bump.value)) {
            Box(Modifier.size(34.dp, 14.dp).clip(RoundedCornerShape(topStart = 7.dp, topEnd = 9.dp)).background(ink))
            Box(
                Modifier.padding(top = 10.dp).fillMaxSize().clip(RoundedCornerShape(0.dp, 16.dp, 16.dp, 16.dp)).background(ink),
                contentAlignment = Alignment.Center
            ) { Icon(folder?.let { folderGlyph(it.icon) } ?: Icons.Outlined.BookmarkBorder, null, tint = Color.White, modifier = Modifier.size(28.dp)) }
            if (spark.value in 0.001f..0.999f) {
                listOf(Triple(0f, -50f, Braise), Triple(38f, -34f, Color(0xFFFFC857)), Triple(48f, 4f, Terracotta), Triple(-38f, -34f, Prune), Triple(-48f, 2f, Braise), Triple(24f, -54f, Prune))
                    .forEach { (dx, dy, c) ->
                        Box(
                            Modifier.align(Alignment.Center).graphicsLayer {
                                translationX = (dx * spark.value).dp.toPx(); translationY = (dy * spark.value).dp.toPx()
                                val s = 0.3f + 0.7f * spark.value
                                scaleX = s; scaleY = s; rotationZ = 90f * spark.value; alpha = 1f - spark.value
                            }.size(9.dp).clip(CircleShape).background(c)
                        )
                    }
            }
        }
    }
    Column(Modifier.appear(1150).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("C'est rangé !", style = display(28.sp))
        Text(
            buildAnnotatedString {
                append("« ${link.title} » est dans ")
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(folder?.name ?: "vos idées non rangées") }
            },
            fontSize = 15.sp, color = Ecorce2, textAlign = TextAlign.Center
        )
    }
    val at = link.eventDate
    if (at != null && at > 0) {
        val on = link.reminderEnabled
        Row(
            Modifier.appear(1300).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if (on) Blush else CardColor)
                .border(2.dp, if (on) Terracotta else Color.Transparent, RoundedCornerShape(16.dp))
                .clickable(role = Role.Switch, onClick = onReminder).semantics { selected = on }.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(if (on) Icons.Outlined.NotificationsActive else Icons.Outlined.NotificationsNone, null, tint = Terracotta, modifier = Modifier.size(20.dp))
            Text(
                if (on) "Rappel la veille, le ${SimpleDateFormat("EEEE d MMMM", Locale.FRENCH).format(at - 86_400_000L)}, c'est noté" else "Me le rappeler la veille",
                fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Ecorce
            )
        }
    }
}
