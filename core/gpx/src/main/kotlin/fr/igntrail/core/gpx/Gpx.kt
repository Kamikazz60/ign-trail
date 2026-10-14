package fr.igntrail.core.gpx

import fr.igntrail.core.model.Route
import fr.igntrail.core.model.RoutePoint
import fr.igntrail.core.model.RouteSource
import fr.igntrail.core.model.Waypoint
import org.xml.sax.Attributes
import org.xml.sax.SAXException
import org.xml.sax.helpers.DefaultHandler
import java.io.InputStream
import java.time.Instant
import java.time.OffsetDateTime
import javax.xml.parsers.SAXParserFactory

/** Résultat d'une importation GPX (§14 : validation et messages d'erreur explicites). */
sealed interface GpxResult {
    data class Success(val route: Route, val warnings: List<String>) : GpxResult

    data class Failure(val message: String) : GpxResult
}

/**
 * Lecture de fichiers GPX 1.0 et 1.1 : traces (`trk`), routes (`rte`) et points (`wpt`).
 * Lecture en flux (SAX) : supporte les gros fichiers. Entités externes désactivées.
 */
object GpxParser {
    /** Au-delà, l'itinéraire est allégé (un point sur n) pour rester fluide sur la carte. */
    const val MAX_POINTS = 50_000

    fun parse(input: InputStream, fallbackName: String): GpxResult {
        val handler = Handler()
        try {
            newParser().parse(input, handler)
        } catch (e: SAXException) {
            return GpxResult.Failure("Fichier illisible : ce n'est pas un fichier GPX valide (${e.message?.take(80)}).")
        } catch (e: Exception) {
            return GpxResult.Failure("Lecture du fichier impossible : ${e.message?.take(80)}")
        }
        if (!handler.sawGpxRoot) return GpxResult.Failure("Ce fichier n'est pas au format GPX.")

        val warnings = mutableListOf<String>()
        // Trace prioritaire ; à défaut, route ; à défaut, la suite des points nommés.
        var points: List<RoutePoint> = handler.trackPoints.takeIf { it.isNotEmpty() } ?: handler.routePoints
        if (points.isEmpty() && handler.waypoints.size >= 2) {
            points = handler.waypoints.map { RoutePoint(it.latitude, it.longitude, it.elevationM) }
            warnings += "Pas de tracé : les points du fichier sont reliés dans l'ordre."
        }
        if (points.size < 2) return GpxResult.Failure("Le fichier ne contient aucun tracé exploitable.")
        if (handler.invalidPoints > 0) warnings += "${handler.invalidPoints} point(s) aux coordonnées invalides ignoré(s)."
        if (handler.trackCount > 1) warnings += "${handler.trackCount} traces dans le fichier : elles sont mises bout à bout."
        if (points.size > MAX_POINTS) {
            val step = (points.size + MAX_POINTS - 1) / MAX_POINTS
            points = points.filterIndexed { i, _ -> i % step == 0 || i == points.lastIndex }
            warnings += "Tracé très détaillé : allégé à ${points.size} points."
        }
        val name = handler.name?.trim()?.takeIf { it.isNotEmpty() } ?: fallbackName
        return GpxResult.Success(Route(name, points, handler.waypoints, RouteSource.IMPORTED), warnings)
    }

    private fun newParser() = SAXParserFactory.newInstance().apply {
        isNamespaceAware = true
        // Protection XXE : aucune entité ni DTD externe.
        listOf(
            "http://xml.org/sax/features/external-general-entities" to false,
            "http://xml.org/sax/features/external-parameter-entities" to false,
            "http://apache.org/xml/features/nonvalidating/load-external-dtd" to false,
        ).forEach { (feature, value) -> runCatching { setFeature(feature, value) } }
    }.newSAXParser()

    private class Handler : DefaultHandler() {
        var sawGpxRoot = false
        var name: String? = null
        var trackCount = 0
        var invalidPoints = 0
        val trackPoints = mutableListOf<RoutePoint>()
        val routePoints = mutableListOf<RoutePoint>()
        val waypoints = mutableListOf<Waypoint>()

        private val path = ArrayDeque<String>()
        private val text = StringBuilder()
        private var lat: Double? = null
        private var lon: Double? = null
        private var ele: Double? = null
        private var time: Long? = null
        private var pointName: String? = null

        override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes) {
            val tag = tag(localName, qName)
            if (path.isEmpty() && tag == "gpx") sawGpxRoot = true
            path.addLast(tag)
            text.setLength(0)
            when (tag) {
                "trk" -> trackCount++
                "trkpt", "rtept", "wpt" -> {
                    lat = attributes.getValue("lat")?.trim()?.toDoubleOrNull()
                    lon = attributes.getValue("lon")?.trim()?.toDoubleOrNull()
                    ele = null
                    time = null
                    pointName = null
                }
            }
        }

        override fun characters(ch: CharArray, start: Int, length: Int) {
            text.appendRange(ch, start, start + length)
        }

