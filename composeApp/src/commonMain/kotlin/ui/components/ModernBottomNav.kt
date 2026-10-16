package ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ui.BarkMute
import ui.Ecorce
import ui.Sable
import ui.Terracotta

enum class NavDestination {
    HOME,
    EXPLORE,
    ADD,
    MY_IDEAS,
    PROFILE
}

/**
 * Barre de navigation « feu de camp » : pilule écorce flottante, onglet actif
 * en pastille sable avec libellé, losange terracotta « + » au centre.
 */
@Composable
fun ModernBottomNav(
    currentDestination: NavDestination,
    onNavigate: (NavDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, bottom = 20.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
                .shadow(18.dp, RoundedCornerShape(26.dp), spotColor = Ecorce)
                .clip(RoundedCornerShape(26.dp))
                .background(Ecorce)
                .padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavTab(NavDestination.HOME, "Accueil", currentDestination, onNavigate) { tint ->
                Icon(Icons.Outlined.Home, null, tint = tint, modifier = Modifier.size(21.dp))
            }
            NavTab(NavDestination.EXPLORE, "Explorer", currentDestination, onNavigate) { tint ->
                Icon(Icons.Outlined.Explore, null, tint = tint, modifier = Modifier.size(21.dp))
            }
            // Emplacement du losange central
            Spacer(Modifier.width(56.dp))
            NavTab(NavDestination.MY_IDEAS, "Mes idées", currentDestination, onNavigate) { tint ->
                TipiIcon(tint, Modifier.size(21.dp))
            }
            NavTab(NavDestination.PROFILE, "Ma tribu", currentDestination, onNavigate) { tint ->
                Icon(Icons.Outlined.Groups, null, tint = tint, modifier = Modifier.size(22.dp))
            }
        }

        // Losange « + » qui déborde au-dessus de la barre
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-30).dp)
                .size(56.dp)
                .rotate(45f)
                .clip(RoundedCornerShape(20.dp))
                .background(Terracotta)
                .border(4.dp, Sable, RoundedCornerShape(20.dp))
                .clickable(role = Role.Button) { onNavigate(NavDestination.ADD) }
                .semantics { contentDescription = "Ajouter une idée" },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(26.dp).rotate(-45f)
            )
        }
    }
}

@Composable
private fun NavTab(
    destination: NavDestination,
    label: String,
    current: NavDestination,
    onNavigate: (NavDestination) -> Unit,
    icon: @Composable (Color) -> Unit
) {
    val selected = current == destination
    Row(
        modifier = Modifier
            .height(44.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) Sable else Color.Transparent)
            .clickable(role = Role.Tab) { onNavigate(destination) }
            .semantics(mergeDescendants = true) {
                this.selected = selected
                contentDescription = label
            }
            .animateContentSize()
            .padding(horizontal = if (selected) 14.dp else 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        icon(if (selected) Terracotta else BarkMute)
        AnimatedVisibility(visible = selected) {
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Ecorce, maxLines = 1)
        }
    }
}

/** Icône tipi « trait » de la refonte (deux triangles imbriqués). */
@Composable
fun TipiIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val s = size.width / 24f
        val stroke = Stroke(width = 1.9f * s, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val outer = Path().apply {
            moveTo(12f * s, 3f * s); lineTo(21f * s, 20f * s); lineTo(3f * s, 20f * s); close()
        }
        val inner = Path().apply {
            moveTo(12f * s, 11f * s); lineTo(16f * s, 20f * s); lineTo(8f * s, 20f * s); close()
        }
        drawPath(outer, tint, style = stroke)
        drawPath(inner, tint, style = stroke)
    }
}

