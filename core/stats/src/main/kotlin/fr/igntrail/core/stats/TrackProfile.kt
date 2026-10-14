package fr.igntrail.core.stats

import fr.igntrail.core.geo.GeoMath
import fr.igntrail.core.model.GeoPoint
import fr.igntrail.core.planning.ElevationProfile
import fr.igntrail.core.tracking.ElevationTracker
import fr.igntrail.core.tracking.TimedPoint

/** Profil d'une randonnée enregistrée et position de chaque point le long du tracé (§11). */
class TrackProfile(
    val profile: ElevationProfile?,
    private val points: List<GeoPoint>,
    private val distancesM: List<Double>,
    /** Profil issu du baromètre (plus fin que le GPS). */
    val barometric: Boolean,
) {
    /** Point du tracé à [distanceM] du départ (pour l'afficher sur la carte). */
    fun pointAt(distanceM: Double): GeoPoint? {
        if (points.isEmpty()) return null
        val i = distancesM.indexOfFirst { it >= distanceM }
        if (i <= 0) return points.first()
        val d0 = distancesM[i - 1]
        val d1 = distancesM[i]
        val t = if (d1 > d0) (distanceM - d0) / (d1 - d0) else 0.0
        val a = points[i - 1]
        val b = points[i]
        return GeoPoint(a.latitude + (b.latitude - a.latitude) * t, a.longitude + (b.longitude - a.longitude) * t)
    }

    companion object {
        private const val SMOOTHING = 15

        /**
         * Construit le profil mesuré : altitudes lissées (moyenne glissante centrée), en
         * altitude barométrique calée sur la moyenne GPS si le téléphone a un baromètre.
         * Les coupures entre segments n'ajoutent pas de distance fictive.
         */
        fun of(segments: List<List<TimedPoint>>): TrackProfile {
            val pts = mutableListOf<GeoPoint>()
            val dist = mutableListOf<Double>()
            val raw = mutableListOf<TimedPoint>()
            var total = 0.0
            for (segment in segments) {
                segment.forEachIndexed { i, p ->
                    if (i > 0) total += GeoMath.distanceM(segment[i - 1].point, p.point)
                    pts += p.point
                    dist += total
                    raw += p
                }
            }
            val withPressure = raw.count { it.pressureHpa != null }
            val barometric = raw.isNotEmpty() && withPressure >= raw.size * 0.9
            val altitudes: List<Double?> = if (barometric) {
                val baro = raw.map { p -> p.pressureHpa?.let { ElevationTracker.pressureToAltitude(it.toDouble()) } }
                val offsets = raw.indices.mapNotNull { i -> raw[i].altitudeM?.let { a -> baro[i]?.let { a - it } } }
                val offset = if (offsets.isNotEmpty()) offsets.average() else 0.0
                baro.map { it?.plus(offset) }
            } else {
                raw.map { it.altitudeM }
            }
            val profile = ElevationProfile.from(dist, smooth(altitudes))
            return TrackProfile(profile, pts, dist, barometric)
        }

        private fun smooth(values: List<Double?>): List<Double?> = values.indices.map { i ->
            if (values[i] == null) return@map null
            val from = (i - SMOOTHING / 2).coerceAtLeast(0)
            val to = (i + SMOOTHING / 2).coerceAtMost(values.lastIndex)
            (from..to).mapNotNull { values[it] }.average()
        }
    }
}
