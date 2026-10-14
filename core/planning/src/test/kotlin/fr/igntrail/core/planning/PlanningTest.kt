package fr.igntrail.core.planning

import fr.igntrail.core.geo.GeoMath
import fr.igntrail.core.model.ActivityType
import fr.igntrail.core.model.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanningTest {
    private val o = GeoPoint(45.9, 6.87)
    private fun p(northM: Double, eastM: Double = 0.0) = GeoMath.offset(GeoMath.offset(o, 0.0, northM), 90.0, eastM)

    @Test
    fun `éditeur - ajout, déplacement, suppression, réorganisation`() {
        val e = PlanEditor()
        val a = e.add(p(0.0))
        val b = e.add(p(100.0))
        val c = e.add(p(200.0))
        assertEquals(listOf(a.id, b.id, c.id), e.points.map { it.id })
        e.move(b.id, p(150.0))
        assertEquals(p(150.0), e.points[1].position)
        e.shift(c.id, -1)
        assertEquals(listOf(a.id, c.id, b.id), e.points.map { it.id })
        e.delete(a.id)
        assertEquals(listOf(c.id, b.id), e.points.map { it.id })
        e.reverse()
        assertEquals(listOf(b.id, c.id), e.points.map { it.id })
        e.insert(0, p(-50.0))
        assertEquals(3, e.points.size)
    }

    @Test
    fun `annuler et rétablir`() {
        val e = PlanEditor()
        e.add(p(0.0))
        val b = e.add(p(100.0))
        e.delete(b.id)
        assertEquals(1, e.points.size)
        assertTrue(e.undo())
        assertEquals(2, e.points.size)
        assertTrue(e.undo())
        assertEquals(1, e.points.size)
        assertTrue(e.redo())
        assertEquals(2, e.points.size)
        // Une nouvelle action efface le « rétablir ».
        e.add(p(300.0))
        assertFalse(e.canRedo)
        e.clear()
        assertTrue(e.points.isEmpty())
        assertTrue(e.undo())
        assertEquals(3, e.points.size)
    }

    @Test
    fun `opérations sans effet n'encombrent pas l'historique`() {
        val e = PlanEditor()
        e.shift(42, 1)
        e.delete(42)
        e.clear()
        assertFalse(e.canUndo)
    }

    @Test
    fun `liaison calculée - petit écart au chemin signalé en ligne directe`() {
        val routed = listOf(p(0.0, 40.0), p(500.0, 40.0)) // chemin à 40 m à l'est
        val leg = Leg.fromRouted(p(0.0), p(500.0), routed)!!
        assertEquals(listOf(SectionKind.STRAIGHT, SectionKind.ROUTED, SectionKind.STRAIGHT), leg.sections.map { it.kind })
        assertEquals(80.0, leg.straightM, 1.0)
        assertEquals(580.0, leg.lengthM, 2.0)
    }

    @Test
    fun `liaison calculée - raccrochage lointain refusé (point en mer, hors réseau)`() {
        val routed = listOf(p(0.0, 2_000.0), p(500.0, 2_000.0))
        assertNull(Leg.fromRouted(p(0.0), p(500.0), routed))
        assertNull(Leg.fromRouted(p(0.0), p(500.0), listOf(p(0.0))))
    }

    @Test
    fun `liaison directe - jamais présentée comme un chemin`() {
        val leg = Leg.straight(p(0.0), p(1000.0), routingFailed = true)
        assertEquals(SectionKind.STRAIGHT, leg.sections.single().kind)
        assertEquals(1000.0, leg.straightM, 1.0)
        assertTrue(leg.routingFailed)
    }

    @Test
    fun `géométrie assemblée sans doublon aux jonctions`() {
        val l1 = Leg.straight(p(0.0), p(100.0))
        val l2 = Leg.fromRouted(p(100.0), p(300.0), listOf(p(100.0), p(200.0), p(300.0)))!!
        val g = PlannedGeometry(listOf(l1, l2))
        assertEquals(4, g.points.size)
        assertEquals(300.0, g.lengthM, 1.0)
        assertEquals(100.0, g.straightM, 1.0)
    }

    @Test
    fun `rééchantillonnage régulier, extrémités et virages conservés`() {
        val line = listOf(p(0.0), p(100.0), p(100.0, 50.0))
        val s = Sampling.resample(line, 20.0)
        assertEquals(150.0, s.distancesM.last(), 0.5)
        assertTrue(s.points.contains(p(100.0)))
        assertEquals(line.last(), s.points.last())
        s.distancesM.zipWithNext { a, b -> assertTrue(b - a <= 20.0 + 1e-6) }
        assertEquals(20.0, Sampling.stepFor(1_000.0), 0.0)
        assertEquals(50.0, Sampling.stepFor(200_000.0), 0.0)
    }

    @Test
    fun `profil - dénivelés, trous comblés, altitude interpolée`() {
        val d = (0..10).map { it * 100.0 }
        val raw: List<Double?> = listOf(1000.0, 1010.0, 1020.0, null, 1040.0, 1050.0, 1040.0, 1030.0, 1020.0, 1010.0, 1000.0)
        val profile = ElevationProfile.from(d, raw)!!
        assertEquals(1030.0, profile.elevationsM[3], 1e-9)
        val (gain, loss) = profile.gainLoss()
        assertEquals(50.0, gain, 0.0)
        assertEquals(50.0, loss, 0.0)
        assertEquals(1005.0, profile.elevationAt(50.0)!!, 1e-9)
        assertEquals(1000.0, profile.min!!, 0.0)
        assertEquals(1050.0, profile.max!!, 0.0)
        // Trop de données manquantes (hors France) : pas de profil.
        assertNull(ElevationProfile.from(d, List(11) { if (it < 5) 1000.0 else null }))
    }

    @Test
    fun `durée DIN 33466 - plat, montée et profil personnel`() {
        // 12 km à plat en randonnée (4 km/h) : 3 h.
        assertEquals(3 * 3_600_000L, DurationEstimator.estimateMs(12_000.0, 0.0, 0.0, ActivityType.HIKE))
        // 8 km et 900 m de montée : horizontal 2 h, vertical 3 h -> 3 + 1 = 4 h.
        assertEquals(4 * 3_600_000L, DurationEstimator.estimateMs(8_000.0, 900.0, 0.0, ActivityType.HIKE))
        // Vitesse personnelle de 5 km/h sur le plat : 10 km -> 2 h.
        assertEquals(2 * 3_600_000L, DurationEstimator.estimateMs(10_000.0, 0.0, 0.0, ActivityType.HIKE, personalFlatKmh = 5.0))
        // Vitesse personnelle aberrante ignorée.
        assertEquals(3 * 3_600_000L, DurationEstimator.estimateMs(12_000.0, 0.0, 0.0, ActivityType.HIKE, personalFlatKmh = 0.2))
    }
}
