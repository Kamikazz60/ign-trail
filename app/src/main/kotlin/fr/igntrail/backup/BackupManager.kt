package fr.igntrail.backup

import android.content.Context
import android.net.Uri
import fr.igntrail.data.storage.TrackRepository
import fr.igntrail.data.storage.TrailDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Archive de sauvegarde complète (§14, §16) : base de données (randonnées, mesures brutes,
 * itinéraires, points d'intérêt), photos et réglages. Fichier ZIP enregistré où
 * l'utilisateur le décide, uniquement à sa demande (§18).
 *
 * Les cartes hors ligne ne sont pas incluses (volumineuses, re-téléchargeables).
 *
 * La restauration est préparée dans un dossier d'attente, puis appliquée au prochain
 * démarrage, avant l'ouverture de la base ([applyPendingRestore]).
 */
class BackupManager(private val context: Context, private val tracks: TrackRepository) {

    suspend fun export(target: Uri) = withContext(Dispatchers.IO) {
        // Toutes les écritures en attente dans le journal WAL sont reportées dans le fichier principal.
        tracks.checkpoint()
        val dbFile = context.getDatabasePath(TrailDatabase.FILE_NAME)
        context.contentResolver.openOutputStream(target)?.use { out ->
            ZipOutputStream(out).use { zip ->
                zip.putNextEntry(ZipEntry(MANIFEST))
                zip.write(
                    JSONObject()
                        .put("format", FORMAT)
                        .put("version", 1)
                        .put("databaseVersion", DATABASE_VERSION)
                        .put("created", System.currentTimeMillis())
                        .toString(2).toByteArray(),
                )
                zip.closeEntry()
                addFile(zip, dbFile, "database/${TrailDatabase.FILE_NAME}")
                photosDir().listFiles()?.forEach { addFile(zip, it, "photos/${it.name}") }
                datastoreDir().listFiles()?.filter { it.name.endsWith(".preferences_pb") }?.forEach { addFile(zip, it, "settings/${it.name}") }
            }
        } ?: error("Impossible d'écrire le fichier de sauvegarde")
    }

    /**
     * Vérifie et prépare la restauration. Retourne null si tout va bien, sinon un message.
     * L'application doit ensuite redémarrer pour l'appliquer.
     */
    suspend fun prepareRestore(source: Uri): String? = withContext(Dispatchers.IO) {
        val pending = pendingDir()
        pending.deleteRecursively()
        pending.mkdirs()
        var manifest: JSONObject? = null
        try {
            context.contentResolver.openInputStream(source)?.use { input ->
                ZipInputStream(input).use { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        val name = entry.name
                        // Protection contre les chemins sortant du dossier (« zip slip »).
                        if (entry.isDirectory || name.contains("..") || name.startsWith("/")) continue
                        if (name == MANIFEST) {
                            manifest = JSONObject(zip.readBytes().decodeToString())
                            continue
                        }
                        if (!(name.startsWith("database/") || name.startsWith("photos/") || name.startsWith("settings/"))) continue
                        val out = File(pending, name)
                        if (!out.canonicalPath.startsWith(pending.canonicalPath + File.separator)) continue
                        out.parentFile?.mkdirs()
                        out.outputStream().use { zip.copyTo(it) }
                    }
                }
            } ?: return@withContext "Fichier illisible."
        } catch (e: Exception) {
            pending.deleteRecursively()
            return@withContext "Ce fichier n'est pas une sauvegarde IGN Trail valide."
        }
        val m = manifest
        val error = when {
            m == null || m.optString("format") != FORMAT -> "Ce fichier n'est pas une sauvegarde IGN Trail."
            m.optInt("databaseVersion") > DATABASE_VERSION -> "Sauvegarde créée par une version plus récente de l'application."
            !File(pending, "database/${TrailDatabase.FILE_NAME}").exists() -> "Sauvegarde incomplète (base de données absente)."
            else -> null
        }
        if (error != null) {
            pending.deleteRecursively()
            return@withContext error
        }
        File(pending, READY).writeText("ok")
        null
    }

    private fun addFile(zip: ZipOutputStream, file: File, name: String) {
        if (!file.exists()) return
        zip.putNextEntry(ZipEntry(name))
        file.inputStream().use { it.copyTo(zip) }
        zip.closeEntry()
    }

    private fun photosDir() = File(context.filesDir, "photos")
    private fun datastoreDir() = File(context.filesDir, "datastore")
    private fun pendingDir() = File(context.filesDir, PENDING)

    companion object {
        private const val FORMAT = "igntrail-backup"
        private const val MANIFEST = "manifest.json"
        private const val PENDING = "restore_pending"
        private const val READY = "READY"
        private const val DATABASE_VERSION = 4

        /**
         * À appeler au démarrage, avant toute ouverture de la base : remplace la base, les
         * photos et les réglages par ceux de la sauvegarde préparée. Retourne vrai si appliqué.
         */
        fun applyPendingRestore(context: Context): Boolean {
            val pending = File(context.filesDir, PENDING)
            if (!File(pending, READY).exists()) {
                pending.deleteRecursively()
                return false
            }
            val dbFile = context.getDatabasePath(TrailDatabase.FILE_NAME)
            listOf("", "-wal", "-shm").forEach { File(dbFile.path + it).delete() }
            dbFile.parentFile?.mkdirs()
            File(pending, "database/${TrailDatabase.FILE_NAME}").copyTo(dbFile, overwrite = true)

            val photos = File(context.filesDir, "photos")
            photos.deleteRecursively()
            photos.mkdirs()
            File(pending, "photos").listFiles()?.forEach { it.copyTo(File(photos, it.name), overwrite = true) }

            val settings = File(pending, "settings").listFiles().orEmpty()
            if (settings.isNotEmpty()) {
                val store = File(context.filesDir, "datastore").apply { mkdirs() }
                settings.forEach { it.copyTo(File(store, it.name), overwrite = true) }
            }
            pending.deleteRecursively()
            return true
        }
    }
}
