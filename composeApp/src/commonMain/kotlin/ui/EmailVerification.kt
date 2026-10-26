package ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import data.AuthRepository
import ui.campfire.card
import viewmodel.LinkViewModel

// Confirmation de l'adresse email : tant qu'elle n'est pas confirmée, publier
// et inviter sont refusés par le serveur (comme sur le web, components/VerifyEmailBanner.tsx).

/**
 * Bandeau « Confirmez votre adresse » avec « Renvoyer l'email ». Rien quand
 * l'adresse est confirmée (ou sans compte).
 */
@Composable
fun VerifyEmailBanner(viewModel: LinkViewModel, modifier: Modifier = Modifier) {
    val verified by viewModel.emailVerified.collectAsState()
    if (verified) return
    var state by remember { mutableStateOf(ResendState.Idle) }
    var error by remember { mutableStateOf<String?>(null) }
    Column(
        modifier.fillMaxWidth().card(RoundedCornerShape(18.dp), 4.dp).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(PeachBg), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Email, null, tint = PeachInk, modifier = Modifier.size(18.dp))
            }
            Column(Modifier.weight(1f)) {
                Text("Confirmez votre adresse email", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Ecorce)
                Text("Ouvrez le lien que nous vous avons envoyé pour publier vos idées et inviter votre tribu.", fontSize = 12.sp, color = TextSec)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            error?.let { Text(it, fontSize = 12.sp, color = CoralInk, modifier = Modifier.weight(1f)) } ?: Spacer(Modifier.weight(1f))
            TextButton(
                enabled = state == ResendState.Idle,
                onClick = {
                    state = ResendState.Sending
                    error = null
                    viewModel.resendEmailVerification { err ->
                        error = err
                        state = if (err == null) ResendState.Sent else ResendState.Idle
                    }
                }
            ) {
                Text(
                    when (state) {
                        ResendState.Idle -> "Renvoyer l'email"
                        ResendState.Sending -> "Envoi…"
                        ResendState.Sent -> "Email envoyé"
                    },
                    color = Terracotta, fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

private enum class ResendState { Idle, Sending, Sent }

/** Petite note sous un réglage « public » verrouillé tant que l'adresse n'est pas confirmée. */
@Composable
fun PublishLockedNote(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(Icons.Outlined.Lock, null, tint = TextSec, modifier = Modifier.size(14.dp))
        Text(data.EmailVerification.PUBLISH_LOCKED, fontSize = 12.sp, color = TextSec)
    }
}

/**
 * Note sous le choix du dossier : une idée rangée dans un dossier public est
 * publiée, elle attend donc sur le téléphone que l'adresse soit confirmée.
 */
@Composable
fun PublicFolderWaitingNote(viewModel: LinkViewModel, folder: data.Folder?, modifier: Modifier = Modifier) {
    val verified by viewModel.emailVerified.collectAsState()
    if (verified || folder == null || !folder.visibility.uppercase().contains("PUBLIC")) return
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(Icons.Outlined.Lock, null, tint = TextSec, modifier = Modifier.size(14.dp))
        Text(data.EmailVerification.PUBLIC_FOLDER_WAITS, fontSize = 12.sp, color = TextSec)
    }
}

/**
 * « Confirmer mon adresse » : ouvert depuis le lien de l'email
 * (https://tribbae.fr/verifier-email#<jeton>). Le lien est vérifié dès
 * l'ouverture ; sans session, il confirme quand même l'adresse.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerifyEmailDialog(
    token: String,
    authRepository: AuthRepository,
    viewModel: LinkViewModel,
    loggedIn: Boolean,
    onDismiss: () -> Unit,
) {
    // null = en cours ; puis succès ou message d'erreur
    var result by remember(token) { mutableStateOf<Result<Unit>?>(null) }
    LaunchedEffect(token) {
        result = runCatching { authRepository.verifyEmail(token) }
        // Le lien peut être celui d'un autre compte : on relit l'état du compte connecté
        if (result?.isSuccess == true && loggedIn) viewModel.refreshAccount()
    }
    BasicAlertDialog(onDismissRequest = { if (result != null) onDismiss() }) {
        Surface(shape = RoundedCornerShape(24.dp), color = SheetColor) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                val r = result
                when {
                    r == null -> Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Terracotta)
                    }
                    r.isSuccess -> {
                        Text("Adresse confirmée !", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ecorce)
                        Text("Merci. Vous pouvez maintenant publier vos idées et inviter votre tribu autour du feu.", fontSize = 14.sp, color = TextSec)
                    }
                    else -> {
                        Text("Lien plus valable", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ecorce)
                        Text(verifyErrorMessage(r.exceptionOrNull()), fontSize = 14.sp, color = TextSec)
                        if (loggedIn) VerifyEmailBanner(viewModel)
                        else Text("Connectez-vous pour en recevoir un nouveau.", fontSize = 14.sp, color = TextSec)
                    }
                }
                if (r != null) {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Terracotta, contentColor = OnDark),
                        shape = RoundedCornerShape(12.dp),
                    ) { Text(if (r.isSuccess) "Ouvrir Tribbae" else "Fermer", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

private fun verifyErrorMessage(e: Throwable?): String {
    val msg = e?.message.orEmpty()
    return when {
        msg.contains("invalid or expired verification link") -> "Ce lien n'est plus valable : il a expiré ou a déjà servi. Demandez-en un nouveau."
        msg.contains("HTTP 429") -> "Trop de tentatives, réessayez dans une minute."
        else -> "Impossible de joindre le serveur. Vérifiez votre connexion et rouvrez le lien."
    }
}
