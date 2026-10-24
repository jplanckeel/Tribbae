package ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import data.Link
import data.LinkCategory
import ui.campfire.*

private enum class Sort(val label: String) { FEATURED("À la une"), POPULAR("Les plus aimées"), RECENT("Les plus récentes"), RATED("Les mieux notées") }

/** Taille d'un lot de cartes de l'Explorer. */
private const val PAGE = 24

/** Durée d'une « une » : l'ordre de l'Explorer change toutes les 6 heures. */
private const val ROTATION_MS = 6 * 3600 * 1000L

/** FNV-1a 32 bits, identique au web (frontend/src/lib/links.ts). */
private fun fnv1a(s: String): Long {
    var h = 0x811c9dc5L
    for (b in s.toByteArray(Charsets.UTF_8)) {
        h = h xor (b.toLong() and 0xff)
        h = (h * 0x01000193L) and 0xffffffffL
    }
    return h
}

/**
 * Score « À la une » pour la période de 6 h en cours : tirage pondéré
 * u^(1/poids), le même pour tous les utilisateurs et sur le web. Les idées
 * aimées et récentes remontent plus souvent, mais chacune a sa chance.
 */
internal fun rotationScore(id: String, likeCount: Int, createdAt: String, now: Long = System.currentTimeMillis()): Double {
    val period = Math.floorDiv(now, ROTATION_MS)
    val u = (fnv1a("$id:$period") + 0.5) / 4294967296.0
    val created = runCatching { java.time.OffsetDateTime.parse(createdAt).toInstant().toEpochMilli() }.getOrNull()
    val ageDays = created?.let { (now - it) / 86_400_000.0 } ?: Double.POSITIVE_INFINITY
    val fresh = if (ageDays < 14) 2 else if (ageDays < 60) 1 else 0
    val weight = 1 + kotlin.math.ln1p(likeCount.coerceAtLeast(0).toDouble()) + fresh
    return Math.pow(u, 1 / weight)
}

private data class AgeBand(val key: String, val label: String, val min: Float, val max: Float)

private val AGES = listOf(
    AgeBand("0-3", "0–3", 0f, 3f),
    AgeBand("3-6", "3–6", 3f, 6f),
    AgeBand("6-10", "6–10", 6f, 10f),
    AgeBand("10+", "10+", 10f, 99f),
)

