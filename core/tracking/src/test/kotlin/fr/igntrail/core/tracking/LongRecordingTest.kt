package fr.igntrail.core.tracking

import fr.igntrail.core.model.ActivityType
import fr.igntrail.core.tracking.SyntheticTrack.Companion.T0
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase 7 : enregistrement de plusieurs heures (cahier des charges, test C). */
class LongRecordingTest {
    /** 6 h de randonnée : marche, montées, arrêts, pertes de signal, à 1 mesure/s. */
    private fun sixHours(): SyntheticTrack {
        val t = SyntheticTrack(seed = 11)
        repeat(12) { leg ->
            t.walk(1_200, 1.2, bearingDeg = 30.0 * leg, noiseM = 3.0, accuracyM = 6f, climbMps = if (leg % 2 == 0) 0.15 else -0.12, altitudeNoiseM = 2.0)
            t.stand(240)
            if (leg % 4 == 3) t.silentMove(90, 1.2, bearingDeg = 30.0 * leg)
            t.walk(120, 1.0, bearingDeg = 30.0 * leg + 90, noiseM = 3.0, accuracyM = 6f)
        }
        return t
    }

    @Test
    fun `6 h de randonnée - distance, chronomètres et dénivelé cohérents`() {
        val track = sixHours()
        val r = TrackRecorder(TrackingParams.forActivity(ActivityType.HIKE, 30), T0)
        r.feed(track.fixes)
        val s = r.stats(track.time)
        assertEquals(track.trueDistanceM, s.distanceM, track.trueDistanceM * 0.03)
        assertEquals((track.time - T0).toDouble(), s.totalMs.toDouble(), 1_000.0)
        // 12 arrêts de 4 min : au moins 3 min de pause détectée par arrêt.
        assertTrue("pause ${s.pausedMs / 60_000} min", s.pausedMs >= 12 * 180_000L)
        // Montées : 6 × 1 200 s × 0,15 m/s = 1 080 m.
        assertEquals(1_080.0, s.elevationGainM, 1_080.0 * 0.1)
    }

    @Test
    fun `6 h à vitesse élevée, un point par seconde - calcul et instantanés du tracé rapides`() {
        val track = SyntheticTrack(seed = 3).walk(6 * 3_600, 14.0, noiseM = 3.0, accuracyM = 6f)
        val r = TrackRecorder(TrackingParams.forActivity(ActivityType.OTHER, 0), T0)
        val start = System.nanoTime()
        var points = 0
        for (fix in track.fixes) {
            r.process(fix)
            // Comme l'écran carte : un instantané à chaque position.
            points = r.timedSegments().sumOf { it.size }
        }
        val ms = (System.nanoTime() - start) / 1_000_000
        assertTrue("$points points", points > 20_000)
        assertTrue("$ms ms pour ${track.fixes.size} positions", ms < 5_000)
    }

    @Test
    fun `instantané figé - les ajouts suivants ne le modifient pas`() {
        val list = AppendOnlyList<Int>()
        repeat(300) { list.add(it) }
        val snap = list.snapshot()
        repeat(1_000) { list.add(1_000 + it) }
        assertEquals(300, snap.size)
        assertEquals((0 until 300).toList(), snap)
        assertEquals(1_300, list.snapshot().size)
    }
}
