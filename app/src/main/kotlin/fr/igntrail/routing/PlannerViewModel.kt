package fr.igntrail.routing

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.igntrail.appContainer
import fr.igntrail.core.geo.GeoMath
import fr.igntrail.core.model.ActivityType
import fr.igntrail.core.model.GeoPoint
import fr.igntrail.core.model.Route
import fr.igntrail.core.model.RoutePoint
import fr.igntrail.core.model.RouteSource
import fr.igntrail.core.model.Waypoint
import fr.igntrail.core.planning.DurationEstimator
import fr.igntrail.core.planning.ElevationProfile
import fr.igntrail.core.planning.Leg
import fr.igntrail.core.planning.LinkMode
import fr.igntrail.core.planning.PlanEditor
import fr.igntrail.core.planning.PlanPoint
import fr.igntrail.core.planning.PlannedGeometry
import fr.igntrail.core.planning.Sampling
import fr.igntrail.data.storage.RouteRepository
import fr.igntrail.data.storage.TrackRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ElevationStatus { NONE, LOADING, READY, UNAVAILABLE }

/** Estimations du parcours en cours de création (§8.3) : jamais des mesures. */
data class PlanEstimate(
    val distanceM: Double,
    val straightM: Double,
    val gainM: Double?,
    val lossM: Double?,
    val durationMs: Long,
    /** Vrai si la durée s'appuie sur la vitesse habituelle de l'utilisateur. */
    val personalSpeed: Boolean,
)

data class PlannerUiState(
    val points: List<PlanPoint> = emptyList(),
    val selectedId: Long? = null,
    /** Point en cours de déplacement : il suit le centre de la carte. */
    val movingId: Long? = null,
    val mode: LinkMode = LinkMode.FOLLOW_PATHS,
    val activity: ActivityType = ActivityType.HIKE,
    val geometry: PlannedGeometry = PlannedGeometry(emptyList()),
    val computing: Boolean = false,
    val profile: ElevationProfile? = null,
    val elevationStatus: ElevationStatus = ElevationStatus.NONE,
    val estimate: PlanEstimate? = null,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val name: String = "",
)

/**
 * Création de parcours (§8). Chaque liaison est calculée séparément et mise en cache :
 * déplacer un point ne recalcule que les deux liaisons qui le touchent.
 */
