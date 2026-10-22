package ui.campfire

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import data.Folder
import data.FolderColor
import data.FolderIcon
import data.FolderStyle
import data.Link
import kotlin.math.max
import kotlin.math.sqrt

// ── Icônes des dossiers : mêmes tracés que FOLDER_ICONS (frontend/src/lib/categories.ts) ──

private val GLYPHS: Map<FolderIcon, String> = mapOf(
    FolderIcon.CAMPING to "M12 3L21 20H3z M12 11l4 9H8z",
    FolderIcon.FOREST to "M12 3l5 7h-3l4 6H6l4-6H7z M12 16v5",
    FolderIcon.MOUNTAIN to "M3 20l6-11 4 6 3-4 5 9z",
    FolderIcon.BEACH to "M4 20h16 M12 8v12 M5 9a7 7 0 0 1 14 0z",
    FolderIcon.SPORTS to "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18z M12 7l4 3-1.5 4.5h-5L8 10z",
    FolderIcon.MUSIC to "M9 18V6l10-2v12 M6 21a3 3 0 1 0 0-6a3 3 0 1 0 0 6z M16 19a3 3 0 1 0 0-6a3 3 0 1 0 0 6z",
    FolderIcon.PALETTE to "M12 3a9 9 0 1 0 0 18c1.5 0 2-1 2-2s-1-1.5-1-2.5 1-1.5 2-1.5h2a4 4 0 0 0 4-4c0-4.4-4-8-9-8z M7.5 11h.01M11 7.5h.01M16 8.5h.01",
    FolderIcon.MOVIE to "M4 6h16v12H4z M4 10h16M8 6l2 4M14 6l2 4",
    FolderIcon.CAKE to "M4 21h16v-8H4z M4 16c2 1.2 4 1.2 6 0s4-1.2 6 0 3 1.2 4 0 M12 13V9 M12 5v1",
    FolderIcon.RESTAURANT to "M7 3v8M5 3v5a2 2 0 0 0 4 0V3M7 11v10M17 21V3c-2 1.5-3 4-3 7h3",
    FolderIcon.PARTY to "M4 20l4-12 8 8z M14 4l1 2M19 5l-2 2M20 10l-2-.5",
    FolderIcon.ROCKET to "M12 2c3 3 4 7 4 11l-4 3-4-3c0-4 1-8 4-11z M8 13l-3 3v3l3-2M16 13l3 3v3l-3-2",
    FolderIcon.MAGIC to "M12 3l1.8 5.2L19 10l-5.2 1.8L12 17l-1.8-5.2L5 10l5.2-1.8z",
    FolderIcon.BABY to "M12 4a4 4 0 1 0 0 8a4 4 0 1 0 0-8z M6 21c0-3.3 2.7-6 6-6s6 2.7 6 6",
    FolderIcon.PETS to "M7 9a1.5 1.5 0 1 0 0-3a1.5 1.5 0 1 0 0 3z M17 9a1.5 1.5 0 1 0 0-3a1.5 1.5 0 1 0 0 3z M12 12c-3 0-5 3-5 5.5 0 1.5 1.5 2 3 1.5s2.5-.5 4 0 3 0 3-1.5c0-2.5-2-5.5-5-5.5z",
    FolderIcon.SCHOOL to "M2 9l10-5 10 5-10 5z M6 11v5c0 1.5 3 3 6 3s6-1.5 6-3v-5",
    FolderIcon.HEART to "M12 20s-7-4.4-7-10a4 4 0 0 1 7-2.6A4 4 0 0 1 19 10c0 5.6-7 10-7 10z",
    FolderIcon.STAR to "M12 3l2.7 5.6 6.1.9-4.4 4.3 1 6.1L12 17l-5.4 2.9 1-6.1-4.4-4.3 6.1-.9z",
    FolderIcon.FOLDER to "M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z",
    FolderIcon.HOME to "M4 11l8-7 8 7v9a1 1 0 0 1-1 1h-4v-6h-6v6H5a1 1 0 0 1-1-1z",
    FolderIcon.TRAVEL to "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18z M15.5 8.5l-2 5-5 2 2-5z",
    FolderIcon.PARK to "M12 3a5 5 0 0 0-5 5c0 3 2.2 5 5 5s5-2 5-5a5 5 0 0 0-5-5z M12 13v8 M8 21h8",
    FolderIcon.GAMES to "M6 8h12a4 4 0 0 1 0 8c-1.5 0-2.5-1-3-2h-6c-.5 1-1.5 2-3 2a4 4 0 0 1 0-8z M8 10v4M6 12h4 M15.5 11h.01M17.5 13h.01",
    FolderIcon.COFFEE to "M4 9h12v6a5 5 0 0 1-5 5H9a5 5 0 0 1-5-5z M16 11h1.5a2.5 2.5 0 0 1 0 5H16 M8 3v3M12 3v3",
    FolderIcon.CANDLE to "M12 3c2 2.5 3 4 3 5.5a3 3 0 0 1-6 0C9 7 10 5.5 12 3z M9 13h6v8H9z",
    FolderIcon.PIRATE to "M3 17h18l-2 4H5z M12 3v14 M12 4l7 9h-7z M12 7l-5 6h5",
    FolderIcon.SHOPPING to "M4 5h2l2 11h11l2-8H7M10 20a1 1 0 1 0 0-.1M17 20a1 1 0 1 0 0-.1",
    FolderIcon.WORK to "M4 8h16v11H4z M9 8V5h6v3 M4 13h16",
    FolderIcon.BOOKMARK to "M6 4h12v17l-6-4-6 4z",
)