        override fun endElement(uri: String?, localName: String?, qName: String?) {
            val tag = tag(localName, qName)
            val parent = path.getOrNull(path.size - 2)
            val value = text.toString().trim()
            when (tag) {
                "ele" -> ele = value.toDoubleOrNull()
                "time" -> time = parseTime(value)
                "name" -> when (parent) {
                    "wpt" -> pointName = value
                    "metadata", "trk", "rte" -> if (name == null && value.isNotEmpty()) name = value
                    "gpx" -> if (value.isNotEmpty()) name = value // GPX 1.0 : nom à la racine
                }
                "trkpt", "rtept", "wpt" -> finishPoint(tag)
            }
            path.removeLast()
            text.setLength(0)
        }

        private fun finishPoint(tag: String) {
            val la = lat
            val lo = lon
            if (la == null || lo == null || la !in -90.0..90.0 || lo !in -180.0..180.0 || la.isNaN() || lo.isNaN()) {
                invalidPoints++
                return
            }
            when (tag) {
                "trkpt" -> trackPoints += RoutePoint(la, lo, ele, time)
                "rtept" -> routePoints += RoutePoint(la, lo, ele, time)
                "wpt" -> waypoints += Waypoint(la, lo, pointName?.takeIf { it.isNotEmpty() } ?: "Point ${waypoints.size + 1}", ele)
            }
        }

        private fun tag(localName: String?, qName: String?): String =
            (localName?.takeIf { it.isNotEmpty() } ?: qName.orEmpty().substringAfter(':')).lowercase()

        private fun parseTime(value: String): Long? = runCatching { Instant.parse(value).toEpochMilli() }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(value).toInstant().toEpochMilli() }.getOrNull()
    }
}

/** Écriture GPX 1.1 (export §14 ; utilisé dès maintenant pour les tests aller-retour). */
object GpxWriter {
    fun write(route: Route, creator: String = "IGN Trail"): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
        append("""<gpx version="1.1" creator="${escape(creator)}" xmlns="http://www.topografix.com/GPX/1/1">""").append('\n')
        append("  <metadata><name>").append(escape(route.name)).append("</name></metadata>\n")
        route.waypoints.forEach { w ->
            append("""  <wpt lat="${coord(w.latitude)}" lon="${coord(w.longitude)}">""")
            w.elevationM?.let { append("<ele>").append(ele(it)).append("</ele>") }
            append("<name>").append(escape(w.name)).append("</name></wpt>\n")
        }
        append("  <trk><name>").append(escape(route.name)).append("</name><trkseg>\n")
        route.points.forEach { p ->
            append("""    <trkpt lat="${coord(p.latitude)}" lon="${coord(p.longitude)}">""")
            p.elevationM?.let { append("<ele>").append(ele(it)).append("</ele>") }
            p.timeMillis?.let { append("<time>").append(Instant.ofEpochMilli(it)).append("</time>") }
            append("</trkpt>\n")
        }
        append("  </trkseg></trk>\n</gpx>\n")
    }

    /** Point d'une trace enregistrée. */
    data class TrackPoint(val latitude: Double, val longitude: Double, val elevationM: Double?, val timeMillis: Long?)

    /**
     * Trace enregistrée (§14) : un segment GPX par segment du tracé (les coupures
     * ne sont pas reliées), avec heure et altitude, et les points d'intérêt en `wpt`.
     */
    fun writeTrack(
        name: String,
        segments: List<List<TrackPoint>>,
        waypoints: List<Waypoint>,
        description: String? = null,
        creator: String = "IGN Trail",
    ): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
        append("""<gpx version="1.1" creator="${escape(creator)}" xmlns="http://www.topografix.com/GPX/1/1">""").append('\n')
        append("  <metadata><name>").append(escape(name)).append("</name>")
        segments.firstOrNull()?.firstOrNull()?.timeMillis?.let { append("<time>").append(Instant.ofEpochMilli(it)).append("</time>") }
        append("</metadata>\n")
        waypoints.forEach { w ->
            append("""  <wpt lat="${coord(w.latitude)}" lon="${coord(w.longitude)}">""")
            w.elevationM?.let { append("<ele>").append(ele(it)).append("</ele>") }
            append("<name>").append(escape(w.name)).append("</name></wpt>\n")
        }
        append("  <trk><name>").append(escape(name)).append("</name>")
        description?.let { append("<desc>").append(escape(it)).append("</desc>") }
        append("\n")
        segments.filter { it.isNotEmpty() }.forEach { segment ->
            append("    <trkseg>\n")
            segment.forEach { p ->
                append("""      <trkpt lat="${coord(p.latitude)}" lon="${coord(p.longitude)}">""")
                p.elevationM?.let { append("<ele>").append(ele(it)).append("</ele>") }
                p.timeMillis?.let { append("<time>").append(Instant.ofEpochMilli(it)).append("</time>") }
                append("</trkpt>\n")
            }
            append("    </trkseg>\n")
        }
        append("  </trk>\n</gpx>\n")
    }

    /**
     * Nombres en notation décimale simple, sans exposant (le format GPX l'exige : « 1.0E-5 »
     * est refusé). 7 décimales = précision du centimètre, bien au-delà du GPS.
     */
    private fun coord(v: Double): String = "%.7f".format(java.util.Locale.ROOT, v).trimEnd('0').trimEnd('.').let { if (it == "-0") "0" else it }

    private fun ele(v: Double): String = "%.1f".format(java.util.Locale.ROOT, v)

    private fun escape(s: String) = s
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
