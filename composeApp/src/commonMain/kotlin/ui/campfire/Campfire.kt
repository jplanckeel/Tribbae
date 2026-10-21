package ui.campfire

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import data.Folder
import data.FolderColor
import data.Link
import data.LinkCategory
import ui.*
import ui.folderIconVector

// ── Métadonnées des catégories (ordre et libellés de la maquette) ─────────────

data class CategoryUi(
    val category: LinkCategory,
    val label: String,
    val plural: String,
    val icon: ImageVector,
    val bg: Color,
    val ink: Color,
    val tint: Color,
)

private fun palette(c: LinkCategory) = CategoryPalettes.getValue(c.name)

val CategoryOrder: List<CategoryUi> = listOf(
    Triple(LinkCategory.ACTIVITE, "Activités", Icons.Outlined.SportsSoccer),
    Triple(LinkCategory.CADEAU, "Cadeaux", Icons.Outlined.CardGiftcard),
    Triple(LinkCategory.RECETTE, "Recettes", Icons.Outlined.Restaurant),
    Triple(LinkCategory.EVENEMENT, "Sorties", Icons.Outlined.CalendarMonth),
    Triple(LinkCategory.IDEE, "Idées", Icons.Outlined.Lightbulb),
    Triple(LinkCategory.LIVRE, "Livres", Icons.Outlined.MenuBook),
    Triple(LinkCategory.DECORATION, "Déco", Icons.Outlined.Palette),
).map { (cat, plural, icon) ->
    val p = palette(cat)
    CategoryUi(cat, cat.label, plural, icon, p.bg, p.ink, p.icon)
}

fun categoryUi(c: LinkCategory): CategoryUi = CategoryOrder.first { it.category == c }

/** Fond pastel / couleur pleine d'un dossier (mêmes teintes que le web). */
fun folderSolid(c: FolderColor): Color = FolderColors[c.name] ?: Terracotta
fun folderBg(c: FolderColor): Color = when (c) {
    FolderColor.ORANGE -> Color(0xFFFCE3D0)
    FolderColor.RED -> Color(0xFFF8DCD3)
    FolderColor.YELLOW -> Color(0xFFFBEACB)
    FolderColor.GREEN -> Color(0xFFE2EDD9)
    FolderColor.TEAL -> Color(0xFFD5ECE8)
    FolderColor.BLUE -> Color(0xFFDCEDF3)
    FolderColor.PURPLE -> Color(0xFFECE1EA)
    FolderColor.PINK -> Color(0xFFF7DDE6)
    FolderColor.SABLE -> Color(0xFFF1E3D1)
}

/** Teinte secondaire (motifs des couvertures, dessins teintés, onglet des cartes). */
fun folderMid(c: FolderColor): Color = when (c) {
    FolderColor.ORANGE -> Color(0xFFF8CDAE)
    FolderColor.RED -> Color(0xFFF2C1B2)
    FolderColor.YELLOW -> Color(0xFFF6DAA3)
    FolderColor.GREEN -> Color(0xFFCCE0BE)
    FolderColor.TEAL -> Color(0xFFB5DDD6)
    FolderColor.BLUE -> Color(0xFFBFE0EC)
    FolderColor.PURPLE -> Color(0xFFDCC8D8)
    FolderColor.PINK -> Color(0xFFEFC3D3)
    FolderColor.SABLE -> Color(0xFFE6D3C0)
}

/** Libellés des couleurs (maquette « Personnaliser un dossier »). */
val FolderColorLabels = mapOf(
    FolderColor.ORANGE to "Orange", FolderColor.RED to "Corail", FolderColor.YELLOW to "Miel",
    FolderColor.GREEN to "Vert", FolderColor.TEAL to "Sarcelle", FolderColor.BLUE to "Bleu",
    FolderColor.PURPLE to "Prune", FolderColor.PINK to "Rose", FolderColor.SABLE to "Sable",
)