class PlannerViewModel(
    private val routing: RoutingProvider,
    private val elevation: ElevationProvider,
    private val routes: RouteRepository,
    private val tracks: TrackRepository,
) : ViewModel() {
    private val editor = PlanEditor()
    private val legCache = mutableMapOf<LegKey, Leg>()
    private val _state = MutableStateFlow(PlannerUiState())
    val state: StateFlow<PlannerUiState> = _state.asStateFlow()

    private var computeJob: Job? = null
    private var elevationJob: Job? = null
    private val personalSpeed = mutableMapOf<ActivityType, Double?>()

    private data class LegKey(val from: GeoPoint, val to: GeoPoint, val mode: LinkMode)

    // --- Édition (§8.1) ---

    fun onMapTap(position: GeoPoint) {
        if (_state.value.movingId != null) return
        editor.add(position)
        changed()
    }

    fun select(id: Long?) = _state.update { it.copy(selectedId = id, movingId = null) }

    fun startMove(id: Long) = _state.update { it.copy(selectedId = id, movingId = id) }

    fun confirmMove(position: GeoPoint) {
        val id = _state.value.movingId ?: return
        editor.move(id, position)
        _state.update { it.copy(movingId = null) }
        changed()
    }

    fun cancelMove() = _state.update { it.copy(movingId = null) }

    fun delete(id: Long) {
        editor.delete(id)
        _state.update { it.copy(selectedId = null, movingId = null) }
        changed()
    }

    fun shift(id: Long, delta: Int) {
        editor.shift(id, delta)
        changed()
    }

    fun reverse() {
        editor.reverse()
        changed()
    }

    fun clear() {
        editor.clear()
        _state.update { it.copy(selectedId = null, movingId = null, name = "") }
        changed()
    }

    fun undo() {
        if (editor.undo()) changed()
    }

    fun redo() {
        if (editor.redo()) changed()
    }

    fun setMode(mode: LinkMode) {
        _state.update { it.copy(mode = mode) }
        changed()
    }

    fun setActivity(activity: ActivityType) {
        _state.update { it.copy(activity = activity) }
        viewModelScope.launch { refreshEstimate() }
    }

    /** Relance le calcul des liaisons qui ont échoué (retour du réseau). */
    fun retry() {
        legCache.entries.removeIf { it.value.routingFailed }
        changed()
    }

    // --- Calcul (§8.2) ---

    private fun changed() {
        _state.update {
            val selected = it.selectedId?.takeIf { id -> editor.points.any { p -> p.id == id } }
            it.copy(points = editor.points, selectedId = selected, canUndo = editor.canUndo, canRedo = editor.canRedo)
        }
        computeJob?.cancel()
        computeJob = viewModelScope.launch { compute() }
    }

    private suspend fun compute() {
        val points = editor.points.map { it.position }
        val mode = _state.value.mode
        val keys = points.zipWithNext { a, b -> LegKey(a, b, mode) }
        // Affichage immédiat : liaisons connues, les autres en ligne droite provisoire.
        publishGeometry(keys, computing = keys.any { it !in legCache && mode == LinkMode.FOLLOW_PATHS })
        for (key in keys) {
            if (key in legCache) continue
            legCache[key] = when (key.mode) {
                LinkMode.STRAIGHT -> Leg.straight(key.from, key.to)
                LinkMode.FOLLOW_PATHS -> try {
                    Leg.fromRouted(key.from, key.to, routing.route(key.from, key.to))
                        ?: Leg.straight(key.from, key.to, routingFailed = true)
                } catch (e: RoutingUnavailableException) {
                    Log.w(TAG, "Calcul impossible : ${e.message}")
                    Leg.straight(key.from, key.to, routingFailed = true)
                }
            }
            publishGeometry(keys, computing = true)
        }
        publishGeometry(keys, computing = false)
        if (legCache.size > 500) legCache.keys.retainAll(keys.toSet())
        loadElevation()
    }

    private fun publishGeometry(keys: List<LegKey>, computing: Boolean) {
        val legs = keys.map { legCache[it] ?: Leg.straight(it.from, it.to) }
        _state.update { it.copy(geometry = PlannedGeometry(legs), computing = computing) }
    }

    /** Profil altimétrique prévisionnel (§8.3, §11), après une courte pause dans l'édition. */
    private suspend fun loadElevation() {
        elevationJob?.cancel()
        val geometry = _state.value.geometry
        if (geometry.points.size < 2) {
            _state.update { it.copy(profile = null, elevationStatus = ElevationStatus.NONE) }
            refreshEstimate()
            return
        }
        _state.update { it.copy(elevationStatus = ElevationStatus.LOADING) }
        refreshEstimate()
        elevationJob = viewModelScope.launch {
            delay(600)
            val sampled = Sampling.resample(geometry.points, Sampling.stepFor(geometry.lengthM))
            val profile = try {
                ElevationProfile.from(sampled.distancesM, elevation.elevations(sampled.points))
            } catch (e: RoutingUnavailableException) {
                null
            }
            _state.update {
                it.copy(profile = profile, elevationStatus = if (profile != null) ElevationStatus.READY else ElevationStatus.UNAVAILABLE)
            }
            refreshEstimate()
        }
    }

    private suspend fun refreshEstimate() {
        val s = _state.value
        if (s.geometry.points.size < 2) {
            _state.update { it.copy(estimate = null) }
            return
        }
        val (gain, loss) = s.profile?.gainLoss()?.let { it.first to it.second } ?: (null to null)
        val speed = personalSpeedKmh(s.activity)
        val duration = DurationEstimator.estimateMs(s.geometry.lengthM, gain ?: 0.0, loss ?: 0.0, s.activity, speed)
        _state.update {
            it.copy(estimate = PlanEstimate(s.geometry.lengthM, s.geometry.straightM, gain, loss, duration, speed != null))
        }
    }

    /**
     * Vitesse moyenne en mouvement habituelle (médiane des 10 dernières sorties de l'activité),
     * si au moins 3 sorties de plus d'un kilomètre sont enregistrées.
     */
    private suspend fun personalSpeedKmh(activity: ActivityType): Double? {
        if (activity in personalSpeed) return personalSpeed[activity]
        val speeds = tracks.finishedSessions().first()
            .filter { it.activity == activity && it.distanceM >= 1_000 && it.movingMs > 0 }
            .take(10)
            .map { it.distanceM / 1000.0 / (it.movingMs / 3_600_000.0) }
            .sorted()
        val median = if (speeds.size >= 3) speeds[speeds.size / 2] else null
        personalSpeed[activity] = median
        return median
    }

    // --- Sauvegarde (§8.3) ---

    /** Enregistre le parcours ; retourne son identifiant, ou null s'il est incomplet. */
    suspend fun save(name: String): Long? {
        val s = _state.value
        if (s.geometry.points.size < 2 || s.computing) return null
        // Altitude de chaque sommet, interpolée sur le profil à sa distance depuis le départ.
        var along = 0.0
        val points = s.geometry.points.mapIndexed { i, p ->
            if (i > 0) along += GeoMath.distanceM(s.geometry.points[i - 1], p)
            RoutePoint(p.latitude, p.longitude, s.profile?.elevationAt(along))
        }
        val steps = s.points.drop(1).dropLast(1).mapIndexed { i, p ->
            Waypoint(p.position.latitude, p.position.longitude, "Étape ${i + 1}")
        }
        return routes.save(Route(name.ifBlank { "Mon parcours" }, points, steps, RouteSource.CREATED))
    }

    companion object {
        private const val TAG = "Planner"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val c = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]!!.appContainer
                PlannerViewModel(IgnRoutingProvider(), IgnElevationProvider(), c.routeRepository, c.trackRepository)
            }
        }
    }
}
