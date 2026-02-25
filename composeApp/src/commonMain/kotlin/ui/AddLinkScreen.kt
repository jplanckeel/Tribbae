package ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import data.LinkCategory
import viewmodel.LinkViewModel

/** Formate un timestamp en date lisible JJ/MM/AAAA */
fun formatDate(millis: Long): String {
    val days = millis / 86400000L
    // Calcul simple de la date à partir de l'epoch
    val totalDays = days + 719468 // jours depuis 0000-03-01
    val era = (if (totalDays >= 0) totalDays else totalDays - 146096) / 146097
    val doe = totalDays - era * 146097
    val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
    val y = yoe + era * 400
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    val d = doy - (153 * mp + 2) / 5 + 1
    val m = mp + (if (mp < 10) 3 else -9)
    val year = y + (if (m <= 2) 1 else 0)
    return "${d.toString().padStart(2, '0')}/${m.toString().padStart(2, '0')}/$year"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddLinkScreen(viewModel: LinkViewModel, onBack: () -> Unit, initialUrl: String? = null) {
    var title by remember { mutableStateOf("") }
    var url by remember { mutableStateOf(initialUrl ?: "") }
    var description by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(guessCategory(initialUrl)) }
    var selectedFolderId by remember { mutableStateOf<String?>(null) }
    var selectedTags by remember { mutableStateOf<List<String>>(emptyList()) }
    var tagInput by remember { mutableStateOf("") }
    var ageRange by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var eventDate by remember { mutableStateOf<Long?>(null) }
    var reminderEnabled by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var rating by remember { mutableStateOf(0) }
    var manualImageUrl by remember { mutableStateOf("") }
    var ingredients by remember { mutableStateOf<List<String>>(emptyList()) }
    val folders by viewModel.folders.collectAsState()

    val showDateFields = selectedCategory == LinkCategory.ACTIVITE || selectedCategory == LinkCategory.EVENEMENT

    Scaffold(
        containerColor = SurfaceColor,
        topBar = {
            TopAppBar(
                title = { Text("Nouvelle idée", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Retour")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Orange,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding).fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            StyledField(title, { title = it }, "Titre", Icons.Default.Title)
            StyledField(url, { url = it }, "URL", Icons.Default.Link)
            StyledField(description, { description = it }, "Description", Icons.Default.Notes, minLines = 3)

            Text("Catégorie", fontWeight = FontWeight.SemiBold, color = TextSecondary)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LinkCategory.entries.forEach { cat ->
                    val catColor = CategoryColors[cat.name] ?: Orange
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { selectedCategory = cat },
                        label = { Text(cat.label) },
                        leadingIcon = { Icon(categoryIcon(cat), contentDescription = null, modifier = Modifier.size(16.dp)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = catColor,
                            selectedLabelColor = Color.White,
                            selectedLeadingIconColor = Color.White
                        )
                    )
                }
            }

            if (folders.isNotEmpty()) {
                Text("Liste", fontWeight = FontWeight.SemiBold, color = TextSecondary)
                var expanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(
                        value = folders.find { it.id == selectedFolderId }?.name ?: "Aucun",
                        onValueChange = {}, readOnly = true,
                        leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Orange, focusedContainerColor = CardColor, unfocusedContainerColor = CardColor
                        )
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        DropdownMenuItem(text = { Text("Aucun") },
                            onClick = { selectedFolderId = null; expanded = false },
                            leadingIcon = { Icon(Icons.Default.FolderOff, contentDescription = null) })
                        folders.forEach { folder ->
                            DropdownMenuItem(text = { Text(folder.name) },
                                onClick = { selectedFolderId = folder.id; expanded = false },
                                leadingIcon = { Icon(folderIconVector(folder), contentDescription = null) })
                        }
                    }
                }
            }

            TagInputField(
                selectedTags = selectedTags,
                onTagAdded = { selectedTags = selectedTags + it },
                onTagRemoved = { selectedTags = selectedTags - it },
                suggestions = viewModel.searchTags(tagInput),
                input = tagInput,
                onInputChange = { tagInput = it }
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AgeField(value = ageRange, onValueChange = { ageRange = it }, modifier = Modifier.weight(1f))
                StyledField(price, { price = it }, "Prix", Icons.Default.Euro, modifier = Modifier.weight(1f))
            }
            LocationAutocompleteField(value = location, onValueChange = { location = it })

            // Note étoiles
            Text("Note", fontWeight = FontWeight.SemiBold, color = TextSecondary)
            StarRating(rating = rating, onRatingChange = { rating = it }, starSize = 32)

            // Image manuelle
            ImagePickerSection(
                imageUrl = manualImageUrl,
                viewModel = viewModel,
                onImageSelected = { manualImageUrl = it }
            )

            // Ingrédients (seulement pour les recettes)
            if (selectedCategory == LinkCategory.RECETTE) {
                IngredientsField(
                    ingredients = ingredients,
                    onIngredientsChange = { ingredients = it }
                )
            }

            // Date d'événement (seulement pour Activité et Événement)
            if (showDateFields) {
                Text("Date de l'événement", fontWeight = FontWeight.SemiBold, color = TextSecondary)
                OutlinedTextField(
                    value = if (eventDate != null) formatDate(eventDate!!) else "",
                    onValueChange = {},
                    readOnly = true,
                    placeholder = { Text("Sélectionner une date") },
                    leadingIcon = { Icon(imageVector = Icons.Default.CalendarMonth, contentDescription = null, tint = Orange) },
                    trailingIcon = {
                        Row {
                            if (eventDate != null) {
                                IconButton(onClick = { eventDate = null; reminderEnabled = false }) {
                                    Icon(imageVector = Icons.Default.Clear, contentDescription = "Effacer")
                                }
                            }
                            IconButton(onClick = { showDatePicker = true }) {
                                Icon(imageVector = Icons.Default.EditCalendar, contentDescription = "Choisir")
                            }
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Orange, focusedContainerColor = CardColor, unfocusedContainerColor = CardColor
                    )
                )

                if (eventDate != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Notifications, contentDescription = null, tint = Orange, modifier = Modifier.size(20.dp))
                            Text("Rappel (1 jour avant)", fontSize = 14.sp, color = TextPrimary)
                        }
                        Switch(
                            checked = reminderEnabled,
                            onCheckedChange = { reminderEnabled = it },
                            colors = SwitchDefaults.colors(checkedTrackColor = Orange)
                        )
                    }
                }
            }

            if (showDatePicker) {
                val datePickerState = rememberDatePickerState()
                DatePickerDialog(
                    onDismissRequest = { showDatePicker = false },
                    confirmButton = {
                        TextButton(onClick = {
                            eventDate = datePickerState.selectedDateMillis
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

            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        viewModel.addLink(title.trim(), url.trim(), description.trim(),
                            selectedCategory, selectedFolderId, selectedTags,
                            ageRange.trim(), location.trim(), price.trim(),
                            eventDate, reminderEnabled, rating, manualImageUrl, ingredients)
                        onBack()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Orange)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Ajouter", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagInputField(
    selectedTags: List<String>, onTagAdded: (String) -> Unit, onTagRemoved: (String) -> Unit,
    suggestions: List<String>, input: String, onInputChange: (String) -> Unit
) {
    val filtered = suggestions.filter { it !in selectedTags }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Tags", fontWeight = FontWeight.SemiBold, color = TextSecondary)
        if (selectedTags.isNotEmpty()) {
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                selectedTags.forEach { tag ->
                    InputChip(selected = true, onClick = { onTagRemoved(tag) },
                        label = { Text("#$tag", fontSize = 13.sp) },
                        trailingIcon = { Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(14.dp)) },
                        colors = InputChipDefaults.inputChipColors(
                            selectedContainerColor = Orange.copy(alpha = 0.15f), selectedLabelColor = Orange))
                }
            }
        }
        OutlinedTextField(
            value = input, onValueChange = onInputChange,
            placeholder = { Text("Ajouter un tag...") },
            leadingIcon = { Icon(Icons.Default.Tag, contentDescription = null, tint = Orange) },
            trailingIcon = {
                if (input.isNotBlank()) IconButton(onClick = { onTagAdded(input.trim().lowercase()); onInputChange("") }) {
                    Icon(Icons.Default.Add, contentDescription = null)
                }
            },
            shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(), singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Orange, focusedContainerColor = CardColor, unfocusedContainerColor = CardColor)
        )
        if (input.isNotBlank() && filtered.isNotEmpty()) {
            Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = CardColor),
                elevation = CardDefaults.cardElevation(4.dp)) {
                Column {
                    filtered.take(5).forEach { s ->
                        TextButton(onClick = { onTagAdded(s); onInputChange("") }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Tag, contentDescription = null, modifier = Modifier.size(16.dp), tint = Orange)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(s, color = Orange, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StyledField(
    value: String, onValueChange: (String) -> Unit, label: String, icon: ImageVector,
    modifier: Modifier = Modifier.fillMaxWidth(), minLines: Int = 1
) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange,
        label = { Text(label) }, leadingIcon = { Icon(icon, contentDescription = null) },
        shape = RoundedCornerShape(16.dp), modifier = modifier, minLines = minLines,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Orange, focusedContainerColor = CardColor, unfocusedContainerColor = CardColor)
    )
}


private fun guessCategory(url: String?): LinkCategory {
    if (url == null) return LinkCategory.IDEE
    val lower = url.lowercase()
    return when {
        lower.contains("recett") || lower.contains("cuisine") || lower.contains("marmiton")
            || lower.contains("recipe") || lower.contains("cook") -> LinkCategory.RECETTE
        lower.contains("event") || lower.contains("meetup") || lower.contains("billeterie")
            || lower.contains("ticket") -> LinkCategory.EVENEMENT
        lower.contains("sport") || lower.contains("rando") || lower.contains("activit")
            || lower.contains("loisir") -> LinkCategory.ACTIVITE
        lower.contains("cadeau") || lower.contains("gift") || lower.contains("amazon")
            || lower.contains("etsy") || lower.contains("fnac") -> LinkCategory.CADEAU
        else -> LinkCategory.IDEE
    }
}
