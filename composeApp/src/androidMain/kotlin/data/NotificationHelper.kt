package data

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

object NotificationHelper {

    const val CHANNEL_ID = "tribbae_reminders"
    const val CHANNEL_NAME = "Rappels"

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Rappels pour vos événements et activités"
            }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    /**
     * Pose les alarmes des rappels « la veille » d'après la liste des idées
     * (voir data.Reminders) et annule celles qui n'ont plus lieu d'être. Appelé
     * à chaque changement de la liste et au redémarrage du téléphone.
     *
     * Alarme inexacte : un rappel de la veille n'a pas besoin de tomber à la
     * minute, et les alarmes exactes demandent une permission spéciale
     * (USE_EXACT_ALARM est réservée par le Play Store aux réveils et agendas,
     * SCHEDULE_EXACT_ALARM est refusée par défaut depuis Android 14). Le
     * système peut la décaler de quelques minutes, y compris en veille.
     */
    @Synchronized
    fun reconcile(context: Context, links: List<Link>, now: Long = System.currentTimeMillis()) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val before = prefs.getStringSet(KEY_SCHEDULED, emptySet()).orEmpty()
        val plan = Reminders.plan(links, before, now)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        plan.cancel.forEach { alarmManager.cancel(pendingIntent(context, it, null)) }
        plan.schedule.forEach { a ->
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, a.at, pendingIntent(context, a.linkId, a.title))
        }
        prefs.edit().putStringSet(KEY_SCHEDULED, plan.schedule.map { it.linkId }.toSet()).apply()
    }

    /** Même requestCode et même Intent (les extras ne comptent pas) : remplace ou annule l'alarme de l'idée. */
    private fun pendingIntent(context: Context, linkId: String, title: String?): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("link_id", linkId)
            title?.let { putExtra("link_title", it) }
        }
        return PendingIntent.getBroadcast(
            context, linkId.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    // Alarmes posées (fichier de l'appareil, hors sauvegarde : les alarmes ne voyagent pas)
    private const val PREFS = "tribbae_reminders"
    private const val KEY_SCHEDULED = "scheduled"
}
