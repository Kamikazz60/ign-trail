package fr.igntrail.map

import fr.igntrail.core.model.LocationFix
import fr.igntrail.core.model.LocationSourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HeadingSelectorTest {
    private fun fix(speed: Float?, bearing: Float?, bearingAcc: Float? = null) = LocationFix(
        45.0, 6.0, 0L, 5f, speedMps = speed, bearingDeg = bearing, bearingAccuracyDeg = bearingAcc,
        source = LocationSourceType.FUSED,
    )

    @Test
    fun `en marche, le cap GPS est prioritaire sur la boussole`() {
        assertEquals(90f, HeadingSelector.select(fix(1.2f, 90f), compassHeading = 10f))
    }

    @Test
    fun `à l'arrêt, la boussole est utilisée`() {
        assertEquals(10f, HeadingSelector.select(fix(0.1f, 90f), compassHeading = 10f))
    }

    @Test
    fun `cap GPS trop imprécis, retour à la boussole`() {
        assertEquals(10f, HeadingSelector.select(fix(1.2f, 90f, bearingAcc = 80f), compassHeading = 10f))
    }

    @Test
    fun `ni cap fiable ni boussole, pas de flèche`() {
        assertNull(HeadingSelector.select(fix(0f, null), compassHeading = null))
    }
}
