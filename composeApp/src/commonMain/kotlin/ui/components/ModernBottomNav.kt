package ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
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
import ui.Appearance
import ui.BarkMute
import ui.SheetColor
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

/** Hauteur de la pilule et marge sous elle (hors inset système). */
private val NavBarHeight = 68.dp
private val NavBarBottomMargin = 20.dp

/**
 * Espace à réserver en bas des écrans à onglets pour que rien ne reste caché
 * sous la barre : pilule + marge + losange qui déborde + barre système
 * (navigation à 3 boutons ou geste).
 */
@Composable
fun bottomNavClearance(): Dp =
    NavBarHeight + NavBarBottomMargin + 28.dp +
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

/**
 * Masque la barre quand on fait défiler vers le bas (lecture) et la ramène dès
 * qu'on remonte. À brancher sur le conteneur des écrans via Modifier.nestedScroll.
 */
class BottomNavScrollState {
    var hidden by mutableStateOf(false)

    val connection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (available.y < -6f) hidden = true
            else if (available.y > 6f) hidden = false
            return Offset.Zero
        }
    }
}

@Composable
fun rememberBottomNavScrollState() = remember { BottomNavScrollState() }

/**
 * Barre de navigation « feu de camp » : pilule écorce flottante, losange
 * terracotta « + » au centre. Chaque onglet a une place fixe (icône + libellé
 * toujours visibles) : changer d'onglet ne décale plus les autres.
 */
@Composable
fun ModernBottomNav(
    currentDestination: NavDestination,
    onNavigate: (NavDestination) -> Unit,
    modifier: Modifier = Modifier,
    hidden: Boolean = false,
) {
    val haptic = LocalHapticFeedback.current
    val navigate: (NavDestination) -> Unit = { d ->
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onNavigate(d)
    }
    // Glisse hors de l'écran (hauteur + marge + inset + débord du losange)
    val slide by animateDpAsState(
        if (hidden) bottomNavClearance() + 16.dp else 0.dp,
        animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
        label = "bottomNavSlide"
    )

    Box(modifier.fillMaxWidth().offset(y = slide), contentAlignment = Alignment.BottomCenter) {
        // Fondu sable derrière la barre : le contenu qui défile dessous s'efface au lieu d'être coupé net
        Box(
            Modifier
                .matchParentSize()
                .background(Brush.verticalGradient(listOf(Sable.copy(alpha = 0f), Sable.copy(alpha = 0.92f))))
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = NavBarBottomMargin),
            contentAlignment = Alignment.BottomCenter
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(NavBarHeight)
                    // Le soir : surface élevée bordée, sans ombre
                    .then(
                        if (Appearance.dark) Modifier.border(1.dp, Color(0xFF4A3A30), RoundedCornerShape(26.dp))
                        else Modifier.shadow(18.dp, RoundedCornerShape(26.dp), spotColor = Ecorce)
                    )
                    .clip(RoundedCornerShape(26.dp))
                    .background(if (Appearance.dark) SheetColor else Ecorce)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                NavTab(NavDestination.HOME, "Accueil", currentDestination, navigate) { tint ->
                    Icon(Icons.Outlined.Home, null, tint = tint, modifier = Modifier.size(21.dp))
                }
                NavTab(NavDestination.EXPLORE, "Explorer", currentDestination, navigate) { tint ->
                    Icon(Icons.Outlined.Explore, null, tint = tint, modifier = Modifier.size(21.dp))
                }
                // Emplacement du losange central
                Spacer(Modifier.weight(1f))
                NavTab(NavDestination.MY_IDEAS, "Mes idées", currentDestination, navigate) { tint ->
                    TipiIcon(tint, Modifier.size(21.dp))
                }
                NavTab(NavDestination.PROFILE, "Ma tribu", currentDestination, navigate) { tint ->
                    Icon(Icons.Outlined.Groups, null, tint = tint, modifier = Modifier.size(22.dp))
                }
            }

            // Losange « + » : comme sur la maquette (margin-top −30 px dans une barre
            // centrée de 68 px), son bord haut dépasse de 9 dp au-dessus de la barre.
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = (-9).dp)
                    .size(56.dp)
                    .rotate(45f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Terracotta)
                    .border(4.dp, Sable, RoundedCornerShape(20.dp))
                    .clickable(role = Role.Button) { navigate(NavDestination.ADD) }
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
}

@Composable
private fun RowScope.NavTab(
    destination: NavDestination,
    label: String,
    current: NavDestination,
    onNavigate: (NavDestination) -> Unit,
    icon: @Composable (Color) -> Unit
) {
    val selected = current == destination
    // Onglet actif : pilule claire (sable le jour, texte clair le soir)
    val active = if (Appearance.dark) Ecorce else Sable
    val pill by animateColorAsState(if (selected) active else active.copy(alpha = 0f), tween(200), label = "navPill")
    val tint by animateColorAsState(if (selected) Terracotta else BarkMute, tween(200), label = "navTint")
    val text by animateColorAsState(if (selected) active else BarkMute, tween(200), label = "navText")
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clip(RoundedCornerShape(18.dp))
            .clickable(role = Role.Tab) { onNavigate(destination) }
            .semantics(mergeDescendants = true) {
                this.selected = selected
                contentDescription = label
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)
    ) {
        Box(
            Modifier.size(width = 48.dp, height = 30.dp).clip(RoundedCornerShape(15.dp)).background(pill),
            contentAlignment = Alignment.Center
        ) { icon(tint) }
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = text, maxLines = 1)
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

