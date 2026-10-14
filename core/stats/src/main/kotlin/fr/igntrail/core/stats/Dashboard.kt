package fr.igntrail.core.stats

import fr.igntrail.core.model.ActivityType
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/** Résumé d'une sortie terminée, tel que nécessaire au tableau de bord (§13). */
data class OutingSummary(
    val startMillis: Long,
    val activity: ActivityType,
    val distanceM: Double,
    val movingMs: Long,
    val totalMs: Long,
    val gainM: Double,
)

data class Totals(
    val count: Int,
    val distanceM: Double,
    val movingMs: Long,
    val totalMs: Long,
    val gainM: Double,
) {
    /** Vitesse moyenne en mouvement sur l'ensemble des sorties (m/s). */
    val avgMovingSpeedMps: Double get() = if (movingMs > 0) distanceM / (movingMs / 1000.0) else 0.0

    companion object {
        val ZERO = Totals(0, 0.0, 0L, 0L, 0.0)

        fun of(list: List<OutingSummary>) = Totals(
            list.size,
            list.sumOf { it.distanceM },
            list.sumOf { it.movingMs },
            list.sumOf { it.totalMs },
            list.sumOf { it.gainM },
        )
    }
}

data class MonthTotals(val month: YearMonth, val totals: Totals)

data class Records(
    val longest: OutingSummary?,
    val highestGain: OutingSummary?,
    val fastest: OutingSummary?,
)

/** Tableau de bord statistique (§13) : cumuls, évolution mensuelle et records par activité. */
object Dashboard {
    /** [activity] null = toutes activités. */
    fun filter(all: List<OutingSummary>, activity: ActivityType?) = all.filter { activity == null || it.activity == activity }

    /** Les [months] derniers mois jusqu'à [now] inclus, mois vides compris (graphique continu). */
    fun monthly(list: List<OutingSummary>, months: Int, now: Long, zone: ZoneId = ZoneId.systemDefault()): List<MonthTotals> {
        val current = YearMonth.from(Instant.ofEpochMilli(now).atZone(zone))
        val byMonth = list.groupBy { YearMonth.from(Instant.ofEpochMilli(it.startMillis).atZone(zone)) }
        return (months - 1 downTo 0).map { back ->
            val m = current.minusMonths(back.toLong())
            MonthTotals(m, Totals.of(byMonth[m].orEmpty()))
        }
    }

    /** Records ; la plus rapide ne retient que les sorties de plus de 3 km (sinon peu significatif). */
    fun records(list: List<OutingSummary>) = Records(
        longest = list.maxByOrNull { it.distanceM },
        highestGain = list.maxByOrNull { it.gainM },
        fastest = list.filter { it.distanceM >= 3_000 && it.movingMs > 0 }.maxByOrNull { it.distanceM / it.movingMs },
    )
}
