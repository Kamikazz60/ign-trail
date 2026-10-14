package fr.igntrail.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Réglages du guidage (§9, §16) : tolérance de sortie de parcours et alertes. */
data class GuidanceSettings(
    val toleranceM: Int = 40,
    val vibrate: Boolean = true,
    val sound: Boolean = true,
) {
    companion object {
        val TOLERANCE_CHOICES_M = listOf(20, 40, 80)
    }
}

private val Context.navigationStore: DataStore<Preferences> by preferencesDataStore(name = "navigation")

class NavigationPreferences(private val context: Context) {
    private val toleranceKey = intPreferencesKey("tolerance_m")
    private val vibrateKey = booleanPreferencesKey("vibrate")
    private val soundKey = booleanPreferencesKey("sound")

    val settings: Flow<GuidanceSettings> = context.navigationStore.data.map { p ->
        GuidanceSettings(
            toleranceM = p[toleranceKey] ?: 40,
            vibrate = p[vibrateKey] ?: true,
            sound = p[soundKey] ?: true,
        )
    }

    suspend fun update(settings: GuidanceSettings) {
        context.navigationStore.edit {
            it[toleranceKey] = settings.toleranceM
            it[vibrateKey] = settings.vibrate
            it[soundKey] = settings.sound
        }
    }
}
