package ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ── « Le feu de camp » — tokens tirés du logo tipi (identiques à frontend/src/index.css) ──

val Sable = Color(0xFFFBF3E7)        // fond
val Sable2 = Color(0xFFF1E3D1)       // surfaces enfoncées, tags
val Soleil = Color(0xFFFDE6BD)
val Braise = Color(0xFFF28C38)
val Terracotta = Color(0xFFC8492C)   // action
val TerracottaDark = Color(0xFF9E3520)
val Tipi = Color(0xFFD45A43)         // logo
val Prune = Color(0xFF5E4558)        // IA
val PruneSoft = Color(0xFFE3D3DF)
val Ecorce = Color(0xFF3A211B)       // texte
val Ecorce2 = Color(0xFF4E3530)      // texte long
val TextSec = Color(0xFF6B5047)      // texte secondaire
val TextMuted = Color(0xFF8A6F64)    // placeholders
val BarkSoft = Color(0xFFD8C3B6)     // texte sur fond écorce
val BarkMute = Color(0xFFCDB8AA)     // icônes inactives sur fond écorce
val Line = Color(0xFFEADBC8)
val Dash = Color(0xFFD9C2AE)

// Anciens noms, conservés pour les écrans existants : ils pointent vers la nouvelle palette
val Orange = Terracotta
val OrangeLight = Braise
val Yellow = Color(0xFF9A6510)
val YellowLight = Soleil
val BlueSky = Color(0xFF2F7393)
val BlueSkyLight = Color(0xFFDCEDF3)
val Coral = Color(0xFFB2412A)
val Purple = Prune

val SurfaceColor = Sable
val CardColor = Color(0xFFFFFFFF)
val TextPrimary = Ecorce
val TextSecondary = TextSec

/** Catégorie : fond pastel, encre (texte) et couleur d'icône — cf. frontend/src/lib/categories.ts. */
data class CategoryPalette(val bg: Color, val ink: Color, val icon: Color)

val CategoryPalettes = mapOf(
    "IDEE" to CategoryPalette(Color(0xFFFBEACB), Color(0xFF85560C), Color(0xFF9A6510)),
    "CADEAU" to CategoryPalette(Color(0xFFFCE3D0), Color(0xFFA04812), Color(0xFFC35A1C)),
    "ACTIVITE" to CategoryPalette(Color(0xFFDCEDF3), Color(0xFF235C77), Color(0xFF2F7393)),
    "EVENEMENT" to CategoryPalette(Color(0xFFF8DCD3), Color(0xFF9A3420), Color(0xFFB2412A)),
    "RECETTE" to CategoryPalette(Color(0xFFE2EDD9), Color(0xFF3E6630), Color(0xFF4C7A3C)),
    "LIVRE" to CategoryPalette(Color(0xFFECE1EA), Color(0xFF5E4558), Color(0xFF5E4558)),
    "DECORATION" to CategoryPalette(Color(0xFFF7DDE6), Color(0xFF9C3A5C), Color(0xFF9C3A5C)),
)

// Couleur pleine par catégorie (icônes, pastilles)
val CategoryColors = CategoryPalettes.mapValues { it.value.icon }

// Couleurs de dossiers — mêmes teintes que FOLDER_COLORS côté web
val FolderColors = mapOf(
    "BLUE" to Color(0xFF2F7393),
    "GREEN" to Color(0xFF4C7A3C),
    "ORANGE" to Color(0xFFC35A1C),
    "PURPLE" to Color(0xFF5E4558),
    "RED" to Color(0xFFB2412A),
    "TEAL" to Color(0xFF2F7A6E),
    "PINK" to Color(0xFF9C3A5C),
    "YELLOW" to Color(0xFF9A6510),
)

private val TribbaeColors = lightColorScheme(
    primary = Terracotta,
    onPrimary = Color.White,
    primaryContainer = Soleil,
    onPrimaryContainer = Ecorce,
    secondary = Ecorce,
    onSecondary = Sable,
    secondaryContainer = Sable2,
    onSecondaryContainer = Ecorce,
    tertiary = Prune,
    onTertiary = Sable,
    tertiaryContainer = PruneSoft,
    onTertiaryContainer = Prune,
    background = Sable,
    onBackground = Ecorce,
    surface = Sable,
    onSurface = Ecorce,
    surfaceVariant = Sable2,
    onSurfaceVariant = TextSec,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Sable,
    surfaceContainerHighest = Sable2,
    outline = Dash,
    outlineVariant = Line,
    error = Color(0xFFB2412A),
    onError = Color.White,
    errorContainer = Color(0xFFF8DCD3),
    onErrorContainer = Color(0xFF9A3420),
)

// Titres serrés et gras (Bricolage Grotesque côté web), texte courant 15 sp
private val TribbaeTypography = Typography().let { t ->
    val display = TextStyle(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.6).sp, color = Ecorce)
    t.copy(
        displaySmall = t.displaySmall.merge(display).copy(fontSize = 32.sp, lineHeight = 34.sp),
        headlineLarge = t.headlineLarge.merge(display).copy(fontSize = 28.sp, lineHeight = 31.sp),
        headlineMedium = t.headlineMedium.merge(display).copy(fontSize = 24.sp, lineHeight = 28.sp),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.Bold, fontSize = 20.sp),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 20.sp),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 16.sp),
        bodyLarge = t.bodyLarge.copy(fontSize = 15.sp, lineHeight = 22.sp),
        bodyMedium = t.bodyMedium.copy(fontSize = 14.sp),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.Bold, fontSize = 15.sp),
    )
}

// Rayons 12 · 18 · 22 · 28
private val TribbaeShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun TribbaeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = TribbaeColors,
        typography = TribbaeTypography,
        shapes = TribbaeShapes,
        content = content,
    )
}
