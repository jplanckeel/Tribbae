/** Petite vibration quand on aime (téléphones qui la permettent). */
export function likeFeedback() {
  try {
    navigator.vibrate?.(12);
  } catch {
    // Vibration indisponible : l'animation suffit
  }
}
