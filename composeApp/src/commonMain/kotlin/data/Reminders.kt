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