// Couleurs d'avatars (fond, encre) de la maquette « Ma tribu »
val AvatarColors = listOf(
    Braise to Ecorce,
    Prune to Sable,
    Color(0xFFDCEDF3) to Color(0xFF235C77),
    Color(0xFFE2EDD9) to Color(0xFF3E6630),
    Color(0xFFFBEACB) to Color(0xFF85560C),
    Color(0xFFF7DDE6) to Color(0xFF9C3A5C),
)

// ── Typographie ────────────────────────────────────────────────────────────────

/** Style « display » (Bricolage Grotesque côté web) : gras serré. */
fun display(size: TextUnit, weight: FontWeight = FontWeight.ExtraBold, color: Color = Ecorce) = TextStyle(
    fontSize = size,
    fontWeight = weight,
    color = color,
    letterSpacing = (-0.025f * size.value).sp,
    lineHeight = (size.value * 1.08f).sp,
)

val CardShadow = 10.dp

/** Ombre douce des cartes blanches de la maquette. */
fun Modifier.card(shape: RoundedCornerShape = RoundedCornerShape(22.dp), elevation: Dp = 6.dp): Modifier =
    this
        .shadow(elevation, shape, ambientColor = Ecorce.copy(alpha = 0.25f), spotColor = Ecorce.copy(alpha = 0.18f))
        .clip(shape)
        .background(Color.White)

// ── Logo et motifs ─────────────────────────────────────────────────────────────

/** Logo tipi Tribbae (repris du SVG de la maquette, viewBox 48×48). */
@Composable
fun TipiMark(size: Dp = 34.dp, stroke: Float = 2.6f, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val s = this.size.width / 48f
        fun p(vararg pts: Float) = Path().apply {
            moveTo(pts[0] * s, pts[1] * s)
            var i = 2
            while (i < pts.size) { lineTo(pts[i] * s, pts[i + 1] * s); i += 2 }
            close()
        }
        val outline = Stroke(stroke * s, cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawLine(Ecorce, Offset(20 * s, 3 * s), Offset(26 * s, 12 * s), stroke * s, StrokeCap.Round)
        drawLine(Ecorce, Offset(28 * s, 3 * s), Offset(22 * s, 12 * s), stroke * s, StrokeCap.Round)
        val big = p(24f, 9f, 44f, 43f, 4f, 43f)
        drawPath(big, Tipi); drawPath(big, Ecorce, style = outline)
        val door = p(24f, 24f, 35f, 43f, 13f, 43f)
        drawPath(door, Prune); drawPath(door, Ecorce, style = outline)
        val flap = p(12f, 26f, 24.5f, 26f, 18f, 39f)
        drawPath(flap, Braise); drawPath(flap, Ecorce, style = Stroke(stroke * 0.85f * s, join = StrokeJoin.Round))
    }
}

@Composable
fun Logo(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TipiMark()
        Text("Tribbae", style = display(22.sp))
    }
}

/** Grand soleil crème + petit rond + croix (motifs du logo), en fond d'écran. */
@Composable
fun BoxScope.SunDecor() {
    Box(
        Modifier
            .align(Alignment.TopEnd)
            .offset(x = 120.dp, y = (-110).dp)
            .size(330.dp)
            .clip(CircleShape)
            .background(Soleil)
    )
    Box(
        Modifier
            .align(Alignment.TopEnd)
            .offset(x = (-40).dp, y = 118.dp)
            .size(9.dp)
            .border(2.dp, Terracotta, CircleShape)
    )
    Canvas(
        Modifier
            .align(Alignment.TopEnd)
            .offset(x = (-150).dp, y = 70.dp)
            .size(12.dp)
    ) {
        val w = size.width
        drawLine(Ecorce, Offset(2f / 12 * w, 2f / 12 * w), Offset(10f / 12 * w, 10f / 12 * w), 2.dp.toPx(), StrokeCap.Round)
        drawLine(Ecorce, Offset(10f / 12 * w, 2f / 12 * w), Offset(2f / 12 * w, 10f / 12 * w), 2.dp.toPx(), StrokeCap.Round)
    }
}

// ── Boutons ────────────────────────────────────────────────────────────────────

