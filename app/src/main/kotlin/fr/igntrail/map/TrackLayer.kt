package fr.igntrail.map

import fr.igntrail.core.geo.Corridor
import fr.igntrail.core.stats.SpeedChunker
import fr.igntrail.core.stats.SpeedThresholds
import fr.igntrail.core.tracking.TimedPoint
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

/**
 * Tracé réellement parcouru. Pendant la sortie : une seule couleur. Une fois la sortie
 * terminée : coloré selon la vitesse (§7) par rapport à sa propre moyenne, avec le dégradé
 * choisi par l'utilisateur : ROUGE plus lent, JAUNE la moyenne, VERT plus rapide.
 *
 * La vitesse est calculée sur des portions d'au moins 60 m et 20 s ([SpeedChunker]).
 * Une coupure (pause manuelle, perte de signal, interruption) n'est jamais reliée.
 */
class TrackLayer {
    private var source: GeoJsonSource? = null
    private var layer: LineLayer? = null
    private var segments: List<List<TimedPoint>> = emptyList()
    private var thresholds: SpeedThresholds? = null

    /** À appeler après chaque chargement de style, avant l'indicateur de position. */
    fun install(style: Style) {
        val src = GeoJsonSource(SOURCE)
        style.addSource(src)
        style.addLayer(
            LineLayer(LAYER_CASING, SOURCE).withProperties(
                PropertyFactory.lineColor("#FFFFFF"),
                PropertyFactory.lineWidth(8f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            ),
        )
        val line = LineLayer(LAYER_LINE, SOURCE).withProperties(
            PropertyFactory.lineWidth(5f),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        )
        style.addLayer(line)
        source = src
        layer = line
        applyColors()
        render()
    }

    fun update(newSegments: List<List<TimedPoint>>, newThresholds: SpeedThresholds?) {
        val thresholdsChanged = newThresholds != thresholds
        segments = newSegments
        thresholds = newThresholds
        if (thresholdsChanged) applyColors()
        render()
    }

    private fun applyColors() {
        val t = thresholds
        layer?.setProperties(
            PropertyFactory.lineColor(
                if (t == null) {
                    Expression.color(android.graphics.Color.parseColor(LIVE))
                } else {
                    Expression.interpolate(
                        Expression.linear(),
                        Expression.get(PROP_SPEED),
                        Expression.stop(t.slowMps, Expression.color(android.graphics.Color.parseColor(SLOW))),
                        Expression.stop(t.usualMps, Expression.color(android.graphics.Color.parseColor(USUAL))),
                        Expression.stop(t.fastMps, Expression.color(android.graphics.Color.parseColor(FAST))),
                    )
                },
            ),
        )
    }

    private fun render() {
        val src = source ?: return
        if (thresholds == null) {
            src.setGeoJson(FeatureCollection.fromFeatures(liveFeatures()))
            return
        }
        val usual = thresholds?.usualMps ?: 1.0
        val features = SpeedChunker.chunks(segments).filter { it.points.size >= 2 }.map { chunk ->
            Feature.fromGeometry(LineString.fromLngLats(chunk.points.map { Point.fromLngLat(it.longitude, it.latitude) })).apply {
                addNumberProperty(PROP_SPEED, chunk.speedMps.takeIf { it.isFinite() } ?: usual)
            }
        }
        src.setGeoJson(FeatureCollection.fromFeatures(features))
    }

    /** Blocs déjà simplifiés du tracé en cours : (segment, bloc, heure du 1er point) → points. */
    private val blockCache = HashMap<Triple<Int, Int, Long>, List<Point>>()

    /**
     * Tracé en cours, d'une seule couleur : simplifié à 1 m par blocs de [BLOCK] points.
     * Les blocs terminés ne changent plus et sont mis en cache ; seul le dernier est
     * redessiné tel quel. Le coût reste faible même après plusieurs heures d'enregistrement.
     */
    private fun liveFeatures(): List<Feature> {
        if (blockCache.size > 4 * segments.sumOf { it.size / BLOCK + 1 }) blockCache.clear()
        return segments.mapIndexedNotNull { si, seg ->
            if (seg.size < 2) return@mapIndexedNotNull null
            val points = ArrayList<Point>()
            val complete = (seg.size - 1) / BLOCK
            for (b in 0 until complete) {
                val from = b * BLOCK
                val block = blockCache.getOrPut(Triple(si, b, seg[from].timeMillis)) {
                    Corridor.simplify(seg.subList(from, from + BLOCK + 1).map { it.point }, SIMPLIFY_M)
                        .map { Point.fromLngLat(it.longitude, it.latitude) }
                }
                points.addAll(block.subList(0, block.size - 1))
            }
            for (i in complete * BLOCK until seg.size) points += seg[i].point.let { Point.fromLngLat(it.longitude, it.latitude) }
            Feature.fromGeometry(LineString.fromLngLats(points))
        }
    }

    companion object {
        private const val BLOCK = 200
        private const val SIMPLIFY_M = 1.0
        private const val SOURCE = "igntrail-track"
        private const val LAYER_CASING = "igntrail-track-casing"
        private const val LAYER_LINE = "igntrail-track-line"
        private const val PROP_SPEED = "speed"

        /** Couleur unique pendant la sortie (les couleurs de vitesse sont calculées à la fin). */
        const val LIVE = "#1E88E5"

        /** Dégradé inversé par rapport au cahier des charges, à la demande de l'utilisateur. */
        const val SLOW = "#D32F2F"
        const val USUAL = "#FBC02D"
        const val FAST = "#2E7D32"
    }
}
