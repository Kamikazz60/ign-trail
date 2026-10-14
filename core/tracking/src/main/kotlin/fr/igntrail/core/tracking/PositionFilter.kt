package fr.igntrail.core.tracking

import fr.igntrail.core.model.GeoPoint
import fr.igntrail.core.model.LocationFix
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Filtre de Kalman « vitesse constante » appliqué indépendamment sur les axes
 * est et nord (§5.3).
 *
 * - La position mesurée est pondérée par la précision annoncée.
 * - La vitesse Doppler (très fiable sur les puces GPS) est utilisée comme seconde
 *   mesure : elle suit les virages et immobilise la position à l'arrêt, ce qui
 *   supprime l'essentiel des déplacements fictifs sans gommer les virages réels.
 */
class PositionFilter(private val accelNoise: Double) {
    private var originLat = 0.0
    private var originLon = 0.0
    private var metersPerDegLon = 0.0
    private var lastTimeMillis = 0L
    private var east: Axis? = null
    private var north: Axis? = null

    val isInitialized: Boolean get() = east != null

    /** Écart-type estimé de la position filtrée (m). */
    val positionSigmaM: Double
        get() = sqrt(((east?.pp ?: 0.0) + (north?.pp ?: 0.0)) / 2.0)

    /** Vitesse horizontale estimée (m/s). */
    val speedMps: Double
        get() = sqrt((east?.v ?: 0.0).let { it * it } + (north?.v ?: 0.0).let { it * it })

    fun reset(fix: LocationFix) {
        originLat = fix.latitude
        originLon = fix.longitude
        metersPerDegLon = METERS_PER_DEG_LAT * cos(fix.latitude * PI / 180.0)
        lastTimeMillis = fix.timeMillis
        val r = measurementVariance(fix)
        east = Axis(0.0, r)
        north = Axis(0.0, r)
        updateVelocity(fix)
    }

    /** Intègre une mesure et retourne la position filtrée. */
    fun update(fix: LocationFix): GeoPoint {
        val e = east
        val n = north
        if (e == null || n == null) {
            reset(fix)
            return position()
        }
        val dt = ((fix.timeMillis - lastTimeMillis) / 1000.0).coerceIn(0.0, 30.0)
        lastTimeMillis = fix.timeMillis
        e.predict(dt, accelNoise)
        n.predict(dt, accelNoise)
        val r = measurementVariance(fix)
        val zEast = (fix.longitude - originLon) * metersPerDegLon
        val zNorth = (fix.latitude - originLat) * METERS_PER_DEG_LAT
        // Écart entre la mesure et la prédiction, pour juger un Doppler « immobile » douteux.
        val innovation = sqrt((zEast - e.p) * (zEast - e.p) + (zNorth - n.p) * (zNorth - n.p))
        val stillPlausible = innovation <= STILL_MAX_INNOVATION_SIGMAS * sqrt(r + e.pp)
        // Manœuvre (virage serré, freinage) : la mesure s'écarte nettement de la trajectoire
        // prévue en ligne droite. On élargit alors l'incertitude de la prédiction pour suivre la
        // mesure au lieu de couper le virage (retour terrain : lacets à 50 km/h).
        val expected = MANEUVER_SIGMAS * MANEUVER_SIGMAS * (r + (e.pp + n.pp) / 2)
        val ratio = innovation * innovation / expected
        if (ratio > 1.0) {
            e.inflate(ratio)
            n.inflate(ratio)
        }
        e.updatePosition(zEast, r)
        n.updatePosition(zNorth, r)
        updateVelocity(fix, stillPlausible)
        return position()
    }

    fun position(): GeoPoint = GeoPoint(
        originLat + (north?.p ?: 0.0) / METERS_PER_DEG_LAT,
        originLon + (east?.p ?: 0.0) / metersPerDegLon,
    )

    /**
     * @param stillPlausible faux si la position s'éloigne nettement : certains récepteurs
     * (et l'émulateur) annoncent une vitesse nulle en permanence, à ne pas croire alors.
     */
    private fun updateVelocity(fix: LocationFix, stillPlausible: Boolean = true) {
        val speed = fix.speedMps ?: return
        val bearing = fix.bearingDeg
        val e = east ?: return
        val n = north ?: return
        when {
            speed < STILL_SPEED_MPS && stillPlausible -> {
                // Le Doppler dit « immobile » : contrainte forte, la position ne dérive plus.
                e.updateVelocity(0.0, STILL_VELOCITY_VARIANCE)
                n.updateVelocity(0.0, STILL_VELOCITY_VARIANCE)
            }
            speed >= MOVING_SPEED_MPS && bearing != null -> {
                val b = bearing.toDouble() * PI / 180.0
                val variance = VELOCITY_VARIANCE + (fix.bearingAccuracyDeg?.let { (it / 57.3) * speed }?.let { it * it } ?: 0.0)
                e.updateVelocity(speed * sin(b), variance)
                n.updateVelocity(speed * cos(b), variance)
            }
        }
    }

    /** Variance par axe : la précision Android est un rayon à 68 % (≈ 1,5 σ par axe). */
    private fun measurementVariance(fix: LocationFix): Double {
        val sigma = (fix.horizontalAccuracyM ?: 10f) / 1.515
        return sigma * sigma
    }

    /** Filtre 1D position/vitesse. */
    private class Axis(var p: Double, var pp: Double) {
        var v = 0.0
        var pv = 0.0
        var vv = INITIAL_VELOCITY_VARIANCE

        fun predict(dt: Double, q: Double) {
            if (dt <= 0.0) return
            p += v * dt
            pp += 2 * dt * pv + dt * dt * vv + q * dt * dt * dt / 3.0
            pv += dt * vv + q * dt * dt / 2.0
            vv += q * dt
        }

        /** Multiplie l'incertitude (position et vitesse) : la prochaine mesure pèsera davantage. */
        fun inflate(factor: Double) {
            pp *= factor
            pv *= factor
            vv *= factor
        }

        fun updatePosition(z: Double, r: Double) {
            val s = pp + r
            val kp = pp / s
            val kv = pv / s
            val y = z - p
            p += kp * y
            v += kv * y
            val pp0 = pp
            val pv0 = pv
            pp = (1 - kp) * pp0
            pv = (1 - kp) * pv0
            vv -= kv * pv0
        }

        fun updateVelocity(z: Double, r: Double) {
            val s = vv + r
            val kp = pv / s
            val kv = vv / s
            val y = z - v
            p += kp * y
            v += kv * y
            val pv0 = pv
            val vv0 = vv
            pp -= kp * pv0
            pv -= kp * vv0
            vv = (1 - kv) * vv0
        }
    }

    companion object {
        private const val METERS_PER_DEG_LAT = 111_195.0
        private const val INITIAL_VELOCITY_VARIANCE = 4.0
        private const val STILL_SPEED_MPS = 0.3f
        private const val MOVING_SPEED_MPS = 0.5f
        private const val STILL_VELOCITY_VARIANCE = 0.05
        private const val VELOCITY_VARIANCE = 0.25
        private const val STILL_MAX_INNOVATION_SIGMAS = 2.5

        /** Au-delà de cet écart (en écarts-types), la mesure signale une manœuvre. */
        private const val MANEUVER_SIGMAS = 2.0
    }
}
