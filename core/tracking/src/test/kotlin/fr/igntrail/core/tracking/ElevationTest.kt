package fr.igntrail.core.tracking

import fr.igntrail.core.model.ActivityType
import fr.igntrail.core.tracking.SyntheticTrack.Companion.T0
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class ElevationTest {
    private val params = TrackingParams.forActivity(ActivityType.HIKE, 30)

    @Test
    fun `terrain plat bruité - dénivelé quasi nul`() {
        val t = ElevationTracker(8.0, 20f)
        val random = Random(1)
        repeat(3600) { t.add(500.0 + random.nextGaussian() * 4.0, 5f, counting = true) }
        assertTrue("D+ ${t.gain}", t.gain < 20.0)
        assertTrue("D- ${t.loss}", t.loss < 20.0)
        // Sans hystérésis, la somme brute des variations dépasserait plusieurs kilomètres.
    }

    @Test
    fun `montée de 300 m avec bruit GPS`() {
        val track = SyntheticTrack().walk(3000, 1.0, climbMps = 0.1, altitudeNoiseM = 5.0)
        val r = TrackRecorder(params, T0)
        r.feed(track.fixes)
        val s = r.stats()
        assertEquals(300.0, s.elevationGainM, 25.0)
        assertTrue("D- ${s.elevationLossM}", s.elevationLossM < 25.0)
        assertEquals(1000.0, s.minAltitudeM!!, 6.0)
        assertEquals(1300.0, s.maxAltitudeM!!, 6.0)
    }

    @Test
    fun `aller-retour - D+ et D- équilibrés`() {
        val track = SyntheticTrack()
            .walk(2000, 1.0, climbMps = 0.1, altitudeNoiseM = 3.0)
            .walk(2000, 1.0, bearingDeg = 225.0, climbMps = -0.1, altitudeNoiseM = 3.0)
        val r = TrackRecorder(params, T0)
        r.feed(track.fixes)
        val s = r.stats()
        assertEquals(200.0, s.elevationGainM, 20.0)
        assertEquals(200.0, s.elevationLossM, 20.0)
    }

    @Test
    fun `altitudes imprécises ignorées`() {
        val t = ElevationTracker(8.0, 20f)
        t.add(1000.0, 50f, counting = true)
        assertNull(t.current)
        assertEquals(0, t.samples)
    }

    @Test
    fun `le bruit pendant une pause ne compte pas`() {
        val t = ElevationTracker(8.0, 20f)
        t.add(1000.0, 5f, counting = true)
        repeat(100) { t.add(if (it % 2 == 0) 1030.0 else 970.0, 5f, counting = false) }
        assertEquals(0.0, t.gain, 0.0)
        assertEquals(0.0, t.loss, 0.0)
    }

    /** Pression correspondant à une altitude (inverse de la formule du nivellement barométrique). */
    private fun pressure(altitudeM: Double) = 1013.25 * Math.pow(1 - altitudeM / 44_330.0, 1 / 0.190_295)

    @Test
    fun `baromètre - montée de 300 m mesurée finement malgré un GPS bruité`() {
        val t = ElevationTracker(8.0, 20f)
        val random = Random(3)
        for (i in 0..3000) {
            val alt = 1000.0 + i * 0.1
            t.add(alt + random.nextGaussian() * 8.0, 6f, counting = true, pressureHpa = (pressure(alt) + random.nextGaussian() * 0.04).toFloat())
        }
        assertTrue(t.barometric)
        assertEquals(300.0, t.gain, 10.0)
        assertTrue("D- ${t.loss}", t.loss < 10.0)
        // Altitude affichée calée sur le GPS (moyenne) : proche de la vraie altitude.
        assertEquals(1300.0, t.current!!, 15.0)
    }

    @Test
    fun `baromètre - bosses de 8 m comptées, contrairement au GPS seul`() {
        val baro = ElevationTracker(8.0, 20f)
        val gps = ElevationTracker(8.0, 20f)
        for (i in 0..1200) {
            // Ondulations de ±4 m toutes les 4 minutes : sous le seuil GPS (8 m), au-dessus du seuil baro (3 m).
            val alt = 1000.0 + 4.0 * Math.sin(i * 2 * Math.PI / 240.0)
            baro.add(alt, 5f, counting = true, pressureHpa = pressure(alt).toFloat())
            gps.add(alt, 5f, counting = true)
        }
        assertTrue("baro D+ ${baro.gain}", baro.gain > 15.0)
        assertEquals(0.0, gps.gain, 0.0)
    }

    @Test
    fun `conversion pression - altitude`() {
        assertEquals(0.0, ElevationTracker.pressureToAltitude(1013.25), 0.01)
        assertEquals(1000.0, ElevationTracker.pressureToAltitude(pressure(1000.0)), 0.01)
    }

    /** Altitude (m) → pression (hPa), inverse de [ElevationTracker.pressureToAltitude]. */
    private fun pressureAt(altitudeM: Double): Float =
        (1_013.25 * Math.pow(1.0 - altitudeM / 44_330.0, 1.0 / 0.190_295)).toFloat()

    @Test
    fun `baromètre bruité en poche, marche à plat - pas de dénivelé fantôme (retour terrain)`() {
        // 45 min à plat, bruit de 3,6 m (pression qui « respire » dans la poche).
        val random = java.util.Random(9)
        val e = ElevationTracker(8.0, 20f)
        repeat(2_700) { e.add(35.0, 3f, counting = true, pressureHpa = pressureAt(35.0 + random.nextGaussian() * 3.6)) }
        assertTrue("D+ ${e.gain}", e.gain < 25.0)
        assertTrue("D- ${e.loss}", e.loss < 25.0)
    }

    @Test
    fun `baromètre bruité - une vraie montée de 40 m reste comptée`() {
        val random = java.util.Random(4)
        val e = ElevationTracker(8.0, 20f)
        repeat(600) { i ->
            val alt = 35.0 + 40.0 * i / 600.0
            e.add(alt, 3f, counting = true, pressureHpa = pressureAt(alt + random.nextGaussian() * 3.6))
        }
        repeat(120) { e.add(75.0, 3f, counting = true, pressureHpa = pressureAt(75.0 + random.nextGaussian() * 3.6)) }
        assertEquals(40.0, e.gain, 8.0)
    }
}
