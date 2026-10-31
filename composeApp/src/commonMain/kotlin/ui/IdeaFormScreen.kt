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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import data.Link
import data.LinkCategory
import data.LinkPreview
import data.extractUrl
import kotlinx.coroutines.launch
import ui.campfire.*
import ui.components.TagInputWithAutocomplete
import viewmodel.LinkViewModel
import java.util.Calendar

private enum class PreviewStatus { Idle, Loading, Done, Failed }

/** Valeurs remplies par le dernier aperçu : on peut les remplacer si le lien change. */
private class AutoFilled {
    var title = ""; var description = ""; var imageUrl = ""; var price = ""
    var location = ""; var eventDate: Long? = null; var ingredients = ""
}

// Remplace la valeur si elle est vide ou si elle venait de l'aperçu précédent
private fun fill(current: String, previousAuto: String, next: String): String =
    if (next.isNotBlank() && (current.isBlank() || current == previousAuto)) next else current

// Resynchronisation : ce que la page donne remplace la valeur, le reste est gardé
private fun replace(current: String, @Suppress("UNUSED_PARAMETER") previousAuto: String, next: String): String =
    next.ifBlank { current }

/**
 * Formulaire d'idée « feu de camp », pour la création comme pour l'édition.
 * Remplit titre / description / images (et catégorie, ingrédients, prix, lieu,
 * date quand la page les décrit) depuis le lien, via l'aperçu côté serveur.
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
    initialTitle: String? = null,
    initialIngredients: List<String> = emptyList(),
) {
    val folders by viewModel.folders.collectAsState()
    val kids by viewModel.children.collectAsState()
    val allLinks by viewModel.repository.links.collectAsState()
    val scope = rememberCoroutineScope()

    var category by remember { mutableStateOf(existing?.category ?: initialCategory ?: LinkCategory.IDEE) }
    var url by remember { mutableStateOf(existing?.url ?: initialUrl.orEmpty()) }
    var title by remember { mutableStateOf(existing?.title ?: initialTitle.orEmpty()) }
    var description by remember { mutableStateOf(existing?.description.orEmpty()) }
    var imageUrl by remember { mutableStateOf(existing?.imageUrl.orEmpty()) }
    var ingredients by remember { mutableStateOf((existing?.ingredients ?: initialIngredients).joinToString("\n")) }
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
        mutableStateOf(existing != null && (price.isNotBlank() || location.isNotBlank() || rating > 0))
    }
    var previewStatus by remember { mutableStateOf(PreviewStatus.Idle) }
    var previewUrl by remember { mutableStateOf<String?>(null) }
    var previewImages by remember { mutableStateOf(emptyList<String>()) }
    var previewSite by remember { mutableStateOf("") }
    // Resynchronisation d'une idée existante : les infos de la page remplacent les siennes
    var previewResync by remember { mutableStateOf(false) }
    // La catégorie suggérée par la page ne remplace pas un choix explicite
    var categoryChosen by remember { mutableStateOf(existing != null || (initialCategory != null && initialCategory != LinkCategory.IDEE)) }
    val autoFilled = remember { AutoFilled() }
    var requestId by remember { mutableStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    var showFolders by remember { mutableStateOf(false) }

    val cat = categoryUi(category)
    val knownTags = remember(allLinks) { allLinks.flatMap { it.tags }.distinct() }
    val isDated = category == LinkCategory.EVENEMENT || category == LinkCategory.ACTIVITE || eventDate != null

    fun applyPreview(p: LinkPreview, forUrl: String, resync: Boolean = false) {
        val prev = autoFilled
        val ingredientsText = p.ingredients.joinToString("\n")
        val fill: (String, String, String) -> String = if (resync) ::replace else ::fill
        title = fill(title, prev.title, p.title)
        description = fill(description, prev.description, p.description)
        imageUrl = fill(imageUrl, prev.imageUrl, p.image)
        price = fill(price, prev.price, p.price)
        location = fill(location, prev.location, p.location)
        ingredients = fill(ingredients, prev.ingredients, ingredientsText)
        val date = p.eventDate.takeIf { it > 0 }
        if (date != null && (resync || eventDate == null || eventDate == prev.eventDate)) eventDate = date
        if (!categoryChosen) p.suggestedCategory?.let { category = it }
        prev.title = p.title; prev.description = p.description; prev.imageUrl = p.image; prev.price = p.price
        prev.location = p.location; prev.eventDate = date; prev.ingredients = ingredientsText
        if (p.price.isNotBlank() || p.location.isNotBlank()) more = true
        previewImages = p.allImages
        previewSite = p.siteName.ifBlank { runCatching { java.net.URI(forUrl).host.removePrefix("www.") }.getOrDefault("") }
        previewStatus = PreviewStatus.Done
    }

    fun fetchPreview(force: Boolean = false, resync: Boolean = false) {
        val target = extractUrl(url) ?: return
        if (target != url) url = target
        if (!force && previewUrl == target && previewStatus != PreviewStatus.Failed) return
        previewUrl = target
        previewResync = resync
        previewStatus = PreviewStatus.Loading
        previewImages = emptyList()
        // Seule la réponse de la dernière requête compte (l'adresse a pu changer)
        val id = ++requestId
        scope.launch {
            val p = viewModel.previewLink(target)
            if (id != requestId) return@launch
            if (p != null) applyPreview(p, target, resync) else previewStatus = PreviewStatus.Failed
        }
    }

    // Lien partagé depuis une autre application ou collé dans « Ajouter » : aperçu tout de suite
    LaunchedEffect(Unit) { if (existing == null && url.isNotBlank()) fetchPreview() }

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

    // Le clavier réduit toute la page (imePadding) : la zone qui défile rétrécit au-dessus
    // de la barre « Annuler / Ajouter », et le champ en cours de saisie (tags…) reste visible.
    Column(Modifier.fillMaxSize().background(Sable).imePadding()) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).statusBarsPadding().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
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
                                .clickable(role = Role.RadioButton) { category = c.category; categoryChosen = true }.padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(c.icon, null, tint = if (on) Sable else c.tint, modifier = Modifier.size(16.dp))
                            Text(c.label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (on) Sable else c.ink)
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                CampField(
                    "Lien (facultatif)", url,
                    {
                        // Un lien collé (même entouré de texte) est lu aussitôt
                        val pasted = it.length - url.length > 8
                        url = it
                        if (pasted && extractUrl(it) != null) fetchPreview()
                    },
                    placeholder = "https://…", icon = Icons.Outlined.Link,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    onFocusLost = { fetchPreview() }
                )
                PreviewStatusRow(previewStatus, previewSite, previewResync) { fetchPreview(force = true, resync = previewResync) }
                if (existing != null && extractUrl(url) != null && previewStatus != PreviewStatus.Loading) {
                    // Relit la page et remplace les infos de l'idée par celles du lien
                    Row(
                        Modifier.clip(RoundedCornerShape(8.dp)).clickable(role = Role.Button) { fetchPreview(force = true, resync = true) }
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Outlined.Refresh, null, tint = Terracotta, modifier = Modifier.size(16.dp))
                        Text("Resynchroniser depuis le lien", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Terracotta)
                    }
                }
            }

            ImagePickerSection(imageUrl = imageUrl, viewModel = viewModel, onImageSelected = { imageUrl = it })
            ImageCandidates(
                candidates = previewImages,
                selected = imageUrl,
                onSelect = { imageUrl = it },
                onBroken = { broken ->
                    // Image morte ou minuscule : on l'écarte, et on passe à la suivante si elle était choisie
                    val rest = previewImages - broken
                    previewImages = rest
                    if (imageUrl == broken) imageUrl = rest.firstOrNull().orEmpty()
                }
            )

            CampField(
                "Titre", title, { title = it; error = null },
                placeholder = if (previewStatus == PreviewStatus.Loading) "Récupération du titre…" else "Fête de la courge à la ferme"
            )
            CampField("Description", description, { description = it }, placeholder = "Ce qu'il faut savoir, ce qu'il faut emporter…", singleLine = false, minLines = 3, maxLength = 5000)

            if (category == LinkCategory.RECETTE) {
                CampField("Ingrédients (un par ligne)", ingredients, { ingredients = it }, placeholder = "250 g de farine\n2 œufs\n50 cl de lait", singleLine = false, minLines = 4, maxLength = 5000)
            }

            // Dossier
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Dossier", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextSec)
                val f = folders.find { it.id == folderId }
                Row(
                    Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(18.dp)).background(CardColor)
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
                TagInputWithAutocomplete(tagsList = tags, onTagsChanged = { tags = it }, allAvailableTags = knownTags, showLabel = false)
            }

            AgePicker(ageRange, { ageRange = it }, kids)

            Row(
                Modifier.clip(RoundedCornerShape(10.dp)).clickable { more = !more }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(if (more) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null, tint = Terracotta)
                Text(if (more) "Moins de détails" else "Prix, lieu, note…", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Terracotta)
            }

            if (more) {
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
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(SoleilPanel).padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Outlined.NotificationsNone, null, tint = Ecorce, modifier = Modifier.size(20.dp))
                        Text("Me le rappeler la veille", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Ecorce, modifier = Modifier.weight(1f))
                        CampSwitch(reminder, { reminder = it }, "Rappel la veille")
                    }
                }
            }

            // Idée d'un autre membre de la tribu : modifiable, mais seul son auteur la publie
            val authorIsOther = existing != null && existing.ownerId.isNotBlank() && existing.ownerId != viewModel.currentUserId()
            if (authorIsOther) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(CardColor).padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Outlined.Lock, null, tint = TextSec, modifier = Modifier.size(15.dp).padding(top = 1.dp))
                    Text(
                        "Idée de ${existing!!.ownerDisplayName.ifBlank { "un membre de la tribu" }} : seul son auteur peut la partager avec la communauté.",
                        fontSize = 12.sp, color = TextSec
                    )
                }
            } else {
                Row(
                    Modifier.fillMaxWidth().card(RoundedCornerShape(18.dp), 4.dp).padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val canPublish by viewModel.emailVerified.collectAsState()
                    Icon(Icons.Outlined.Public, null, tint = Color(0xFF2F7393), modifier = Modifier.size(20.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Partager avec la communauté", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Ecorce)
                        Text("Visible dans l'Explorer par toutes les tribus", fontSize = 12.sp, color = TextSec)
                        if (!canPublish) PublishLockedNote(Modifier.padding(top = 4.dp))
                    }
                    // Publier attend la confirmation de l'adresse ; dépublier reste toujours possible
                    CampSwitch(isPublic, { if (!it || canPublish) isPublic = it }, "Partager avec la communauté")
                }
            }
            // Dans son propre dossier public, une idée est visible de tous même sans être partagée
            // à la communauté (les idées d'un collaborateur, elles, restent réservées aux membres).
            val chosenFolder = folders.find { it.id == folderId }
            PublicFolderWaitingNote(viewModel, chosenFolder, Modifier.padding(horizontal = 4.dp))
            val me = viewModel.currentUserId()
            val inMyPublicFolder = chosenFolder != null && chosenFolder.visibility.uppercase().contains("PUBLIC") &&
                (me == null || chosenFolder.collaborators.none { it.userId == me })
            if (inMyPublicFolder && !isPublic) {
                Row(Modifier.padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.Visibility, null, tint = TextSec, modifier = Modifier.size(15.dp).padding(top = 1.dp))
                    Text(
                        "Le dossier « ${chosenFolder!!.name} » est public : cette idée y sera visible par tous ses visiteurs.",
                        fontSize = 12.sp, color = TextSec
                    )
                }
            }

            if (error != null) {
                Text(
                    error!!, color = CoralInk, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(CoralBg).padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }
        }

        Row(
            Modifier.fillMaxWidth().background(Sable).navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CampButton("Annuler", onBack, kind = ButtonKind.Ghost)
            CampButton(if (existing != null) "Enregistrer" else "Ajouter à la tribu", ::submit, Modifier.weight(1f))
        }
    }

    if (showFolders) {
        ModalBottomSheet(onDismissRequest = { showFolders = false }, containerColor = SheetColor, dragHandle = { SheetHandle() }) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Dossier", style = display(24.sp), modifier = Modifier.padding(bottom = 8.dp))
                NewFolderRow(onCreate = { name ->
                    viewModel.createFolderQuick(name) { id -> folderId = id; showFolders = false }
                })
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
            containerColor = SheetColor,
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

/** « Nouveau dossier » : se déplie en un champ pour nommer et créer le dossier sans quitter l'idée. */
@Composable
private fun NewFolderRow(onCreate: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var creating by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(open) { if (open) runCatching { focus.requestFocus() } }
    if (!open) {
        Row(
            Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(18.dp)).border(ThinBorder, RoundedCornerShape(18.dp))
                .clickable(role = Role.Button) { open = true }.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Outlined.CreateNewFolder, null, tint = Terracotta, modifier = Modifier.size(22.dp))
            Text("Nouveau dossier", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Terracotta)
        }
        return
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(CardColor).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        CampField(
            "Nom du nouveau dossier", name, { name = it }, Modifier.focusRequester(focus), placeholder = "Vacances d'été, Noël…", maxLength = 80,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done)
        )
        Text("Privé : visible seulement par votre tribu.", fontSize = 12.sp, color = TextSec)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CampButton("Annuler", { open = false; name = "" }, kind = ButtonKind.Ghost, height = 44.dp)
            CampButton(
                if (creating) "Création…" else "Créer et choisir",
                { creating = true; onCreate(name) },
                Modifier.weight(1f),
                enabled = name.isNotBlank() && !creating,
                height = 44.dp
            )
        }
    }
}

