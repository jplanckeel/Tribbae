package ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import data.AuthRepository
import data.AuthResponse
import kotlinx.coroutines.launch


/** Message d'erreur lisible pour le mot de passe oublié. */
internal fun resetErrorMessage(e: Exception): String {
    val msg = e.message.orEmpty()
    return when {
        msg.contains("invalid or expired reset link") -> "Ce lien n'est plus valable : il a expiré ou a déjà servi. Demandez-en un nouveau."
        msg.contains("password must be") -> "Le mot de passe doit contenir entre 8 et 72 caractères."
        msg.contains("HTTP 429") -> "Trop de tentatives, réessayez dans une minute."
        msg.contains("timeout", ignoreCase = true) || msg.contains("Unable to resolve host") || msg.contains("Connection") ->
            "Impossible de joindre le serveur. Vérifiez votre connexion."
        else -> "Une erreur est survenue, réessayez."
    }
}

@Composable
private fun ErrorBox(message: String) {
    Surface(Modifier.fillMaxWidth(), color = CoralBg, shape = RoundedCornerShape(8.dp)) {
        Text(message, color = Color(0xFFB2412A), fontSize = 12.sp, modifier = Modifier.padding(10.dp))
    }
}

private val fieldColors @Composable get() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Terracotta,
    focusedLabelColor = Terracotta,
)

/** « Mot de passe oublié » : demande d'un lien de réinitialisation par email. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForgotPasswordDialog(
    authRepository: AuthRepository,
    initialEmail: String = "",
    onDismiss: () -> Unit,
) {
    var email by remember { mutableStateOf(initialEmail) }
    var sentTo by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    AlertDialog(onDismissRequest = { if (!loading) onDismiss() }) {
        Surface(shape = RoundedCornerShape(24.dp), color = Color.White) {
            Column(
                Modifier.padding(24.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                val sent = sentTo
                if (sent != null) {
                    Text("Regardez vos emails", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ecorce)
                    Text(
                        "Si un compte Tribbae utilise $sent, un lien pour choisir un nouveau mot de passe vient d'y être envoyé. Il est valable une heure.",
                        fontSize = 14.sp, color = TextSec,
                    )
                    Text(
                        "Rien reçu ? Regardez dans les indésirables, ou vérifiez l'adresse et recommencez dans une minute.",
                        fontSize = 12.sp, color = TextSec,
                    )
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Terracotta),
                        shape = RoundedCornerShape(12.dp),
                    ) { Text("Compris", fontWeight = FontWeight.Bold) }
                    TextButton(onClick = { sentTo = null }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        Text("Changer d'adresse", color = TextSec)
                    }
                } else {
                    Text("Mot de passe oublié", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ecorce)
                    Text(
                        "Indiquez l'adresse de votre compte : nous vous enverrons un lien pour en choisir un nouveau.",
                        fontSize = 14.sp, color = TextSec,
                    )
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it; error = null },
                        label = { Text("Email") },
                        leadingIcon = { Icon(Icons.Filled.Email, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        colors = fieldColors,
                    )
                    error?.let { ErrorBox(it) }
                    Button(
                        onClick = {
                            scope.launch {
                                loading = true
                                error = null
                                try {
                                    authRepository.forgotPassword(email)
                                    sentTo = email.trim()
                                } catch (e: Exception) {
                                    error = resetErrorMessage(e)
                                } finally {
                                    loading = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !loading && email.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = Terracotta),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        if (loading) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                        else Text("Recevoir le lien", fontWeight = FontWeight.Bold)
                    }
                    TextButton(onClick = { if (!loading) onDismiss() }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        Text("Annuler", color = TextSec)
                    }
                }
            }
        }
    }
}

/**
 * « Nouveau mot de passe » : ouvert depuis le lien de l'email
 * (https://…/mot-de-passe/nouveau#<jeton>). onDone reçoit la nouvelle session.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewPasswordDialog(
    token: String,
    authRepository: AuthRepository,
    onDone: (AuthResponse) -> Unit,
    onDismiss: () -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showForgot by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    if (showForgot) {
        ForgotPasswordDialog(authRepository = authRepository, onDismiss = onDismiss)
        return
    }

    AlertDialog(onDismissRequest = { if (!loading) onDismiss() }) {
        Surface(shape = RoundedCornerShape(24.dp), color = Color.White) {
            Column(
                Modifier.padding(24.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("Nouveau mot de passe", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ecorce)
                Text(
                    "Choisissez-le, puis vous serez connecté. Vos autres appareils devront se reconnecter.",
                    fontSize = 14.sp, color = TextSec,
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; error = null },
                    label = { Text("Nouveau mot de passe") },
                    supportingText = { Text("8 caractères minimum") },
                    leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = { visible = !visible }) {
                            Icon(
                                if (visible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                contentDescription = if (visible) "Masquer le mot de passe" else "Afficher le mot de passe",
                            )
                        }
                    },
                    visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    colors = fieldColors,
                )
                error?.let { msg ->
                    ErrorBox(msg)
                    if (msg.contains("n'est plus valable")) {
                        TextButton(onClick = { showForgot = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                            Text("Demander un nouveau lien", color = Terracotta, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                Button(
                    onClick = {
                        if (password.length < 8) {
                            error = "Le mot de passe doit contenir au moins 8 caractères."
                            return@Button
                        }
                        scope.launch {
                            loading = true
                            error = null
                            try {
                                onDone(authRepository.resetPassword(token, password))
                            } catch (e: Exception) {
                                error = resetErrorMessage(e)
                            } finally {
                                loading = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !loading && password.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = Terracotta),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    if (loading) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    else Text("Enregistrer et me connecter", fontWeight = FontWeight.Bold)
                }
                TextButton(onClick = { if (!loading) onDismiss() }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("Annuler", color = TextSec)
                }
            }
        }
    }
}
