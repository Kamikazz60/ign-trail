package fr.igntrail.core.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScaleBarTest {
    @Test
    fun `choisit la plus grande valeur ronde qui tient`() {
        // 2 m/px, 300 px max -> 600 m max -> 500 m.
        val s = ScaleBar.compute(2.0, 300.0)!!
        assertEquals(500.0, s.meters, 0.0)
        assertEquals(250.0, s.widthPx, 1e-9)
        assertEquals("500 m", s.label)
    }

    @Test
    fun `valeur 2,5 utilisée`() {
        // 1 m/px, 260 px -> 260 m -> 250 m.
        assertEquals(250.0, ScaleBar.compute(1.0, 260.0)!!.meters, 0.0)
    }

    @Test
    fun `kilomètres`() {
        val s = ScaleBar.compute(10.0, 120.0)!! // 1200 m -> 1000 m
        assertEquals("1 km", s.label)
        assertEquals("2,5 km", ScaleBar.formatDistance(2500.0))
        assertEquals("1,25 km", ScaleBar.formatDistance(1250.0))
        assertEquals("250 m", ScaleBar.formatDistance(250.0))
        assertEquals("0,5 m", ScaleBar.formatDistance(0.5))
    }

    @Test
    fun `la barre ne dépasse jamais la largeur maximale`() {
        var mpp = 0.05
        while (mpp < 50_000) {
            val s = ScaleBar.compute(mpp, 200.0)!!
            assertTrue(s.widthPx <= 200.0 + 1e-9)
            assertTrue(s.widthPx >= 200.0 * 0.39) // au pire 2 -> 5 ou 5 -> 10 : ≥ 40 %
            mpp *= 1.37
        }
    }

    @Test
    fun `paramètres invalides`() {
        assertNull(ScaleBar.compute(0.0, 100.0))
        assertNull(ScaleBar.compute(1.0, 0.0))
        assertNull(ScaleBar.compute(Double.NaN, 100.0))
    }
}
