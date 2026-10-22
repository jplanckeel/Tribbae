package ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import data.Folder
import data.FolderColor
import data.FolderIcon
import data.FolderStyle
import ui.campfire.*
import viewmodel.LinkViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private fun inDays(n: Int): Long = Calendar.getInstance().apply {
    add(Calendar.DAY_OF_YEAR, n)
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

/**
 * « Personnaliser » un dossier (maquette du même nom, comme le web) : aperçu en
 * direct de la carte, puis Style (couleur, couverture, icône), Infos (nom,
 * petit mot, date clé, pour qui, visibilité) et Affichage (vue, tri, épinglage).
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun FolderFormScreen(viewModel: LinkViewModel, onBack: () -> Unit, existing: Folder? = null) {
    var draft by remember { mutableStateOf(existing ?: Folder(id = "", name = "", icon = FolderIcon.CAMPING, color = FolderColor.ORANGE, visibility = "PRIVATE")) }
    var tab by remember { mutableStateOf(if (existing != null) 0 else 1) }
    var error by remember { mutableStateOf<String?>(null) }
    var showDate by remember { mutableStateOf(false) }
    val children by viewModel.children.collectAsState()
    val allLinks by viewModel.repository.links.collectAsState()
    val inFolder = remember(allLinks, existing) { if (existing == null) emptyList() else allLinks.filter { it.folderId == existing.id } }
    val photos = remember(inFolder) { inFolder.map { it.imageUrl }.filter { it.startsWith("http") }.distinct().take(12) }

    val ink by animateColorAsState(folderSolid(draft.color))
    val shown = draft.copy(name = draft.name.ifBlank { "Mon dossier" })
    val (cover, illustration) = FolderStyle.resolvedCover(shown)
    val suggested = FolderStyle.autoIllustration(draft.name, draft.keyDate, draft.keyDateType)
    val photo = if (cover == "photo") FolderStyle.coverPhoto(draft, inFolder) else null
    val cd = FolderStyle.countdown(draft)

    fun submit() {
        if (draft.name.isBlank()) { tab = 1; error = "Donnez un nom au dossier."; return }
        val clean = draft.copy(name = draft.name.trim(), description = draft.description.trim())
        if (existing != null) viewModel.updateFolder(clean) else viewModel.addFolder(clean)
        onBack()
    }

    Column(Modifier.fillMaxSize().background(Sable).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            CircleIconButton(Icons.Outlined.ChevronLeft, "Retour", onBack, background = Sable2)
            Text(if (existing != null) "Personnaliser" else "Nouveau dossier", style = display(18.sp, FontWeight.Bold), modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Spacer(Modifier.width(44.dp))
        }

        // Aperçu en direct
        Box(Modifier.padding(horizontal = 20.dp).padding(top = 4.dp).fillMaxWidth().height(214.dp)) {
            Box(Modifier.fillMaxWidth().height(128.dp).clip(RoundedCornerShape(24.dp))) {
                CoverArt(cover, illustration, draft.color, photo)
            }
            Box(Modifier.align(Alignment.BottomCenter).padding(horizontal = 16.dp).padding(top = 12.dp)) {
                Box(Modifier.offset(y = (-12).dp).size(64.dp, 24.dp).clip(RoundedCornerShape(topStart = 10.dp, topEnd = 12.dp)).background(Color.White))
                Row(
                    Modifier.fillMaxWidth().card(RoundedCornerShape(0.dp, 22.dp, 22.dp, 22.dp), 8.dp).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(Modifier.size(54.dp).clip(RoundedCornerShape(17.dp)).background(ink), contentAlignment = Alignment.Center) {
                        Icon(folderGlyph(draft.icon), null, tint = Color.White, modifier = Modifier.size(27.dp))
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(shown.name, style = display(19.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            draft.description.ifBlank { "${inFolder.size} idée${if (inFolder.size > 1) "s" else ""}" },
                            fontSize = 13.sp, color = TextSec, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        Row(Modifier.heightIn(min = 24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (cd != null) {
                                Text(
                                    cd.label, color = ink, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(folderBg(draft.color)).padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                            ChildAvatars(draft, children, 24.dp)
                        }
                    }
                }
                if (draft.pinned) {
                    Box(
                        Modifier.align(Alignment.TopEnd).offset(x = (-12).dp, y = (-20).dp).size(28.dp).clip(CircleShape).background(Ecorce),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Outlined.PushPin, "Épinglé", tint = Sable, modifier = Modifier.size(14.dp)) }
                }
            }
        }

        SegmentedTabs(listOf("Style", "Infos", "Affichage"), tab, { tab = it }, Modifier.padding(horizontal = 20.dp).padding(top = 14.dp).fillMaxWidth())

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            when (tab) {
                0 -> {
                    Group("Couleur") {
                        FlowRow {
                            FolderColor.entries.forEach { c ->
                                val on = c == draft.color
                                val sc by animateFloatAsState(if (on) 1f else 0.86f, spring(Spring.DampingRatioMediumBouncy))
                                Box(
                                    Modifier.size(44.dp).clickable(role = Role.RadioButton) { draft = draft.copy(color = c) }
                                        .semantics { contentDescription = FolderColorLabels[c] ?: c.name; selected = on },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        Modifier.size(42.dp).border(2.dp, if (on) folderSolid(c) else Color.Transparent, CircleShape)
                                            .padding(5.dp).scale(sc).clip(CircleShape).background(folderSolid(c))
                                    )
                                }
                            }
                        }
                    }

                    Group("Couverture", if (draft.cover.isEmpty()) "· automatique" else null) {
                        FolderStyle.COVERS.chunked(3).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { (k, label) ->
                                    val on = cover == k
                                    val sc by animateFloatAsState(if (on) 1.04f else 1f, spring(Spring.DampingRatioMediumBouncy))
                                    Box(
                                        Modifier.weight(1f).height(64.dp).scale(sc).clip(RoundedCornerShape(16.dp))
                                            .border(2.5.dp, if (on) Ecorce else Color.Transparent, RoundedCornerShape(16.dp))
                                            .clickable(role = Role.RadioButton) {
                                                draft = if (k == "illu" && draft.illustration.isEmpty()) draft.copy(cover = k, illustration = illustration ?: suggested ?: "anniv") else draft.copy(cover = k)
                                            }
                                            .semantics { selected = on },
                                        contentAlignment = Alignment.BottomCenter
                                    ) {
                                        CoverArt(k, if (k == "illu") illustration ?: suggested ?: "anniv" else null, draft.color, if (k == "photo") photo ?: photos.firstOrNull() else null)
                                        Text(
                                            label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = folderSolid(draft.color),
                                            modifier = Modifier.padding(bottom = 4.dp).clip(RoundedCornerShape(5.dp)).background(Color.White.copy(alpha = 0.85f)).padding(horizontal = 5.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                        if (cover == "illu") {
                            Text("Choisir un dessin", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextSec)
                            Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FolderStyle.ILLUSTRATIONS.forEach { (k, label) ->
                                    val on = illustration == k
                                    Box(
                                        Modifier.size(108.dp, 68.dp).clip(RoundedCornerShape(14.dp))
                                            .border(3.dp, if (on) Ecorce else Color.Transparent, RoundedCornerShape(14.dp))
                                            .clickable(role = Role.RadioButton) { draft = draft.copy(cover = "illu", illustration = k) }
                                            .semantics { contentDescription = label; selected = on }
                                    ) {
                                        IllustrationArt(k, draft.color)
                                        if (suggested == k && !on) {
                                            Text(
                                                "Suggéré", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Sable,
                                                modifier = Modifier.align(Alignment.BottomStart).padding(4.dp).clip(RoundedCornerShape(6.dp)).background(Ecorce).padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        if (cover == "photo") {
                            if (photos.isEmpty()) {
                                Text("Ajoutez au dossier des idées avec une photo pour en faire la couverture.", fontSize = 12.sp, color = TextSec)
                            } else {
                                Text("Une photo des idées du dossier", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextSec)
                                Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    photos.forEach { u ->
                                        val on = photo == u
                                        AsyncImage(
                                            u, "Choisir cette photo",
                                            Modifier.size(96.dp, 68.dp).clip(RoundedCornerShape(14.dp))
                                                .border(3.dp, if (on) Ecorce else Color.Transparent, RoundedCornerShape(14.dp))
                                                .clickable(role = Role.RadioButton) { draft = draft.copy(cover = "photo", bannerUrl = u) },
                                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Group("Icône") {
                        FolderIconOrder.chunked(6).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                row.forEach { i ->
                                    val on = i == draft.icon
                                    val sc by animateFloatAsState(if (on) 1.08f else 1f, spring(Spring.DampingRatioMediumBouncy))
                                    Box(
                                        Modifier.weight(1f).height(50.dp).scale(sc).clip(RoundedCornerShape(14.dp)).background(if (on) ink else Color.White)
                                            .clickable(role = Role.RadioButton) { draft = draft.copy(icon = i) }
                                            .semantics { contentDescription = FolderIconLabels[i] ?: i.name; selected = on },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(folderGlyph(i), null, tint = if (on) Color.White else Ecorce, modifier = Modifier.size(22.dp))
                                    }
                                }
                                repeat(6 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                            Spacer(Modifier.height(6.dp))
                        }
                    }
                }

                1 -> {
                    CampField("Nom", draft.name, { draft = draft.copy(name = it); error = null }, placeholder = "Vacances Drôme", maxLength = 100)
                    CampField("Un petit mot (facultatif)", draft.description, { draft = draft.copy(description = it) }, placeholder = "Ex : pour ses 10 ans", maxLength = 140)

                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Outlined.CalendarMonth, null, tint = Terracotta, modifier = Modifier.size(20.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Une date clé", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Ecorce)
                                Text("Compte à rebours sur le dossier", fontSize = 12.sp, color = TextSec)
                            }
                            Switch(
                                checked = draft.keyDate > 0,
                                onCheckedChange = { on ->
                                    draft = if (on) draft.copy(keyDate = inDays(14), keyDateType = draft.keyDateType.ifEmpty { "event" }) else draft.copy(keyDate = 0, keyDateType = "")
                                },
                                colors = SwitchDefaults.colors(checkedTrackColor = ink, checkedThumbColor = Color.White, uncheckedTrackColor = Color(0xFFD9C2AE), uncheckedThumbColor = Color.White, uncheckedBorderColor = Color.Transparent)
                            )
                        }
                        if (draft.keyDate > 0) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                FolderStyle.KEY_DATE_TYPES.forEach { (k, label) ->
                                    val on = draft.keyDateType == k
                                    Text(
                                        label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Ecorce,
                                        modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(if (on) folderBg(draft.color) else Color.White)
                                            .border(2.dp, if (on) Ecorce else Color(0xFFE6D3C0), RoundedCornerShape(12.dp))
                                            .clickable(role = Role.RadioButton) { draft = draft.copy(keyDateType = k) }
                                            .padding(horizontal = 12.dp, vertical = 10.dp)
                                    )
                                }
                            }
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).border(1.5.dp, Color(0xFFE6D3C0), RoundedCornerShape(12.dp))
                                    .clickable(role = Role.Button) { showDate = true }.padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(if (draft.keyDateType == "anniv") "Date de l'anniversaire" else "Date", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextSec)
                                Text(SimpleDateFormat("d MMMM yyyy", Locale.FRENCH).format(draft.keyDate), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ecorce)
                            }
                        }
                    }

                    Group("Pour qui") {
                        if (children.isEmpty()) {
                            Text("Ajoutez vos enfants dans « Ma tribu » pour indiquer à qui s'adresse le dossier.", fontSize = 12.sp, color = TextSec)
                        } else {
                            val all = draft.childIds.size == children.size
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), maxItemsInEachRow = 3) {
                                children.forEach { c ->
                                    val on = c.id in draft.childIds
                                    KidChip(c.name, childColors(children, c.id), on, Modifier.weight(1f)) {
                                        draft = draft.copy(childIds = if (on) draft.childIds - c.id else draft.childIds + c.id)
                                    }
                                }
                                if (children.size > 1) {
                                    KidChip("Tous", Braise to Ecorce, all, Modifier.weight(1f)) {
                                        draft = draft.copy(childIds = if (all) emptyList() else children.map { it.id })
                                    }
                                }
                            }
                        }
                    }

                    Group("Visibilité") {
                        val isPublic = draft.visibility.uppercase().contains("PUBLIC")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(false to "Privé", true to "Public").forEach { (pub, label) ->
                                val on = isPublic == pub
                                Column(
                                    Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).background(if (on) Color.White else Sable2)
                                        .border(2.dp, if (on) Ecorce else Color.Transparent, RoundedCornerShape(18.dp))
                                        .clickable(role = Role.RadioButton) {
                                            // « Partagé » est dérivé des collaborateurs : on le garde tant que le dossier reste privé
                                            draft = draft.copy(visibility = if (pub) "PUBLIC" else if (existing?.visibility == "SHARED") "SHARED" else "PRIVATE")
                                        }.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Icon(if (pub) Icons.Outlined.Public else Icons.Outlined.Lock, null, tint = Ecorce, modifier = Modifier.size(16.dp))
                                        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Ecorce)
                                    }
                                    Text(if (pub) "Vos idées du dossier visibles par tous, dans l'Explorer" else "Vous et les membres invités", fontSize = 12.sp, color = TextSec)
                                }
                            }
                        }
                    }
                }

                else -> {
                    Group("Afficher les idées en") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FolderStyle.VIEW_MODES.forEach { (k, label) ->
                                val on = draft.viewMode.ifEmpty { "grid" } == k
                                Column(
                                    Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(Color.White)
                                        .border(2.5.dp, if (on) Ecorce else Color.Transparent, RoundedCornerShape(16.dp))
                                        .clickable(role = Role.RadioButton) { draft = draft.copy(viewMode = k) }.padding(10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    ViewPreview(k, ink, folderMid(draft.color))
                                    Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Ecorce)
                                }
                            }
                        }
                    }
                    Group("Trier par") {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            FolderStyle.SORTS.forEach { (k, label) ->
                                val on = draft.sort.ifEmpty { "recent" } == k
                                Text(
                                    label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (on) Sable else Ecorce,
                                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(if (on) Ecorce else Color.White)
                                        .clickable(role = Role.RadioButton) { draft = draft.copy(sort = k) }
                                        .padding(horizontal = 14.dp, vertical = 11.dp)
                                )
                            }
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White)
                            .clickable(role = Role.Switch) { draft = draft.copy(pinned = !draft.pinned) }.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Outlined.PushPin, null, tint = Terracotta, modifier = Modifier.size(20.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Épingler en haut de Mes idées", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Ecorce)
                            Text("Toujours sous la main", fontSize = 12.sp, color = TextSec)
                        }
                        Switch(
                            checked = draft.pinned,
                            onCheckedChange = { draft = draft.copy(pinned = it) },
                            colors = SwitchDefaults.colors(checkedTrackColor = ink, checkedThumbColor = Color.White, uncheckedTrackColor = Color(0xFFD9C2AE), uncheckedThumbColor = Color.White, uncheckedBorderColor = Color.Transparent)
                        )
                    }
                }
            }
            error?.let { Text(it, color = Color(0xFF9A3420), fontSize = 14.sp) }
        }

        Box(Modifier.fillMaxWidth().background(Sable).navigationBarsPadding().imePadding().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 16.dp)) {
            CampButton(if (existing != null) "Enregistrer" else "Créer le dossier", ::submit, Modifier.fillMaxWidth())
        }
    }

    if (showDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = (draft.keyDate.takeIf { it > 0 } ?: System.currentTimeMillis()).let { local ->
            // Le sélecteur travaille en minuit UTC
            val c = Calendar.getInstance().apply { timeInMillis = local }
            Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply { clear(); set(c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)) }.timeInMillis
        })
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { utc ->
                        val day = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply { timeInMillis = utc }
                        draft = draft.copy(keyDate = Calendar.getInstance().apply {
                            clear(); set(day.get(Calendar.YEAR), day.get(Calendar.MONTH), day.get(Calendar.DAY_OF_MONTH))
                        }.timeInMillis)
                    }
                    showDate = false
                }) { Text("Valider", color = Terracotta, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("Annuler", color = Ecorce) } }
        ) { DatePicker(state) }
    }
}

@Composable
private fun Group(label: String, hint: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row {
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextSec)
            if (hint != null) Text(" $hint", fontSize = 13.sp, color = TextSec)
        }
        content()
    }
}

@Composable
private fun KidChip(name: String, colors: Pair<Color, Color>, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier.height(56.dp).clip(RoundedCornerShape(16.dp)).background(Color.White)
            .border(2.dp, if (on) Ecorce else Color.Transparent, RoundedCornerShape(16.dp))
            .clickable(role = Role.Checkbox, onClick = onClick).semantics { selected = on },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
    ) {
        ChildAvatar(name, colors, 28.dp, ring = colors.first)
        Text(name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Ecorce, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Mini-schéma d'une vue (grille, liste, mosaïque) aux couleurs du dossier. */
@Composable
private fun ViewPreview(mode: String, ink: Color, mid: Color) {
    val cell = @Composable { first: Boolean, m: Modifier -> Box(m.clip(RoundedCornerShape(4.dp)).background(if (first) ink else mid)) }
    Box(Modifier.size(64.dp, 52.dp)) {
        when (mode) {
            "list" -> Column(verticalArrangement = Arrangement.spacedBy(3.dp)) { repeat(3) { cell(it == 0, Modifier.fillMaxWidth().weight(1f)) } }
            "mosaic" -> Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                cell(true, Modifier.weight(1f).fillMaxHeight())
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) { repeat(2) { cell(false, Modifier.fillMaxWidth().weight(1f)) } }
            }
            else -> Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                repeat(2) { r -> Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(3.dp)) { repeat(2) { c -> cell(r == 0 && c == 0, Modifier.weight(1f).fillMaxHeight()) } } }
            }
        }
    }
}
