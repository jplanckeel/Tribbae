package ui.campfire

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import data.Child
import data.Folder
import data.FolderStyle
import ui.*

// Cartes de dossier de la maquette « Mes idées » (mêmes que frontend/src/components/cards.tsx)

/** Couleurs d'avatar d'un enfant (mêmes que sur le web : ordre des enfants de la tribu). */
fun childColors(children: List<Child>, id: String): Pair<Color, Color> {
    val i = children.indexOfFirst { it.id == id }.coerceAtLeast(0)
    return AvatarColors[(i + 2) % AvatarColors.size]
}

@Composable
fun ChildAvatar(name: String, colors: Pair<Color, Color>, size: Dp, ring: Color = Color.White, modifier: Modifier = Modifier) {
    Box(
        modifier.size(size).clip(CircleShape).background(ring).padding(2.dp).clip(CircleShape).background(colors.first),
        contentAlignment = Alignment.Center
    ) {
        Text(name.trim().take(1).uppercase().ifEmpty { "?" }, color = colors.second, fontSize = (size.value * 0.4f).sp, fontWeight = FontWeight.ExtraBold)
    }
}

/** Avatars des enfants « Pour qui » d'un dossier. */
@Composable
fun ChildAvatars(folder: Folder, children: List<Child>, size: Dp = 26.dp, ring: Color = Color.White) {
    val kids = folder.childIds.mapNotNull { id -> children.find { it.id == id } }
    if (kids.isEmpty()) return
    Row(Modifier.semantics { contentDescription = "Pour " + kids.joinToString(", ") { it.name } }) {
        kids.take(3).forEachIndexed { i, c ->
            ChildAvatar(c.name, childColors(children, c.id), size, ring, Modifier.offset(x = (-8 * i).dp))
        }
        if (kids.size > 3) ChildAvatar("+", Braise to Ecorce, size, ring, Modifier.offset(x = (-24).dp))
    }
}

/** « 12 idées · privé », « 23 idées · avec Camille »… */
private fun folderMeta(folder: Folder, count: Int, ownerLabel: String?): String {
    val who = when {
        ownerLabel != null -> ownerLabel
        folder.collaborators.isNotEmpty() -> {
            val c = folder.collaborators.first()
            "avec ${c.displayName.ifBlank { c.email.ifBlank { "un proche" } }}" + if (folder.collaborators.size > 1) " +${folder.collaborators.size - 1}" else ""
        }
        folder.visibility.uppercase().contains("PUBLIC") -> "public"
        folder.childIds.isNotEmpty() -> null
        else -> "privé"
    }
    return listOfNotNull("$count idée${if (count > 1) "s" else ""}", who).joinToString(" · ")
}

@Composable
private fun CountdownBadge(text: String, bg: Color, fg: Color, small: Boolean, modifier: Modifier = Modifier) {
    Text(
        text,
        color = fg,
        fontSize = if (small) 11.sp else 12.sp,
        fontWeight = FontWeight.ExtraBold,
        modifier = modifier.clip(RoundedCornerShape(if (small) 8.dp else 10.dp)).background(bg)
            .padding(horizontal = if (small) 7.dp else 10.dp, vertical = if (small) 3.dp else 5.dp)
    )
}

/**
 * Carte dossier « intercalaire » : onglet, bannière, compte à rebours, icône à
 * cheval, nom et méta. Sert aussi pour la communauté (ownerLabel).
 */
@Composable
fun FolderCard(
    folder: Folder,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    ownerLabel: String? = null,
    children: List<Child> = emptyList(),
) {
    val ink = folderSolid(folder.color)
    val cd = FolderStyle.countdown(folder)
    Box(modifier.clickable(onClick = onClick).padding(top = 10.dp)) {
        Box(Modifier.offset(y = (-10).dp).size(58.dp, 22.dp).clip(RoundedCornerShape(topStart = 10.dp, topEnd = 12.dp)).background(folderMid(folder.color)))
        Column(Modifier.fillMaxWidth().card(RoundedCornerShape(0.dp, 20.dp, 20.dp, 20.dp), 4.dp)) {
            Box(Modifier.fillMaxWidth().height(64.dp)) {
                FolderCover(folder)
                when {
                    cd != null -> CountdownBadge(cd.label, ink, Color.White, small = true, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp))
                    folder.aiGenerated -> Box(Modifier.align(Alignment.TopEnd).padding(8.dp)) { Tag("IA", Prune, Sable, Icons.Outlined.AutoAwesome) }
                }
            }
            Column(Modifier.pullUp(18.dp).padding(start = 12.dp, end = 12.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier.size(40.dp).clip(RoundedCornerShape(13.dp)).background(Color.White).padding(3.dp)
                        .clip(RoundedCornerShape(10.dp)).background(ink),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(folderGlyph(folder.icon), null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
                Column {
                    Text(folder.name, style = display(15.sp, FontWeight.Bold), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(folderMeta(folder, count, ownerLabel), fontSize = 12.sp, color = TextSec, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (ownerLabel == null) ChildAvatars(folder, children, 16.dp)
                    }
                }
            }
        }
    }
}

/** Grande carte d'un dossier épinglé en haut de « Mes idées ». */
@Composable
fun PinnedFolderCard(folder: Folder, count: Int, onClick: () -> Unit, children: List<Child>, modifier: Modifier = Modifier) {
    val ink = folderSolid(folder.color)
    val cd = FolderStyle.countdown(folder)
    val sub = listOf(folder.description, "$count idée${if (count > 1) "s" else ""}").filter { it.isNotBlank() }.joinToString(" · ")
    Column(modifier.fillMaxWidth().card(RoundedCornerShape(24.dp)).clickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().height(96.dp)) {
            FolderCover(folder)
            if (cd != null) {
                val vac = folder.keyDateType == "vacances"
                CountdownBadge(cd.label, if (vac) Ecorce else ink, if (vac) Sable else Color.White, small = false, modifier = Modifier.padding(12.dp))
            }
        }
        Row(
            Modifier.pullUp(22.dp).padding(start = 14.dp, end = 14.dp, bottom = 14.dp).fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                Modifier.size(52.dp).clip(RoundedCornerShape(17.dp)).background(Color.White).padding(3.dp)
                    .clip(RoundedCornerShape(14.dp)).background(ink),
                contentAlignment = Alignment.Center
            ) {
                Icon(folderGlyph(folder.icon), null, tint = Color.White, modifier = Modifier.size(25.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(folder.name, style = display(18.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(sub, fontSize = 13.sp, color = TextSec, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            ChildAvatars(folder, children)
        }
    }
}

/** Remonte un élément à cheval sur la bannière (marge négative qui réduit aussi sa hauteur). */
fun Modifier.pullUp(by: Dp): Modifier = layout { measurable, constraints ->
    val p = measurable.measure(constraints)
    val dy = by.roundToPx()
    layout(p.width, (p.height - dy).coerceAtLeast(0)) { p.place(0, -dy) }
}