@Composable
fun CircleIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    background: Color = Color.White,
    tint: Color = Ecorce,
    size: Dp = 44.dp,
    badge: Boolean = false,
) {
    Box(
        modifier
            .size(size)
            .shadow(if (background == Color.White) 4.dp else 0.dp, CircleShape, spotColor = Ecorce.copy(alpha = 0.2f))
            .clip(CircleShape)
            .background(background)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(21.dp))
        if (badge) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-11).dp, y = 10.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(Terracotta)
            )
        }
    }
}

enum class ButtonKind { Primary, Secondary, Ai, Dark, Ghost }

@Composable
fun CampButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.Primary,
    icon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    enabled: Boolean = true,
    height: Dp = 52.dp,
) {
    val (bg, fg) = when (kind) {
        ButtonKind.Primary -> Terracotta to Color.White
        ButtonKind.Secondary -> Color.Transparent to Ecorce
        ButtonKind.Ai -> Prune to Sable
        ButtonKind.Dark -> Ecorce to Sable
        ButtonKind.Ghost -> Color.White to Ecorce
    }
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier
            .height(height)
            .alpha(if (enabled) 1f else 0.55f)
            .clip(shape)
            .background(bg)
            .then(
                when (kind) {
                    ButtonKind.Secondary -> Modifier.border(2.dp, Ecorce, shape)
                    ButtonKind.Ghost -> Modifier.border(1.5.dp, Color(0xFFE6D3C0), shape)
                    else -> Modifier
                }
            )
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
    ) {
        if (icon != null) Icon(icon, null, tint = fg, modifier = Modifier.size(20.dp))
        Text(text, color = fg, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1)
        if (trailingIcon != null) Icon(trailingIcon, null, tint = fg, modifier = Modifier.size(20.dp))
    }
}

/** Pastille de filtre arrondie (chips de l'Explorer). */
@Composable
fun Pill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    idleBg: Color = Color.White,
    idleFg: Color = Ecorce,
    bordered: Boolean = idleBg == Color.White,
) {
    Box(
        Modifier
            .height(38.dp)
            .clip(CircleShape)
            .background(if (selected) Ecorce else idleBg)
            .then(if (!selected && bordered) Modifier.border(1.5.dp, Color(0xFFE6D3C0), CircleShape) else Modifier)
            .clickable(role = Role.Checkbox, onClick = onClick)
            .padding(horizontal = 15.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = if (selected) Sable else idleFg, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1)
    }
}

/** Petit bouton contour (tranches d'âge). */
@Composable
fun OutlinePill(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(CircleShape)
            .background(if (selected) Color.White else Color.Transparent)
            .border(1.5.dp, if (selected) Terracotta else Color(0xFFE6D3C0), CircleShape)
            .clickable(role = Role.Checkbox, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text,
            fontSize = 13.sp,
            color = if (selected) TerracottaDark else TextSec,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

// ── Champs ─────────────────────────────────────────────────────────────────────

@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    onSearch: () -> Unit = {},
) {
    Row(
        modifier
            .height(52.dp)
            .card(RoundedCornerShape(18.dp), 4.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(Icons.Outlined.Search, null, tint = TextMuted, modifier = Modifier.size(20.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(placeholder, color = TextMuted, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(color = Ecorce, fontSize = 15.sp, fontWeight = FontWeight.Medium),
                cursorBrush = SolidColor(Terracotta),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = placeholder }
            )
        }
    }
}

/** Onglets segmentés sur fond sable (Mes idées : Dossiers / Favoris / Tags). */
@Composable
fun SegmentedTabs(tabs: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .height(48.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Sable2)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        tabs.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .then(if (on) Modifier.shadow(2.dp, RoundedCornerShape(12.dp)) else Modifier)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (on) Color.White else Color.Transparent)
                    .clickable(role = Role.Tab) { onSelect(i) },
                contentAlignment = Alignment.Center
            ) {
                Text(label, fontSize = 14.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold, color = if (on) Ecorce else TextSec)
            }
        }
    }
}

@Composable
fun SectionHeader(title: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, style = display(20.sp, FontWeight.Bold))
        if (action != null) {
            Text(
                action,
                color = Terracotta,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onAction).padding(4.dp)
            )
        }
    }
}

