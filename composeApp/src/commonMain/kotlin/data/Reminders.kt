package data

/**
 * Rappels « la veille » : la liste des alarmes découle de la liste des idées.
 * Elle est recalculée à chaque changement (création, modification, idée reçue
 * d'un proche par la synchronisation, nouvel identifiant après envoi) et au
 * redémarrage du téléphone (les alarmes ne survivent pas à un redémarrage).
 * Mise en œuvre Android : NotificationHelper.reconcile.
 */
object Reminders {
    const val DAY_MS = 24L * 60 * 60 * 1000

    /**
     * Heure du rappel : la veille, à la même heure. Null si ce moment est déjà
     * passé (événement passé, ou dans moins de 24 h) : un rappel n'est jamais
     * envoyé en retard.
     */
    fun triggerAt(eventDate: Long?, now: Long): Long? = eventDate?.minus(DAY_MS)?.takeIf { it > now }

    data class Alarm(val linkId: String, val title: String, val at: Long)

    /** Alarmes à poser, et identifiants des alarmes posées avant qui n'ont plus lieu d'être. */
    data class Plan(val schedule: List<Alarm>, val cancel: Set<String>)

    fun plan(links: List<Link>, previouslyScheduled: Set<String>, now: Long): Plan {
        val schedule = links.mapNotNull { l ->
            if (!l.reminderEnabled) null else triggerAt(l.eventDate, now)?.let { Alarm(l.id, l.title, it) }
        }
        return Plan(schedule, previouslyScheduled - schedule.map { it.linkId }.toSet())
    }
}

/**
 * « Vous y êtes allés ? » : le lendemain d'une sortie ou d'une activité datée,
 * à 10 h, une seule notification demande si elle a été faite (voir data.Done).
 * Rien pour une idée déjà faite ou à laquelle on a répondu « Pas cette fois »,
 * et jamais en retard : passé 10 h le lendemain, seul le bandeau du dossier
 * pose la question. Mise en œuvre Android : NotificationHelper.reconcile.
 */
object DonePrompts {
    const val HOUR = 10

    /** Le lendemain du jour de l'événement, à 10 h (heure de l'appareil). */
    fun dayAfterAt10(eventMs: Long): Long = java.util.Calendar.getInstance().apply {
        timeInMillis = eventMs
        add(java.util.Calendar.DAY_OF_MONTH, 1)
        set(java.util.Calendar.HOUR_OF_DAY, HOUR); set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
    }.timeInMillis

    fun triggerAt(l: Link, now: Long): Long? {
        if (l.category != LinkCategory.ACTIVITE && l.category != LinkCategory.EVENEMENT) return null
        if (Done.isDone(l) || l.donePromptDismissed) return null
        val event = Done.eventMs(l) ?: return null
        return dayAfterAt10(event).takeIf { it > now }
    }

    data class Alarm(val linkId: String, val title: String, val category: LinkCategory, val eventAt: Long, val at: Long)

    /** Alarmes à poser, et identifiants des alarmes posées avant qui n'ont plus lieu d'être. */
    data class Plan(val schedule: List<Alarm>, val cancel: Set<String>)

    fun plan(links: List<Link>, previouslyScheduled: Set<String>, now: Long): Plan {
        val schedule = links.mapNotNull { l ->
            // Une idée pas encore reçue par le serveur ne peut pas être marquée depuis la notification
            if (SyncIds.isLocal(l.id)) null else triggerAt(l, now)?.let { Alarm(l.id, l.title, l.category, Done.eventMs(l) ?: 0, it) }
        }
        return Plan(schedule, previouslyScheduled - schedule.map { it.linkId }.toSet())
    }

    /** Une question ne reste affichée que deux semaines après la sortie (au-delà, l'alarme ne repart plus). */
    const val ANSWER_DAYS = 14

    /**
     * Questions déjà affichées à retirer : la sortie a été faite, ou on a répondu
     * « Pas cette fois » (sur cet appareil, sur le web ou par un autre membre de
     * la tribu). Seules les sorties récentes sont regardées.
     */
    fun answered(links: List<Link>, now: Long): Set<String> = links.filter { l ->
        (l.category == LinkCategory.ACTIVITE || l.category == LinkCategory.EVENEMENT) &&
            (Done.isDone(l) || l.donePromptDismissed) &&
            (Done.eventMs(l) ?: 0L) in (now - ANSWER_DAYS * Reminders.DAY_MS)..now
    }.mapTo(mutableSetOf()) { it.id }

    /** Identifiant de la notification d'une idée (le même que celui de son alarme). */
    fun notificationId(linkId: String): Int = "done:$linkId".hashCode()

    /** Texte de la notification : « C'était hier. Un toucher pour garder le souvenir. » */
    fun body(l: Link, now: Long): String {
        val day = Done.promptDay(l, now).replaceFirstChar { it.lowercase() }
        // « le 7 nov. » finit déjà par un point
        val whenText = if (day.firstOrNull()?.isDigit() == true) "le ${day.trimEnd('.')}" else day
        return "C'était $whenText. Un toucher pour garder le souvenir."
    }
}
