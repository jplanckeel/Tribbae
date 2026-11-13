package data

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

object NotificationHelper {

    const val CHANNEL_ID = "tribbae_reminders"
    const val CHANNEL_NAME = "Rappels"

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID, CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Rappels pour vos événements et activités"
        }
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
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
    fun reconcile(context: Context, links: List<Link>, reminders: Boolean = true, donePrompts: Boolean = true, now: Long = System.currentTimeMillis()) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val before = prefs.getStringSet(KEY_SCHEDULED, emptySet()).orEmpty()
        // Interrupteur des Réglages éteint : aucune alarme (celles déjà posées sont annulées)
        val plan = Reminders.plan(if (reminders) links else emptyList(), before, now)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        plan.cancel.forEach { alarmManager.cancel(pendingIntent(context, it, null)) }
        plan.schedule.forEach { a ->
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, a.at, pendingIntent(context, a.linkId, a.title))
        }

        // « Vous y êtes allés ? » : le lendemain d'une sortie datée, à 10 h (data.DonePrompts)
        val asked = prefs.getStringSet(KEY_DONE_PROMPTS, emptySet()).orEmpty()
        val prompts = DonePrompts.plan(if (donePrompts) links else emptyList(), asked, now)
        prompts.cancel.forEach { alarmManager.cancel(donePromptIntent(context, it, null)) }
        // Question déjà affichée mais résolue entre-temps : la notification part
        val shown = androidx.core.app.NotificationManagerCompat.from(context)
        DonePrompts.answered(links, now).forEach { shown.cancel(DonePrompts.notificationId(it)) }
        prompts.schedule.forEach { a -> alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, a.at, donePromptIntent(context, a.linkId, a)) }

        prefs.edit()
            .putStringSet(KEY_SCHEDULED, plan.schedule.map { it.linkId }.toSet())
            .putStringSet(KEY_DONE_PROMPTS, prompts.schedule.map { it.linkId }.toSet())
            .apply()
    }

    /** Alarme de la question du lendemain : même récepteur, action à part pour ne pas remplacer le rappel de la veille. */
    private fun donePromptIntent(context: Context, linkId: String, alarm: DonePrompts.Alarm?): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_DONE_PROMPT
            putExtra("link_id", linkId)
            alarm?.let {
                putExtra("link_title", it.title)
                putExtra("link_category", it.category.name)
                putExtra("event_at", it.eventAt)
            }
        }
        return PendingIntent.getBroadcast(
            context, "done:$linkId".hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    const val ACTION_DONE_PROMPT = "fr.tribbae.app.DONE_PROMPT"
    /** « Pas cette fois », depuis la notification */
    const val ACTION_DONE_DISMISS = "fr.tribbae.app.DONE_DISMISS"
    /** « Oui, on y était » : ouvre la fiche sur la feuille souvenir (extra de MainActivity) */
    const val EXTRA_DONE_LINK = "done_link_id"

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
    private const val KEY_DONE_PROMPTS = "done_prompts"
}
