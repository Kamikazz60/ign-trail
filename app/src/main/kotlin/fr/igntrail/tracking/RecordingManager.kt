package fr.igntrail.tracking

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import fr.igntrail.core.model.ActivityType
import fr.igntrail.core.model.GeoPoint
import fr.igntrail.core.model.LocationFix
import fr.igntrail.core.stats.SpeedThresholds
import fr.igntrail.core.tracking.RecordedFix
import fr.igntrail.core.tracking.RecordingStats
import fr.igntrail.core.tracking.SessionEvent
import fr.igntrail.core.tracking.SessionEventType
import fr.igntrail.core.tracking.TimedPoint
import fr.igntrail.core.tracking.TrackRecorder
import fr.igntrail.core.tracking.TrackingParams
import fr.igntrail.data.settings.RecordingPreferences
import fr.igntrail.data.storage.SessionSummary
import fr.igntrail.data.storage.TrackRepository
import fr.igntrail.map.OutingColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Session en cours d'enregistrement. */
data class ActiveSession(val id: Long, val name: String, val activity: ActivityType, val startMillis: Long, val activityLabel: String? = null)

/** Segments du tracé ; [version] change à chaque nouveau point. */
data class TrackSnapshot(val version: Int, val timed: List<List<TimedPoint>>) {
    val segments: List<List<GeoPoint>> get() = timed.map { seg -> seg.map { it.point } }

    companion object {
        val EMPTY = TrackSnapshot(-1, emptyList())
    }
}

sealed interface RecordingStatus {
    /** Vérification d'une éventuelle session interrompue au démarrage. */
    data object Loading : RecordingStatus

    data object Idle : RecordingStatus

    data class Active(val session: ActiveSession, val stats: RecordingStats, val track: TrackSnapshot) : RecordingStatus

    /** Session non terminée trouvée au démarrage (plantage, redémarrage du téléphone…). */
    data class Interrupted(val summary: SessionSummary, val lastDataMillis: Long) : RecordingStatus

    /** Session terminée, affichée jusqu'à ce que l'utilisateur ferme le résumé. */
    data class Finished(
        val summary: SessionSummary,
        val stats: RecordingStats,
        val track: TrackSnapshot,
        /** Couleurs du tracé, calculées à la fin d'après la moyenne de la sortie et enregistrées. */
        val colors: SpeedThresholds?,
    ) : RecordingStatus
}

/**
 * Chef d'orchestre de l'enregistrement (§5) : relie le moteur [TrackRecorder],
 * la base de données et le service au premier plan.
 *
 * - Toutes les mesures brutes sont écrites progressivement (par lots de 20 ou toutes
 *   les 10 s) : un arrêt brutal fait perdre au plus quelques secondes (§5.4).
 * - Après un arrêt anormal, la session est recalculée à partir des données brutes
 *   et peut être reprise ; la coupure est enregistrée comme interruption.
 */
