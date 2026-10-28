package ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import data.ApiConnectedApp
import data.ConnectedApps
import kotlinx.coroutines.launch
import ui.campfire.*
import viewmodel.LinkViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val longDate = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRENCH)

/** « 28 octobre 2026 », ou "" si la date est absente ou illisible. */
private fun frenchDate(rfc3339: String): String =
    runCatching { Instant.parse(rfc3339).atZone(ZoneId.of("Europe/Paris")).format(longDate) }.getOrDefault("")

/**
 * « Applications connectées » : accès donnés à Claude (serveur MCP), à voir
 * et retirer, comme sur le web (frontend/src/pages/Tribe.tsx, ConnectedAppsSheet).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectedAppsSheet(viewModel: LinkViewModel, onDismiss: () -> Unit) {
    var apps by remember { mutableStateOf<List<ApiConnectedApp>?>(null) }
    var failed by remember { mutableStateOf(false) }
    var reloadKey by remember { mutableIntStateOf(0) }
    LaunchedEffect(reloadKey) {
        val list = viewModel.connectedApps()
        failed = list == null
        apps = list
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = SheetColor, dragHandle = { SheetHandle() }) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Applications connectées", style = display(24.sp))
            Text(
                "Les applications auxquelles vous avez donné accès à votre compte, par exemple Claude. Elles peuvent seulement " +
                    "voir vos dossiers et y ajouter des idées privées. Un accès dure au plus 90 jours.",
                fontSize = 14.sp, color = TextSec
            )
            when {
                failed -> Text("Impossible de joindre le serveur, réessayez.", color = CoralInk, fontSize = 14.sp)
                apps == null -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Terracotta) }
                apps!!.isEmpty() -> Text(
                    "Aucune application n'a accès à votre compte.",
                    fontSize = 15.sp, color = Ecorce2,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(CardColor).padding(14.dp)
                )
                else -> apps!!.forEach { app ->
                    key(app.id) { ConnectedAppCard(viewModel, app) { reloadKey++ } }
                }
            }
        }
    }
}

@Composable
private fun ConnectedAppCard(viewModel: LinkViewModel, app: ApiConnectedApp, onRevoked: () -> Unit) {
    val scope = rememberCoroutineScope()
    var deleteIdeas by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val name = app.name.ifBlank { "Application" }
    val ideas = app.ideaCount

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(CardColor).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Prune), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.AutoAwesome, null, tint = Soleil, modifier = Modifier.size(20.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(name, fontWeight = FontWeight.Bold, color = Ecorce, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val connected = frenchDate(app.connectedAt)
                Text(listOf(app.site, if (connected.isNotEmpty()) "connectée le $connected" else "").filter { it.isNotEmpty() }.joinToString(" · "),
                    fontSize = 12.sp, color = TextSec)
            }
        }
        ConnectedApps.scopeLabels(app.scopes).forEach { Text("• $it", fontSize = 14.sp, color = Ecorce2) }
        val lastUsed = frenchDate(app.lastUsedAt)
        val until = frenchDate(app.expiresAt)
        Text(
            listOfNotNull(
                lastUsed.takeIf { it.isNotEmpty() }?.let { "Dernier usage le $it" },
                until.takeIf { it.isNotEmpty() }?.let { "accès jusqu'au $it" },
                if (ideas > 1) "$ideas idées ajoutées" else "$ideas idée ajoutée",
            ).joinToString(" · "),
            fontSize = 12.sp, color = TextSec
        )
        if (ideas > 0) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (ideas > 1) "Effacer aussi les $ideas idées ajoutées" else "Effacer aussi l'idée ajoutée",
                    fontSize = 14.sp, color = Ecorce, modifier = Modifier.weight(1f))
                CampSwitch(deleteIdeas, { deleteIdeas = it }, "Effacer aussi les idées ajoutées par cette application")
            }
        }
        error?.let { Text(it, color = CoralInk, fontSize = 13.sp, fontWeight = FontWeight.Medium) }
        CampButton(
            if (busy) "Retrait…" else "Retirer l'accès", onClick = { confirm = true },
            kind = ButtonKind.Ghost, icon = Icons.Outlined.Close, enabled = !busy, height = 44.dp,
            modifier = Modifier.fillMaxWidth()
        )
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            containerColor = SheetColor,
            title = { Text("Retirer l'accès de $name ?", style = display(20.sp, FontWeight.Bold)) },
            text = {
                Text(
                    if (deleteIdeas) "$name ne pourra plus rien faire sur votre compte, et " +
                        (if (ideas > 1) "les $ideas idées qu'elle a ajoutées seront effacées." else "l'idée qu'elle a ajoutée sera effacée.")
                    else "$name ne pourra plus rien faire sur votre compte. Les idées qu'elle a ajoutées restent.",
                    color = Ecorce2, fontSize = 14.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    busy = true
                    error = null
                    scope.launch {
                        viewModel.revokeConnectedApp(app.id, deleteIdeas)
                            // La liste se recharge : la carte de l'application disparaît
                            .onSuccess { onRevoked() }
                            .onFailure { error = it.message }
                        busy = false
                    }
                }) { Text("Retirer l'accès", color = CoralInk, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Annuler", color = Ecorce) } }
        )
    }
}
