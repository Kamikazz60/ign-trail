package fr.igntrail.core.geo

import fr.igntrail.core.model.GeoPoint
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan

/** Tuile « XYZ » Web Mercator (même découpage que le WMTS PM de l'IGN). */
data class TileId(val z: Int, val x: Int, val y: Int)

/** Rectangle géographique. */
data class GeoBounds(val south: Double, val west: Double, val north: Double, val east: Double) {
    val center: GeoPoint get() = GeoPoint((south + north) / 2, (west + east) / 2)

    /** Surface approximative en km². */
    val areaKm2: Double
        get() {
            val h = (north - south) * KM_PER_DEG
            val w = (east - west) * KM_PER_DEG * cos(center.latitude * PI / 180.0)
            return h * w
        }

    fun contains(p: GeoPoint) = p.latitude in south..north && p.longitude in west..east

    companion object {
        private const val KM_PER_DEG = 111.195

        fun of(points: Iterable<GeoPoint>): GeoBounds? {
            val list = points.toList()
            if (list.isEmpty()) return null
            return GeoBounds(
                list.minOf { it.latitude }, list.minOf { it.longitude },
                list.maxOf { it.latitude }, list.maxOf { it.longitude },
            )
        }

        /** Carré de [radiusM] mètres autour d'un point. */
        fun around(p: GeoPoint, radiusM: Double): GeoBounds {
            val sw = GeoMath.offset(GeoMath.offset(p, 180.0, radiusM), 270.0, radiusM)
            val ne = GeoMath.offset(GeoMath.offset(p, 0.0, radiusM), 90.0, radiusM)
            return GeoBounds(sw.latitude, sw.longitude, ne.latitude, ne.longitude)
        }
    }
}

object TileMath {
    /** Abscisse fractionnaire de tuile au zoom [z]. */
    fun x(longitude: Double, z: Int): Double = (longitude + 180.0) / 360.0 * (1 shl z)

    /** Ordonnée fractionnaire de tuile au zoom [z] (0 en haut). */
    fun y(latitude: Double, z: Int): Double {
        val lat = latitude.coerceIn(-85.0511, 85.0511) * PI / 180.0
        return (1.0 - ln(tan(lat) + 1.0 / cos(lat)) / PI) / 2.0 * (1 shl z)
    }

    fun tileAt(p: GeoPoint, z: Int): TileId = TileId(z, clamp(floor(x(p.longitude, z)).toInt(), z), clamp(floor(y(p.latitude, z)).toInt(), z))

    fun countInBounds(b: GeoBounds, z: Int): Long {
        val (x0, x1, y0, y1) = range(b, z)
        return (x1 - x0 + 1).toLong() * (y1 - y0 + 1).toLong()
    }

    fun tilesInBounds(b: GeoBounds, z: Int): Set<TileId> {
        val (x0, x1, y0, y1) = range(b, z)
        val result = HashSet<TileId>()
        for (x in x0..x1) for (y in y0..y1) result += TileId(z, x, y)
        return result
    }

    private fun range(b: GeoBounds, z: Int): List<Int> = listOf(
        clamp(floor(x(b.west, z)).toInt(), z),
        clamp(floor(x(b.east, z)).toInt(), z),
        clamp(floor(y(b.north, z)).toInt(), z),
        clamp(floor(y(b.south, z)).toInt(), z),
    )

    internal fun clamp(v: Int, z: Int) = v.coerceIn(0, (1 shl z) - 1)
}

/**
 * Taille moyenne des tuiles mesurée le 13/10/2026 sur la Géoplateforme
 * (montagne, moyenne montagne, plaine et ville), en kilo-octets.
 */
object TileSizeModel {
    private val VECTOR_KB = mapOf(5 to 40, 6 to 45, 7 to 45, 8 to 45, 9 to 90, 10 to 170, 11 to 100, 12 to 50, 13 to 115, 14 to 90, 15 to 30, 16 to 12, 17 to 6, 18 to 4)
    private val HILLSHADE_KB = mapOf(5 to 35, 6 to 35, 7 to 35, 8 to 35, 9 to 30, 10 to 26, 11 to 22, 12 to 19, 13 to 14, 14 to 8, 15 to 7)

    /** Style, polices et pictogrammes téléchargés une fois par zone. */
    const val STYLE_OVERHEAD_BYTES = 3_000_000L

    const val HILLSHADE_MAX_ZOOM = 15

    /**
     * Les moyennes ci-dessus surestiment d'environ 3 fois (mesuré sur deux téléchargements réels :
     * 24 Mo estimés pour 8 Mo). On divise par 2 : l'estimation reste prudente.
     */
    private const val CALIBRATION = 0.5

    fun bytesPerTile(z: Int): Long = (((VECTOR_KB[z] ?: 12) + (HILLSHADE_KB[z] ?: 0)) * 1024L * CALIBRATION).toLong()

    /** Nombre de ressources : une tuile vectorielle, plus une tuile d'ombrage jusqu'au zoom 15. */
    fun resourcesPerTile(z: Int): Int = if (z <= HILLSHADE_MAX_ZOOM) 2 else 1
}
