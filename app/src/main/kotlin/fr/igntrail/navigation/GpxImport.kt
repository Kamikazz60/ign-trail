package fr.igntrail.navigation

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import fr.igntrail.core.gpx.GpxParser
import fr.igntrail.core.gpx.GpxResult
import fr.igntrail.data.storage.RouteRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream

/** Résultat d'une importation, prêt à afficher. */
sealed interface ImportOutcome {
    data class Imported(val routeId: Long, val name: String, val warnings: List<String>) : ImportOutcome

    data class Error(val message: String) : ImportOutcome
}

/** Importation d'un fichier GPX choisi, ouvert ou partagé depuis une autre application (§9, §14). */
class GpxImport(private val context: Context, private val routes: RouteRepository) {
    suspend fun import(uri: Uri): ImportOutcome = withContext(Dispatchers.IO) {
        val name = displayName(uri)?.removeSuffix(".gpx")?.removeSuffix(".GPX") ?: "Itinéraire importé"
        val bytes = try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                // Lecture bornée : on refuse les fichiers démesurés plutôt que de saturer la mémoire.
                readAtMost(input, MAX_BYTES) ?: return@withContext ImportOutcome.Error("Fichier trop volumineux (plus de 30 Mo).")
            } ?: return@withContext ImportOutcome.Error("Impossible d'ouvrir le fichier.")
        } catch (e: SecurityException) {
            return@withContext ImportOutcome.Error("Accès au fichier refusé.")
        } catch (e: Exception) {
            return@withContext ImportOutcome.Error("Lecture du fichier impossible : ${e.message}")
        }
        when (val result = GpxParser.parse(ByteArrayInputStream(bytes), name)) {
            is GpxResult.Failure -> ImportOutcome.Error(result.message)
            is GpxResult.Success -> {
                val id = routes.save(result.route)
                ImportOutcome.Imported(id, result.route.name, result.warnings)
            }
        }
    }

    private fun displayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/')

    /** Lit au plus [limit] octets ; null si le fichier est plus gros (compatible Android 10). */
    private fun readAtMost(input: java.io.InputStream, limit: Int): ByteArray? {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) return out.toByteArray()
            if (out.size() + n > limit) return null
            out.write(buffer, 0, n)
        }
    }

    companion object {
        private const val MAX_BYTES = 30 * 1024 * 1024
    }
}
