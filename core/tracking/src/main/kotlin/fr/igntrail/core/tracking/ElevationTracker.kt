package fr.igntrail.core.tracking

import kotlin.math.pow

/**
 * Altitude, minimum/maximum et dénivelés (§11).
 *
 * Les variations ne sont pas additionnées point par point (ce qui surestimerait
 * fortement le dénivelé) : l'altitude est lissée, puis une variation n'est comptée
 * qu'une fois qu'elle dépasse un seuil d'hystérésis depuis la dernière référence.
 *
 * Si le téléphone a un baromètre, les dénivelés sont calculés sur l'altitude
 * barométrique (bruit de l'ordre de 0,5 m, contre 5 à 10 m pour le GPS) avec un
 * seuil plus faible. L'altitude affichée reste calée sur le GPS (le baromètre ne
 * donne qu'une altitude relative, la pression variant avec la météo).
 */
class ElevationTracker(
    private val thresholdM: Double,
    private val maxVerticalAccuracyM: Float,
    /** Moyenne glissante sur ~15 mesures (15 s à 1 Hz) : divise le bruit par ~4. */
    private val windowSize: Int = 15,
    private val baroThresholdM: Double = 3.0,
) {
    private val window = ArrayDeque<Double>()
    private var reference: Double? = null

    private val baroWindow = ArrayDeque<Double>()
    private var baroReference: Double? = null

    /** Écart moyen altitude GPS − altitude barométrique brute (calage). */
    private var baroOffset: Double? = null
    private var offsetSamples = 0

    /** Vrai dès qu'une mesure de pression a été reçue : le baromètre fait foi pour les dénivelés. */
    var barometric = false
        private set

    var current: Double? = null
        private set
    var min: Double? = null
        private set
    var max: Double? = null
        private set
    var gain = 0.0
        private set
    var loss = 0.0
        private set

    /** Nombre de mesures d'altitude exploitées : 0 = pas de données altimétriques. */
    var samples = 0
        private set

    /**
     * @param counting faux pendant les pauses : l'altitude reste affichée
     * mais les variations (bruit à l'arrêt) ne sont pas comptées.
     * @param pressureHpa pression atmosphérique si le téléphone a un baromètre.
     */
    fun add(altitudeM: Double?, verticalAccuracyM: Float?, counting: Boolean, pressureHpa: Float? = null) {
        val gpsOk = altitudeM != null && altitudeM.isFinite() &&
            (verticalAccuracyM == null || verticalAccuracyM <= maxVerticalAccuracyM)
        if (pressureHpa != null && pressureHpa in 300f..1_100f) {
            addBarometric(pressureToAltitude(pressureHpa.toDouble()), if (gpsOk) altitudeM else null, counting)
            return
        }
        if (!gpsOk) return
        samples++
        window.addLast(altitudeM!!)
        if (window.size > windowSize) window.removeFirst()
        val smoothed = window.average()
        updateDisplayed(smoothed)
        // En mode baromètre, une mesure sans pression ne compte pas de dénivelé.
        if (barometric) return
        reference = hysteresis(reference, smoothed, thresholdM, counting) ?: reference
    }

    private fun addBarometric(raw: Double, gpsAltitude: Double?, counting: Boolean) {
        barometric = true
        samples++
        baroWindow.addLast(raw)
        if (baroWindow.size > BARO_WINDOW) baroWindow.removeFirst()
        val smoothedRaw = baroWindow.average()

        // Calage progressif sur le GPS : moyenne exacte au début, puis moyenne mobile lente.
        if (gpsAltitude != null) {
            val sample = gpsAltitude - raw
            offsetSamples++
            val alpha = if (offsetSamples <= 30) 1.0 / offsetSamples else 0.02
            baroOffset = baroOffset?.let { it + alpha * (sample - it) } ?: sample
        }
        val offset = baroOffset
        if (offset != null) updateDisplayed(smoothedRaw + offset)

        // Dénivelés sur l'altitude barométrique brute : indépendants du calage.
        baroReference = hysteresis(baroReference, smoothedRaw, baroThresholdM, counting) ?: baroReference
    }

    /** Retourne la nouvelle référence si une variation a été comptée (ou s'il n'y en avait pas). */
    private fun hysteresis(ref: Double?, value: Double, threshold: Double, counting: Boolean): Double? {
        if (ref == null) return value
        if (!counting) return null
        val delta = value - ref
        return when {
            delta >= threshold -> value.also { gain += delta }
            -delta >= threshold -> value.also { loss += -delta }
            else -> null
        }
    }

    private fun updateDisplayed(altitude: Double) {
        current = altitude
        min = min?.coerceAtMost(altitude) ?: altitude
        max = max?.coerceAtLeast(altitude) ?: altitude
    }

    companion object {
        /**
         * Moyenne glissante sur 20 s de l'altitude barométrique. Téléphone en poche, la
         * pression « respire » (bruit de 3 à 4 m) : sur 5 s, une marche à plat donnait
         * 522 m de D+ ; sur 20 s, 15 m, en gardant 80 % d'une vraie bosse de 10 m.
         */
        private const val BARO_WINDOW = 20

        /** Altitude de l'atmosphère standard (formule internationale du nivellement barométrique). */
        fun pressureToAltitude(hpa: Double): Double = 44_330.0 * (1.0 - (hpa / 1_013.25).pow(0.190_295))
    }
}
