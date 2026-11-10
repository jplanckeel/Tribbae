package data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Interrupteur général des rappels « la veille » (Réglages), retenu sur
 * l'appareil. Éteint, aucune alarme n'est posée ; le choix idée par idée est
 * gardé et revient quand on le rallume.
 */
object ReminderPrefs {
    var enabled by mutableStateOf(true)
    /** Enregistre le choix sur l'appareil (branché par MainActivity). */
    var save: (Boolean) -> Unit = {}

    fun set(value: Boolean) {
        enabled = value
        save(value)
    }
}
