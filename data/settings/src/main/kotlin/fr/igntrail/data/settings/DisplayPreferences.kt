package fr.igntrail.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Unité de vitesse affichée (§16). */
enum class SpeedUnit(val label: String) {
    /** Allure en course à pied, vitesse pour les autres activités. */
    AUTO("Automatique"),
    KMH("km/h"),
    PACE("min/km"),
}

data class DisplaySettings(
    val speedUnit: SpeedUnit = SpeedUnit.AUTO,
)

private val Context.displayStore: DataStore<Preferences> by preferencesDataStore(name = "display")

class DisplayPreferences(private val context: Context) {
    private val unitKey = stringPreferencesKey("speed_unit")

    val settings: Flow<DisplaySettings> = context.displayStore.data.map { p ->
        DisplaySettings(
            speedUnit = p[unitKey]?.let { runCatching { SpeedUnit.valueOf(it) }.getOrNull() } ?: SpeedUnit.AUTO,
        )
    }

    suspend fun setSpeedUnit(unit: SpeedUnit) {
        context.displayStore.edit { it[unitKey] = unit.name }
    }
}
