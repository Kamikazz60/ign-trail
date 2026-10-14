package fr.igntrail.core.planning

import fr.igntrail.core.geo.GeoMath
import fr.igntrail.core.model.GeoPoint

/** Point placé par l'utilisateur (départ, étape ou arrivée selon sa position dans la liste). */
data class PlanPoint(val id: Long, val position: GeoPoint)

/** Mode de liaison entre deux points (§8.2). */
enum class LinkMode {
    /** Calcul d'itinéraire sur les chemins et sentiers existants. */
    FOLLOW_PATHS,

    /** Ligne droite, jamais présentée comme un chemin praticable. */
    STRAIGHT,
}

enum class SectionKind {
    /** Tracé calculé sur le réseau de chemins. */
    ROUTED,

    /** Liaison directe, non vérifiée sur le terrain. */
    STRAIGHT,
}

data class Section(val kind: SectionKind, val points: List<GeoPoint>) {
    val lengthM: Double get() = points.zipWithNext { a, b -> GeoMath.distanceM(a, b) }.sum()
}

/** Liaison entre deux points consécutifs. */
data class Leg(
    val from: GeoPoint,
    val to: GeoPoint,
    val mode: LinkMode,
    val sections: List<Section>,
    /** Vrai si le calcul était demandé mais a échoué : liaison directe de repli. */
    val routingFailed: Boolean = false,
) {
    val lengthM: Double get() = sections.sumOf { it.lengthM }
    val straightM: Double get() = sections.filter { it.kind == SectionKind.STRAIGHT }.sumOf { it.lengthM }

    companion object {
        /** Distance maximale entre un point placé et le réseau de chemins trouvé. */
        const val MAX_SNAP_M = 300.0

        /** En dessous, l'écart avec le chemin n'est pas représenté (précision de placement). */
        private const val NEGLIGIBLE_M = 5.0

        fun straight(from: GeoPoint, to: GeoPoint, routingFailed: Boolean = false) =
            Leg(from, to, if (routingFailed) LinkMode.FOLLOW_PATHS else LinkMode.STRAIGHT, listOf(Section(SectionKind.STRAIGHT, listOf(from, to))), routingFailed)

        /**
         * Construit une liaison à partir du tracé renvoyé par le service de calcul.
         * Le service raccroche les points au chemin le plus proche, même très loin
         * (vérifié : un point en mer est relié à une route bretonne). On refuse donc
         * un raccrochage de plus de [MAX_SNAP_M] mètres ; en deçà, l'écart entre le point
         * placé et le chemin est ajouté comme liaison directe, signalée comme telle.
         *
         * @return null si le tracé est inexploitable (le repli en ligne droite est alors à faire).
         */
        fun fromRouted(from: GeoPoint, to: GeoPoint, routed: List<GeoPoint>): Leg? {
            if (routed.size < 2) return null
            val startGap = GeoMath.distanceM(from, routed.first())
            val endGap = GeoMath.distanceM(to, routed.last())
            if (startGap > MAX_SNAP_M || endGap > MAX_SNAP_M) return null
            val sections = buildList {
                if (startGap > NEGLIGIBLE_M) add(Section(SectionKind.STRAIGHT, listOf(from, routed.first())))
                add(Section(SectionKind.ROUTED, routed))
                if (endGap > NEGLIGIBLE_M) add(Section(SectionKind.STRAIGHT, listOf(routed.last(), to)))
            }
            return Leg(from, to, LinkMode.FOLLOW_PATHS, sections)
        }
    }
}

/** Itinéraire complet : géométrie continue et part non vérifiée. */
data class PlannedGeometry(val legs: List<Leg>) {
    /** Points successifs, sans doublon aux jonctions. */
    val points: List<GeoPoint> = buildList {
        legs.flatMap { it.sections }.forEach { section ->
            section.points.forEach { p -> if (lastOrNull() != p) add(p) }
        }
    }

    val lengthM: Double get() = legs.sumOf { it.lengthM }
    val straightM: Double get() = legs.sumOf { it.straightM }
    val failedLegs: Int get() = legs.count { it.routingFailed }
}
