package fr.igntrail.core.geo

import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/** Échelle graphique : une longueur « ronde » et sa largeur à l'écran. */
data class ScaleBar(val meters: Double, val widthPx: Double) {
    val label: String get() = formatDistance(meters)

    companion object {
        private val NICE_STEPS = doubleArrayOf(1.0, 2.0, 2.5, 5.0)

        /**
         * Plus grande distance ronde (1, 2, 2,5 ou 5 × 10ⁿ) qui tient dans [maxWidthPx].
         * Retourne null si les paramètres sont invalides.
         */
        fun compute(metersPerPixel: Double, maxWidthPx: Double): ScaleBar? {
            if (metersPerPixel <= 0.0 || maxWidthPx <= 0.0 || !metersPerPixel.isFinite()) return null
            val maxMeters = metersPerPixel * maxWidthPx
            val magnitude = 10.0.pow(floor(log10(maxMeters)))
            val step = NICE_STEPS.last { it * magnitude <= maxMeters }
            val meters = step * magnitude
            return ScaleBar(meters, meters / metersPerPixel)
        }

        /** « 500 m », « 1 km », « 1,25 km » (séparateur décimal français). */
        fun formatDistance(meters: Double): String =
            if (meters >= 1000.0) "${trimmed(meters / 1000.0)} km" else "${trimmed(meters)} m"

        private fun trimmed(value: Double): String =
            java.math.BigDecimal(value).setScale(2, java.math.RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString().replace('.', ',')
    }
}
