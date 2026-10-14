package fr.igntrail.map

import fr.igntrail.core.geo.TileId
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.exp

/**
 * Zones de carte disponibles hors ligne (§4) : vert = téléchargé, orange = téléchargement
 * en cours ou incomplet. Affiché par tuiles (≈ 1,5 km de côté) : les zones qui se
 * recouvrent se fondent, et l'on voit exactement ce qui manque.
 */
class OfflineCoverageLayer {
    private var source: GeoJsonSource? = null
    private var layer: FillLayer? = null
    private var complete: Set<TileId> = emptySet()
    private var partial: Set<TileId> = emptySet()
    private var visible = true

    fun install(style: Style) {
        val src = GeoJsonSource(SOURCE)
        style.addSource(src)
        val fill = FillLayer(LAYER, SOURCE).withProperties(
            PropertyFactory.fillColor(
                Expression.match(
                    Expression.get(PROP_STATE),
                    Expression.literal(STATE_COMPLETE), Expression.color(android.graphics.Color.parseColor("#2E7D32")),
                    Expression.color(android.graphics.Color.parseColor("#F57C00")),
                ),
            ),
            PropertyFactory.fillOpacity(0.16f),
        )
        style.addLayer(fill)
        source = src
        layer = fill
        render()
    }

    fun update(newComplete: Set<TileId>, newPartial: Set<TileId>, show: Boolean) {
        complete = newComplete
        partial = newPartial - newComplete
        visible = show
        render()
    }

    private fun render() {
        val features = if (!visible) {
            emptyList()
        } else {
            complete.map { feature(it, STATE_COMPLETE) } + partial.map { feature(it, STATE_PARTIAL) }
        }
        source?.setGeoJson(FeatureCollection.fromFeatures(features))
    }

    private fun feature(t: TileId, state: String): Feature {
        val west = lon(t.x, t.z)
        val east = lon(t.x + 1, t.z)
        val north = lat(t.y, t.z)
        val south = lat(t.y + 1, t.z)
        val ring = listOf(
            Point.fromLngLat(west, north), Point.fromLngLat(east, north),
            Point.fromLngLat(east, south), Point.fromLngLat(west, south), Point.fromLngLat(west, north),
        )
        return Feature.fromGeometry(Polygon.fromLngLats(listOf(ring))).apply { addStringProperty(PROP_STATE, state) }
    }

    private fun lon(x: Int, z: Int) = x.toDouble() / (1 shl z) * 360.0 - 180.0

    private fun lat(y: Int, z: Int): Double {
        val n = PI - 2.0 * PI * y / (1 shl z)
        return 180.0 / PI * atan(0.5 * (exp(n) - exp(-n)))
    }

    companion object {
        private const val SOURCE = "igntrail-offline-coverage"
        private const val LAYER = "igntrail-offline-coverage"
        private const val PROP_STATE = "state"
        private const val STATE_COMPLETE = "complete"
        private const val STATE_PARTIAL = "partial"
    }
}
