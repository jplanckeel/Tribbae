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

private enum class Sort(val label: String) { POPULAR("Les plus aimées"), RECENT("Les plus récentes"), RATED("Les mieux notées") }

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
    modifier: Modifier = Modifier
) {
    var query by remember(initialQuery) { mutableStateOf(initialQuery) }
    var category by remember(initialCategory) { mutableStateOf(initialCategory) }
    var ages by remember { mutableStateOf(setOf<String>()) }
    var tags by remember { mutableStateOf(setOf<String>()) }
    var minRating by remember { mutableStateOf(0) }
    var sort by remember { mutableStateOf(Sort.POPULAR) }
    var showFilters by remember { mutableStateOf(false) }
    // État « j'aime » optimiste, par idée
    val likes = remember { mutableStateMapOf<String, Pair<Boolean, Int>>() }
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val children by viewModel.children.collectAsState()

    val allTags = remember(links) {
        links.flatMap { it.tags }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(20).map { it.key }
    }

    val list = remember(links, query, category, ages, tags, minRating, sort) {
        val bands = AGES.filter { it.key in ages }
        links.filter { l ->
            (query.isBlank() || listOf(l.title, l.description, l.location, l.ownerDisplayName).any { it.contains(query, true) } ||
                l.tags.any { it.contains(query, true) }) &&
                (category == null || l.category == category) &&
                (bands.isEmpty() || bands.any { matchesAge(l.ageRange, it.min, it.max) }) &&
                (tags.isEmpty() || tags.all { it in l.tags }) &&
                l.rating >= minRating
        }.let { f ->
            when (sort) {
                Sort.POPULAR -> f.sortedByDescending { it.likeCount }
                Sort.RECENT -> f.sortedByDescending { it.createdAt.ifEmpty { it.updatedAt } }
                Sort.RATED -> f.sortedWith(compareByDescending<Link> { it.rating }.thenByDescending { it.likeCount })
            }
        }
    }
    val activeFilters = ages.size + tags.size + (if (minRating > 0) 1 else 0) + (if (sort != Sort.POPULAR) 1 else 0)

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { viewModel.loadCommunityLinks() },
        modifier = modifier.fillMaxSize().background(Sable)
    ) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(top = 20.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(category?.let { categoryUi(it).plural } ?: "Explorer", style = display(32.sp))
                Row(
                    Modifier
                        .height(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White)
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
            }

            SearchField(query, { query = it }, "Sortie nature, crêpes, Lego…", Modifier.fillMaxWidth().padding(horizontal = 20.dp))

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
                    val items: List<Link?> = list.take(slot) + listOf(null) + list.drop(slot)
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

    if (showFilters) {
        ModalBottomSheet(onDismissRequest = { showFilters = false }, containerColor = Sable, dragHandle = { SheetHandle() }) {
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
                        ages = emptySet(); tags = emptySet(); minRating = 0; sort = Sort.POPULAR
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
        Text("Pas d'idée ? L'IA prépare un dossier sur mesure.", style = display(16.sp, FontWeight.Bold, Sable))
        Text(
            "Essayer",
            fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Ecorce,
            modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Soleil).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}
