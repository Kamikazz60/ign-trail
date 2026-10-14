package fr.igntrail.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import fr.igntrail.core.model.LocationFix
import fr.igntrail.core.model.LocationSourceType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class BaseMap { PLAN_IGN_CLASSIQUE, PLAN_IGN_STANDARD, ORTHOPHOTOS }

data class MapDisplaySettings(
    val baseMap: BaseMap = BaseMap.PLAN_IGN_CLASSIQUE,
    val hillshade: Boolean = true,
    /** Zones disponibles hors ligne teintées sur la carte (§4). */
    val showOfflineZones: Boolean = true,
)

private val Context.mapStore: DataStore<Preferences> by preferencesDataStore(name = "map")

class MapPreferences(private val context: Context) {
    private object Keys {
        val baseMap = stringPreferencesKey("base_map")
        val hillshade = booleanPreferencesKey("hillshade")
        val offlineZones = booleanPreferencesKey("offline_zones")
        val lastLat = doublePreferencesKey("last_lat")
        val lastLon = doublePreferencesKey("last_lon")
        val lastTime = longPreferencesKey("last_time")
    }

    val display: Flow<MapDisplaySettings> = context.mapStore.data.map { p ->
        MapDisplaySettings(
            baseMap = p[Keys.baseMap]?.let { runCatching { BaseMap.valueOf(it) }.getOrNull() }
                ?: BaseMap.PLAN_IGN_CLASSIQUE,
            hillshade = p[Keys.hillshade] ?: true,
            showOfflineZones = p[Keys.offlineZones] ?: true,
        )
    }

    suspend fun setBaseMap(value: BaseMap) {
        context.mapStore.edit { it[Keys.baseMap] = value.name }
    }

    suspend fun setShowOfflineZones(enabled: Boolean) {
        context.mapStore.edit { it[Keys.offlineZones] = enabled }
    }

    suspend fun setHillshade(enabled: Boolean) {
        context.mapStore.edit { it[Keys.hillshade] = enabled }
    }

    /** Dernière position GPS fiable, stockée uniquement sur l'appareil. */
    suspend fun lastKnownPosition(): LocationFix? {
        val p = context.mapStore.data.first()
        val lat = p[Keys.lastLat] ?: return null
        val lon = p[Keys.lastLon] ?: return null
        return LocationFix(
            latitude = lat,
            longitude = lon,
            timeMillis = p[Keys.lastTime] ?: 0L,
            horizontalAccuracyM = null,
            source = LocationSourceType.STORED,
        )
    }

    suspend fun saveLastKnownPosition(fix: LocationFix) {
        context.mapStore.edit {
            it[Keys.lastLat] = fix.latitude
            it[Keys.lastLon] = fix.longitude
            it[Keys.lastTime] = fix.timeMillis
        }
    }
}