/** Ordre du sélecteur d'icônes (celui de la maquette, puis les autres). */
val FolderIconOrder: List<FolderIcon> = GLYPHS.keys.toList()

val FolderIconLabels: Map<FolderIcon, String> = mapOf(
    FolderIcon.CAMPING to "Camping", FolderIcon.FOREST to "Forêt", FolderIcon.MOUNTAIN to "Montagne", FolderIcon.BEACH to "Plage",
    FolderIcon.SPORTS to "Sport", FolderIcon.MUSIC to "Musique", FolderIcon.PALETTE to "Arts", FolderIcon.MOVIE to "Cinéma",
    FolderIcon.CAKE to "Gâteau", FolderIcon.RESTAURANT to "Cuisine", FolderIcon.PARTY to "Fête", FolderIcon.ROCKET to "Fusée",
    FolderIcon.MAGIC to "Magie", FolderIcon.BABY to "Bébé", FolderIcon.PETS to "Animaux", FolderIcon.SCHOOL to "École",
    FolderIcon.HEART to "Cœur", FolderIcon.STAR to "Étoile", FolderIcon.FOLDER to "Dossier", FolderIcon.HOME to "Maison",
    FolderIcon.TRAVEL to "Voyage", FolderIcon.PARK to "Parc", FolderIcon.GAMES to "Jeux", FolderIcon.COFFEE to "Goûter",
    FolderIcon.CANDLE to "Veillée", FolderIcon.PIRATE to "Aventure", FolderIcon.SHOPPING to "Courses", FolderIcon.WORK to "Travail",
    FolderIcon.BOOKMARK to "Signet",
)

private val glyphCache = HashMap<FolderIcon, ImageVector>()

