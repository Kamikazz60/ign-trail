package fr.igntrail.core.stats

import fr.igntrail.core.geo.GeoMath
import fr.igntrail.core.model.ActivityType
import fr.igntrail.core.model.GeoPoint
import fr.igntrail.core.tracking.TimedPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

class StatsTest {
    private val o = GeoPoint(45.9, 6.87)

    /** Marche à vitesse constante : un point par seconde. */
    private fun walk(start: GeoPoint, t0: Long, seconds: Int, speedMps: Double): List<TimedPoint> =
        (0..seconds).map { TimedPoint(GeoMath.offset(start, 0.0, it * speedMps), t0 + it * 1000L) }

    @Test
    fun `portions d'au moins 60 m et 20 s, vitesse fidèle`() {
        val seg = walk(o, 0, 600, 1.0) // 600 m à 3,6 km/h
        val chunks = SpeedChunker.chunks(listOf(seg))
        assertTrue(chunks.dropLast(1).all { it.distanceM >= 59.0 })
        assertTrue(chunks.last().distanceM >= 30.0) // reste du segment
        chunks.forEach { assertEquals(1.0, it.speedMps, 0.01) }
        assertEquals(600.0, chunks.sumOf { it.distanceM }, 1.0)
        // Continuité : chaque portion commence où la précédente finit.
        chunks.zipWithNext { a, b -> assertEquals(a.points.last(), b.points.first()) }
    }

    @Test
    fun `changement de rythme visible`() {
        val slow = walk(o, 0, 300, 0.6)
        val fast = walk(slow.last().point, slow.last().timeMillis, 300, 2.0).drop(1)
        val chunks = SpeedChunker.chunks(listOf(slow + fast))
        assertEquals(0.6, chunks.first().speedMps, 0.02)
        assertEquals(2.0, chunks.last().speedMps, 0.05)
    }

    @Test
    fun `un arrêt prolongé n'assombrit pas la vitesse`() {
        val before = walk(o, 0, 120, 1.2)
        // 10 minutes plus tard, reprise au même endroit (pause automatique).
        val after = walk(before.last().point, before.last().timeMillis + 600_000, 120, 1.2)
        val chunks = SpeedChunker.chunks(listOf(before + after))
        chunks.filter { it.speedMps.isFinite() }.forEach { assertEquals(1.2, it.speedMps, 0.05) }
    }

    @Test
    fun `couleurs d'une sortie - jaune à sa moyenne, rouge et vert autour`() {
        // Montée lente (0,6 m/s) puis plat (1,2) puis descente rapide (1,8) : 500 m chacune environ.
        val slow = walk(o, 0, 830, 0.6)
        val mid = walk(slow.last().point, slow.last().timeMillis, 420, 1.2).drop(1)
        val fast = walk(mid.last().point, mid.last().timeMillis, 280, 1.8).drop(1)
        val chunks = SpeedChunker.chunks(listOf(slow + mid + fast))
        val t = SpeedThresholds.forOuting(chunks, averageMps = 1.0)!!
        assertEquals(1.0, t.usualMps, 0.0)
        assertTrue("lent ${t.slowMps}", t.slowMps <= 0.65)
        assertTrue("rapide ${t.fastMps}", t.fastMps >= 1.75)
    }

    @Test
    fun `couleurs d'une sortie très régulière - écart minimal, pas de couleurs extrêmes partout`() {
        val chunks = SpeedChunker.chunks(listOf(walk(o, 0, 1000, 1.0)))
        val t = SpeedThresholds.forOuting(chunks, averageMps = 1.0)!!
        assertEquals(0.92, t.slowMps, 1e-9)
        assertEquals(1.08, t.fastMps, 1e-9)
    }

    @Test
    fun `sortie trop courte - pas de couleurs`() {
        assertNull(SpeedThresholds.forOuting(SpeedChunker.chunks(listOf(walk(o, 0, 100, 1.0))), 1.0))
    }

    @Test
    fun `tableau de bord - cumuls, mois vides, records`() {
        fun at(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        val list = listOf(
            OutingSummary(at(2026, 8, 3), ActivityType.HIKE, 12_000.0, 3 * 3_600_000L, 4 * 3_600_000L, 800.0),
            OutingSummary(at(2026, 8, 20), ActivityType.RUN, 10_000.0, 3_600_000L, 3_700_000L, 100.0),
            OutingSummary(at(2026, 10, 1), ActivityType.HIKE, 8_000.0, 2 * 3_600_000L, 2 * 3_600_000L, 400.0),
        )
        val months = Dashboard.monthly(list, 3, at(2026, 10, 13), ZoneOffset.UTC)
        assertEquals(listOf(YearMonth.of(2026, 8), YearMonth.of(2026, 9), YearMonth.of(2026, 10)), months.map { it.month })
        assertEquals(22_000.0, months[0].totals.distanceM, 0.0)
        assertEquals(0, months[1].totals.count)
        val hikes = Dashboard.filter(list, ActivityType.HIKE)
        assertEquals(1_200.0, Totals.of(hikes).gainM, 0.0)
        val r = Dashboard.records(list)
        assertEquals(12_000.0, r.longest!!.distanceM, 0.0)
        assertEquals(ActivityType.RUN, r.fastest!!.activity)
    }

    @Test
    fun `profil enregistré - distance continue, altitude lissée, point sur la carte`() {
        val seg1 = (0..100).map { TimedPoint(GeoMath.offset(o, 0.0, it * 10.0), it * 1000L, 1000.0 + it + if (it % 2 == 0) 5.0 else -5.0) }
        val seg2 = (0..100).map { TimedPoint(GeoMath.offset(o, 90.0, 5_000.0 + it * 10.0), 500_000L + it * 1000L, 1100.0) }
        val tp = TrackProfile.of(listOf(seg1, seg2))
        val p = tp.profile!!
        // 2 × 1000 m, sans les 5 km de la coupure.
        assertEquals(2000.0, p.distancesM.last(), 1.0)
        // Bruit de ±5 m effacé par le lissage.
        assertTrue(p.elevationsM.take(90).zipWithNext().all { (a, b) -> kotlin.math.abs(b - a) < 2.0 })
        assertEquals(seg1[50].point.latitude, tp.pointAt(500.0)!!.latitude, 1e-6)
        assertTrue(!tp.barometric)
    }
}