/** Explorer « feu de camp » : idées publiques en mosaïque, filtres catégorie / âge / tags / note. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExploreScreen(
    links: List<Link>,
    onNavigateBack: () -> Unit,
    onNavigateToDetail: (String) -> Unit,
    onSaveLink: (Link) -> Unit,
    viewModel: viewmodel.LinkViewModel,
    initialQuery: String = "",
    initialCategory: LinkCategory? = null,
    onNavigateToAI: () -> Unit = {},
    onOpenFolder: (data.Folder) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var query by remember(initialQuery) { mutableStateOf(initialQuery) }
    // 0 = idées, 1 = dossiers publics (comme l'onglet « Dossiers » du web)
    var view by rememberSaveable { mutableStateOf(0) }
    val communityFolders by viewModel.communityFolders.collectAsState()
    LaunchedEffect(view) { if (view == 1) viewModel.loadCommunityFolders() }
    var category by remember(initialCategory) { mutableStateOf(initialCategory) }
    var ages by remember { mutableStateOf(setOf<String>()) }
    var tags by remember { mutableStateOf(setOf<String>()) }
    var minRating by remember { mutableStateOf(0) }
    // Budget max en euros : null = peu importe, 0 = gratuit (même filtre que le web)
    var budget by remember { mutableStateOf<Int?>(null) }
    var sort by remember { mutableStateOf(Sort.FEATURED) }
    var showFilters by remember { mutableStateOf(false) }
    // État « j'aime » optimiste, par idée
    val likes = remember { mutableStateMapOf<String, Pair<Boolean, Int>>() }
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val children by viewModel.children.collectAsState()

    val allTags = remember(links) {
        links.flatMap { it.tags }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(20).map { it.key }
    }

    val list = remember(links, query, category, ages, tags, minRating, budget, sort) {
        val bands = AGES.filter { it.key in ages }
        links.filter { l ->
            (query.isBlank() || listOf(l.title, l.description, l.location, l.ownerDisplayName).any { it.contains(query, true) } ||
                l.tags.any { it.contains(query, true) }) &&
                (category == null || l.category == category) &&
                (bands.isEmpty() || bands.any { matchesAge(l.ageRange, it.min, it.max) }) &&
                (tags.isEmpty() || tags.all { it in l.tags }) &&
                l.rating >= minRating &&
                budget.let { max -> max == null || priceValue(l.price)?.let { if (max == 0) it == 0.0 else it < max } == true }
        }.let { f ->
            when (sort) {
                Sort.FEATURED -> {
                    val now = System.currentTimeMillis()
                    f.sortedByDescending { rotationScore(it.id, it.likeCount, it.createdAt, now) }
                }
                Sort.POPULAR -> f.sortedByDescending { it.likeCount }
                Sort.RECENT -> f.sortedByDescending { it.createdAt.ifEmpty { it.updatedAt } }
                Sort.RATED -> f.sortedWith(compareByDescending<Link> { it.rating }.thenByDescending { it.likeCount })
            }
        }
    }
    // Cartes construites par lots : toutes d'un coup (jusqu'à 700 idées avec
    // image) rendaient l'arrivée sur l'Explorer lente. Le lot suivant est ajouté
    // à l'approche du bas de la page.
    val scroll = rememberScrollState()
    var shown by remember(list) { mutableStateOf(PAGE) }
    val nearBottom by remember { derivedStateOf { scroll.maxValue - scroll.value < 1500 } }
    LaunchedEffect(nearBottom, shown, list.size) { if (nearBottom && shown < list.size) shown += PAGE }
    val activeFilters = ages.size + tags.size + (if (minRating > 0) 1 else 0) + (if (budget != null) 1 else 0) + (if (sort != Sort.FEATURED) 1 else 0)

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { viewModel.loadCommunityLinks() },
        modifier = modifier.fillMaxSize().background(Sable)
    ) {
        Column(
            Modifier.fillMaxSize().verticalScroll(scroll).statusBarsPadding().padding(top = 20.dp, bottom = ui.components.bottomNavClearance()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            PageHeader(
                category?.let { categoryUi(it).plural } ?: "Explorer",
                Modifier.padding(horizontal = 20.dp),
                subtitle = if (view == 1) "Les dossiers publiés par les tribus" else "Les idées partagées par les tribus",
                action = {
                    Row(
                        Modifier
                            .height(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(CardColor)
                            .border(ThinBorder, RoundedCornerShape(14.dp))
                            .clickable(role = Role.Button) { showFilters = true }
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Outlined.Tune, null, tint = Ecorce, modifier = Modifier.size(20.dp))
                        Text("Filtres", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Ecorce)
                        if (activeFilters > 0) {
                            Box(Modifier.size(20.dp).clip(RoundedCornerShape(10.dp)).background(Terracotta), contentAlignment = Alignment.Center) {
                                Text("$activeFilters", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                },
            )

            SearchField(
                query, { query = it },
                if (view == 1) "Anniversaire, vacances, recettes d'hiver…" else "Sortie nature, crêpes, Lego…",
                Modifier.fillMaxWidth().padding(horizontal = 20.dp)
            )

            SegmentedTabs(listOf("Idées", "Dossiers"), view, { view = it }, Modifier.padding(horizontal = 20.dp))

            if (view == 1) {
                CommunityFolderGrid(communityFolders, query, isRefreshing, onOpenFolder)
            } else {

            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Pill("Tout", category == null, { category = null })
                CategoryOrder.forEach { c ->
                    Pill(c.plural, category == c.category, { category = if (category == c.category) null else c.category }, idleBg = c.bg, idleFg = c.ink)
                }
            }

            Row(
                Modifier.padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Âge", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextSec)
                AGES.forEach { a ->
                    OutlinePill(a.label, a.key in ages) { ages = if (a.key in ages) ages - a.key else ages + a.key }
                }
            }

            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Ecorce)) {
                        append("${list.size} idée${if (list.size > 1) "s" else ""}")
                    }
                    append(" partagée${if (list.size > 1) "s" else ""} par la communauté")
                },
                fontSize = 13.sp, color = TextSec, modifier = Modifier.padding(horizontal = 20.dp)
            )

            Box(Modifier.padding(horizontal = 20.dp)) {
                if (list.isEmpty()) {
                    EmptyBlock(
                        Icons.Outlined.Explore,
                        if (isRefreshing) "Chargement…" else "Rien trouvé par ici",
                        "Élargissez les filtres ou demandez à l'IA de vous préparer un dossier sur mesure."
                    ) { CampButton("Demander à l'IA", onNavigateToAI, kind = ButtonKind.Ai, icon = Icons.Outlined.AutoAwesome) }
                } else {
                    // La carte IA s'insère en 4e position, comme sur la maquette
                    val slot = minOf(3, list.size)
                    val items: List<Link?> = (list.take(slot) + listOf(null) + list.drop(slot)).take(shown + 1)
                    TwoColumnMosaic(items) { l, i ->
                        if (l == null) {
                            AiPromoCard(onNavigateToAI)
                        } else {
                            val (liked, _) = likes[l.id] ?: (l.likedByMe to l.likeCount)
                            MosaicCard(
                                link = l.copy(likeCount = likes[l.id]?.second ?: l.likeCount),
                                index = i,
                                onClick = { onNavigateToDetail(l.id) },
                                liked = liked,
                                onLike = {
                                    val count = likes[l.id]?.second ?: l.likeCount
                                    likes[l.id] = !liked to (count + if (liked) -1 else 1)
                                    viewModel.toggleLike(l.id, liked) { ok, c -> likes[l.id] = ok to c }
                                }
                            )
                        }
                    }
                }
            }
            }
        }
    }

    if (showFilters) {
        ModalBottomSheet(onDismissRequest = { showFilters = false }, containerColor = SheetColor, dragHandle = { SheetHandle() }) {
            Column(
                Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Text("Filtres", style = display(24.sp))
                FilterGroup("Trier par") {
                    Sort.entries.forEach { s -> Pill(s.label, sort == s, { sort = s }) }
                }
                FilterGroup("Âge des enfants") {
                    AGES.forEach { a -> Pill("${a.label} ans", a.key in ages, { ages = if (a.key in ages) ages - a.key else ages + a.key }) }
                }
                if (children.isNotEmpty()) {
                    Row(
                        Modifier.clip(RoundedCornerShape(12.dp)).clickable {
                            ages = children.map { c ->
                                val y = ageInMonths(c.birthDate) / 12f
                                AGES.firstOrNull { y >= it.min && y < it.max }?.key ?: "10+"
                            }.toSet()
                        }.padding(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Outlined.ChildCare, null, tint = Terracotta, modifier = Modifier.size(18.dp))
                        Text("L'âge de mes enfants (${children.joinToString { it.name }})", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Terracotta)
                    }
                }
                FilterGroup("Budget") {
                    listOf<Pair<Int?, String>>(null to "Peu importe", 0 to "Gratuit", 10 to "Moins de 10 €").forEach { (b, label) ->
                        Pill(label, budget == b, { budget = b })
                    }
                }
                FilterGroup("Note minimale") {
                    listOf(0, 3, 4, 5).forEach { r -> Pill(if (r == 0) "Toutes" else "$r ★ et +", minRating == r, { minRating = r }) }
                }
                if (allTags.isNotEmpty()) {
                    FilterGroup("Tags populaires") {
                        allTags.forEach { t -> Pill("#$t", t in tags, { tags = if (t in tags) tags - t else tags + t }, idleBg = Sable2, idleFg = TextSec) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CampButton("Réinitialiser", {
                        ages = emptySet(); tags = emptySet(); minRating = 0; budget = null; sort = Sort.FEATURED
                    }, Modifier.weight(1f), kind = ButtonKind.Ghost)
                    CampButton("Voir ${list.size} idées", { showFilters = false }, Modifier.weight(1f), kind = ButtonKind.Dark)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterGroup(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextSec)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

@Composable
private fun AiPromoCard(onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Prune).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(Icons.Outlined.AutoAwesome, null, tint = Soleil, modifier = Modifier.size(26.dp))
        Text("Pas d'idée ? L'IA prépare un dossier sur mesure.", style = display(16.sp, FontWeight.Bold, OnDark))
        Text(
            "Essayer",
            fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF3A211B),
            modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Soleil).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}

/** Dossiers publics de la communauté, filtrés par la recherche. */
@Composable
private fun CommunityFolderGrid(folders: List<data.Folder>, query: String, loading: Boolean, onOpen: (data.Folder) -> Unit) {
    val q = query.trim().lowercase()
    val list = if (q.isEmpty()) folders else folders.filter { f ->
        f.name.lowercase().contains(q) || f.tags.any { it.lowercase().contains(q) } || f.ownerDisplayName.lowercase().contains(q)
    }
    Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "${list.size} dossier${if (list.size > 1) "s" else ""} partagé${if (list.size > 1) "s" else ""} par la communauté",
            fontSize = 13.sp, color = TextSec
        )
        if (list.isEmpty()) {
            EmptyBlock(
                Icons.Outlined.Explore,
                if (loading && folders.isEmpty()) "Chargement…" else "Aucun dossier public",
                "Rendez un de vos dossiers public pour inspirer les autres tribus."
            )
        } else {
            list.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { f ->
                        FolderCard(
                            f, f.linkCount, { onOpen(f) }, Modifier.weight(1f),
                            ownerLabel = listOfNotNull(
                                f.ownerDisplayName.takeIf { it.isNotBlank() },
                                f.likeCount.takeIf { it > 0 }?.let { "♥ $it" }
                            ).joinToString(" · ").ifBlank { null }
                        )
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * Prix comparable d'une idée, en euros : 0 si gratuite, le premier montant
 * trouvé sinon (« 6 € / enfant » → 6), null si absent ou illisible.
 * Même logique que `priceValue` côté web (frontend/src/lib/format.ts).
 */
private fun priceValue(price: String): Double? {
    val p = price.trim().lowercase()
    if (p.isEmpty()) return null
    if (Regex("gratuit|free|offert").containsMatchIn(p)) return 0.0
    return Regex("""\d+(?:[.,]\d+)?""").find(p)?.value?.replace(',', '.')?.toDoubleOrNull()
}
