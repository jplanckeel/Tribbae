package ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import data.CircleInvitationInfo
import data.InvitationRepository
import ui.campfire.*

/**
 * « Rejoindre le cercle » : ouvert depuis le lien de l'email d'invitation à des
 * dossiers (https://…/invitation-cercle#<jeton>). Comme InvitationDialog : sans
 * compte, onSignIn mène à la connexion et la boîte revient ensuite.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CircleInvitationDialog(
    token: String,
    loggedIn: Boolean,
    onAccept: (onDone: (String?) -> Unit) -> Unit,
    onSignIn: () -> Unit,
    onDismiss: () -> Unit,
) {
    val repository = remember { InvitationRepository() }
    var info by remember { mutableStateOf<CircleInvitationInfo?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(token) {
        loading = true
        runCatching { repository.lookupCircle(token) }
            .onSuccess { info = it; if (it == null) error = "Cette invitation n'est plus valable : elle a expiré, a été annulée ou a déjà servi." }
            .onFailure { error = "Impossible de joindre le serveur. Vérifiez votre connexion." }
        loading = false
    }

    BasicAlertDialog(onDismissRequest = { if (!busy) onDismiss() }) {
        Surface(shape = RoundedCornerShape(24.dp), color = Sable) {
            Column(
                Modifier.padding(24.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                val inv = info
                when {
                    loading -> Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Terracotta)
                    }
                    inv == null -> {
                        Text("Invitation introuvable", style = display(24.sp))
                        Text(
                            (error ?: "") + " Demandez à la personne qui vous a invité de vous renvoyer une invitation.",
                            fontSize = 14.sp, color = TextSec,
                        )
                        CampButton("Fermer", onDismiss, Modifier.fillMaxWidth(), kind = ButtonKind.Ghost)
                    }
                    else -> {
                        val inviter = inv.invitedByName.ifBlank { "Un membre de Tribbae" }
                        val several = inv.folderNames.size > 1
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.size(44.dp).clip(CircleShape).background(Color(0xFFFFC857)), contentAlignment = Alignment.Center) {
                                Text(inviter.take(1).uppercase(), fontWeight = FontWeight.Bold, color = Color(0xFF3A211B))
                            }
                            Text("$inviter vous fait une place dans son cercle", fontSize = 14.sp, color = TextSec)
                        }
                        Text(
                            if (several) "Des dossiers vous sont ouverts" else "Le dossier « ${inv.folderNames.firstOrNull().orEmpty()} » vous est ouvert",
                            style = display(26.sp)
                        )
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(CardColor)
                                .border(1.dp, Line, RoundedCornerShape(22.dp)).padding(horizontal = 16.dp, vertical = 6.dp),
                        ) {
                            inv.folderNames.forEach { name ->
                                Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(Sable2), contentAlignment = Alignment.Center) {
                                        Icon(Icons.Outlined.Folder, null, tint = Ecorce, modifier = Modifier.size(18.dp))
                                    }
                                    Text(name, style = display(17.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                        Text(
                            (if (inv.editor) "Vous pourrez y voir les idées et en ajouter : sorties, cadeaux, recettes…" else "Vous pourrez y retrouver ses idées de sorties, de cadeaux ou de recettes.") +
                                " Vous ne verrez que ${if (several) "ces dossiers" else "ce dossier"}.",
                            fontSize = 14.sp, color = Ecorce2,
                        )
                        error?.let { Text(it, color = CoralInk, fontSize = 14.sp, fontWeight = FontWeight.Medium) }
                        if (loggedIn) {
                            CampButton(if (busy) "Un instant…" else if (several) "Voir les dossiers" else "Voir le dossier", {
                                if (busy) return@CampButton
                                busy = true
                                error = null
                                onAccept { err -> busy = false; error = err }
                            }, Modifier.fillMaxWidth())
                        } else {
                            CampButton("Créer mon compte ou me connecter", onSignIn, Modifier.fillMaxWidth())
                        }
                        Text("Invitation envoyée à ${inv.email}. Utilisez cette adresse pour l'accepter.", fontSize = 12.sp, color = TextSec)
                        TextButton(onClick = { if (!busy) onDismiss() }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                            Text("Plus tard", color = TextSec)
                        }
                    }
                }
            }
        }
    }
}
