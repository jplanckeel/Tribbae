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
                .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Mes idées", style = display(32.sp))
                Text(
                    "${links.size} idée${if (links.size > 1) "s" else ""} rangée${if (links.size > 1) "s" else ""} dans ${folders.size} dossier${if (folders.size > 1) "s" else ""}",
                    fontSize = 14.sp, color = TextSec
                )
            }

            SegmentedTabs(listOf("Dossiers", "Favoris", "Tags"), tab, { tab = it }, Modifier.fillMaxWidth())

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

                    // Grille 2 colonnes : dossiers, « Non rangées », puis « Nouveau dossier »
                    val tiles: List<@Composable (Modifier) -> Unit> = buildList {
                        folders.forEach { f ->
                            add { m -> FolderCard(f, counts[f.id] ?: f.linkCount, { onFolderClick(f) }, m) }
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
