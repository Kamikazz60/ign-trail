package fr.igntrail.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.igntrail.appContainer
import fr.igntrail.core.geo.GeoBounds
import fr.igntrail.core.model.Route
import fr.igntrail.core.model.RoutePoint
import fr.igntrail.core.model.RouteSource
import fr.igntrail.data.storage.RouteRepository
import fr.igntrail.export.GpxExport
import fr.igntrail.map.MapViewModel
import fr.igntrail.navigation.GpxImport
import fr.igntrail.navigation.ImportOutcome
import fr.igntrail.routing.PlannerViewModel
import fr.igntrail.tracking.RecordingStatus
import fr.igntrail.tracking.TrackingNotifications
import fr.igntrail.ui.carte.CarteScreen
import fr.igntrail.ui.creer.SavedPlanDialog
import fr.igntrail.ui.offline.CorridorDownloadSheet
import fr.igntrail.ui.offline.OfflineMapsScreen
import fr.igntrail.ui.parcours.HikeDetailScreen
import fr.igntrail.ui.parcours.ParcoursActions
import fr.igntrail.ui.parcours.ParcoursScreen
import fr.igntrail.ui.recording.FinishedSheet
import fr.igntrail.ui.recording.InterruptedDialog
import fr.igntrail.ui.settings.SettingsScreen
import fr.igntrail.ui.stats.StatsScreen
import fr.igntrail.ui.theme.LocalPanelColors
import fr.igntrail.ui.theme.TrailColors
import kotlinx.coroutines.launch

/** Ouverture de la feuille « autour d'un parcours », avec ou sans parcours présélectionné. */
private data class CorridorRequest(val sessionId: Long? = null, val routeId: Long? = null)

/** Itinéraire affiché sur la carte avant d'être suivi. */
data class RoutePreview(val name: String, val route: Route, val distanceM: Double)

enum class Tab(val label: String, val icon: ImageVector) {
    CARTE("Carte", Icons.Outlined.Map),
    CREER("Créer", Icons.Outlined.Route),
    PARCOURS("Parcours", Icons.Outlined.Folder),
    STATISTIQUES("Statistiques", Icons.Outlined.BarChart),
}

