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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import data.Link
import data.LinkCategory
import viewmodel.LinkViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditLinkScreen(link: Link, viewModel: LinkViewModel, onBack: () -> Unit) {
    var title by remember { mutableStateOf(link.title) }
    var url by remember { mutableStateOf(link.url) }
    var description by remember { mutableStateOf(link.description) }
    var selectedCategory by remember { mutableStateOf(link.category) }
    var selectedFolderId by remember { mutableStateOf(link.folderId) }
    var selectedTags by remember { mutableStateOf(link.tags) }
    var tagInput by remember { mutableStateOf("") }
    var ageRange by remember { mutableStateOf(link.ageRange) }
    var location by remember { mutableStateOf(link.location) }
    var price by remember { mutableStateOf(link.price) }
    var eventDate by remember { mutableStateOf(link.eventDate) }
    var reminderEnabled by remember { mutableStateOf(link.reminderEnabled) }
    var showDatePicker by remember { mutableStateOf(false) }
    var rating by remember { mutableStateOf(link.rating) }
    var manualImageUrl by remember { mutableStateOf(link.imageUrl) }
    var ingredients by remember { mutableStateOf(link.ingredients) }
    val folders by viewModel.folders.collectAsState()

    val showDateFields = selectedCategory == LinkCategory.ACTIVITE || selectedCategory == LinkCategory.EVENEMENT

    Scaffold(
        containerColor = SurfaceColor,
        topBar = {
            TopAppBar(
                title = { Text("Modifier", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Retour")
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
                        leadingIcon = { Icon(imageVector = categoryIcon(cat), contentDescription = null, modifier = Modifier.size(16.dp)) },
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
                        leadingIcon = { Icon(imageVector = Icons.Default.Folder, contentDescription = null) },
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
                            leadingIcon = { Icon(imageVector = Icons.Default.FolderOff, contentDescription = null) })
                        folders.forEach { folder ->
                            DropdownMenuItem(text = { Text(folder.name) },
                                onClick = { selectedFolderId = folder.id; expanded = false },
                                leadingIcon = { Icon(imageVector = folderIconVector(folder), contentDescription = null) })
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
            
            // Lieu (sauf pour les recettes)
            if (selectedCategory != LinkCategory.RECETTE) {
                LocationAutocompleteField(value = location, onValueChange = { location = it })
            }

            // Note étoiles
            Text("Note", fontWeight = FontWeight.SemiBold, color = TextSecondary)
            StarRating(rating = rating, onRatingChange = { rating = it }, starSize = 32)

            // Image
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
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                val datePickerState = rememberDatePickerState(initialSelectedDateMillis = eventDate)
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
                    DatePicker(state = datePickerState, colors = DatePickerDefaults.colors(
                        selectedDayContainerColor = Orange, todayDateBorderColor = Orange
                    ))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        viewModel.updateLink(link.copy(
                            title = title.trim(), url = url.trim(), description = description.trim(),
                            category = selectedCategory, folderId = selectedFolderId,
                            tags = selectedTags, ageRange = ageRange.trim(),
                            location = location.trim(), price = price.trim(),
                            eventDate = eventDate, reminderEnabled = reminderEnabled,
                            rating = rating, imageUrl = manualImageUrl,
                            ingredients = ingredients
                        ))
                        onBack()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Orange)
            ) {
                Icon(imageVector = Icons.Default.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Enregistrer", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
