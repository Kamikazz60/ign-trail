package fr.igntrail.data.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {
    @Insert
    suspend fun insertSession(session: SessionEntity): Long

    @Update
    suspend fun updateSession(session: SessionEntity)

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun session(id: Long): SessionEntity?

    @Query("SELECT * FROM sessions WHERE status = 'RECORDING' ORDER BY start_time DESC LIMIT 1")
    suspend fun activeSession(): SessionEntity?

    @Query("SELECT * FROM sessions WHERE status = 'FINISHED' ORDER BY start_time DESC")
    fun finishedSessions(): Flow<List<SessionEntity>>

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun deleteSession(id: Long)

    @Query("UPDATE sessions SET color_slow_mps = :slow, color_usual_mps = :usual, color_fast_mps = :fast WHERE id = :id")
    suspend fun setColors(id: Long, slow: Double, usual: Double, fast: Double)

    /** Noms d'activités personnalisés déjà utilisés, du plus récent au plus ancien. */
    @Query("SELECT activity_label FROM sessions WHERE activity_label IS NOT NULL GROUP BY activity_label ORDER BY MAX(start_time) DESC")
    fun activityLabels(): Flow<List<String>>

    @Query("UPDATE sessions SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Insert
    suspend fun insertPoints(points: List<TrackPointEntity>)

    @Query("SELECT * FROM track_points WHERE session_id = :sessionId ORDER BY time, id")
    suspend fun points(sessionId: Long): List<TrackPointEntity>

    @Query("SELECT MAX(time) FROM track_points WHERE session_id = :sessionId")
    suspend fun lastPointTime(sessionId: Long): Long?

    @Insert
    suspend fun insertEvent(event: SessionEventEntity)

    @Query("SELECT * FROM session_events WHERE session_id = :sessionId ORDER BY time, id")
    suspend fun events(sessionId: Long): List<SessionEventEntity>
}
