package fr.igntrail.offline

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.getSystemService
import fr.igntrail.MainActivity
import fr.igntrail.R
import fr.igntrail.appContainer
import fr.igntrail.ui.format.Format
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Service au premier plan (« synchronisation de données ») pendant les téléchargements
 * de cartes : ils continuent si l'utilisateur quitte l'application.
 */
class OfflineDownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannel(this)
        val started = runCatching {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(0f, 0L), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        }.isSuccess
        if (!started) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (job?.isActive != true) {
            job = scope.launch {
                appContainer.offlineMaps.regions.collect { regions ->
                    val active = regions.filter { it.state is DownloadState.Downloading }
                    if (active.isEmpty()) {
                        ServiceCompat.stopForeground(this@OfflineDownloadService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    } else {
                        val done = active.sumOf { it.completedResources }
                        val total = active.sumOf { it.requiredResources }.coerceAtLeast(1)
                        val progress = done.toFloat() / total
                        runCatching {
                            NotificationManagerCompat.from(this@OfflineDownloadService)
                                .notify(NOTIFICATION_ID, notification(progress, active.sumOf { it.sizeBytes }))
                        }
                    }
                }
            }
        }
        // Pas de relance automatique : la reprise se fait à la prochaine ouverture de l'application.
        return START_NOT_STICKY
    }

    /** Android 15 limite la durée de ce type de service : on s'arrête proprement. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        stopSelf()
    }

    private fun notification(progress: Float, bytes: Long) = NotificationCompat.Builder(this, CHANNEL)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle("Téléchargement des cartes")
        .setContentText("${(progress * 100).toInt()} % · ${Format.bytes(bytes)}")
        .setProgress(100, (progress * 100).toInt(), progress == 0f)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setSilent(true)
        .setContentIntent(
            PendingIntent.getActivity(this, 1, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE),
        )
        .build()

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val NOTIFICATION_ID = 3
        private const val CHANNEL = "offline_download"

        fun createChannel(context: Context) {
            context.getSystemService<NotificationManager>()?.createNotificationChannel(
                NotificationChannel(CHANNEL, "Téléchargement des cartes", NotificationManager.IMPORTANCE_LOW),
            )
        }
    }
}
