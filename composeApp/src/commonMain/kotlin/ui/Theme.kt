package ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ── « Le feu de camp » — tokens tirés du logo tipi (identiques à frontend/src/index.css) ──
// Chaque couleur a sa valeur « veillée » (mode sombre, maquette « Mode sombre · palette
// veillée ») : un sombre chaud, sans ombres, où la profondeur vient de surfaces de plus
// en plus claires (fond → carte → feuille). Les couleurs sont lues à chaque usage :
// changer d'apparence recompose et redessine l'app.

/** Apparence choisie dans « Ma tribu » : « jour », « soir » ou « auto » (comme l'appareil). */
object Appearance {
    var pref by mutableStateOf("auto")
    /** Mode sombre de l'appareil (mis à jour par MainActivity). */
    var systemDark by mutableStateOf(false)
    val dark: Boolean get() = pref == "soir" || (pref == "auto" && systemDark)
    /** Enregistre la préférence sur l'appareil (branché par MainActivity). */
    var save: (String) -> Unit = {}

    fun set(value: String) {
        pref = value
        save(value)
    }
}

private fun pick(light: Long, dark: Long) = Color(if (Appearance.dark) dark else light)

val Sable: Color get() = pick(0xFFFBF3E7, 0xFF1C1411)        // fond
val Sable2: Color get() = pick(0xFFF1E3D1, 0xFF33281F)       // surfaces enfoncées, tags
val Soleil = Color(0xFFFDE6BD)
val Braise = Color(0xFFF28C38)
val Terracotta = Color(0xFFC8492C)   // action (boutons : inchangée le soir)
val TerracottaDark: Color get() = pick(0xFF9E3520, 0xFFF0855E)
val Tipi = Color(0xFFD45A43)         // logo
val Prune = Color(0xFF5E4558)        // IA (inchangée le soir)
val PruneSoft = Color(0xFFE3D3DF)
val Ecorce: Color get() = pick(0xFF3A211B, 0xFFF6EBDD)       // texte
val Ecorce2: Color get() = pick(0xFF4E3530, 0xFFD9C6B8)      // texte long
val TextSec: Color get() = pick(0xFF6B5047, 0xFFC4AE9E)      // texte secondaire
val TextMuted: Color get() = pick(0xFF8A6F64, 0xFFA38C7C)    // placeholders
val BarkSoft: Color get() = pick(0xFFD8C3B6, 0xFF5C4A3E)     // texte sur fond écorce
val BarkMute: Color get() = pick(0xFFCDB8AA, 0xFFA38C7C)     // icônes inactives sur fond écorce
val Line: Color get() = pick(0xFFEADBC8, 0xFF44342B)
val Dash: Color get() = pick(0xFFD9C2AE, 0xFF5C4A3E)

/** Carte (blanche le jour). */
val CardColor: Color get() = pick(0xFFFFFFFF, 0xFF282019)
/** Feuilles du bas et barre de navigation : surface élevée le soir. */
val SheetColor: Color get() = pick(0xFFFBF3E7, 0xFF33281F)
/** Bordure fine des cartes le soir (le jour, l'ombre suffit). */
val CardBorder: Color get() = pick(0x00000000, 0xFF3A2D24)
/** Texte clair posé sur prune ou terracotta (ces fonds ne changent pas le soir). */
val OnDark = Color(0xFFFBF3E7)
/** Texte ou icône posé sur une encre pleine (dossier, catégorie). */
val OnSolid: Color get() = pick(0xFFFFFFFF, 0xFF1C1411)
/** Disque « soleil » : un halo orangé le soir. */
val SunDisc: Color get() = pick(0xFFFDE6BD, 0x29F28C38)
/** Pastille posée sur une photo (retour, partage) : voile clair le jour, sombre le soir. */
val PhotoVeil: Color get() = if (Appearance.dark) Color(0xB81C1411) else Color.White.copy(alpha = 0.92f)
/** Encart « soleil » (rappel, bienvenue) : miel sombre le soir. */
val SoleilPanel: Color get() = pick(0xFFFDE6BD, 0xFF3A2D14)
/** Ciel et sol des petites scènes (célébrations, année en idées). */
val SceneSky: Color get() = pick(0xFF9FD3E6, 0xFF17303A)
val SceneGround: Color get() = pick(0xFFF7B57F, 0xFF4A2E1B)

// Pastels des pastilles et des tuiles (fond, encre) — mêmes paires que les catégories
val BlueBg: Color get() = pick(0xFFDCEDF3, 0xFF17303A)
val BlueInk: Color get() = pick(0xFF235C77, 0xFF8CCBE3)
val GreenBg: Color get() = pick(0xFFE2EDD9, 0xFF1F301B)
val GreenInk: Color get() = pick(0xFF3E6630, 0xFFA7D08F)
val HoneyBg: Color get() = pick(0xFFFBEACB, 0xFF3A2D14)
val HoneyInk: Color get() = pick(0xFF85560C, 0xFFF2C76B)
val PeachBg: Color get() = pick(0xFFFCE3D0, 0xFF3D2617)
val PeachInk: Color get() = pick(0xFFA04812, 0xFFF5A66E)
val PlumBg: Color get() = pick(0xFFECE1EA, 0xFF32232F)
val PlumInk: Color get() = pick(0xFF5E4558, 0xFFD6B4CF)
val CoralBg: Color get() = pick(0xFFF8DCD3, 0xFF3B201B)
val CoralInk: Color get() = pick(0xFF9A3420, 0xFFF09A84)
/** Fonds teintés de sélection (choix actif). */
val Blush: Color get() = pick(0xFFFFF4EE, 0xFF3B201B)
val PeachTint: Color get() = pick(0xFFFFF4EA, 0xFF3D2617)

