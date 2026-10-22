package ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import data.InvitationRepository
import data.TribeInvitationInfo
import ui.campfire.*

/**
 * « Rejoindre la tribu » : ouvert depuis le lien de l'email d'invitation
 * (https://…/invitation#<jeton>). Sans compte, onSignIn mène à la connexion ;
 * la boîte revient ensuite pour accepter.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvitationDialog(
    token: String,
    loggedIn: Boolean,
    onAccept: (onDone: (String?) -> Unit) -> Unit,
    onSignIn: () -> Unit,
    onDismiss: () -> Unit,
) {
    val repository = remember { InvitationRepository() }
    var info by remember { mutableStateOf<TribeInvitationInfo?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(token) {
        loading = true
        runCatching { repository.lookup(token) }
            .onSuccess { info = it; if (it == null) error = "Cette invitation n'est plus valable : elle a expiré, a été annulée ou a déjà servi." }
            .onFailure { error = "Impossible de joindre le serveur. Vérifiez votre connexion." }
        loading = false
    }

    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }) {
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
                        val tribeName = inv.tribeName.ifBlank { inviter }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.size(44.dp).clip(CircleShape).background(Color(0xFFFFC857)), contentAlignment = Alignment.Center) {
                                Text(inviter.take(1).uppercase(), fontWeight = FontWeight.Bold, color = Ecorce)
                            }
                            Text("$inviter vous invite à rejoindre sa tribu", fontSize = 14.sp, color = TextSec)
                        }
                        Text("Rejoignez la tribu «\u00A0$tribeName\u00A0»", style = display(26.sp))
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color.White)
                                .border(1.dp, Line, RoundedCornerShape(22.dp)).padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(tribeName, style = display(20.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                val n = inv.memberNames.size
                                Text("$n membre${if (n > 1) "s" else ""}", fontSize = 13.sp, color = TextSec)
                            }
                            Row {
                                val shown = if (inv.memberNames.size > 3) inv.memberNames.take(2) else inv.memberNames
                                shown.forEachIndexed { i, m -> InitialAvatar(m, i, 34.dp, 17.dp, 13.sp) }
                                if (inv.memberNames.size > 3) {
                                    Box(Modifier.size(34.dp).clip(CircleShape).background(Prune), contentAlignment = Alignment.Center) {
                                        Text("+${inv.memberNames.size - 2}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Soleil)
                                    }
                                }
                            }
                        }
                        Text(
                            "Dans une tribu, chacun ajoute ses idées de sorties, de cadeaux ou de recettes dans des dossiers partagés, et toute la famille les retrouve au même endroit. Vos propres idées restent privées tant que vous n'avez pas confirmé leur partage.",
                            fontSize = 14.sp, color = Ecorce2,
                        )
                        error?.let { Text(it, color = Color(0xFF9A3420), fontSize = 14.sp, fontWeight = FontWeight.Medium) }
                        if (loggedIn) {
                            CampButton(if (busy) "Un instant…" else "Rejoindre la tribu", {
                                if (busy) return@CampButton
                                busy = true
                                error = null
                                onAccept { err -> busy = false; error = err }
                            }, Modifier.fillMaxWidth())
                        } else {
                            CampButton("Créer mon compte ou me connecter", onSignIn, Modifier.fillMaxWidth())
                        }
                        Text("Invitation envoyée à ${inv.email}. Utilisez cette adresse pour la rejoindre.", fontSize = 12.sp, color = TextSec)
                        TextButton(onClick = { if (!busy) onDismiss() }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                            Text("Plus tard", color = TextSec)
                        }
                    }
                }
            }
        }
    }
}
