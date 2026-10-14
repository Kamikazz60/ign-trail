package fr.igntrail.tracking

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import fr.igntrail.appContainer
import fr.igntrail.navigation.NavigationStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Service au premier plan de type « location » (§5.4) : l'enregistrement continue
 * écran verrouillé, application en arrière-plan ou hors connexion.
 *
 * Il ne contient aucune logique d'enregistrement : il reçoit les positions et les
 * transmet au [RecordingManager], et tient la notification à jour.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TrackingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var locationJob: Job? = null
    private var barometerJob: Job? = null
    private val locationInterval = MutableStateFlow(LOCATION_INTERVAL_MS)

    /** Hystérésis : passage rapide au-dessus de 30 km/h, retour sous 20 km/h. */
    private fun adaptInterval(speedMps: Float?) {
        val speed = speedMps ?: return
        when {
            speed > FAST_ABOVE_MPS -> locationInterval.value = FAST_INTERVAL_MS
            speed < NORMAL_BELOW_MPS -> locationInterval.value = LOCATION_INTERVAL_MS
        }
    }

    /** Dernière pression mesurée et son heure (le baromètre et le GPS ne sont pas synchrones). */
    @Volatile
    private var lastPressure: Pair<Float, Long>? = null
    private var statusJob: Job? = null
    private var lastNotifiedMillis = 0L
    private var lastNotifiedKey: Any? = null

    private val manager get() = appContainer.recordingManager
    private val navigation get() = appContainer.navigationManager

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> manager.pause()
            ACTION_RESUME -> manager.resume()
            ACTION_STOP_GUIDANCE -> navigation.stop()
        }
        if (!goForeground()) {
            // Android refuse le premier plan (redémarrage en arrière-plan) : on prévient l'utilisateur.
            TrackingNotifications.showInterrupted(this)
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent == null) {
            // Relancé par le système après l'arrêt du processus (START_STICKY).
            scope.launch { if (!manager.onServiceRestarted()) stopSelf() }
        }
        startCollecting()
        return START_STICKY
    }

    private fun goForeground(): Boolean = try {
        ServiceCompat.startForeground(
            this,
            TrackingNotifications.RECORDING_ID,
            TrackingNotifications.recording(this, manager.status.value as? RecordingStatus.Active, navigation.status.value),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
        )
        true
    } catch (e: Exception) {
        Log.w(TAG, "Passage au premier plan refusé", e)
        false
    }

    private fun startCollecting() {
        if (barometerJob?.isActive != true) {
            barometerJob = scope.launch {
                appContainer.barometer.pressures().collect { lastPressure = it to System.currentTimeMillis() }
            }
        }
        if (locationJob?.isActive != true) {
            locationJob = scope.launch {
                // Fréquence adaptée à la vitesse (§17) : une position par seconde à pied,
                // deux au-delà de 30 km/h pour suivre fidèlement les virages (lacets à vélo).
                locationInterval.flatMapLatest { appContainer.locationSource.updates(it) }
                    .catch { Log.e(TAG, "Positions indisponibles", it) }
                    .collect { raw ->
                        adaptInterval(raw.speedMps)
                        val pressure = lastPressure?.takeIf { System.currentTimeMillis() - it.second < PRESSURE_MAX_AGE_MS }?.first
                        val fix = if (pressure != null) raw.copy(pressureHpa = pressure) else raw
                        appContainer.liveFix.value = fix
                        manager.onFix(fix)
                        navigation.onFix(fix)
                    }
            }
        }
        if (statusJob?.isActive != true) {
            statusJob = scope.launch {
                // Lors d'une relance par le système, l'état est d'abord « interrompu » le temps
                // de la reprise : on ne s'arrête qu'après avoir vu une activité (enregistrement
                // ou guidage). Le service vit tant que l'un des deux est en cours.
                var sawActive = false
                combine(manager.status, navigation.status) { r, n -> r to n }.collect { (recording, guidance) ->
                    val active = recording as? RecordingStatus.Active
                    val guiding = guidance !is NavigationStatus.Idle
                    when {
                        active != null || guiding -> {
                            sawActive = true
                            updateNotification(active, guidance)
                        }
                        recording == RecordingStatus.Loading -> Unit
                        sawActive -> stopRecordingService()
                    }
                }
            }
        }
    }

    /** Notification rafraîchie au changement d'état ou toutes les 15 s, pas à chaque position. */
    private fun updateNotification(status: RecordingStatus.Active?, guidance: NavigationStatus) {
        val now = System.currentTimeMillis()
        val key = listOf(
            status?.stats?.state,
            guidance::class,
            (guidance as? NavigationStatus.Following)?.state?.offRoute,
            (guidance as? NavigationStatus.DirectionToStart)?.arrived,
        )
        if (key == lastNotifiedKey && now - lastNotifiedMillis < NOTIFICATION_REFRESH_MS) return
        lastNotifiedKey = key
        lastNotifiedMillis = now
        runCatching {
            NotificationManagerCompat.from(this)
                .notify(TrackingNotifications.RECORDING_ID, TrackingNotifications.recording(this, status, guidance))
        }
    }

    private fun stopRecordingService() {
        locationJob?.cancel()
        barometerJob?.cancel()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        manager.flushNow()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "TrackingService"
        const val ACTION_RECORD = "fr.igntrail.action.RECORD"
        const val ACTION_PAUSE = "fr.igntrail.action.PAUSE"
        const val ACTION_RESUME = "fr.igntrail.action.RESUME"
        const val ACTION_STOP_GUIDANCE = "fr.igntrail.action.STOP_GUIDANCE"
        private const val LOCATION_INTERVAL_MS = 1_000L
        private const val FAST_INTERVAL_MS = 500L
        private const val FAST_ABOVE_MPS = 8.3f
        private const val NORMAL_BELOW_MPS = 5.5f
        private const val NOTIFICATION_REFRESH_MS = 15_000L
        private const val PRESSURE_MAX_AGE_MS = 5_000L
    }
}
