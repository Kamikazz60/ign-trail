package fr.igntrail.data.storage

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        SessionEntity::class, TrackPointEntity::class, SessionEventEntity::class,
        RouteEntity::class, RoutePointEntity::class, RouteWaypointEntity::class,
        PoiEntity::class, PhotoEntity::class,
    ],
    version = 4,
    exportSchema = true,
    // Version 2 (Phase 4) : ajout des itinéraires, sans toucher aux randonnées existantes.
    // Version 3 (Phase 6) : pression, points d'intérêt et photos.
    // Version 4 : nom d'activité personnalisé et couleurs figées par sortie.
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3), AutoMigration(from = 3, to = 4)],
)
abstract class TrailDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao

    abstract fun routeDao(): RouteDao

    abstract fun poiDao(): PoiDao

    /** Reporte le journal WAL dans le fichier principal (avant une copie de sauvegarde). */
    fun checkpoint() {
        openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }
    }

    companion object {
        const val FILE_NAME = "igntrail.db"

        /** Base privée à l'application (§18). Journal WAL : écritures fréquentes et sûres. */
        fun create(context: Context): TrailDatabase =
            Room.databaseBuilder(context, TrailDatabase::class.java, "igntrail.db")
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .build()
    }
}
