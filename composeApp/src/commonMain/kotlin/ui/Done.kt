package ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import data.Done
import data.DoneInput
import data.Link
import data.LinkCategory
import data.LinkDone
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ui.campfire.*
import viewmodel.LinkViewModel

// « C'est fait » (maquettes « C'est fait · prototype » et « … · états ») :
// un geste pour dire qu'une idée a été vécue, un souvenir facultatif, une
// petite célébration et un toast pour annuler. Même parcours sur le web
// (components/Done.tsx) ; libellés et règles dans data/Done.kt ↔ lib/done.ts.

/** Feuille « Comment c'était ? » à ouvrir. */
data class MemoryRequest(
    val link: Link,
    /** Jour proposé d'office (rappel d'une sortie passée) : AAAA-MM-JJ et son libellé */
    val day: String? = null,
    val dayLabel: String? = null,
    /** Souvenir à corriger, au lieu d'en créer un */
    val edit: LinkDone? = null,
)

internal data class DoneToast(val text: String, val linkId: String? = null, val doneId: String? = null, val key: Long = System.nanoTime())

/** Actions « C'est fait » partagées par les vignettes, le dossier, la fiche et la notification. */
class DoneController(
    private val vm: LinkViewModel,
    private val scope: CoroutineScope,
    private val haptic: () -> Unit,
    private val reducedMotion: () -> Boolean,
) {
    var sheet by mutableStateOf<MemoryRequest?>(null)
        private set
    /** Idée dont le menu (appui long) est ouvert */
    var menu by mutableStateOf<Link?>(null)
    internal var celebration by mutableStateOf<Pair<String, String>?>(null)
    internal var toast by mutableStateOf<DoneToast?>(null)
    /** Change à chaque souvenir ajouté, corrigé ou retiré : la fiche relit les siens. */
    var version by mutableIntStateOf(0)
        private set

    private fun folderOf(l: Link) = vm.folders.value.find { it.id == l.folderId }

    /** Le lecteur voit l'état « fait » : sa tribu, ou un dossier dont il est membre. */
    fun canSee(l: Link): Boolean {
        val me = vm.currentUserId() ?: return false
        if (vm.isTribePeer(l.ownerId)) return true
        val f = folderOf(l) ?: return false
        return vm.isTribePeer(f.ownerId) || f.collaborators.any { it.userId == me }
    }

    /** Il peut la marquer : sa tribu, ou collaborateur éditeur du dossier ; jamais un cadeau d'une liste de cadeaux. */
    fun canMark(l: Link): Boolean {
        val me = vm.currentUserId() ?: return false
        val f = folderOf(l)
        if (Done.inGiftList(l, f)) return false
        if (vm.isTribePeer(l.ownerId)) return true
        return f != null && (vm.isTribePeer(f.ownerId) || f.collaborators.any { it.userId == me && it.role == "COLLABORATOR_ROLE_EDITOR" })
    }

    private fun progressOf(l: Link): Done.Progress? {
        val folderId = l.folderId ?: return null
        val inFolder = vm.repository.links.value.filter { it.folderId == folderId }
        return Done.folderProgress(inFolder, folderOf(l)?.giftReservations == true)
    }

    /** Marque l'idée, puis célébration et toast « 5 sur 8 faites · Annuler ». Faux si le serveur a refusé. */
    suspend fun mark(l: Link, input: DoneInput): Boolean {
        val (done, after) = vm.markDone(l.id, input) ?: return false
        version++
        haptic()
        val p = progressOf(after)
        celebration = Done.celebrationTitle(after.category, Done.isDone(l)) to (p?.let { "${Done.progressLabel(it)} dans ce dossier" } ?: "Un souvenir de plus pour la tribu")
        scope.launch {
            delay(if (reducedMotion()) 700 else 1500)
            celebration = null
            toast = DoneToast(Done.toast(p), after.id, done.id)
        }
        return true
    }

    /** Coche d'une vignette : faite, sans souvenir. */
    fun quick(l: Link) {
        scope.launch { mark(l, DoneInput()) }
    }

    fun remember(l: Link, day: String? = null, dayLabel: String? = null, edit: LinkDone? = null) {
        sheet = MemoryRequest(l, day, dayLabel, edit)
    }

    fun closeSheet() {
        sheet = null
    }

    suspend fun update(l: Link, doneId: String, input: DoneInput): Boolean {
        vm.updateDone(l.id, doneId, input) ?: return false
        version++
        return true
    }

    private fun removed(after: Link?) {
        if (after == null) return
        version++
        toast = DoneToast(if (Done.isDone(after)) "Souvenir retiré" else "Remis dans « À faire »")
    }

    /** « Plus fait » : retire le dernier souvenir. */
    fun unmark(l: Link) {
        scope.launch { removed(vm.unmarkLastDone(l.id)) }
    }

    fun remove(l: Link, doneId: String) {
        scope.launch { removed(vm.unmarkDone(l.id, doneId)) }
    }

    /** « Annuler » du toast. */
    internal fun undo(t: DoneToast) {
        toast = null
        val linkId = t.linkId ?: return
        val doneId = t.doneId ?: return
        scope.launch { if (vm.unmarkDone(linkId, doneId) != null) version++ }
    }

    /** « Pas cette fois ». */
    fun dismiss(l: Link) {
        scope.launch { if (vm.dismissDonePrompt(l.id) != null) toast = DoneToast("D'accord, elle reste à faire") }
    }
}

