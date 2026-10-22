package ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import data.LinkCategory
import ui.campfire.*
import viewmodel.LinkViewModel

private val SUGGESTIONS = listOf(
    "10 activités d'intérieur pour un jour de pluie",
    "Goûters sans sucre ajouté",
    "Cadeaux de Noël pour une ado de 13 ans",
    "Jeux en plein air pour un anniversaire de 6 ans",
    "Recettes à cuisiner avec un enfant de 4 ans",
    "Bricolages avec des rouleaux en carton",
    "Activités calmes avant le coucher",
    "Idées de sorties en forêt avec des petits",
    "Repas rapides pour les soirs de semaine",
    "Livres pour un enfant qui commence à lire",
    "Cadeaux faits maison pour la fête des mères",
    "Jeux de société coopératifs pour les 5–8 ans",
    "Activités pour occuper les enfants en voiture",
    "Déco de chambre pour une petite fille de 7 ans",
    "Expériences scientifiques faciles à la maison",
    "Idées de pique-nique originales",
    "Activités manuelles pour Halloween",
    "Desserts sans four pour l'été",
    "Cadeaux à moins de 20 € pour un garçon de 10 ans",
    "Jeux d'eau pour les journées de canicule",
    "Activités Montessori pour un bébé de 18 mois",
    "Romans d'aventure pour les 9–12 ans",
    "Idées de week-end en famille à la campagne",
    "Recettes de légumes que les enfants aiment",
    "Chasse au trésor à organiser dans le jardin",
    "Activités pour les vacances de la Toussaint",
    "Décorations de Noël à fabriquer ensemble",
    "Petits-déjeuners équilibrés et gourmands",
    "Jeux pour apprendre les lettres en s'amusant",
    "Cadeaux de naissance utiles",
    "Sorties culturelles pour des ados",
    "Activités sportives à faire en famille",
    "Recettes de crêpes et galettes pour la Chandeleur",
    "Albums jeunesse sur les émotions",
    "Jeux de rôle pour les 3–5 ans",
    "Idées pour un anniversaire à la maison",
    "Plantes faciles à faire pousser avec les enfants",
    "Activités pour un dimanche après-midi d'hiver",
    "Goûters d'anniversaire faits maison",
    "Rituels du soir pour aider à s'endormir",
    "Idées de cabane à construire",
    "Cadeaux pour remercier la maîtresse",
)

private const val SHOWN_SUGGESTIONS = 4

/** Quelques suggestions tirées au hasard. */
private fun pickSuggestions(): List<String> = SUGGESTIONS.shuffled().take(SHOWN_SUGGESTIONS)

private fun toCategory(raw: String) =
    runCatching { LinkCategory.valueOf(raw.removePrefix("LINK_CATEGORY_")) }.getOrDefault(LinkCategory.IDEE)

