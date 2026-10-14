package fr.igntrail.map

import fr.igntrail.core.geo.GeoMath
import fr.igntrail.core.model.GeoPoint
import fr.igntrail.core.model.LocationFix
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon

/**
 * Indicateur de position (§3.3) : point bleu cerclé de blanc, flèche de direction
 * et cercle de précision.
 *
 * Entièrement vectoriel (cercles et triangle géographique recalculé selon le zoom) :
 * aucune image n'est transmise au moteur, ce qui évite les différences de rendu
 * des bitmaps selon les GPU.
 */
class PositionIndicator {
    private var dotSource: GeoJsonSource? = null
    private var arrowSource: GeoJsonSource? = null
    private var lastFix: LocationFix? = null
    private var lastHeading: Float? = null
    private var zoom = 15.0

    /** À appeler après chaque chargement de style (les couches sont perdues au changement de fond). */
    fun install(style: Style, currentZoom: Double) {
        zoom = currentZoom
        val dots = GeoJsonSource(SOURCE_DOT)
        val arrow = GeoJsonSource(SOURCE_ARROW)
        style.addSource(dots)
        style.addSource(arrow)
        style.addLayer(
            CircleLayer(LAYER_ACCURACY, SOURCE_DOT).withProperties(
                PropertyFactory.circleColor(BLUE),
                PropertyFactory.circleOpacity(0.15f),
                PropertyFactory.circleStrokeColor(BLUE),
                PropertyFactory.circleStrokeOpacity(0.35f),
                PropertyFactory.circleStrokeWidth(1f),
                PropertyFactory.circlePitchAlignment(Property.CIRCLE_PITCH_ALIGNMENT_MAP),
                PropertyFactory.circleRadius(
                    Expression.interpolate(
                        Expression.exponential(2),
                        Expression.zoom(),
                        Expression.stop(0, Expression.get(PROP_RADIUS_Z0)),
                        Expression.stop(22, Expression.product(Expression.get(PROP_RADIUS_Z0), Expression.literal(4_194_304))),
                    ),
                ),
            ),
        )
        style.addLayer(
            FillLayer(LAYER_ARROW, SOURCE_ARROW).withProperties(
                PropertyFactory.fillColor(BLUE),
                PropertyFactory.fillOutlineColor("#FFFFFF"),
            ),
        )
        style.addLayer(
            CircleLayer(LAYER_DOT, SOURCE_DOT).withProperties(
                PropertyFactory.circleRadius(DOT_RADIUS_DP),
                PropertyFactory.circleColor(BLUE),
                PropertyFactory.circleStrokeWidth(DOT_STROKE_DP),
                PropertyFactory.circleStrokeColor("#FFFFFF"),
            ),
        )
        dotSource = dots
        arrowSource = arrow
        renderDot()
        renderArrow()
    }

    fun update(fix: LocationFix, heading: Float?) {
        lastFix = fix
        lastHeading = heading
        renderDot()
        renderArrow()
    }

    /** La taille de la flèche est fixe à l'écran : on la recalcule quand le zoom change. */
    fun onZoomChanged(newZoom: Double) {
        if (kotlin.math.abs(newZoom - zoom) < 0.01) return
        zoom = newZoom
        renderArrow()
    }

    private fun renderDot() {
        val fix = lastFix ?: return
        val src = dotSource ?: return
        val feature = Feature.fromGeometry(Point.fromLngLat(fix.longitude, fix.latitude))
        val accuracy = (fix.horizontalAccuracyM ?: 0f).toDouble()
        // Rayon en pixels au zoom 0 ; l'expression le double à chaque niveau de zoom.
        feature.addNumberProperty(PROP_RADIUS_Z0, accuracy / GeoMath.metersPerPixel(fix.latitude, 0.0))
        src.setGeoJson(feature)
    }

    private fun renderArrow() {
        val src = arrowSource ?: return
        val fix = lastFix
        val heading = lastHeading
        if (fix == null || heading == null) {
            src.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
            return
        }
        val center = GeoPoint(fix.latitude, fix.longitude)
        val mpp = GeoMath.metersPerPixel(fix.latitude, zoom)
        val h = heading.toDouble()
        val tip = GeoMath.offset(center, h, ARROW_TIP_DP * mpp)
        // Base cachée sous le point : seule la pointe dépasse du cercle blanc.
        val baseCenterDist = ARROW_BASE_DP * mpp
        val halfWidth = ARROW_HALF_WIDTH_DP * mpp
        val baseCenter = GeoMath.offset(center, h, baseCenterDist)
        val left = GeoMath.offset(baseCenter, h - 90.0, halfWidth)
        val right = GeoMath.offset(baseCenter, h + 90.0, halfWidth)
        val ring = listOf(tip, right, left, tip).map { Point.fromLngLat(it.longitude, it.latitude) }
        src.setGeoJson(Feature.fromGeometry(Polygon.fromLngLats(listOf(ring))))
    }

    companion object {
        private const val SOURCE_DOT = "igntrail-position"
        private const val SOURCE_ARROW = "igntrail-position-arrow"
        private const val LAYER_ACCURACY = "igntrail-position-accuracy"
        private const val LAYER_ARROW = "igntrail-position-arrow"
        private const val LAYER_DOT = "igntrail-position-dot"
        private const val PROP_RADIUS_Z0 = "r0"
        private const val BLUE = "#1E6FE8"

        private const val DOT_RADIUS_DP = 8f
        private const val DOT_STROKE_DP = 3f
        private const val ARROW_TIP_DP = 22.0
        private const val ARROW_BASE_DP = 4.0
        private const val ARROW_HALF_WIDTH_DP = 10.0
    }
}
