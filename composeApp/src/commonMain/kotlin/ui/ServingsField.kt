package ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Recette : nombre de personnes prévu (0 = non précisé, les courses comptent alors 4). Même champ que le web. */
@Composable
fun ServingsField(value: Int, onChange: (Int) -> Unit) {
    fun step(d: Int) = onChange(((if (value == 0) 4 else value) + d).coerceIn(1, 50))
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text("Pour combien de personnes", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextSec)
            Text("Les courses recalculent les quantités selon les parts.", fontSize = 12.sp, color = TextMuted)
        }
        Row(Modifier.clip(CircleShape).background(CardColor).padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            Step(Icons.Outlined.Remove, "Une personne de moins", value != 1) { step(-1) }
            Text(
                if (value > 0) "$value pers." else "—", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Ecorce, textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(min = 56.dp).semantics { liveRegion = LiveRegionMode.Polite },
            )
            Step(Icons.Outlined.Add, "Une personne de plus", value < 50) { step(1) }
        }
    }
}

@Composable
private fun Step(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(36.dp).clip(CircleShape).background(Sable).alpha(if (enabled) 1f else 0.4f)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = Ecorce, modifier = Modifier.size(15.dp)) }
}