/** Icône « trait » d'un dossier (viewBox 24, trait 1.9, bouts arrondis). */
fun folderGlyph(icon: FolderIcon): ImageVector = glyphCache.getOrPut(icon) {
    ImageVector.Builder(name = "folder_${icon.name}", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        .addPath(
            pathData = addPathNodes(GLYPHS.getValue(icon)),
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.9f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
        .build()
}

// ── Couvertures ───────────────────────────────────────────────────────────────

private val pathCache = HashMap<String, Path>()
private fun path(d: String): Path = pathCache.getOrPut(d) { PathParser().parsePathString(d).toPath() }

/** Dessine une scène 350×110 en « cover » (preserveAspectRatio xMidYMid slice). */
private fun DrawScope.slice(block: DrawScope.() -> Unit) {
    val s = max(size.width / 350f, size.height / 110f)
    clipRect {
        withTransform({
            translate((size.width - 350f * s) / 2f, (size.height - 110f * s) / 2f)
            scale(s, s, pivot = Offset.Zero)
        }, block)
    }
}

private fun DrawScope.drawShapes(shapes: List<Any>, bg: Color, mid: Color, measurer: TextMeasurer) {
    fun color(c: Long) = when (c) {
        BG -> bg
        MID -> mid
        else -> Color(c.toInt())
    }
    for (sh in shapes) {
        val rot = (sh as? S)?.rot ?: (sh as? T)?.rot
        val draw: DrawScope.() -> Unit = {
            when (sh) {
                is S -> {
                    val p = path(sh.d)
                    if (sh.fill != 0L) drawPath(p, color(sh.fill), alpha = sh.alpha)
                    if (sh.stroke != 0L) drawPath(p, color(sh.stroke), alpha = sh.alpha, style = Stroke(sh.sw, cap = if (sh.round) StrokeCap.Round else StrokeCap.Butt))
                }
                is T -> {
                    val layout = measurer.measure(sh.text, TextStyle(fontSize = sh.size.toSp(), fontWeight = FontWeight.ExtraBold, color = color(sh.fill)))
                    drawText(layout, topLeft = Offset(sh.x - layout.size.width / 2f, sh.y - layout.firstBaseline))
                }
            }
        }
        if (rot != null) rotate(rot[0], pivot = Offset(rot[1], rot[2]), block = draw) else draw()
    }
}

/** Dessin d'une bannière (maquette « Bannières illustrées ») aux couleurs du dossier. */
@Composable
fun IllustrationArt(illustration: String, color: FolderColor, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val bg = folderBg(color)
    val mid = folderMid(color)
    Canvas(modifier.fillMaxSize()) {
        val shapes = ILLUSTRATION_SHAPES[illustration] ?: return@Canvas
        slice { drawShapes(shapes, bg, mid, measurer) }
    }
}

// Tipis semés sur la couverture « Tipis » : x, y, taille, rotation (repère 350×110)
private val TIPIS = listOf(
    floatArrayOf(18f, 14f, 26f, -8f), floatArrayOf(78f, 54f, 20f, 6f), floatArrayOf(140f, 10f, 30f, 0f), floatArrayOf(206f, 50f, 22f, -5f),
    floatArrayOf(262f, 16f, 26f, 8f), floatArrayOf(320f, 58f, 20f, 0f), floatArrayOf(40f, 84f, 18f, 4f), floatArrayOf(300f, 92f, 16f, -6f),
)

/**
 * Fond d'une couverture de dossier, qui remplit son conteneur : dessin, uni,
 * tipis, pois, rayures ou photo (sans photo disponible : tipis).
 */
@Composable
fun CoverArt(cover: String, illustration: String?, color: FolderColor, photo: String? = null, modifier: Modifier = Modifier) {
    val bg = folderBg(color)
    val mid = folderMid(color)
    Box(modifier.fillMaxSize().background(bg)) {
        when {
            cover == "illu" && illustration != null -> IllustrationArt(illustration, color)
            cover == "photo" && photo != null -> AsyncImage(photo, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            cover == "pois" -> Canvas(Modifier.fillMaxSize()) {
                val step = 16.dp.toPx()
                val r = 2.9.dp.toPx()
                var y = step / 2
                while (y < size.height + step) {
                    var x = step / 2
                    while (x < size.width + step) { drawCircle(mid, r, Offset(x, y)); x += step }
                    y += step
                }
            }
            cover == "rayures" -> Canvas(Modifier.fillMaxSize()) {
                clipRect {
                    val gap = 16.dp.toPx() * sqrt(2f)
                    var x = -size.height
                    while (x < size.width + size.height) {
                        drawLine(mid, Offset(x, 0f), Offset(x + size.height, size.height), strokeWidth = 7.dp.toPx())
                        x += gap
                    }
                }
            }
            cover == "tipis" || cover == "photo" -> Canvas(Modifier.fillMaxSize()) {
                val outer = path("M12 2L22 20H2Z")
                val inner = path("M12 11l4.5 9h-9z")
                slice {
                    TIPIS.forEach { (x, y, s, r) ->
                        withTransform({
                            translate(x, y)
                            rotate(r, pivot = Offset(s / 2, s / 2))
                            scale(s / 24f, s / 24f, pivot = Offset.Zero)
                        }) {
                            drawPath(outer, mid)
                            drawPath(inner, bg)
                        }
                    }
                }
            }
        }
    }
}

/** Couverture effective d'un dossier (choisie, ou dessin auto selon le nom / la date clé). */
@Composable
fun FolderCover(folder: Folder, links: List<Link> = emptyList(), modifier: Modifier = Modifier) {
    val (cover, illustration) = FolderStyle.resolvedCover(folder)
    CoverArt(cover, illustration, folder.color, if (cover == "photo") FolderStyle.coverPhoto(folder, links) else null, modifier)
}
