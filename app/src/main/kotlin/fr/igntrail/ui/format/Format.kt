package fr.igntrail.ui.format

import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** Mise en forme française des mesures (virgule décimale, espaces insécables). */
object Format {
    private const val NBSP = ' '

    /** « 850 m », « 5,8 km », « 12,6 km ». */
    fun distance(meters: Double): String = when {
        meters < 1000.0 -> "${meters.roundToInt()}${NBSP}m"
        meters < 100_000.0 -> "${decimal(meters / 1000.0, 1)}${NBSP}km"
        else -> "${(meters / 1000.0).roundToInt()}${NBSP}km"
    }

    /** Valeur et unité séparées, pour les grandes tuiles de statistiques. */
    fun distanceParts(meters: Double): Pair<String, String> {
        val text = distance(meters)
        return text.substringBefore(NBSP) to text.substringAfter(NBSP)
    }

    /** « 0:42 », « 1:42:16 » (minutes:secondes sous une heure). */
    fun duration(ms: Long): String {
        val totalSeconds = (ms.coerceAtLeast(0) / 1000)
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    /** « 3,4 » (km/h). */
    fun speedKmh(mps: Double): String = decimal(mps * 3.6, 1)

    /** Allure « 12'30" » par km, ou « – » si non significative. */
    fun pace(secondsPerKm: Double?): String {
        if (secondsPerKm == null || !secondsPerKm.isFinite() || secondsPerKm > 3600) return "–"
        val total = secondsPerKm.roundToLong()
        return "%d'%02d\"".format(total / 60, total % 60)
    }

    /** Allure instantanée à partir d'une vitesse ; « – » à l'arrêt. */
    fun paceFromSpeed(mps: Double): String = if (mps < 0.3) "–" else pace(1000.0 / mps)

    /** « 850 Ko », « 48 Mo », « 1,2 Go ». */
    fun bytes(value: Long): String = when {
        value < 1024L * 1024 -> "${(value / 1024).coerceAtLeast(if (value > 0) 1 else 0)}${NBSP}Ko"
        value < 1024L * 1024 * 1024 -> "${(value / (1024 * 1024))}${NBSP}Mo"
        else -> "${decimal(value / (1024.0 * 1024 * 1024), 1)}${NBSP}Go"
    }

    fun meters(value: Double?): String = value?.let { "${it.roundToInt()}${NBSP}m" } ?: "–"

    private fun decimal(value: Double, digits: Int): String = "%.${digits}f".format(Locale.FRANCE, value)
}
