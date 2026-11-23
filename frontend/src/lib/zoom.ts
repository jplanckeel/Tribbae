// Image en plein écran : zoom (pincer, double-toucher, molette) et déplacement.
// Même logique sur Android (data/Zoom.kt) : zoom.test.ts ↔ ZoomTest.

export const MIN_SCALE = 1;
export const MAX_SCALE = 4;
/** Zoom d'un double-toucher depuis l'image entière. */
export const TAP_SCALE = 2.5;

export const clampScale = (s: number) => Math.min(MAX_SCALE, Math.max(MIN_SCALE, s));

/** Double-toucher : agrandit l'image entière, ou revient à l'image entière. */
export const toggleScale = (s: number) => (s > 1.01 ? 1 : TAP_SCALE);

/**
 * Déplacement permis pour une image agrandie [scale] fois dans une zone de
 * [w]×[h] : jamais au-delà de ses bords (une image entière ne bouge pas).
 */
export function clampPan(x: number, y: number, scale: number, w: number, h: number): { x: number; y: number } {
  const mx = ((scale - 1) * w) / 2;
  const my = ((scale - 1) * h) / 2;
  const c = (v: number, m: number) => (m <= 0 ? 0 : Math.min(m, Math.max(-m, v)));
  return { x: c(x, mx), y: c(y, my) };
}