val LocalDone = staticCompositionLocalOf<DoneController?> { null }

@Composable
fun rememberDoneController(vm: LinkViewModel): DoneController {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    return remember(vm) {
        DoneController(
            vm, scope,
            haptic = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
            // Réglage « Supprimer les animations » d'Android
            reducedMotion = {
                android.provider.Settings.Global.getFloat(context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
            },
        )
    }
}

/** Feuilles, célébration et toast de « C'est fait », posés une fois à la racine de l'app. */
@Composable
fun DoneHost(done: DoneController, vm: LinkViewModel, modifier: Modifier = Modifier) {
    done.sheet?.let { MemorySheet(it, done, vm) }
    done.menu?.let { DoneMenuSheet(it, done) }
    Box(Modifier.fillMaxSize()) {
        done.celebration?.let { (title, sub) -> Celebration(title, sub) }
        val t = done.toast
        LaunchedEffect(t?.key) {
            if (t != null) {
                delay(if (t.doneId != null) 5000 else 3200)
                if (done.toast?.key == t.key) done.toast = null
            }
        }
        AnimatedVisibility(t != null, modifier.align(Alignment.BottomCenter), enter = fadeIn() + scaleIn(initialScale = 0.96f), exit = fadeOut()) {
            // Dernier toast gardé pendant qu'il s'efface
            val shown = remember { mutableStateOf(t) }
            if (t != null) shown.value = t
            val cur = shown.value ?: return@AnimatedVisibility
            Row(
                Modifier.clip(RoundedCornerShape(18.dp)).background(Color(0xFF3A211B)).border(1.dp, CardBorder, RoundedCornerShape(18.dp))
                    .padding(start = 16.dp, end = if (cur.doneId != null) 10.dp else 16.dp, top = 10.dp, bottom = 10.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(cur.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = OnDark)
                if (cur.doneId != null) {
                    Text(
                        "Annuler", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFC857),
                        modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(OnDark.copy(alpha = 0.14f))
                            .clickable(role = Role.Button) { done.undo(cur) }.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

/** Coche tracée (même tracé que la maquette : M5 12.5 l4.5 4.5 L19 7.5 sur 24). */
@Composable
private fun Check(size: Dp, color: Color, modifier: Modifier = Modifier, width: Float = 3f, progress: Float = 1f) {
    Canvas(modifier.size(size)) {
        val u = this.size.width / 24f
        val a = Offset(5f * u, 12.5f * u); val b = Offset(9.5f * u, 17f * u); val c = Offset(19f * u, 7.5f * u)
        val first = 6.36f; val total = first + 13.44f
        val drawn = progress.coerceIn(0f, 1f) * total
        val stroke = width * u
        if (drawn <= 0f) return@Canvas
        val p1 = (drawn / first).coerceAtMost(1f)
        drawLine(color, a, a + (b - a) * p1, stroke, StrokeCap.Round)
        if (drawn > first) drawLine(color, b, b + (c - b) * ((drawn - first) / (total - first)), stroke, StrokeCap.Round)
    }
}

private val Sparks = listOf(
    Triple(-70, -60, 0xFFF28C38), Triple(70, -64, 0xFFFFC857), Triple(-88, 10, 0xFF9FD3E6), Triple(90, 14, 0xFFC8492C), Triple(-50, 76, 0xFFFFC857),
    Triple(56, 80, 0xFF4C7A3C), Triple(0, -96, 0xFF9FD3E6), Triple(-20, 96, 0xFFF28C38), Triple(104, -26, 0xFF4C7A3C), Triple(-104, -28, 0xFFC8492C),
)

/** La coche se dessine, quelques confettis aux couleurs de la marque. */
@Composable
private fun Celebration(title: String, sub: String) {
    val pop = remember { Animatable(0.4f) }
    val draw = remember { Animatable(0f) }
    val burst = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch { pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)) }
        launch { delay(250); draw.animateTo(1f, tween(450)) }
        launch { delay(200); burst.animateTo(1f, tween(1000)) }
    }
    Column(
        Modifier.fillMaxSize().background(Sable.copy(alpha = 0.94f)).clickable(enabled = false) {}
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically),
    ) {
        Box(Modifier.size(132.dp), contentAlignment = Alignment.Center) {
            Sparks.forEach { (dx, dy, c) ->
                val p = burst.value
                Box(
                    Modifier.graphicsLayer {
                        translationX = dx.dp.toPx() * p; translationY = dy.dp.toPx() * p
                        rotationZ = 200f * p
                        alpha = if (p < 0.25f) p * 4f else 1f - (p - 0.25f) / 0.75f
                    }.size(10.dp).clip(RoundedCornerShape(3.dp)).background(Color(c))
                )
            }
            Box(Modifier.size(132.dp).scale(pop.value).clip(CircleShape).background(DoneBg), contentAlignment = Alignment.Center) {
                Box(Modifier.size(112.dp).clip(CircleShape).background(DoneSolid), contentAlignment = Alignment.Center) {
                    Check(66.dp, Color.White, width = 2.8f, progress = draw.value)
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(horizontal = 24.dp)) {
            Text(title, style = display(26.sp), textAlign = TextAlign.Center)
            Text(sub, fontSize = 15.sp, color = TextSec, textAlign = TextAlign.Center)
        }
    }
}

private data class Person(val id: String, val name: String, val child: Boolean, val colors: Pair<Color, Color>)

/** Qui peut avoir été là : les comptes de la tribu, puis ses enfants. */
@Composable
private fun people(vm: LinkViewModel): List<Person> {
    val tribe by vm.tribe.collectAsState()
    val children by vm.children.collectAsState()
    val me = vm.currentUserId()
    val accounts = tribe?.members.orEmpty().filter { it.sharing || it.userId == me }
        .map { it.userId to (if (it.userId == me) it.displayName.ifBlank { "Moi" } else it.displayName.ifBlank { it.email.ifBlank { "Membre" } }) }
        .let { list -> if (me != null && list.none { it.first == me }) listOf(me to "Moi") + list else list }
    return (accounts.map { Triple(it.first, it.second, false) } + children.map { Triple(it.id, it.name, true) })
        .mapIndexed { i, (id, name, child) -> Person(id, name, child, AvatarColors[i % AvatarColors.size]) }
}

@Composable
private fun Stars(value: Int, modifier: Modifier = Modifier, size: Dp = 15.dp) {
    Row(modifier.semantics { contentDescription = "$value sur 5" }, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(5) { i -> Icon(if (i < value) Icons.Filled.Star else Icons.Outlined.StarBorder, null, tint = Braise, modifier = Modifier.size(size)) }
    }
}

@Composable
private fun RoundAvatar(p: Person, size: Dp, fontSize: Int, modifier: Modifier = Modifier) {
    Box(modifier.size(size).clip(CircleShape).background(p.colors.first), contentAlignment = Alignment.Center) {
        Text(p.name.trim().take(1).uppercase().ifEmpty { "?" }, style = display(fontSize.sp, color = p.colors.second))
    }
}

@Composable
private fun ChoicePill(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (selected) Sable else Ecorce,
        modifier = Modifier.height(38.dp).clip(CircleShape).background(if (selected) Ecorce else CardColor)
            .then(if (selected) Modifier else Modifier.border(1.5.dp, Line, CircleShape))
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics { stateDescription = if (selected) "Choisi" else "Non choisi" }
            .padding(horizontal = 14.dp).wrapContentHeight(),
    )
}

/** Feuille « Comment c'était ? » : tout y est facultatif. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun MemorySheet(req: MemoryRequest, done: DoneController, vm: LinkViewModel) {
    val link = req.link
    val edit = req.edit
    val scope = rememberCoroutineScope()
    val now = remember { System.currentTimeMillis() }
    val everyone = people(vm)
    val words = Done.words(link.category)
    val choices = remember(req) {
        val base = Done.dayChoices(now)
        val extra = edit?.doneOn ?: req.day
        if (extra != null && base.none { it.second == extra }) base + ((req.dayLabel.takeIf { edit == null } ?: Done.formatDay(extra, now)) to extra) else base
    }
    var day by remember { mutableStateOf(edit?.doneOn ?: req.day ?: choices.first().second) }
    var picking by remember { mutableStateOf(false) }
    var who by remember { mutableStateOf(if (edit != null) (edit.withUserIds + edit.withChildIds).toSet() else everyone.map { it.id }.toSet()) }
    var rating by remember { mutableIntStateOf(edit?.rating ?: 0) }
    var note by remember { mutableStateOf(edit?.note.orEmpty()) }
    var redo by remember { mutableStateOf(link.redo) }
    var photoUrl by remember { mutableStateOf(edit?.photoUrl.orEmpty()) }
    var localPhoto by remember { mutableStateOf<String?>(null) }
    var uploading by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val custom = choices.none { it.second == day }

    fun input() = DoneInput(
        doneOn = day,
        withUserIds = everyone.filter { !it.child && it.id in who }.map { it.id },
        withChildIds = everyone.filter { it.child && it.id in who }.map { it.id },
        rating = rating, note = note.trim(), photoUrl = photoUrl, redo = redo,
    )
    fun run(call: suspend () -> Boolean) {
        busy = true
        scope.launch {
            // Un refus s'affiche en toast, une fois la feuille refermée (les feuilles passent devant)
            call()
            busy = false
            done.closeSheet()
        }
    }

    ModalBottomSheet(
        onDismissRequest = done::closeSheet,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = SheetColor,
        dragHandle = { SheetHandle() },
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(if (edit != null) "Notre souvenir" else if (Done.isDone(link)) "${words.again} ?" else words.ask, style = display(24.sp))
                Text("${link.title} · tout est facultatif", fontSize = 14.sp, color = TextSec, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Quand ?", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextSec)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    choices.forEach { (label, d) -> ChoicePill(label, day == d) { day = d } }
                    ChoicePill(if (custom) Done.formatDay(day, now) else "Choisir…", custom) { picking = true }
                }
            }

            if (everyone.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Qui était là ?", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextSec)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        everyone.forEach { p ->
                            val on = p.id in who
                            Column(
                                Modifier.width(56.dp).alpha(if (on) 1f else 0.55f).clip(RoundedCornerShape(12.dp))
                                    .clickable(role = Role.Checkbox) { who = if (on) who - p.id else who + p.id }
                                    .semantics(mergeDescendants = true) { stateDescription = if (on) "Présent" else "Absent" },
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Box(Modifier.size(54.dp), contentAlignment = Alignment.Center) {
                                    if (on) Box(Modifier.size(54.dp).border(2.dp, DoneInk, CircleShape))
                                    RoundAvatar(p, 46.dp, 17)
                                    if (on) {
                                        Box(
                                            Modifier.align(Alignment.BottomEnd).size(20.dp).clip(CircleShape).background(SheetColor).padding(2.dp).clip(CircleShape).background(DoneSolid),
                                            contentAlignment = Alignment.Center,
                                        ) { Check(11.dp, Color.White, width = 3.4f) }
                                    }
                                }
                                Text(p.name, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Ecorce, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("C'était comment ?", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextSec)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    for (n in 1..5) {
                        val s by animateFloatAsState(if (rating == n) 1.15f else 1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "étoile")
                        Box(
                            Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Button) { rating = if (rating == n) 0 else n }
                                .semantics { contentDescription = "$n sur 5" },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(if (rating >= n) Icons.Filled.Star else Icons.Outlined.StarBorder, null, tint = if (rating >= n) Braise else Dash, modifier = Modifier.size(34.dp).scale(s))
                        }
                    }
                    Text(Done.starWord(rating), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = PeachInk, modifier = Modifier.padding(start = 6.dp).semantics { liveRegion = LiveRegionMode.Polite })
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val preview = localPhoto ?: photoUrl.ifBlank { null }
                Box(
                    Modifier.size(96.dp).clip(RoundedCornerShape(18.dp)).background(CardColor)
                        .then(if (preview == null) Modifier.border(2.dp, Dash, RoundedCornerShape(18.dp)) else Modifier)
                        .clickable(role = Role.Button, enabled = !uploading && vm.imagePickerGallery != null) {
                            vm.imagePickerGallery?.invoke { path ->
                                localPhoto = path
                                uploading = true
                                scope.launch {
                                    val url = vm.uploadDonePhoto(path)
                                    uploading = false
                                    if (url != null) photoUrl = url else localPhoto = null
                                }
                            }
                        }
                        .semantics { contentDescription = if (preview == null) "Ajouter une photo" else "Changer la photo" },
                    contentAlignment = Alignment.Center,
                ) {
                    if (preview != null) {
                        AsyncImage(preview, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Outlined.PhotoCamera, null, tint = TextSec, modifier = Modifier.size(24.dp))
                            Text("Photo", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextSec)
                        }
                    }
                    if (uploading) {
                        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                        }
                    }
                }
                CampField(
                    "Un petit mot", note, { note = it }, Modifier.weight(1f), placeholder = "Un petit mot pour s'en souvenir…",
                    singleLine = false, minLines = 3, maxLength = 280,
                )
            }

            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(CardColor).padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text("À refaire", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Ecorce)
                    Text("On la reproposera l'an prochain", fontSize = 13.sp, color = TextSec)
                }
                CampSwitch(redo, { redo = it }, "À refaire")
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.Lock, null, tint = TextSec, modifier = Modifier.size(15.dp))
                Text("Le souvenir reste dans la tribu, même si l'idée est publique.", fontSize = 12.sp, color = TextSec)
            }

            CampButton(
                if (edit != null) "Enregistrer" else "Garder ce souvenir",
                { run { if (edit != null) done.update(link, edit.id, input()) else done.mark(link, input()) } },
                Modifier.fillMaxWidth(), enabled = !busy && !uploading,
            )
            if (edit == null) {
                // Sans souvenir, mais au jour choisi (le jour de la sortie depuis la question du lendemain)
                TextButton(onClick = { run { done.mark(link, DoneInput(doneOn = day)) } }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Text("Juste marquer comme fait", color = TerracottaDark, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
    if (picking) {
        val (y, m, d) = day.split("-").map { it.toIntOrNull() ?: 0 }.let { Triple(it.getOrElse(0) { 0 }, it.getOrElse(1) { 1 }, it.getOrElse(2) { 1 }) }
        val value = java.util.Calendar.getInstance().apply { clear(); set(y, m - 1, d, 12, 0) }.timeInMillis
        DateWheelSheet("Quel jour ?", data.DateWheel.Kind.BIRTH, value, onConfirm = { day = Done.dayOf(it) }, onDismiss = { picking = false })
    }
}

/** Appui long sur une vignette : Marquer comme fait / On l'a refait / Plus fait (retirer). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DoneMenuSheet(link: Link, done: DoneController) {
    val made = Done.isDone(link)
    ModalBottomSheet(onDismissRequest = { done.menu = null }, containerColor = SheetColor, dragHandle = { SheetHandle() }) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(link.title, style = display(22.sp), maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(bottom = 8.dp))
            if (!made) MenuRow(Icons.Outlined.Check, "Marquer comme fait", tint = DoneInk) { done.menu = null; done.quick(link) }
            MenuRow(Icons.Outlined.Refresh, if (made) Done.words(link.category).again else "Garder un souvenir") { done.menu = null; done.remember(link) }
            if (made) MenuRow(Icons.Outlined.Delete, "Plus fait (retirer)", danger = true) { done.menu = null; done.unmark(link) }
        }
    }
}

/**
 * Coche d'une vignette, en bas à droite du visuel : vide = un toucher marque
 * l'idée comme faite ; verte = elle l'est (le toucher ouvre la fiche) ; jaune
 * « Fait ? » = la sortie datée est passée. Rien si le lecteur ne voit pas l'état.
 */
@Composable
fun DoneTick(link: Link, modifier: Modifier = Modifier, small: Boolean = false) {
    val done = LocalDone.current ?: return
    if (!done.canSee(link)) return
    val now = remember { System.currentTimeMillis() }
    val n = link.doneCount
    when {
        n > 0 -> Row(
            modifier.padding(if (small) 0.dp else 6.dp).height(if (small) 22.dp else 32.dp).widthIn(min = if (small) 22.dp else 32.dp)
                .then(if (small) Modifier.clip(CircleShape).background(CardColor).padding(2.dp) else Modifier.shadow(4.dp, CircleShape, spotColor = DoneSolid))
                .clip(CircleShape).background(DoneSolid).padding(horizontal = if (n > 1 && !small) 8.dp else 0.dp)
                .semantics { contentDescription = "${Done.label(link, now)}, voir le souvenir" },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterHorizontally),
        ) {
            Check(if (small) 12.dp else 15.dp, Color.White)
            if (n > 1 && !small) Text("×$n", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
        !done.canMark(link) -> Unit
        Done.awaitingConfirmation(link, now) -> Box(
            modifier.height(44.dp).clip(CircleShape)
                .clickable(role = Role.Button) { done.remember(link, Done.dayOf(Done.eventMs(link) ?: now), Done.promptDay(link, now)) }
                .padding(horizontal = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "Fait ?", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF3A211B),
                modifier = Modifier.clip(CircleShape).background(Color(0xFFFFC857)).padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
        else -> Box(
            modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Button) { done.quick(link) }
                .semantics { contentDescription = "Marquer comme fait" },
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(32.dp).clip(CircleShape).background(CardColor.copy(alpha = 0.92f)).border(1.5.dp, Dash, CircleShape), contentAlignment = Alignment.Center) {
                Check(15.dp, TextMuted)
            }
        }
    }
}

/**
 * Méta d'une vignette : état fait (vert), ou « Samedi · c'était bien ? » pour
 * qui peut répondre (pas un lecteur du cercle) ; null sinon. Même règle que doneMeta (lib/done.ts).
 */
@Composable
fun doneMeta(link: Link, enabled: Boolean): Pair<String, Color>? {
    val done = LocalDone.current
    if (!enabled || done == null || !done.canSee(link)) return null
    val now = remember { System.currentTimeMillis() }
    return when {
        Done.isDone(link) -> Done.label(link, now) to DoneInk
        Done.awaitingConfirmation(link, now) && done.canMark(link) -> Done.awaitingLabel(link, now) to PeachInk
        else -> null
    }
}

/** Anneau « 4 sur 8 faites » d'un dossier. */
@Composable
fun DoneProgressRing(progress: Done.Progress) {
    val sweep by animateFloatAsState(360f * progress.done / progress.total, tween(700), label = "avancement")
    val track = DoneBg
    val ink = DoneInk
    Row(
        Modifier.fillMaxWidth().card(RoundedCornerShape(20.dp), 4.dp).padding(horizontal = 14.dp, vertical = 12.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(52.dp)) {
                val w = 7.dp.toPx()
                val inset = w / 2 + 1.5.dp.toPx()
                val arc = Size(size.width - 2 * inset, size.height - 2 * inset)
                drawArc(track, 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(w))
                if (sweep > 0f) drawArc(ink, -90f, sweep, false, Offset(inset, inset), arc, style = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            Text("${progress.done}", style = display(15.sp, color = DoneInk))
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(Done.progressLabel(progress), style = display(17.sp, FontWeight.Bold))
            Text(Done.progressHint(progress), fontSize = 13.sp, color = TextSec)
        }
    }
}

/** Bandeau sombre : la sortie datée est passée, « Vous y êtes allés ? ». */
@Composable
fun DonePromptBanner(link: Link) {
    val done = LocalDone.current ?: return
    val now = remember { System.currentTimeMillis() }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color(0xFF3A211B)).border(1.dp, CardBorder, RoundedCornerShape(20.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFFF8DCD3)), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.CalendarMonth, null, tint = Color(0xFFB2412A), modifier = Modifier.size(22.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(Done.promptKicker(link, now), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFC857))
                Text(Done.promptQuestion(link.category, link.title), style = display(17.sp, FontWeight.Bold, OnDark), lineHeight = 20.sp)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Oui, on y était !", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF3A211B), textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFFFFC857))
                    .clickable(role = Role.Button) { done.remember(link, Done.dayOf(Done.eventMs(link) ?: now), Done.promptDay(link, now)) }.wrapContentHeight(),
            )
            Text(
                "Pas cette fois", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = OnDark, textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(14.dp)).background(OnDark.copy(alpha = 0.12f))
                    .clickable(role = Role.Button) { done.dismiss(link) }.wrapContentHeight(),
            )
        }
    }
}

