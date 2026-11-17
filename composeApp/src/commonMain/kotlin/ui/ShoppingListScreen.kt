package ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import coil.compose.AsyncImage
import data.Link
import data.LinkCategory
import data.ShoppingList
import data.ShoppingList.Op
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ui.campfire.*
import viewmodel.LinkViewModel
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity

/** Rayon : icône et couleurs (thème clair et sombre). */
private data class AisleStyle(val icon: ImageVector, val bg: Color, val ink: Color)

private fun aisleStyle(aisle: String): AisleStyle = when (aisle) {
    "fruits-legumes" -> AisleStyle(Icons.Outlined.Eco, GreenBg, GreenInk)
    "boulangerie" -> AisleStyle(Icons.Outlined.BakeryDining, PeachBg, PeachInk)
    "boucherie" -> AisleStyle(Icons.Outlined.SetMeal, CoralBg, CoralInk)
    "cremerie" -> AisleStyle(Icons.Outlined.Egg, BlueBg, BlueInk)
    "epicerie" -> AisleStyle(Icons.Outlined.Kitchen, HoneyBg, HoneyInk)
    "surgeles" -> AisleStyle(Icons.Outlined.AcUnit, BlueBg, BlueInk)
    "boissons" -> AisleStyle(Icons.Outlined.LocalDrink, PlumBg, PlumInk)
    "bebe-hygiene" -> categoryUi(LinkCategory.DECORATION).let { AisleStyle(Icons.Outlined.WaterDrop, it.bg, it.ink) }
    "maison" -> AisleStyle(Icons.Outlined.Home, PlumBg, PlumInk)
    else -> AisleStyle(Icons.Outlined.MoreHoriz, Sable2, TextSec)
}

// Teintes des cartes du menu sans photo, dans l'ordre
private val menuTints: List<Pair<Color, Color>> get() = listOf(PeachBg to PeachInk, HoneyBg to HoneyInk, GreenBg to GreenInk, CoralBg to CoralInk)

private fun plural(n: Int, one: String, many: String) = "$n ${if (n > 1) many else one}"

/** « 6 bananes » → quantité « 6 », article « bananes » (comme le web). */
private fun splitQuantity(text: String): Pair<String, String> {
    val m = Regex("^(\\d+(?:[.,]\\d+)?\\s*(?:kg|g|l|cl|ml|x|paquets?|boîtes?|bouteilles?|packs?|lots?|sachets?|pots?)?)\\s+(.+)$", RegexOption.IGNORE_CASE).find(text.trim())
    return if (m != null) m.groupValues[2].trim() to m.groupValues[1].trim() else text.trim() to ""
}

/** Date d'une idée en millisecondes (millisecondes ou date ISO). */
private fun stamp(v: String): Long = v.toLongOrNull()?.let { if (it < 100_000_000_000L) it * 1000 else it }
    ?: runCatching { java.time.Instant.parse(v).toEpochMilli() }.getOrDefault(0L)