/** « Demander à l'IA » : écran prune de la maquette, idées à cocher puis à ranger. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AiGenerateScreen(
    viewModel: LinkViewModel,
    onBack: () -> Unit,
    initialFolderId: String? = null,
    initialPrompt: String = "",
    onSavedInFolder: (String?) -> Unit = { onBack() },
) {
    val ideas by viewModel.aiIdeas.collectAsState()
    val loading by viewModel.aiLoading.collectAsState()
    val error by viewModel.aiError.collectAsState()
    val folders by viewModel.folders.collectAsState()
    var prompt by remember { mutableStateOf(initialPrompt) }
    var suggestions by remember { mutableStateOf(pickSuggestions()) }
    var selected by remember(ideas) { mutableStateOf(ideas.indices.toSet()) }
    val categories = remember(ideas) { mutableStateMapOf<Int, LinkCategory>() }
    var target by remember { mutableStateOf(initialFolderId) } // null = nouveau dossier
    var showTarget by remember { mutableStateOf(false) }

    DisposableEffect(Unit) { onDispose { viewModel.clearAiIdeas() } }

    fun generate() {
        if (prompt.isNotBlank() && !loading) viewModel.generateAiIdeas(prompt.trim())
    }

    fun save() {
        val chosen = ideas.mapIndexedNotNull { i, idea ->
            if (i in selected) idea.copy(category = "LINK_CATEGORY_" + (categories[i] ?: toCategory(idea.category)).name) else null
        }
        if (chosen.isEmpty()) return
        val t = target
        if (t == null) {
            viewModel.saveAiIdeasInNewFolder(chosen, prompt.trim()) { id -> onSavedInFolder(id) }
        } else {
            viewModel.saveAiIdeas(chosen, t)
            onSavedInFolder(t)
        }
    }

    Box(Modifier.fillMaxSize().background(Prune)) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = if (ideas.isNotEmpty()) 160.dp else 40.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Demander à l'IA", style = display(26.sp, color = Sable))
                CircleIconButton(Icons.Outlined.Close, "Fermer", onBack, background = Color.White.copy(alpha = 0.12f), tint = Sable)
            }
            Text("Décrivez ce que vous cherchez : l'IA prépare des idées prêtes à ranger.", fontSize = 14.sp, color = PruneSoft)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier.weight(1f).heightIn(min = 64.dp).clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.1f))
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    if (prompt.isEmpty()) Text("« 10 activités d'intérieur pour un jour de pluie »", color = Color.White.copy(alpha = 0.45f), fontSize = 15.sp)
                    BasicTextField(
                        value = prompt,
                        onValueChange = { if (it.length <= 500) prompt = it },
                        textStyle = TextStyle(color = Sable, fontSize = 15.sp),
                        cursorBrush = SolidColor(Soleil),
                        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Votre demande" }
                    )
                }
                Box(
                    Modifier.size(64.dp).clip(RoundedCornerShape(18.dp)).background(if (prompt.isBlank()) Soleil.copy(alpha = 0.5f) else Soleil)
                        .clickable(enabled = prompt.isNotBlank() && !loading, role = Role.Button, onClick = ::generate)
                        .semantics { contentDescription = "Générer des idées" },
                    contentAlignment = Alignment.Center
                ) {
                    if (loading) CircularProgressIndicator(color = Prune, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                    else Icon(Icons.Outlined.AutoAwesome, null, tint = Prune, modifier = Modifier.size(26.dp))
                }
            }

            if (ideas.isEmpty() && !loading) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    suggestions.forEach { s ->
                        Text(
                            s, fontSize = 13.sp, color = PruneSoft,
                            modifier = Modifier.clip(CircleShape).border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                                .clickable { prompt = s }.padding(horizontal = 12.dp, vertical = 7.dp)
                        )
                    }
                    Row(
                        Modifier.clip(CircleShape).clickable(role = Role.Button) { suggestions = pickSuggestions() }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Outlined.AutoAwesome, null, tint = Soleil, modifier = Modifier.size(14.dp))
                        Text("D'autres idées", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Soleil)
                    }
                }
            }

            if (loading) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color.White.copy(alpha = 0.06f)).padding(vertical = 40.dp, horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Outlined.AutoAwesome, null, tint = Soleil, modifier = Modifier.size(32.dp))
                    Text("L'IA prépare vos idées…", style = display(18.sp, FontWeight.Bold, Sable))
                    Text("Rédaction en cours, comptez 20 à 60 secondes.", fontSize = 14.sp, color = PruneSoft)
                }
            }

            error?.let {
                Text(it, fontSize = 14.sp, color = Soleil, fontWeight = FontWeight.Medium,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.1f)).padding(14.dp))
            }

            ideas.forEachIndexed { i, idea ->
                val on = i in selected
                val cat = categoryUi(categories[i] ?: toCategory(idea.category))
                var showCats by remember { mutableStateOf(false) }
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(if (on) Color.White else Color.White.copy(alpha = 0.06f))
                        .clickable(role = Role.Checkbox) { selected = if (on) selected - i else selected + i }.padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        Modifier.size(24.dp).clip(RoundedCornerShape(8.dp)).background(if (on) Terracotta else Color.Transparent)
                            .then(if (on) Modifier else Modifier.border(2.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(8.dp))),
                        contentAlignment = Alignment.Center
                    ) { if (on) Icon(Icons.Outlined.Check, null, tint = Color.White, modifier = Modifier.size(14.dp)) }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(idea.title, style = display(16.sp, FontWeight.Bold, if (on) Ecorce else Sable))
                        if (idea.description.isNotBlank()) {
                            Text(idea.description, fontSize = 13.sp, color = if (on) TextSec else PruneSoft, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box {
                                Row(
                                    Modifier.clip(CircleShape).background(cat.bg).clickable { showCats = true }.padding(start = 10.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(cat.label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = cat.ink)
                                    Icon(Icons.Outlined.ExpandMore, "Changer la catégorie", tint = cat.ink, modifier = Modifier.size(16.dp))
                                }
                                DropdownMenu(showCats, { showCats = false }, containerColor = Sable) {
                                    CategoryOrder.forEach { c ->
                                        DropdownMenuItem(
                                            text = { Text(c.label, color = Ecorce) },
                                            leadingIcon = { Icon(c.icon, null, tint = c.tint) },
                                            onClick = { categories[i] = c.category; showCats = false }
                                        )
                                    }
                                }
                            }
                            val meta = idea.ageRange
                            if (meta.isNotEmpty()) Text(meta, fontSize = 12.sp, color = if (on) TextSec else PruneSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }

            if (ideas.isNotEmpty()) {
                CampButton("Relancer", ::generate, kind = ButtonKind.Ghost, icon = Icons.Outlined.Refresh, height = 40.dp)
            }
        }

        if (ideas.isNotEmpty()) {
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Prune).navigationBarsPadding().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.1f)).clickable { showTarget = true }.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Ranger dans", fontSize = 13.sp, color = PruneSoft)
                    Text(
                        folders.find { it.id == target }?.name ?: "Nouveau dossier « ${prompt.trim().take(30)} »",
                        fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Sable, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                    )
                    Icon(Icons.Outlined.ExpandMore, null, tint = Sable)
                }
                Row(
                    Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(18.dp)).background(if (selected.isEmpty()) Soleil.copy(alpha = 0.5f) else Soleil)
                        .clickable(enabled = selected.isNotEmpty(), role = Role.Button, onClick = ::save),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                ) {
                    Icon(Icons.Outlined.Check, null, tint = Ecorce, modifier = Modifier.size(18.dp))
                    Text("Garder ${selected.size} idée${if (selected.size > 1) "s" else ""}", fontWeight = FontWeight.Bold, color = Ecorce, fontSize = 15.sp)
                }
            }
        }
    }

    if (showTarget) {
        ModalBottomSheet(onDismissRequest = { showTarget = false }, containerColor = Sable, dragHandle = { SheetHandle() }) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Ranger dans…", style = display(24.sp), modifier = Modifier.padding(bottom = 8.dp))
                MenuRow(Icons.Outlined.AutoAwesome, "Nouveau dossier", tint = Prune) { target = null; showTarget = false }
                folders.forEach { f -> MenuRow(folderIconVector(f), f.name, tint = folderSolid(f.color)) { target = f.id; showTarget = false } }
            }
        }
    }
}
