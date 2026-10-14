package fr.igntrail.core.geo

import fr.igntrail.core.model.GeoPoint
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot

/** Quadrilatère convexe (4 sommets, sens quelconque). */
typealias Quad = List<GeoPoint>

/**
 * Bande de [marginM] mètres autour d'un parcours (§4, téléchargement « autour
 * d'un itinéraire »). Le tracé est simplifié, puis chaque tronçon devient un
 * rectangle prolongé de la marge aux deux extrémités : l'union des rectangles
 * contient la bande exacte, coins des virages compris.
 */
object Corridor {
    fun build(segments: List<List<GeoPoint>>, marginM: Double): List<Quad> =
        segments.filter { it.isNotEmpty() }.flatMap { seg ->
            val simplified = simplify(seg, toleranceM = (marginM / 4).coerceAtLeast(20.0))
            if (simplified.size == 1) {
                listOf(rectangle(simplified[0], simplified[0], marginM))
            } else {
                simplified.zipWithNext { a, b -> rectangle(a, b, marginM) }
            }
        }

    /** Tuiles du zoom [z] qui touchent au moins un quadrilatère. */
    fun tiles(quads: List<Quad>, z: Int): Set<TileId> {
        val result = HashSet<TileId>()
        for (quad in quads) {
            val pts = quad.map { TileMath.x(it.longitude, z) to TileMath.y(it.latitude, z) }
            val x0 = TileMath.clamp(floor(pts.minOf { it.first }).toInt(), z)
            val x1 = TileMath.clamp(floor(pts.maxOf { it.first }).toInt(), z)
            val y0 = TileMath.clamp(floor(pts.minOf { it.second }).toInt(), z)
            val y1 = TileMath.clamp(floor(pts.maxOf { it.second }).toInt(), z)
            for (x in x0..x1) for (y in y0..y1) {
                if (intersectsUnitSquare(pts, x.toDouble(), y.toDouble())) result += TileId(z, x, y)
            }
        }
        return result
    }

    fun bounds(quads: List<Quad>): GeoBounds? = GeoBounds.of(quads.flatten())

    private fun rectangle(a: GeoPoint, b: GeoPoint, m: Double): Quad {
        val length = GeoMath.distanceM(a, b)
        val bearing = if (length < 1.0) 0.0 else GeoMath.bearingDeg(a, b)
        val back = GeoMath.offset(a, bearing + 180.0, m)
        val front = GeoMath.offset(b, bearing, m)
        return listOf(
            GeoMath.offset(back, bearing - 90.0, m),
            GeoMath.offset(front, bearing - 90.0, m),
            GeoMath.offset(front, bearing + 90.0, m),
            GeoMath.offset(back, bearing + 90.0, m),
        )
    }

    /** Test des axes séparateurs entre un polygone convexe et la tuile [x, x+1] × [y, y+1]. */
    private fun intersectsUnitSquare(poly: List<Pair<Double, Double>>, x: Double, y: Double): Boolean {
        if (poly.maxOf { it.first } < x || poly.minOf { it.first } > x + 1) return false
        if (poly.maxOf { it.second } < y || poly.minOf { it.second } > y + 1) return false
        val square = listOf(x to y, x + 1 to y, x + 1 to y + 1, x to y + 1)
        for (i in poly.indices) {
            val (ax, ay) = poly[i]
            val (bx, by) = poly[(i + 1) % poly.size]
            val nx = -(by - ay)
            val ny = bx - ax
            if (nx == 0.0 && ny == 0.0) continue
            val p = poly.map { it.first * nx + it.second * ny }
            val s = square.map { it.first * nx + it.second * ny }
            if (p.max() < s.min() || s.max() < p.min()) return false
        }
        return true
    }

    /** Douglas-Peucker en mètres (projection plane locale). */
    fun simplify(points: List<GeoPoint>, toleranceM: Double): List<GeoPoint> {
        if (points.size <= 2) return points
        val origin = points.first()
        val kx = 111_195.0 * cos(origin.latitude * PI / 180.0)
        val xy = points.map { (it.longitude - origin.longitude) * kx to (it.latitude - origin.latitude) * 111_195.0 }
        val keep = BooleanArray(points.size)
        keep[0] = true
        keep[points.lastIndex] = true
        val stack = ArrayDeque<Pair<Int, Int>>().apply { add(0 to points.lastIndex) }
        while (stack.isNotEmpty()) {
            val (i, j) = stack.removeLast()
            var maxD = -1.0
            var index = -1
            for (k in i + 1 until j) {
                val d = distanceToSegment(xy[k], xy[i], xy[j])
                if (d > maxD) {
                    maxD = d
                    index = k
                }
            }
            if (index >= 0 && maxD > toleranceM) {
                keep[index] = true
                stack.add(i to index)
                stack.add(index to j)
            }
        }
        return points.filterIndexed { i, _ -> keep[i] }
    }

    private fun distanceToSegment(p: Pair<Double, Double>, a: Pair<Double, Double>, b: Pair<Double, Double>): Double {
        val dx = b.first - a.first
        val dy = b.second - a.second
        val len2 = dx * dx + dy * dy
        if (len2 == 0.0) return hypot(p.first - a.first, p.second - a.second)
        val t = (((p.first - a.first) * dx + (p.second - a.second) * dy) / len2).coerceIn(0.0, 1.0)
        return hypot(p.first - (a.first + t * dx), p.second - (a.second + t * dy))
    }
}

/** Part des tuiles [needed] présentes dans [available] (1.0 = entièrement couvert). */
fun coverage(needed: Set<TileId>, available: Set<TileId>): Double =
    if (needed.isEmpty()) 1.0 else needed.count { it in available }.toDouble() / needed.size