@Composable
private fun DateChip(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier.height(52.dp).clip(RoundedCornerShape(18.dp)).background(CardColor).border(ThinBorder, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(icon, null, tint = TextMuted, modifier = Modifier.size(19.dp))
        Text(text, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Ecorce)
    }
}

/** État de la lecture du lien, sous le champ « Lien ». */
@Composable
private fun PreviewStatusRow(status: PreviewStatus, site: String, resync: Boolean, onRetry: () -> Unit) {
    when (status) {
        PreviewStatus.Loading -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CircularProgressIndicator(Modifier.size(14.dp), color = Terracotta, strokeWidth = 2.dp)
            Text(if (resync) "Resynchronisation depuis la page…" else "Lecture de la page…", fontSize = 12.sp, color = TextSec)
        }
        PreviewStatus.Failed -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Impossible de lire cette page : complétez à la main.", fontSize = 12.sp, color = TextSec, modifier = Modifier.weight(1f, fill = false))
            Text(
                "Réessayer", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Terracotta,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onRetry).padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }
        PreviewStatus.Done -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Outlined.Check, null, tint = Color(0xFF2F7393), modifier = Modifier.size(14.dp))
            val from = if (site.isNotBlank()) " depuis $site" else ""
            Text(
                if (resync) "Infos mises à jour$from : vérifiez puis enregistrez" else "Infos récupérées$from",
                fontSize = 12.sp, color = TextSec
            )
        }
        PreviewStatus.Idle -> Unit
    }
}