@Composable
fun EmptyBlock(icon: ImageVector, title: String, text: String? = null, action: (@Composable () -> Unit)? = null) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .border(2.dp, Dash, shape)
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(Modifier.size(56.dp).clip(RoundedCornerShape(18.dp)).background(Sable2), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Terracotta, modifier = Modifier.size(26.dp))
        }
        Text(title, style = display(18.sp, FontWeight.Bold))
        if (text != null) Text(text, fontSize = 14.sp, color = TextSec, lineHeight = 20.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        action?.invoke()
    }
}

// ── Cartes d'idées ─────────────────────────────────────────────────────────────

/** Photo de l'idée si disponible, sinon fond pastel + icône de catégorie. */
@Composable
fun IdeaVisual(link: Link, height: Dp, modifier: Modifier = Modifier, iconSize: Dp = 46.dp) {
    val cat = categoryUi(link.category)
    var failed by remember(link.imageUrl) { mutableStateOf(false) }
    Box(modifier.fillMaxWidth().height(height).cardOrigin(link.id).background(cat.bg), contentAlignment = Alignment.Center) {
        if (link.imageUrl.isNotBlank() && !failed) {
            AsyncImage(
                model = link.imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onError = { failed = true },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(cat.icon, null, tint = cat.tint.copy(alpha = 0.55f), modifier = Modifier.size(iconSize))
        }
    }
}

@Composable
fun CategoryBadge(cat: CategoryUi, modifier: Modifier = Modifier, onWhite: Boolean = true) {
    Text(
        cat.label,
        color = if (onWhite) cat.tint else cat.ink,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
            .clip(CircleShape)
            .background(if (onWhite) Color.White else cat.bg)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

/** Compte connecté, fourni à la racine de l'app : sert à signer les idées des autres. */
val LocalCurrentUserId = androidx.compose.runtime.staticCompositionLocalOf<String?> { null }

/** « par Camille » pour l'idée d'un autre compte (tribu, communauté), sinon "". */
@Composable
fun authorLine(link: Link): String {
    val me = LocalCurrentUserId.current
    return if (link.ownerDisplayName.isNotBlank() && link.ownerId.isNotBlank() && link.ownerId != me) "par ${link.ownerDisplayName}" else ""
}

fun metaLine(link: Link): String = listOf(link.ageRange, priceLabel(link.price)).filter { it.isNotBlank() }.joinToString(" · ")

/** Prix affiché : « 0 », « 0 € »… deviennent « Gratuit » (comme sur le web). */
fun priceLabel(price: String): String =
    if (Regex("""^0+(?:[.,]0+)?\s*(?:€|eur|euros?)?$""", RegexOption.IGNORE_CASE).matches(price.trim())) "Gratuit" else price.trim()

/** Carte « rail » de l'accueil (210 dp). */
@Composable
fun RailCard(link: Link, onClick: () -> Unit) {
    val cat = categoryUi(link.category)
    Column(Modifier.width(210.dp).card().clickable(onClick = onClick)) {
        Box {
            IdeaVisual(link, 120.dp)
            CategoryBadge(cat, Modifier.padding(10.dp))
        }
        Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(link.title, style = display(16.sp, FontWeight.Bold), maxLines = 2, overflow = TextOverflow.Ellipsis)
            val meta = listOf(metaLine(link), authorLine(link)).filter { it.isNotEmpty() }.joinToString(" · ")
            if (meta.isNotEmpty()) Text(meta, fontSize = 13.sp, color = TextSec, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private val Mosaic = listOf(150.dp, 110.dp, 100.dp, 120.dp, 140.dp, 96.dp)

/** Carte de la mosaïque de l'Explorer (2 colonnes décalées). */
@Composable
fun MosaicCard(link: Link, index: Int, onClick: () -> Unit, liked: Boolean, onLike: (() -> Unit)?) {
    val cat = categoryUi(link.category)
    Column(Modifier.fillMaxWidth().card(RoundedCornerShape(20.dp)).clickable(onClick = onClick)) {
        Box {
            IdeaVisual(link, Mosaic[index % Mosaic.size], iconSize = 40.dp)
            if (onLike != null) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.9f))
                        .clickable(role = Role.Button, onClick = onLike)
                        .semantics { contentDescription = if (liked) "Je n'aime plus" else "J'aime" },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(if (liked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder, null, tint = Terracotta, modifier = Modifier.size(16.dp))
                }
            }
        }
        Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(link.title, style = display(15.sp, FontWeight.Bold), maxLines = 3, overflow = TextOverflow.Ellipsis)
            Text(metaLine(link).ifEmpty { cat.label }, fontSize = 12.sp, color = TextSec)
            val author = authorLine(link)
            if (author.isNotEmpty()) Text(author, fontSize = 12.sp, color = TextSec, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (link.likeCount > 0) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Outlined.FavoriteBorder, null, tint = TextSec, modifier = Modifier.size(13.dp))
                    Text("${link.likeCount}", fontSize = 12.sp, color = TextSec)
                }
            }
        }
    }
}

