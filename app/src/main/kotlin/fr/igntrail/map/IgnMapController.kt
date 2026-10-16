package fr.igntrail.map

import android.graphics.PointF
import fr.igntrail.core.geo.GeoBounds
import fr.igntrail.core.geo.TileId
import fr.igntrail.core.model.GeoPoint
import fr.igntrail.core.model.LocationFix
import fr.igntrail.core.model.Route
import fr.igntrail.core.planning.PlanPoint
import fr.igntrail.core.planning.PlannedGeometry
import fr.igntrail.core.stats.SpeedThresholds
import fr.igntrail.core.tracking.TimedPoint
import fr.igntrail.data.settings.MapDisplaySettings
import fr.igntrail.data.storage.Poi
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory

/** Toutes les opérations MapLibre de l'écran carte. Utilisé uniquement sur le thread principal. */
class IgnMapController(val map: MapLibreMap) {
    private var appliedSettings: MapDisplaySettings? = null
    private var style: Style? = null
    private val coverage = OfflineCoverageLayer()
    private val route = RouteLayer()
    private val planner = PlannerLayer()
    private val track = TrackLayer()
    private val poiLayer = PoiLayer()
    private val position = PositionIndicator()

    init {
        map.uiSettings.apply {
            // Rotation à deux doigts (choix de l'utilisateur du 16/10/2026, au lieu du nord
            // toujours en haut du §3.3) ; seuil relevé pendant un zoom pour ne pas tourner par
            // mégarde. Pas d'inclinaison 3D.
            isRotateGesturesEnabled = true
            isIncreaseRotateThresholdWhenScaling = true
            isTiltGesturesEnabled = false
            isCompassEnabled = false
            isLogoEnabled = false
            isAttributionEnabled = false
        }
        map.setMinZoomPreference(4.0)
        map.setMaxZoomPreference(19.0)
    }

    fun applyDisplay(settings: MapDisplaySettings) {
        val previous = appliedSettings
        appliedSettings = settings
        if (previous != null && previous.baseMap == settings.baseMap) {
            style?.let { updateHillshade(it, settings) }
            return
        }
        val builder = IgnLayers.styleUri(settings.baseMap)
            ?.let { Style.Builder().fromUri(it) }
            ?: Style.Builder().fromJson(IgnLayers.orthoStyleJson)
        map.setStyle(builder) { loaded ->
            style = loaded
            appliedSettings?.let { updateHillshade(loaded, it) }
            coverage.install(loaded)
            route.install(loaded)
            planner.install(loaded)
            track.install(loaded)
            poiLayer.install(loaded)
            position.install(loaded, map.cameraPosition.zoom)
        }
    }

    /** L'ombrage fait partie du style Plan IGN (voir [IgnStyleInterceptor]) : on règle sa visibilité. */
    private fun updateHillshade(style: Style, settings: MapDisplaySettings) {
        style.getLayer(IgnLayers.HILLSHADE_ID)?.setProperties(
            PropertyFactory.visibility(if (settings.hillshade) Property.VISIBLE else Property.NONE),
        )
    }

    /** Indicateur bleu ; [heading] null = point sans flèche. */
    fun updateLocation(fix: LocationFix, heading: Float?) {
        position.update(fix, heading)
    }

    /** Itinéraire prévu ; [connector] relie la position au tracé quand on en est sorti. */
    fun showRoute(newRoute: Route?, connector: Pair<GeoPoint, GeoPoint>?) {
        route.update(newRoute, connector)
    }

    /** Points d'intérêt et repère de sélection du profil (§11, §12). */
    fun showPois(pois: List<Poi>, highlight: GeoPoint?) {
        poiLayer.update(pois, highlight)
    }

    /** Zones disponibles hors ligne (§4). */
    fun showOfflineCoverage(complete: Set<TileId>, partial: Set<TileId>, visible: Boolean) {
        coverage.update(complete, partial, visible)
    }

    /** Parcours en cours de création (§8). */
    fun showPlan(geometry: PlannedGeometry, points: List<PlanPoint>, selectedId: Long?) {
        planner.update(geometry, points, selectedId)
    }

    /** Position à l'écran d'un point géographique (pixels de la vue carte). */
    fun screenPoint(p: GeoPoint): PointF = map.projection.toScreenLocation(LatLng(p.latitude, p.longitude))

    /** Point géographique au centre de la carte. */
    fun center(): GeoPoint = map.cameraPosition.target?.let { GeoPoint(it.latitude, it.longitude) } ?: GeoPoint(0.0, 0.0)

    /** Centre la carte sur un point, zoom inchangé. */
    fun centerOn(p: GeoPoint) {
        map.easeCamera(CameraUpdateFactory.newLatLng(LatLng(p.latitude, p.longitude)), 300)
    }

    /** Tracé enregistré, coloré selon la vitesse ; liste vide = aucun tracé. */
    fun showTrack(segments: List<List<TimedPoint>>, thresholds: SpeedThresholds?) {
        track.update(segments, thresholds)
    }

    fun onCameraMoved() {
        position.onZoomChanged(map.cameraPosition.zoom)
    }

    fun execute(command: CameraCommand) {
        val target = LatLng(command.target.latitude, command.target.longitude)
        if (command.zoom != null) {
            // Premier cadrage : saut direct, une animation depuis la vue France serait longue.
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(target, command.zoom))
        } else {
            map.easeCamera(CameraUpdateFactory.newLatLng(target), CAMERA_ANIMATION_MS)
        }
    }

    /** Zone géographique couverte par un rectangle de l'écran (pixels de la vue carte). */
    fun boundsOf(left: Float, top: Float, right: Float, bottom: Float): GeoBounds {
        val corners = listOf(PointF(left, top), PointF(right, top), PointF(right, bottom), PointF(left, bottom))
            .map { map.projection.fromScreenLocation(it) }
        return GeoBounds(
            corners.minOf { it.latitude }, corners.minOf { it.longitude },
            corners.maxOf { it.latitude }, corners.maxOf { it.longitude },
        )
    }

    /** Cadre la carte sur une zone (bouton « Voir » d'une carte hors ligne). */
    fun showBounds(bounds: GeoBounds) {
        map.easeCamera(
            CameraUpdateFactory.newLatLngBounds(LatLngBounds.from(bounds.north, bounds.east, bounds.south, bounds.west), 80),
            CAMERA_ANIMATION_MS,
        )
    }

    fun moveImmediately(latitude: Double, longitude: Double, zoom: Double, bearing: Double = 0.0) {
        map.moveCamera(
            CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder().target(LatLng(latitude, longitude)).zoom(zoom).bearing(bearing).build(),
            ),
        )
    }

    /** Appui sur la boussole : nord de nouveau en haut. */
    fun resetNorth() {
        map.easeCamera(CameraUpdateFactory.bearingTo(0.0), NORTH_RESET_MS)
    }

    /** Zoom par boutons : centré sur l'écran, sans toucher au mode de suivi. */
    fun zoomBy(delta: Double) {
        map.animateCamera(CameraUpdateFactory.zoomBy(delta), 250)
    }

    companion object {
        private const val CAMERA_ANIMATION_MS = 600
        private const val NORTH_RESET_MS = 300
    }
}
