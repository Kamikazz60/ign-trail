package fr.igntrail.diagnostic

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Journal local du dernier plantage (Phase 7). Rien n'est envoyé : le fichier reste dans le
 * stockage privé de l'application, et l'utilisateur peut le partager depuis les Paramètres.
 * Il ne contient ni position ni trace, seulement l'erreur et la version.
 */
object CrashLog {
    private fun file(context: Context) = File(context.filesDir, "diagnostic/dernier-plantage.txt")

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { write(app, thread, error) }
            previous?.uncaughtException(thread, error)
        }
    }

    private fun write(context: Context, thread: Thread, error: Throwable) {
        val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
        val f = file(context).apply { parentFile?.mkdirs() }
        f.writeText(
            buildString {
                val info = runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
                appendLine("IGN Trail ${info?.versionName} (${info?.longVersionCode})")
                appendLine("Date : ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.FRANCE).format(Date())}")
                appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL}")
                appendLine("Fil : ${thread.name}")
                appendLine()
                append(trace)
            },
        )
    }

    /** Date du dernier plantage enregistré, ou null. */
    fun lastCrashMillis(context: Context): Long? = file(context).takeIf { it.exists() }?.lastModified()

    fun clear(context: Context) {
        file(context).delete()
    }

    /** Partage le journal (copie dans le cache des exports, seul dossier partageable). */
    fun share(context: Context) {
        val source = file(context).takeIf { it.exists() } ?: return
        val copy = File(context.cacheDir, "exports/igntrail-plantage.txt").apply { parentFile?.mkdirs() }
        source.copyTo(copy, overwrite = true)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", copy)
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, "IGN Trail — rapport de plantage")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, "Partager le rapport").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
