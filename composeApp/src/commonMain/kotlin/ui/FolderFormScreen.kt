package ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import data.Folder
import data.FolderColor
import data.FolderIcon
import ui.campfire.*
import viewmodel.LinkViewModel

private val COLOR_LABELS = mapOf(
    FolderColor.BLUE to "Lac", FolderColor.ORANGE to "Braise", FolderColor.GREEN to "Forêt", FolderColor.RED to "Terre",
    FolderColor.YELLOW to "Soleil", FolderColor.PURPLE to "Prune", FolderColor.PINK to "Rose", FolderColor.TEAL to "Sauge",
)

/** Création / modification d'un dossier : nom, couleur, icône, visibilité. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FolderFormScreen(viewModel: LinkViewModel, onBack: () -> Unit, existing: Folder? = null) {
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var icon by remember { mutableStateOf(existing?.icon ?: FolderIcon.FOLDER) }
    var color by remember { mutableStateOf(existing?.color ?: FolderColor.ORANGE) }
    var isPublic by remember { mutableStateOf(existing?.visibility?.uppercase()?.contains("PUBLIC") == true) }
    var error by remember { mutableStateOf<String?>(null) }
    val solid = folderSolid(color)

    fun submit() {
        if (name.isBlank()) { error = "Donnez un nom au dossier."; return }
        if (existing != null) {
            viewModel.updateFolder(existing.copy(name = name.trim(), icon = icon, color = color, visibility = if (isPublic) "PUBLIC" else "PRIVATE"))
        } else {
            viewModel.addFolder(name.trim(), icon, color, if (isPublic) "PUBLIC" else "PRIVATE")
        }
        onBack()
    }

    Box(Modifier.fillMaxSize().background(Sable)) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(if (existing != null) "Modifier le dossier" else "Nouveau dossier", style = display(26.sp))
                CircleIconButton(Icons.Outlined.Close, "Fermer", onBack, background = Sable2)
            }

            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(56.dp).clip(RoundedCornerShape(18.dp)).background(solid), contentAlignment = Alignment.Center) {
                    Icon(folderIconVector(Folder(id = "", name = "", icon = icon)), null, tint = Color.White, modifier = Modifier.size(26.dp))
                }
                CampField("Nom", name, { name = it; error = null }, Modifier.weight(1f), placeholder = "Vacances Drôme", maxLength = 100)
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Couleur", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextSec)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    FolderColor.entries.forEach { c ->
                        val on = c == color
                        Box(
                            Modifier.size(40.dp).scale(if (on) 1.1f else 1f).clip(CircleShape).background(folderSolid(c))
                                .border(3.dp, if (on) Ecorce else Color.White, CircleShape)
                                .clickable(role = Role.RadioButton) { color = c }
                                .semantics { contentDescription = COLOR_LABELS[c] ?: c.name; selected = on }
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Icône", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextSec)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), maxItemsInEachRow = 7) {
                    FolderIcon.entries.forEach { i ->
                        val on = i == icon
                        Box(
                            Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(if (on) solid else Color.White)
                                .clickable(role = Role.RadioButton) { icon = i }
                                .semantics { contentDescription = i.name.lowercase(); selected = on },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(folderIconVector(Folder(id = "", name = "", icon = i)), null, tint = if (on) Color.White else Ecorce, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Visibilité", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextSec)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(false to "Privé", true to "Public").forEach { (pub, label) ->
                        val on = isPublic == pub
                        Column(
                            Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).background(if (on) Color.White else Sable2)
                                .border(2.dp, if (on) Ecorce else Color.Transparent, RoundedCornerShape(18.dp))
                                .clickable(role = Role.RadioButton) { isPublic = pub }.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(if (pub) Icons.Outlined.Public else Icons.Outlined.Lock, null, tint = Ecorce, modifier = Modifier.size(16.dp))
                                Text(label, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Ecorce)
                            }
                            Text(if (pub) "Vos idées du dossier visibles par tous, dans l'Explorer" else "Vous et les membres invités", fontSize = 12.sp, color = TextSec)
                        }
                    }
                }
            }

            error?.let { Text(it, color = Color(0xFF9A3420), fontSize = 14.sp) }
        }

        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Sable).navigationBarsPadding().imePadding().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 16.dp)) {
            CampButton(if (existing != null) "Enregistrer" else "Créer le dossier", ::submit, Modifier.fillMaxWidth())
        }
    }
}
