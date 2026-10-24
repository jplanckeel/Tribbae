package ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import data.Folder
import kotlinx.coroutines.launch
import ui.campfire.*
import viewmodel.LinkViewModel

private const val ROLE_EDITOR = "COLLABORATOR_ROLE_EDITOR"
private const val ROLE_VIEWER = "COLLABORATOR_ROLE_VIEWER"

/** Inviter dans un dossier : lien de partage (lecture sans compte) et membres par email. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareFolderSheet(folder: Folder, viewModel: LinkViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val folders by viewModel.folders.collectAsState()
    val current = folders.find { it.id == folder.id } ?: folder
    var shareUrl by remember { mutableStateOf<String?>(null) }
    var linkBusy by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var role by remember { mutableStateOf(ROLE_EDITOR) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    fun sendLink(url: String) {
        context.startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "${current.name} sur Tribbae :\n$url"),
                "Partager le dossier"
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = SheetColor, dragHandle = { SheetHandle() }) {
        Column(
            Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text("Inviter dans « ${current.name} »", style = display(24.sp))

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Lien de partage", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextSec)
                Text("Toute personne qui a le lien peut consulter ce dossier, même sans compte.", fontSize = 14.sp, color = Ecorce2)
                if (shareUrl != null) {
                    Row(
                        Modifier.fillMaxWidth().card(RoundedCornerShape(18.dp), 4.dp).padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(shareUrl!!, fontSize = 14.sp, color = TextSec, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        CampButton("Envoyer", { sendLink(shareUrl!!) }, kind = ButtonKind.Dark, icon = Icons.Outlined.IosShare, height = 40.dp)
                    }
                } else {
                    CampButton(
                        if (linkBusy) "Création…" else "Créer un lien de partage",
                        {
                            linkBusy = true
                            scope.launch {
                                val r = viewModel.shareFolder(current.id)
                                linkBusy = false
                                if (r == null) {
                                    message = "Connexion requise pour partager un dossier."
                                } else {
                                    val url = r.shareUrl.ifBlank { "${data.Backend.PRODUCTION}/share/${r.shareToken}" }
                                    shareUrl = url
                                    sendLink(url)
                                }
                            }
                        },
                        kind = ButtonKind.Dark, icon = Icons.Outlined.Link, enabled = !linkBusy
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Membres invités", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextSec)
                if (current.collaborators.isEmpty()) {
                    Text("Invitez un proche qui a un compte Tribbae pour remplir ce dossier ensemble.", fontSize = 14.sp, color = TextSec)
                } else {
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(CardColor)) {
                        current.collaborators.forEachIndexed { i, c ->
                            val name = c.displayName.ifBlank { c.email.ifBlank { "Membre" } }
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                InitialAvatar(name, i + 1, 36.dp, 12.dp, 15.sp)
                                Column(Modifier.weight(1f)) {
                                    Text(name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Ecorce, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(if (c.role == ROLE_EDITOR) "Peut ajouter des idées" else "Lecture seule", fontSize = 12.sp, color = TextSec)
                                }
                                CircleIconButton(Icons.Outlined.Close, "Retirer $name", {
                                    scope.launch { viewModel.removeCollaborator(current.id, c.userId) }
                                }, background = Sable2, size = 36.dp)
                            }
                            if (i < current.collaborators.lastIndex) HorizontalDivider(color = Sable2)
                        }
                    }
                }

                CampField(
                    "Email du proche", email, { email = it; message = null },
                    placeholder = "camille@exemple.fr", icon = Icons.Outlined.PersonAdd,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(ROLE_EDITOR to "Peut ajouter", ROLE_VIEWER to "Lecture seule").forEach { (k, label) ->
                        val on = role == k
                        Box(
                            Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(12.dp)).background(if (on) Ecorce else Sable2)
                                .clickable(role = Role.RadioButton) { role = k },
                            contentAlignment = Alignment.Center
                        ) { Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (on) Sable else TextSec) }
                    }
                }
                if (message != null) Text(message!!, fontSize = 14.sp, color = CoralInk)
                CampButton(
                    if (busy) "Invitation…" else "Inviter",
                    {
                        busy = true
                        scope.launch {
                            val r = viewModel.addCollaborator(current.id, email.trim(), role)
                            busy = false
                            if (r == null) message = "Invitation impossible : vérifiez l'email (compte Tribbae requis)."
                            else { email = ""; message = null }
                        }
                    },
                    Modifier.fillMaxWidth(), icon = Icons.Outlined.Send, enabled = email.contains("@") && !busy
                )
            }
        }
    }
}
