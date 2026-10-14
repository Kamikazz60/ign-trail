package fr.igntrail.data.storage

import androidx.room.withTransaction
import fr.igntrail.core.geo.GeoMath
import fr.igntrail.core.model.GeoPoint
import fr.igntrail.core.model.Route
import fr.igntrail.core.model.RoutePoint
import fr.igntrail.core.model.RouteSource
import fr.igntrail.core.model.Waypoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Résumé d'un itinéraire pour la liste. */
data class RouteSummary(
    val id: Long,
    val name: String,
    val source: RouteSource,
    val createdTime: Long,
    val distanceM: Double,
    val elevationGainM: Double?,
    val elevationLossM: Double?,
)

class RouteRepository(private val db: TrailDatabase) {
    private val dao = db.routeDao()

    fun routes(): Flow<List<RouteSummary>> = dao.routes().map { list -> list.map { it.toSummary() } }

    suspend fun save(route: Route, createdTime: Long = System.currentTimeMillis()): Long = db.withTransaction {
        val (gain, loss) = elevation(route.points)
        val id = dao.insertRoute(
            RouteEntity(
                name = route.name,
                source = route.source.name,
                createdTime = createdTime,
                distanceM = length(route.points),
                elevationGainM = gain,
                elevationLossM = loss,
            ),
        )
        dao.insertPoints(route.points.mapIndexed { i, p -> RoutePointEntity(routeId = id, seq = i, latitude = p.latitude, longitude = p.longitude, elevation = p.elevationM, time = p.timeMillis) })
        dao.insertWaypoints(route.waypoints.map { RouteWaypointEntity(routeId = id, name = it.name, latitude = it.latitude, longitude = it.longitude, elevation = it.elevationM) })
        id
    }

    suspend fun load(id: Long): Route? {
        val r = dao.route(id) ?: return null
        return Route(
            name = r.name,
            points = dao.points(id).map { RoutePoint(it.latitude, it.longitude, it.elevation, it.time) },
            waypoints = dao.waypoints(id).map { Waypoint(it.latitude, it.longitude, it.name, it.elevation) },
            source = runCatching { RouteSource.valueOf(r.source) }.getOrDefault(RouteSource.IMPORTED),
        )
    }

    suspend fun summary(id: Long): RouteSummary? = dao.route(id)?.toSummary()

    suspend fun rename(id: Long, name: String) = dao.rename(id, name)

    suspend fun delete(id: Long) = dao.delete(id)

    private fun RouteEntity.toSummary() = RouteSummary(
        id, name, runCatching { RouteSource.valueOf(source) }.getOrDefault(RouteSource.IMPORTED),
        createdTime, distanceM, elevationGainM, elevationLossM,
    )

    companion object {
        fun length(points: List<RoutePoint>): Double = points.zipWithNext { a, b ->
            GeoMath.distanceM(GeoPoint(a.latitude, a.longitude), GeoPoint(b.latitude, b.longitude))
        }.sum()

        /**
         * Dénivelés prévisionnels à partir des altitudes du fichier (si présentes),
         * avec un seuil de 3 m : les altitudes d'un GPX sont en général déjà lissées.
         */
        fun elevation(points: List<RoutePoint>, thresholdM: Double = 3.0): Pair<Double?, Double?> {
            val elevations = points.mapNotNull { it.elevationM }
            if (elevations.size < points.size / 2 || elevations.size < 2) return null to null
            var gain = 0.0
            var loss = 0.0
            var ref = elevations.first()
            for (e in elevations) {
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
    }
}
