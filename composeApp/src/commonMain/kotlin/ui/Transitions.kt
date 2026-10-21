package ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned

// Transitions d'écran (maquette « Transitions & navigation »), mêmes valeurs que
// le web (frontend/src/lib/transitions.ts + index.css) :
//  - ouvrir une idée : sa carte devient la fiche (AppModern, IdeaOpenTransition) ;
//  - creuser : la page arrive par la droite, la précédente recule de 30 % et s'assombrit ;
//  - changer d'onglet : fondu + 26 dp dans le sens de l'onglet.

/** Courbe « douce » des glissements (cubic-bezier(.2,.8,.2,1)). */
val EaseSoft = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)

/** Courbe de la carte qui grandit, avec un léger dépassement (cubic-bezier(.3,1.12,.4,1)). */
val EaseHero = CubicBezierEasing(0.3f, 1.12f, 0.4f, 1f)

const val HERO_MS = 440
const val PUSH_MS = 380
const val TAB_MS = 280

/**
 * Dernière position à l'écran du visuel de chaque idée, pour que sa fiche
 * s'ouvre depuis la carte touchée. Simple cache (pas un état observé).
 */
object CardOrigins {
    private val bounds = HashMap<String, Rect>()
    operator fun get(id: String): Rect? = bounds[id]
    internal fun set(id: String, r: Rect) { bounds[id] = r }
}

/** Enregistre la position du visuel de l'idée `id` (cartes de l'accueil, Explorer, dossiers…). */
fun Modifier.cardOrigin(id: String): Modifier = onGloballyPositioned { c ->
    val r = c.boundsInRoot()
    if (r.width > 0f && r.height > 0f) CardOrigins.set(id, r)
}

/**
 * Avancement de l'ouverture de la fiche affichée (0 = carte, 1 = fiche) : la
 * fiche fait arriver ses infos et ses boutons après l'en-tête. 1 hors ouverture.
 */
val LocalOpenProgress = compositionLocalOf<() -> Float> { { 1f } }

/** Part de l'ouverture après laquelle les infos arrivent (120 ms sur 440). */
fun lateProgress(p: Float): Float = ((p - 120f / HERO_MS) / (1f - 120f / HERO_MS)).coerceIn(0f, 1f)
