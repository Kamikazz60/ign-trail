package fr.igntrail.map

import fr.igntrail.core.model.GeoPoint
import fr.igntrail.data.storage.Poi
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

/** Points d'intérêt (§12) : pastille colorée selon la catégorie, nom à côté. Plus un repère de sélection. */
class PoiLayer {
    private var source: GeoJsonSource? = null
    private var highlightSource: GeoJsonSource? = null
    private var pois: List<Poi> = emptyList()
    private var highlight: GeoPoint? = null

    fun install(style: Style) {
        val src = GeoJsonSource(SOURCE)
        val hl = GeoJsonSource(SOURCE_HIGHLIGHT)
        style.addSource(src)
        style.addSource(hl)
        style.addLayer(
            CircleLayer(LAYER_CIRCLES, SOURCE).withProperties(
                PropertyFactory.circleRadius(9f),
                PropertyFactory.circleColor(Expression.get(PROP_COLOR)),
                PropertyFactory.circleStrokeColor("#FFFFFF"),
                PropertyFactory.circleStrokeWidth(2.5f),
            ),
        )
        style.addLayer(
            SymbolLayer(LAYER_LABELS, SOURCE).withProperties(
                PropertyFactory.textField(Expression.get(PROP_NAME)),
                PropertyFactory.textFont(arrayOf("Source Sans Pro Semibold")),
                PropertyFactory.textSize(12f),
                PropertyFactory.textColor("#202124"),
                PropertyFactory.textHaloColor("#FFFFFF"),
                PropertyFactory.textHaloWidth(1.5f),
                PropertyFactory.textAnchor(Property.TEXT_ANCHOR_LEFT),
                PropertyFactory.textOffset(arrayOf(1.1f, 0f)),
                PropertyFactory.textOptional(true),
            ),
        )
        // Point du tracé correspondant à la position touchée sur le profil (§11).
        style.addLayer(
            CircleLayer(LAYER_HIGHLIGHT, SOURCE_HIGHLIGHT).withProperties(
                PropertyFactory.circleRadius(8f),
                PropertyFactory.circleColor("#FFFFFF"),
                PropertyFactory.circleStrokeColor("#7B3FE4"),
                PropertyFactory.circleStrokeWidth(4f),
            ),
        )
        source = src
        highlightSource = hl
        render()
    }

    fun update(newPois: List<Poi>, newHighlight: GeoPoint?) {
        pois = newPois
        highlight = newHighlight
        render()
    }

    private fun render() {
        source?.setGeoJson(
            FeatureCollection.fromFeatures(
                pois.map { p ->
                    Feature.fromGeometry(Point.fromLngLat(p.position.longitude, p.position.latitude)).apply {
                        addStringProperty(PROP_COLOR, p.category.color)
                        addStringProperty(PROP_NAME, p.name)
                    }
                },
            ),
        )
        highlightSource?.setGeoJson(
            FeatureCollection.fromFeatures(listOfNotNull(highlight?.let { Feature.fromGeometry(Point.fromLngLat(it.longitude, it.latitude)) })),
        )
    }

    companion object {
        private const val SOURCE = "igntrail-pois"
        private const val SOURCE_HIGHLIGHT = "igntrail-highlight"
        private const val LAYER_CIRCLES = "igntrail-pois-circles"
        private const val LAYER_LABELS = "igntrail-pois-labels"
        private const val LAYER_HIGHLIGHT = "igntrail-highlight"
        private const val PROP_COLOR = "color"
        private const val PROP_NAME = "name"
    }
}
