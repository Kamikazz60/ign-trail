package fr.igntrail.core.planning

import fr.igntrail.core.geo.GeoMath
import fr.igntrail.core.model.ActivityType
import fr.igntrail.core.model.GeoPoint
import kotlin.math.max
import kotlin.math.min

/** Points régulièrement espacés le long d'un tracé, avec leur distance depuis le départ. */
data class Sampled(val points: List<GeoPoint>, val distancesM: List<Double>)

object Sampling {
    /**
     * Rééchantillonne [line] tous les [stepM] mètres (extrémités et sommets conservés,
     * pour ne pas couper les virages). Sert à interroger l'altimétrie.
     */
    fun resample(line: List<GeoPoint>, stepM: Double): Sampled {
        if (line.isEmpty()) return Sampled(emptyList(), emptyList())
        val pts = mutableListOf(line.first())
        val dist = mutableListOf(0.0)
        var total = 0.0
        var sinceLast = 0.0
        for (i in 1 until line.size) {
            val a = line[i - 1]
            val b = line[i]
            val segment = GeoMath.distanceM(a, b)
            if (segment == 0.0) continue
            val bearing = GeoMath.bearingDeg(a, b)
            var along = stepM - sinceLast
            while (along < segment) {
                pts += GeoMath.offset(a, bearing, along)
                dist += total + along
                along += stepM
            }
            total += segment
            sinceLast = (sinceLast + segment) % stepM
            pts += b
            dist += total
        }
        return Sampled(pts, dist)
    }

    /** Pas d'échantillonnage pour rester sous [maxPoints] points. */
    fun stepFor(lengthM: Double, maxPoints: Int = 4_000, minStepM: Double = 20.0): Double =
        max(minStepM, lengthM / maxPoints)
}

/** Profil altimétrique prévisionnel (§8.3, §11). */
data class ElevationProfile(val distancesM: List<Double>, val elevationsM: List<Double>) {
    init {
        require(distancesM.size == elevationsM.size)
    }

    val min: Double? get() = elevationsM.minOrNull()
    val max: Double? get() = elevationsM.maxOrNull()

    /**
     * Dénivelés positif et négatif avec un seuil d'hystérésis faible : les altitudes du
     * modèle numérique de terrain sont précises et lisses, contrairement au GPS.
     */
    fun gainLoss(thresholdM: Double = 2.0): Pair<Double, Double> {
        if (elevationsM.isEmpty()) return 0.0 to 0.0
        var gain = 0.0
        var loss = 0.0
        var ref = elevationsM.first()
        for (e in elevationsM) {
            if (e - ref >= thresholdM) {
                gain += e - ref
                ref = e
            } else if (ref - e >= thresholdM) {
                loss += ref - e
                ref = e
            }
        }
        return gain to loss
    }

    /** Altitude interpolée à [distanceM] du départ. */
    fun elevationAt(distanceM: Double): Double? {
        if (elevationsM.isEmpty()) return null
        val i = distancesM.indexOfFirst { it >= distanceM }
        if (i <= 0) return elevationsM.first()
        val d0 = distancesM[i - 1]
        val d1 = distancesM[i]
        val t = if (d1 > d0) (distanceM - d0) / (d1 - d0) else 0.0
        return elevationsM[i - 1] + t * (elevationsM[i] - elevationsM[i - 1])
    }

    companion object {
        /**
         * Construit un profil à partir d'altitudes éventuellement manquantes (hors France,
         * en mer) : les trous sont comblés par interpolation ; null si trop de manques.
         */
        fun from(distancesM: List<Double>, raw: List<Double?>): ElevationProfile? {
            val known = raw.indices.filter { raw[it] != null }
            if (known.size < 2 || known.size < raw.size * 0.8) return null
            val filled = raw.indices.map { i ->
                raw[i] ?: run {
                    val before = known.lastOrNull { it < i }
                    val after = known.firstOrNull { it > i }
                    when {
                        before == null -> raw[after!!]!!
                        after == null -> raw[before]!!
                        else -> {
                            val t = (distancesM[i] - distancesM[before]) / (distancesM[after] - distancesM[before])
                            raw[before]!! + t * (raw[after]!! - raw[before]!!)
                        }
                    }
                }
            }
            return ElevationProfile(distancesM, filled)
        }
    }
}

/**
 * Durée de marche estimée (§8.3) selon la règle de randonnée DIN 33466 :
 * temps horizontal et temps vertical, le plus long plus la moitié du plus court.
 */
object DurationEstimator {
    data class Rates(val horizontalKmh: Double, val ascentMh: Double, val descentMh: Double)

    fun defaultRates(activity: ActivityType): Rates = when (activity) {
        ActivityType.WALK -> Rates(4.5, 300.0, 500.0)
        ActivityType.HIKE -> Rates(4.0, 300.0, 500.0)
        ActivityType.RUN -> Rates(9.0, 700.0, 1_000.0)
        ActivityType.OTHER -> Rates(4.0, 300.0, 500.0)
    }

    /**
     * @param personalFlatKmh vitesse moyenne en mouvement mesurée sur les sorties de
     * l'utilisateur (si assez d'historique) ; remplace la vitesse horizontale par défaut.
     */
    fun estimateMs(distanceM: Double, gainM: Double, lossM: Double, activity: ActivityType, personalFlatKmh: Double? = null): Long {
        val base = defaultRates(activity)
        val rates = personalFlatKmh?.takeIf { it in 1.5..25.0 }?.let { base.copy(horizontalKmh = it) } ?: base
        val horizontalH = distanceM / 1000.0 / rates.horizontalKmh
        val verticalH = gainM / rates.ascentMh + lossM / rates.descentMh
        val hours = max(horizontalH, verticalH) + min(horizontalH, verticalH) / 2.0
        return (hours * 3_600_000).toLong()
    }
}
