package ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import data.AiRequests
import data.AiRequests.Blank
import data.AiSuggestedLink
import data.Folder
import data.LinkCategory
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ui.campfire.*
import viewmodel.LinkViewModel

// « Idées avec l'IA » (maquette « Idées avec l'IA v2 ») : une phrase à
// compléter → le tipi s'illumine pendant la rédaction → les idées arrivent une
// à une, chacune avec son « pourquoi », on garde ou on écarte (une autre
// arrive) → les idées gardées s'envolent dans le dossier.
// Même parcours côté web : frontend/src/components/AiSheet.tsx.

private enum class AiStep { Compose, Gen, Results, Done }

/** Idées demandées, et combien en redemander quand on en écarte. */
private const val FIRST = 5
private const val REFILL = 3
private const val NEW_FOLDER = "__new__"

private val PruneBg: Color get() = PlumBg
private val Gold = Color(0xFFFFC857)

private class IdeaCard(val id: Int, val idea: AiSuggestedLink) {
    var kept by mutableStateOf(false)
    var gone by mutableStateOf(false)
}

private fun toCategory(raw: String) =
    runCatching { LinkCategory.valueOf(raw.removePrefix("LINK_CATEGORY_")) }.getOrDefault(LinkCategory.IDEE)

private fun blankColors(b: Blank): Pair<Color, Color> = when (b) {
    Blank.What -> PeachBg to PeachInk
    Blank.Who -> BlueBg to BlueInk
    Blank.When -> HoneyBg to HoneyInk
    Blank.Where -> GreenBg to GreenInk
    Blank.Budget -> PlumBg to PlumInk
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AiGenerateScreen(
    viewModel: LinkViewModel,
    onBack: () -> Unit,
    initialFolderId: String? = null,
    initialPrompt: String = "",
    childId: String? = null,
    onSavedInFolder: (String?) -> Unit = { onBack() },
) {
    val children by viewModel.children.collectAsState()
    val folders by viewModel.folders.collectAsState()
    val links by viewModel.repository.links.collectAsState()
    val scope = rememberCoroutineScope()
    val now = remember { System.currentTimeMillis() }

    var step by remember { mutableStateOf(AiStep.Compose) }
    var req by remember { mutableStateOf(AiRequests.defaultRequest(children, childId, initialPrompt)) }
    var edit by remember { mutableStateOf<Blank?>(null) }
    var cascade by remember { mutableIntStateOf(0) }
    var inspiration by remember { mutableStateOf<String?>(null) }
    val cards = remember { mutableStateListOf<IdeaCard>() }
    val reserve = remember { mutableListOf<AiSuggestedLink>() }
    var pending by remember { mutableIntStateOf(0) }
    val seen = remember { mutableListOf<String>() }
    var nextId by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var target by remember { mutableStateOf(initialFolderId.orEmpty()) }
    var picking by remember { mutableStateOf(false) }
    var confirmClose by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf<Pair<List<AiSuggestedLink>, String?>?>(null) }
    var genJob by remember { mutableStateOf<Job?>(null) }
    var refillJob by remember { mutableStateOf<Job?>(null) }

    val newName = AiRequests.folderNameFor(req, children, inspiration, now)
    // Par défaut : le dossier d'où l'on vient, sinon un dossier existant du même nom, sinon un nouveau
    val folderTarget = target.ifEmpty { folders.find { it.name.equals(newName, ignoreCase = true) }?.id ?: NEW_FOLDER }
    val targetFolder = folders.find { it.id == folderTarget }
    val kept = cards.filter { it.kept && !it.gone }
    val shownCount = cards.count { !it.gone }

    fun toCards(ideas: List<AiSuggestedLink>) = ideas.map { IdeaCard(nextId++, it) }

    fun generate() {
        genJob?.cancel()
        refillJob?.cancel()
        step = AiStep.Gen
        edit = null
        error = null
        genJob = scope.launch {
            viewModel.requestAiIdeas(AiRequests.promptOf(req, children, now), FIRST, seen.toList())
                .onSuccess { ideas ->
                    seen += ideas.map { it.title }
                    val stay = cards.filter { it.kept && !it.gone }
                    cards.clear(); cards += stay; cards += toCards(ideas)
                    reserve.clear(); pending = 0
                    step = if (cards.isNotEmpty()) AiStep.Results else AiStep.Compose
                    if (ideas.isEmpty()) error = "Aucune idée trouvée : changez un mot de la phrase et réessayez."
                }
                .onFailure { e ->
                    error = e.message
                    step = if (kept.isNotEmpty()) AiStep.Results else AiStep.Compose
                }
        }
    }

    fun cancel() {
        genJob?.cancel()
        step = AiStep.Compose
    }

    /** « Pas pour nous » : la carte s'en va, une autre arrive. */
    fun drop(card: IdeaCard) {
        card.gone = true
        card.kept = false
        if (reserve.isNotEmpty()) {
            cards += toCards(listOf(reserve.removeAt(0)))
            return
        }
        pending++
        if (refillJob?.isActive == true) return
        refillJob = scope.launch {
            viewModel.requestAiIdeas(AiRequests.promptOf(req, children, now), REFILL, seen.toList())
                .onSuccess { ideas ->
                    seen += ideas.map { it.title }
                    val take = minOf(pending, ideas.size)
                    cards += toCards(ideas.take(take))
                    reserve += ideas.drop(take)
                    if (ideas.isEmpty()) error = "Pas d'autre idée pour cette demande."
                }
                .onFailure { e -> error = e.message }
            pending = 0
        }
    }

    fun save() {
        if (kept.isEmpty()) return
        val ideas = kept.map { c -> c.idea }
        if (folderTarget == NEW_FOLDER) {
            viewModel.saveAiIdeasInNewFolder(ideas, newName) { id -> saved = ideas to id; step = AiStep.Done }
        } else {
            val id = targetFolder?.id
            viewModel.saveAiIdeas(ideas, id)
            saved = ideas to id
            step = AiStep.Done
        }
    }

    fun restart() {
        cards.clear(); reserve.clear(); seen.clear(); pending = 0
        saved = null; error = null
        step = AiStep.Compose
    }

    fun close() {
        if (step != AiStep.Done && kept.isNotEmpty()) confirmClose = true
        else { genJob?.cancel(); refillJob?.cancel(); onBack() }
    }

    val gen = step == AiStep.Gen
    val bg by animateColorAsState(if (gen) Prune else Sable, tween(280, easing = EaseSoft), label = "ai-bg")
    val fg = if (gen) OnDark else Ecorce

    Column(Modifier.fillMaxSize().background(bg).statusBarsPadding()) {
        // ── En-tête ──
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween
        ) {
            CircleIconButton(Icons.Outlined.Close, "Fermer", ::close, background = if (gen) Color.White.copy(alpha = 0.14f) else Sable2, tint = fg)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Outlined.AutoAwesome, null, tint = if (gen) Gold else Prune, modifier = Modifier.size(18.dp).twinkle())
                Text("Idées avec l'IA", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = fg)
            }
            Spacer(Modifier.width(44.dp))
        }

        Crossfade(step, Modifier.weight(1f), animationSpec = tween(280), label = "ai-step") { s ->
            when (s) {
                AiStep.Compose -> ComposeStep(
                    req = req, children = children, now = now, edit = edit, cascade = cascade, error = error,
                    onEdit = { edit = if (edit == it) null else it },
                    onPick = { b, key ->
                        req = req.with(b, key); inspiration = null
                        if (b != Blank.Who) edit = null
                    },
                    onDetail = { req = req.copy(detail = it.take(200)) },
                    onInspiration = { i -> req = i.apply(req); inspiration = i.label; edit = null; cascade++ },
                    onGenerate = ::generate,
                )
                AiStep.Gen -> WaitingStep(AiRequests.waitingMessages(req, children, now), ::cancel)
                AiStep.Results -> Column(Modifier.fillMaxSize()) {
                    Column(
                        Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(AiRequests.resultTitle(shownCount + pending, req, children, now), style = display(25.sp))
                                Text(
                                    "${AiRequests.resultSubtitle(req)} — modifier", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextSec,
                                    textDecoration = TextDecoration.Underline,
                                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(role = Role.Button) { step = AiStep.Compose }.padding(vertical = 4.dp)
                                )
                            }
                            val spin = remember { Animatable(0f) }
                            Box(
                                Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(PruneBg)
                                    .clickable(role = Role.Button) { scope.launch { spin.snapTo(-360f); spin.animateTo(0f, spring(0.5f)) }; generate() }
                                    .semantics { contentDescription = "Proposer d'autres idées" },
                                contentAlignment = Alignment.Center
                            ) { Icon(Icons.Outlined.Refresh, null, tint = Prune, modifier = Modifier.size(20.dp).rotate(spin.value)) }
                        }
                        error?.let { ErrorText(it) }
                        cards.forEachIndexed { i, c ->
                            key(c.id) {
                                AnimatedVisibility(
                                    !c.gone,
                                    exit = slideOutHorizontally(tween(300)) { it } + fadeOut(tween(250)) + shrinkVertically(tween(300, delayMillis = 250))
                                ) {
                                    IdeaCardView(c, if (i < FIRST) i * 220 else 80, onKeep = { c.kept = !c.kept }, onDrop = { drop(c) })
                                }
                            }
                        }
                        repeat(pending) {
                            Row(
                                Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(22.dp)).background(Sable2)
                                    .semantics { liveRegion = LiveRegionMode.Polite },
                                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Outlined.AutoAwesome, null, tint = Prune, modifier = Modifier.size(16.dp).twinkle())
                                Text("Une autre idée arrive…", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextSec)
                            }
                        }
                    }
                    Column(
                        Modifier.fillMaxWidth().background(Sable).border(1.dp, Line).navigationBarsPadding()
                            .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            buildAnnotatedString {
                                append("Ranger dans ")
                                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Ecorce)) {
                                    append(targetFolder?.name ?: if (folderTarget == NEW_FOLDER) "« $newName » (nouveau)" else "vos idées non rangées")
                                }
                                append(" · ")
                                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Terracotta)) { append("changer") }
                            },
                            fontSize = 13.sp, color = TextSec,
                            modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(role = Role.Button, onClickLabel = "Changer de dossier") { picking = true }
                        )
                        PublicFolderWaitingNote(viewModel, targetFolder)
                        Box(
                            Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(18.dp)).background(if (kept.isEmpty()) BarkMute else Terracotta)
                                .clickable(enabled = kept.isNotEmpty(), role = Role.Button, onClick = ::save),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                if (kept.isEmpty()) "Gardez au moins une idée" else "Ranger ${kept.size} idée${if (kept.size > 1) "s" else ""}",
                                fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White
                            )
                        }
                    }
                }
                AiStep.Done -> saved?.let { (ideas, id) ->
                    DoneStep(ideas, folders.find { it.id == id }, onRestart = ::restart, onOpen = { onSavedInFolder(id) })
                }
            }
        }
    }

    if (picking) {
        FolderPickerSheet(
            folders = folders, links = links, current = if (folderTarget == NEW_FOLDER) null else targetFolder?.id, me = viewModel.currentUserId(),
            onDismiss = { picking = false },
            onPick = { target = it ?: "none"; picking = false },
            onCreate = { name -> viewModel.createFolderQuick(name) { id -> target = id; picking = false } },
        )
    }

    if (confirmClose) {
        AlertDialog(
            onDismissRequest = { confirmClose = false },
            containerColor = SheetColor,
            title = { Text("Abandonner ces idées ?", style = display(20.sp, FontWeight.Bold)) },
            text = { Text("Les idées gardées ne seront pas rangées.", color = Ecorce2) },
            confirmButton = {
                TextButton(onClick = { confirmClose = false; genJob?.cancel(); refillJob?.cancel(); onBack() }) {
                    Text("Abandonner", color = Terracotta, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { confirmClose = false }) { Text("Annuler", color = Ecorce) } }
        )
    }
}