/**
 * « Les courses » : la liste de la tribu (data.ShoppingRepository), même écran
 * que le web (pages/Shopping.tsx). « Préparer » : menu de la semaine avec ses
 * parts, articles, placard, liste par rayon ; « En magasin » : grandes lignes à
 * cocher, écran allumé. [recipeId] : recette d'où l'on vient, mise au menu.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShoppingListScreen(
    viewModel: LinkViewModel,
    modifier: Modifier = Modifier,
    recipeId: String? = null,
    onBack: () -> Unit = {},
    onAddRecipe: () -> Unit = {},
    onEditRecipe: (Link) -> Unit = {},
) {
    val snap by viewModel.shopping.collectAsState()
    val links by viewModel.repository.links.collectAsState()
    val tribe by viewModel.tribe.collectAsState()
    val list = snap.list
    val me = viewModel.currentUserId().orEmpty()
    val view = remember(list) { ShoppingList.viewOf(list) }
    val arranged = view.arranged
    val picked = remember(links) { ShoppingList.pick(links) }
    val ready = picked.first

    // Relue à l'ouverture et toutes les 10 s tant que l'écran est au premier plan
    val scope = rememberCoroutineScope()
    LifecycleResumeEffect(Unit) {
        val job = scope.launch {
            while (true) {
                viewModel.syncShopping()
                delay(10_000)
            }
        }
        onPauseOrDispose { job.cancel() }
    }
    // « Préparer la liste de courses » depuis une recette : elle passe au menu
    LaunchedEffect(recipeId, snap.loaded) {
        if (recipeId == null || !snap.loaded) return@LaunchedEffect
        val r = ready.find { it.id == recipeId } ?: return@LaunchedEffect
        if (list.menu.none { it.linkId == recipeId }) viewModel.changeShopping(Op("menu_add", linkId = r.id), viewModel.recipeInfo(r))
    }

    val members = tribe?.members.orEmpty()
    fun nameOf(id: String?): String = if (id == me) "vous" else members.firstOrNull { it.userId == id }?.displayName?.ifBlank { null } ?: "un proche"
    val others = members.filter { it.userId != me }

    var chosenMode by rememberSaveable { mutableStateOf<String?>(null) }
    val mode = chosenMode ?: if (snap.loaded && list.shopperId == me && me.isNotEmpty()) "shop" else "prep"
    var sheet by rememberSaveable { mutableStateOf<String?>(null) }
    var lineSheet by remember { mutableStateOf<ShoppingList.Line?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<Pair<String, (() -> Unit)?>?>(null) }
    LaunchedEffect(toast) { if (toast != null) { delay(3200); toast = null } }

    fun change(op: Op, recipe: ShoppingList.RecipeInfo? = null) = viewModel.changeShopping(op, recipe)
    fun checkedBy(l: ShoppingList.Line) = list.checked.firstOrNull { it.key == l.key }?.by
    fun toggle(l: ShoppingList.Line, inShop: Boolean) {
        val checked = list.checked.any { it.key == l.key }
        change(Op(if (checked) "uncheck" else "check", key = l.key))
        if (inShop) toast = if (checked) null else "${l.name} dans le panier" to { change(Op("uncheck", key = l.key)) }
    }
    fun stopShopping() {
        toast = null
        change(Op("shopping_stop"))
        chosenMode = "prep"
    }

    Box(modifier.fillMaxSize().background(Sable)) {
        if (mode == "shop") {
            BackHandler { stopShopping() }
            // En magasin : l'écran reste allumé
            val hostView = LocalView.current
            DisposableEffect(Unit) {
                hostView.keepScreenOn = true
                onDispose { hostView.keepScreenOn = false }
            }
            ShopMode(
                arranged = arranged,
                menuCount = list.menu.size,
                me = me,
                checkedBy = ::checkedBy,
                nameOf = ::nameOf,
                onToggle = { toggle(it, true) },
                onStop = ::stopShopping,
                onFinish = {
                    toast = "Courses terminées · le menu reste en place" to null
                    change(Op("finish"))
                    chosenMode = "prep"
                },
            )
        } else {
            PrepMode(
                snap = snap,
                arranged = arranged,
                lines = view.lines,
                others = others.map { it.displayName.ifBlank { "un proche" } },
                shopperAway = if (list.shopperId.isNotEmpty() && list.shopperId != me) nameOf(list.shopperId) else "",
                nameOf = ::nameOf,
                checkedBy = ::checkedBy,
                onBack = onBack,
                onShare = { sheet = "share" },
                onNewList = { confirmClear = true },
                onPick = { sheet = "pick" },
                onToggle = { toggle(it, false) },
                onLine = { lineSheet = it },
                change = { op, r -> change(op, r) },
                onToast = { text, undo -> toast = text to undo },
                onShop = {
                    change(Op("shopping_start"))
                    chosenMode = "shop"
                },
            )
        }

        toast?.let { (text, undo) ->
            Row(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp, start = 16.dp, end = 16.dp)
                    .clip(RoundedCornerShape(18.dp)).background(Color(0xFF3A211B)).border(1.dp, CardBorder, RoundedCornerShape(18.dp))
                    .padding(start = 16.dp, end = if (undo != null) 10.dp else 16.dp, top = 10.dp, bottom = 10.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(Icons.Outlined.Check, null, tint = OnDark, modifier = Modifier.size(18.dp))
                Text(text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = OnDark)
                if (undo != null) {
                    Text(
                        "Annuler", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFC857),
                        modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(OnDark.copy(alpha = 0.14f))
                            .clickable(role = Role.Button) { undo(); toast = null }.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }

    when (sheet) {
        "pick" -> RecipePickerSheet(
            ready = ready,
            missing = picked.second,
            inMenu = { id -> list.menu.any { it.linkId == id } },
            menuCount = list.menu.size,
            onToggle = { r ->
                val on = list.menu.any { it.linkId == r.id }
                change(if (on) Op("menu_remove", linkId = r.id) else Op("menu_add", linkId = r.id), viewModel.recipeInfo(r))
            },
            onComplete = { sheet = null; onEditRecipe(it) },
            onAddRecipe = { sheet = null; onAddRecipe() },
            onDismiss = { sheet = null },
        )
        "share" -> ShareSheet(
            text = ShoppingList.shareText(arranged),
            shared = others.map { it.displayName.ifBlank { "un proche" } },
            onDone = { msg -> sheet = null; if (msg != null) toast = msg to null },
        )
    }
    lineSheet?.let { l ->
        LineSheet(
            line = l,
            onDismiss = { lineSheet = null },
            onSave = { qty, aisle ->
                val item = l.itemId?.let { id -> list.items.firstOrNull { it.id == id } }
                if (item != null) {
                    if (qty != l.qty || aisle != l.aisle) change(Op("item_update", id = item.id, label = item.label, quantity = qty, aisle = aisle))
                } else {
                    if (qty != l.qty) change(Op("quantity", key = l.product, value = qty))
                    if (aisle != l.aisle) change(Op("aisle", key = l.product, value = aisle))
                }
                lineSheet = null
            },
            onRemove = {
                if (l.itemId != null) change(Op("item_remove", id = l.itemId))
                else {
                    change(Op("remove", key = l.product))
                    toast = "${l.name} retiré de la liste" to { change(Op("restore", key = l.product)) }
                }
                lineSheet = null
            },
        )
    }
    if (confirmClear) {
        ConfirmSheet(
            title = "Commencer une nouvelle liste ?",
            text = "Le menu, les articles et les cases cochées sont effacés pour toute la tribu. Le placard et les rayons restent.",
            confirmLabel = "Nouvelle liste",
            onConfirm = { confirmClear = false; change(Op("clear")) },
            onDismiss = { confirmClear = false },
        )
    }
}

// ── Préparer ─────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PrepMode(
    snap: data.ShoppingRepository.State,
    arranged: ShoppingList.Arranged,
    lines: List<ShoppingList.Line>,
    others: List<String>,
    shopperAway: String,
    nameOf: (String?) -> String,
    checkedBy: (ShoppingList.Line) -> String?,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onNewList: () -> Unit,
    onPick: () -> Unit,
    onToggle: (ShoppingList.Line) -> Unit,
    onLine: (ShoppingList.Line) -> Unit,
    change: (Op, ShoppingList.RecipeInfo?) -> Unit,
    onToast: (String, (() -> Unit)?) -> Unit,
    onShop: () -> Unit,
) {
    val list = snap.list
    val empty = snap.loaded && list.menu.isEmpty() && list.items.isEmpty()
    var expanded by remember { mutableStateOf(setOf<String>()) }
    var cartOpen by rememberSaveable { mutableStateOf(false) }
    val suggestions = remember(lines) { ShoppingList.suggestions(lines) }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding()
                .padding(top = 16.dp, bottom = if (arranged.total > 0) 110.dp else 40.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            val pad = Modifier.padding(horizontal = 20.dp)
            Row(pad.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                CircleIconButton(Icons.Outlined.ChevronLeft, "Retour", onBack)
                Spacer(Modifier.weight(1f))
                if (!empty) {
                    Text(
                        "Nouvelle liste", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Ecorce,
                        modifier = Modifier.clip(CircleShape).background(CardColor).clickable(role = Role.Button, onClick = onNewList)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                }
                CircleIconButton(Icons.Outlined.Share, "Envoyer la liste", onShare)
            }
            Column(pad, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Les courses", style = display(32.sp))
                val status = when {
                    snap.offline -> "hors ligne · envoyé au retour du réseau"
                    snap.pending > 0 -> "envoi…"
                    !snap.loaded -> "chargement…"
                    else -> "à jour"
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (snap.offline) Icon(Icons.Outlined.CloudOff, null, tint = TextSec, modifier = Modifier.size(16.dp))
                    Text(
                        (if (others.isNotEmpty()) "Partagée avec ${others.joinToString(", ")}" else "Votre liste") + " · $status",
                        fontSize = 14.sp, color = TextSec,
                    )
                }
                if (shopperAway.isNotEmpty()) {
                    Text(
                        "$shopperAway est en magasin", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PlumInk,
                        modifier = Modifier.clip(CircleShape).background(PlumBg).padding(horizontal = 12.dp, vertical = 5.dp),
                    )
                }
            }

            // Au menu
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(pad.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    Text("Au menu", style = display(18.sp, FontWeight.Bold), modifier = Modifier.weight(1f))
                    val parts = list.menu.sumOf { it.servings }
                    Text(if (list.menu.isEmpty()) "Rien au menu" else "${plural(list.menu.size, "recette", "recettes")} · ${plural(parts, "part", "parts")}", fontSize = 14.sp, color = TextSec)
                }
                if (empty) {
                    Row(
                        pad.fillMaxWidth().card(RoundedCornerShape(22.dp), 3.dp).clickable(role = Role.Button, onClick = onPick).padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Box(Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(GreenBg), contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.Restaurant, null, tint = GreenInk, modifier = Modifier.size(24.dp))
                        }
                        Column {
                            Text("Qu'est-ce qu'on mange cette semaine ?", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Ecorce)
                            Text("Choisissez 2 ou 3 recettes, la liste se fait toute seule.", fontSize = 13.sp, color = TextSec)
                        }
                    }
                } else {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        list.menu.forEachIndexed { i, e ->
                            MenuCard(e, menuTints[i % menuTints.size], onRemove = {
                                change(Op("menu_remove", linkId = e.linkId), null)
                                onToast("${e.title} retirée du menu") {
                                    change(Op("menu_add", linkId = e.linkId, servings = e.servings), ShoppingList.RecipeInfo(e.title, e.ingredients, e.imageUrl, e.baseServings))
                                }
                            }, onServings = { change(Op("menu_servings", linkId = e.linkId, servings = it), null) })
                        }
                        Column(
                            Modifier.width(110.dp).height(166.dp).clip(RoundedCornerShape(22.dp)).border(2.dp, Dash, RoundedCornerShape(22.dp))
                                .clickable(role = Role.Button, onClick = onPick),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                        ) {
                            Box(Modifier.size(36.dp).clip(CircleShape).background(CardColor), contentAlignment = Alignment.Center) {
                                Icon(Icons.Outlined.Add, null, tint = Terracotta, modifier = Modifier.size(20.dp))
                            }
                            Spacer(Modifier.height(8.dp))
                            Text("Ajouter\nune recette", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextSec, textAlign = TextAlign.Center)
                        }
                    }
                }
            }

            // Ajouter un article
            Column(pad.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(CardColor).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AddItemField { text ->
                    val (label, qty) = splitQuantity(text)
                    if (label.isNotBlank()) change(Op("item_add", id = newItemId(), label = label, quantity = qty), null)
                }
                if (suggestions.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                        Text("Souvent :", fontSize = 12.sp, color = TextSec)
                        suggestions.forEach { s ->
                            val st = aisleStyle(s.aisle)
                            Row(
                                Modifier.clip(CircleShape).background(st.bg).clickable(role = Role.Button) {
                                    change(Op("item_add", id = newItemId(), label = s.label, aisle = s.aisle), null)
                                    onToast("${s.label} ajouté · ${ShoppingList.aisleName(s.aisle)}", null)
                                }.padding(horizontal = 12.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(Icons.Outlined.Add, null, tint = st.ink, modifier = Modifier.size(13.dp))
                                Text(s.label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = st.ink)
                            }
                        }
                    }
                }
            }

            // Placard
            if (arranged.staples.isNotEmpty()) {
                Column(pad.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(HoneyBg).padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(CardColor), contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.Kitchen, null, tint = HoneyInk, modifier = Modifier.size(18.dp))
                        }
                        Column {
                            Text("Déjà dans le placard ?", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Ecorce)
                            Text("Touchez ce que vous avez : ça sort de la liste.", fontSize = 13.sp, color = HoneyInk)
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        arranged.staples.forEach { l ->
                            val have = l.product in list.pantry
                            Row(
                                Modifier.clip(CircleShape).background(if (have) DoneSolid else CardColor)
                                    .clickable(role = Role.Checkbox) { change(Op(if (have) "pantry_out" else "pantry_have", key = l.product), null) }
                                    .semantics { stateDescription = if (have) "On en a" else "Pas au placard" }
                                    .padding(horizontal = 14.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                if (have) Icon(Icons.Outlined.Check, null, tint = Color.White, modifier = Modifier.size(13.dp))
                                Text(l.name, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (have) Color.White else HoneyInk)
                                if (have) Text(" · on en a", fontSize = 13.sp, color = Color.White.copy(alpha = 0.85f))
                            }
                        }
                    }
                }
            }

            // La liste par rayon
            Column(pad, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    Text("La liste", style = display(18.sp, FontWeight.Bold), modifier = Modifier.weight(1f))
                    Text("${arranged.remaining} à prendre · par rayon", fontSize = 14.sp, color = TextSec)
                }
                if (snap.loaded && arranged.total == 0) {
                    Text(
                        "La liste est vide : ajoutez une recette au menu ou un article.", fontSize = 14.sp, color = TextSec, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(CardColor).padding(horizontal = 16.dp, vertical = 20.dp),
                    )
                }
                arranged.groups.forEach { g ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        AisleTitle(g.aisle, g.name, g.lines.size)
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(CardColor)) {
                            g.lines.forEachIndexed { i, l ->
                                val open = l.key in expanded
                                Row(Modifier.fillMaxWidth().padding(end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        Modifier.size(width = 52.dp, height = 56.dp).clickable(role = Role.Checkbox, onClickLabel = "Cocher") { onToggle(l) }
                                            .semantics { contentDescription = l.name; stateDescription = "Pas encore pris" },
                                        contentAlignment = Alignment.Center,
                                    ) { Box(Modifier.size(26.dp).border(2.dp, Dash, RoundedCornerShape(8.dp))) }
                                    Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text(l.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ecorce)
                                            if (l.staple) Text("placard ?", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = HoneyInk, modifier = Modifier.clip(CircleShape).background(HoneyBg).padding(horizontal = 6.dp))
                                        }
                                        Text(lineSub(l, nameOf), fontSize = 12.sp, color = TextSec, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    if (l.qty.isNotEmpty()) Text(l.qty, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Ecorce, modifier = Modifier.padding(horizontal = 4.dp))
                                    if (l.from.size > 1) {
                                        IconButton(onClick = { expanded = if (open) expanded - l.key else expanded + l.key }, modifier = Modifier.size(36.dp)) {
                                            Icon(Icons.Outlined.ExpandMore, if (open) "Masquer le détail" else "Détail par recette", tint = TextSec, modifier = Modifier.rotate(if (open) 180f else 0f))
                                        }
                                    }
                                    IconButton(onClick = { onLine(l) }, modifier = Modifier.size(36.dp)) {
                                        Icon(Icons.Outlined.MoreHoriz, "Modifier ${l.name}", tint = TextSec)
                                    }
                                }
                                if (open) {
                                    Column(
                                        Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Sable).padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        l.from.forEach { f ->
                                            Row(Modifier.fillMaxWidth()) {
                                                Text(f.recipe, fontSize = 13.sp, color = TextSec, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                                Text(f.qty, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Ecorce)
                                            }
                                        }
                                    }
                                }
                                if (i < g.lines.lastIndex) HorizontalDivider(color = Sable2)
                            }
                        }
                    }
                }

                if (arranged.cart.isNotEmpty()) {
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(CardColor)) {
                        Row(
                            Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = if (cartOpen) "Replier" else "Déplier") { cartOpen = !cartOpen }.padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            CheckedBox(24.dp)
                            Text("Dans le panier", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Ecorce, modifier = Modifier.weight(1f))
                            Text("${arranged.cart.size}", fontSize = 14.sp, color = TextSec)
                            Icon(Icons.Outlined.ExpandMore, null, tint = TextSec, modifier = Modifier.rotate(if (cartOpen) 180f else 0f))
                        }
                        if (cartOpen) {
                            arranged.cart.forEach { l ->
                                HorizontalDivider(color = Sable2)
                                Row(
                                    Modifier.fillMaxWidth().clickable(role = Role.Checkbox, onClickLabel = "Décocher") { onToggle(l) }.padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    CheckedBox(24.dp)
                                    Text(l.name, fontSize = 15.sp, color = TextMuted, textDecoration = TextDecoration.LineThrough, modifier = Modifier.weight(1f))
                                    checkedBy(l)?.let { Text("par ${nameOf(it)}", fontSize = 12.sp, color = TextSec) }
                                }
                            }
                        }
                    }
                }
                if (arranged.have.isNotEmpty()) {
                    Text("Au placard, pas sur la liste : " + arranged.have.joinToString(", ") { it.name.lowercase() }, fontSize = 13.sp, color = TextSec)
                }
                if (arranged.removed.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                        Text("Retirés de la liste :", fontSize = 13.sp, color = TextSec)
                        arranged.removed.forEach { l ->
                            Row(
                                Modifier.clip(CircleShape).background(Sable2).clickable(role = Role.Button, onClickLabel = "Remettre sur la liste") { change(Op("restore", key = l.product), null) }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(Icons.Outlined.Refresh, null, tint = Ecorce2, modifier = Modifier.size(12.dp))
                                Text(l.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Ecorce2)
                            }
                        }
                    }
                }
            }
        }

        // Barre du bas
        if (arranged.total > 0) {
            Row(
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp).fillMaxWidth().height(56.dp)
                    .card(RoundedCornerShape(22.dp), 8.dp).clip(RoundedCornerShape(22.dp)).background(Terracotta)
                    .clickable(role = Role.Button, onClick = onShop),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.Outlined.ShoppingCart, null, tint = Color.White, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Text("Faire les courses", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(Modifier.width(10.dp))
                Text("${arranged.remaining}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.25f)).padding(horizontal = 8.dp, vertical = 2.dp))
            }
        }
    }
}

private fun lineSub(l: ShoppingList.Line, nameOf: (String?) -> String): String {
    if (l.itemId != null) return nameOf(l.addedBy).let { if (it == "vous") "Ajouté par vous" else "Ajouté par $it" }
    return (l.from.map { it.recipe } + if (l.edited) listOf("modifié") else emptyList()).joinToString(" · ")
}

private fun newItemId(): String = "a" + java.util.UUID.randomUUID().toString().replace("-", "").take(20)

@Composable
private fun CheckedBox(size: Dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(8.dp)).background(DoneSolid), contentAlignment = Alignment.Center) {
        Icon(Icons.Outlined.Check, null, tint = Color.White, modifier = Modifier.size(size * 0.6f))
    }
}

@Composable
private fun AisleTitle(aisle: String, name: String, count: Int) {
    val st = aisleStyle(aisle)
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(28.dp).clip(RoundedCornerShape(9.dp)).background(st.bg), contentAlignment = Alignment.Center) {
            Icon(st.icon, null, tint = st.ink, modifier = Modifier.size(16.dp))
        }
        Text(name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Ecorce, modifier = Modifier.weight(1f))
        Text("$count", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextMuted)
    }
}

@Composable
private fun MenuCard(e: ShoppingList.MenuEntry, tint: Pair<Color, Color>, onRemove: () -> Unit, onServings: (Int) -> Unit) {
    Column(Modifier.width(150.dp).height(166.dp).alpha(if (e.cooked) 0.6f else 1f).card(RoundedCornerShape(22.dp), 3.dp)) {
        Box(Modifier.fillMaxWidth().height(76.dp).background(tint.first), contentAlignment = Alignment.Center) {
            if (e.imageUrl.isNotBlank()) AsyncImage(e.imageUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            else Icon(Icons.Outlined.Restaurant, null, tint = tint.second, modifier = Modifier.size(26.dp))
            if (e.cooked) {
                Row(
                    Modifier.align(Alignment.TopStart).padding(8.dp).clip(CircleShape).background(DoneSolid).padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Icon(Icons.Outlined.Check, null, tint = Color.White, modifier = Modifier.size(11.dp))
                    Text("Cuisinée", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
            Box(
                Modifier.align(Alignment.TopEnd).padding(6.dp).size(32.dp).clip(CircleShape).background(CardColor)
                    .clickable(role = Role.Button, onClickLabel = "Retirer ${e.title} du menu", onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.Close, "Retirer ${e.title} du menu", tint = Ecorce, modifier = Modifier.size(14.dp)) }
        }
        Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text(e.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Ecorce, lineHeight = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(Modifier.fillMaxWidth().clip(CircleShape).background(Sable).padding(2.dp), verticalAlignment = Alignment.CenterVertically) {
                StepButton(Icons.Outlined.Remove, "Moins de parts", e.servings > 1) { onServings(e.servings - 1) }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${e.servings} pers.", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Ecorce)
                    if (e.servings != e.baseServings && e.baseServings > 0) {
                        val f = Math.round(e.servings * 10.0 / e.baseServings) / 10.0
                        Text("×" + (if (f == Math.floor(f)) f.toLong().toString() else f.toString()).replace(".", ","), fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Terracotta)
                    }
                }
                StepButton(Icons.Outlined.Add, "Plus de parts", e.servings < 50) { onServings(e.servings + 1) }
            }
        }
    }
}

@Composable
private fun StepButton(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(32.dp).clip(CircleShape).background(CardColor).alpha(if (enabled) 1f else 0.4f)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = Ecorce, modifier = Modifier.size(14.dp)) }
}

@Composable
private fun AddItemField(onAdd: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    fun submit() {
        if (text.isBlank()) return
        onAdd(text)
        text = ""
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Sable).padding(start = 12.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Add, null, tint = TextSec, modifier = Modifier.size(18.dp))
        androidx.compose.foundation.text.BasicTextField(
            value = text,
            onValueChange = { if (it.length <= 80) text = it },
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, color = Ecorce),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(Terracotta),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp, vertical = 14.dp).semantics { contentDescription = "Ajouter un article" },
            decorationBox = { inner ->
                if (text.isEmpty()) Text("Ajouter un article…", fontSize = 15.sp, color = TextMuted)
                inner()
            },
        )
        if (text.isNotBlank()) {
            Text("Ajouter", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Terracotta, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(role = Role.Button) { submit() }.padding(8.dp))
        }
    }
}

// ── En magasin ───────────────────────────────────────────────────────────────

@Composable
private fun ShopMode(
    arranged: ShoppingList.Arranged,
    menuCount: Int,
    me: String,
    checkedBy: (ShoppingList.Line) -> String?,
    nameOf: (String?) -> String,
    onToggle: (ShoppingList.Line) -> Unit,
    onStop: () -> Unit,
    onFinish: () -> Unit,
) {
    val got = arranged.total - arranged.remaining
    val byOthers = arranged.cart.filter { checkedBy(it).let { by -> by != null && by.isNotEmpty() && by != me } }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Column(Modifier.fillMaxWidth().background(Sable).padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.clip(CircleShape).background(CardColor).clickable(role = Role.Button, onClick = onStop).padding(start = 8.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.ChevronLeft, null, tint = Ecorce, modifier = Modifier.size(20.dp))
                    Text("Préparer", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Ecorce)
                }
                Spacer(Modifier.weight(1f))
                Box(Modifier.size(8.dp).clip(CircleShape).background(DoneSolid))
                Spacer(Modifier.width(6.dp))
                Text("Écran toujours allumé", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextSec)
            }
            Text("EN MAGASIN", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Terracotta, letterSpacing = 0.8.sp)
            Text(if (arranged.remaining > 0) "Encore ${arranged.remaining}" else "C'est tout bon", style = display(30.sp))
            Text("$got sur ${arranged.total} dans le panier", fontSize = 14.sp, color = TextSec)
            LinearProgressIndicator(
                progress = { if (arranged.total > 0) got.toFloat() / arranged.total else 0f },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                color = DoneSolid, trackColor = Sable2, drawStopIndicator = {}, gapSize = 0.dp,
            )
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (byOthers.isNotEmpty() && arranged.remaining > 0) {
                val who = nameOf(checkedBy(byOthers[0]))
                Text(
                    "${who.replaceFirstChar { it.uppercase() }} a déjà pris ${if (byOthers.size > 1) "${byOthers.size} articles" else byOthers[0].name.lowercase()}. La liste se met à jour pour vous tous.",
                    fontSize = 14.sp, color = Ecorce2,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(CardColor).padding(14.dp),
                )
            }
            if (arranged.total > 0 && arranged.remaining == 0) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(CardColor).padding(horizontal = 20.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(Modifier.size(56.dp).clip(CircleShape).background(DoneSolid), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Check, null, tint = Color.White, modifier = Modifier.size(28.dp))
                    }
                    Text("Tout est dans le panier !", style = display(20.sp))
                    Text(
                        "${plural(arranged.total, "article", "articles")} · ${plural(menuCount, "recette", "recettes")} au menu. Elles y restent jusqu'à ce que vous les cuisiniez.",
                        fontSize = 14.sp, color = TextSec, textAlign = TextAlign.Center,
                    )
                    CampButton("Terminer les courses", onFinish, Modifier.padding(top = 8.dp))
                }
            }
            arranged.groups.forEach { g ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AisleTitle(g.aisle, g.name, g.lines.size)
                    g.lines.forEach { l ->
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 64.dp).card(RoundedCornerShape(18.dp), 2.dp)
                                .clickable(role = Role.Checkbox, onClickLabel = "Mettre dans le panier") { onToggle(l) }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Box(Modifier.size(28.dp).border(2.dp, Dash, RoundedCornerShape(9.dp)))
                            Column(Modifier.weight(1f)) {
                                Text(l.name, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Ecorce)
                                Text(lineSub(l, nameOf), fontSize = 13.sp, color = TextSec, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            if (l.qty.isNotEmpty()) Text(l.qty, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Ecorce)
                        }
                    }
                }
            }
            if (arranged.cart.isNotEmpty()) {
                Text("DANS LE PANIER · ${arranged.cart.size}", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = TextSec, letterSpacing = 0.8.sp)
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(CardColor)) {
                    arranged.cart.forEachIndexed { i, l ->
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Checkbox, onClickLabel = "Sortir du panier") { onToggle(l) }.padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            CheckedBox(24.dp)
                            Text(l.name, fontSize = 15.sp, color = TextMuted, textDecoration = TextDecoration.LineThrough, modifier = Modifier.weight(1f))
                            checkedBy(l)?.takeIf { it.isNotEmpty() && it != me }?.let { Text("par ${nameOf(it)}", fontSize = 12.sp, color = TextSec) }
                        }
                        if (i < arranged.cart.lastIndex) HorizontalDivider(color = Sable2)
                    }
                }
            }
        }
    }
}

// ── Feuilles ─────────────────────────────────────────────────────────────────

/**
 * Garde la feuille en place quand la liste arrive en bas : sans lui, l'élan restant d'un
 * défilement vers le bas remontait la feuille par à-coups (elle « sautait » sous la barre
 * d'état). Tirer vers le bas en haut de la liste ferme toujours la feuille.
 */
