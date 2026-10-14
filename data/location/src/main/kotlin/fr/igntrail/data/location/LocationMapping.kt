package fr.igntrail.data.location

import android.content.Context
import android.location.Location
import androidx.core.location.LocationCompat
import androidx.core.location.altitude.AltitudeConverterCompat
import fr.igntrail.core.model.LocationFix
import fr.igntrail.core.model.LocationSourceType

/**
 * Conversion vers le modèle indépendant d'Android.
 *
 * L'altitude GPS brute est mesurée au-dessus de l'ellipsoïde WGS84 (≈ 50 m
 * d'écart en France) : on la convertit en altitude au-dessus du niveau moyen
 * de la mer, comme sur les cartes IGN. Lecture disque : hors du fil principal.
 */
internal fun Location.toFix(context: Context, source: LocationSourceType): LocationFix {
    val (altitude, altitudeAccuracy) = mslAltitude(context)
    return LocationFix(
        latitude = latitude,
        longitude = longitude,
        timeMillis = time,
        horizontalAccuracyM = if (hasAccuracy()) accuracy else null,
        altitudeM = altitude,
        verticalAccuracyM = altitudeAccuracy,
        speedMps = if (hasSpeed()) speed else null,
        bearingDeg = if (hasBearing()) bearing else null,
        bearingAccuracyDeg = if (hasBearingAccuracy()) bearingAccuracyDegrees else null,
        source = source,
    )
}

/** Altitude au-dessus du niveau de la mer et sa précision ; (null, null) si indisponible. */
private fun Location.mslAltitude(context: Context): Pair<Double?, Float?> {
    if (!hasAltitude()) return null to null
    if (!LocationCompat.hasMslAltitude(this)) {
        runCatching { AltitudeConverterCompat.addMslAltitudeToLocation(context, this) }
    }
    if (!LocationCompat.hasMslAltitude(this)) return null to null
    val accuracy = when {
        LocationCompat.hasMslAltitudeAccuracy(this) -> LocationCompat.getMslAltitudeAccuracyMeters(this)
        hasVerticalAccuracy() -> verticalAccuracyMeters
        else -> null
    }
    return LocationCompat.getMslAltitudeMeters(this) to accuracy
}
