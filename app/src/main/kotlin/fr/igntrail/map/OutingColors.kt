package fr.igntrail.map

import fr.igntrail.core.stats.SpeedChunker
import fr.igntrail.core.stats.SpeedThresholds
import fr.igntrail.core.tracking.TrackRecorder
import fr.igntrail.data.storage.SessionSummary
import fr.igntrail.data.storage.TrackRepository

/**
 * Couleurs du tracé d'une sortie (§7, choix de l'utilisateur) : aucune pendant la sortie,
 * calculées à la fin par rapport à la moyenne de cette sortie, puis enregistrées et figées.
 */
object OutingColors {
    fun compute(recorder: TrackRecorder, end: Long): SpeedThresholds? =
        SpeedThresholds.forOuting(SpeedChunker.chunks(recorder.timedSegments()), recorder.stats(end).avgMovingSpeedMps)

    fun stored(summary: SessionSummary): SpeedThresholds? =
        summary.colors?.let { (slow, usual, fast) -> runCatching { SpeedThresholds(slow, usual, fast) }.getOrNull() }

    /** Couleurs enregistrées ; pour une sortie antérieure à ce fonctionnement, calculées une fois et enregistrées. */
    suspend fun forSession(summary: SessionSummary, recorder: TrackRecorder, end: Long, repository: TrackRepository): SpeedThresholds? =
        stored(summary) ?: compute(recorder, end)?.also { repository.saveColors(summary.id, it.slowMps, it.usualMps, it.fastMps) }
}
