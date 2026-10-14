package fr.igntrail.core.geo

import fr.igntrail.core.model.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Test

class GeoMathTest {
    @Test
    fun `distance nulle entre deux points identiques`() {
        val p = GeoPoint(45.0, 6.0)
        assertEquals(0.0, GeoMath.distanceM(p, p), 1e-9)
    }

    @Test
    fun `un degré de latitude vaut environ 111,2 km`() {
        val d = GeoMath.distanceM(GeoPoint(45.0, 6.0), GeoPoint(46.0, 6.0))
        assertEquals(111_195.0, d, 5.0)
    }

    @Test
    fun `distance Paris - Lyon de référence`() {
        // Notre-Dame de Paris -> Place Bellecour (Lyon) : ≈ 392 km à vol d'oiseau.
        val d = GeoMath.distanceM(GeoPoint(48.8530, 2.3499), GeoPoint(45.7578, 4.8320))
        assertEquals(392_000.0, d, 2_000.0)
    }

    @Test
    fun `petite distance de 10 m précise au centimètre`() {
        // 10 m vers le nord ≈ 0,0000899322° de latitude.
        val d = GeoMath.distanceM(GeoPoint(45.0, 6.0), GeoPoint(45.0 + 10.0 / 111_195.0, 6.0))
        assertEquals(10.0, d, 0.01)
    }

    @Test
    fun `caps cardinaux`() {
        val o = GeoPoint(45.0, 6.0)
        assertEquals(0.0, GeoMath.bearingDeg(o, GeoPoint(45.1, 6.0)), 0.01)
        assertEquals(180.0, GeoMath.bearingDeg(o, GeoPoint(44.9, 6.0)), 0.01)
        assertEquals(90.0, GeoMath.bearingDeg(o, GeoPoint(45.0, 6.1)), 0.1)
        assertEquals(270.0, GeoMath.bearingDeg(o, GeoPoint(45.0, 5.9)), 0.1)
    }

    @Test
    fun `point décalé cohérent avec distance et cap`() {
        val o = GeoPoint(45.9, 6.87)
        for (bearing in listOf(0.0, 45.0, 90.0, 135.0, 200.0, 315.0)) {
            val p = GeoMath.offset(o, bearing, 50.0)
            assertEquals(50.0, GeoMath.distanceM(o, p), 0.01)
            assertEquals(bearing, GeoMath.bearingDeg(o, p), 0.05)
        }
    }

    @Test
    fun `mètres par pixel à l'équateur au zoom 0`() {
        assertEquals(78_271.5, GeoMath.metersPerPixel(0.0, 0.0), 0.5)
        // Divisé par deux à chaque niveau de zoom.
        assertEquals(78_271.5 / 2, GeoMath.metersPerPixel(0.0, 1.0), 0.5)
    }
}
