package fr.igntrail.map

import fr.igntrail.core.planning.PlanPoint
import fr.igntrail.core.planning.PlannedGeometry
import fr.igntrail.core.planning.SectionKind
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
 * Parcours en cours de création (§8). Les liaisons calculées sur les chemins sont en
 * violet plein ; les liaisons directes (non vérifiées) en orange, plus fines et
 * pointillées : elles ne doivent jamais passer pour un chemin praticable (§8.2).
 */
class PlannerLayer {
    private var lineSource: GeoJsonSource? = null
    private var pointSource: GeoJsonSource? = null
    private var geometry = PlannedGeometry(emptyList())
    private var points: List<PlanPoint> = emptyList()
    private var selectedId: Long? = null

    fun install(style: Style) {
        val lines = GeoJsonSource(SOURCE_LINES)
        val pts = GeoJsonSource(SOURCE_POINTS)
        style.addSource(lines)
        style.addSource(pts)
        val routed = Expression.eq(Expression.get(PROP_KIND), Expression.literal(SectionKind.ROUTED.name))
        style.addLayer(
            LineLayer(LAYER_CASING, SOURCE_LINES).withProperties(
                PropertyFactory.lineColor("#FFFFFF"),
                PropertyFactory.lineWidth(9f),
                PropertyFactory.lineOpacity(0.8f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            ).withFilter(routed),
        )
        style.addLayer(
            LineLayer(LAYER_ROUTED, SOURCE_LINES).withProperties(
                PropertyFactory.lineColor(RouteLayer.ROUTE_COLOR),
                PropertyFactory.lineWidth(5.5f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            ).withFilter(routed),
        )
        val straight = Expression.neq(Expression.get(PROP_KIND), Expression.literal(SectionKind.ROUTED.name))
        // Trait continu léger, toujours visible, sous les pointillés (non affichés par certains GPU).
        style.addLayer(
            LineLayer(LAYER_STRAIGHT_BASE, SOURCE_LINES).withProperties(
                PropertyFactory.lineColor(STRAIGHT_COLOR),
                PropertyFactory.lineWidth(3f),
                PropertyFactory.lineOpacity(0.45f),
            ).withFilter(straight),
        )
        style.addLayer(
            LineLayer(LAYER_STRAIGHT, SOURCE_LINES).withProperties(
                PropertyFactory.lineColor(STRAIGHT_COLOR),
                PropertyFactory.lineWidth(3.5f),
                PropertyFactory.lineOpacity(0.9f),
                PropertyFactory.lineDasharray(arrayOf(2f, 1.5f)),
            ).withFilter(straight),
        )
        style.addLayer(
            CircleLayer(LAYER_POINTS, SOURCE_POINTS).withProperties(
                PropertyFactory.circleRadius(Expression.get(PROP_RADIUS)),
                PropertyFactory.circleColor(Expression.get(PROP_COLOR)),
                PropertyFactory.circleStrokeColor("#FFFFFF"),
                PropertyFactory.circleStrokeWidth(3f),
            ),
        )
        style.addLayer(
            SymbolLayer(LAYER_LABELS, SOURCE_POINTS).withProperties(
                PropertyFactory.textField(Expression.get(PROP_LABEL)),
                PropertyFactory.textFont(arrayOf("Source Sans Pro Bold")),
                PropertyFactory.textSize(12f),
                PropertyFactory.textColor("#FFFFFF"),
                PropertyFactory.textAllowOverlap(true),
                PropertyFactory.textIgnorePlacement(true),
            ),
        )
        lineSource = lines
        pointSource = pts
        render()
    }

    fun update(newGeometry: PlannedGeometry, newPoints: List<PlanPoint>, newSelected: Long?) {
        geometry = newGeometry
        points = newPoints
        selectedId = newSelected
        render()
    }

    private fun render() {
        val sections = geometry.legs.flatMap { it.sections }.filter { it.points.size >= 2 }.map { s ->
            Feature.fromGeometry(LineString.fromLngLats(s.points.map { Point.fromLngLat(it.longitude, it.latitude) })).apply {
                addStringProperty(PROP_KIND, s.kind.name)
            }
        }
        lineSource?.setGeoJson(FeatureCollection.fromFeatures(sections))
        val features = points.mapIndexed { i, p ->
            val last = i == points.lastIndex && points.size > 1
            val (color, label) = when {
                i == 0 -> START_COLOR to "D"
                last -> END_COLOR to "A"
                else -> RouteLayer.ROUTE_COLOR to "$i"
            }
            Feature.fromGeometry(Point.fromLngLat(p.position.longitude, p.position.latitude)).apply {
                val selected = p.id == selectedId
                addStringProperty(PROP_COLOR, if (selected) SELECTED_COLOR else color)
                addNumberProperty(PROP_RADIUS, if (selected) 14f else 11f)
                addStringProperty(PROP_LABEL, label)
            }
        }
        pointSource?.setGeoJson(FeatureCollection.fromFeatures(features))
    }

    companion object {
        private const val SOURCE_LINES = "igntrail-plan-lines"
        private const val SOURCE_POINTS = "igntrail-plan-points"
        private const val LAYER_CASING = "igntrail-plan-casing"
        private const val LAYER_ROUTED = "igntrail-plan-routed"
        private const val LAYER_STRAIGHT = "igntrail-plan-straight"
        private const val LAYER_STRAIGHT_BASE = "igntrail-plan-straight-base"
        private const val LAYER_POINTS = "igntrail-plan-points"
        private const val LAYER_LABELS = "igntrail-plan-labels"
        private const val PROP_KIND = "kind"
        private const val PROP_COLOR = "color"
        private const val PROP_RADIUS = "radius"
        private const val PROP_LABEL = "label"
        const val STRAIGHT_COLOR = "#F57C1F"
        private const val START_COLOR = "#1B7F4A"
        private const val END_COLOR = "#202124"
        private const val SELECTED_COLOR = "#1E6FE8"
    }
}