/** Tampon « Fait » posé en biais sur le visuel de la fiche (700 ms, ressort). */
@Composable
fun DoneStamp(label: String, modifier: Modifier = Modifier) {
    val drop = remember { Animatable(0f) }
    LaunchedEffect(Unit) { drop.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessLow)) }
    Row(
        modifier.graphicsLayer {
            val p = drop.value
            scaleX = 2.2f - 1.2f * p; scaleY = scaleX
            rotationZ = -18f + 12f * p
            alpha = p.coerceIn(0f, 1f)
        }.shadow(6.dp, RoundedCornerShape(12.dp), spotColor = DoneSolid).clip(RoundedCornerShape(12.dp)).background(DoneSolid).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Check(16.dp, Color.White)
        Text(label, style = display(14.sp, color = Color.White))
    }
}

/** Pastille verte « Fait le 26 oct. » : remplace le bouton une fois l'idée faite. */
@Composable
fun DonePill(link: Link, modifier: Modifier = Modifier) {
    val now = remember { System.currentTimeMillis() }
    Row(
        modifier.height(40.dp).clip(RoundedCornerShape(14.dp)).background(DoneBg).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Check(16.dp, DoneInk, width = 2.8f)
        Text(Done.label(link, now), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = DoneInk)
    }
}

/** Puce « 120 familles l'ont fait » d'une idée publique (Explorer). */
@Composable
fun DoneFamiliesChip(count: Int, modifier: Modifier = Modifier, small: Boolean = false) {
    if (count <= 0) return
    Row(
        modifier.clip(CircleShape).background(DoneBg).padding(horizontal = if (small) 8.dp else 11.dp, vertical = if (small) 2.dp else 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (small) 4.dp else 6.dp),
    ) {
        Check(if (small) 11.dp else 14.dp, DoneInk)
        Text(Done.familiesLabel(count), fontSize = if (small) 11.sp else 13.sp, fontWeight = FontWeight.Bold, color = DoneInk, maxLines = 1)
    }
}