/** Ligne compacte (listes). */
@Composable
fun RowCard(link: Link, onClick: () -> Unit, trailing: (@Composable () -> Unit)? = null) {
    val cat = categoryUi(link.category)
    Row(
        Modifier.fillMaxWidth().card(RoundedCornerShape(18.dp), 4.dp).clickable(onClick = onClick).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        IdeaVisual(link, 56.dp, Modifier.width(56.dp).clip(RoundedCornerShape(14.dp)), iconSize = 24.dp)
        Column(Modifier.weight(1f)) {
            Text(link.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Ecorce, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(listOf(cat.label, metaLine(link), authorLine(link)).filter { it.isNotEmpty() }.joinToString(" · "), fontSize = 13.sp, color = TextSec, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        trailing?.invoke() ?: Icon(Icons.Outlined.ChevronRight, null, tint = TextMuted, modifier = Modifier.size(20.dp))
    }
}

/** Mosaïque à deux colonnes décalées (sans LazyGrid : s'insère dans un défilement). */
@Composable
fun <T> TwoColumnMosaic(items: List<T>, modifier: Modifier = Modifier, content: @Composable (item: T, index: Int) -> Unit) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        for (col in 0..1) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items.forEachIndexed { i, item -> if (i % 2 == col) content(item, i) }
            }
        }
    }
}

/** Tuile de catégorie arrondie (accueil). */
@Composable
fun CategoryTile(cat: CategoryUi, count: Int?, onClick: () -> Unit) {
    Column(
        Modifier.width(76.dp).clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(Modifier.size(64.dp).clip(RoundedCornerShape(22.dp)).background(cat.bg), contentAlignment = Alignment.Center) {
            Icon(cat.icon, null, tint = cat.tint, modifier = Modifier.size(28.dp))
        }
        Text(cat.plural, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Ecorce)
        if (count != null) Text("$count", fontSize = 12.sp, color = TextMuted, modifier = Modifier.offset(y = (-6).dp))
    }
}

/** Tuile de catégorie carrée (feuille « Ajouter »). */
@Composable
fun CategorySquare(cat: CategoryUi, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.height(78.dp).clip(RoundedCornerShape(18.dp)).background(cat.bg).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically)
    ) {
        Icon(cat.icon, null, tint = cat.tint, modifier = Modifier.size(22.dp))
        Text(cat.label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = cat.ink)
    }
}

// ── Dossiers ───────────────────────────────────────────────────────────────────

@Composable
fun AvatarStack(names: List<String>, label: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        names.take(3).forEachIndexed { i, n ->
            val (bg, ink) = AvatarColors[i % AvatarColors.size]
            Box(
                Modifier
                    .offset(x = (-8 * i).dp)
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(bg),
                contentAlignment = Alignment.Center
            ) {
                Text(n.trim().take(1).uppercase().ifEmpty { "?" }, color = ink, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (label != null) Text(label, fontSize = 12.sp, color = TextSec, modifier = Modifier.offset(x = (-8 * (names.size.coerceAtMost(3) - 1) + 6).dp))
    }
}

@Composable
fun Tag(text: String, bg: Color, fg: Color, icon: ImageVector? = null) {
    Row(
        Modifier.clip(RoundedCornerShape(8.dp)).background(bg).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (icon != null) Icon(icon, null, tint = fg, modifier = Modifier.size(12.dp))
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = fg)
    }
}

/** Bloc « nouveau » en pointillés. */
@Composable
fun DashedAddTile(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector = Icons.Outlined.Add) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier
            .heightIn(min = 150.dp)
            .clip(shape)
            .dashedBorder(Dash, 22.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Sable2), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Terracotta, modifier = Modifier.size(22.dp))
        }
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextSec)
    }
}

