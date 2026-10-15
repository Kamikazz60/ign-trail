package fr.igntrail.map

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FixArbiterTest {
    @Test
    fun `sans sortie en cours - la carte affiche ses propres positions`() {
        val a = FixArbiter()
        assertTrue(a.accept(1_000, fromService = false))
        assertTrue(a.accept(2_000, fromService = false))
    }

    @Test
    fun `pendant une sortie - positions du service prioritaires`() {
        val a = FixArbiter()
        assertTrue(a.accept(1_000, fromService = true))
        assertFalse(a.accept(1_500, fromService = false))
        assertTrue(a.accept(2_000, fromService = true))
    }

    @Test
    fun `service silencieux - la carte reprend son abonnement`() {
        val a = FixArbiter(servicePriorityMs = 5_000)
        assertTrue(a.accept(1_000, fromService = true))
        assertTrue(a.accept(7_000, fromService = false))
    }

    @Test
    fun `position déjà vue ou plus ancienne ignorée`() {
        val a = FixArbiter()
        assertTrue(a.accept(2_000, fromService = false))
        assertFalse(a.accept(2_000, fromService = false))
        assertFalse(a.accept(1_000, fromService = false))
    }
}
