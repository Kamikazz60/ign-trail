package fr.igntrail.core.tracking

import fr.igntrail.core.model.LocationFix

/** État de la session au moment d'une mesure (§5.2). */
enum class RecordingState {
    MOVING,
    AUTO_PAUSED,
    MANUAL_PAUSED,

    /** Le service a été arrêté (plantage, redémarrage du téléphone…) : aucune mesure. */
    INTERRUPTED,
    FINISHED,
}

/** Décision du filtre pour chaque mesure brute. Toutes les mesures sont conservées. */
enum class PointQuality {
    /** Ajoutée au tracé. */
    ACCEPTED,

    /** Déplacement inférieur au bruit GPS : ignorée pour la distance. */
    STATIONARY,

    /** Précision annoncée insuffisante. */
    LOW_ACCURACY,

    /** Saut incompatible avec la vitesse maximale du profil. */
    OUTLIER,

    /** Reçue pendant une pause manuelle. */
    PAUSED,

    /** Horodatage antérieur à la mesure précédente. */
    OUT_OF_ORDER,
}

enum class SessionEventType {
    START,
    MANUAL_PAUSE,
    MANUAL_RESUME,

    /** Fin des données avant un arrêt anormal du service. */
    INTERRUPTED,

    /** Reprise de l'enregistrement après un arrêt anormal. */
    RESUMED_AFTER_INTERRUPTION,
    STOP,
}

data class SessionEvent(val timeMillis: Long, val type: SessionEventType)

/** Mesure brute telle qu'enregistrée en base, avec la décision du filtre. */
data class RecordedFix(
    val fix: LocationFix,
    val quality: PointQuality,
    val state: RecordingState,
)

/** Point du tracé filtré, horodaté, avec l'altitude et la pression mesurées à cet instant. */
data class TimedPoint(
    val point: fr.igntrail.core.model.GeoPoint,
    val timeMillis: Long,
    val altitudeM: Double? = null,
    val pressureHpa: Float? = null,
)
