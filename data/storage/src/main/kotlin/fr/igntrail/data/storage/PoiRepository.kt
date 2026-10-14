package fr.igntrail.data.storage

import fr.igntrail.core.model.GeoPoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File

/** Catégories de points d'intérêt (§12). */
enum class PoiCategory(val label: String, val color: String) {
    PARKING("Parking", "#1565C0"),
    SOURCE("Source", "#0097A7"),
    VIEWPOINT("Point de vue", "#7B1FA2"),
    JUNCTION("Intersection", "#5D4037"),
    REFUGE("Refuge", "#2E7D32"),
    DANGER("Danger", "#D32F2F"),
    OTHER("Autre", "#616161"),
}

data class Poi(
    val id: Long,
    val sessionId: Long?,
    val name: String,
    val category: PoiCategory,
    val position: GeoPoint,
    val altitudeM: Double?,
    val description: String,
    val createdTime: Long,
)

data class Photo(val id: Long, val poiId: Long?, val sessionId: Long?, val file: File, val createdTime: Long)

/**
 * Points d'intérêt et photos. Les photos sont des fichiers du dossier privé de
 * l'application ([photoDir]) : supprimées avec leur point ou leur randonnée.
 */
class PoiRepository(db: TrailDatabase, val photoDir: File) {
    private val dao = db.poiDao()

    fun all(): Flow<List<Poi>> = dao.all().map { list -> list.map { it.toPoi() } }

    fun forSession(sessionId: Long): Flow<List<Poi>> = dao.forSession(sessionId).map { list -> list.map { it.toPoi() } }

    suspend fun get(id: Long): Poi? = dao.get(id)?.toPoi()

    /** Crée ([Poi.id] = 0) ou met à jour un point ; retourne son identifiant. */
    suspend fun save(poi: Poi): Long {
        val entity = PoiEntity(
            id = poi.id,
            sessionId = poi.sessionId,
            name = poi.name,
            category = poi.category.name,
            latitude = poi.position.latitude,
            longitude = poi.position.longitude,
            altitude = poi.altitudeM,
            description = poi.description,
            createdTime = poi.createdTime,
        )
        return if (poi.id == 0L) dao.insert(entity) else entity.id.also { dao.update(entity) }
    }

    suspend fun delete(id: Long) {
        dao.photosOfPoiNow(id).forEach { File(photoDir, it.file).delete() }
        dao.delete(id)
    }

    fun photosOfPoi(poiId: Long): Flow<List<Photo>> = dao.photosOfPoi(poiId).map { l -> l.map { it.toPhoto() } }

    fun photosOfSession(sessionId: Long): Flow<List<Photo>> = dao.photosOfSession(sessionId).map { l -> l.map { it.toPhoto() } }

    suspend fun addPhoto(file: File, poiId: Long?, sessionId: Long?): Long =
        dao.insertPhoto(PhotoEntity(poiId = poiId, sessionId = sessionId, file = file.name, createdTime = System.currentTimeMillis()))

    suspend fun deletePhoto(photo: Photo) {
        photo.file.delete()
        dao.deletePhoto(photo.id)
    }

    /** Photos prises pendant la randonnée, hors points d'intérêt : supprimées avec elle. */
    suspend fun deleteSessionPhotos(sessionId: Long) {
        dao.loosePhotosOfSession(sessionId).forEach {
            File(photoDir, it.file).delete()
            dao.deletePhoto(it.id)
        }
    }

    /** Supprime les fichiers photo qui ne sont plus référencés (photo abandonnée, plantage…). */
    suspend fun cleanOrphanFiles() {
        val known = dao.allPhotoFiles().toSet()
        photoDir.listFiles()?.filter { it.name !in known && System.currentTimeMillis() - it.lastModified() > 3_600_000 }
            ?.forEach { it.delete() }
    }

    private fun PoiEntity.toPoi() = Poi(
        id, sessionId, name, runCatching { PoiCategory.valueOf(category) }.getOrDefault(PoiCategory.OTHER),
        GeoPoint(latitude, longitude), altitude, description, createdTime,
    )

    private fun PhotoEntity.toPhoto() = Photo(id, poiId, sessionId, File(photoDir, file), createdTime)
}