class RecordingManager(
    private val context: Context,
    private val repository: TrackRepository,
    private val preferences: RecordingPreferences,
) {
    /** Un seul fil pour l'état : pas d'accès concurrent au moteur. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))
    private val mutex = Mutex()

    private val _status = MutableStateFlow<RecordingStatus>(RecordingStatus.Loading)
    val status: StateFlow<RecordingStatus> = _status.asStateFlow()

    private var recorder: TrackRecorder? = null
    private var session: ActiveSession? = null
    private var track = TrackSnapshot.EMPTY
    private val buffer = mutableListOf<RecordedFix>()
    private var lastFlushMillis = 0L
    private var lastStatsSaveMillis = 0L

    init {
        scope.launch {
            mutex.withLock {
                val pending = runCatching { repository.activeSession() }.getOrNull()
                _status.value = if (pending == null) {
                    RecordingStatus.Idle
                } else {
                    RecordingStatus.Interrupted(pending, repository.lastDataTime(pending.id) ?: pending.startTime)
                }
            }
        }
    }

    suspend fun defaultActivity(): ActivityType = preferences.lastActivity() ?: ActivityType.HIKE

    suspend fun lastActivityLabel(): String? = preferences.lastLabel()

    suspend fun autoPauseSeconds(activity: ActivityType): Int =
        preferences.autoPauseSeconds(activity) ?: TrackingParams.defaultAutoPauseSeconds(activity)

    /** @param activityLabel nom libre pour l'activité « Autre » (ski, roller…). */
    fun start(activity: ActivityType, autoPauseSeconds: Int, activityLabel: String? = null) = scope.launch {
        mutex.withLock {
            val current = _status.value
            if (current != RecordingStatus.Idle && current !is RecordingStatus.Finished) return@withLock
            val now = System.currentTimeMillis()
            val label = activityLabel?.trim()?.takeIf { activity == ActivityType.OTHER && it.isNotEmpty() }
            val name = defaultName(activity, label, now)
            val id = repository.createSession(name, activity, autoPauseSeconds, now, label)
            preferences.remember(activity, autoPauseSeconds, label)
            recorder = TrackRecorder(TrackingParams.forActivity(activity, autoPauseSeconds), now)
            session = ActiveSession(id, name, activity, now, label)
            track = TrackSnapshot.EMPTY
            buffer.clear()
            lastFlushMillis = now
            publish(now)
            startService()
        }
    }

    /** Appelé par le service pour chaque mesure GPS. */
    suspend fun onFix(fix: LocationFix) = mutex.withLock {
        val rec = recorder ?: return@withLock
        val quality = rec.process(fix)
        buffer += RecordedFix(fix, quality, rec.state)
        val now = System.currentTimeMillis()
        publish(now)
        if (buffer.size >= FLUSH_EVERY_POINTS || now - lastFlushMillis >= FLUSH_EVERY_MS) flush(now)
    }

    fun pause() = command(SessionEventType.MANUAL_PAUSE) { rec, t -> rec.pauseManually(t) }

    fun resume() = command(SessionEventType.MANUAL_RESUME) { rec, t -> rec.resumeManually(t) }

    private fun command(type: SessionEventType, action: (TrackRecorder, Long) -> Unit) = scope.launch {
        mutex.withLock {
            val rec = recorder ?: return@withLock
            val id = session?.id ?: return@withLock
            val now = System.currentTimeMillis()
            val before = rec.state
            action(rec, now)
            if (rec.state == before) return@withLock
            flush(now)
            repository.appendEvent(id, SessionEvent(now, type))
            publish(now)
        }
    }

    /** Termine et enregistre la randonnée en cours. */
    fun stop() = scope.launch {
        mutex.withLock {
            val rec = recorder ?: return@withLock
            val s = session ?: return@withLock
            val now = System.currentTimeMillis()
            rec.finish(now)
            flush(now)
            repository.appendEvent(s.id, SessionEvent(now, SessionEventType.STOP))
            finalize(s.id, rec, now)
        }
    }

    /** Supprime la randonnée en cours (après confirmation de l'utilisateur). */
    fun discard() = scope.launch {
        mutex.withLock {
            val id = session?.id ?: (status.value as? RecordingStatus.Interrupted)?.summary?.id ?: return@withLock
            buffer.clear()
            recorder = null
            session = null
            repository.delete(id)
            _status.value = RecordingStatus.Idle
        }
    }

    /** Reprend une session interrompue : recalcul depuis les données brutes. */
    fun resumeInterrupted() = scope.launch {
        if (resumeInterruptedLocked()) startService()
    }

    /**
     * Redémarrage automatique du service par le système (START_STICKY).
     * @return vrai si un enregistrement est en cours ou a été repris.
     */
    suspend fun onServiceRestarted(): Boolean {
        val current = status.first { it != RecordingStatus.Loading }
        return when (current) {
            is RecordingStatus.Active -> true
            is RecordingStatus.Interrupted -> resumeInterruptedLocked()
            else -> false
        }
    }

    private suspend fun resumeInterruptedLocked(): Boolean = mutex.withLock {
        val pending = status.value as? RecordingStatus.Interrupted ?: return@withLock false
        val data = repository.load(pending.summary.id) ?: return@withLock false
        val summary = data.summary
        val params = TrackingParams.forActivity(summary.activity, summary.autoPauseSeconds)
        val rec = TrackRecorder.replay(params, summary.startTime, data.fixes, data.events)
        rec.interrupt(pending.lastDataMillis)
        repository.appendEvent(summary.id, SessionEvent(pending.lastDataMillis, SessionEventType.INTERRUPTED))
        val now = System.currentTimeMillis()
        rec.resumeAfterInterruption(now)
        repository.appendEvent(summary.id, SessionEvent(now, SessionEventType.RESUMED_AFTER_INTERRUPTION))
        recorder = rec
        session = ActiveSession(summary.id, summary.name, summary.activity, summary.startTime, summary.activityLabel)
        track = TrackSnapshot.EMPTY
        buffer.clear()
        lastFlushMillis = now
        publish(now)
        true
    }

    /** Termine une session interrompue à l'heure de sa dernière donnée. */
    fun finishInterrupted() = scope.launch {
        mutex.withLock {
            val pending = status.value as? RecordingStatus.Interrupted ?: return@withLock
            val data = repository.load(pending.summary.id) ?: return@withLock
            val summary = data.summary
            val params = TrackingParams.forActivity(summary.activity, summary.autoPauseSeconds)
            val rec = TrackRecorder.replay(params, summary.startTime, data.fixes, data.events)
            val end = pending.lastDataMillis
            rec.finish(end)
            repository.appendEvent(summary.id, SessionEvent(end, SessionEventType.STOP))
            finalize(summary.id, rec, end)
        }
    }

    /** Tracé (segments filtrés) d'une randonnée enregistrée, recalculé depuis les données brutes. */
    suspend fun segmentsOf(sessionId: Long): List<List<GeoPoint>> = replay(sessionId)?.segments().orEmpty()

    /**
     * Recalcule une randonnée enregistrée à partir de ses données brutes (§13 :
     * statistiques recalculables). Null si elle n'existe plus.
     */
    suspend fun replay(sessionId: Long): TrackRecorder? = withContext(Dispatchers.Default) {
        val data = repository.load(sessionId) ?: return@withContext null
        val s = data.summary
        TrackRecorder.replay(TrackingParams.forActivity(s.activity, s.autoPauseSeconds), s.startTime, data.fixes, data.events)
    }

    fun acknowledgeFinished() {
        _status.update { if (it is RecordingStatus.Finished) RecordingStatus.Idle else it }
    }

    fun rename(sessionId: Long, name: String) = scope.launch {
        repository.rename(sessionId, name)
        val finished = _status.value as? RecordingStatus.Finished
        if (finished != null && finished.summary.id == sessionId) {
            _status.value = finished.copy(summary = finished.summary.copy(name = name))
        }
    }

    /** Écrit immédiatement les mesures en attente (le service va s'arrêter). */
    fun flushNow() = scope.launch { mutex.withLock { flush(System.currentTimeMillis()) } }

    private suspend fun finalize(id: Long, rec: TrackRecorder, end: Long) {
        val stats = rec.stats(end)
        repository.saveStats(id, stats, finished = true, endTime = end)
        // Couleurs calculées une seule fois, ici, puis figées (§7, choix de l'utilisateur).
        val colors = OutingColors.compute(rec, end)
        colors?.let { repository.saveColors(id, it.slowMps, it.usualMps, it.fastMps) }
        val summary = repository.load(id)?.summary
        val snapshot = TrackSnapshot(rec.trackVersion, rec.timedSegments())
        recorder = null
        session = null
        buffer.clear()
        _status.value = if (summary != null) RecordingStatus.Finished(summary, stats, snapshot, colors) else RecordingStatus.Idle
    }

    private suspend fun flush(now: Long) {
        val id = session?.id ?: return
        if (buffer.isNotEmpty()) {
            val batch = buffer.toList()
            try {
                repository.appendPoints(id, batch)
                buffer.clear()
            } catch (e: Exception) {
                // On garde le lot en mémoire et on réessaiera au prochain point.
                Log.e(TAG, "Écriture des points impossible", e)
            }
        }
        lastFlushMillis = now
        val rec = recorder ?: return
        if (now - lastStatsSaveMillis >= SAVE_STATS_EVERY_MS) {
            lastStatsSaveMillis = now
            runCatching { repository.saveStats(id, rec.stats(now), finished = false, endTime = null) }
        }
    }

    private fun publish(now: Long) {
        val rec = recorder ?: return
        val s = session ?: return
        if (rec.trackVersion != track.version) track = TrackSnapshot(rec.trackVersion, rec.timedSegments())
        _status.value = RecordingStatus.Active(s, rec.stats(now), track)
    }

    private fun startService() {
        ContextCompat.startForegroundService(
            context,
            Intent(context, TrackingService::class.java).setAction(TrackingService.ACTION_RECORD),
        )
    }

    private fun defaultName(activity: ActivityType, label: String?, now: Long): String {
        val date = SimpleDateFormat("d MMMM yyyy", Locale.FRANCE).format(Date(now))
        if (label != null) return "${label.replaceFirstChar { it.uppercase() }} du $date"
        return when (activity) {
            ActivityType.WALK -> "Marche du $date"
            ActivityType.HIKE -> "Randonnée du $date"
            ActivityType.RUN -> "Course du $date"
            ActivityType.OTHER -> "Activité du $date"
        }
    }

    companion object {
        private const val TAG = "RecordingManager"
        private const val FLUSH_EVERY_POINTS = 20
        private const val FLUSH_EVERY_MS = 10_000L
        private const val SAVE_STATS_EVERY_MS = 60_000L
    }
}
