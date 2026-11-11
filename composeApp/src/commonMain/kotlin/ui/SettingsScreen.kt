package ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ui.campfire.CircleIconButton
import ui.campfire.CampSwitch
import ui.campfire.display

/** Vrai pour l'onglet affiché sans écran par-dessus (les onglets cachés restent composés). */
val LocalTabActive = androidx.compose.runtime.compositionLocalOf { true }

/** Ce que l'écran Réglages affiche ; les actions restent celles de Ma tribu. */
class SettingsState(
    val name: String,
    val email: String?,
    val owner: Boolean,
    val isAdmin: Boolean,
    val tribeName: String,
    val membersLabel: String,
    val childrenLabel: String?,
    val followingCount: Int?,
    val version: String,
)

class SettingsActions(
    val onBack: () -> Unit,
    val onRename: () -> Unit,
    val onMembers: () -> Unit,
    val onChildren: () -> Unit,
    val onFollowing: () -> Unit,
    val onBlocked: () -> Unit,
    val onShopping: () -> Unit,
    val onApps: () -> Unit,
    val onPassword: () -> Unit,
    val onPrivacy: () -> Unit,
    val onExport: () -> Unit,
    val onDelete: () -> Unit,
    val onFaq: () -> Unit,
    val onAbout: () -> Unit,
    val onTerms: () -> Unit,
    val onLegalNotice: () -> Unit,
    val onAdmin: () -> Unit,
    val onLogout: () -> Unit,
)

/**
 * Écran « Réglages » (maquette « Ma tribu · Réglages regroupés ») : le compte,
 * puis la tribu, les préférences, les outils, le compte et ses données, et l'aide.
 */
@Composable
fun SettingsScreen(s: SettingsState, a: SettingsActions, dataMessage: String?, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().background(Sable).verticalScroll(rememberScrollState()).statusBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 40.dp + ui.components.bottomNavClearance()),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CircleIconButton(Icons.Outlined.ChevronLeft, "Retour à Ma tribu", a.onBack, background = Sable2)
            Text("Ma tribu", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextSec)
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Réglages", style = display(32.sp))
            Text("Votre compte et votre tribu, au même endroit.", fontSize = 14.sp, color = TextSec)
        }

        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(CardColor).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(Modifier.size(56.dp).clip(RoundedCornerShape(20.dp)).background(Braise), contentAlignment = Alignment.Center) {
                // Initiale brun foncé sur le disque braise, de jour comme de soir (contraste)
                Text(s.name.trim().take(1).uppercase().ifEmpty { "?" }, style = display(24.sp, color = Color(0xFF3A211B)))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(s.name, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Ecorce)
                s.email?.let { Text(it, fontSize = 13.sp, color = TextSec, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                if (s.owner) {
                    Text(
                        "Créateur de la tribu", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSec,
                        modifier = Modifier.padding(top = 4.dp).clip(RoundedCornerShape(8.dp)).background(Sable2).padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
        }

        Group("La tribu", "Le foyer et le cercle se gèrent aussi directement depuis Ma tribu.") {
            SettingsRow(Icons.Outlined.Cottage, "Nom de la tribu", sub = "La tribu ${s.tribeName}", onClick = a.onRename)
            SettingsRow(Icons.Outlined.Group, "Membres et invitations", value = s.membersLabel, onClick = a.onMembers)
            SettingsRow(Icons.Outlined.Person, "Les enfants", value = s.childrenLabel, onClick = a.onChildren)
            SettingsRow(Icons.Outlined.FavoriteBorder, "Tribus suivies", value = s.followingCount?.toString(), onClick = a.onFollowing)
            SettingsRow(Icons.Outlined.Block, "Comptes bloqués", last = true, onClick = a.onBlocked)
        }

        Group("Préférences", "L'apparence et les rappels sont retenus sur cet appareil.") {
            AppearanceRow()
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(Icons.Outlined.NotificationsNone, null, tint = TextSec, modifier = Modifier.size(20.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text("Rappels", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ecorce)
                    Text("La veille d'une sortie ou d'un anniversaire", fontSize = 13.sp, color = TextSec)
                }
                CampSwitch(data.ReminderPrefs.enabled, { data.ReminderPrefs.set(it) }, "Rappels")
            }
        }

        Group("Outils") {
            SettingsRow(Icons.Outlined.ShoppingCart, "Liste de courses", onClick = a.onShopping)
            SettingsRow(Icons.Outlined.AutoAwesome, "Applications connectées", sub = "Voir et retirer l'accès donné à Claude", tint = Prune, last = true, onClick = a.onApps)
        }

        Group("Compte et données", dataMessage) {
            SettingsRow(Icons.Outlined.Key, "Mot de passe", sub = "Recevoir un lien par email", onClick = a.onPassword)
            SettingsRow(Icons.Outlined.Lock, "Confidentialité", onClick = a.onPrivacy)
            SettingsRow(Icons.Outlined.Download, "Télécharger mes données", onClick = a.onExport)
            SettingsRow(Icons.Outlined.DeleteOutline, "Supprimer mon compte", danger = true, last = true, onClick = a.onDelete)
        }

        Group("Aide") {
            SettingsRow(Icons.Outlined.HelpOutline, "Aide & FAQ", onClick = a.onFaq)
            SettingsRow(Icons.Outlined.Info, "À propos de Tribbae", onClick = a.onAbout)
            SettingsRow(Icons.Outlined.Description, "Conditions d'utilisation", onClick = a.onTerms)
            SettingsRow(Icons.Outlined.Gavel, "Mentions légales", last = !s.isAdmin, onClick = a.onLegalNotice)
            if (s.isAdmin) SettingsRow(Icons.Outlined.AdminPanelSettings, "Administration", badge = "Admin", last = true, onClick = a.onAdmin)
        }

        Row(
            Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(18.dp)).background(CardColor)
                .border(1.5.dp, CoralBg, RoundedCornerShape(18.dp))
                .clickable(role = Role.Button, onClick = a.onLogout),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.Outlined.Logout, null, tint = CoralInk, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Se déconnecter", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = CoralInk)
        }
        if (s.version.isNotBlank()) {
            Text("Tribbae ${s.version}", fontSize = 12.sp, color = TextMuted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun Group(title: String, note: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp, color = TextSec, modifier = Modifier.padding(horizontal = 4.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(CardColor), content = content)
        note?.let { Text(it, fontSize = 12.sp, color = TextSec, modifier = Modifier.padding(horizontal = 4.dp)) }
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector, label: String, sub: String? = null, value: String? = null, badge: String? = null,
    tint: Color = TextSec, danger: Boolean = false, last: Boolean = false, onClick: () -> Unit,
) {
    Column {
        Row(
            Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val ink = if (danger) CoralInk else Ecorce
            Icon(icon, null, tint = if (danger) CoralInk else tint, modifier = Modifier.size(20.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = ink)
                sub?.let { Text(it, fontSize = 13.sp, color = TextSec, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            value?.let { Text(it, fontSize = 13.sp, color = TextSec, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 140.dp)) }
            badge?.let {
                Text(it, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSec,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Sable2).padding(horizontal = 7.dp, vertical = 2.dp))
            }
            if (!danger) Icon(Icons.Outlined.ChevronRight, null, tint = TextMuted, modifier = Modifier.size(18.dp))
        }
        if (!last) HorizontalDivider(color = Sable2)
    }
}
