package ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import data.Folder
import data.Gifts
import data.Link
import kotlinx.coroutines.launch
import ui.campfire.*
import viewmodel.LinkViewModel

/**
 * « Offrir une idée » sur un dossier ouvert par un lien de partage : chacun dit
 * « Je l'offre » pour éviter les doublons, sans que personne sache qui offre
 * quoi. Même écran que components/GiftList.tsx sur le web.
 */
@Composable
fun SharedGiftList(token: String, folder: Folder, gifts: List<Link>, viewModel: LinkViewModel, onChanged: () -> Unit) {
    val pending by viewModel.pendingGifts.collectAsState()
    var filter by remember { mutableStateOf(Gifts.Filter.ALL) }
    var open by remember { mutableStateOf<String?>(null) }
    var claimed by remember { mutableStateOf<String?>(null) }
    val now = System.currentTimeMillis()
    fun statusOf(l: Link) = Gifts.sharedStatus(l.giftStatus, (pending[l.id] ?: 0L) > now)
    val progress = Gifts.progress(gifts.map(::statusOf))
    val shown = gifts.filter { Gifts.matches(statusOf(it), filter) }
    val owner = folder.ownerDisplayName.ifBlank { "la personne qui a fait la liste" }
    val done = CategoryPalettes["RECETTE"]?.ink ?: Ecorce

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Prune).padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Outlined.AutoAwesome, null, tint = Color(0xFFFFC857), modifier = Modifier.size(20.dp))
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = OnDark, fontWeight = FontWeight.Bold)) { append("La surprise est gardée.") }
                    append(" Personne ne voit qui offre quoi, pas même $owner.")
                },
                color = Soleil, fontSize = 13.sp, lineHeight = 18.sp,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(progress.label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Ecorce)
                Text(progress.freeLabel, fontSize = 13.sp, color = TextSec)
            }
            Row(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(Sable2)) {
                if (progress.pDone > 0) Box(Modifier.fillMaxHeight().weight(progress.pDone.toFloat()).background(done))
                if (progress.pTaken > 0) Box(Modifier.fillMaxHeight().weight(progress.pTaken.toFloat()).background(Prune))
                val rest = 100 - progress.pDone - progress.pTaken
                if (rest > 0) Spacer(Modifier.weight(rest.toFloat()))
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Gifts.Filter.entries.forEach { f -> Pill(f.label, filter == f, { filter = f }, idleBg = CardColor, bordered = true) }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            shown.forEach { l -> GiftRow(l, statusOf(l)) { open = l.id } }
        }
        if (shown.isEmpty()) {
            val mine = filter == Gifts.Filter.MINE
            EmptyBlock(
                Icons.Outlined.CardGiftcard,
                if (mine) "Vous n'offrez rien pour l'instant" else "Tout a trouvé preneur",
                if (mine) "Choisissez une idée libre : personne d'autre ne pourra la prendre." else "Merci pour eux !",
            )
        }
    }

    gifts.find { it.id == open }?.let { current ->
        GiftSheet(
            token = token, link = current, owner = owner, status = statusOf(current), viewModel = viewModel,
            onClose = { open = null },
            onClaimed = { open = null; claimed = current.title },
            onChanged = onChanged,
        )
    }
    claimed?.let { title -> ClaimedSheet(title) { claimed = null } }
}

@Composable
private fun GiftRow(link: Link, status: String, onOpen: () -> Unit) {
    val s = Gifts.state(status)
    val meta = listOf(priceLabel(link.price), link.location).filter { it.isNotBlank() }.joinToString(" · ")
    val bought = status == "bought" || status == "mine_bought"
    Box(
        Modifier.fillMaxWidth().alpha(if (!s.dim) 1f else if (s.strike) 0.6f else 0.75f)
            .card(RoundedCornerShape(22.dp), 4.dp).clickable(role = Role.Button, onClickLabel = "Voir ce cadeau", onClick = onOpen),
    ) {
        // Ruban en coin : c'est moi qui l'offre
        if (status == "mine") Box(Modifier.align(Alignment.TopEnd).offset(x = 30.dp, y = 12.dp).size(96.dp, 18.dp).rotate(38f).background(Prune))
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box {
                IdeaVisual(link.copy(giftStatus = ""), 64.dp, Modifier.width(64.dp).clip(RoundedCornerShape(16.dp)), iconSize = 30.dp)
                if (bought) {
                    Box(
                        Modifier.align(Alignment.BottomEnd).offset(x = 4.dp, y = 4.dp).size(24.dp).clip(CircleShape).background(CardColor).padding(2.dp)
                            .clip(CircleShape).background(CategoryPalettes["RECETTE"]?.ink ?: Ecorce),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Outlined.Check, null, tint = Color.White, modifier = Modifier.size(13.dp)) }
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(link.title, style = display(15.sp, FontWeight.Bold), textDecoration = if (s.strike) TextDecoration.LineThrough else null)
                if (meta.isNotEmpty()) Text(meta, fontSize = 12.sp, color = TextSec)
                GiftTag(status)
            }
            Icon(Icons.Outlined.ChevronRight, null, tint = TextMuted, modifier = Modifier.size(18.dp))
        }
    }
}

