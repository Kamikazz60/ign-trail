package fr.igntrail.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.igntrail.appContainer
import fr.igntrail.core.model.GeoPoint
import fr.igntrail.core.model.LocationFix
import fr.igntrail.data.location.LocationSource
import fr.igntrail.data.settings.BaseMap
import fr.igntrail.data.settings.MapDisplaySettings
import fr.igntrail.data.settings.MapPreferences
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class GpsStatus { SEARCHING, OK, ERROR }

data class MapUiState(
    /** Dernière position GPS réelle reçue pendant cette session. */
    val fix: LocationFix? = null,
    /** Position provisoire (dernière connue), affichée tant que le GPS n'a pas répondu. */
    val approximate: LocationFix? = null,
    val following: Boolean = true,
    val gpsStatus: GpsStatus = GpsStatus.SEARCHING,
)

/** Cadrage mémorisé pour le restaurer si la vue carte est recréée. */
data class SavedCamera(val latitude: Double, val longitude: Double, val zoom: Double, val bearing: Double = 0.0)

class MapViewModel(
    private val locationSource: LocationSource,
    private val preferences: MapPreferences,
    private val serviceFixes: Flow<LocationFix?>,
) : ViewModel() {
    private val follow = CameraFollowController()
    private val _state = MutableStateFlow(MapUiState())
    val state: StateFlow<MapUiState> = _state.asStateFlow()

    val display: StateFlow<MapDisplaySettings> =
        preferences.display.stateIn(viewModelScope, SharingStarted.Eagerly, MapDisplaySettings())

    // Toutes les commandes sont conservées : le premier cadrage (avec zoom) ne doit pas
    // être écrasé par un simple suivi si la carte n'est pas encore prête.
    private val commands = Channel<CameraCommand>(Channel.UNLIMITED)
    val cameraCommands: Flow<CameraCommand> = commands.receiveAsFlow()

    var savedCamera: SavedCamera? = null
        private set

    private var lastSavedPositionAt = 0L

    /** À appeler une fois l'autorisation de localisation obtenue. */
    fun loadApproximatePosition() {
        if (_state.value.fix != null || _state.value.approximate != null) return
        viewModelScope.launch {
            val candidate = locationSource.lastKnown() ?: preferences.lastKnownPosition() ?: return@launch
            if (_state.value.fix != null) return@launch
            _state.update { it.copy(approximate = candidate) }
            follow.onApproximatePosition(GeoPoint(candidate.latitude, candidate.longitude))?.let(commands::trySend)
        }
    }

    /** Collecte les positions tant que l'écran est visible (appelée dans repeatOnLifecycle). */
    /**
     * Positions de la carte : son propre abonnement, plus celles du service quand une sortie
     * ou un guidage est en cours. Une position déjà vue (même heure) n'est traitée qu'une fois.
     */
    suspend fun collectLocation() {
        val arbiter = FixArbiter(SERVICE_PRIORITY_MS)
        merge(
            locationSource.updates(LOCATION_INTERVAL_MS)
                .catch { _state.update { s -> s.copy(gpsStatus = GpsStatus.ERROR) } }
                .map { it to false },
            serviceFixes.filterNotNull().map { it to true },
        ).collect { (fix, fromService) ->
            if (arbiter.accept(fix.timeMillis, fromService)) onFix(fix)
        }
    }

    private fun onFix(fix: LocationFix) {
        _state.update { it.copy(fix = fix, approximate = null, gpsStatus = GpsStatus.OK) }
        follow.onFix(fix)?.let(commands::trySend)
        val accurate = (fix.horizontalAccuracyM ?: Float.MAX_VALUE) <= 50f
        if (accurate && fix.timeMillis - lastSavedPositionAt > SAVE_POSITION_EVERY_MS) {
            lastSavedPositionAt = fix.timeMillis
            viewModelScope.launch { preferences.saveLastKnownPosition(fix) }
        }
    }

    fun onUserGesture() {
        follow.onUserGesture()
        _state.update { it.copy(following = false) }
    }

    fun onRecenter() {
        follow.onRecenter(_state.value.fix)?.let(commands::trySend)
        _state.update { it.copy(following = true) }
    }

    fun onCameraIdle(camera: SavedCamera) {
        savedCamera = camera
    }

    /** La vue carte a été recréée : on restaure le suivi sans recadrer. */
    fun onMapRecreated() {
        follow.restore(following = _state.value.following, alreadyCentered = savedCamera != null)
    }

    fun setBaseMap(baseMap: BaseMap) {
        viewModelScope.launch { preferences.setBaseMap(baseMap) }
    }

    fun setShowOfflineZones(enabled: Boolean) {
        viewModelScope.launch { preferences.setShowOfflineZones(enabled) }
    }

    fun setHillshade(enabled: Boolean) {
        viewModelScope.launch { preferences.setHillshade(enabled) }
    }

    companion object {
        private const val LOCATION_INTERVAL_MS = 1_000L
        private const val SAVE_POSITION_EVERY_MS = 30_000L

        /** Sans position du service depuis ce délai, la carte reprend son propre abonnement. */
        private const val SERVICE_PRIORITY_MS = 5_000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]!!.appContainer
                MapViewModel(container.locationSource, container.mapPreferences, container.liveFix)
            }
        }
    }
}
