package fr.igntrail.map

import fr.igntrail.core.model.GeoPoint
import fr.igntrail.core.model.LocationFix
import fr.igntrail.core.model.LocationSourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraFollowControllerTest {
    private fun fix(lat: Double, lon: Double) =
        LocationFix(lat, lon, 0L, 5f, source = LocationSourceType.FUSED)

    @Test
    fun `premier fix cadre avec le zoom initial, les suivants conservent le zoom`() {
        val c = CameraFollowController()
        assertEquals(CameraCommand(GeoPoint(45.0, 6.0), 15.0), c.onFix(fix(45.0, 6.0)))
        assertEquals(CameraCommand(GeoPoint(45.001, 6.0), null), c.onFix(fix(45.001, 6.0)))
    }

    @Test
    fun `un geste désactive le suivi et la carte ne bouge plus`() {
        val c = CameraFollowController()
        c.onFix(fix(45.0, 6.0))
        c.onUserGesture()
        assertFalse(c.isFollowing)
        assertNull(c.onFix(fix(45.01, 6.01)))
        assertNull(c.onFix(fix(45.02, 6.02)))
    }

    @Test
    fun `le recentrage réactive le suivi sans changer le zoom`() {
        val c = CameraFollowController()
        c.onFix(fix(45.0, 6.0))
        c.onUserGesture()
        val cmd = c.onRecenter(fix(45.02, 6.02))
        assertTrue(c.isFollowing)
        assertEquals(CameraCommand(GeoPoint(45.02, 6.02), null), cmd)
        assertEquals(null, c.onFix(fix(45.03, 6.03))!!.zoom)
    }

    @Test
    fun `position approximative cadrée une seule fois puis remplacée par le GPS`() {
        val c = CameraFollowController()
        assertEquals(13.0, c.onApproximatePosition(GeoPoint(44.0, 5.0))!!.zoom)
        assertNull(c.onApproximatePosition(GeoPoint(44.0, 5.0)))
        assertEquals(15.0, c.onFix(fix(45.0, 6.0))!!.zoom)
    }

    @Test
    fun `si l'utilisateur bouge la carte avant le premier fix, le GPS ne la recadre pas`() {
        val c = CameraFollowController()
        c.onApproximatePosition(GeoPoint(44.0, 5.0))
        c.onUserGesture()
        assertNull(c.onFix(fix(45.0, 6.0)))
    }

    @Test
    fun `recentrage avant le premier fix active le suivi et attend le GPS`() {
        val c = CameraFollowController()
        c.onUserGesture()
        assertNull(c.onRecenter(null))
        assertTrue(c.isFollowing)
        assertEquals(15.0, c.onFix(fix(45.0, 6.0))!!.zoom)
    }

    @Test
    fun `restauration après recréation`() {
        val c = CameraFollowController()
        c.restore(following = false, alreadyCentered = true)
        assertNull(c.onFix(fix(45.0, 6.0)))
        c.restore(following = true, alreadyCentered = true)
        assertNull(c.onFix(fix(45.0, 6.0))!!.zoom)
    }
}
