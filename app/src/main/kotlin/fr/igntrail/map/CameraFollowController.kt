package fr.igntrail.map

import fr.igntrail.core.model.GeoPoint
import fr.igntrail.core.model.LocationFix

/** Ordre de déplacement de la caméra. [zoom] null = conserver le zoom actuel. */
data class CameraCommand(val target: GeoPoint, val zoom: Double?)

/**
 * Règles de suivi GPS de la carte (cahier des charges §3.3 et §15.4) :
 * - suivi actif au démarrage ;
 * - tout geste manuel (déplacement, pincement, double appui) désactive le suivi ;
 * - le zoom choisi par l'utilisateur n'est jamais modifié, sauf le tout premier
 *   cadrage sur une position GPS réelle ;
 * - le recentrage réactive le suivi sans changer le zoom.
 *
 * Aucune dépendance Android : testé unitairement.
 */
class CameraFollowController(
    private val firstFixZoom: Double = FIRST_FIX_ZOOM,
    private val approximateZoom: Double = APPROXIMATE_ZOOM,
) {
    var isFollowing: Boolean = true
        private set

    private var centeredOnRealFix = false
    private var centeredOnApproximate = false

    /** Position provisoire (dernière connue) en attendant le GPS. */
    fun onApproximatePosition(position: GeoPoint): CameraCommand? {
        if (!isFollowing || centeredOnRealFix || centeredOnApproximate) return null
        centeredOnApproximate = true
        return CameraCommand(position, approximateZoom)
    }

    fun onFix(fix: LocationFix): CameraCommand? {
        if (!isFollowing) return null
        val target = GeoPoint(fix.latitude, fix.longitude)
        if (!centeredOnRealFix) {
            centeredOnRealFix = true
            return CameraCommand(target, firstFixZoom)
        }
        return CameraCommand(target, null)
    }

    fun onUserGesture() {
        isFollowing = false
    }

    /** Bouton de recentrage : réactive le suivi, zoom inchangé. */
    fun onRecenter(lastFix: LocationFix?): CameraCommand? {
        isFollowing = true
        if (lastFix == null) return null
        // Si l'utilisateur n'a jamais été cadré sur une vraie position, on garde
        // son zoom quand même : c'est lui qui a demandé le recentrage.
        centeredOnRealFix = true
        return CameraCommand(GeoPoint(lastFix.latitude, lastFix.longitude), null)
    }

    /** Restauration après recréation de l'écran. */
    fun restore(following: Boolean, alreadyCentered: Boolean) {
        isFollowing = following
        centeredOnRealFix = alreadyCentered
        centeredOnApproximate = alreadyCentered
    }

    companion object {
        const val FIRST_FIX_ZOOM = 15.0
        const val APPROXIMATE_ZOOM = 13.0
    }
}
