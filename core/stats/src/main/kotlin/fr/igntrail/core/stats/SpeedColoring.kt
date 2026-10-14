package fr.igntrail.core.stats

import fr.igntrail.core.geo.GeoMath
import fr.igntrail.core.model.ActivityType
import fr.igntrail.core.model.GeoPoint
import fr.igntrail.core.tracking.TimedPoint

/** Portion du tracé de vitesse homogène (§7). */
data class SpeedChunk(val points: List<GeoPoint>, val speedMps: Double, val distanceM: Double)

/**
 * Découpage du tracé en portions d'au moins [minDistanceM] mètres et [minDurationMs]
 * millisecondes (§7) : la couleur ne change pas au gré du bruit GPS d'un point à l'autre.
 *
 * Les intervalles de plus de [maxGapMs] entre deux points (pause automatique, perte de
 * signal) restent dessinés mais ne comptent pas dans la vitesse : un arrêt pique-nique
 * ne colore pas le passage en « très lent ».
 */
object SpeedChunker {
    fun chunks(
        segments: List<List<TimedPoint>>,
        minDistanceM: Double = 60.0,
        minDurationMs: Long = 20_000,
        maxGapMs: Long = 30_000,
    ): List<SpeedChunk> {
        val result = mutableListOf<SpeedChunk>()
        for (segment in segments) {
            if (segment.size < 2) continue
            var points = mutableListOf(segment.first().point)
            var dist = 0.0
            var dur = 0L
            for (i in 1 until segment.size) {
                val a = segment[i - 1]
                val b = segment[i]
                points += b.point
                val dt = b.timeMillis - a.timeMillis
                if (dt in 1..maxGapMs) {
                    dist += GeoMath.distanceM(a.point, b.point)
                    dur += dt
                }
                if (dist >= minDistanceM && dur >= minDurationMs) {
                    result += SpeedChunk(points, dist / (dur / 1000.0), dist)
                    points = mutableListOf(b.point)
                    dist = 0.0
                    dur = 0L
                }
            }
            // Reste du segment : fusionné avec la portion précédente s'il est trop court pour être fiable.
            if (points.size >= 2) {
                val previous = result.lastOrNull()
                val sameSegment = previous != null && previous.points.last() == points.first()
                if (dur > 0 && dist > 0 && (dist >= minDistanceM / 2 || !sameSegment)) {
                    result += SpeedChunk(points, dist / (dur / 1000.0), dist)
                } else if (sameSegment) {
                    result[result.lastIndex] = previous!!.copy(points = previous.points + points.drop(1), distanceM = previous.distanceM + dist)
                } else {
                    // Segment entier trop court, sans vitesse fiable : vitesse neutre (habituelle).
                    result += SpeedChunk(points, Double.NaN, dist)
                }
            }
        }
        return result
    }
}

/**
 * Seuils de couleur (m/s) d'une sortie (§7, choix de l'utilisateur du 14/10/2026) :
 * calculés UNE FOIS, à la fin de la sortie, par rapport à sa propre vitesse moyenne,
 * puis figés. Rouge = portions nettement plus lentes que d'habitude dans cette sortie,
 * jaune = la moyenne, vert = portions nettement plus rapides.
 */
data class SpeedThresholds(val slowMps: Double, val usualMps: Double, val fastMps: Double) {
    init {
        require(slowMps < usualMps && usualMps < fastMps) { "Seuils croissants attendus" }
    }

    companion object {
        private const val MIN_DISTANCE_M = 300.0

        /** Écart minimal autour de la moyenne : une sortie très régulière reste à dominante jaune. */
        private const val MIN_SPREAD = 0.08

        /**
         * Jaune = vitesse moyenne en mouvement de la sortie ; rouge et vert = 20ᵉ et 80ᵉ centiles
         * des vitesses de ses portions (pondérés par la distance), écartés d'au moins 8 % de la
         * moyenne. Null si la sortie est trop courte pour colorer utilement.
         */
        fun forOuting(chunks: List<SpeedChunk>, averageMps: Double): SpeedThresholds? {
            val valid = chunks.filter { it.speedMps.isFinite() && it.speedMps > 0 }.sortedBy { it.speedMps }
            val total = valid.sumOf { it.distanceM }
            if (averageMps <= 0 || valid.size < 3 || total < MIN_DISTANCE_M) return null
            fun percentile(p: Double): Double {
                var acc = 0.0
                for (c in valid) {
                    acc += c.distanceM
                    if (acc >= p * total) return c.speedMps
                }
                return valid.last().speedMps
            }
            val slow = minOf(percentile(0.20), averageMps * (1 - MIN_SPREAD))
            val fast = maxOf(percentile(0.80), averageMps * (1 + MIN_SPREAD))
            return SpeedThresholds(slow, averageMps, fast)
        }
    }
}
