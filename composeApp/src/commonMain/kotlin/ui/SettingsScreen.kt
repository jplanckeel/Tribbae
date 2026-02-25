package ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import viewmodel.LinkViewModel

// Couleurs pour les avatars enfants
private val childColors = listOf(
    Color(0xFF4FC3F7), Color(0xFFFF8C00), Color(0xFF81C784),
    Color(0xFFBA68C8), Color(0xFFFF7043), Color(0xFFFFD700)
)

@Composable
fun SettingsScreen(viewModel: LinkViewModel, modifier: Modifier = Modifier, onCommunityClick: () -> Unit = {}) {
    val children by viewModel.children.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var editingChild by remember { mutableStateOf<data.Child?>(null) }

    Column(modifier = modifier.fillMaxSize().padding(20.dp)) {
        Text("Tribbae", fontWeight = FontWeight.Bold, fontSize = 24.sp, color = TextPrimary)
        Text("Organisation familiale", color = TextSecondary, fontSize = 14.sp)

        Spacer(modifier = Modifier.height(20.dp))

        // Section Enfants
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = Icons.Default.ChildCare, contentDescription = null, tint = Orange, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Text("Mes enfants", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextPrimary)
            Spacer(modifier = Modifier.weight(1f))
            FilledIconButton(
                onClick = { showAddDialog = true },
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Orange)
            ) {
                Icon(imageVector = Icons.Default.PersonAdd, contentDescription = "Ajouter", tint = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "Sélectionnez un enfant sur l'accueil pour filtrer par âge",
            fontSize = 12.sp, color = TextSecondary
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (children.isEmpty()) {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = CardColor)
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.FamilyRestroom, contentDescription = null,
                            modifier = Modifier.size(56.dp), tint = OrangeLight.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("Ajoutez vos enfants", color = TextSecondary)
                        Text("pour filtrer les idées par âge", fontSize = 12.sp, color = TextSecondary)
                    }
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(children) { child ->
                    val colorIndex = children.indexOf(child) % childColors.size
                    val avatarColor = childColors[colorIndex]
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = CardColor),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(avatarColor),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    child.name.take(1).uppercase(),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp,
                                    color = Color.White
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(child.name, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                                Text(
                                    "${formatChildAge(child.birthDate)} · né le ${formatDate(child.birthDate)}",
                                    fontSize = 13.sp, color = TextSecondary
                                )
                            }
                            IconButton(onClick = { editingChild = child }) {
                                Icon(imageVector = Icons.Default.Edit, contentDescription = "Modifier",
                                    tint = Orange, modifier = Modifier.size(20.dp))
                            }
                            IconButton(onClick = { viewModel.deleteChild(child.id) }) {
                                Icon(imageVector = Icons.Default.Delete, contentDescription = "Supprimer",
                                    tint = Color.LightGray, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Bouton Communauté
        Card(
            onClick = onCommunityClick,
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = CardColor),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(imageVector = Icons.Default.Public, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(28.dp))
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Communauté", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Text("Découvrir les listes partagées", fontSize = 12.sp, color = TextSecondary)
                }
                Icon(imageVector = Icons.Default.ChevronRight, contentDescription = null, tint = Color.LightGray)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = CardColor)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Info, contentDescription = null, tint = Orange)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Version 1.0", fontWeight = FontWeight.Medium)
                }
            }
        }
    }

    if (showAddDialog) {
        ChildDialog(
            title = "Ajouter un enfant",
            onDismiss = { showAddDialog = false },
            onConfirm = { name, birthDate ->
                viewModel.addChild(name, birthDate)
                showAddDialog = false
            }
        )
    }

    editingChild?.let { child ->
        ChildDialog(
            title = "Modifier",
            initialName = child.name,
            initialBirthDate = child.birthDate,
            onDismiss = { editingChild = null },
            onConfirm = { name, birthDate ->
                viewModel.updateChild(child.copy(name = name, birthDate = birthDate))
                editingChild = null
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChildDialog(
    title: String,
    initialName: String = "",
    initialBirthDate: Long? = null,
    onDismiss: () -> Unit,
    onConfirm: (String, Long) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var showDatePicker by remember { mutableStateOf(false) }
    var birthDate by remember { mutableStateOf(initialBirthDate) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        icon = { Icon(imageVector = Icons.Default.ChildCare, contentDescription = null, tint = Orange) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Prénom") },
                    leadingIcon = { Icon(imageVector = Icons.Default.Person, contentDescription = null) },
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Orange, focusedContainerColor = CardColor, unfocusedContainerColor = CardColor
                    )
                )
                OutlinedTextField(
                    value = if (birthDate != null) formatDate(birthDate!!) else "",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Date de naissance") },
                    leadingIcon = { Icon(imageVector = Icons.Default.Cake, contentDescription = null, tint = Orange) },
                    trailingIcon = {
                        IconButton(onClick = { showDatePicker = true }) {
                            Icon(imageVector = Icons.Default.EditCalendar, contentDescription = "Choisir")
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Orange, focusedContainerColor = CardColor, unfocusedContainerColor = CardColor
                    )
                )
                if (birthDate != null) {
                    val ageLabel = formatChildAge(birthDate!!)
                    Surface(shape = RoundedCornerShape(10.dp), color = BlueSky.copy(alpha = 0.12f)) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(imageVector = Icons.Default.ChildCare, contentDescription = null,
                                tint = BlueSky, modifier = Modifier.size(16.dp))
                            Text(ageLabel, color = BlueSky, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val bd = birthDate
                    if (name.isNotBlank() && bd != null) onConfirm(name.trim(), bd)
                }
            ) { Text("OK", color = Orange, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        }
    )

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = birthDate,
            yearRange = 2000..currentYear()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    birthDate = datePickerState.selectedDateMillis
                    showDatePicker = false
                }) { Text("OK", color = Orange) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Annuler") }
            }
        ) {
            DatePicker(
                state = datePickerState,
                colors = DatePickerDefaults.colors(
                    selectedDayContainerColor = Orange,
                    todayDateBorderColor = Orange
                )
            )
        }
    }
}