// Anciens noms, conservés pour les écrans existants : ils pointent vers la nouvelle palette
val Orange = Terracotta
val OrangeLight = Braise
val BlueSky = Color(0xFF2F7393)

val TextPrimary: Color get() = Ecorce
val TextSecondary: Color get() = TextSec

/** Catégorie : fond pastel, encre (texte) et couleur d'icône — cf. frontend/src/lib/categories.ts. */
data class CategoryPalette(val bg: Color, val ink: Color, val icon: Color)

internal val LightCategoryPalettes = mapOf(
    "IDEE" to CategoryPalette(Color(0xFFFBEACB), Color(0xFF85560C), Color(0xFF9A6510)),
    "CADEAU" to CategoryPalette(Color(0xFFFCE3D0), Color(0xFFA04812), Color(0xFFC35A1C)),
    "ACTIVITE" to CategoryPalette(Color(0xFFDCEDF3), Color(0xFF235C77), Color(0xFF2F7393)),
    "EVENEMENT" to CategoryPalette(Color(0xFFF8DCD3), Color(0xFF9A3420), Color(0xFFB2412A)),
    "RECETTE" to CategoryPalette(Color(0xFFE2EDD9), Color(0xFF3E6630), Color(0xFF4C7A3C)),
    "LIVRE" to CategoryPalette(Color(0xFFECE1EA), Color(0xFF5E4558), Color(0xFF5E4558)),
    "DECORATION" to CategoryPalette(Color(0xFFF7DDE6), Color(0xFF9C3A5C), Color(0xFF9C3A5C)),
)

// Le soir : fond sombre teinté, encre claire (≥ 6,8:1)
internal val DarkCategoryPalettes = mapOf(
    "IDEE" to CategoryPalette(Color(0xFF3A2D14), Color(0xFFF2C76B), Color(0xFFF2C76B)),
    "CADEAU" to CategoryPalette(Color(0xFF3D2617), Color(0xFFF5A66E), Color(0xFFF5A66E)),
    "ACTIVITE" to CategoryPalette(Color(0xFF17303A), Color(0xFF8CCBE3), Color(0xFF8CCBE3)),
    "EVENEMENT" to CategoryPalette(Color(0xFF3B201B), Color(0xFFF09A84), Color(0xFFF09A84)),
    "RECETTE" to CategoryPalette(Color(0xFF1F301B), Color(0xFFA7D08F), Color(0xFFA7D08F)),
    "LIVRE" to CategoryPalette(Color(0xFF32232F), Color(0xFFD6B4CF), Color(0xFFD6B4CF)),
    "DECORATION" to CategoryPalette(Color(0xFF3B1F29), Color(0xFFEE9DB8), Color(0xFFEE9DB8)),
)

val CategoryPalettes: Map<String, CategoryPalette> get() = if (Appearance.dark) DarkCategoryPalettes else LightCategoryPalettes

// Couleurs de dossiers (encre) — mêmes teintes que FOLDER_COLORS côté web ;
// fonds et teintes secondaires dans ui/campfire/Campfire.kt (folderBg, folderMid)
private val LightFolderColors = mapOf(
    "ORANGE" to Color(0xFFA04812),
    "RED" to Color(0xFF9A3420),
    "YELLOW" to Color(0xFF85560C),
    "GREEN" to Color(0xFF3E6630),
    "TEAL" to Color(0xFF1F6B60),
    "BLUE" to Color(0xFF235C77),
    "PURPLE" to Color(0xFF5E4558),
    "PINK" to Color(0xFF9C3A5C),
    "SABLE" to Color(0xFF6B5047),
)
private val DarkFolderColors = mapOf(
    "ORANGE" to Color(0xFFF5A66E),
    "RED" to Color(0xFFF09A84),
    "YELLOW" to Color(0xFFF2C76B),
    "GREEN" to Color(0xFFA7D08F),
    "TEAL" to Color(0xFF8FD3C6),
    "BLUE" to Color(0xFF8CCBE3),
    "PURPLE" to Color(0xFFD6B4CF),
    "PINK" to Color(0xFFEE9DB8),
    "SABLE" to Color(0xFFC4AE9E),
)
val FolderColors: Map<String, Color> get() = if (Appearance.dark) DarkFolderColors else LightFolderColors

private fun tribbaeColors() = (if (Appearance.dark) androidx.compose.material3.darkColorScheme() else lightColorScheme()).copy(
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
    surfaceContainerLowest = CardColor,
    surfaceContainerLow = CardColor,
    surfaceContainer = CardColor,
    surfaceContainerHigh = SheetColor,
    surfaceContainerHighest = Sable2,
    outline = Dash,
    outlineVariant = Line,
    error = Color(0xFFB2412A),
    onError = Color.White,
    errorContainer = pick(0xFFF8DCD3, 0xFF3B201B),
    onErrorContainer = pick(0xFF9A3420, 0xFFF09A84),
)

// Titres serrés et gras (Bricolage Grotesque côté web), texte courant 15 sp
private fun tribbaeTypography() = Typography().let { t ->
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
        colorScheme = tribbaeColors(),
        typography = tribbaeTypography(),
        shapes = TribbaeShapes,
        content = content,
    )
}