// ── Composer la phrase ─────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ComposeStep(
    req: AiRequests.Request,
    children: List<data.Child>,
    now: Long,
    edit: Blank?,
    cascade: Int,
    error: String?,
    onEdit: (Blank) -> Unit,
    onPick: (Blank, String) -> Unit,
    onDetail: (String) -> Unit,
    onInspiration: (AiRequests.Inspiration) -> Unit,
    onGenerate: () -> Unit,
) {
    val sentence = display(23.sp, FontWeight.Bold)
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Text("Racontez-moi votre envie, je m'occupe du reste.", fontSize = 15.sp, color = TextSec)
            error?.let { ErrorText(it) }

            Column(Modifier.fillMaxWidth().card(RoundedCornerShape(26.dp), 4.dp).padding(horizontal = 18.dp, vertical = 20.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                    @Composable fun word(t: String) = Text(t, style = sentence)
                    @Composable fun blank(b: Blank, i: Int, after: String = "") = Row(verticalAlignment = Alignment.CenterVertically) {
                        BlankChip(AiRequests.blankLabel(b, req, children, now).ifEmpty { "…" }, b, edit == b, cascade, i * 90) { onEdit(b) }
                        if (after.isNotEmpty()) Text(after, style = sentence)
                    }
                    word("Je cherche"); blank(Blank.What, 0); word("pour"); blank(Blank.Who, 1); blank(Blank.When, 2, ",")
                    blank(Blank.Where, 3, ","); word("budget"); blank(Blank.Budget, 4, ".")
                }
                edit?.let { b ->
                    key(b) {
                        Column(
                            Modifier.padding(top = 14.dp).fillMaxWidth().slideIn().dashedTop().padding(top = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(AiRequests.HINTS.getValue(b), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextSec)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                AiRequests.optionsFor(b, children, now).forEach { o ->
                                    val on = if (b == Blank.Who) o.key in req.who else req.valueOf(b) == o.key
                                    val (cbg, cfg) = blankColors(b)
                                    val scale by androidx.compose.animation.core.animateFloatAsState(if (on) 1.04f else 1f, spring(0.5f), label = "opt")
                                    Text(
                                        if (b == Blank.Who) o.label else o.label.replaceFirstChar { it.uppercase() },
                                        fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (on) cfg else Ecorce,
                                        modifier = Modifier.scale(scale).height(38.dp).clip(RoundedCornerShape(12.dp))
                                            .background(if (on) cbg else CardColor)
                                            .border(2.dp, if (on) Ecorce else Line, RoundedCornerShape(12.dp))
                                            .clickable(role = if (b == Blank.Who) Role.Checkbox else Role.RadioButton) { onPick(b, o.key) }
                                            .semantics { stateDescription = if (on) "choisi" else "non choisi" }
                                            .wrapContentHeight().padding(horizontal = 12.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row {
                    Text("Un détail en plus ? ", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextSec)
                    Text("(facultatif)", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextSec)
                }
                Row(
                    Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(16.dp)).background(CardColor)
                        .border(1.5.dp, Line, RoundedCornerShape(16.dp)).padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Outlined.Edit, null, tint = TextMuted, modifier = Modifier.size(18.dp))
                    Box(Modifier.weight(1f)) {
                        if (req.detail.isEmpty()) Text("« ${children.firstOrNull()?.name ?: "Il"} adore l'espace en ce moment »", fontSize = 15.sp, color = TextMuted, maxLines = 1)
                        BasicTextField(
                            req.detail, onDetail, singleLine = true,
                            textStyle = TextStyle(color = Ecorce, fontSize = 15.sp, fontWeight = FontWeight.Medium),
                            cursorBrush = SolidColor(Terracotta),
                            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Un détail en plus, facultatif" }
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Ou partir d'une inspiration", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextSec)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AiRequests.inspirations(children, now).forEach { i ->
                        val ink = Color(i.ink)
                        Row(
                            Modifier.height(44.dp).clip(RoundedCornerShape(14.dp)).background(Color(i.bg))
                                .clickable(role = Role.Button) { onInspiration(i) }.padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            PathIcon(i.d, ink, 17.dp)
                            Text(i.label, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = ink, maxLines = 1)
                        }
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().border(1.dp, Line).navigationBarsPadding().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 16.dp)) {
            Row(
                Modifier.fillMaxWidth().height(58.dp).clip(RoundedCornerShape(20.dp)).background(Prune).clickable(role = Role.Button, onClick = onGenerate),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)
            ) {
                Icon(Icons.Outlined.AutoAwesome, null, tint = Soleil, modifier = Modifier.size(20.dp).twinkle())
                Text("Trouver $FIRST idées", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = OnDark)
            }
        }
    }
}

/** Un mot de la phrase à toucher ; il « saute » quand une inspiration remplit la phrase. */
@Composable
private fun BlankChip(label: String, b: Blank, open: Boolean, cascade: Int, delayMs: Int, onClick: () -> Unit) {
    val (cbg, cfg) = blankColors(b)
    val pop = remember { Animatable(1f) }
    LaunchedEffect(cascade) {
        if (cascade == 0) return@LaunchedEffect
        pop.snapTo(0.7f); delay(delayMs.toLong())
        pop.animateTo(1f, keyframes { durationMillis = 500; 1.12f at 300 using FastOutSlowInEasing })
    }
    Text(
        label, style = display(22.sp, FontWeight.Bold, cfg),
        modifier = Modifier.scale(pop.value).clip(RoundedCornerShape(12.dp)).background(cbg)
            .border(2.dp, if (open) Ecorce else Color.Transparent, RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClickLabel = "Changer") { onClick() }
            .semantics { stateDescription = if (open) "choix ouverts" else "" }
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

// ── Pendant la rédaction ───────────────────────────────────────────────────────

@Composable
private fun WaitingStep(messages: List<String>, onCancel: () -> Unit) {
    var i by remember { mutableIntStateOf(0) }
    LaunchedEffect(messages.size) { while (i < messages.size - 1) { delay(2700); i++ } }
    // La rédaction prend 20 à 60 s : la barre avance vite puis ralentit, sans jamais finir seule
    val bar = remember { Animatable(0.04f) }
    LaunchedEffect(Unit) { bar.animateTo(0.92f, tween(45_000, easing = CubicBezierEasing(0.1f, 0.6f, 0.2f, 1f))) }
    val t = rememberInfiniteTransition(label = "ai-wait")
    val glow by t.animateFloat(0.55f, 1f, infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "glow")
    val orbit by t.animateFloat(0f, 360f, infiniteRepeatable(tween(6000, easing = LinearEasing)), label = "orbit")
    val flap by t.animateFloat(0f, 30f, infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "flap")

    Column(
        Modifier.fillMaxSize().padding(horizontal = 30.dp).navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(30.dp, Alignment.CenterVertically)
    ) {
        Box(Modifier.size(200.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier.padding(20.dp).fillMaxSize().graphicsLayer { alpha = glow }
                    .background(Brush.radialGradient(listOf(Gold.copy(alpha = 0.45f), Gold.copy(alpha = 0f))), CircleShape)
            )
            Box(Modifier.fillMaxSize().rotate(orbit)) {
                Icon(Icons.Outlined.AutoAwesome, null, tint = Gold, modifier = Modifier.offset(x = 89.dp).size(22.dp))
                Icon(Icons.Outlined.AutoAwesome, null, tint = Braise, modifier = Modifier.align(Alignment.TopEnd).offset(x = (-8).dp, y = 120.dp).size(16.dp))
                Icon(Icons.Outlined.AutoAwesome, null, tint = Soleil, modifier = Modifier.offset(x = 10.dp, y = 130.dp).size(18.dp))
            }
            GlowingTipi(glow, flap, Modifier.size(130.dp, 113.dp))
        }
        Crossfade(i, Modifier.fillMaxWidth().height(50.dp).semantics { liveRegion = LiveRegionMode.Polite }, label = "ai-msg") { n ->
            Text(messages[n], style = display(20.sp, FontWeight.Bold, OnDark), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
        Box(Modifier.fillMaxWidth(0.7f).height(6.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f))) {
            Box(Modifier.fillMaxWidth(bar.value).fillMaxHeight().clip(CircleShape).background(Gold))
        }
        Text(
            "Modifier ma demande", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = OnDark,
            modifier = Modifier.height(44.dp).clip(RoundedCornerShape(14.dp)).border(1.5.dp, OnDark.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                .clickable(role = Role.Button, onClick = onCancel).wrapContentHeight().padding(horizontal = 18.dp)
        )
    }
}

/** Le tipi de la maquette (viewBox 76×66) : l'entrée s'illumine, les pans s'ouvrent. */
@Composable
private fun GlowingTipi(glow: Float, flap: Float, modifier: Modifier) {
    val paths = remember {
        fun p(d: String) = PathParser().parsePathString(d).toPath()
        listOf(p("M31 4L44 22M45 4L32 22"), p("M38 12L70 62H6Z"), p("M38 34L52 62H24Z"), p("M38 34L38 62H24Z"), p("M38 34L52 62H38Z"))
    }
    Canvas(modifier) {
        val s = size.width / 76f
        scale(s, s, pivot = Offset.Zero) {
            fun stroke(w: Float) = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round)
            drawPath(paths[0], OnDark, style = stroke(2.6f))
            drawPath(paths[1], Tipi); drawPath(paths[1], OnDark, style = stroke(2.6f))
            drawPath(paths[2], Gold, alpha = glow)
            rotate(-flap, pivot = Offset(38f, 34f)) { drawPath(paths[3], Color(0xFF3A211B)); drawPath(paths[3], OnDark, style = stroke(1.8f)) }
            rotate(flap, pivot = Offset(38f, 34f)) { drawPath(paths[4], Braise); drawPath(paths[4], OnDark, style = stroke(1.8f)) }
        }
    }
}

// ── Les idées ──────────────────────────────────────────────────────────────────

@Composable
private fun IdeaCardView(card: IdeaCard, delayMs: Int, onKeep: () -> Unit, onDrop: () -> Unit) {
    val cat = categoryUi(toCategory(card.idea.category))
    Column(
        Modifier.fillMaxWidth().appearUp(delayMs).card(RoundedCornerShape(22.dp), 3.dp)
            .border(2.dp, if (card.kept) Prune else Color.Transparent, RoundedCornerShape(22.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(15.dp)).background(cat.bg), contentAlignment = Alignment.Center) {
                Icon(cat.icon, null, tint = cat.ink, modifier = Modifier.size(24.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(card.idea.title, style = display(17.sp, FontWeight.Bold))
                Text(listOf(cat.label, card.idea.ageRange).filter { it.isNotBlank() }.joinToString(" · "), fontSize = 13.sp, color = TextSec)
                if (card.idea.description.isNotBlank()) Text(card.idea.description, fontSize = 13.sp, color = Ecorce2)
            }
        }
        if (card.idea.why.isNotBlank()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Outlined.AutoAwesome, null, tint = Prune, modifier = Modifier.size(14.dp))
                Text(card.idea.why, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Prune)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(14.dp)).background(if (card.kept) Prune else Sable2)
                    .clickable(role = Role.Checkbox, onClick = onKeep).semantics { stateDescription = if (card.kept) "gardée" else "pas encore gardée" },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
            ) {
                if (card.kept) Icon(Icons.Outlined.Check, null, tint = OnDark, modifier = Modifier.size(17.dp).popIn())
                Text(if (card.kept) "Gardée" else "Je garde", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (card.kept) OnDark else Ecorce)
            }
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Sable).border(1.5.dp, Line, RoundedCornerShape(14.dp))
                    .clickable(role = Role.Button, onClick = onDrop)
                    .semantics { contentDescription = "Pas pour nous : écarter « ${card.idea.title} » et proposer autre chose" },
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Outlined.Close, null, tint = TextSec, modifier = Modifier.size(18.dp)) }
        }
    }
}

// ── C'est rangé ────────────────────────────────────────────────────────────────

/** Les idées gardées s'envolent dans le dossier. */
@Composable
private fun DoneStep(ideas: List<AiSuggestedLink>, folder: Folder?, onRestart: () -> Unit, onOpen: () -> Unit) {
    val ink = folder?.let { folderSolid(it.color) } ?: Prune
    val flights = ideas.take(3).map { remember { Animatable(0f) } }
    val bump = remember { Animatable(1f) }
    val spark = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        flights.forEachIndexed { j, a -> launch { delay(250L + j * 160); a.animateTo(1f, tween(850, easing = CubicBezierEasing(0.5f, 0f, 0.3f, 1f))) } }
        launch { delay(800); bump.animateTo(1f, keyframes { durationMillis = 800; 1f at 320; 1.22f at 560 }) }
        launch { delay(1200); spark.animateTo(1f, tween(750, easing = EaseSoft)) }
    }
    Column(Modifier.fillMaxSize().navigationBarsPadding().padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Box(Modifier.fillMaxWidth().height(260.dp).clip(RoundedCornerShape(26.dp)).background(PruneBg)) {
            Box(Modifier.align(Alignment.BottomCenter).offset(y = 120.dp).size(300.dp).clip(CircleShape).background(SunDisc))
            ideas.take(3).forEachIndexed { j, idea ->
                val cat = categoryUi(toCategory(idea.category))
                Column(
                    Modifier.align(Alignment.TopCenter).offset(x = (-109 + j * 22).dp, y = (50 + j * 26).dp).width(92.dp).graphicsLayer {
                        val t = flights[j].value
                        val a = (t / 0.55f).coerceAtMost(1f)
                        val b = ((t - 0.55f) / 0.45f).coerceIn(0f, 1f)
                        translationX = (70f * a + 48f * b).dp.toPx()
                        translationY = (-50f * a + 80f * b).dp.toPx()
                        val s = if (t < 0.55f) 1f - 0.45f * a else 0.55f - 0.45f * b
                        scaleX = s; scaleY = s
                        rotationZ = -8f * a
                        alpha = if (t < 0.55f) 1f else 1f - b
                    }.card(RoundedCornerShape(12.dp), 8.dp)
                ) {
                    Box(Modifier.fillMaxWidth().height(36.dp).background(cat.bg))
                    Text(idea.title, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Ecorce, maxLines = 1, modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp))
                }
            }
            Box(Modifier.align(Alignment.BottomCenter).offset(x = 69.dp, y = (-46).dp).size(78.dp, 68.dp).scale(bump.value)) {
                Box(Modifier.size(34.dp, 14.dp).clip(RoundedCornerShape(topStart = 7.dp, topEnd = 9.dp)).background(ink))
                Box(
                    Modifier.padding(top = 10.dp).fillMaxSize().clip(RoundedCornerShape(0.dp, 16.dp, 16.dp, 16.dp)).background(ink),
                    contentAlignment = Alignment.Center
                ) { Icon(folder?.let { folderGlyph(it.icon) } ?: Icons.Outlined.Lightbulb, null, tint = Color.White, modifier = Modifier.size(28.dp)) }
                if (spark.value in 0.001f..0.999f) {
                    listOf(Triple(0f, -52f, Braise), Triple(40f, -34f, Gold), Triple(50f, 4f, Terracotta), Triple(-40f, -34f, Ecorce), Triple(-50f, 2f, Braise))
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
        val n = ideas.size
        Column(Modifier.fillMaxWidth().appearUp(1300), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("$n idée${if (n > 1) "s rangées" else " rangée"} !", style = display(28.sp))
            Text(
                buildAnnotatedString {
                    append("dans ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(folder?.name ?: "vos idées non rangées") }
                    append(". Bonne aventure à la tribu !")
                },
                fontSize = 15.sp, color = Ecorce2, textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth().appearUp(1300), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CampButton("Nouvelle demande", onRestart, kind = ButtonKind.Secondary, height = 56.dp)
            CampButton(if (folder != null) "Voir le dossier" else "Voir mes idées", onOpen, Modifier.weight(1f), kind = ButtonKind.Dark, height = 56.dp)
        }
    }
}

// ── Morceaux ───────────────────────────────────────────────────────────────────

@Composable
private fun ErrorText(text: String) {
    Text(
        text, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = CoralInk,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(CoralBg).padding(horizontal = 16.dp, vertical = 10.dp)
            .semantics { liveRegion = LiveRegionMode.Polite }
    )
}

/** Icône dessinée d'un chemin SVG (viewBox 24, trait 1,9). */
@Composable
private fun PathIcon(d: String, tint: Color, size: Dp) {
    val path = remember(d) { PathParser().parsePathString(d).toPath() }
    Canvas(Modifier.size(size)) {
        val s = this.size.width / 24f
        scale(s, s, pivot = Offset.Zero) { drawPath(path, tint, style = Stroke(1.9f, cap = StrokeCap.Round, join = StrokeJoin.Round)) }
    }
}

/** Scintillement de l'étincelle IA (grossit et tourne un peu). */
@Composable
private fun Modifier.twinkle(): Modifier {
    val t = rememberInfiniteTransition(label = "twinkle")
    val p by t.animateFloat(0f, 1f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "twinkle-p")
    return graphicsLayer { val s = 1f + 0.2f * p; scaleX = s; scaleY = s; rotationZ = 18f * p }
}

/** Apparition « up » : monte de 18 dp avec un léger rebond, après `delayMs`. */
@Composable
private fun Modifier.appearUp(delayMs: Int): Modifier {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) { delay(delayMs.toLong()); p.animateTo(1f, spring(0.62f, Spring.StiffnessLow)) }
    return graphicsLayer {
        alpha = p.value.coerceIn(0f, 1f)
        translationY = (1f - p.value) * 18.dp.toPx()
        val s = 0.96f + 0.04f * p.value
        scaleX = s; scaleY = s
    }
}

/** Éclat « pop » : 0.3 → 1.25 → 1. */
@Composable
private fun Modifier.popIn(): Modifier {
    val p = remember { Animatable(0.3f) }
    LaunchedEffect(Unit) { p.animateTo(1f, keyframes { durationMillis = 450; 1.25f at 270 using FastOutSlowInEasing }) }
    return scale(p.value)
}

/** Les choix d'un mot glissent de 8 dp en apparaissant. */
@Composable
private fun Modifier.slideIn(): Modifier {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) { p.animateTo(1f, tween(380, easing = EaseSoft)) }
    return graphicsLayer { alpha = p.value; translationY = (1f - p.value) * (-8).dp.toPx() }
}

/** Trait pointillé au-dessus des choix (1,5 dp, couleur Line). */
private fun Modifier.dashedTop(): Modifier = drawBehind {
    drawLine(Line, Offset(0f, 0f), Offset(size.width, 0f), 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)))
}
