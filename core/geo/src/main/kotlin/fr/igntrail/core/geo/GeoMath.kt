package fr.igntrail.core.geo

import fr.igntrail.core.model.GeoPoint
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

object GeoMath {
    /** Rayon terrestre moyen (IUGG), en mètres. */
    const val EARTH_RADIUS_M = 6_371_008.8

    /** Circonférence équatoriale WGS84 utilisée par la projection Web Mercator. */
    private const val EQUATOR_CIRCUMFERENCE_M = 40_075_016.686

    /** Distance orthodromique (haversine) en mètres. */
    fun distanceM(a: GeoPoint, b: GeoPoint): Double {
        val lat1 = a.latitude.toRad()
        val lat2 = b.latitude.toRad()
        val dLat = lat2 - lat1
        val dLon = (b.longitude - a.longitude).toRad()
        val h = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
        return 2 * EARTH_RADIUS_M * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }

    /** Cap initial de [a] vers [b] en degrés [0, 360). */
    fun bearingDeg(a: GeoPoint, b: GeoPoint): Double {
        val lat1 = a.latitude.toRad()
        val lat2 = b.latitude.toRad()
        val dLon = (b.longitude - a.longitude).toRad()
        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        return (atan2(y, x).toDeg() + 360.0) % 360.0
    }

    /**
     * Point situé à [distanceM] de [origin] dans la direction [bearingDeg].
     * Approximation plane locale, exacte au centimètre pour quelques centaines de mètres.
     */
    fun offset(origin: GeoPoint, bearingDeg: Double, distanceM: Double): GeoPoint {
        val b = bearingDeg.toRad()
        val dNorth = distanceM * cos(b)
        val dEast = distanceM * sin(b)
        val dLat = dNorth / EARTH_RADIUS_M
        val dLon = dEast / (EARTH_RADIUS_M * cos(origin.latitude.toRad()))
        return GeoPoint(origin.latitude + dLat.toDeg(), origin.longitude + dLon.toDeg())
    }

    /**
     * Mètres par pixel logique (tuiles de 512 px, convention MapLibre)
     * à la latitude et au zoom donnés.
     */
    fun metersPerPixel(latitude: Double, zoom: Double): Double =
        EQUATOR_CIRCUMFERENCE_M * cos(latitude.toRad()) / (512.0 * 2.0.pow(zoom))

    private fun Double.toRad() = this * PI / 180.0
    private fun Double.toDeg() = this * 180.0 / PI
}
