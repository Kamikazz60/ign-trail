package fr.igntrail.core.geo

import fr.igntrail.core.model.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TilesTest {
    @Test
    fun `tuile de référence - Paris au zoom 15`() {
        // x = (2,3522 + 180) / 360 × 2^15 = 16598,1 ; y = (1 − ln(tan φ + sec φ)/π) / 2 × 2^15 = 11273,7
        assertEquals(TileId(15, 16_598, 11_273), TileMath.tileAt(GeoPoint(48.8566, 2.3522), 15))
        // Quadrants au zoom 1 : nord-est = (1, 0), sud-ouest = (0, 1).
        assertEquals(TileId(1, 1, 0), TileMath.tileAt(GeoPoint(10.0, 10.0), 1))
        assertEquals(TileId(1, 0, 1), TileMath.tileAt(GeoPoint(-10.0, -10.0), 1))
    }

    @Test
    fun `zoom 0 - une seule tuile pour le monde`() {
        assertEquals(TileId(0, 0, 0), TileMath.tileAt(GeoPoint(45.0, 6.0), 0))
        assertEquals(1L, TileMath.countInBounds(GeoBounds(-80.0, -179.0, 80.0, 179.0), 0))
    }

    @Test
    fun `nombre de tuiles multiplié par 4 à chaque niveau`() {
        val b = GeoBounds(45.80, 6.80, 45.90, 6.95)
        val z14 = TileMath.countInBounds(b, 14)
        val z15 = TileMath.countInBounds(b, 15)
        assertTrue(z15 in (3 * z14)..(5 * z14))
        assertEquals(z15, TileMath.tilesInBounds(b, 15).size.toLong())
    }

    @Test
    fun `surface d'une zone de 10 km sur 10 km`() {
        val b = GeoBounds.around(GeoPoint(45.9, 6.87), 5_000.0)
        assertEquals(100.0, b.areaKm2, 1.0)
    }

    @Test
    fun `bande autour d'un parcours - contient tout point à moins de la marge`() {
        val track = listOf(GeoPoint(45.90, 6.80), GeoPoint(45.92, 6.84), GeoPoint(45.90, 6.88)) // virage en V
        val quads = Corridor.build(listOf(track), marginM = 2_000.0)
        val tiles = Corridor.tiles(quads, 15)
        // Points à 1,9 km du tracé, de part et d'autre et au sommet du virage.
        val probes = listOf(
            GeoMath.offset(track[1], 0.0, 1_900.0),
            GeoMath.offset(track[1], 180.0, 1_900.0),
            GeoMath.offset(track[0], 270.0, 1_900.0),
            GeoMath.offset(track[2], 90.0, 1_900.0),
        )
        probes.forEach { assertTrue("$it hors bande", TileMath.tileAt(it, 15) in tiles) }
        // Un point à 6 km n'y est pas.
        assertTrue(TileMath.tileAt(GeoMath.offset(track[1], 0.0, 6_000.0), 15) !in tiles)
    }

    @Test
    fun `bande moins coûteuse que le rectangle englobant pour un long parcours en diagonale`() {
        val track = (0..40).map { GeoPoint(45.5 + it * 0.01, 6.0 + it * 0.015) } // ≈ 60 km en diagonale
        val quads = Corridor.build(listOf(track), marginM = 2_000.0)
        val corridor = Corridor.tiles(quads, 15).size
        val box = TileMath.countInBounds(Corridor.bounds(quads)!!, 15)
        assertTrue("bande $corridor vs boîte $box", corridor < box / 3)
    }

    @Test
    fun `simplification - garde la forme, supprime l'alignement`() {
        val straight = (0..100).map { GeoPoint(45.0 + it * 0.0001, 6.0) }
        assertEquals(2, Corridor.simplify(straight, 20.0).size)
        val vShape = straight.take(50) + (0..50).map { GeoPoint(45.0049, 6.0 + it * 0.0001) }
        assertEquals(3, Corridor.simplify(vShape, 20.0).size)
    }

    @Test
    fun `couverture partielle et totale`() {
        val a = setOf(TileId(15, 1, 1), TileId(15, 1, 2))
        assertEquals(1.0, coverage(a, a + TileId(15, 9, 9)), 0.0)
        assertEquals(0.5, coverage(a, setOf(TileId(15, 1, 1))), 0.0)
        assertEquals(0.0, coverage(a, emptySet()), 0.0)
    }
}
