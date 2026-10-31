package ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import data.DateWheel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import ui.campfire.*
import kotlin.math.abs
import kotlin.math.roundToInt

private val ITEM = 44.dp // hauteur d'un cran
private const val VISIBLE = 5 // crans visibles, celui du milieu est choisi

// Les crans s'estompent vers le haut et le bas (comme .wheel sur le web)
private val fade = Brush.verticalGradient(0f to Color.Transparent, 0.32f to Color.Black, 0.68f to Color.Black, 1f to Color.Transparent)

/**
 * Une colonne qui défile au doigt et s'arrête sur un cran (snap), avec un léger
 * retour haptique à chaque cran franchi. Lue par TalkBack comme une valeur réglable.
 */
@Composable
fun Wheel(label: String, options: List<Pair<Int, String>>, value: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    val index = options.indexOfFirst { it.first == value }.coerceAtLeast(0)
    val state = rememberLazyListState(initialFirstVisibleItemIndex = index)
    val fling = rememberSnapFlingBehavior(state, SnapPosition.Center)
    val haptic = LocalHapticFeedback.current
    val currentOptions by rememberUpdatedState(options)
    val currentValue by rememberUpdatedState(value)
    val currentOnChange by rememberUpdatedState(onChange)

    // Cran au milieu de la colonne
    val centered by remember {
        derivedStateOf {
            val info = state.layoutInfo
            val center = (info.viewportStartOffset + info.viewportEndOffset) / 2
            info.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2 - center) }?.index ?: state.firstVisibleItemIndex
        }
    }

    // Arrêt du défilement : la valeur du cran du milieu est choisie
    LaunchedEffect(state) {
        snapshotFlow { state.isScrollInProgress }.filter { !it }.collect {
            currentOptions.getOrNull(centered)?.let { (v, _) -> if (v != currentValue) currentOnChange(v) }
        }
    }
    LaunchedEffect(state) {
        snapshotFlow { centered }.drop(1).distinctUntilChanged().collect { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
    }
    // Valeur changée d'ailleurs (raccourci, mois plus court, bornes) : la colonne la rejoint
    LaunchedEffect(index, options.size) {
        if (!state.isScrollInProgress && centered != index) state.animateScrollToItem(index)
    }

    LazyColumn(
        state = state,
        flingBehavior = fling,
        contentPadding = PaddingValues(vertical = ITEM * (VISIBLE / 2)),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .height(ITEM * VISIBLE)
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent { drawContent(); drawRect(fade, blendMode = BlendMode.DstIn) }
            .semantics {
                contentDescription = label
                stateDescription = options.getOrNull(index)?.second.orEmpty()
                val last = (options.size - 1).coerceAtLeast(1).toFloat()
                progressBarRangeInfo = ProgressBarRangeInfo(index.toFloat(), 0f..last, steps = (options.size - 2).coerceAtLeast(0))
                setProgress { target -> options.getOrNull(target.roundToInt())?.let { onChange(it.first) }; true }
            },
    ) {
        itemsIndexed(options, key = { _, o -> o.first }) { i, (v, text) ->
            val distance = abs(i - centered)
            Box(
                Modifier.height(ITEM).fillMaxWidth().clearAndSetSemantics {}.clickable(role = Role.Button) { onChange(v) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text,
                    fontSize = 17.sp,
                    fontWeight = if (distance == 0) FontWeight.Bold else FontWeight.Medium,
                    color = (if (distance == 0) Ecorce else TextSec).copy(alpha = if (distance == 0) 1f else if (distance == 1) 0.7f else 0.4f),
                    maxLines = 1,
                )
            }
        }
    }
}

private fun pad(n: Int) = n.toString().padStart(2, '0')

private fun defaultValue(kind: DateWheel.Kind, now: Long): DateWheel.Parts {
    val n = DateWheel.partsOf(now)
    // Un enfant : quelques années en arrière, pour moins faire tourner les roulettes
    return if (kind == DateWheel.Kind.BIRTH) DateWheel.Parts(n.year - 4, n.month, n.day) else DateWheel.Parts(n.year, n.month, n.day)
}

/**
 * Sélecteur de date à roulettes dans une feuille du bas (jour, mois, année, et
 * heure pour une sortie). [value] null ou 0 : rien de choisi encore.
 * [onClear] : « Effacer la date » (absent quand la date est obligatoire).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateWheelSheet(
    title: String,
    kind: DateWheel.Kind,
    value: Long?,
    withTime: Boolean = false,
    onConfirm: (Long) -> Unit,
    onClear: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    val now = remember { System.currentTimeMillis() }
    val saved = value?.takeIf { it > 0 }
    val bounds = remember { DateWheel.bounds(kind, now, saved) }
    var parts by remember { mutableStateOf(DateWheel.clamp(saved?.let(DateWheel::partsOf) ?: defaultValue(kind, now), bounds)) }
    fun set(p: DateWheel.Parts) { parts = DateWheel.clamp(p, bounds) }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    fun close(then: () -> Unit) {
        scope.launch { sheet.hide() }.invokeOnCompletion { then(); onDismiss() }
    }
    val quick = remember { DateWheel.shortcuts(kind, now) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = SheetColor, dragHandle = { SheetHandle() }) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(title, style = display(24.sp))

            if (quick.isNotEmpty()) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    quick.forEach { q ->
                        val on = q.date.year == parts.year && q.date.month == parts.month && q.date.day == parts.day
                        Text(
                            q.label, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (on) Sable else Ecorce,
                            modifier = Modifier.clip(CircleShape).background(if (on) Ecorce else Sable2)
                                .clickable(role = Role.Button) { set(parts.copy(year = q.date.year, month = q.date.month, day = q.date.day)) }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                        )
                    }
                }
            }

            Text(
                DateWheel.describe(parts, withTime), style = display(18.sp, FontWeight.Bold), textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
            )

            Box(Modifier.fillMaxWidth().height(ITEM * VISIBLE)) {
                // Bande du cran choisi, derrière les colonnes
                Box(Modifier.align(Alignment.Center).fillMaxWidth().height(ITEM).clip(RoundedCornerShape(16.dp)).background(Sable2))
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    Wheel("Jour", DateWheel.dayOptions(parts.year, parts.month, bounds).map { it to "$it" }, parts.day, { set(parts.copy(day = it)) }, Modifier.width(56.dp))
                    Wheel("Mois", DateWheel.monthOptions(parts.year, bounds).map { it to DateWheel.MONTHS[it - 1] }, parts.month, { set(parts.copy(month = it)) }, Modifier.weight(1f))
                    Wheel("Année", DateWheel.yearOptions(bounds).map { it to "$it" }, parts.year, { set(parts.copy(year = it)) }, Modifier.width(72.dp))
                    if (withTime) {
                        Spacer(Modifier.width(8.dp))
                        Wheel("Heure", DateWheel.HOURS.map { it to pad(it) }, parts.hour, { set(parts.copy(hour = it)) }, Modifier.width(48.dp))
                        Text(":", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Ecorce)
                        Wheel("Minute", DateWheel.minuteOptions(parts.minute).map { it to pad(it) }, parts.minute, { set(parts.copy(minute = it)) }, Modifier.width(48.dp))
                    }
                }
            }
            if (withTime) Text("00:00 : sans heure précise", fontSize = 12.sp, color = TextMuted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onClear != null) CampButton("Effacer la date", { close(onClear) }, Modifier.weight(1f), kind = ButtonKind.Secondary)
                CampButton("Valider", { close { onConfirm(DateWheel.toMs(parts, kind)) } }, Modifier.weight(1f), icon = Icons.Outlined.Check)
            }
        }
    }
}
