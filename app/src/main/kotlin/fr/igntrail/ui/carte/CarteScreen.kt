package fr.igntrail.ui.carte

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import fr.igntrail.appContainer
import fr.igntrail.core.geo.GeoBounds
import fr.igntrail.core.geo.TileMath
import fr.igntrail.core.model.ActivityType
import fr.igntrail.core.model.GeoPoint
import fr.igntrail.core.model.LocationFix
import fr.igntrail.core.model.Route
import fr.igntrail.core.model.RoutePoint
import fr.igntrail.core.model.RouteSource
import fr.igntrail.core.planning.PlannedGeometry
import fr.igntrail.data.storage.Poi
import fr.igntrail.map.CameraSnapshot
import fr.igntrail.map.GpsStatus
import fr.igntrail.map.HeadingSelector
import fr.igntrail.map.IgnLayers
import fr.igntrail.map.IgnMap
import fr.igntrail.map.IgnMapController
import fr.igntrail.map.MapUiState
import fr.igntrail.map.MapViewModel
import fr.igntrail.map.SavedCamera
import fr.igntrail.navigation.GuidanceKind
import fr.igntrail.navigation.NavigationStatus
import fr.igntrail.offline.Availability
import fr.igntrail.offline.OfflineRequest
import fr.igntrail.routing.PlannerViewModel
import fr.igntrail.tracking.RecordingManager
import fr.igntrail.tracking.RecordingStatus
import fr.igntrail.tracking.TrackSnapshot
import fr.igntrail.ui.RoutePreview
import fr.igntrail.ui.components.MapRoundButton
import fr.igntrail.ui.components.NorthIndicator
import fr.igntrail.ui.components.ScaleBarView
import fr.igntrail.ui.components.SpeedLegend
import fr.igntrail.ui.components.StatusChip
import fr.igntrail.ui.components.ZoomButtons
import fr.igntrail.ui.creer.CreatePanel
import fr.igntrail.ui.creer.PlanPointsSheet
import fr.igntrail.ui.creer.SavePlanDialog
import fr.igntrail.ui.navigation.GuidanceCard
import fr.igntrail.ui.navigation.GuidanceOptionsSheet
import fr.igntrail.ui.navigation.RoutePreviewCard
import fr.igntrail.ui.offline.AreaDownloadCard
import fr.igntrail.ui.offline.AreaSelectionFrame
import fr.igntrail.ui.poi.PoiEditorSheet
import fr.igntrail.ui.poi.newPoi
import fr.igntrail.ui.recording.FullscreenStatsBar
import fr.igntrail.ui.recording.RecordingPanel
import fr.igntrail.ui.recording.StartRecordingSheet
import fr.igntrail.ui.recording.StopRecordingDialog
import fr.igntrail.ui.theme.LocalPanelColors
import fr.igntrail.ui.theme.TrailColors
import java.util.concurrent.TimeUnit
import kotlin.math.hypot
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch

/** Vue d'ensemble de la France métropolitaine, avant toute position. */
private val FRANCE = SavedCamera(46.6, 2.4, 5.0)
private const val TRACK_REFRESH_MS = 1_000L

/**
 * Onglet Carte (maquette, écrans 1 à 4).
 * @param active faux quand un autre onglet recouvre la carte : on n'actualise plus l'indicateur.
 */