@Composable
fun AppRoot(incomingGpx: Uri?, onIncomingGpxConsumed: () -> Unit) {
    var tab by rememberSaveable { mutableStateOf(Tab.CARTE) }
    val context = LocalContext.current
    val mapViewModel: MapViewModel = viewModel(factory = MapViewModel.Factory)
    val manager = context.appContainer.recordingManager
    val recording by manager.status.collectAsStateWithLifecycle()
    val panel = LocalPanelColors.current
    val isRecording = recording is RecordingStatus.Active
    var showOffline by rememberSaveable { mutableStateOf(false) }
    var selectingArea by rememberSaveable { mutableStateOf(false) }
    var corridorSheet by remember { mutableStateOf<CorridorRequest?>(null) }
    var focusBounds by remember { mutableStateOf<GeoBounds?>(null) }
    var preview by remember { mutableStateOf<RoutePreview?>(null) }
    var importResult by remember { mutableStateOf<ImportOutcome?>(null) }
    val plannerViewModel: PlannerViewModel = viewModel(factory = PlannerViewModel.Factory)
    var savedPlan by remember { mutableStateOf<Pair<Long, String>?>(null) }
    var detailSessionId by rememberSaveable { mutableStateOf<Long?>(null) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var detailFullMap by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val container = context.appContainer
    val navigation = container.navigationManager

    // Fichier GPX ouvert ou partagé depuis une autre application (§9 : « partagé par un ami »).
    LaunchedEffect(incomingGpx) {
        val uri = incomingGpx ?: return@LaunchedEffect
        importResult = GpxImport(context, container.routeRepository).import(uri)
        onIncomingGpxConsumed()
        tab = Tab.PARCOURS
    }

    suspend fun sessionRoute(id: Long): Route? {
        val summary = container.trackRepository.load(id)?.summary ?: return null
        val points = container.recordingManager.segmentsOf(id).flatten().map { RoutePoint(it.latitude, it.longitude) }
        return Route(summary.name, points, source = RouteSource.RECORDED).takeIf { points.size >= 2 }
    }

    fun showPreview(route: Route?) {
        if (route == null) return
        preview = RoutePreview(route.name, route, RouteRepository.length(route.points))
        tab = Tab.CARTE
    }

    fun follow(route: Route?, reversed: Boolean) {
        if (route == null) return
        preview = null
        navigation.follow(route, reversed)
        mapViewModel.onRecenter()
        tab = Tab.CARTE
    }

    // Import GPX depuis la carte : le parcours importé s'affiche aussitôt (aperçu, « Suivre »).
    var importError by remember { mutableStateOf<String?>(null) }
    val gpxPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            when (val result = GpxImport(context, container.routeRepository).import(uri)) {
                is ImportOutcome.Imported -> showPreview(container.routeRepository.load(result.routeId))
                is ImportOutcome.Error -> importError = result.message
            }
        }
    }

    val parcoursActions = remember {
        object : ParcoursActions {
            override fun openSession(id: Long) { detailSessionId = id }
            override fun shareRoute(id: Long) { scope.launch { GpxExport(context, container).shareRoute(id) } }
            override fun previewSession(id: Long) { scope.launch { showPreview(sessionRoute(id)) } }
            override fun previewRoute(id: Long) { scope.launch { showPreview(container.routeRepository.load(id)) } }
            override fun followSession(id: Long) { scope.launch { follow(sessionRoute(id), reversed = false) } }
            override fun followRoute(id: Long, reversed: Boolean) { scope.launch { follow(container.routeRepository.load(id), reversed) } }
            override fun offlineAroundSession(id: Long) { corridorSheet = CorridorRequest(sessionId = id) }
            override fun offlineAroundRoute(id: Long) { corridorSheet = CorridorRequest(routeId = id) }
        }
    }

    // Pendant l'enregistrement, la carte occupe tout l'écran (maquette, écran 2).
    LaunchedEffect(isRecording) { if (isRecording) tab = Tab.CARTE }
    BackHandler(enabled = tab != Tab.CARTE) { tab = Tab.CARTE }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (!isRecording && !selectingArea && !detailFullMap) NavigationBar(containerColor = panel.container.copy(alpha = 1f)) {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = {
                            tab = t
                            detailSessionId = null
                            showSettings = false
                        },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(t.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = TrailColors.Blue,
                            selectedTextColor = TrailColors.Blue,
                            unselectedIconColor = panel.secondary,
                            unselectedTextColor = panel.secondary,
                            indicatorColor = TrailColors.Blue.copy(alpha = 0.15f),
                        ),
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            // La carte reste en mémoire sous les autres onglets : cadrage et suivi conservés.
            CarteScreen(
                viewModel = mapViewModel,
                active = tab == Tab.CARTE || tab == Tab.CREER,
                recording = recording,
                manager = manager,
                selectingArea = selectingArea,
                onAreaDone = {
                    selectingArea = false
                    showOffline = true
                },
                focusBounds = focusBounds,
                onFocusConsumed = { focusBounds = null },
                onOpenOfflineMaps = { showOffline = true },
                onImportGpx = { gpxPicker.launch(arrayOf("application/gpx+xml", "application/octet-stream", "text/xml", "application/xml", "*/*")) },
                preview = preview,
                onPreviewClose = { preview = null },
                onFollowPreview = { reversed -> follow(preview?.route, reversed) },
                planner = plannerViewModel.takeIf { tab == Tab.CREER && !selectingArea },
                onPlanSaved = { id, name -> savedPlan = id to name },
            )
            when (tab) {
                Tab.CARTE, Tab.CREER -> Unit
                Tab.PARCOURS -> ParcoursScreen(parcoursActions, importResult, onImportResultShown = { importResult = null })
                Tab.STATISTIQUES -> StatsScreen(onOpenSettings = { showSettings = true }, onOpenSession = { detailSessionId = it })
            }
            detailSessionId?.let { id ->
                HikeDetailScreen(
                    sessionId = id,
                    onBack = { detailSessionId = null },
                    onFollow = { sid ->
                        detailSessionId = null
                        parcoursActions.followSession(sid)
                    },
                    onOffline = { sid -> corridorSheet = CorridorRequest(sessionId = sid) },
                    onFullscreen = { detailFullMap = it },
                )
            }
            if (showSettings) {
                SettingsScreen(
                    onBack = { showSettings = false },
                    onOpenOfflineMaps = {
                        showSettings = false
                        showOffline = true
                    },
                )
            }
            if (showOffline) {
                OfflineMapsScreen(
                    onBack = { showOffline = false },
                    onSelectArea = {
                        showOffline = false
                        tab = Tab.CARTE
                        selectingArea = true
                    },
                    onAroundHike = { corridorSheet = CorridorRequest() },
                    onShow = { bounds ->
                        showOffline = false
                        tab = Tab.CARTE
                        focusBounds = bounds
                    },
                )
            }
        }
    }

    importError?.let { message ->
        AlertDialog(
            onDismissRequest = { importError = null },
            title = { Text("Importation impossible") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { importError = null }) { Text("OK") } },
        )
    }

    savedPlan?.let { (id, name) ->
        SavedPlanDialog(
            name = name,
            onFollow = {
                savedPlan = null
                scope.launch { follow(container.routeRepository.load(id), reversed = false) }
            },
            onOffline = {
                savedPlan = null
                corridorSheet = CorridorRequest(routeId = id)
            },
            onClose = { savedPlan = null },
        )
    }

    corridorSheet?.let { request ->
        CorridorDownloadSheet(
            initialSessionId = request.sessionId,
            initialRouteId = request.routeId,
            onDismiss = { corridorSheet = null },
            onStarted = {
                corridorSheet = null
                showOffline = true
            },
        )
    }

    when (val status = recording) {
        is RecordingStatus.Interrupted -> {
            LaunchedEffect(status.summary.id) { TrackingNotifications.cancelInterrupted(context) }
            InterruptedDialog(status, onResume = manager::resumeInterrupted, onFinish = manager::finishInterrupted)
        }
        is RecordingStatus.Finished -> FinishedSheet(
            status,
            onRename = { manager.rename(status.summary.id, it) },
            onClose = manager::acknowledgeFinished,
        )
        else -> Unit
    }
}
