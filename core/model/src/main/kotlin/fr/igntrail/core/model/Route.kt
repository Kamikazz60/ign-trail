package fr.igntrail.core.model

/** Point d'un itinéraire (altitude et heure facultatives, comme dans un GPX). */
data class RoutePoint(
    val latitude: Double,
    val longitude: Double,
    val elevationM: Double? = null,
    val timeMillis: Long? = null,
)

/** Point remarquable nommé (waypoint GPX). */
data class Waypoint(val latitude: Double, val longitude: Double, val name: String, val elevationM: Double? = null)

/** Origine d'un itinéraire (§9). */
enum class RouteSource(val label: String) {
    IMPORTED("Importé (GPX)"),
    RECORDED("Randonnée enregistrée"),
    CREATED("Créé sur la carte"),
}

/** Itinéraire à suivre : une ligne continue, éventuellement accompagnée de points nommés. */
data class Route(
    val name: String,
    val points: List<RoutePoint>,
    val waypoints: List<Waypoint> = emptyList(),
    val source: RouteSource = RouteSource.IMPORTED,
) {
    /** Parcours en sens inverse (§9) ; les points nommés restent inchangés. */
    fun reversed(): Route = copy(points = points.asReversed().toList())
}
