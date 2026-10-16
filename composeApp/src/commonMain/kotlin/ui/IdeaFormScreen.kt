package ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import data.Link
import data.LinkCategory
import kotlinx.coroutines.launch
import ui.campfire.*
import ui.components.TagInputWithAutocomplete
import viewmodel.LinkViewModel
import java.util.Calendar

/**
 * Formulaire d'idée « feu de camp », pour la création comme pour l'édition.
 * Remplit titre / description / image depuis le lien (aperçu côté serveur).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun IdeaFormScreen(
    viewModel: LinkViewModel,
    onBack: () -> Unit,
    onSaved: () -> Unit = onBack,
    existing: Link? = null,
    initialUrl: String? = null,
    initialCategory: LinkCategory? = null,
    initialFolderId: String? = null,
) {
    val folders by viewModel.folders.collectAsState()
    val allLinks by viewModel.repository.links.collectAsState()
    val scope = rememberCoroutineScope()

    var category by remember { mutableStateOf(existing?.category ?: initialCategory ?: LinkCategory.IDEE) }
    var url by remember { mutableStateOf(existing?.url ?: initialUrl.orEmpty()) }
    var title by remember { mutableStateOf(existing?.title.orEmpty()) }
    var description by remember { mutableStateOf(existing?.description.orEmpty()) }
    var imageUrl by remember { mutableStateOf(existing?.imageUrl.orEmpty()) }
    var ingredients by remember { mutableStateOf(existing?.ingredients?.joinToString("\n").orEmpty()) }
    var folderId by remember { mutableStateOf(existing?.folderId ?: initialFolderId) }
    var tags by remember { mutableStateOf(existing?.tags ?: emptyList()) }
    var ageRange by remember { mutableStateOf(existing?.ageRange.orEmpty()) }
    var price by remember { mutableStateOf(existing?.price.orEmpty()) }
    var location by remember { mutableStateOf(existing?.location.orEmpty()) }
    var eventDate by remember { mutableStateOf(existing?.let { eventMs(it) }) }
    var reminder by remember { mutableStateOf(existing?.reminderEnabled ?: false) }
    var rating by remember { mutableStateOf(existing?.rating ?: 0) }
    var isPublic by remember { mutableStateOf(existing?.visibility == "public") }
    var more by remember {
        mutableStateOf(existing != null && (ageRange.isNotBlank() || price.isNotBlank() || location.isNotBlank() || rating > 0))
    }
    var previewing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    var showFolders by remember { mutableStateOf(false) }

    val cat = categoryUi(category)
    val knownTags = remember(allLinks) { allLinks.flatMap { it.tags }.distinct() }
    val isDated = category == LinkCategory.EVENEMENT || category == LinkCategory.ACTIVITE || eventDate != null

    fun fetchPreview() {
        if (url.isBlank() || previewing) return
        previewing = true
        scope.launch {
            viewModel.previewLink(url)?.let { p ->
                if (title.isBlank()) title = p.title
                if (description.isBlank()) description = p.description
                if (imageUrl.isBlank()) imageUrl = p.image
            }
            previewing = false
        }
    }

    // Lien partagé depuis une autre application : on tente l'aperçu tout de suite
    LaunchedEffect(Unit) { if (existing == null && url.isNotBlank() && title.isBlank()) fetchPreview() }

    fun submit() {
        if (title.isBlank()) { error = "Donnez un titre à votre idée."; return }
        val ing = if (category == LinkCategory.RECETTE) ingredients.lines().map { it.trim() }.filter { it.isNotEmpty() } else existing?.ingredients ?: emptyList()
        val visibility = if (isPublic) "public" else "private"
        val remind = reminder && eventDate != null
        if (existing != null) {
            viewModel.updateLink(
                existing.copy(
                    title = title.trim(), url = url.trim(), description = description.trim(), category = category,
                    folderId = folderId, tags = tags, ageRange = ageRange.trim(), location = location.trim(), price = price.trim(),
                    imageUrl = imageUrl, eventDate = eventDate, reminderEnabled = remind, rating = rating,
                    ingredients = ing, visibility = visibility
                )
            )
        } else {
            viewModel.addLink(
                title = title.trim(), url = url.trim(), description = description.trim(), category = category,
                folderId = folderId, tags = tags, ageRange = ageRange.trim(), location = location.trim(), price = price.trim(),
                eventDate = eventDate, reminderEnabled = remind, rating = rating, imageUrl = imageUrl,
                ingredients = ing, visibility = visibility
            )
        }
        onSaved()
    }

    Box(Modifier.fillMaxSize().background(Sable)) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(if (existing != null) "Modifier l'idée" else "Nouvelle ${cat.label.lowercase()}", style = display(26.sp))
                CircleIconButton(Icons.Outlined.Close, "Fermer", onBack, background = Sable2)
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Catégorie", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextSec)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CategoryOrder.forEach { c ->
                        val on = c.category == category
                        Row(
                            Modifier.height(38.dp).clip(CircleShape).background(if (on) Ecorce else c.bg)
                                .clickable(role = Role.RadioButton) { category = c.category }.padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(c.icon, null, tint = if (on) Sable else c.tint, modifier = Modifier.size(16.dp))
                            Text(c.label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (on) Sable else c.ink)
                        }
                    }
                }
            }

            CampField(
                "Lien (facultatif)", url, { url = it },
                placeholder = "https://…", icon = Icons.Outlined.Link,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                onFocusLost = { if (title.isBlank()) fetchPreview() },
                hint = if (previewing) "Récupération du titre et de l'image…" else null
            )

            ImagePickerSection(imageUrl = imageUrl, viewModel = viewModel, onImageSelected = { imageUrl = it })

            CampField("Titre", title, { title = it; error = null }, placeholder = "Fête de la courge à la ferme")
            CampField("Description", description, { description = it }, placeholder = "Ce qu'il faut savoir, ce qu'il faut emporter…", singleLine = false, minLines = 3, maxLength = 5000)

            if (category == LinkCategory.RECETTE) {
                CampField("Ingrédients (un par ligne)", ingredients, { ingredients = it }, placeholder = "250 g de farine\n2 œufs\n50 cl de lait", singleLine = false, minLines = 4, maxLength = 5000)
            }

            // Dossier
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Dossier", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextSec)
                val f = folders.find { it.id == folderId }
                Row(
                    Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(18.dp)).background(Color.White)
                        .border(ThinBorder, RoundedCornerShape(18.dp)).clickable { showFolders = true }.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(if (f != null) folderIconVector(f) else Icons.Outlined.BookmarkBorder, null, tint = f?.let { folderSolid(it.color) } ?: TextMuted, modifier = Modifier.size(20.dp))
                    Text(f?.name ?: "Non rangée", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Ecorce, modifier = Modifier.weight(1f))
                    Icon(Icons.Outlined.ExpandMore, null, tint = TextMuted)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Tags", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextSec)
                TagInputWithAutocomplete(tagsList = tags, onTagsChanged = { tags = it }, allAvailableTags = knownTags)
            }

            Row(
                Modifier.clip(RoundedCornerShape(10.dp)).clickable { more = !more }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(if (more) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null, tint = Terracotta)
                Text(if (more) "Moins de détails" else "Âge, prix, lieu, note…", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Terracotta)
            }

            if (more) {
                CampField("Âge", ageRange, { ageRange = it }, placeholder = "3–10 ans", icon = Icons.Outlined.Person, maxLength = 200)
                CampField("Prix", price, { price = it }, placeholder = "Gratuit, 6 € / enfant…", icon = Icons.Outlined.Euro, maxLength = 200)
                CampField("Lieu", location, { location = it }, placeholder = "Ferme des Saules", icon = Icons.Outlined.Place, maxLength = 200)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Note de la tribu", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextSec)
                    Row {
                        (1..5).forEach { n ->
                            Icon(
                                if (rating >= n) Icons.Filled.Star else Icons.Outlined.StarBorder, null, tint = Braise,
                                modifier = Modifier.size(40.dp).clip(CircleShape).clickable { rating = if (rating == n) 0 else n }.padding(8.dp)
                                    .semantics { contentDescription = "$n sur 5" }
                            )
                        }
                    }
                }
            }

            if (isDated) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Date", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextSec)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DateChip(
                            Icons.Outlined.CalendarMonth,
                            eventDate?.let { formatShortDate(it) } ?: "Choisir une date",
                            Modifier.weight(1f)
                        ) { showDate = true }
                        if (eventDate != null) {
                            DateChip(Icons.Outlined.Schedule, formatTime(eventDate!!).ifEmpty { "Heure" }) { showTime = true }
                            CircleIconButton(Icons.Outlined.Close, "Retirer la date", { eventDate = null; reminder = false }, background = Sable2, size = 52.dp)
                        }
                    }
                }
                if (eventDate != null) {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Soleil).padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Outlined.NotificationsNone, null, tint = Ecorce, modifier = Modifier.size(20.dp))
                        Text("Me le rappeler la veille", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Ecorce, modifier = Modifier.weight(1f))
                        CampSwitch(reminder, { reminder = it }, "Rappel la veille")
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth().card(RoundedCornerShape(18.dp), 4.dp).padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(Icons.Outlined.Public, null, tint = Color(0xFF2F7393), modifier = Modifier.size(20.dp))
                Column(Modifier.weight(1f)) {
                    Text("Partager avec la communauté", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Ecorce)
                    Text("Visible dans l'Explorer par toutes les tribus", fontSize = 12.sp, color = TextSec)
                }
                CampSwitch(isPublic, { isPublic = it }, "Partager avec la communauté")
            }

            if (error != null) {
                Text(
                    error!!, color = Color(0xFF9A3420), fontSize = 14.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFFF8DCD3)).padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }
        }

        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Sable).navigationBarsPadding().imePadding()
                .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CampButton("Annuler", onBack, kind = ButtonKind.Ghost)
            CampButton(if (existing != null) "Enregistrer" else "Ajouter à la tribu", ::submit, Modifier.weight(1f))
        }
    }

    if (showFolders) {
        ModalBottomSheet(onDismissRequest = { showFolders = false }, containerColor = Sable, dragHandle = { SheetHandle() }) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Dossier", style = display(24.sp), modifier = Modifier.padding(bottom = 8.dp))
                MenuRow(Icons.Outlined.BookmarkBorder, "Non rangée") { folderId = null; showFolders = false }
                folders.forEach { f ->
                    MenuRow(folderIconVector(f), f.name, tint = folderSolid(f.color)) { folderId = f.id; showFolders = false }
                }
            }
        }
    }

    if (showDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = eventDate ?: System.currentTimeMillis())
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { utc ->
                        // Le sélecteur renvoie minuit UTC : on garde le jour, en heure locale, avec l'heure déjà choisie
                        val day = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply { timeInMillis = utc }
                        val prev = Calendar.getInstance().apply { timeInMillis = eventDate ?: 0 }
                        eventDate = Calendar.getInstance().apply {
                            set(day.get(Calendar.YEAR), day.get(Calendar.MONTH), day.get(Calendar.DAY_OF_MONTH),
                                if (eventDate != null) prev.get(Calendar.HOUR_OF_DAY) else 0,
                                if (eventDate != null) prev.get(Calendar.MINUTE) else 0, 0)
                            set(Calendar.MILLISECOND, 0)
                        }.timeInMillis
                    }
                    showDate = false
                }) { Text("Valider", color = Terracotta, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("Annuler", color = Ecorce) } }
        ) { DatePicker(state) }
    }

    if (showTime && eventDate != null) {
        val c = Calendar.getInstance().apply { timeInMillis = eventDate!! }
        val state = rememberTimePickerState(c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), is24Hour = true)
        AlertDialog(
            onDismissRequest = { showTime = false },
            containerColor = Sable,
            title = { Text("Heure", style = display(20.sp, FontWeight.Bold)) },
            text = { TimePicker(state) },
            confirmButton = {
                TextButton(onClick = {
                    eventDate = c.apply { set(Calendar.HOUR_OF_DAY, state.hour); set(Calendar.MINUTE, state.minute) }.timeInMillis
                    showTime = false
                }) { Text("Valider", color = Terracotta, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showTime = false }) { Text("Annuler", color = Ecorce) } }
        )
    }
}

@Composable
private fun DateChip(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier.height(52.dp).clip(RoundedCornerShape(18.dp)).background(Color.White).border(ThinBorder, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(icon, null, tint = TextMuted, modifier = Modifier.size(19.dp))
        Text(text, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Ecorce)
    }
}