private val KeepSheetInPlace = object : NestedScrollConnection {
    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
        if (available.y < 0f) Offset(0f, available.y) else Offset.Zero

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity =
        if (available.y < 0f) Velocity(0f, available.y) else Velocity.Zero
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecipePickerSheet(
    ready: List<Link>,
    missing: List<Link>,
    inMenu: (String) -> Boolean,
    menuCount: Int,
    onToggle: (Link) -> Unit,
    onComplete: (Link) -> Unit,
    onAddRecipe: () -> Unit,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val all = ready + missing
    val base = when (tab) {
        1 -> all.filter { it.likedByMe || it.favorite }
        2 -> all.sortedByDescending { maxOf(stamp(it.createdAt), stamp(it.lastDoneAt)) }.take(12)
        else -> all.sortedWith(compareBy(java.text.Collator.getInstance(java.util.Locale.FRENCH)) { it.title })
    }
    val q = query.trim().lowercase()
    val shown = if (q.isEmpty()) base else base.filter { l -> l.title.lowercase().contains(q) || l.ingredients.any { it.lowercase().contains(q) } }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = SheetColor,
        dragHandle = { SheetHandle() },
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.92f).padding(horizontal = 20.dp)) {
            Text("Qu'est-ce qu'on mange ?", style = display(24.sp))
            Spacer(Modifier.height(12.dp))
            SearchField(query, { query = it }, "Chercher une recette, un ingrédient…")
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Toutes", "J'aime", "Récentes").forEachIndexed { i, label -> OutlinePill(label, tab == i) { tab = i } }
            }
            Spacer(Modifier.height(12.dp))
            Column(Modifier.weight(1f).nestedScroll(KeepSheetInPlace).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (all.isEmpty()) {
                    EmptyBlock(Icons.Outlined.Restaurant, "Aucune recette pour l'instant", "Ajoutez une recette et listez ses ingrédients.") {
                        CampButton("Ajouter une recette", onAddRecipe, icon = Icons.Outlined.Add)
                    }
                } else if (shown.isEmpty()) {
                    Text("Aucune recette ne correspond.", fontSize = 14.sp, color = TextSec, modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), textAlign = TextAlign.Center)
                }
                shown.forEach { r ->
                    val noIngredients = r.ingredients.none { it.isNotBlank() }
                    val on = inMenu(r.id)
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(CardColor)
                            .then(if (on) Modifier.border(2.dp, DoneSolid, RoundedCornerShape(16.dp)) else Modifier).padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(GreenBg), contentAlignment = Alignment.Center) {
                            if (r.imageUrl.isNotBlank()) AsyncImage(r.imageUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            else Icon(Icons.Outlined.Restaurant, null, tint = GreenInk, modifier = Modifier.size(20.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Text(r.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Ecorce, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val n = r.ingredients.count { it.isNotBlank() }
                            Text(
                                if (noIngredients) "Pas encore d'ingrédients" else plural(n, "ingrédient", "ingrédients") + if (r.servings > 0) " · ${r.servings} pers." else "",
                                fontSize = 12.sp, color = if (noIngredients) Terracotta else TextSec, fontWeight = if (noIngredients) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        }
                        if (noIngredients) {
                            Row(
                                Modifier.clip(CircleShape).background(Sable2).clickable(role = Role.Button, onClickLabel = "Compléter les ingrédients de ${r.title}") { onComplete(r) }.padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(Icons.Outlined.Edit, null, tint = Terracotta, modifier = Modifier.size(14.dp))
                                Text("Compléter", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Terracotta)
                            }
                        } else {
                            Row(
                                Modifier.height(36.dp).then(if (on) Modifier else Modifier.width(36.dp)).clip(CircleShape).background(if (on) DoneSolid else Terracotta)
                                    .clickable(role = Role.Checkbox, onClickLabel = if (on) "Retirer ${r.title} du menu" else "Ajouter ${r.title} au menu") { onToggle(r) }
                                    .padding(horizontal = if (on) 12.dp else 0.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
                            ) {
                                Icon(if (on) Icons.Outlined.Check else Icons.Outlined.Add, if (on) "Au menu" else "Ajouter au menu", tint = Color.White, modifier = Modifier.size(16.dp))
                                if (on) Text(" Au menu", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                }
            }
            CampButton(
                if (menuCount > 0) "Voir la liste · ${plural(menuCount, "recette", "recettes")}" else "Fermer", onDismiss,
                Modifier.fillMaxWidth().padding(vertical = 12.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShareSheet(text: String, shared: List<String>, onDone: (String?) -> Unit) {
    val context = LocalContext.current
    @Suppress("DEPRECATION") val clipboard = LocalClipboardManager.current
    ModalBottomSheet(onDismissRequest = { onDone(null) }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = SheetColor, dragHandle = { SheetHandle() }) {
        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Envoyer la liste", style = display(24.sp))
            Text(
                if (shared.isNotEmpty()) "Déjà partagée avec la tribu. ${shared.joinToString(", ")} ${if (shared.size > 1) "voient la même liste et peuvent" else "voit la même liste et peut"} cocher en magasin."
                else "Invitez vos proches dans votre tribu (Ma tribu) : ils verront la même liste et pourront cocher en magasin.",
                fontSize = 14.sp, color = PlumInk, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(PlumBg).padding(14.dp),
            )
            Text("POUR QUELQU'UN SANS TRIBBAE : EN TEXTE", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = TextSec, letterSpacing = 0.8.sp)
            Text(text, fontSize = 13.sp, lineHeight = 20.sp, color = Ecorce2, modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp).clip(RoundedCornerShape(16.dp)).background(CardColor).verticalScroll(rememberScrollState()).padding(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CampButton("Copier", { clipboard.setText(AnnotatedString(text)); onDone("Liste copiée") }, Modifier.weight(1f), kind = ButtonKind.Ghost, icon = Icons.Outlined.ContentCopy)
                CampButton("Partager…", {
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Envoyer la liste"))
                    onDone(null)
                }, Modifier.weight(1f), icon = Icons.Outlined.Share)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun LineSheet(line: ShoppingList.Line, onDismiss: () -> Unit, onSave: (qty: String, aisle: String) -> Unit, onRemove: () -> Unit) {
    var qty by remember(line.key) { mutableStateOf(line.qty) }
    var aisle by remember(line.key) { mutableStateOf(line.aisle) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = SheetColor, dragHandle = { SheetHandle() }) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(line.name, style = display(24.sp))
            CampField("Quantité", qty, { if (it.length <= 30) qty = it }, placeholder = "Par exemple 2 bouteilles")
            if (line.itemId == null) Text("Retenue pour ce produit. Videz le champ pour revenir à la quantité des recettes.", fontSize = 12.sp, color = TextSec)
            Text("Rayon", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Ecorce)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ShoppingList.AISLES.forEach { a ->
                    val st = aisleStyle(a.id)
                    val on = aisle == a.id
                    Row(
                        Modifier.clip(CircleShape).background(if (on) Ecorce else st.bg).clickable(role = Role.RadioButton) { aisle = a.id }.padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(st.icon, null, tint = if (on) Sable else st.ink, modifier = Modifier.size(14.dp))
                        Text(a.name, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (on) Sable else st.ink)
                    }
                }
            }
            if (line.itemId == null) Text("Retenu pour la tribu, d'une liste à l'autre.", fontSize = 12.sp, color = TextSec)
            CampButton("Enregistrer", { onSave(qty.trim(), aisle) }, Modifier.fillMaxWidth())
            CampButton("Retirer de la liste", onRemove, Modifier.fillMaxWidth(), kind = ButtonKind.Ghost, icon = Icons.Outlined.Delete)
            if (line.itemId == null) Text("Retirer un ingrédient ne touche pas la recette.", fontSize = 12.sp, color = TextSec, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }
}