/** Bordure en pointillés (2 dp) arrondie. */
fun Modifier.dashedBorder(color: Color, radius: Dp): Modifier =
    this.drawBehind {
        val stroke = 2.dp.toPx()
        drawRoundRect(
            color = color,
            topLeft = Offset(stroke / 2, stroke / 2),
            size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius.toPx()),
            style = Stroke(stroke, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
        )
    }

// ── Divers ─────────────────────────────────────────────────────────────────────

/** Ligne horizontale défilante qui déborde à droite (rails de l'accueil). */
@Composable
fun HorizontalRail(content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        content = content
    )
}

/** Interrupteur de la maquette (piste écorce, pastille sable). */
@Composable
fun CampSwitch(checked: Boolean, onChange: (Boolean) -> Unit, label: String) {
    Box(
        Modifier
            .size(width = 46.dp, height = 28.dp)
            .clip(CircleShape)
            .background(if (checked) Ecorce else Dash)
            .clickable(role = Role.Switch) { onChange(!checked) }
            .semantics { contentDescription = label }
            .padding(3.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(Modifier.size(22.dp).clip(CircleShape).background(Sable))
    }
}

/** Initiale dans un carré arrondi coloré (membres de la tribu, commentaires). */
@Composable
fun InitialAvatar(name: String, index: Int, size: Dp = 58.dp, radius: Dp = 20.dp, fontSize: TextUnit = 22.sp) {
    val (bg, ink) = AvatarColors[index % AvatarColors.size]
    Box(Modifier.size(size).clip(RoundedCornerShape(radius)).background(bg), contentAlignment = Alignment.Center) {
        Text(name.trim().take(1).uppercase().ifEmpty { "?" }, style = display(fontSize, color = ink))
    }
}

val ThinBorder = BorderStroke(1.5.dp, Color(0xFFE6D3C0))

/** Poignée des feuilles du bas (44 × 5, sable foncé). */
@Composable
fun SheetHandle() {
    Box(Modifier.padding(top = 10.dp, bottom = 8.dp).size(width = 44.dp, height = 5.dp).clip(CircleShape).background(Dash))
}

/** Champ avec libellé visible, fond blanc arrondi, bordure terracotta au focus. */
@Composable
fun CampField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    icon: ImageVector? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLength: Int = 300,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    onFocusLost: () -> Unit = {},
    hint: String? = null,
) {
    var focused by remember { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextSec)
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color.White)
                .border(1.5.dp, if (focused) Terracotta else Color(0xFFE6D3C0), RoundedCornerShape(18.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (icon != null) Icon(icon, null, tint = TextMuted, modifier = Modifier.size(19.dp))
            Box(Modifier.weight(1f)) {
                if (value.isEmpty()) Text(placeholder, color = TextMuted, fontSize = 15.sp, maxLines = if (singleLine) 1 else 4)
                BasicTextField(
                    value = value,
                    onValueChange = { if (it.length <= maxLength) onValueChange(it) },
                    singleLine = singleLine,
                    minLines = minLines,
                    textStyle = TextStyle(color = Ecorce, fontSize = 15.sp, fontWeight = FontWeight.Medium, lineHeight = 21.sp),
                    cursorBrush = SolidColor(Terracotta),
                    keyboardOptions = keyboardOptions,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = label }
                        .onFocusChanged {
                            if (focused && !it.isFocused) onFocusLost()
                            focused = it.isFocused
                        }
                )
            }
        }
        if (hint != null) Text(hint, fontSize = 12.sp, color = TextSec)
    }
}
