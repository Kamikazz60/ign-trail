package fr.igntrail.core.tracking

import fr.igntrail.core.model.ActivityType
import fr.igntrail.core.model.GeoPoint
import fr.igntrail.core.tracking.SyntheticTrack.Companion.T0
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackRecorderTest {
    private fun hike(autoPauseS: Int = 30) = TrackingParams.forActivity(ActivityType.HIKE, autoPauseS)

    private fun assertTimesAddUp(s: RecordingStats, expectedTotalMs: Long) {
        assertEquals("total = déplacement + pause + interruption", expectedTotalMs, s.totalMs)
    }

    @Test
    fun `marche régulière - distance fidèle à 3 pour cent malgré le bruit GPS`() {
        val track = SyntheticTrack().walk(1800, 1.2, noiseM = 2.5) // 30 min à 4,3 km/h ≈ 2160 m
        val r = TrackRecorder(hike(), T0)
        r.feed(track.fixes)
        val s = r.stats()
        assertEquals(track.trueDistanceM, s.distanceM, track.trueDistanceM * 0.03)
        assertEquals(1800_000L, s.movingMs)
        assertEquals(0L, s.pausedMs)
        assertEquals(4.32, s.avgMovingSpeedMps * 3.6, 0.15)
    }

    @Test
    fun `à l'arrêt le bruit GPS ne crée pas de distance et la pause automatique se déclenche`() {
        val track = SyntheticTrack().walk(120, 1.2).stand(600, noiseM = 4.0)
        val r = TrackRecorder(hike(autoPauseS = 30), T0)
        r.feed(track.fixes)
        val s = r.stats()
        assertEquals(RecordingState.AUTO_PAUSED, s.state)
        // Distance de la marche seulement (≈ 144 m), le bruit pendant 10 min ne compte pas.
        assertEquals(144.0, s.distanceM, 12.0)
        // Le temps d'immobilité est entièrement compté en pause, y compris le délai de détection.
        assertEquals(120_000.0, s.movingMs.toDouble(), 8_000.0)
        assertEquals(600_000.0, s.pausedMs.toDouble(), 8_000.0)
        assertTimesAddUp(s, 720_000L)
        assertEquals(0.0, s.currentSpeedMps, 0.0)
    }

    @Test
    fun `reprise automatique dès qu'un mouvement réel est détecté`() {
        val track = SyntheticTrack().walk(60, 1.2).stand(120).walk(120, 1.2)
        val r = TrackRecorder(hike(), T0)
        r.feed(track.fixes)
        val s = r.stats()
        assertEquals(RecordingState.MOVING, s.state)
        assertEquals(track.trueDistanceM, s.distanceM, 15.0)
        assertEquals(120_000.0, s.pausedMs.toDouble(), 10_000.0)
        assertEquals(1, r.segments().size) // pas de coupure du tracé pour une pause auto
    }

    @Test
    fun `pause automatique désactivée - le temps d'arrêt reste du déplacement`() {
        val track = SyntheticTrack().walk(60, 1.2).stand(300)
        val r = TrackRecorder(hike(autoPauseS = 0), T0)
        r.feed(track.fixes)
        val s = r.stats()
        assertEquals(RecordingState.MOVING, s.state)
        assertEquals(360_000L, s.movingMs)
        assertEquals(72.0, s.distanceM, 12.0)
    }

    @Test
    fun `marche très lente en montée raide sans pause automatique intempestive`() {
        // 0,35 m/s horizontal : typique d'une montée très raide.
        val track = SyntheticTrack().walk(600, 0.35, noiseM = 2.0)
        val r = TrackRecorder(hike(autoPauseS = 30), T0)
        r.feed(track.fixes)
        val s = r.stats()
        assertEquals(RecordingState.MOVING, s.state)
        assertTrue("pause détectée à tort : ${s.pausedMs} ms", s.pausedMs < 30_000)
        assertEquals(track.trueDistanceM, s.distanceM, track.trueDistanceM * 0.06)
    }

    @Test
    fun `la pause manuelle n'est jamais annulée par le mouvement`() {
        val track = SyntheticTrack().walk(60, 1.2)
        val r = TrackRecorder(hike(), T0)
        r.feed(track.fixes)
        r.pauseManually(track.time)
        val distanceBefore = r.stats().distanceM
        track.walk(300, 1.5) // l'utilisateur marche pendant la pause
        val qualities = r.feed(track.fixes.takeLast(300))
        assertTrue(qualities.all { it == PointQuality.PAUSED })
        val s = r.stats()
        assertEquals(RecordingState.MANUAL_PAUSED, s.state)
        assertEquals(distanceBefore, s.distanceM, 0.0)
        assertEquals(60_000L, s.movingMs)
        assertEquals(300_000L, s.pausedMs)
        assertTimesAddUp(s, 360_000L)
    }

    @Test
    fun `reprise manuelle - nouveau segment, pas de liaison à travers la pause`() {
        val track = SyntheticTrack().walk(60, 1.2)
        val r = TrackRecorder(hike(), T0)
        r.feed(track.fixes)
        r.pauseManually(track.time)
        track.silentMove(300, 2.0) // 600 m parcourus pendant la pause (ex. en voiture)
        r.resumeManually(track.time)
        track.walk(60, 1.2)
        r.feed(track.fixes.takeLast(60))
        val s = r.stats()
        assertEquals(2, r.segments().size)
        assertEquals(144.0, s.distanceM, 15.0) // 2 × 72 m, sans les 600 m
        assertEquals(300_000L, s.pausedMs)
    }

    @Test
    fun `point aberrant isolé rejeté sans fausser distance ni vitesse max`() {
        val track = SyntheticTrack().walk(60, 1.2).spike(800.0).walk(60, 1.2)
        val r = TrackRecorder(hike(), T0)
        val q = r.feed(track.fixes)
        assertTrue(q.contains(PointQuality.OUTLIER))
        val s = r.stats()
        assertEquals(track.trueDistanceM, s.distanceM, 15.0)
        assertTrue("vitesse max ${s.maxSpeedMps}", s.maxSpeedMps < 2.0)
    }

    @Test
    fun `longue perte de signal - pas de ligne droite ni de distance fictive`() {
        val track = SyntheticTrack().walk(60, 1.2).silentMove(600, 1.2).walk(60, 1.2)
        val r = TrackRecorder(hike(), T0)
        r.feed(track.fixes)
        val s = r.stats()
        assertEquals(2, r.segments().size)
        assertEquals(144.0, s.distanceM, 15.0)
    }

    @Test
    fun `courte perte de signal - tracé continu`() {
        val track = SyntheticTrack().walk(60, 1.2).silentMove(20, 1.2).walk(60, 1.2)
        val r = TrackRecorder(hike(), T0)
        r.feed(track.fixes)
        assertEquals(1, r.segments().size)
        assertEquals(track.trueDistanceM, r.stats().distanceM, 15.0)
    }

    @Test
    fun `sans vitesse Doppler - distance fidèle`() {
        val track = SyntheticTrack().walk(900, 1.2, noiseM = 2.0, dopplerSpeed = false)
        val r = TrackRecorder(hike(autoPauseS = 0), T0)
        r.feed(track.fixes)
        assertEquals(track.trueDistanceM, r.stats().distanceM, track.trueDistanceM * 0.05)
    }

    @Test
    fun `vitesse Doppler toujours nulle - la position suit quand même`() {
        val track = SyntheticTrack().walk(900, 1.2, noiseM = 2.0, dopplerAlwaysZero = true)
        val r = TrackRecorder(hike(autoPauseS = 0), T0)
        r.feed(track.fixes)
        assertEquals(track.trueDistanceM, r.stats().distanceM, track.trueDistanceM * 0.08)
        // La vitesse affichée vient alors du filtre, pas du Doppler nul.
        assertEquals(1.2, r.stats().currentSpeedMps, 0.3)
    }

    @Test
    fun `précision insuffisante ignorée`() {
        val track = SyntheticTrack().walk(30, 1.2, accuracyM = 80f)
        val r = TrackRecorder(hike(), T0)
        assertTrue(r.feed(track.fixes).all { it == PointQuality.LOW_ACCURACY })
        assertEquals(0.0, r.stats().distanceM, 0.0)
    }

    @Test
    fun `les virages réels sont conservés`() {
        // Aller-retour en épingle : 100 m au nord-est puis 100 m au sud-ouest.
        val track = SyntheticTrack().walk(84, 1.2, bearingDeg = 45.0, noiseM = 1.5)
            .walk(84, 1.2, bearingDeg = 225.0, noiseM = 1.5)
        val r = TrackRecorder(hike(autoPauseS = 0), T0)
        r.feed(track.fixes)
        assertEquals(track.trueDistanceM, r.stats().distanceM, track.trueDistanceM * 0.05)
    }

    @Test
    fun `vitesse maximale filtrée sur la fenêtre glissante`() {
        val track = SyntheticTrack().walk(120, 1.2).walk(60, 3.0).walk(120, 1.2)
        val r = TrackRecorder(TrackingParams.forActivity(ActivityType.RUN, 0), T0)
        r.feed(track.fixes)
        assertEquals(3.0, r.stats().maxSpeedMps, 0.25)
    }

    @Test
    fun `interruption - temps à part, nouveau segment après reprise`() {
        val track = SyntheticTrack().walk(60, 1.2)
        val r = TrackRecorder(hike(), T0)
        r.feed(track.fixes)
        r.interrupt(track.time)
        track.silentMove(1200, 1.0) // 20 min sans service
        r.resumeAfterInterruption(track.time)
        track.walk(60, 1.2)
        r.feed(track.fixes.takeLast(60))
        val s = r.stats()
        assertEquals(1_200_000L, s.interruptedMs)
        assertEquals(120_000L, s.movingMs)
        assertEquals(2, r.segments().size)
        assertEquals(144.0, s.distanceM, 15.0)
        assertTimesAddUp(s, 1_320_000L)
    }

    @Test
    fun `le rejeu des données brutes redonne exactement les mêmes statistiques`() {
        val track = SyntheticTrack().walk(300, 1.2, climbMps = 0.1, altitudeNoiseM = 3.0)
            .stand(120).walk(200, 1.3).spike(500.0).walk(100, 1.1)
        val events = mutableListOf(SessionEvent(T0, SessionEventType.START))
        val live = TrackRecorder(hike(), T0)
        track.fixes.forEachIndexed { i, fix ->
            if (i == 650) live.pauseManually(fix.timeMillis).also { events += SessionEvent(fix.timeMillis, SessionEventType.MANUAL_PAUSE) }
            if (i == 680) live.resumeManually(fix.timeMillis).also { events += SessionEvent(fix.timeMillis, SessionEventType.MANUAL_RESUME) }
            live.process(fix)
        }
        live.finish(track.time).also { events += SessionEvent(track.time, SessionEventType.STOP) }

        val replayed = TrackRecorder.replay(hike(), T0, track.fixes, events)
        assertEquals(live.stats(), replayed.stats())
        assertEquals(live.segments(), replayed.segments())
    }

    @Test
    fun `projection des chronomètres sans nouvelle mesure`() {
        val track = SyntheticTrack().walk(10, 1.2)
        val r = TrackRecorder(hike(), T0)
        r.feed(track.fixes)
        val later = r.stats(track.time + 5_000)
        assertEquals(15_000L, later.movingMs)
        r.pauseManually(track.time)
        assertEquals(5_000L, r.stats(track.time + 5_000).pausedMs)
    }

    @Test
    fun `signal perdu signalé après 15 s sans mesure précise`() {
        val track = SyntheticTrack().walk(10, 1.2)
        val r = TrackRecorder(hike(), T0)
        r.feed(track.fixes)
        assertTrue(r.stats(track.time + 5_000).gpsSignalOk)
        assertFalse(r.stats(track.time + 20_000).gpsSignalOk)
    }

    @Test
    fun `vitesse max - 54 kmh avec sauts GPS, pas de valeur fantaisiste (retour terrain)`() {
        // Activité « Autre » (vélo) : 15 m/s, GPS bruité de 4 m, plus des sauts de 40 à 60 m acceptés.
        val track = SyntheticTrack().walk(120, 15.0, noiseM = 4.0, accuracyM = 8f)
        repeat(6) { track.spike(40.0 + it * 4).walk(60, 15.0, noiseM = 4.0, accuracyM = 8f) }
        val r = TrackRecorder(TrackingParams.forActivity(ActivityType.OTHER, 0), T0)
        r.feed(track.fixes)
        val max = r.stats().maxSpeedMps * 3.6
        assertTrue("vitesse max $max km/h", max in 52.0..58.0)
    }

    @Test
    fun `vitesse max sans Doppler - repli sur la distance, fenêtre longue`() {
        val track = SyntheticTrack().walk(120, 15.0, noiseM = 4.0, accuracyM = 8f, dopplerSpeed = false)
        repeat(4) { track.spike(50.0).walk(60, 15.0, noiseM = 4.0, accuracyM = 8f, dopplerSpeed = false) }
        val r = TrackRecorder(TrackingParams.forActivity(ActivityType.OTHER, 0), T0)
        r.feed(track.fixes)
        val max = r.stats().maxSpeedMps * 3.6
        assertTrue("vitesse max $max km/h", max in 50.0..62.0)
    }

    /** Distance (m) d'un point à la vraie trajectoire (segments entre positions successives). */
    private fun deviation(p: GeoPoint, path: List<GeoPoint>): Double = path.zipWithNext { a, b ->
        val k = 111_195.0 * Math.cos(Math.toRadians(a.latitude))
        val bx = (b.longitude - a.longitude) * k
        val by = (b.latitude - a.latitude) * 111_195.0
        val px = (p.longitude - a.longitude) * k
        val py = (p.latitude - a.latitude) * 111_195.0
        val len2 = bx * bx + by * by
        val t = if (len2 == 0.0) 0.0 else ((px * bx + py * by) / len2).coerceIn(0.0, 1.0)
        Math.hypot(px - bx * t, py - by * t)
    }.minOrNull() ?: 0.0

    @Test
    fun `lacets à 50 kmh - le tracé ne coupe pas les virages (retour terrain)`() {
        // Route de col : lignes droites et épingles à 180 degrés (30 degrés/s, rayon ~27 m) à 14 m/s, GPS bruité de 3 m.
        val track = SyntheticTrack(seed = 7)
        var bearing = 0.0
        repeat(4) { lap ->
            track.walk(15, 14.0, bearingDeg = bearing, noiseM = 3.0, accuracyM = 6f)
            val turn = if (lap % 2 == 0) 30.0 else -30.0
            repeat(6) {
                bearing = (bearing + turn + 360.0) % 360.0
                track.walk(1, 14.0, bearingDeg = bearing, noiseM = 3.0, accuracyM = 6f)
            }
        }
        val r = TrackRecorder(TrackingParams.forActivity(ActivityType.OTHER, 0), T0)
        r.feed(track.fixes)
        val worst = r.segments().flatten().maxOf { deviation(it, track.truePath) }
        assertTrue("écart maximal au vrai tracé : ${"%.1f".format(worst)} m", worst < 7.0)
        assertEquals(track.trueDistanceM, r.stats().distanceM, track.trueDistanceM * 0.05)
    }

    @Test
    fun `épingles très serrées (rayon 10 m) à 50 kmh - écart au vrai tracé limité`() {
        val track = SyntheticTrack(seed = 7)
        var bearing = 0.0
        repeat(4) { lap ->
            track.walk(15, 14.0, bearingDeg = bearing, noiseM = 3.0, accuracyM = 6f)
            repeat(2) {
                bearing = (bearing + (if (lap % 2 == 0) 90.0 else -90.0) + 360.0) % 360.0
                track.walk(1, 14.0, bearingDeg = bearing, noiseM = 3.0, accuracyM = 6f)
            }
        }
        val r = TrackRecorder(TrackingParams.forActivity(ActivityType.OTHER, 0), T0)
        r.feed(track.fixes)
        val worst = r.segments().flatten().maxOf { deviation(it, track.truePath) }
        assertTrue("écart maximal : ${"%.1f".format(worst)} m", worst < 5.0)
    }

    @Test
    fun `trajet rapide en mode randonnée - conservé grâce à la vitesse Doppler, pas rejeté`() {
        // Randonneur qui monte en voiture ou à vélo sans changer d'activité.
        val track = SyntheticTrack().walk(60, 1.2).walk(120, 14.0, noiseM = 3.0, accuracyM = 6f)
        val r = TrackRecorder(TrackingParams.forActivity(ActivityType.HIKE, 0), T0)
        val q = r.feed(track.fixes)
        assertTrue("points rejetés : ${q.count { it == PointQuality.OUTLIER }}", q.count { it == PointQuality.OUTLIER } <= 2)
        assertEquals(track.trueDistanceM, r.stats().distanceM, track.trueDistanceM * 0.05)
        assertEquals(1, r.segments().size)
    }

    @Test
    fun `point aberrant à pied toujours rejeté (Doppler cohérent avec la marche)`() {
        val track = SyntheticTrack().walk(60, 1.2).spike(300.0).walk(60, 1.2)
        val r = TrackRecorder(TrackingParams.forActivity(ActivityType.HIKE, 0), T0)
        assertTrue(r.feed(track.fixes).contains(PointQuality.OUTLIER))
        assertEquals(track.trueDistanceM, r.stats().distanceM, 15.0)
    }

    @Test
    fun `vitesse max - retour du signal en voiture, vitesses Doppler fausses ignorées (retour terrain)`() {
        // Trajet maison-travail en « Autre » à 80 km/h : perte de signal de 5 s, la position
        // revient 190 m plus loin et la puce annonce 100 km/h pendant quelques secondes.
        val track = SyntheticTrack().walk(120, 22.0, noiseM = 4.0, accuracyM = 8f)
        track.silentMove(4, 22.0)
        repeat(4) {
            track.walk(1, 22.0, noiseM = 4.0, accuracyM = 8f)
            val f = track.fixes.removeAt(track.fixes.lastIndex)
            track.fixes += f.copy(speedMps = 27.6f + it * 0.3f)
        }
        track.walk(120, 22.0, noiseM = 4.0, accuracyM = 8f)
        val r = TrackRecorder(TrackingParams.forActivity(ActivityType.OTHER, 0), T0)
        r.feed(track.fixes)
        val max = r.stats().maxSpeedMps * 3.6
        assertTrue("vitesse max $max km/h", max in 76.0..84.0)
    }

    @Test
    fun `vitesse max - Doppler surestimé sans déplacement correspondant, plafonné par la distance`() {
        val track = SyntheticTrack().walk(60, 15.0, noiseM = 3.0, accuracyM = 6f)
        repeat(6) {
            track.walk(1, 15.0, noiseM = 3.0, accuracyM = 6f)
            val f = track.fixes.removeAt(track.fixes.lastIndex)
            track.fixes += f.copy(speedMps = 25f)
        }
        track.walk(60, 15.0, noiseM = 3.0, accuracyM = 6f)
        val r = TrackRecorder(TrackingParams.forActivity(ActivityType.OTHER, 0), T0)
        r.feed(track.fixes)
        val max = r.stats().maxSpeedMps * 3.6
        assertTrue("vitesse max $max km/h (90 annoncés, 54 réels)", max < 75.0)
    }

    /** Plus grand écart entre deux points consécutifs d'un même segment (ligne droite dessinée). */
    private fun longestDrawnStep(r: TrackRecorder): Double =
        r.segments().maxOf { seg -> seg.zipWithNext { a, b -> fr.igntrail.core.geo.GeoMath.distanceM(a, b) }.maxOrNull() ?: 0.0 }

    @Test
    fun `perte de signal en forêt à vélo - pas de chemin inventé ni de détour fictif (retour terrain)`() {
        // 30 km/h ; 59 s sans signal sous les arbres ; au retour, 7 s de positions décalées
        // de 120 m (précision annoncée 30 m), nouveau trou de 15 s, puis signal normal.
        val track = SyntheticTrack().walk(120, 8.3, bearingDeg = 20.0, noiseM = 4.0, accuracyM = 8f)
            .silentMove(59, 8.3, bearingDeg = 20.0)
            .walkOffset(7, 8.3, 20.0, offsetM = 120.0, offsetBearingDeg = 300.0, accuracyM = 30f)
            .silentMove(15, 8.3, bearingDeg = 20.0)
            .walk(120, 8.3, bearingDeg = 20.0, noiseM = 4.0, accuracyM = 8f)
        val r = TrackRecorder(TrackingParams.forActivity(ActivityType.OTHER, 0), T0)
        r.feed(track.fixes)
        val worst = r.segments().flatten().maxOf { deviation(it, track.truePath) }
        assertTrue("écart maximal au vrai chemin : ${"%.0f".format(worst)} m", worst < 25.0)
        assertTrue("ligne droite de ${"%.0f".format(longestDrawnStep(r))} m", longestDrawnStep(r) < 40.0)
        assertEquals(track.trueDistanceM, r.stats().distanceM, track.trueDistanceM * 0.05)
    }

    @Test
    fun `positions décalées mais précises sans coupure - rejetées grâce au Doppler`() {
        val track = SyntheticTrack().walk(120, 8.3, bearingDeg = 20.0, noiseM = 4.0, accuracyM = 8f)
            .walkOffset(4, 8.3, 20.0, offsetM = 120.0, offsetBearingDeg = 300.0, accuracyM = 10f)
            .walk(120, 8.3, bearingDeg = 20.0, noiseM = 4.0, accuracyM = 8f)
        val r = TrackRecorder(TrackingParams.forActivity(ActivityType.OTHER, 0), T0)
        r.feed(track.fixes)
        val worst = r.segments().flatten().maxOf { deviation(it, track.truePath) }
        assertTrue("écart maximal au vrai chemin : ${"%.0f".format(worst)} m", worst < 25.0)
    }
}
