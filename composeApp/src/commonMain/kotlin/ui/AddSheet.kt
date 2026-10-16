package ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import data.LinkCategory
import data.extractUrl
import ui.campfire.*

/** Ce que l'utilisateur a choisi dans la feuille « Ajouter à la tribu ». */
sealed class AddChoice {
    data class FromUrl(val url: String) : AddChoice()
    data class FromCategory(val category: LinkCategory) : AddChoice()
    object Folder : AddChoice()
    object Ai : AddChoice()
}

/** Feuille « Ajouter à la tribu » de la maquette. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSheet(onDismiss: () -> Unit, onChoice: (AddChoice) -> Unit) {
    val clipboard = LocalClipboardManager.current
    var url by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    // Le formulaire s'ouvre tout de suite : c'est lui qui récupère l'aperçu
    fun go(text: String): Boolean {
        val found = extractUrl(text) ?: return false
        onChoice(AddChoice.FromUrl(found))
        return true
    }
    fun submit() {
        if (url.isNotBlank() && !go(url)) error = "Cette adresse ne semble pas valide (ex. : marmiton.org/recette…)."
    }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = Sable, dragHandle = { SheetHandle() }) {
        Column(
            Modifier.padding(horizontal = 20.dp).padding(bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Text("Ajouter à la tribu", style = display(24.sp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Un lien repéré ?", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextSec)
                Row(
                    Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(18.dp)).background(androidx.compose.ui.graphics.Color.White)
                        .border(2.dp, when {
                            error != null -> androidx.compose.ui.graphics.Color(0xFF9A3420)
                            url.isNotBlank() -> Terracotta
                            else -> androidx.compose.ui.graphics.Color(0xFFE6D3C0)
                        }, RoundedCornerShape(18.dp))
                        .padding(start = 16.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Outlined.Link, null, tint = TextMuted, modifier = Modifier.size(20.dp))
                    Box(Modifier.weight(1f)) {
                        if (url.isEmpty()) Text("Coller l'adresse d'une page", color = TextMuted, fontSize = 15.sp, maxLines = 1)
                        BasicTextField(
                            value = url,
                            onValueChange = {
                                // Un lien collé (même entouré de texte) ouvre directement le formulaire
                                val pasted = it.length - url.length > 8
                                url = it
                                error = null
                                if (pasted) go(it)
                            },
                            singleLine = true,
                            textStyle = TextStyle(color = Ecorce, fontSize = 15.sp, fontWeight = FontWeight.Medium),
                            cursorBrush = SolidColor(Terracotta),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                            keyboardActions = KeyboardActions(onGo = { submit() }),
                            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Adresse de la page" }
                        )
                    }
                    if (url.isNotBlank()) {
                        SmallAction("Ajouter", Terracotta, androidx.compose.ui.graphics.Color.White) { submit() }
                    } else {
                        SmallAction("Coller", Ecorce, Sable) {
                            val text = clipboard.getText()?.text?.trim().orEmpty()
                            if (!go(text)) {
                                url = text
                                error = if (text.isEmpty()) "Le presse-papiers est vide." else "Le presse-papiers ne contient pas d'adresse web."
                            }
                        }
                    }
                }
                Text(
                    error ?: "Titre, images, et même ingrédients, prix ou date : tout est récupéré automatiquement.",
                    fontSize = 12.sp,
                    color = if (error != null) androidx.compose.ui.graphics.Color(0xFF9A3420) else TextSec,
                    fontWeight = if (error != null) FontWeight.SemiBold else FontWeight.Normal
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Ou partir d'une catégorie", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextSec)
                val cells: List<@Composable (Modifier) -> Unit> = CategoryOrder.map { c ->
                    @Composable { m: Modifier -> CategorySquare(c, { onChoice(AddChoice.FromCategory(c.category)) }, m) }
                } + listOf(@Composable { m: Modifier -> FolderSquare(m) { onChoice(AddChoice.Folder) } })
                cells.chunked(4).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { it(Modifier.weight(1f)) }
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Prune).clickable { onChoice(AddChoice.Ai) }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(Soleil), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.AutoAwesome, null, tint = Prune, modifier = Modifier.size(24.dp))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Demander à l'IA", style = display(17.sp, FontWeight.Bold, Sable))
                    Text("« 10 activités d'intérieur pour un jour de pluie »", fontSize = 13.sp, color = PruneSoft)
                }
                Icon(Icons.Outlined.ChevronRight, null, tint = Sable, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun SmallAction(text: String, bg: androidx.compose.ui.graphics.Color, fg: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Text(
        text, color = fg, fontSize = 13.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(bg).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 11.dp)
    )
}

@Composable
private fun FolderSquare(modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.height(78.dp).clip(RoundedCornerShape(18.dp)).dashedBorder(Dash, 18.dp).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically)
    ) {
        ui.components.TipiIcon(Terracotta, Modifier.size(22.dp))
        Text("Dossier", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextSec)
    }
}
