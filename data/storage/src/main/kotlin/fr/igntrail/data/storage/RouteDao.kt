package fr.igntrail.data.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RouteDao {
    @Insert
    suspend fun insertRoute(route: RouteEntity): Long

    @Insert
    suspend fun insertPoints(points: List<RoutePointEntity>)

    @Insert
    suspend fun insertWaypoints(waypoints: List<RouteWaypointEntity>)

    @Query("SELECT * FROM routes ORDER BY created_time DESC")
    fun routes(): Flow<List<RouteEntity>>

    @Query("SELECT * FROM routes WHERE id = :id")
    suspend fun route(id: Long): RouteEntity?

    @Query("SELECT * FROM route_points WHERE route_id = :id ORDER BY seq")
    suspend fun points(id: Long): List<RoutePointEntity>

    @Query("SELECT * FROM route_waypoints WHERE route_id = :id ORDER BY id")
    suspend fun waypoints(id: Long): List<RouteWaypointEntity>

    @Query("UPDATE routes SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM routes WHERE id = :id")
    suspend fun delete(id: Long)
}
