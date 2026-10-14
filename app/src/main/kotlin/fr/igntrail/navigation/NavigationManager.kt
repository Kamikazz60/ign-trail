package fr.igntrail.navigation

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import fr.igntrail.core.model.GeoPoint
import fr.igntrail.core.model.LocationFix
import fr.igntrail.core.model.Route
import fr.igntrail.core.navigation.DirectionTo
import fr.igntrail.core.navigation.GuidanceEvent
import fr.igntrail.core.navigation.GuidanceParams
import fr.igntrail.core.navigation.GuidanceState
import fr.igntrail.core.navigation.Navigator
import fr.igntrail.data.settings.GuidanceSettings
import fr.igntrail.data.settings.NavigationPreferences
import fr.igntrail.tracking.TrackingService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Ce que l'on suit (§9, §10). */
enum class GuidanceKind {
    /** Itinéraire importé, créé ou randonnée enregistrée. */
    ROUTE,

    /** Retour sur mes pas : le tracé de la randonnée en cours, en sens inverse (§10). */
    TRACK_BACK,
}

sealed interface NavigationStatus {
    data object Idle : NavigationStatus

    data class Following(
        val name: String,
        /** Itinéraire tel qu'il est suivi (déjà inversé si sens inverse). */
        val route: Route,
        val reversed: Boolean,
        val kind: GuidanceKind,
        /** Null tant qu'aucune position n'a été reçue. */
        val state: GuidanceState?,
    ) : NavigationStatus

    /** Direction du départ à vol d'oiseau (§10) : ne tient compte ni du terrain ni des obstacles. */
    data class DirectionToStart(val start: GeoPoint, val direction: DirectionTo?, val arrived: Boolean) : NavigationStatus
}

/**
 * Guidage (§9, §10). Reçoit les positions du service au premier plan : le guidage et
 * ses alertes fonctionnent écran éteint, et hors connexion (tout est local).
 */
class NavigationManager(
    private val context: Context,
    private val preferences: NavigationPreferences,
    private val alerts: GuidanceAlerts,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))
    private val _status = MutableStateFlow<NavigationStatus>(NavigationStatus.Idle)
    val status: StateFlow<NavigationStatus> = _status.asStateFlow()

    val settings: StateFlow<GuidanceSettings> =
        preferences.settings.stateIn(scope, SharingStarted.Eagerly, GuidanceSettings())

    private var navigator: Navigator? = null

    fun follow(route: Route, reversed: Boolean, kind: GuidanceKind = GuidanceKind.ROUTE) {
        val followed = if (reversed) route.reversed() else route
        if (followed.points.size < 2) return
        scope.launch {
            navigator = newNavigator(followed)
            _status.value = NavigationStatus.Following(followed.name, followed, reversed, kind, null)
            alerts.clear()
            startService()
        }
    }

    fun directionToStart(start: GeoPoint) {
        scope.launch {
            navigator = null
            _status.value = NavigationStatus.DirectionToStart(start, null, arrived = false)
            alerts.clear()
            startService()
        }
    }

    fun stop() {
        scope.launch {
            navigator = null
            _status.value = NavigationStatus.Idle
            alerts.clear()
        }
    }

    fun updateSettings(newSettings: GuidanceSettings) {
        scope.launch {
            preferences.update(newSettings)
            // La nouvelle tolérance s'applique immédiatement.
            (status.value as? NavigationStatus.Following)?.let { navigator = newNavigator(it.route, newSettings) }
        }
    }

    /** Appelé par le service pour chaque position. */
    fun onFix(fix: LocationFix) {
        scope.launch {
            val here = GeoPoint(fix.latitude, fix.longitude)
            when (val current = _status.value) {
                is NavigationStatus.Following -> {
                    val nav = navigator ?: return@launch
                    val (state, event) = nav.update(here)
                    _status.value = current.copy(state = state)
                    if (event != null) alerts.signal(event, settings.value, state)
                }
                is NavigationStatus.DirectionToStart -> {
                    val direction = DirectionTo.compute(here, current.start)
                    val arrived = current.arrived || direction.distanceM <= ARRIVAL_M
                    if (arrived && !current.arrived) alerts.signal(GuidanceEvent.ARRIVED, settings.value, null)
                    _status.value = current.copy(direction = direction, arrived = arrived)
                }
                NavigationStatus.Idle -> Unit
            }
        }
    }

    private fun newNavigator(route: Route, s: GuidanceSettings = settings.value) = Navigator(
        route.points.map { GeoPoint(it.latitude, it.longitude) },
        GuidanceParams(toleranceM = s.toleranceM.toDouble()),
    )

    private fun startService() {
        // Démarré depuis l'application au premier plan (bouton « Suivre »).
        runCatching {
            ContextCompat.startForegroundService(
                context,
                Intent(context, TrackingService::class.java).setAction(TrackingService.ACTION_RECORD),
            )
        }
    }

    companion object {
        private const val ARRIVAL_M = 30.0
    }
}
