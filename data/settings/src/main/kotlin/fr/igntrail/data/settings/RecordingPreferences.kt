package fr.igntrail.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import fr.igntrail.core.model.ActivityType
import kotlinx.coroutines.flow.first

private val Context.recordingStore: DataStore<Preferences> by preferencesDataStore(name = "recording")

/** Choix mémorisés au démarrage d'une randonnée (§5.1, §6.1). */
class RecordingPreferences(private val context: Context) {
    private val lastActivityKey = stringPreferencesKey("last_activity")
    private val lastLabelKey = stringPreferencesKey("last_label")
    private fun autoPauseKey(activity: ActivityType) = intPreferencesKey("auto_pause_s_${activity.name}")

    suspend fun lastActivity(): ActivityType? =
        context.recordingStore.data.first()[lastActivityKey]?.let { runCatching { ActivityType.valueOf(it) }.getOrNull() }

    /** Délai de pause automatique en secondes (0 = désactivée), ou null si jamais choisi. */
    suspend fun autoPauseSeconds(activity: ActivityType): Int? = context.recordingStore.data.first()[autoPauseKey(activity)]

    /** Dernier nom libre choisi pour l'activité « Autre ». */
    suspend fun lastLabel(): String? = context.recordingStore.data.first()[lastLabelKey]

    suspend fun remember(activity: ActivityType, autoPauseSeconds: Int, label: String? = null) {
        context.recordingStore.edit {
            it[lastActivityKey] = activity.name
            if (label != null) it[lastLabelKey] = label
            it[autoPauseKey(activity)] = autoPauseSeconds
        }
    }
}
