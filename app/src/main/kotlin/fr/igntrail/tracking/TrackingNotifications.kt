package fr.igntrail.tracking

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import fr.igntrail.MainActivity
import fr.igntrail.R
import fr.igntrail.core.tracking.RecordingState
import fr.igntrail.navigation.NavigationStatus
import fr.igntrail.ui.format.Format

/** Notifications : enregistrement en cours (persistante) et randonnée interrompue. */
object TrackingNotifications {
    const val RECORDING_ID = 1
    private const val INTERRUPTED_ID = 2
    private const val CHANNEL_RECORDING = "recording"
    const val CHANNEL_ALERTS = "alerts"

    fun createChannels(context: Context) {
        val manager = context.getSystemService<NotificationManager>() ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_RECORDING, "Enregistrement en cours", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Affichée pendant l'enregistrement d'une randonnée"
                setShowBadge(false)
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ALERTS, "Alertes de randonnée", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Randonnée interrompue, à reprendre"
            },
        )
    }

    fun recording(context: Context, status: RecordingStatus.Active?, guidance: NavigationStatus = NavigationStatus.Idle): Notification {
        val stats = status?.stats
        val title = when {
            guidance is NavigationStatus.Following && guidance.state?.offRoute == true -> "Hors parcours"
            stats?.state == RecordingState.MANUAL_PAUSED -> "Randonnée en pause"
            stats?.state == RecordingState.AUTO_PAUSED -> "Pause automatique"
            stats != null -> "Randonnée en cours"
            else -> "Guidage en cours"
        }
        val recordingText = stats?.let {
            "${Format.distance(it.distanceM)} · D+ ${Format.meters(it.elevationGainM)}" +
                if (!it.gpsSignalOk) " · signal GPS faible" else ""
        }
        val guidanceText = when (guidance) {
            is NavigationStatus.Following -> guidance.state?.let { s ->
                if (s.offRoute) "à ${Format.distance(s.crossTrackM)} du tracé" else "reste ${Format.distance(s.remainingM)}"
            } ?: "recherche de la position…"
            is NavigationStatus.DirectionToStart -> guidance.direction?.let { "départ à ${Format.distance(it.distanceM)}" }
            NavigationStatus.Idle -> null
        }
        val text = listOfNotNull(recordingText, guidanceText).joinToString(" · ").ifEmpty { "Démarrage…" }
        val builder = NotificationCompat.Builder(context, CHANNEL_RECORDING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openApp(context))
        if (guidance !is NavigationStatus.Idle) {
            builder.addAction(0, "Arrêter le guidage", serviceAction(context, TrackingService.ACTION_STOP_GUIDANCE))
        }
        if (status != null) {
            // Chronomètre système : temps total, sans mise à jour de notre part chaque seconde.
            builder.setUsesChronometer(true).setWhen(status.session.startMillis).setShowWhen(true)
            if (stats?.state == RecordingState.MANUAL_PAUSED) {
                builder.addAction(0, "Reprendre", serviceAction(context, TrackingService.ACTION_RESUME))
            } else {
                builder.addAction(0, "Pause", serviceAction(context, TrackingService.ACTION_PAUSE))
            }
        }
        return builder.build()
    }

    /** Après un redémarrage du téléphone ou un arrêt du service (§5.4). */
    fun showInterrupted(context: Context) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Randonnée interrompue")
            .setContentText("La trace est conservée. Touchez pour reprendre l'enregistrement.")
            .setAutoCancel(true)
            .setContentIntent(openApp(context))
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(INTERRUPTED_ID, notification) }
    }

    fun cancelInterrupted(context: Context) {
        NotificationManagerCompat.from(context).cancel(INTERRUPTED_ID)
    }

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun serviceAction(context: Context, action: String): PendingIntent = PendingIntent.getService(
        context,
        action.hashCode(),
        Intent(context, TrackingService::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
