package data

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.linkkeeper.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("link_title") ?: "Rappel"
        val linkId = intent.getStringExtra("link_id") ?: ""
        when (intent.action) {
            NotificationHelper.ACTION_DONE_PROMPT -> return askIfDone(context, intent, linkId, title)
            NotificationHelper.ACTION_DONE_DISMISS -> return dismissDonePrompt(context, linkId)
        }

        // Intent pour ouvrir l'app au clic
        val launchIntent = context.packageManager
            .getLaunchIntentForPackage(context.packageName)
            ?.apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP }
        val pendingLaunch = PendingIntent.getActivity(
            context, 0, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, NotificationHelper.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_tribbae)
            .setColor(0xFFC8492C.toInt()) // terracotta, dans le volet de notifications
            .setContentTitle("Rappel Tribbae")
            .setContentText("Demain : $title")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingLaunch)
            .build()

        try {
            NotificationManagerCompat.from(context)
                .notify(linkId.hashCode(), notification)
        } catch (_: SecurityException) {
            // Permission POST_NOTIFICATIONS non accordée
        }
    }

    /**
     * « Vous êtes allés à … ? » : le lendemain d'une sortie datée. Un toucher (ou
     * « Oui, on y était ») ouvre la fiche sur la feuille souvenir ; « Pas cette
     * fois » la laisse à faire, sans relance.
     */
    private fun askIfDone(context: Context, intent: Intent, linkId: String, title: String) {
        if (linkId.isEmpty()) return
        val category = runCatching { LinkCategory.valueOf(intent.getStringExtra("link_category").orEmpty()) }.getOrDefault(LinkCategory.EVENEMENT)
        val eventAt = intent.getLongExtra("event_at", 0)
        val link = Link(id = linkId, title = title, url = "", category = category, eventDate = eventAt.takeIf { it > 0 })
        val id = DonePrompts.notificationId(linkId)

        val open = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(NotificationHelper.EXTRA_DONE_LINK, linkId)
        }
        val yes = PendingIntent.getActivity(context, id, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val no = PendingIntent.getBroadcast(
            context, id,
            Intent(context, ReminderReceiver::class.java).setAction(NotificationHelper.ACTION_DONE_DISMISS).putExtra("link_id", linkId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, NotificationHelper.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_tribbae)
            .setColor(0xFFC8492C.toInt())
            .setContentTitle(Done.promptQuestion(category, title))
            .setContentText(DonePrompts.body(link, System.currentTimeMillis()))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(yes)
            .addAction(0, "Oui, on y était", yes)
            .addAction(0, "Pas cette fois", no)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (_: SecurityException) {
            // Permission POST_NOTIFICATIONS non accordée : le bandeau du dossier posera la question
        }
    }

    /** « Pas cette fois » : la notification part, l'idée reste à faire et on ne redemande plus. */
    private fun dismissDonePrompt(context: Context, linkId: String) {
        if (linkId.isEmpty()) return
        NotificationManagerCompat.from(context).cancel(DonePrompts.notificationId(linkId))
        val repository = AppGraph.repository(context)
        repository.links.value.find { it.id == linkId }?.let { repository.updateLink(it.copy(donePromptDismissed = true)) }
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Sans réseau, la réponse n'atteint pas le serveur : le dossier reposera la question
                AuthenticatedApiClient(sessionManager = SessionManager(context)).dismissDonePrompt(linkId)
            } catch (_: Exception) {
            } finally {
                pending.finish()
            }
        }
    }
}