/**
 * Autres images trouvées sur la page, à choisir d'une touche. Les images qui
 * ne chargent pas ou trop petites (icônes, pixels) sont signalées via onBroken.
 */
@Composable
private fun ImageCandidates(candidates: List<String>, selected: String, onSelect: (String) -> Unit, onBroken: (String) -> Unit) {
    if (candidates.size < 2 && (candidates.isEmpty() || candidates.first() == selected)) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("${candidates.size} images trouvées · touchez pour choisir", fontSize = 12.sp, color = TextSec)
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            candidates.forEachIndexed { i, u ->
                val on = u == selected
                Box(
                    Modifier.size(64.dp).clip(RoundedCornerShape(14.dp)).background(Sable2)
                        .then(if (on) Modifier.border(3.dp, Terracotta, RoundedCornerShape(14.dp)) else Modifier)
                        .clickable(role = Role.RadioButton) { onSelect(u) }
                        .semantics { contentDescription = "Image ${i + 1} sur ${candidates.size}" }
                ) {
                    AsyncImage(
                        model = u,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().padding(if (on) 3.dp else 0.dp).clip(RoundedCornerShape(11.dp)),
                        onError = { onBroken(u) },
                        onSuccess = { state ->
                            val size = state.painter.intrinsicSize
                            if (size.width > 0f && (size.width < 120f || size.height < 80f)) onBroken(u)
                        }
                    )
                }
            }
        }
    }
}