private enum class GiftStep { GIFT, WHO, CHECK_MAIL, RELEASE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GiftSheet(
    token: String,
    link: Link,
    owner: String,
    status: String,
    viewModel: LinkViewModel,
    onClose: () -> Unit,
    onClaimed: () -> Unit,
    onChanged: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val uri = LocalUriHandler.current
    var step by remember { mutableStateOf(GiftStep.GIFT) }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val s = Gifts.state(status)
    val (bg, fg) = giftColors(s.tone)
    val cat = categoryUi(link.category)

    // Lance une action ; en cas de refus, la liste est rechargée (un autre l'a peut-être pris)
    fun run(call: suspend () -> String?, then: () -> Unit) {
        busy = true
        error = null
        scope.launch {
            val failure = call()
            busy = false
            onChanged()
            if (failure != null) error = failure else then()
        }
    }
    fun reserve() {
        if (viewModel.currentUserId() == null) { step = GiftStep.WHO; return }
        run({ viewModel.reserveSharedGift(token, link.id) }, onClaimed)
    }
    fun act(action: String) = run({ viewModel.sharedGiftAction(link.id, action) }, onClose)

    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = SheetColor,
        dragHandle = { SheetHandle() },
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                when (step) {
                    GiftStep.GIFT -> link.title
                    GiftStep.WHO -> "Vous offrez ${link.title}"
                    GiftStep.CHECK_MAIL -> "Vérifiez vos e-mails"
                    GiftStep.RELEASE -> "Libérer ce cadeau ?"
                },
                style = display(24.sp),
            )
            when (step) {
                GiftStep.GIFT -> {
                    Box {
                        IdeaVisual(link.copy(giftStatus = ""), 150.dp, Modifier.clip(RoundedCornerShape(22.dp)), iconSize = 64.dp)
                        Text(
                            cat.label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = cat.tint,
                            modifier = Modifier.padding(12.dp).clip(CircleShape).background(CardColor).padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                    val meta = listOf(priceLabel(link.price), link.location).filter { it.isNotBlank() }.joinToString(" · ")
                    val site = link.url.takeIf { it.startsWith("https://") || it.startsWith("http://") }
                    if (meta.isNotEmpty() || site != null) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (meta.isNotEmpty()) Text(meta, fontSize = 14.sp, color = TextSec, modifier = Modifier.weight(1f, fill = false))
                            if (site != null) {
                                Text(
                                    "Voir le site ↗", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TerracottaDark,
                                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(role = Role.Button) { runCatching { uri.openUri(site) } }
                                        .padding(vertical = 6.dp),
                                )
                            }
                        }
                    }
                    if (link.description.isNotBlank()) {
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(CardColor).padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Box(Modifier.size(32.dp).clip(CircleShape).background(Prune), contentAlignment = Alignment.Center) {
                                Text(owner.take(1).uppercase(), style = display(13.sp, FontWeight.ExtraBold, Soleil))
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("Le mot de $owner", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Ecorce)
                                Text(link.description, fontSize = 14.sp, lineHeight = 19.sp, color = Ecorce2)
                            }
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(bg).padding(horizontal = 14.dp, vertical = 12.dp)
                            .semantics { liveRegion = LiveRegionMode.Polite },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(giftIcon(status), null, tint = fg, modifier = Modifier.size(22.dp))
                        Column(Modifier.weight(1f)) {
                            Text(s.long, color = fg, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text(s.sub, color = fg.copy(alpha = 0.85f), fontSize = 13.sp)
                        }
                    }
                    error?.let { ErrorLine(it) }
                    if (s.free) CampButton("Je l'offre", ::reserve, Modifier.fillMaxWidth(), icon = Icons.Outlined.CardGiftcard, enabled = !busy)
                    if (status == "mine") {
                        CampButton("C'est acheté", { act("bought") }, Modifier.fillMaxWidth(), kind = ButtonKind.Dark, icon = Icons.Outlined.Check, enabled = !busy)
                        TextButton(onClick = { error = null; step = GiftStep.RELEASE }, modifier = Modifier.fillMaxWidth()) {
                            Text("Libérer ce cadeau", color = CoralInk, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    if (status == "mine_bought") {
                        TextButton(onClick = { act("unbought") }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                            Text("Finalement, pas encore acheté", color = TerracottaDark, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    if (status == "pending") {
                        Note("Ouvrez le lien reçu par e-mail pour confirmer. Le même lien sert ensuite à dire « C'est acheté » ou à le libérer.")
                    }
                    if (status == "reserved" || status == "bought") {
                        Note("S'il se libère, les personnes qui offrent un cadeau de cette liste sont prévenues.")
                    }
                }

                GiftStep.WHO -> {
                    Text(
                        "Pour que les autres sachent qu'il est pris. Votre prénom ne sert qu'à vous écrire : personne ne le verra sur la liste.",
                        fontSize = 13.sp, lineHeight = 18.sp, color = Ecorce2,
                    )
                    CampField(
                        "Votre prénom", name, { name = it }, maxLength = 40,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    )
                    CampField(
                        "Votre e-mail", email, { email = it }, maxLength = 254,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        hint = "Pour confirmer, puis pour dire « C'est acheté » ou libérer le cadeau.",
                    )
                    error?.let { ErrorLine(it) }
                    CampButton(
                        "Réserver ce cadeau",
                        { run({ viewModel.reserveSharedGift(token, link.id, name.trim(), email.trim()) }) { step = GiftStep.CHECK_MAIL } },
                        Modifier.fillMaxWidth(), icon = Icons.Outlined.CardGiftcard,
                        enabled = !busy && name.isNotBlank() && email.isNotBlank(),
                    )
                    Note("Déjà sur Tribbae ? Connectez-vous depuis l'onglet Ma tribu : le cadeau sera réservé tout de suite.")
                }

                GiftStep.CHECK_MAIL -> {
                    val gift = CategoryPalettes["CADEAU"]
                    Box(
                        Modifier.align(Alignment.CenterHorizontally).size(64.dp).clip(CircleShape).background(gift?.bg ?: Sable2),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Outlined.MailOutline, null, tint = gift?.ink ?: Ecorce, modifier = Modifier.size(30.dp)) }
                    Text(
                        "Nous avons écrit à ${email.trim()}. Ouvrez le lien pour confirmer que vous offrez « ${link.title} ». Sans confirmation sous 2 heures, il redevient libre.",
                        fontSize = 14.sp, lineHeight = 20.sp, color = Ecorce2, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                    )
                    CampButton("Compris", onClose, Modifier.fillMaxWidth())
                }

                GiftStep.RELEASE -> {
                    Text(
                        "« ${link.title} » redeviendra libre et les autres offreurs de la liste en seront prévenus. Personne ne saura que c'était vous.",
                        fontSize = 15.sp, lineHeight = 22.sp, color = Ecorce2,
                    )
                    error?.let { ErrorLine(it) }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CampButton("Je le garde", { error = null; step = GiftStep.GIFT }, Modifier.weight(1f), kind = ButtonKind.Ghost)
                        CampButton("Libérer", { act("release") }, Modifier.weight(1f), enabled = !busy)
                    }
                }
            }
        }
    }
}

@Composable
private fun Note(text: String) {
    Text(text, fontSize = 13.sp, lineHeight = 18.sp, color = TextSec, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun ErrorLine(text: String) {
    Text(
        text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = CoralInk,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).border(1.5.dp, CoralInk.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp).semantics { liveRegion = LiveRegionMode.Assertive },
    )
}
