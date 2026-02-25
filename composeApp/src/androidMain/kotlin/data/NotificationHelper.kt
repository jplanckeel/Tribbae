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
     * Planifie un rappel 1 jour avant la date de l'événement.
     * Si la date est déjà passée ou dans moins de 24h, le rappel est immédiat.
     */
    fun scheduleReminder(context: Context, linkId: String, title: String, eventDate: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("link_id", linkId)
            putExtra("link_title", title)
        }
        val requestCode = linkId.hashCode()
        val pendingIntent = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Rappel 1 jour avant (24h)
        val triggerTime = eventDate - 24 * 60 * 60 * 1000L
        val now = System.currentTimeMillis()
        val finalTrigger = if (triggerTime > now) triggerTime else now + 5000L

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP, finalTrigger, pendingIntent
        )
    }

    fun cancelReminder(context: Context, linkId: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java)
        val requestCode = linkId.hashCode()
        val pendingIntent = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }
}
