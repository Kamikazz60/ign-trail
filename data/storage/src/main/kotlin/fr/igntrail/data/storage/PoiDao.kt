package fr.igntrail.data.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PoiDao {
    @Insert
    suspend fun insert(poi: PoiEntity): Long

    @Update
    suspend fun update(poi: PoiEntity)

    @Query("DELETE FROM pois WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM pois ORDER BY created_time DESC")
    fun all(): Flow<List<PoiEntity>>

    @Query("SELECT * FROM pois WHERE id = :id")
    suspend fun get(id: Long): PoiEntity?

    @Query("SELECT * FROM pois WHERE session_id = :sessionId ORDER BY created_time")
    fun forSession(sessionId: Long): Flow<List<PoiEntity>>

    @Insert
    suspend fun insertPhoto(photo: PhotoEntity): Long

    @Query("DELETE FROM photos WHERE id = :id")
    suspend fun deletePhoto(id: Long)

    @Query("SELECT * FROM photos WHERE poi_id = :poiId ORDER BY created_time")
    fun photosOfPoi(poiId: Long): Flow<List<PhotoEntity>>

    @Query("SELECT * FROM photos WHERE poi_id = :poiId")
    suspend fun photosOfPoiNow(poiId: Long): List<PhotoEntity>

    /** Photos de la randonnée : prises pendant la sortie ou attachées à ses points. */
    @Query(
        "SELECT * FROM photos WHERE session_id = :sessionId " +
            "OR poi_id IN (SELECT id FROM pois WHERE session_id = :sessionId) ORDER BY created_time",
    )
    fun photosOfSession(sessionId: Long): Flow<List<PhotoEntity>>

    @Query("SELECT * FROM photos WHERE session_id = :sessionId AND poi_id IS NULL")
    suspend fun loosePhotosOfSession(sessionId: Long): List<PhotoEntity>

    @Query("SELECT file FROM photos")
    suspend fun allPhotoFiles(): List<String>
}
