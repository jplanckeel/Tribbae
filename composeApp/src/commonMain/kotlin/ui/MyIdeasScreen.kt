package ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import data.Folder
import data.Link
import data.LinkCategory
import ui.campfire.*

/** « Mes idées » : dossiers, favoris et tags de la tribu. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MyIdeasScreen(
    links: List<Link>,
    folders: List<Folder>,
    onNavigateToDetail: (String) -> Unit,
    onSaveLink: (Link) -> Unit,
    onAddFolderClick: () -> Unit,
    onFolderClick: (Folder) -> Unit,
    onNavigateToAdd: () -> Unit = {},
    onNavigateToCategory: (LinkCategory) -> Unit = {},
    viewModel: viewmodel.LinkViewModel,
    onOpenShopping: () -> Unit = {},
    onOpenLoose: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var tab by rememberSaveable { mutableStateOf(0) }
    var selectedTag by rememberSaveable { mutableStateOf<String?>(null) }
    val isRefreshing by viewModel.isRefreshing.collectAsState()

    val counts = remember(links) { links.filter { it.folderId != null }.groupingBy { it.folderId!! }.eachCount() }
    val folderIds = remember(folders) { folders.map { it.id }.toSet() }
    val loose = remember(links, folderIds) { links.filter { it.folderId == null || it.folderId !in folderIds } }
    val recipes = remember(links) { links.filter { it.category == LinkCategory.RECETTE && it.ingredients.isNotEmpty() } }
    val ingredientCount = remember(recipes) { recipes.flatMap { r -> r.ingredients.map { it.trim().lowercase() } }.toSet().size }
    val children by viewModel.children.collectAsState()
    var folderSort by rememberSaveable { mutableStateOf("recent") }
    val pinned = remember(folders) { folders.filter { it.pinned } }
    val others = remember(folders, folderSort) { sortFolders(folders.filter { !it.pinned }, folderSort) }
    val tagTotal = remember(links) { links.flatMap { it.tags }.toSet().size }
    val favTotal = remember(links) { links.count { it.favorite } }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { viewModel.forceSync() },
        modifier = modifier.fillMaxSize().background(Sable)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = ui.components.bottomNavClearance()),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Mes idées", style = display(32.sp))
                Text(
                    "${links.size} idée${if (links.size > 1) "s" else ""} · ${folders.size} dossier${if (folders.size > 1) "s" else ""} · $tagTotal tag${if (tagTotal > 1) "s" else ""}",
                    fontSize = 14.sp, color = TextSec
                )
            }

            SegmentedTabs(
                listOf(Triple("Dossiers", folders.size, 0), Triple("Favoris", favTotal, 1), Triple("Tags", tagTotal, 2)).map { (l, n, _) -> if (n > 0) "$l $n" else l },
                tab, { tab = it }, Modifier.fillMaxWidth()
            )

            when (tab) {
                0 -> {
                    Row(
                        Modifier.fillMaxWidth().card(RoundedCornerShape(18.dp), 4.dp).clickable(role = Role.Button, onClick = onOpenShopping).padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val c = categoryUi(LinkCategory.RECETTE)
                        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(c.bg), contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.ShoppingCart, null, tint = c.tint, modifier = Modifier.size(20.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Text("Liste de courses", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Ecorce)
                            Text(
                                if (recipes.isEmpty()) "À partir de vos recettes"
                                else "$ingredientCount ingrédient${if (ingredientCount > 1) "s" else ""} · ${recipes.size} recette${if (recipes.size > 1) "s" else ""}",
                                fontSize = 13.sp, color = TextSec
                            )
                        }
                        Icon(Icons.Outlined.ChevronRight, null, tint = TextMuted, modifier = Modifier.size(20.dp))
                    }

                    if (pinned.isNotEmpty()) {
                        SectionLabel("Épinglés", Icons.Outlined.PushPin)
                        pinned.forEach { f -> PinnedFolderCard(f, counts[f.id] ?: f.linkCount, { onFolderClick(f) }, children) }
                    }

                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        SectionLabel("Tous les dossiers")
                        Box {
                            var open by remember { mutableStateOf(false) }
                            Row(
                                Modifier.clip(RoundedCornerShape(8.dp)).clickable(role = Role.DropdownList) { open = true }.padding(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(FOLDER_SORTS.first { it.first == folderSort }.second, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextSec)
                                Icon(Icons.Outlined.ExpandMore, "Trier les dossiers", tint = TextSec, modifier = Modifier.size(16.dp))
                            }
                            DropdownMenu(open, { open = false }, containerColor = Color.White) {
                                FOLDER_SORTS.forEach { (k, label) ->
                                    DropdownMenuItem(
                                        text = { Text(label, fontWeight = if (k == folderSort) FontWeight.Bold else FontWeight.Normal, color = Ecorce) },
                                        onClick = { folderSort = k; open = false }
                                    )
                                }
                            }
                        }
                    }

                    // Grille 2 colonnes : dossiers, « Non rangées », puis « Nouveau dossier »
                    val tiles: List<@Composable (Modifier) -> Unit> = buildList {
                        others.forEach { f ->
                            add { m -> FolderCard(f, counts[f.id] ?: f.linkCount, { onFolderClick(f) }, m, children = children) }
                        }
                        if (loose.isNotEmpty()) add { m -> LooseCard(loose.size, onOpenLoose, m) }
                        add { m -> DashedAddTile("Nouveau dossier", onAddFolderClick, m) }
                    }
                    tiles.chunked(2).forEach { row ->
                        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            row.forEach { tile -> tile(Modifier.weight(1f).fillMaxHeight()) }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }

                1 -> {
                    val favs = links.filter { it.favorite }.sortedByDescending { it.updatedAt }
                    if (favs.isEmpty()) {
                        EmptyBlock(
                            Icons.Outlined.StarBorder,
                            "Pas encore de favoris",
                            "Ouvrez une idée et touchez l'étoile pour la retrouver ici en un clin d'œil."
                        )
                    } else {
                        TwoColumnMosaic(favs) { l, i ->
                            MosaicCard(l, i, { onNavigateToDetail(l.id) }, liked = true, onLike = { onSaveLink(l) })
                        }
                    }
                }

                else -> {
                    val tagCounts = remember(links) {
                        links.flatMap { it.tags }.groupingBy { it }.eachCount().entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
                    }
                    if (tagCounts.isEmpty()) {
                        EmptyBlock(Icons.Outlined.Sell, "Aucun tag", "Ajoutez des tags à vos idées (#automne, #pluie…) pour les retrouver par thème.")
                    } else {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            tagCounts.forEach { (t, n) ->
                                val on = t == selectedTag
                                Text(
                                    "#$t  $n",
                                    fontSize = 14.sp,
                                    color = if (on) Sable else TextSec,
                                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (on) Ecorce else Sable2)
                                        .clickable { selectedTag = if (on) null else t }
                                        .padding(horizontal = 12.dp, vertical = 7.dp)
                                )
                            }
                        }
                        selectedTag?.let { t ->
                            links.filter { t in it.tags }.forEach { l -> RowCard(l, onClick = { onNavigateToDetail(l.id) }) }
                        }
                    }
                }
            }
        }
    }
}

private val FOLDER_SORTS = listOf("recent" to "Récents", "date" to "Date clé", "name" to "A → Z")

private fun sortFolders(list: List<Folder>, sort: String): List<Folder> = when (sort) {
    "name" -> list.sortedBy { it.name.lowercase() }
    // Prochaine date clé d'abord, puis les dossiers sans date
    "date" -> list.sortedWith(compareBy<Folder> { data.FolderStyle.countdown(it)?.days ?: Int.MAX_VALUE }.thenByDescending { it.updatedAt })
    else -> list.sortedByDescending { it.updatedAt }
}

@Composable
private fun SectionLabel(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (icon != null) Icon(icon, null, tint = Terracotta, modifier = Modifier.size(14.dp))
        Text(text.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.8.sp, color = TextSec)
    }
}

@Composable
private fun LooseCard(count: Int, onClick: () -> Unit, modifier: Modifier) {
    Box(modifier.card().clickable(onClick = onClick)) {
        Box(Modifier.align(Alignment.TopEnd).offset(x = 14.dp, y = (-14).dp).size(64.dp).clip(RoundedCornerShape(32.dp)).background(Sable2))
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(TextSec), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.BookmarkBorder, null, tint = Color.White, modifier = Modifier.size(22.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Non rangées", style = display(16.sp, FontWeight.Bold))
                Text("$count idée${if (count > 1) "s" else ""}", fontSize = 13.sp, color = TextSec)
            }
        }
    }
}
