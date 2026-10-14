package fr.igntrail.export

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import fr.igntrail.AppContainer
import fr.igntrail.core.gpx.GpxWriter
import fr.igntrail.core.model.Waypoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Export GPX et partage par le menu Android (§14). Le fichier n'est créé qu'à la demande
 * de l'utilisateur, dans le cache privé, puis transmis à l'application qu'il choisit.
 */
class GpxExport(private val context: Context, private val container: AppContainer) {

    /** Randonnée enregistrée : trace filtrée (un segment par portion continue) et ses points d'intérêt. */
    suspend fun shareSession(sessionId: Long): Boolean = withContext(Dispatchers.Default) {
        val summary = container.trackRepository.load(sessionId)?.summary ?: return@withContext false
        val recorder = container.recordingManager.replay(sessionId) ?: return@withContext false
        val segments = recorder.timedSegments().map { seg ->
            seg.map { GpxWriter.TrackPoint(it.point.latitude, it.point.longitude, it.altitudeM, it.timeMillis) }
        }
        val pois = container.poiRepository.forSession(sessionId).first().map {
            Waypoint(it.position.latitude, it.position.longitude, it.name, it.altitudeM)
        }
        val xml = GpxWriter.writeTrack(summary.name, segments, pois, summary.activity.label)
        share(summary.name, xml)
        true
    }

    /** Itinéraire importé ou créé. */
    suspend fun shareRoute(routeId: Long): Boolean = withContext(Dispatchers.Default) {
        val route = container.routeRepository.load(routeId) ?: return@withContext false
        share(route.name, GpxWriter.write(route))
        true
    }

    private fun share(name: String, xml: String) {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        // Un seul export conservé à la fois : pas d'accumulation de traces dans le cache.
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, safeFileName(name) + ".gpx")
        file.writeText(xml)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("application/gpx+xml")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, name)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, "Partager « $name »").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    companion object {
        fun safeFileName(name: String): String =
            name.replace(Regex("[^\\p{L}\\p{N} _.-]"), "_").trim().take(60).ifEmpty { "parcours" }
    }
}
