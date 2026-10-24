package ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import data.LinkCategory
import ui.campfire.*
import viewmodel.LinkViewModel

private const val PREFS = "tribbae_courses"

/** Liste de courses : ingrédients des recettes choisies, regroupés et cochables (conservés sur l'appareil). */
@Composable
fun ShoppingListScreen(
    viewModel: LinkViewModel,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onAddRecipe: () -> Unit = {},
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    val links by viewModel.repository.links.collectAsState()
    var selected by remember { mutableStateOf(prefs.getStringSet("recipes", emptySet())!!.toSet()) }
    var checked by remember { mutableStateOf(prefs.getStringSet("checked", emptySet())!!.toSet()) }

    LaunchedEffect(selected, checked) {
        prefs.edit().putStringSet("recipes", selected).putStringSet("checked", checked).apply()
    }

    val recipes = remember(links) { links.filter { it.category == LinkCategory.RECETTE && it.ingredients.isNotEmpty() } }
    val items = remember(recipes, selected) {
        val m = linkedMapOf<String, Pair<String, MutableList<String>>>()
        recipes.filter { it.id in selected }.forEach { r ->
            r.ingredients.map { it.trim() }.filter { it.isNotEmpty() }.forEach { name ->
                val e = m.getOrPut(name.lowercase()) { name to mutableListOf() }
                if (r.title !in e.second) e.second += r.title
            }
        }
        m.entries.sortedBy { it.value.first.lowercase() }
    }
    val remaining = items.count { it.key !in checked }
    val rc = categoryUi(LinkCategory.RECETTE)

    Column(
        modifier.fillMaxSize().background(Sable).verticalScroll(rememberScrollState()).statusBarsPadding().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CircleIconButton(Icons.Outlined.ChevronLeft, "Retour", onBack)
            Column {
                Text("Liste de courses", style = display(28.sp))
                Text(
                    if (items.isEmpty()) "Choisissez vos recettes de la semaine" else "$remaining restant${if (remaining > 1) "s" else ""} sur ${items.size}",
                    fontSize = 14.sp, color = TextSec
                )
            }
        }

        if (recipes.isEmpty()) {
            EmptyBlock(
                Icons.Outlined.Restaurant, "Aucune recette avec ingrédients",
                "Ajoutez une recette et listez ses ingrédients : ils se retrouveront ici, regroupés."
            ) { CampButton("Ajouter une recette", onAddRecipe, icon = Icons.Outlined.Add) }
            return@Column
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Recettes", style = display(18.sp, FontWeight.Bold))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                recipes.forEach { r ->
                    val on = r.id in selected
                    Row(
                        Modifier.clip(CircleShape).background(if (on) rc.tint else rc.bg)
                            .clickable(role = Role.Checkbox) { selected = if (on) selected - r.id else selected + r.id }
                            .padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(Modifier.size(24.dp).clip(CircleShape).background(if (on) Color.White.copy(alpha = 0.25f) else CardColor), contentAlignment = Alignment.Center) {
                            Icon(if (on) Icons.Outlined.Check else Icons.Outlined.Add, null, tint = if (on) Color.White else rc.ink, modifier = Modifier.size(14.dp))
                        }
                        Text(r.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (on) Color.White else rc.ink, maxLines = 1)
                        Text("${r.ingredients.size}", fontSize = 13.sp, color = (if (on) Color.White else rc.ink).copy(alpha = 0.7f))
                    }
                }
            }
        }

        if (items.isEmpty()) {
            Text("Touchez une ou plusieurs recettes pour composer la liste.", fontSize = 14.sp, color = TextSec)
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                Text("${items.size} ingrédient${if (items.size > 1) "s" else ""}", style = display(18.sp, FontWeight.Bold))
                if (checked.isNotEmpty()) {
                    Text(
                        "Tout décocher", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Terracotta,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { checked = emptySet() }.padding(4.dp)
                    )
                }
            }
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(CardColor)) {
                items.forEachIndexed { i, (key, value) ->
                    val (name, from) = value
                    val done = key in checked
                    Row(
                        Modifier.fillMaxWidth().clickable(role = Role.Checkbox) { checked = if (done) checked - key else checked + key }.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            Modifier.size(24.dp).clip(RoundedCornerShape(8.dp)).background(if (done) rc.tint else Color.Transparent)
                                .then(if (done) Modifier else Modifier.border(2.dp, Dash, RoundedCornerShape(8.dp))),
                            contentAlignment = Alignment.Center
                        ) { if (done) Icon(Icons.Outlined.Check, null, tint = Color.White, modifier = Modifier.size(14.dp)) }
                        Column(Modifier.weight(1f)) {
                            Text(
                                name, fontSize = 15.sp, color = if (done) TextMuted else Ecorce,
                                fontWeight = if (done) FontWeight.Normal else FontWeight.Medium,
                                textDecoration = if (done) TextDecoration.LineThrough else null
                            )
                            if (from.size > 1) Text(from.joinToString(" · "), fontSize = 12.sp, color = TextSec, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    if (i < items.lastIndex) HorizontalDivider(color = Sable2)
                }
            }
        }
    }
}
