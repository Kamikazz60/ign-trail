package fr.igntrail.core.tracking

import fr.igntrail.core.model.ActivityType

/**
 * Paramètres de filtrage (§5.3). Volontairement peu agressifs : on préfère
 * garder un point douteux plutôt que d'effacer un virage réel.
 */
data class TrackingParams(
    /** Au-delà, la mesure est jugée trop imprécise pour le tracé. */
    val maxAccuracyM: Float = 35f,
    /** Vitesse physiquement plausible pour l'activité ; au-delà : point aberrant. */
    val maxSpeedMps: Double,
    /** Déplacement minimal pour ajouter un point, même avec une très bonne précision. */
    val minStepM: Double = 4.0,
    /** Plafond du rayon de bruit : au-delà, tout déplacement est considéré réel. */
    val maxStepM: Double = 15.0,
    /** Sans point accepté pendant cette durée : nouveau segment, pas de ligne droite. */
    val gapMs: Long = 60_000,
    /** Délai d'immobilité avant la pause automatique ; null = désactivée (§6.1). */
    val autoPauseDelayMs: Long?,
    /** Distance minimale au point d'arrêt pour reprendre automatiquement. */
    val autoResumeMinDistanceM: Double = 10.0,
    /** Mesures consécutives hors du rayon d'arrêt nécessaires pour reprendre. */
    val autoResumeFixes: Int = 2,
    /** Vitesse Doppler sous laquelle on est considéré immobile. */
    val stationarySpeedMps: Float = 0.4f,
    /** Fenêtre de calcul de la vitesse maximale filtrée. */
    val maxSpeedWindowMs: Long = 15_000,
    /** Seuil d'hystérésis du dénivelé (altitude GPS seule, §11). */
    val elevationThresholdM: Double = 8.0,
    /** Mesures d'altitude moins précises que cela ignorées. */
    val maxVerticalAccuracyM: Float = 20f,
    /** Bruit d'accélération du filtre de Kalman (m²/s³) : plus élevé = plus réactif. */
    val accelNoise: Double = 0.5,
    /** Signal jugé perdu sans mesure précise pendant cette durée. */
    val signalLostAfterMs: Long = 15_000,
) {
    companion object {
        /** Délais de pause automatique proposés à l'utilisateur, en secondes (0 = désactivée). */
        val AUTO_PAUSE_CHOICES_S = listOf(0, 10, 30, 60, 120)

        fun defaultAutoPauseSeconds(activity: ActivityType): Int = when (activity) {
            ActivityType.RUN -> 10
            ActivityType.WALK, ActivityType.HIKE, ActivityType.OTHER -> 30
        }

        fun forActivity(activity: ActivityType, autoPauseSeconds: Int): TrackingParams {
            val delay = autoPauseSeconds.takeIf { it > 0 }?.let { it * 1000L }
            return when (activity) {
                ActivityType.WALK -> TrackingParams(maxSpeedMps = 4.0, autoPauseDelayMs = delay)
                // Descentes rapides en randonnée : marge confortable.
                ActivityType.HIKE -> TrackingParams(maxSpeedMps = 5.0, autoPauseDelayMs = delay)
                ActivityType.RUN -> TrackingParams(maxSpeedMps = 9.0, minStepM = 5.0, accelNoise = 1.5, autoPauseDelayMs = delay)
                // Vitesse inconnue (vélo, ski…) : jusqu'à 90 km/h et filtre plus réactif.
                ActivityType.OTHER -> TrackingParams(maxSpeedMps = 25.0, minStepM = 5.0, accelNoise = 3.0, autoPauseDelayMs = delay)
            }
        }
    }
}
