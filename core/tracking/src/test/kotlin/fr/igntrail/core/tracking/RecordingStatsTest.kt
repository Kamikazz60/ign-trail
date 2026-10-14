package fr.igntrail.core.tracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecordingStatsTest {
    private fun stats(distance: Double, movingMs: Long, pausedMs: Long = 0, state: RecordingState = RecordingState.MOVING) =
        RecordingStats(
            state, 0L, 0L, distance, movingMs, pausedMs, 0L, 0.0, 0.0,
            null, null, null, 0.0, 0.0, 0, true,
        )

    @Test
    fun `vitesses et allure de référence`() {
        // 12,6 km en 2 h 54 min 32 s de déplacement et 33 min 43 s de pause (maquette).
        val s = stats(12_600.0, (2 * 3600 + 54 * 60 + 32) * 1000L, (33 * 60 + 43) * 1000L)
        assertEquals(4.33, s.avgMovingSpeedMps * 3.6, 0.01)
        assertEquals(3.63, s.avgTotalSpeedMps * 3.6, 0.01)
        // 13 min 51 s par km.
        assertEquals(831.0, s.paceSecondsPerKm!!, 1.0)
        assertEquals((3 * 3600 + 28 * 60 + 15) * 1000L, s.totalMs)
    }

    @Test
    fun `allure non calculée sur une distance trop courte`() {
        assertNull(stats(20.0, 60_000).paceSecondsPerKm)
    }

    @Test
    fun `projection selon l'état`() {
        assertEquals(10_000L, stats(0.0, 0).at(10_000).movingMs)
        assertEquals(10_000L, stats(0.0, 0, state = RecordingState.MANUAL_PAUSED).at(10_000).pausedMs)
        assertEquals(0L, stats(0.0, 0, state = RecordingState.FINISHED).at(10_000).totalMs)
    }
}
