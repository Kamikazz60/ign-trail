package fr.igntrail.ui.format

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {
    private fun String.plain() = replace(' ', ' ')

    @Test
    fun distances() {
        assertEquals("850 m", Format.distance(849.6).plain())
        assertEquals("5,8 km", Format.distance(5_800.0).plain())
        assertEquals("12,6 km", Format.distance(12_640.0).plain())
        assertEquals("5,8" to "km", Format.distanceParts(5_800.0))
    }

    @Test
    fun tailles() {
        assertEquals("850 Ko", Format.bytes(850 * 1024L).plain())
        assertEquals("48 Mo", Format.bytes(48L * 1024 * 1024).plain())
        assertEquals("1,5 Go", Format.bytes(1536L * 1024 * 1024).plain())
    }

    @Test
    fun durées() {
        assertEquals("0:42", Format.duration(42_000))
        assertEquals("1:42:16", Format.duration((3600 + 42 * 60 + 16) * 1000L))
        assertEquals("0:00", Format.duration(-5))
    }

    @Test
    fun `vitesse et allure`() {
        assertEquals("3,4", Format.speedKmh(3.4 / 3.6))
        assertEquals("12'30\"", Format.pace(750.0))
        assertEquals("5'00\"", Format.paceFromSpeed(1000.0 / 300.0))
        assertEquals("–", Format.paceFromSpeed(0.1))
        assertEquals("–", Format.pace(null))
    }
}
