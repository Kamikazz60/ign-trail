package fr.igntrail.map

import fr.igntrail.core.model.LocationFix

/**
 * Choix du cap affiché par la flèche (§3.3) :
 * - en déplacement, le cap GPS (fiable dès que l'on marche) ;
 * - à l'arrêt, la boussole si elle est jugée fiable ;
 * - sinon aucune flèche, seulement le point.
 */
object HeadingSelector {
    const val MOVING_SPEED_MPS = 0.6f
    private const val MAX_BEARING_ERROR_DEG = 45f

    fun select(fix: LocationFix, compassHeading: Float?): Float? {
        val bearing = fix.bearingDeg
        val moving = (fix.speedMps ?: 0f) >= MOVING_SPEED_MPS
        val bearingOk = bearing != null && (fix.bearingAccuracyDeg ?: 0f) <= MAX_BEARING_ERROR_DEG
        return if (moving && bearingOk) bearing else compassHeading
    }
}