@Composable
fun CarteScreen(
    viewModel: MapViewModel,
    active: Boolean,
    recording: RecordingStatus,
    manager: RecordingManager,
    selectingArea: Boolean,
    onAreaDone: () -> Unit,
    focusBounds: GeoBounds?,
    onFocusConsumed: () -> Unit,
    onOpenOfflineMaps: () -> Unit,
    onImportGpx: () -> Unit,
    preview: RoutePreview?,
    onPreviewClose: () -> Unit,
    onFollowPreview: (reversed: Boolean) -> Unit,
    /** Onglet Créer : la carte sert à placer les points du parcours (§8). */
    planner: PlannerViewModel?,
    onPlanSaved: (routeId: Long, name: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val display by viewModel.display.collectAsStateWithLifecycle()
    val permission = rememberLocationPermissionState()
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current

    var controller by remember { mutableStateOf<IgnMapController?>(null) }
    var camera by remember { mutableStateOf<CameraSnapshot?>(null) }
    var showLayers by remember { mutableStateOf(false) }
    var showStart by remember { mutableStateOf(false) }
    var showPlanList by remember { mutableStateOf(false) }
    // Points d'intérêt (§12) : tous visibles sur la carte, ouverts par un appui.
    val poiRepository = context.appContainer.poiRepository
    val poisFlow = remember { poiRepository.all() }
    val pois by poisFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    var editingPoi by remember { mutableStateOf<Poi?>(null) }
    var showPlanSave by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val planningScope = rememberCoroutineScope()
    var showGuidanceOptions by remember { mutableStateOf(false) }
    val navigation = context.appContainer.navigationManager
    val guidance by navigation.status.collectAsStateWithLifecycle()
    val guidanceSettings by navigation.settings.collectAsStateWithLifecycle()
    val offlineMaps = context.appContainer.offlineMaps
    val network by context.appContainer.network.state.collectAsStateWithLifecycle()
    val offlineRegions by offlineMaps.regions.collectAsStateWithLifecycle()
    var mapRect by remember { mutableStateOf<Rect?>(null) }
    var frameRect by remember { mutableStateOf<Rect?>(null) }
    var showStop by remember { mutableStateOf(false) }
    var fullscreen by rememberSaveable { mutableStateOf(false) }
    val activeRecording = recording as? RecordingStatus.Active
    if (activeRecording == null) {
        fullscreen = false
        showStop = false
    }

    val granted = permission.access != LocationAccess.NONE
    LaunchedEffect(granted) {
        if (!granted) return@LaunchedEffect
        viewModel.loadApproximatePosition()
        // Positions reçues uniquement quand l'application est visible (Phase 1, §17).
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) { viewModel.collectLocation() }
    }
    LaunchedEffect(controller) {
        val map = controller ?: return@LaunchedEffect
        viewModel.cameraCommands.collect { map.execute(it) }
    }
    LaunchedEffect(controller, display) { controller?.applyDisplay(display) }
    // Boussole active seulement quand la carte est visible (économie d'énergie, §17).
    var compass by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            try {
                context.appContainer.compass.headings().collect { compass = it }
            } finally {
                compass = null
            }
        }
    }
    // Tracé en cours, ou tracé de la randonnée terminée tant que son résumé est affiché.
    val track = when (recording) {
        is RecordingStatus.Active -> recording.track
        is RecordingStatus.Finished -> recording.track
        else -> TrackSnapshot.EMPTY
    }
    // Une seule couleur pendant la sortie ; couleurs de vitesse figées une fois terminée (§7).
    val trackThresholds = (recording as? RecordingStatus.Finished)?.colors
    // Au plus un rafraîchissement du tracé par seconde (2 positions/s à vitesse élevée).
    val latestTrack by rememberUpdatedState(track)
    LaunchedEffect(controller, trackThresholds) {
        val map = controller ?: return@LaunchedEffect
        snapshotFlow { latestTrack }.conflate().collect {
            map.showTrack(it.timed, trackThresholds)
            delay(TRACK_REFRESH_MS)
        }
    }

    LaunchedEffect(controller, pois) { controller?.showPois(pois, null) }
    // Zones déjà téléchargées (§4), toujours visibles pendant le choix d'une zone.
    LaunchedEffect(controller, offlineRegions, display.showOfflineZones, selectingArea) {
        controller?.showOfflineCoverage(offlineMaps.completeTiles(), offlineMaps.partialTiles(), display.showOfflineZones || selectingArea)
    }
    val activeSessionId = (recording as? RecordingStatus.Active)?.session?.id
    /** Point d'intérêt touché (à moins de 28 dp), s'il y en a un. */
    fun poiAt(tapped: GeoPoint): Poi? {
        val map = controller ?: return null
        val tapPx = map.screenPoint(tapped)
        val radius = with(density) { 28.dp.toPx() }
        return pois.minByOrNull { p -> map.screenPoint(p.position).let { hypot(it.x - tapPx.x, it.y - tapPx.y) } }
            ?.takeIf { p -> map.screenPoint(p.position).let { hypot(it.x - tapPx.x, it.y - tapPx.y) } <= radius }
    }

    // Création de parcours (§8) : affichage du tracé prévu et des points.
    val plan = planner?.state?.collectAsStateWithLifecycle()?.value
    LaunchedEffect(controller, plan?.geometry, plan?.points, plan?.selectedId) {
        val map = controller ?: return@LaunchedEffect
        if (plan == null) map.showPlan(PlannedGeometry(emptyList()), emptyList(), null)
        else map.showPlan(plan.geometry, plan.points, plan.selectedId)
    }
    // Déplacement d'un point : la carte se centre dessus, l'utilisateur la fait glisser.
    LaunchedEffect(controller, plan?.movingId) {
        val id = plan?.movingId ?: return@LaunchedEffect
        plan.points.firstOrNull { it.id == id }?.let { controller?.centerOn(it.position) }
    }
    // En création, la carte ne suit plus le GPS : elle resterait sinon recentrée sous les doigts.
    LaunchedEffect(planner != null) { if (planner != null) viewModel.onUserGesture() }
    val onPlanTap: (GeoPoint) -> Boolean = tap@{ tapped ->
        val vm = planner ?: return@tap poiAt(tapped)?.let { editingPoi = it; true } ?: false
        val map = controller ?: return@tap false
        if (plan?.movingId != null) return@tap true
        // Appui sur un point existant (à moins de 28 dp) : sélection ; sinon nouveau point.
        val tapPx = map.screenPoint(tapped)
        val radius = with(density) { 28.dp.toPx() }
        val hit = plan?.points?.minByOrNull { p ->
            val s = map.screenPoint(p.position)
            hypot(s.x - tapPx.x, s.y - tapPx.y)
        }?.takeIf { p ->
            val s = map.screenPoint(p.position)
            hypot(s.x - tapPx.x, s.y - tapPx.y) <= radius
        }
        if (hit != null) vm.select(if (hit.id == plan.selectedId) null else hit.id) else vm.onMapTap(tapped)
        true
    }

    // Itinéraire suivi ou en aperçu ; pointillés vers le tracé (ou vers le départ) quand on s'en écarte.
    val followed = guidance as? NavigationStatus.Following
    val shownRoute = followed?.route ?: preview?.route
    val connector = state.fix?.let { fix ->
        val here = GeoPoint(fix.latitude, fix.longitude)
        when (val g = guidance) {
            is NavigationStatus.Following -> g.state?.takeIf { it.offRoute }?.let { here to it.nearest }
            is NavigationStatus.DirectionToStart -> here to g.start
            NavigationStatus.Idle -> null
        }
    }
    LaunchedEffect(controller, shownRoute, connector) { controller?.showRoute(shownRoute, connector) }

    // Aperçu : la carte se cadre sur l'itinéraire (le suivi GPS est suspendu).
    LaunchedEffect(controller, preview) {
        val r = preview?.route ?: return@LaunchedEffect
        val map = controller ?: return@LaunchedEffect
        val bounds = GeoBounds.of(r.points.map { GeoPoint(it.latitude, it.longitude) }) ?: return@LaunchedEffect
        viewModel.onUserGesture()
        map.showBounds(bounds)
    }

    // « Voir sur la carte » depuis la liste des cartes hors ligne : on quitte le suivi GPS.
    LaunchedEffect(controller, focusBounds) {
        val bounds = focusBounds ?: return@LaunchedEffect
        val map = controller ?: return@LaunchedEffect
        viewModel.onUserGesture()
        map.showBounds(bounds)
        onFocusConsumed()
    }

    // Zone sélectionnée : rectangle du cadre converti en coordonnées géographiques.
    val selectedBounds = remember(controller, frameRect, mapRect, camera, selectingArea) {
        val map = controller
        val frame = frameRect
        val view = mapRect
        if (!selectingArea || map == null || frame == null || view == null) {
            null
        } else {
            map.boundsOf(frame.left - view.left, frame.top - view.top, frame.right - view.left, frame.bottom - view.top)
        }
    }

    // Disponibilité hors ligne : autour de la position (départ) et de la vue (hors connexion).
    val hereAvailability = remember(state.fix?.let { it.latitude to it.longitude }, offlineRegions) {
        state.fix?.let { offlineMaps.availability(TileMath.tilesInBounds(GeoBounds.around(GeoPoint(it.latitude, it.longitude), 2_000.0), OfflineRequest.COVERAGE_ZOOM)) }
    }
    val viewAvailability = remember(camera?.let { (it.latitude * 200).toInt() to (it.longitude * 200).toInt() }, offlineRegions, network.online) {
        val c = camera
        if (network.online || c == null) null else offlineMaps.availability(setOf(TileMath.tileAt(GeoPoint(c.latitude, c.longitude), OfflineRequest.COVERAGE_ZOOM)))
    }

    // Android 13+ : la notification d'enregistrement nécessite une autorisation (facultative).
    var pendingStart by remember { mutableStateOf<Triple<ActivityType, Int, String?>?>(null) }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        pendingStart?.let { (activity, autoPause, label) -> manager.start(activity, autoPause, label) }
        pendingStart = null
    }
    val startRecording: (ActivityType, Int, String?) -> Unit = { activity, autoPause, label ->
        showStart = false
        val needsNotificationPermission = Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsNotificationPermission) {
            pendingStart = Triple(activity, autoPause, label)
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            manager.start(activity, autoPause, label)
        }
    }

    LaunchedEffect(controller, state.fix, compass, active) {
        val fix = state.fix
        if (active && fix != null) controller?.updateLocation(fix, HeadingSelector.select(fix, compass))
    }

    Box(modifier.fillMaxSize()) {
        IgnMap(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { mapRect = it.boundsInRoot() },
            onMapReady = { map ->
                val saved = viewModel.savedCamera
                if (saved != null) viewModel.onMapRecreated()
                val start = saved ?: FRANCE
                map.moveImmediately(start.latitude, start.longitude, start.zoom, start.bearing)
                controller = map
            },
            onUserGesture = viewModel::onUserGesture,
            onCameraMove = { camera = it },
            onCameraIdle = { viewModel.onCameraIdle(SavedCamera(it.latitude, it.longitude, it.zoom, it.bearing)) },
            onMapClick = onPlanTap,
            onMapLongClick = { pos ->
                if (planner == null && !selectingArea) {
                    editingPoi = newPoi(pos, null, activeSessionId)
                    true
                } else {
                    false
                }
            },
        )

        // Cible au centre de la carte pendant le déplacement d'un point.
        if (plan?.movingId != null) {
            Icon(
                Icons.Filled.Add,
                contentDescription = null,
                tint = TrailColors.Blue,
                modifier = Modifier.align(Alignment.Center).size(48.dp),
            )
        }

        // Plein écran : bandeau de statistiques en haut, le reste est décalé dessous.
        val topOffset = if (fullscreen) 84.dp else 8.dp
        if (fullscreen && activeRecording != null) {
            FullscreenStatsBar(
                activeRecording,
                Modifier
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .fillMaxWidth(),
            )
        }

        // Haut gauche : échelle et état du GPS.
        Column(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                // Ne jamais passer sous la colonne de boutons de droite.
                .padding(start = 12.dp, top = topOffset, end = 80.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (guidance !is NavigationStatus.Idle) {
                GuidanceCard(
                    status = guidance,
                    headingDeg = compass,
                    onOptions = { showGuidanceOptions = true },
                    onStop = navigation::stop,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            camera?.let { ScaleBarView(latitude = it.latitude, zoom = it.zoom) }
            if (granted && permission.locationEnabled) GpsChip(state)
            // Légende du tracé coloré (§7) pendant l'enregistrement.
            trackThresholds?.let { SpeedLegend(it, Modifier.width(230.dp)) }
            if (!network.online) {
                StatusChip(
                    text = "Hors connexion · " + when (viewAvailability) {
                        Availability.AVAILABLE -> "carte téléchargée ici"
                        Availability.PARTIAL -> "carte partielle ici"
                        else -> "carte non téléchargée ici"
                    },
                    accent = if (viewAvailability == Availability.AVAILABLE) TrailColors.Green else TrailColors.Orange,
                )
            }
        }

        if (selectingArea) AreaSelectionFrame(onFrame = { frameRect = it })

        // Colonne droite : couches, nord, recentrage, zoom.
        Column(
            Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(end = 12.dp, top = topOffset),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!fullscreen) MapRoundButton(Icons.Filled.Layers, "Fonds de carte", onClick = { showLayers = true })
            if (!fullscreen && planner == null && !selectingArea) {
                MapRoundButton(Icons.Filled.DownloadForOffline, "Cartes hors ligne", onClick = onOpenOfflineMaps)
            }
            // Ajout d'un point d'intérêt à la position actuelle (§12) ; l'appui long fait de même ailleurs.
            if (planner == null && !selectingArea) {
                MapRoundButton(Icons.Filled.AddLocationAlt, "Ajouter un point d'intérêt", onClick = {
                    val fix = state.fix
                    editingPoi = if (fix != null) {
                        newPoi(GeoPoint(fix.latitude, fix.longitude), fix.altitudeM, activeSessionId)
                    } else {
                        controller?.let { newPoi(it.center(), null, activeSessionId) }
                    }
                })
            }
            NorthIndicator(bearing = camera?.bearing ?: 0.0, onClick = { controller?.resetNorth() })
            MapRoundButton(
                icon = if (state.following) Icons.Filled.MyLocation else Icons.Filled.GpsFixed,
                contentDescription = "Recentrer sur ma position",
                onClick = viewModel::onRecenter,
                tint = if (state.following && state.fix != null) TrailColors.Blue else LocalPanelColors.current.content,
            )
            Spacer(Modifier.height(4.dp))
            ZoomButtons(onZoomIn = { controller?.zoomBy(1.0) }, onZoomOut = { controller?.zoomBy(-1.0) })
        }

        // Bas : messages d'autorisation, recentrage, puis démarrage ou panneau d'enregistrement.
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when {
                    !granted -> InfoCard(
                        text = "IGN Trail a besoin de votre position pour l'afficher sur la carte. " +
                            "Elle reste sur votre téléphone.",
                        action = if (permission.permanentlyDenied) "Ouvrir les réglages" else "Autoriser",
                        onAction = permission.request,
                    )
                    !permission.locationEnabled -> InfoCard(
                        text = "La localisation du téléphone est désactivée.",
                        action = "Activer",
                        onAction = { context.openLocationSettings() },
                    )
                    permission.access == LocationAccess.APPROXIMATE -> InfoCard(
                        text = "Seule la position approximative est autorisée : le suivi sera imprécis.",
                        action = "Position exacte",
                        onAction = permission.request,
                    )
                }

                AnimatedVisibility(visible = !state.following && planner == null, enter = fadeIn(), exit = fadeOut()) {
                    Button(
                        onClick = viewModel::onRecenter,
                        colors = ButtonDefaults.buttonColors(containerColor = TrailColors.Blue, contentColor = Color.White),
                        contentPadding = ButtonDefaults.ContentPadding,
                        modifier = Modifier.height(48.dp),
                    ) {
                        Icon(Icons.Filled.GpsFixed, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Revenir au suivi GPS", fontWeight = FontWeight.SemiBold)
                    }
                }

                if (selectingArea || planner != null) {
                    // Sélection de zone et création de parcours : panneaux dédiés plus bas.
                } else if (preview != null) {
                    RoutePreviewCard(preview.name, preview.route, preview.distanceM, onFollow = onFollowPreview, onClose = onPreviewClose)
                } else if (activeRecording == null) {
                    Button(
                        onClick = { if (granted) showStart = true else permission.request() },
                        colors = ButtonDefaults.buttonColors(containerColor = TrailColors.Green, contentColor = Color.White),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("Démarrer ma randonnée", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    }
                } else if (fullscreen) {
                    MapRoundButton(Icons.Filled.ExpandLess, "Quitter le plein écran", onClick = { fullscreen = false })
                }

                Text(
                    IgnLayers.ATTRIBUTION,
                    fontSize = 10.sp,
                    color = Color(0xFF30343A),
                    modifier = Modifier
                        .align(Alignment.Start)
                        .padding(bottom = if (activeRecording != null && !fullscreen) 4.dp else 12.dp),
                )
            }

            if (selectingArea) {
                AreaDownloadCard(bounds = selectedBounds, onCancel = onAreaDone, onStarted = onAreaDone)
            } else if (planner != null && plan != null) {
                CreatePanel(
                    state = plan,
                    vm = planner,
                    onShowList = { showPlanList = true },
                    onSave = { showPlanSave = true },
                    onConfirmMove = { controller?.let { planner.confirmMove(it.center()) } },
                )
            } else if (activeRecording != null && !fullscreen) {
                RecordingPanel(
                    status = activeRecording,
                    onPause = manager::pause,
                    onResume = manager::resume,
                    onStop = { showStop = true },
                    onFullscreen = { fullscreen = true },
                    // Retour au point de départ (§10).
                    onTrackBack = {
                        val points = activeRecording.track.segments.flatten().map { RoutePoint(it.latitude, it.longitude) }
                        if (points.size >= 2) {
                            navigation.follow(Route("Retour sur mes pas", points, source = RouteSource.RECORDED), reversed = true, kind = GuidanceKind.TRACK_BACK)
                        }
                    },
                    onDirectionToStart = {
                        activeRecording.track.segments.firstOrNull()?.firstOrNull()?.let { navigation.directionToStart(it) }
                    },
                )
            }
        }
    }

    if (planner != null && plan != null) {
        if (showPlanList) PlanPointsSheet(plan, planner, onDismiss = { showPlanList = false })
        if (showPlanSave) {
            SavePlanDialog(
                plan,
                planner,
                onSave = { name ->
                    showPlanSave = false
                    planningScope.launch {
                        val id = planner.save(name) ?: return@launch
                        onPlanSaved(id, name.ifBlank { "Mon parcours" })
                        planner.clear()
                    }
                },
                onDismiss = { showPlanSave = false },
            )
        }
    }
    editingPoi?.let { PoiEditorSheet(it, onDone = { editingPoi = null }) }
    if (showGuidanceOptions) {
        GuidanceOptionsSheet(guidanceSettings, onChange = navigation::updateSettings, onDismiss = { showGuidanceOptions = false })
    }
    if (showStart) {
        StartRecordingSheet(
            manager = manager,
            offlineAvailability = hereAvailability,
            onStart = startRecording,
            onDismiss = { showStart = false },
        )
    }
    if (showStop && activeRecording != null) {
        StopRecordingDialog(
            stats = activeRecording.stats,
            onFinish = {
                showStop = false
                manager.stop()
            },
            onDelete = {
                showStop = false
                manager.discard()
            },
            onDismiss = { showStop = false },
        )
    }

    if (showLayers) {
        LayersSheet(
            settings = display,
            onBaseMap = viewModel::setBaseMap,
            onHillshade = viewModel::setHillshade,
            onShowOfflineZones = viewModel::setShowOfflineZones,
            onOpenOfflineMaps = {
                showLayers = false
                onOpenOfflineMaps()
            },
            onImportGpx = {
                showLayers = false
                onImportGpx()
            },
            onDismiss = { showLayers = false },
        )
    }
}

@Composable
private fun GpsChip(state: MapUiState) {
    val fix: LocationFix? = state.fix
    when {
        fix != null -> {
            val accuracy = fix.horizontalAccuracyM
            val good = accuracy != null && accuracy <= 20f
            StatusChip(
                text = if (accuracy != null) "GPS ± ${accuracy.toInt()} m" else "GPS actif",
                accent = if (good) TrailColors.Green else TrailColors.Orange,
            )
        }
        state.gpsStatus == GpsStatus.ERROR -> StatusChip("Localisation indisponible", accent = TrailColors.Red)
        state.approximate != null -> {
            val ageMin = TimeUnit.MILLISECONDS.toMinutes(System.currentTimeMillis() - state.approximate.timeMillis)
            val age = when {
                state.approximate.timeMillis <= 0L -> ""
                ageMin < 60 -> " (il y a $ageMin min)"
                ageMin < 48 * 60 -> " (il y a ${ageMin / 60} h)"
                else -> " (il y a ${ageMin / (24 * 60)} j)"
            }
            StatusChip("Position approximative$age · recherche GPS…", accent = TrailColors.Orange)
        }
        else -> StatusChip("Recherche du signal GPS…", accent = TrailColors.Orange)
    }
}

@Composable
private fun InfoCard(text: String, action: String, onAction: () -> Unit) {
    val panel = LocalPanelColors.current
    Surface(shape = RoundedCornerShape(16.dp), color = panel.container, shadowElevation = 4.dp) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.LocationOff, contentDescription = null, tint = TrailColors.Orange)
            Spacer(Modifier.width(12.dp))
            Text(text, color = panel.content, fontSize = 14.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = onAction) { Text(action, color = TrailColors.Blue, fontWeight = FontWeight.SemiBold) }
        }
    }
}
