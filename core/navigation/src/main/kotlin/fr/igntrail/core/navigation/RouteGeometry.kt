package fr.igntrail.core.navigation

import fr.igntrail.core.geo.GeoMath
import fr.igntrail.core.model.GeoPoint
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot

/** Projection d'une position sur l'itinéraire. */
data class Projection(
    val segmentIndex: Int,
    /** Distance parcourue le long de l'itinéraire jusqu'au point projeté (m). */
    val alongM: Double,
    /** Distance entre la position et l'itinéraire (m). */
    val crossTrackM: Double,
    val point: GeoPoint,
)

/** Géométrie d'un itinéraire : distances cumulées et projection d'une position. */
class RouteGeometry(val points: List<GeoPoint>) {
    init {
        require(points.size >= 2) { "Un itinéraire a au moins deux points" }
    }

    /** Distance cumulée au début de chaque point. */
    private val cumulative: DoubleArray = DoubleArray(points.size).also { c ->
        for (i in 1 until points.size) c[i] = c[i - 1] + GeoMath.distanceM(points[i - 1], points[i])
    }

    val lengthM: Double get() = cumulative.last()

    /**
     * Point le plus proche, cherché parmi les segments qui recoupent [fromM, toM]
     * le long de l'itinéraire (tout l'itinéraire par défaut).
     *
     * Plusieurs branches peuvent être presque aussi proches (aller-retour sur le même
     * sentier, boucles) : parmi elles (moins de 5 m d'écart), on retient celle dont la
     * progression est la plus proche de [expectedAlongM], c'est-à-dire la continuité.
     */
    fun project(p: GeoPoint, fromM: Double = 0.0, toM: Double = lengthM, expectedAlongM: Double = 0.0): Projection {
        val candidates = (0 until points.lastIndex)
            .filter { cumulative[it + 1] >= fromM && cumulative[it] <= toM }
            .map { projectOnSegment(p, it) }
        if (candidates.isEmpty()) return projectOnSegment(p, 0)
        val best = candidates.minOf { it.crossTrackM }
        return candidates
            .filter { it.crossTrackM <= best + TIE_M }
            .minBy { kotlin.math.abs(it.alongM - expectedAlongM) }
    }

    /** Point situé à [alongM] mètres du départ, le long de l'itinéraire. */
    fun pointAt(alongM: Double): GeoPoint {
        val d = alongM.coerceIn(0.0, lengthM)
        var i = cumulative.indexOfLast { it <= d }.coerceIn(0, points.lastIndex - 1)
        while (i < points.lastIndex - 1 && cumulative[i + 1] < d) i++
        val segLength = cumulative[i + 1] - cumulative[i]
        if (segLength <= 0.0) return points[i]
        val t = (d - cumulative[i]) / segLength
        val a = points[i]
        val b = points[i + 1]
        return GeoPoint(a.latitude + (b.latitude - a.latitude) * t, a.longitude + (b.longitude - a.longitude) * t)
    }

    private fun projectOnSegment(p: GeoPoint, i: Int): Projection {
        val a = points[i]
        val b = points[i + 1]
        // Projection plane locale (mètres), précise à l'échelle d'un segment.
        val kx = METERS_PER_DEG * cos(a.latitude * PI / 180.0)
        val bx = (b.longitude - a.longitude) * kx
        val by = (b.latitude - a.latitude) * METERS_PER_DEG
        val px = (p.longitude - a.longitude) * kx
        val py = (p.latitude - a.latitude) * METERS_PER_DEG
        val len2 = bx * bx + by * by
        val t = if (len2 == 0.0) 0.0 else ((px * bx + py * by) / len2).coerceIn(0.0, 1.0)
        val qx = bx * t
        val qy = by * t
        val point = GeoPoint(a.latitude + qy / METERS_PER_DEG, a.longitude + qx / kx)
        val along = cumulative[i] + (cumulative[i + 1] - cumulative[i]) * t
        return Projection(i, along, hypot(px - qx, py - qy), point)
    }

    companion object {
        private const val METERS_PER_DEG = 111_195.0
        private const val TIE_M = 5.0
    }
}
