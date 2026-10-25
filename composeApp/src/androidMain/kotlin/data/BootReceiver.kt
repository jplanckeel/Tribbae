package data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Les alarmes ne survivent ni à un redémarrage du téléphone ni à une mise à
 * jour de l'app : on repose les rappels « la veille » d'après les idées
 * gardées sur l'appareil.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        NotificationHelper.reconcile(context, AndroidStorage(context).loadLinks())
    }
}
