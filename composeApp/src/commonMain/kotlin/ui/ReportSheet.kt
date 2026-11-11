package ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import data.Moderation
import kotlinx.coroutines.launch
import ui.campfire.CampButton
import ui.campfire.SheetHandle
import ui.campfire.display

/** Signaler une idée, un commentaire ou un compte : un motif, des précisions facultatives. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportSheet(targetType: String, onSend: suspend (reason: String, details: String) -> String?, onDismiss: () -> Unit) {
    var reason by remember { mutableStateOf<String?>(null) }
    var details by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = SheetColor, dragHandle = { SheetHandle() }) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(Moderation.title(targetType), style = display(24.sp))
            Text("Votre signalement reste anonyme : la personne concernée ne saura pas qu'il vient de vous.", fontSize = 14.sp, color = TextSec)
            Text("Pourquoi ?", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextSec)
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Moderation.REASONS.forEach { (key, label) ->
                    val on = reason == key
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(CardColor)
                            .border(2.dp, if (on) Ecorce else Color.Transparent, RoundedCornerShape(18.dp))
                            .selectable(on, role = Role.RadioButton) { reason = key; error = null }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        RadioButton(selected = on, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = Ecorce))
                        Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ecorce)
                    }
                }
            }
            OutlinedTextField(
                value = details, onValueChange = { details = it.take(Moderation.DETAILS_MAX) },
                label = { Text("Précisions (facultatif)") }, minLines = 3, modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
            )
            error?.let { Text(it, fontSize = 14.sp, color = CoralInk) }
            CampButton(
                if (busy) "Envoi…" else "Envoyer le signalement",
                {
                    val r = reason ?: return@CampButton
                    busy = true
                    scope.launch {
                        error = onSend(r, details.trim())
                        busy = false
                        if (error == null) onDismiss()
                    }
                },
                Modifier.fillMaxWidth(), enabled = reason != null && !busy,
            )
        }
    }
}

/** Comptes bloqués : leurs idées et commentaires sont cachés ; on peut les débloquer. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockedSheet(viewModel: viewmodel.LinkViewModel, onDismiss: () -> Unit) {
    var list by remember { mutableStateOf<List<data.ApiBlockedUser>?>(null) }
    var failed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { list = viewModel.blockedUsers(); failed = list == null }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = SheetColor, dragHandle = { SheetHandle() }) {
        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Comptes bloqués", style = display(24.sp))
            when {
                failed -> Text("La liste n'a pas pu être chargée. Réessayez dans un instant.", fontSize = 14.sp, color = CoralInk)
                list == null -> CircularProgressIndicator(color = Terracotta, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                list!!.isEmpty() -> Text("Aucun compte bloqué. Depuis une idée, le menu « … » permet de bloquer son auteur.", fontSize = 14.sp, color = TextSec)
                else -> list!!.forEach { u ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(CardColor).padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(u.displayName.ifBlank { "Compte" }, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ecorce, modifier = Modifier.weight(1f))
                        TextButton(onClick = {
                            scope.launch { if (viewModel.unblockUser(u.userId)) list = list.orEmpty().filterNot { it.userId == u.userId } }
                        }) { Text("Débloquer", color = TerracottaDark, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
    }
}
