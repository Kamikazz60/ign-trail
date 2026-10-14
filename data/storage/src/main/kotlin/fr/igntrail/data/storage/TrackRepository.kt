package fr.igntrail.data.storage

import androidx.room.withTransaction
import fr.igntrail.core.model.ActivityType
import fr.igntrail.core.model.LocationFix
import fr.igntrail.core.model.LocationSourceType
import fr.igntrail.core.tracking.RecordedFix
import fr.igntrail.core.tracking.RecordingStats
import fr.igntrail.core.tracking.SessionEvent
import fr.igntrail.core.tracking.SessionEventType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Résumé d'une session pour les listes et la reprise. */
data class SessionSummary(
    val id: Long,
    val name: String,
    val activity: ActivityType,
    val autoPauseSeconds: Int,
    val startTime: Long,
    val endTime: Long?,
    val distanceM: Double,
    val movingMs: Long,
    val pausedMs: Long,
    val interruptedMs: Long,
    val elevationGainM: Double,
    val elevationLossM: Double,
    /** Nom libre pour l'activité « Autre » (ski, roller…). */
    val activityLabel: String? = null,
    /** Seuils de couleur figés (lent, moyen, rapide en m/s) ; null si pas encore calculés. */
    val colors: Triple<Double, Double, Double>? = null,
) {
    /** Libellé affiché : nom personnalisé, sinon le type d'activité. */
    val activityName: String get() = activityLabel ?: activity.label
}

/** Données brutes complètes d'une session, pour la recalculer. */
class SessionData(val summary: SessionSummary, val fixes: List<LocationFix>, val events: List<SessionEvent>)

class TrackRepository(private val db: TrailDatabase) {
    private val dao = db.trackDao()

    suspend fun createSession(name: String, activity: ActivityType, autoPauseSeconds: Int, startTime: Long, activityLabel: String? = null): Long =
        db.withTransaction {
            val id = dao.insertSession(
                SessionEntity(
                    name = name,
                    activity = activity.name,
                    autoPauseSeconds = autoPauseSeconds,
                    startTime = startTime,
                    status = STATUS_RECORDING,
                    activityLabel = activityLabel?.trim()?.takeIf { it.isNotEmpty() },
                ),
            )
            dao.insertEvent(SessionEventEntity(sessionId = id, time = startTime, type = SessionEventType.START.name))
            id
        }

    suspend fun appendPoints(sessionId: Long, points: List<RecordedFix>) {
        if (points.isEmpty()) return
        dao.insertPoints(points.map { it.toEntity(sessionId) })
    }

    suspend fun appendEvent(sessionId: Long, event: SessionEvent) {
        dao.insertEvent(SessionEventEntity(sessionId = sessionId, time = event.timeMillis, type = event.type.name))
    }

    /** Session non terminée (en cours, ou interrompue par un arrêt anormal). */
    suspend fun activeSession(): SessionSummary? = dao.activeSession()?.toSummary()

    suspend fun load(sessionId: Long): SessionData? {
        val session = dao.session(sessionId) ?: return null
        return SessionData(
            summary = session.toSummary(),
            fixes = dao.points(sessionId).map { it.toFix() },
            events = dao.events(sessionId).mapNotNull { e ->
                runCatching { SessionEvent(e.time, SessionEventType.valueOf(e.type)) }.getOrNull()
            },
        )
    }

    suspend fun lastDataTime(sessionId: Long): Long? {
        val lastPoint = dao.lastPointTime(sessionId)
        val lastEvent = dao.events(sessionId).maxOfOrNull { it.time }
        return listOfNotNull(lastPoint, lastEvent).maxOrNull()
    }

    /** Met à jour le cache de synthèse (pendant l'enregistrement et à la fin). */
    suspend fun saveStats(sessionId: Long, stats: RecordingStats, finished: Boolean, endTime: Long?) {
        val s = dao.session(sessionId) ?: return
        dao.updateSession(
            s.copy(
                status = if (finished) STATUS_FINISHED else STATUS_RECORDING,
                endTime = endTime ?: s.endTime,
                distanceM = stats.distanceM,
                movingMs = stats.movingMs,
                pausedMs = stats.pausedMs,
                interruptedMs = stats.interruptedMs,
                maxSpeedMps = stats.maxSpeedMps,
                elevationGainM = stats.elevationGainM,
                elevationLossM = stats.elevationLossM,
                minAltitudeM = stats.minAltitudeM,
                maxAltitudeM = stats.maxAltitudeM,
            ),
        )
    }

    fun finishedSessions(): Flow<List<SessionSummary>> = dao.finishedSessions().map { list -> list.map { it.toSummary() } }

    suspend fun rename(sessionId: Long, name: String) = dao.rename(sessionId, name)

    suspend fun saveColors(sessionId: Long, slow: Double, usual: Double, fast: Double) = dao.setColors(sessionId, slow, usual, fast)

    fun activityLabels(): Flow<List<String>> = dao.activityLabels()

    /** Base cohérente sur disque avant une copie de sauvegarde. */
    fun checkpoint() = db.checkpoint()

    suspend fun delete(sessionId: Long) = dao.deleteSession(sessionId)

    private fun SessionEntity.toSummary() = SessionSummary(
        id = id,
        name = name,
        activity = runCatching { ActivityType.valueOf(activity) }.getOrDefault(ActivityType.HIKE),
        autoPauseSeconds = autoPauseSeconds,
        startTime = startTime,
        endTime = endTime,
        distanceM = distanceM,
        movingMs = movingMs,
        pausedMs = pausedMs,
        interruptedMs = interruptedMs,
        elevationGainM = elevationGainM,
        elevationLossM = elevationLossM,
        activityLabel = activityLabel,
        colors = if (colorSlowMps != null && colorUsualMps != null && colorFastMps != null) Triple(colorSlowMps, colorUsualMps, colorFastMps) else null,
    )

    private fun RecordedFix.toEntity(sessionId: Long) = TrackPointEntity(
        sessionId = sessionId,
        time = fix.timeMillis,
        latitude = fix.latitude,
        longitude = fix.longitude,
        horizontalAccuracyM = fix.horizontalAccuracyM,
        altitude = fix.altitudeM,
        verticalAccuracyM = fix.verticalAccuracyM,
        speed = fix.speedMps,
        bearing = fix.bearingDeg,
        bearingAccuracyDeg = fix.bearingAccuracyDeg,
        source = fix.source.name,
        quality = quality.name,
        state = state.name,
        pressure = fix.pressureHpa,
    )

    private fun TrackPointEntity.toFix() = LocationFix(
        latitude = latitude,
        longitude = longitude,
        timeMillis = time,
        horizontalAccuracyM = horizontalAccuracyM,
        altitudeM = altitude,
        verticalAccuracyM = verticalAccuracyM,
        speedMps = speed,
        bearingDeg = bearing,
        bearingAccuracyDeg = bearingAccuracyDeg,
        source = runCatching { LocationSourceType.valueOf(source) }.getOrDefault(LocationSourceType.FUSED),
        pressureHpa = pressure,
    )

    companion object {
        const val STATUS_RECORDING = "RECORDING"
        const val STATUS_FINISHED = "FINISHED"
    }
}
