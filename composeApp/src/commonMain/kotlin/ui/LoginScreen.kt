package ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import data.AuthRepository
import data.SessionManager
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    sessionManager: SessionManager,
    authRepository: AuthRepository,
    onLoginSuccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isLoginMode by remember { mutableStateOf(true) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SurfaceColor)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        
        // Logo
        Icon(
            imageVector = Icons.Default.Lightbulb,
            contentDescription = "Tribbae",
            modifier = Modifier.size(80.dp),
            tint = Orange
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Tribbae",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        
        Text(
            text = "Vos idées en famille",
            fontSize = 16.sp,
            color = TextSecondary
        )
        
        Spacer(modifier = Modifier.height(48.dp))
        
        // Tabs
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { isLoginMode = true; errorMessage = null },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isLoginMode) Orange else CardColor,
                    contentColor = if (isLoginMode) Color.White else TextSecondary
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Connexion", fontWeight = FontWeight.SemiBold)
            }
            
            Button(
                onClick = { isLoginMode = false; errorMessage = null },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (!isLoginMode) Orange else CardColor,
                    contentColor = if (!isLoginMode) Color.White else TextSecondary
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Inscription", fontWeight = FontWeight.SemiBold)
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Form
        if (!isLoginMode) {
            OutlinedTextField(
                value = displayName,
                onValueChange = { displayName = it; errorMessage = null },
                label = { Text("Pseudo") },
                leadingIcon = {
                    Icon(Icons.Default.Person, contentDescription = null)
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Orange,
                    focusedLabelColor = Orange,
                    focusedLeadingIconColor = Orange
                ),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next
                )
            )
            
            Spacer(modifier = Modifier.height(16.dp))
        }
        
        OutlinedTextField(
            value = email,
            onValueChange = { email = it; errorMessage = null },
            label = { Text("Email") },
            leadingIcon = {
                Icon(Icons.Default.Email, contentDescription = null)
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Orange,
                focusedLabelColor = Orange,
                focusedLeadingIconColor = Orange
            ),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next
            )
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        OutlinedTextField(
            value = password,
            onValueChange = { password = it; errorMessage = null },
            label = { Text("Mot de passe") },
            leadingIcon = {
                Icon(Icons.Default.Lock, contentDescription = null)
            },
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Default.Visibility
                        else Icons.Default.VisibilityOff,
                        contentDescription = if (passwordVisible) "Masquer" else "Afficher"
                    )
                }
            },
            visualTransformation = if (passwordVisible) VisualTransformation.None
            else PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Orange,
                focusedLabelColor = Orange,
                focusedLeadingIconColor = Orange
            ),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    if (email.isNotBlank() && password.isNotBlank()) {
                        scope.launch {
                            handleAuth(
                                isLoginMode = isLoginMode,
                                email = email,
                                password = password,
                                displayName = displayName,
                                authRepository = authRepository,
                                sessionManager = sessionManager,
                                onLoading = { isLoading = it },
                                onError = { errorMessage = it },
                                onSuccess = onLoginSuccess
                            )
                        }
                    }
                }
            )
        )
        
        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = null,
                        tint = Color(0xFFD32F2F),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = errorMessage ?: "",
                        color = Color(0xFFD32F2F),
                        fontSize = 14.sp
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(
            onClick = {
                scope.launch {
                    handleAuth(
                        isLoginMode = isLoginMode,
                        email = email,
                        password = password,
                        displayName = displayName,
                        authRepository = authRepository,
                        sessionManager = sessionManager,
                        onLoading = { isLoading = it },
                        onError = { errorMessage = it },
                        onSuccess = onLoginSuccess
                    )
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            enabled = !isLoading && email.isNotBlank() && password.isNotBlank() &&
                    (isLoginMode || displayName.isNotBlank()),
            colors = ButtonDefaults.buttonColors(
                containerColor = Orange,
                disabledContainerColor = Orange.copy(alpha = 0.5f)
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
            } else {
                Text(
                    text = if (isLoginMode) "Se connecter" else "S'inscrire",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        TextButton(
            onClick = { /* TODO: Mode invité */ }
        ) {
            Text(
                text = "Continuer sans compte",
                color = TextSecondary,
                fontSize = 14.sp
            )
        }
        
        Spacer(modifier = Modifier.height(32.dp))
    }
}

private suspend fun handleAuth(
    isLoginMode: Boolean,
    email: String,
    password: String,
    displayName: String,
    authRepository: AuthRepository,
    sessionManager: SessionManager,
    onLoading: (Boolean) -> Unit,
    onError: (String) -> Unit,
    onSuccess: () -> Unit
) {
    println("DEBUG handleAuth: Début - isLoginMode=$isLoginMode, email=$email")
    onLoading(true)
    try {
        println("DEBUG handleAuth: Appel authRepository.${if (isLoginMode) "login" else "register"}")
        val response = if (isLoginMode) {
            authRepository.login(email, password)
        } else {
            authRepository.register(email, password, displayName)
        }
        
        println("DEBUG handleAuth: Réponse reçue - userId=${response.userId}, token=${response.token}, displayName=${response.displayName}")
        
        // Vérifier que la réponse contient bien les données nécessaires
        if (response.token.isBlank()) {
            println("DEBUG handleAuth: Token vide!")
            onError("Erreur: Token vide reçu du serveur")
            return
        }
        
        println("DEBUG handleAuth: Sauvegarde de la session")
        sessionManager.saveSession(response.userId, response.token, response.displayName)
        println("DEBUG handleAuth: Succès!")
        onSuccess()
    } catch (e: Exception) {
        val errorMsg = e.message ?: "Erreur inconnue"
        println("DEBUG handleAuth: Exception - $errorMsg")
        e.printStackTrace()
        onError(
            when {
                errorMsg.contains("401") || errorMsg.contains("Invalid") || errorMsg.contains("invalid credentials") ->
                    "Email ou mot de passe incorrect"
                errorMsg.contains("409") || errorMsg.contains("already exists") ->
                    "Cet email est déjà utilisé"
                errorMsg.contains("Connection") || errorMsg.contains("timeout") || errorMsg.contains("Unable to resolve host") ->
                    "Impossible de se connecter au serveur"
                errorMsg.contains("SSL") || errorMsg.contains("certificate") ->
                    "Erreur de certificat SSL"
                else -> "Erreur: $errorMsg"
            }
        )
    } finally {
        println("DEBUG handleAuth: Fin - loading=false")
        onLoading(false)
    }
}

