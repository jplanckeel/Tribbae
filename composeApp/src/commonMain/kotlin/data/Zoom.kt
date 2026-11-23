package data

/**
 * Image en plein écran : zoom (pincer, double-toucher) et déplacement. Même
 * logique sur le web (frontend/src/lib/zoom.ts) : ZoomTest ↔ zoom.test.ts.
 */
object Zoom {
    const val MIN_SCALE = 1f
    const val MAX_SCALE = 4f
    /** Zoom d'un double-toucher depuis l'image entière. */
    const val TAP_SCALE = 2.5f

    fun clampScale(s: Float) = s.coerceIn(MIN_SCALE, MAX_SCALE)

    /** Double-toucher : agrandit l'image entière, ou revient à l'image entière. */
    fun toggleScale(s: Float) = if (s > 1.01f) 1f else TAP_SCALE

    /**
     * Déplacement permis pour une image agrandie [scale] fois dans une zone de
     * [w]×[h] : jamais au-delà de ses bords (une image entière ne bouge pas).
     */
    fun clampPan(x: Float, y: Float, scale: Float, w: Float, h: Float): Pair<Float, Float> {
        val mx = (scale - 1) * w / 2
        val my = (scale - 1) * h / 2
        fun c(v: Float, m: Float) = if (m <= 0f) 0f else v.coerceIn(-m, m)
        return c(x, mx) to c(y, my)
    }
}