/** Carte « Notre souvenir » de la fiche : le dernier, et les précédents si l'idée a été refaite. */
@Composable
fun MemoryCard(link: Link, vm: LinkViewModel) {
    val done = LocalDone.current ?: return
    val everyone = people(vm)
    val now = remember { System.currentTimeMillis() }
    var dones by remember(link.id) { mutableStateOf<List<LinkDone>>(emptyList()) }
    var all by remember(link.id) { mutableStateOf(false) }
    LaunchedEffect(link.id, link.doneCount, link.lastDoneAt, done.version) { vm.loadDones(link.id)?.let { dones = it } }
    val last = dones.firstOrNull() ?: return
    fun present(d: LinkDone) = everyone.filter { it.id in (if (it.child) d.withChildIds else d.withUserIds) }
    val canMark = done.canMark(link)
    val pop = remember { Animatable(0.9f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy)) }

    Column(Modifier.fillMaxWidth().scale(pop.value).card(RoundedCornerShape(20.dp), 4.dp)) {
        if (last.photoUrl.isNotBlank()) {
            AsyncImage(last.photoUrl, "Photo souvenir", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().height(150.dp))
        }
        Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("NOTRE SOUVENIR", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DoneInk, letterSpacing = 0.6.sp)
                if (last.rating > 0) Stars(last.rating)
            }
            if (last.note.isNotBlank()) Text("« ${last.note} »", fontSize = 15.sp, lineHeight = 21.sp, color = Ecorce2)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val who = present(last)
                if (who.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy((-6).dp)) {
                        who.take(5).forEach { p -> RoundAvatar(p, 24.dp, 10, Modifier.clip(CircleShape).background(CardColor).padding(2.dp)) }
                    }
                }
                Text(Done.memoryLine(who.map { it.name }, last.doneOn, dones.size, now), fontSize = 13.sp, color = TextSec)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (link.redo) Tag("À refaire", PeachBg, PeachInk, Icons.Outlined.Refresh)
                if (canMark) {
                    Text(
                        "Modifier", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TerracottaDark,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(role = Role.Button) { done.remember(link, edit = last) }.padding(vertical = 6.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                if (dones.size > 1) {
                    Text(
                        if (all) "Masquer" else "Voir les ${dones.size} souvenirs", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TerracottaDark,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(role = Role.Button) { all = !all }.padding(vertical = 6.dp),
                    )
                }
            }
            if (all) {
                dones.drop(1).forEach { d ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(Done.formatDay(d.doneOn, now), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Ecorce)
                                if (d.rating > 0) Stars(d.rating, size = 12.dp)
                            }
                            if (d.note.isNotBlank()) Text("« ${d.note} »", fontSize = 13.sp, color = Ecorce2)
                            present(d).takeIf { it.isNotEmpty() }?.let { Text("Avec ${Done.joinNames(it.map { p -> p.name })}", fontSize = 13.sp, color = TextSec) }
                        }
                        if (canMark) {
                            Box(
                                Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Button) { done.remove(link, d.id) }
                                    .semantics { contentDescription = "Retirer le souvenir du ${Done.formatDay(d.doneOn, now)}" },
                                contentAlignment = Alignment.Center,
                            ) { Icon(Icons.Outlined.Delete, null, tint = TextMuted, modifier = Modifier.size(16.dp)) }
                        }
                    }
                }
            }
        }
    }
}

/** Fine barre verte et « 4/8 » d'une carte de dossier. */
@Composable
fun DoneFolderBar(progress: Done.Progress, modifier: Modifier = Modifier) {
    Row(
        modifier.semantics(mergeDescendants = true) { contentDescription = Done.progressLabel(progress) },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.weight(1f).height(6.dp).clip(CircleShape).background(Sable2)) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(progress.done.toFloat() / progress.total).clip(CircleShape).background(DoneInk))
        }
        Text("${progress.done}/${progress.total}", fontSize = 12.sp, color = TextSec)
    }
}

/** Une idée faite de catégorie Cadeau (hors liste) : vignette à 60 %, titre barré. */
fun doneDimmed(link: Link, enabled: Boolean): Boolean = enabled && Done.dimmed(link) && link.category == LinkCategory.CADEAU
