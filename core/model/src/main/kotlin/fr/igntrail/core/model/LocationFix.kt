package fr.igntrail.core.model

/**
 * Une mesure de position, indépendante d'Android.
 *
 * Les champs optionnels sont null lorsque le capteur ne les fournit pas
 * (par exemple l'altitude au démarrage du GPS).
 */
data class LocationFix(
    val latitude: Double,
    val longitude: Double,
    /** Horodatage UTC en millisecondes. */
    val timeMillis: Long,
    /** Rayon de précision horizontale (68 %) en mètres. */
    val horizontalAccuracyM: Float?,
    val altitudeM: Double? = null,
    val verticalAccuracyM: Float? = null,
    /** Vitesse en m/s. */
    val speedMps: Float? = null,
    /** Cap en degrés (0 = nord, sens horaire). */
    val bearingDeg: Float? = null,
    val bearingAccuracyDeg: Float? = null,
    val source: LocationSourceType,
    /** Pression atmosphérique (hPa) si le téléphone a un baromètre. */
    val pressureHpa: Float? = null,
)

enum class LocationSourceType {
    /** Fused Location Provider (Google Play Services). */
    FUSED,

    /** Récepteur GNSS natif via LocationManager. */
    PLATFORM_GPS,

    /** Dernière position connue enregistrée lors d'une session précédente. */
    STORED,
}

data class GeoPoint(val latitude: Double, val longitude: Double)
