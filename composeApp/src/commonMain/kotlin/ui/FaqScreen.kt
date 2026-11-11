package ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import data.Faq
import data.FaqSection
import ui.campfire.CircleIconButton
import ui.campfire.display

/** Icônes des sections, mêmes noms que la page /aide du site. */
private fun sectionIcon(name: String): ImageVector = when (name) {
    "tipi" -> Icons.Outlined.Cottage
    "idee" -> Icons.Outlined.Lightbulb
    "tribe" -> Icons.Outlined.Group
    "sparkles" -> Icons.Outlined.AutoAwesome
    "lock" -> Icons.Outlined.Lock
    else -> Icons.Outlined.HelpOutline
}

/** « Aide & FAQ » : la FAQ du site (faq.json), copiée dans l'app à la compilation. */
@Composable
fun FaqScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val sections = remember {
        runCatching { context.assets.open("faq.json").bufferedReader().use { it.readText() } }
            .map(Faq::parse).getOrDefault(emptyList<FaqSection>())
    }
    var open by remember { mutableStateOf<String?>(null) }
    val uri = LocalUriHandler.current

    Column(
        Modifier.fillMaxSize().background(Sable).verticalScroll(rememberScrollState()).statusBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 32.dp + ui.components.bottomNavClearance()),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CircleIconButton(Icons.Outlined.ChevronLeft, "Retour", onBack, background = Sable2)
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Aide & FAQ", style = display(32.sp))
            Text("Les réponses aux questions qu'on nous pose le plus.", fontSize = 14.sp, color = TextSec)
        }

        sections.forEach { section ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(horizontal = 4.dp)) {
                    Icon(sectionIcon(section.icon), null, tint = TextSec, modifier = Modifier.size(18.dp))
                    Text(section.title.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp, color = TextSec)
                }
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(CardColor)) {
                    section.questions.forEachIndexed { i, (q, a) ->
                        val key = "${section.title}/$i"
                        val expanded = open == key
                        Column(
                            Modifier.fillMaxWidth()
                                .clickable(role = Role.Button) { open = if (expanded) null else key }
                                .semantics { stateDescription = if (expanded) "Réponse affichée" else "Réponse masquée" }
                                .padding(horizontal = 16.dp, vertical = 14.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(q, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ecorce, modifier = Modifier.weight(1f).padding(end = 8.dp))
                                Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null, tint = TextMuted, modifier = Modifier.size(20.dp))
                            }
                            AnimatedVisibility(expanded, enter = expandVertically(), exit = shrinkVertically()) {
                                Text(a, fontSize = 14.sp, lineHeight = 21.sp, color = Ecorce2, modifier = Modifier.padding(top = 8.dp))
                            }
                        }
                        if (i < section.questions.lastIndex) HorizontalDivider(color = Sable2)
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(CardColor)
                .clickable(role = Role.Button) { uri.openUri("mailto:contact@tribbae.fr") }.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(Icons.Outlined.MailOutline, null, tint = TerracottaDark, modifier = Modifier.size(22.dp))
            Column(Modifier.weight(1f)) {
                Text("Vous n'avez pas trouvé votre réponse ?", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Ecorce)
                Text("Écrivez-nous : contact@tribbae.fr", fontSize = 13.sp, color = TextSec)
            }
        }
    }
}
