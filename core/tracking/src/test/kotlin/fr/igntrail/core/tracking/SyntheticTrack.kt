package fr.igntrail.core.tracking

import fr.igntrail.core.geo.GeoMath
import fr.igntrail.core.model.GeoPoint
import fr.igntrail.core.model.LocationFix
import fr.igntrail.core.model.LocationSourceType
import java.util.Random

/** Générateur de traces de référence reproductibles (graine fixe). */
class SyntheticTrack(seed: Long = 42, start: GeoPoint = GeoPoint(45.9, 6.87), startMillis: Long = T0) {
    private val random = Random(seed)
    private var position = start
    var time = startMillis
        private set
    private var altitude = 1000.0
    val fixes = mutableListOf<LocationFix>()

    /** Vraie trajectoire, une position par seconde (vérité terrain). */
    val truePath = mutableListOf(start)

    /** Distance réellement parcourue (vérité terrain). */
    var trueDistanceM = 0.0
        private set

    /**
     * Marche en ligne droite.
     * @param noiseM écart-type du bruit horizontal ajouté aux mesures
     * @param climbMps vitesse verticale (m/s)
     */
    fun walk(
        seconds: Int,
        speedMps: Double,
        bearingDeg: Double = 45.0,
        noiseM: Double = 2.0,
        accuracyM: Float = 5f,
        climbMps: Double = 0.0,
        altitudeNoiseM: Double = 0.0,
        dopplerSpeed: Boolean = true,
        /** Récepteur qui annonce toujours une vitesse nulle (émulateur, certaines puces). */
        dopplerAlwaysZero: Boolean = false,
    ): SyntheticTrack {
        repeat(seconds) {
            time += 1000
            if (speedMps > 0) {
                position = GeoMath.offset(position, bearingDeg, speedMps)
                truePath += position
                trueDistanceM += speedMps
            }
            altitude += climbMps
            val doppler = when {
                dopplerAlwaysZero -> 0f
                dopplerSpeed -> speedMps.toFloat()
                else -> null
            }
            emit(noiseM, accuracyM, altitudeNoiseM, doppler, bearingDeg)
        }
        return this
    }

    fun stand(seconds: Int, noiseM: Double = 3.0, accuracyM: Float = 6f, altitudeNoiseM: Double = 0.0) =
        walk(seconds, 0.0, noiseM = noiseM, accuracyM = accuracyM, altitudeNoiseM = altitudeNoiseM)

    /** Déplacement sans aucune mesure (tunnel, poche…). */
    fun silentMove(seconds: Int, speedMps: Double, bearingDeg: Double = 45.0): SyntheticTrack {
        position = GeoMath.offset(position, bearingDeg, speedMps * seconds)
        trueDistanceM += speedMps * seconds
        time += seconds * 1000L
        return this
    }

    /**
     * Déplacement réel, mais positions mesurées décalées de [offsetM] vers [offsetBearingDeg]
     * (retour du signal sous les arbres) ; la vitesse Doppler, elle, reste juste.
     */
    fun walkOffset(seconds: Int, speedMps: Double, bearingDeg: Double, offsetM: Double, offsetBearingDeg: Double, accuracyM: Float): SyntheticTrack {
        repeat(seconds) {
            time += 1000
            position = GeoMath.offset(position, bearingDeg, speedMps)
            truePath += position
            trueDistanceM += speedMps
            val p = GeoMath.offset(position, offsetBearingDeg, offsetM)
            fixes += LocationFix(p.latitude, p.longitude, time, accuracyM, altitude, 4f, speedMps.toFloat(), bearingDeg.toFloat(), 10f, LocationSourceType.FUSED)
        }
        return this
    }

    /** Une mesure aberrante isolée à [jumpM] mètres. */
    fun spike(jumpM: Double): SyntheticTrack {
        time += 1000
        val p = GeoMath.offset(position, 90.0, jumpM)
        fixes += LocationFix(p.latitude, p.longitude, time, 5f, altitude, 3f, 1.2f, 90f, 10f, LocationSourceType.FUSED)
        return this
    }

    private fun emit(noiseM: Double, accuracyM: Float, altitudeNoiseM: Double, speed: Float?, bearing: Double) {
        val noisy = GeoMath.offset(position, random.nextDouble() * 360.0, kotlin.math.abs(random.nextGaussian()) * noiseM)
        fixes += LocationFix(
            latitude = noisy.latitude,
            longitude = noisy.longitude,
            timeMillis = time,
            horizontalAccuracyM = accuracyM,
            altitudeM = altitude + random.nextGaussian() * altitudeNoiseM,
            verticalAccuracyM = 4f,
            speedMps = speed?.let { (it + random.nextGaussian() * 0.1).toFloat().coerceAtLeast(0f) },
            bearingDeg = bearing.toFloat(),
            bearingAccuracyDeg = 10f,
            source = LocationSourceType.FUSED,
        )
    }

    companion object {
        const val T0 = 1_760_000_000_000L
    }
}

fun TrackRecorder.feed(fixes: List<LocationFix>): List<PointQuality> = fixes.map { process(it) }
