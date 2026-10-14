package fr.igntrail.map

import fr.igntrail.core.model.GeoPoint
import fr.igntrail.core.model.Route
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

/**
 * Itinéraire prévu (§9), visuellement distinct du tracé réalisé (§7) : ligne violette
 * large et semi-transparente sous le tracé, départ (vert), arrivée (noir), points
 * nommés, et un trait fin vers le tracé quand on en est sorti.
 */
class RouteLayer {
    private var routeSource: GeoJsonSource? = null
    private var markerSource: GeoJsonSource? = null
    private var connectorSource: GeoJsonSource? = null
    private var route: Route? = null
    private var connector: Pair<GeoPoint, GeoPoint>? = null

    /** À appeler après chaque chargement de style, avant le tracé enregistré. */
    fun install(style: Style) {
        val routeSrc = GeoJsonSource(SOURCE_ROUTE)
        val markerSrc = GeoJsonSource(SOURCE_MARKERS)
        val connectorSrc = GeoJsonSource(SOURCE_CONNECTOR)
        style.addSource(routeSrc)
        style.addSource(markerSrc)
        style.addSource(connectorSrc)
        style.addLayer(
            LineLayer(LAYER_CASING, SOURCE_ROUTE).withProperties(
                PropertyFactory.lineColor("#FFFFFF"),
                PropertyFactory.lineWidth(10f),
                PropertyFactory.lineOpacity(0.7f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            ),
        )
        style.addLayer(
            LineLayer(LAYER_LINE, SOURCE_ROUTE).withProperties(
                PropertyFactory.lineColor(ROUTE_COLOR),
                PropertyFactory.lineWidth(6.5f),
                PropertyFactory.lineOpacity(0.75f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            ),
        )
        style.addLayer(
            // Ligne continue, fine et transparente : les pointillés de MapLibre passent par un rendu
            // spécial (SDF) absent de certains GPU ; ce trait s'affiche partout.
            LineLayer(LAYER_CONNECTOR, SOURCE_CONNECTOR).withProperties(
                PropertyFactory.lineColor(ROUTE_COLOR),
                PropertyFactory.lineWidth(2.5f),
                PropertyFactory.lineOpacity(0.6f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            ),
        )
        style.addLayer(
            CircleLayer(LAYER_MARKERS, SOURCE_MARKERS).withProperties(
                PropertyFactory.circleRadius(Expression.get(PROP_RADIUS)),
                PropertyFactory.circleColor(Expression.get(PROP_COLOR)),
                PropertyFactory.circleStrokeColor("#FFFFFF"),
                PropertyFactory.circleStrokeWidth(2.5f),
            ),
        )
        style.addLayer(
            SymbolLayer(LAYER_LABELS, SOURCE_MARKERS).withProperties(
                PropertyFactory.textField(Expression.get(PROP_LABEL)),
                PropertyFactory.textFont(arrayOf("Source Sans Pro Semibold")),
                PropertyFactory.textSize(13f),
                PropertyFactory.textColor(ROUTE_COLOR),
                PropertyFactory.textHaloColor("#FFFFFF"),
                PropertyFactory.textHaloWidth(1.6f),
                PropertyFactory.textOffset(arrayOf(0f, 1.3f)),
                PropertyFactory.textAnchor(Property.TEXT_ANCHOR_TOP),
                PropertyFactory.textOptional(true),
            ),
        )
        routeSource = routeSrc
        markerSource = markerSrc
        connectorSource = connectorSrc
        render()
    }

    fun update(newRoute: Route?, newConnector: Pair<GeoPoint, GeoPoint>?) {
        val routeChanged = newRoute !== route
        route = newRoute
        connector = newConnector
        if (routeChanged) render() else renderConnector()
    }

    private fun render() {
        val r = route
        val line = r?.points?.map { Point.fromLngLat(it.longitude, it.latitude) }
        routeSource?.setGeoJson(
            if (line != null && line.size >= 2) FeatureCollection.fromFeature(Feature.fromGeometry(LineString.fromLngLats(line)))
            else FeatureCollection.fromFeatures(emptyList()),
        )
        val markers = buildList {
            if (r != null && r.points.size >= 2) {
                r.waypoints.forEach { add(marker(it.latitude, it.longitude, ROUTE_COLOR, 5f, it.name)) }
                val first = r.points.first()
                val last = r.points.last()
                add(marker(last.latitude, last.longitude, "#202124", 8f, "Arrivée"))
                add(marker(first.latitude, first.longitude, "#1B7F4A", 8f, "Départ"))
            }
        }
        markerSource?.setGeoJson(FeatureCollection.fromFeatures(markers))
        renderConnector()
    }

    private fun renderConnector() {
        val c = connector
        connectorSource?.setGeoJson(
            if (c == null) FeatureCollection.fromFeatures(emptyList())
            else FeatureCollection.fromFeature(
                Feature.fromGeometry(
                    LineString.fromLngLats(listOf(Point.fromLngLat(c.first.longitude, c.first.latitude), Point.fromLngLat(c.second.longitude, c.second.latitude))),
                ),
            ),
        )
    }

    private fun marker(lat: Double, lon: Double, color: String, radius: Float, label: String) =
        Feature.fromGeometry(Point.fromLngLat(lon, lat)).apply {
            addStringProperty(PROP_COLOR, color)
            addNumberProperty(PROP_RADIUS, radius)
            addStringProperty(PROP_LABEL, label)
        }

    companion object {
        private const val SOURCE_ROUTE = "igntrail-route"
        private const val SOURCE_MARKERS = "igntrail-route-markers"
        private const val SOURCE_CONNECTOR = "igntrail-route-connector"
        private const val LAYER_CASING = "igntrail-route-casing"
        private const val LAYER_LINE = "igntrail-route-line"
        private const val LAYER_CONNECTOR = "igntrail-route-connector"
        private const val LAYER_MARKERS = "igntrail-route-markers"
        private const val LAYER_LABELS = "igntrail-route-labels"
        private const val PROP_COLOR = "color"
        private const val PROP_RADIUS = "radius"
        private const val PROP_LABEL = "label"
        const val ROUTE_COLOR = "#7B3FE4"
    }
}
