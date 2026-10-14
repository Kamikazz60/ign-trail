package fr.igntrail.core.tracking

/**
 * Statistiques d'une session (§5.2), figées à l'instant [computedAtMillis].
 * Les durées en cours s'obtiennent avec [at] sans recalcul complet.
 */
data class RecordingStats(
    val state: RecordingState,
    val startMillis: Long,
    val computedAtMillis: Long,
    val distanceM: Double,
    val movingMs: Long,
    val pausedMs: Long,
    val interruptedMs: Long,
    /** Vitesse instantanée lissée ; 0 à l'arrêt. */
    val currentSpeedMps: Double,
    /** Vitesse maximale calculée sur une fenêtre glissante (insensible aux sauts GPS). */
    val maxSpeedMps: Double,
    val currentAltitudeM: Double?,
    val minAltitudeM: Double?,
    val maxAltitudeM: Double?,
    val elevationGainM: Double,
    val elevationLossM: Double,
    /** Nombre de mesures d'altitude exploitées. */
    val altitudeSamples: Int,
    /** Faux si aucune mesure précise récente pendant l'enregistrement. */
    val gpsSignalOk: Boolean,
    /** Dénivelés calculés avec le baromètre du téléphone. */
    val barometric: Boolean = false,
) {
    val totalMs: Long get() = movingMs + pausedMs + interruptedMs

    /** Vitesse moyenne en mouvement (m/s). */
    val avgMovingSpeedMps: Double get() = if (movingMs > 0) distanceM / (movingMs / 1000.0) else 0.0

    /** Vitesse moyenne sur le temps total (m/s). */
    val avgTotalSpeedMps: Double get() = if (totalMs > 0) distanceM / (totalMs / 1000.0) else 0.0

    /** Allure moyenne en mouvement, en secondes par kilomètre ; null si distance trop courte. */
    val paceSecondsPerKm: Double? get() = if (distanceM >= 50.0 && movingMs > 0) movingMs / distanceM else null

    /** Projection des chronomètres à [nowMillis] selon l'état courant. */
    fun at(nowMillis: Long): RecordingStats {
        val delta = (nowMillis - computedAtMillis).coerceAtLeast(0)
        if (delta == 0L) return this
        return when (state) {
            RecordingState.MOVING -> copy(movingMs = movingMs + delta, computedAtMillis = nowMillis)
            RecordingState.AUTO_PAUSED, RecordingState.MANUAL_PAUSED ->
                copy(pausedMs = pausedMs + delta, computedAtMillis = nowMillis)
            RecordingState.INTERRUPTED -> copy(interruptedMs = interruptedMs + delta, computedAtMillis = nowMillis)
            RecordingState.FINISHED -> this
        }
    }
}
