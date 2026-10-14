package fr.igntrail.core.navigation

import fr.igntrail.core.geo.GeoMath
import fr.igntrail.core.model.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigatorTest {
    private val start = GeoPoint(45.9, 6.87)

    /** Itinéraire en L : 1 km vers le nord, puis 1 km vers l'est. */
    private val lShape = listOf(start, GeoMath.offset(start, 0.0, 1000.0), GeoMath.offset(GeoMath.offset(start, 0.0, 1000.0), 90.0, 1000.0))

    private fun walkAlong(points: List<GeoPoint>, stepM: Double): List<GeoPoint> = buildList {
        for (i in 0 until points.lastIndex) {
            val a = points[i]
            val b = points[i + 1]
            val d = GeoMath.distanceM(a, b)
            val bearing = GeoMath.bearingDeg(a, b)
            var x = 0.0
            while (x < d) {
                add(GeoMath.offset(a, bearing, x))
                x += stepM
            }
        }
        add(points.last())
    }

    @Test
    fun `géométrie - longueur, point intermédiaire et projection`() {
        val g = RouteGeometry(lShape)
        assertEquals(2000.0, g.lengthM, 1.0)
        assertEquals(0.0, GeoMath.distanceM(g.pointAt(1500.0), GeoMath.offset(lShape[1], 90.0, 500.0)), 1.0)
        val p = g.project(GeoMath.offset(GeoMath.offset(start, 0.0, 400.0), 90.0, 25.0))
        assertEquals(400.0, p.alongM, 1.0)
        assertEquals(25.0, p.crossTrackM, 0.5)
    }

    @Test
    fun `progression et distance restante le long du tracé`() {
        val nav = Navigator(lShape)
        val (s, _) = nav.update(GeoMath.offset(lShape[1], 90.0, 300.0))
        assertEquals(1300.0, s.alongM, 2.0)
        assertEquals(700.0, s.remainingM, 2.0)
        assertFalse(s.offRoute)
    }

    @Test
    fun `la flèche suit le tracé et non la destination finale`() {
        val nav = Navigator(lShape)
        // Au milieu de la branche nord, l'arrivée est au nord-est ; la flèche doit indiquer le nord.
        val (s, _) = nav.update(GeoMath.offset(start, 0.0, 500.0))
        assertEquals(0.0, s.targetBearingDeg, 2.0)
        val towardsEnd = GeoMath.bearingDeg(GeoMath.offset(start, 0.0, 500.0), lShape.last())
        assertTrue(towardsEnd > 20.0)
        // Juste avant le virage, elle tourne vers l'est.
        val (s2, _) = nav.update(GeoMath.offset(start, 0.0, 995.0))
        assertTrue("cap ${s2.targetBearingDeg}", s2.targetBearingDeg in 45.0..95.0)
    }

    @Test
    fun `sortie de parcours confirmée après plusieurs mesures, une seule alerte`() {
        val nav = Navigator(lShape, GuidanceParams(toleranceM = 40.0))
        nav.update(GeoMath.offset(start, 0.0, 100.0))
        val away = (1..10).map { GeoMath.offset(GeoMath.offset(start, 0.0, 100.0 + it * 5), 270.0, 80.0) }
        val events = away.map { nav.update(it).second }
        assertNull(events[0])
        assertNull(events[1])
        assertEquals(GuidanceEvent.OFF_ROUTE, events[2])
        assertTrue(events.drop(3).all { it == null }) // pas d'alerte répétée
    }

    @Test
    fun `hystérésis - le bruit GPS autour de la tolérance ne fait pas clignoter l'alerte`() {
        val nav = Navigator(lShape, GuidanceParams(toleranceM = 40.0))
        val events = (0 until 60).map { i ->
            val cross = if (i % 2 == 0) 35.0 else 45.0 // oscillation autour de 40 m
            nav.update(GeoMath.offset(GeoMath.offset(start, 0.0, 100.0 + i * 3), 270.0, cross)).second
        }
        assertTrue(events.all { it == null })
    }

    @Test
    fun `retour sur le parcours signalé une fois`() {
        val nav = Navigator(lShape, GuidanceParams(toleranceM = 40.0))
        repeat(4) { nav.update(GeoMath.offset(GeoMath.offset(start, 0.0, 200.0), 270.0, 100.0)) }
        val e1 = nav.update(GeoMath.offset(start, 0.0, 210.0)).second
        val e2 = nav.update(GeoMath.offset(start, 0.0, 215.0)).second
        assertNull(e1)
        assertEquals(GuidanceEvent.BACK_ON_ROUTE, e2)
    }

    @Test
    fun `hors parcours - la flèche ramène vers le tracé`() {
        val nav = Navigator(lShape)
        val pos = GeoMath.offset(GeoMath.offset(start, 0.0, 500.0), 270.0, 200.0) // 200 m à l'ouest
        repeat(3) { nav.update(pos) }
        val (s, _) = nav.update(pos)
        assertTrue(s.offRoute)
        assertEquals(90.0, s.targetBearingDeg, 3.0) // vers l'est, droit sur le tracé
        assertEquals(200.0, s.crossTrackM, 2.0)
    }

    @Test
    fun `aller-retour - pas de saut sur la branche du retour`() {
        val far = GeoMath.offset(start, 0.0, 1000.0)
        val outAndBack = listOf(start, far, start) // même sentier à l'aller et au retour
        val nav = Navigator(outAndBack)
        var last: GuidanceState? = null
        for (p in walkAlong(listOf(start, far), 20.0)) last = nav.update(p).first
        assertEquals(1000.0, last!!.alongM, 25.0)
        // Au début du retour, la progression continue (pas de retour à 0).
        val back = nav.update(GeoMath.offset(far, 180.0, 100.0)).first
        assertEquals(1100.0, back.alongM, 25.0)
        assertEquals(900.0, back.remainingM, 25.0)
    }

    @Test
    fun `arrivée signalée une fois`() {
        val nav = Navigator(lShape)
        val events = walkAlong(lShape, 25.0).map { nav.update(it).second }
        assertEquals(1, events.count { it == GuidanceEvent.ARRIVED })
        assertTrue(nav.update(lShape.last()).first.arrived)
    }

    @Test
    fun `sens inverse - progression depuis l'autre extrémité`() {
        val nav = Navigator(lShape.asReversed())
        val (s, _) = nav.update(GeoMath.offset(lShape[1], 90.0, 300.0))
        assertEquals(700.0, s.alongM, 2.0)
        assertEquals(1300.0, s.remainingM, 2.0)
    }

    @Test
    fun `direction du départ à vol d'oiseau`() {
        val d = DirectionTo.compute(GeoMath.offset(start, 45.0, 1500.0), start)
        assertEquals(1500.0, d.distanceM, 1.0)
        assertEquals(225.0, d.bearingDeg, 0.5)
    }
}
