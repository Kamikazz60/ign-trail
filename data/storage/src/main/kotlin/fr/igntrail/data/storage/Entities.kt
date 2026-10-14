package fr.igntrail.data.storage

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Une session d'enregistrement. Les colonnes de synthèse sont un cache recalculable. */
@Entity(tableName = "sessions", indices = [Index("status")])
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val activity: String,
    @ColumnInfo(name = "auto_pause_seconds") val autoPauseSeconds: Int,
    @ColumnInfo(name = "start_time") val startTime: Long,
    @ColumnInfo(name = "end_time") val endTime: Long? = null,
    /** RECORDING tant que la session n'est pas terminée (même après un plantage). */
    val status: String,
    @ColumnInfo(name = "distance_m") val distanceM: Double = 0.0,
    @ColumnInfo(name = "moving_ms") val movingMs: Long = 0,
    @ColumnInfo(name = "paused_ms") val pausedMs: Long = 0,
    @ColumnInfo(name = "interrupted_ms") val interruptedMs: Long = 0,
    @ColumnInfo(name = "max_speed_mps") val maxSpeedMps: Double = 0.0,
    @ColumnInfo(name = "elevation_gain_m") val elevationGainM: Double = 0.0,
    @ColumnInfo(name = "elevation_loss_m") val elevationLossM: Double = 0.0,
    @ColumnInfo(name = "min_altitude_m") val minAltitudeM: Double? = null,
    @ColumnInfo(name = "max_altitude_m") val maxAltitudeM: Double? = null,
    /** Nom libre de l'activité « Autre » (ski, roller…), réutilisable (version 4). */
    @ColumnInfo(name = "activity_label", defaultValue = "NULL") val activityLabel: String? = null,
    /**
     * Seuils de couleur du tracé (m/s), calculés une fois à la fin de la sortie d'après
     * sa propre moyenne, puis figés (version 4).
     */
    @ColumnInfo(name = "color_slow_mps", defaultValue = "NULL") val colorSlowMps: Double? = null,
    @ColumnInfo(name = "color_usual_mps", defaultValue = "NULL") val colorUsualMps: Double? = null,
    @ColumnInfo(name = "color_fast_mps", defaultValue = "NULL") val colorFastMps: Double? = null,
)

/** Mesure GPS brute (§5.2), conservée même si le filtre l'a écartée. */
@Entity(
    tableName = "track_points",
    foreignKeys = [ForeignKey(SessionEntity::class, ["id"], ["session_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("session_id", "time")],
)
data class TrackPointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "session_id") val sessionId: Long,
    val time: Long,
    val latitude: Double,
    val longitude: Double,
    @ColumnInfo(name = "h_accuracy") val horizontalAccuracyM: Float?,
    /** Altitude au-dessus du niveau moyen de la mer. */
    val altitude: Double?,
    @ColumnInfo(name = "v_accuracy") val verticalAccuracyM: Float?,
    val speed: Float?,
    val bearing: Float?,
    @ColumnInfo(name = "bearing_accuracy") val bearingAccuracyDeg: Float?,
    val source: String,
    /** Décision du filtre (qualité estimée). */
    val quality: String,
    /** État de la session au moment de la mesure. */
    val state: String,
    /** Pression atmosphérique (hPa), si baromètre (version 3). */
    @ColumnInfo(defaultValue = "NULL") val pressure: Float? = null,
)

@Entity(
    tableName = "session_events",
    foreignKeys = [ForeignKey(SessionEntity::class, ["id"], ["session_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("session_id")],
)
data class SessionEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "session_id") val sessionId: Long,
    val time: Long,
    val type: String,
)

/** Itinéraire importé, créé ou issu d'une randonnée (§9). */
@Entity(tableName = "routes")
data class RouteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val source: String,
    @ColumnInfo(name = "created_time") val createdTime: Long,
    @ColumnInfo(name = "distance_m") val distanceM: Double,
    @ColumnInfo(name = "elevation_gain_m") val elevationGainM: Double?,
    @ColumnInfo(name = "elevation_loss_m") val elevationLossM: Double?,
)

@Entity(
    tableName = "route_points",
    foreignKeys = [ForeignKey(RouteEntity::class, ["id"], ["route_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("route_id", "seq")],
)
data class RoutePointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "route_id") val routeId: Long,
    val seq: Int,
    val latitude: Double,
    val longitude: Double,
    val elevation: Double?,
    val time: Long?,
)

@Entity(
    tableName = "route_waypoints",
    foreignKeys = [ForeignKey(RouteEntity::class, ["id"], ["route_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("route_id")],
)
data class RouteWaypointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "route_id") val routeId: Long,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val elevation: Double?,
)

/** Point d'intérêt (§12) : lié à une randonnée, ou indépendant (session_id nul). */
@Entity(
    tableName = "pois",
    foreignKeys = [ForeignKey(SessionEntity::class, ["id"], ["session_id"], onDelete = ForeignKey.SET_NULL)],
    indices = [Index("session_id")],
)
data class PoiEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "session_id") val sessionId: Long?,
    val name: String,
    val category: String,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double?,
    val description: String,
    @ColumnInfo(name = "created_time") val createdTime: Long,
)

/** Photo stockée dans le dossier privé de l'application (§12, §18). */
@Entity(
    tableName = "photos",
    foreignKeys = [
        ForeignKey(PoiEntity::class, ["id"], ["poi_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(SessionEntity::class, ["id"], ["session_id"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("poi_id"), Index("session_id")],
)
data class PhotoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "poi_id") val poiId: Long?,
    @ColumnInfo(name = "session_id") val sessionId: Long?,
    /** Nom du fichier dans le dossier photos. */
    val file: String,
    @ColumnInfo(name = "created_time") val createdTime: Long,
)
