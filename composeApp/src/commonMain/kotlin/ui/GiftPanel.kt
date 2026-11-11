package ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import data.Gifts
import kotlinx.coroutines.launch
import ui.campfire.ButtonKind
import ui.campfire.CampButton
import ui.campfire.SheetHandle
import ui.campfire.display
import viewmodel.LinkViewModel

/** Couleurs d'un état de cadeau (maquette « Offrir une idée », design system GiftReservation). */
fun giftColors(tone: Gifts.Tone): Pair<Color, Color> = when (tone) {
    Gifts.Tone.FREE, Gifts.Tone.TAKEN -> Sable2 to TextSec
    Gifts.Tone.MINE -> Prune to Soleil
    Gifts.Tone.DONE -> (CategoryPalettes["RECETTE"]?.bg ?: Sable2) to (CategoryPalettes["RECETTE"]?.ink ?: Ecorce)
}

fun giftIcon(status: String): ImageVector = when (status) {
    "mine" -> Icons.Outlined.CardGiftcard
    "pending" -> Icons.Outlined.MailOutline
    "reserved" -> Icons.Outlined.Lock
    "bought", "mine_bought" -> Icons.Outlined.Check
    else -> Icons.Outlined.Add
}

/** Pastille d'état d'un cadeau, jamais qui l'offre (posée sur le visuel des cartes). */
@Composable
fun GiftTag(status: String, modifier: Modifier = Modifier) {
    val s = Gifts.state(status)
    val (bg, fg) = giftColors(s.tone)
    Row(
        modifier.clip(RoundedCornerShape(8.dp)).background(bg).padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(giftIcon(status), null, tint = fg, modifier = Modifier.size(12.dp))
        Text(s.label, color = fg, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

/**
 * Fiche d'une idée Cadeau d'une liste de cadeaux. La famille de la liste voit
 * seulement « Réservé » ou « Offert » ; les autres proches peuvent l'offrir.
 */
@Composable
fun GiftPanel(viewModel: LinkViewModel, linkId: String, title: String, status: String, canOffer: Boolean) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var claimed by remember { mutableStateOf(false) }
    var confirmRelease by remember { mutableStateOf(false) }
    val s = Gifts.state(status)
    val (bg, fg) = giftColors(s.tone)
    fun act(action: String) {
        busy = true
        scope.launch {
            val next = viewModel.giftAction(linkId, action)
            busy = false
            if (next != null && action == "reserve") claimed = true
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(bg).padding(horizontal = 14.dp, vertical = 12.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(giftIcon(status), null, tint = fg, modifier = Modifier.size(22.dp))
            Column(Modifier.weight(1f)) {
                Text(s.long, color = fg, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(if (canOffer) s.sub else "Liste de cadeaux : personne ne voit qui offre quoi.", color = fg.copy(alpha = 0.85f), fontSize = 13.sp)
            }
        }
        if (canOffer && s.free) {
            CampButton("Je l'offre", { act("reserve") }, Modifier.fillMaxWidth(), icon = Icons.Outlined.CardGiftcard, enabled = !busy)
        }
        if (canOffer && status == "mine") {
            CampButton("C'est acheté", { act("bought") }, Modifier.fillMaxWidth(), kind = ButtonKind.Dark, icon = Icons.Outlined.Check, enabled = !busy)
            TextButton(onClick = { confirmRelease = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Libérer ce cadeau", color = CoralInk, fontWeight = FontWeight.SemiBold)
            }
        }
        if (canOffer && status == "mine_bought") {
            TextButton(onClick = { act("unbought") }, modifier = Modifier.fillMaxWidth()) {
                Text("Finalement, pas encore acheté", color = TerracottaDark, fontWeight = FontWeight.SemiBold)
            }
        }
    }

    if (confirmRelease) {
        ConfirmSheet(
            "Libérer ce cadeau ?",
            "« $title » redeviendra libre et les autres offreurs de la liste en seront prévenus. Personne ne saura que c'était vous.",
            "Libérer",
            onConfirm = { confirmRelease = false; act("release") },
            onDismiss = { confirmRelease = false },
        )
    }
    if (claimed) ClaimedSheet(title) { claimed = false }
}

/** « C'est vous qui l'offrez ! » : le paquet surgit, comme dans la maquette. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClaimedSheet(title: String, onDismiss: () -> Unit) {
    val pop = remember { Animatable(0.3f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = SheetColor, dragHandle = { SheetHandle() }) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(112.dp).scale(pop.value).clip(CircleShape).background(CategoryPalettes["CADEAU"]?.bg ?: Sable2), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.CardGiftcard, null, tint = Color(0xFFC24F78), modifier = Modifier.size(64.dp))
            }
            Text("C'est vous qui l'offrez !", style = display(28.sp), textAlign = TextAlign.Center)
            Text(
                "« $title » est réservé pour vous. Les autres voient seulement qu'il est pris ; personne ne sait qui l'offre.",
                fontSize = 14.sp, color = TextSec, textAlign = TextAlign.Center,
            )
            CampButton("Parfait", onDismiss, Modifier.fillMaxWidth())
        }
    }
}
