package fr.igntrail.core.gpx

import fr.igntrail.core.model.Route
import fr.igntrail.core.model.RoutePoint
import fr.igntrail.core.model.Waypoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GpxTest {
    private fun parse(xml: String) = GpxParser.parse(xml.byteInputStream(), "Sans nom")
    private fun ok(xml: String) = parse(xml) as GpxResult.Success

    @Test
    fun `trace GPX 1_1 avec altitude, heure et points nommés`() {
        val r = ok(
            """<?xml version="1.0"?>
            <gpx version="1.1" xmlns="http://www.topografix.com/GPX/1/1">
              <metadata><name>Tour du lac</name></metadata>
              <wpt lat="45.1" lon="6.1"><name>Refuge</name></wpt>
              <trk><name>Autre nom</name><trkseg>
                <trkpt lat="45.0" lon="6.0"><ele>1000.5</ele><time>2026-07-01T08:00:00Z</time></trkpt>
                <trkpt lat="45.001" lon="6.001"><ele>1010</ele><time>2026-07-01T08:01:00Z</time></trkpt>
              </trkseg></trk>
            </gpx>""",
        )
        assertEquals("Tour du lac", r.route.name)
        assertEquals(2, r.route.points.size)
        assertEquals(1000.5, r.route.points[0].elevationM!!, 0.0)
        assertEquals(1_782_892_800_000L, r.route.points[0].timeMillis)
        assertEquals(listOf("Refuge"), r.route.waypoints.map { it.name })
        assertTrue(r.warnings.isEmpty())
    }

    @Test
    fun `GPX 1_0 avec route, sans espace de noms et nom à la racine`() {
        val r = ok(
            """<gpx version="1.0"><name>Boucle</name>
              <rte><rtept lat="44.0" lon="5.0"/><rtept lat="44.01" lon="5.01"/><rtept lat="44.02" lon="5.0"/></rte>
            </gpx>""",
        )
        assertEquals("Boucle", r.route.name)
        assertEquals(3, r.route.points.size)
    }

    @Test
    fun `plusieurs traces mises bout à bout avec avertissement`() {
        val r = ok(
            """<gpx><trk><trkseg><trkpt lat="1" lon="1"/><trkpt lat="1.1" lon="1"/></trkseg></trk>
                    <trk><trkseg><trkpt lat="1.2" lon="1"/></trkseg></trk></gpx>""",
        )
        assertEquals(3, r.route.points.size)
        assertTrue(r.warnings.any { it.contains("2 traces") })
        assertEquals("Sans nom", r.route.name)
    }

    @Test
    fun `points invalides ignorés et signalés`() {
        val r = ok("""<gpx><trk><trkseg><trkpt lat="91" lon="1"/><trkpt lat="1" lon="x"/><trkpt lat="1" lon="1"/><trkpt lat="1.1" lon="1"/></trkseg></trk></gpx>""")
        assertEquals(2, r.route.points.size)
        assertTrue(r.warnings.any { it.contains("2 point(s)") })
    }

    @Test
    fun `seulement des points nommés - reliés dans l'ordre`() {
        val r = ok("""<gpx><wpt lat="1" lon="1"><name>A</name></wpt><wpt lat="1.1" lon="1"><name>B</name></wpt></gpx>""")
        assertEquals(2, r.route.points.size)
        assertTrue(r.warnings.isNotEmpty())
    }

    @Test
    fun `messages d'erreur explicites`() {
        assertTrue((parse("ceci n'est pas du XML") as GpxResult.Failure).message.contains("illisible"))
        assertTrue((parse("<kml><Document/></kml>") as GpxResult.Failure).message.contains("pas au format GPX"))
        assertTrue((parse("<gpx><trk><trkseg><trkpt lat=\"1\" lon=\"1\"/></trkseg></trk></gpx>") as GpxResult.Failure).message.contains("aucun tracé"))
    }

    @Test
    fun `entité externe refusée - pas de lecture de fichier local`() {
        val xxe = """<?xml version="1.0"?>
            <!DOCTYPE gpx [<!ENTITY secret SYSTEM "file:///etc/hosts">]>
            <gpx><metadata><name>&secret;</name></metadata>
            <trk><trkseg><trkpt lat="1" lon="1"/><trkpt lat="1.1" lon="1"/></trkseg></trk></gpx>"""
        when (val r = parse(xxe)) {
            is GpxResult.Failure -> Unit // refus pur et simple : acceptable
            is GpxResult.Success -> assertTrue(!r.route.name.contains("localhost"))
        }
    }

    @Test
    fun `aller-retour export puis import - coordonnées, altitudes et heures conservées`() {
        val route = Route(
            name = "Sortie <test> & co",
            points = (0 until 100).map { RoutePoint(45.0 + it * 1e-4, 6.0 + it * 1.3e-4, 1000.0 + it, 1_782_892_800_000L + it * 1000L) },
            waypoints = listOf(Waypoint(45.0, 6.0, "Départ", 1000.0)),
        )
        val back = ok(GpxWriter.write(route)).route
        assertEquals(route.name, back.name)
        // Coordonnées au centimètre près (7 décimales), altitudes au décimètre, heures exactes.
        route.points.zip(back.points).forEach { (a, b) ->
            assertEquals(a.latitude, b.latitude, 1e-7)
            assertEquals(a.longitude, b.longitude, 1e-7)
            assertEquals(a.elevationM!!, b.elevationM!!, 0.05)
            assertEquals(a.timeMillis, b.timeMillis)
        }
        assertEquals(route.points.size, back.points.size)
        assertEquals(route.waypoints, back.waypoints)
    }

    @Test
    fun `test G - randonnée exportée puis réimportée, coordonnées, altitudes et heures conservées`() {
        val t0 = 1_782_892_800_000L
        val seg1 = (0 until 50).map { GpxWriter.TrackPoint(45.0 + it * 1e-4, 6.0, 1000.0 + it, t0 + it * 1000L) }
        val seg2 = (0 until 50).map { GpxWriter.TrackPoint(45.1 + it * 1e-4, 6.1, 1200.0, t0 + 600_000L + it * 1000L) }
        val xml = GpxWriter.writeTrack("Sortie", listOf(seg1, seg2), listOf(Waypoint(45.05, 6.05, "Source")))
        val back = ok(xml).route
        assertEquals(100, back.points.size)
        (seg1 + seg2).zip(back.points).forEach { (a, b) ->
            assertEquals(a.latitude, b.latitude, 1e-7)
            assertEquals(a.longitude, b.longitude, 1e-7)
            assertEquals(a.elevationM!!, b.elevationM!!, 0.05)
            assertEquals(a.timeMillis, b.timeMillis)
        }
        assertEquals("Source", back.waypoints.single().name)
        // Deux segments distincts dans le fichier : la coupure n'est pas reliée.
        assertEquals(2, Regex("<trkseg>").findAll(xml).count())
    }

    /** Validation contre le schéma officiel GPX 1.1 (topografix.com), copié dans les ressources de test. */
    private fun assertValidGpx(xml: String) {
        val schema = javax.xml.validation.SchemaFactory.newInstance(javax.xml.XMLConstants.W3C_XML_SCHEMA_NS_URI)
            .newSchema(javaClass.classLoader!!.getResource("gpx11.xsd"))
        schema.newValidator().validate(javax.xml.transform.stream.StreamSource(xml.byteInputStream()))
    }

    @Test
    fun `exports conformes au schéma officiel GPX 1_1, y compris près du méridien de Greenwich`() {
        // Longitudes proches de 0 (ouest de la France) : naguère écrites « 1.0E-5 », refusées par les sites.
        val pts = (0 until 20).map { GpxWriter.TrackPoint(47.2 + it * 1e-4, -0.00002 + it * 1e-5, 12.0, 1_782_892_800_000L + it * 1000L) }
        val track = GpxWriter.writeTrack("Sortie <Nantes> & co", listOf(pts, pts), listOf(Waypoint(47.2, 0.00001, "Pont", 10.0)), "Randonnée")
        assertValidGpx(track)
        assertTrue("notation scientifique interdite", !Regex("[0-9]E-?[0-9]").containsMatchIn(track))
        val route = Route("Boucle", pts.map { RoutePoint(it.latitude, it.longitude, it.elevationM) }, listOf(Waypoint(47.2, -1e-6, "Étape 1")))
        assertValidGpx(GpxWriter.write(route))
        // Les coordonnées relues sont identiques au centimètre près.
        val back = ok(track).route.points
        pts.zip(back).forEach { (a, b) -> assertEquals(a.longitude, b.longitude, 1e-7) }
    }
}
