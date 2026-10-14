package fr.igntrail.core.navigation

import fr.igntrail.core.geo.GeoMath
import fr.igntrail.core.model.GeoPoint

/** Réglages du guidage (§9, §16). */
data class GuidanceParams(
    /** Distance au tracé au-delà de laquelle on est hors parcours (réglable). */
    val toleranceM: Double = 40.0,
    /** Mesures consécutives hors tolérance avant l'alerte (évite les fausses alertes GPS). */
    val confirmOffFixes: Int = 3,
    /** Retour sur le parcours : sous ce pourcentage de la tolérance (hystérésis). */
    val backOnRatio: Double = 0.7,
    val confirmOnFixes: Int = 2,
    /** La flèche vise un point situé à cette distance devant soi sur l'itinéraire. */
    val lookAheadM: Double = 30.0,
    /** Distance restante sous laquelle on considère l'arrivée atteinte. */
    val arrivalM: Double = 30.0,
    /** Recherche de la position autour de la progression connue (aller-retour, boucles). */
    val searchBehindM: Double = 300.0,
    val searchAheadM: Double = 2_000.0,
)

enum class GuidanceEvent { OFF_ROUTE, BACK_ON_ROUTE, ARRIVED }

data class GuidanceState(
    val alongM: Double,
    val remainingM: Double,
    val totalM: Double,
    /** Distance au tracé (m). */
    val crossTrackM: Double,
    val offRoute: Boolean,
    val arrived: Boolean,
    /** Cap à suivre depuis la position actuelle (degrés, 0 = nord). */
    val targetBearingDeg: Double,
    /** Point visé : devant soi sur l'itinéraire, ou le point le plus proche si hors parcours. */
    val target: GeoPoint,
    /** Point de l'itinéraire le plus proche de la position. */
    val nearest: GeoPoint,
) {
    val progress: Double get() = if (totalM > 0) (alongM / totalM).coerceIn(0.0, 1.0) else 0.0
}

/**
 * Guidage le long d'un itinéraire (§9). Sans dépendance Android : testé unitairement.
 *
 * La direction indiquée suit la progression le long du tracé (un point situé un peu
 * plus loin) et non la destination finale ; hors parcours, elle ramène vers le tracé.
 */
class Navigator(points: List<GeoPoint>, private val params: GuidanceParams = GuidanceParams()) {
    val geometry = RouteGeometry(points)

    private var progressM: Double? = null
    private var offRoute = false
    private var arrived = false
    private var offCount = 0
    private var onCount = 0

    /** Traite une position ; retourne le nouvel état et l'éventuel événement à signaler. */
    fun update(position: GeoPoint): Pair<GuidanceState, GuidanceEvent?> {
        val projection = locate(position)
        progressM = projection.alongM
        var event: GuidanceEvent? = null

        val cross = projection.crossTrackM
        if (!offRoute) {
            offCount = if (cross > params.toleranceM) offCount + 1 else 0
            if (offCount >= params.confirmOffFixes) {
                offRoute = true
                onCount = 0
                event = GuidanceEvent.OFF_ROUTE
            }
        } else {
            onCount = if (cross < params.toleranceM * params.backOnRatio) onCount + 1 else 0
            if (onCount >= params.confirmOnFixes) {
                offRoute = false
                offCount = 0
                event = GuidanceEvent.BACK_ON_ROUTE
            }
        }

        val remaining = geometry.lengthM - projection.alongM
        if (!arrived && !offRoute && remaining <= params.arrivalM) {
            arrived = true
            event = GuidanceEvent.ARRIVED
        }

        val target = if (offRoute) projection.point else geometry.pointAt(projection.alongM + params.lookAheadM)
        val bearing = if (GeoMath.distanceM(position, target) < 1.0) {
            GeoMath.bearingDeg(projection.point, geometry.pointAt(projection.alongM + params.lookAheadM + 1.0))
        } else {
            GeoMath.bearingDeg(position, target)
        }
        return GuidanceState(
            alongM = projection.alongM,
            remainingM = remaining.coerceAtLeast(0.0),
            totalM = geometry.lengthM,
            crossTrackM = cross,
            offRoute = offRoute,
            arrived = arrived,
            targetBearingDeg = bearing,
            target = target,
            nearest = projection.point,
        ) to event
    }

    /**
     * Position sur l'itinéraire : d'abord autour de la progression connue (évite de sauter
     * sur l'autre branche d'un aller-retour), sinon sur tout l'itinéraire.
     */
    private fun locate(p: GeoPoint): Projection {
        val known = progressM ?: return geometry.project(p)
        // On s'attend à avancer : en cas d'ambiguïté, la branche située devant l'emporte.
        val expected = known + FORWARD_BIAS_M
        val local = geometry.project(p, known - params.searchBehindM, known + params.searchAheadM, expected)
        if (local.crossTrackM <= params.toleranceM * 2) return local
        val global = geometry.project(p, expectedAlongM = expected)
        return if (global.crossTrackM < local.crossTrackM - params.toleranceM) global else local
    }
}

private const val FORWARD_BIAS_M = 50.0

/** Direction du départ à vol d'oiseau (§10), sans tenir compte du terrain. */
data class DirectionTo(val distanceM: Double, val bearingDeg: Double) {
    companion object {
        fun compute(from: GeoPoint, to: GeoPoint) = DirectionTo(GeoMath.distanceM(from, to), GeoMath.bearingDeg(from, to))
    }
}
