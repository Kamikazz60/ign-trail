package fr.igntrail.ui.parcours

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.igntrail.appContainer
import fr.igntrail.core.geo.GeoBounds
import fr.igntrail.core.model.GeoPoint
import fr.igntrail.core.stats.SpeedThresholds
import fr.igntrail.core.stats.TrackProfile
import fr.igntrail.core.tracking.RecordingStats
import fr.igntrail.core.tracking.TimedPoint
import fr.igntrail.data.settings.MapDisplaySettings
import fr.igntrail.data.storage.Poi
import fr.igntrail.data.storage.SessionSummary
import fr.igntrail.export.GpxExport
import fr.igntrail.map.IgnMap
import fr.igntrail.map.IgnMapController
import fr.igntrail.map.OutingColors
import fr.igntrail.ui.components.ElevationChart
import fr.igntrail.ui.components.MapRoundButton
import fr.igntrail.ui.components.SpeedLegend
import fr.igntrail.ui.format.Format
import fr.igntrail.ui.poi.PhotoStrip
import fr.igntrail.ui.poi.PoiEditorSheet
import fr.igntrail.ui.recording.FullStatsGrid
import fr.igntrail.ui.theme.LocalPanelColors
import fr.igntrail.ui.theme.TrailColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

/** Données recalculées à partir des mesures brutes enregistrées (§13). */
private class HikeData(
    val summary: SessionSummary,
    val stats: RecordingStats,
    val segments: List<List<TimedPoint>>,
    val profile: TrackProfile,
    val colors: SpeedThresholds?,
)

/**
 * Fiche d'une randonnée (§13) : carte du parcours colorée selon la vitesse, durées,
 * vitesses, dénivelé, profil altimétrique interactif relié à la carte (§11),
 * points d'intérêt et photos, export GPX (§14).
 */
@Composable
fun HikeDetailScreen(
    sessionId: Long,
    onBack: () -> Unit,
    onFollow: (Long) -> Unit,
    onOffline: (Long) -> Unit,
    /** Carte plein écran ouverte : l'appelant masque la barre d'onglets. */
    onFullscreen: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val container = context.appContainer
    val scope = rememberCoroutineScope()
    BackHandler(onBack = onBack)

    var reload by remember { mutableStateOf(0) }
    val data by produceState<HikeData?>(null, sessionId, reload) {
        val summary = container.trackRepository.load(sessionId)?.summary ?: return@produceState
        val recorder = container.recordingManager.replay(sessionId) ?: return@produceState
        val end = summary.endTime ?: recorder.lastDataMillis
        val segments = recorder.timedSegments()
        val colors = OutingColors.forSession(summary, recorder, end, container.trackRepository)
        value = HikeData(summary, recorder.stats(end), segments, TrackProfile.of(segments), colors)
    }
    val poisFlow = remember(sessionId) { container.poiRepository.forSession(sessionId) }
    val pois by poisFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val photosFlow = remember(sessionId) { container.poiRepository.photosOfSession(sessionId) }
    val photos by photosFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val display by container.mapPreferences.display.collectAsStateWithLifecycle(initialValue = MapDisplaySettings())

    var highlight by remember { mutableStateOf<GeoPoint?>(null) }
    var renaming by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var editingPoi by remember { mutableStateOf<Poi?>(null) }
    var controller by remember { mutableStateOf<IgnMapController?>(null) }
    // Une seule carte à la fois : la mini-carte ou la carte plein écran (même contrôleur).
    var fullMap by remember { mutableStateOf(false) }
    val scroll = rememberScrollState()
    LaunchedEffect(fullMap) { onFullscreen(fullMap) }
    DisposableEffect(Unit) { onDispose { onFullscreen(false) } }

    val d = data
    LaunchedEffect(controller, display) { controller?.applyDisplay(display) }
    LaunchedEffect(controller, d) {
        val map = controller ?: return@LaunchedEffect
        val hike = d ?: return@LaunchedEffect
        map.showTrack(hike.segments, hike.colors)
        GeoBounds.of(hike.segments.flatten().map { it.point })?.let { map.showBounds(it) }
    }
    LaunchedEffect(controller, pois, highlight) { controller?.showPois(pois, highlight) }

    if (fullMap && d != null) {
        BackHandler {
            controller = null
            fullMap = false
        }
        FullTrackMap(
            title = d.summary.name,
            colors = d.colors,
            onReady = { controller = it },
            onRefit = { GeoBounds.of(d.segments.flatten().map { it.point })?.let { b -> controller?.showBounds(b) } },
            onClose = {
                controller = null
                fullMap = false
            },
        )
    } else Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.statusBarsPadding()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour") }
                Text(
                    d?.summary?.name ?: "Randonnée",
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    modifier = Modifier.weight(1f).clickable { renaming = true },
                )
                IconButton(onClick = { renaming = true }) { Icon(Icons.Filled.Edit, contentDescription = "Renommer") }
                IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, contentDescription = "Supprimer", tint = TrailColors.Red) }
            }
            if (d == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                return@Column
            }
            Column(Modifier.verticalScroll(scroll).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val date = SimpleDateFormat("EEEE d MMMM yyyy, HH:mm", Locale.FRANCE).format(Date(d.summary.startTime))
                Text("${d.summary.activityName} · $date", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Box(Modifier.fillMaxWidth().height(260.dp).clip(RoundedCornerShape(16.dp))) {
                    IgnMap(
                        modifier = Modifier.fillMaxSize(),
                        onMapReady = { controller = it },
                        onUserGesture = {},
                        onCameraMove = {},
                        onCameraIdle = {},
                    )
                    // Aperçu : les gestes iraient en conflit avec le défilement de la fiche ;
                    // un appui ouvre la carte en plein écran.
                    Box(
                        Modifier.matchParentSize().clickable {
                            controller = null
                            fullMap = true
                        },
                    )
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = LocalPanelColors.current.container,
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                    ) {
                        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Fullscreen, contentDescription = null, tint = LocalPanelColors.current.content, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Agrandir", color = LocalPanelColors.current.content, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                d.colors?.let { SpeedLegend(it, Modifier.fillMaxWidth()) }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onFollow(sessionId) }) {
                        Icon(Icons.Filled.Navigation, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Suivre")
                    }
                    OutlinedButton(onClick = { scope.launch { GpxExport(context, container).shareSession(sessionId) } }) {
                        Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Partager (GPX)")
                    }
                    OutlinedButton(onClick = { onOffline(sessionId) }) {
                        Icon(Icons.Filled.DownloadForOffline, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Carte hors ligne")
                    }
                }
                Surface(color = LocalPanelColors.current.container, shape = MaterialTheme.shapes.large) {
                    FullStatsGrid(d.stats, Modifier.padding(8.dp), live = false)
                }
                Text("Profil altimétrique", style = MaterialTheme.typography.titleMedium)
                val profile = d.profile.profile
                if (profile != null) {
                    Surface(color = LocalPanelColors.current.container, shape = MaterialTheme.shapes.large) {
                        Column(Modifier.padding(12.dp)) {
                            ElevationChart(profile, Modifier.fillMaxWidth(), onSelect = { dist -> highlight = dist?.let { d.profile.pointAt(it) } })
                            Text(
                                (if (d.profile.barometric) "Altitude barométrique" else "Altitude GPS lissée") +
                                    " · touchez la courbe pour situer le point sur la carte",
                                style = MaterialTheme.typography.bodySmall,
                                color = LocalPanelColors.current.secondary,
                            )
                        }
                    }
                } else {
                    Text("Altitudes insuffisantes pour tracer le profil.", style = MaterialTheme.typography.bodyMedium)
                }
                if (pois.isNotEmpty()) {
                    Text("Points d'intérêt", style = MaterialTheme.typography.titleMedium)
                    pois.forEach { p -> PoiRow(p) { editingPoi = p } }
                }
                if (photos.isNotEmpty()) {
                    Text("Photos", style = MaterialTheme.typography.titleMedium)
                    PhotoStrip(photos)
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (renaming && d != null) {
        var name by remember { mutableStateOf(d.summary.name) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("Renommer") },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    renaming = false
                    scope.launch {
                        container.trackRepository.rename(sessionId, name.trim().ifEmpty { d.summary.name })
                        reload++
                    }
                }) { Text("Enregistrer") }
            },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("Annuler") } },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Supprimer cette sortie ?") },
            text = { Text("La trace et ses photos seront définitivement supprimées. Les points d'intérêt sont conservés.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        container.poiRepository.deleteSessionPhotos(sessionId)
                        container.trackRepository.delete(sessionId)
                        onBack()
                    }
                }) { Text("Supprimer", color = TrailColors.Red) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annuler") } },
        )
    }
    editingPoi?.let { PoiEditorSheet(it, onDone = { editingPoi = null }) }
}

/** Carte du parcours en plein écran : déplacement et zoom libres, légende sous la carte. */
@Composable
private fun FullTrackMap(
    title: String,
    colors: SpeedThresholds?,
    onReady: (IgnMapController) -> Unit,
    onRefit: () -> Unit,
    onClose: () -> Unit,
) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().navigationBarsPadding()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                IgnMap(
                    modifier = Modifier.fillMaxSize(),
                    onMapReady = onReady,
                    onUserGesture = {},
                    onCameraMove = {},
                    onCameraIdle = {},
                )
                Row(
                    Modifier.statusBarsPadding().padding(12.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    MapRoundButton(Icons.Filled.Close, "Fermer la carte", onClick = onClose)
                    Surface(shape = RoundedCornerShape(16.dp), color = LocalPanelColors.current.container, modifier = Modifier.weight(1f)) {
                        Text(
                            title,
                            color = LocalPanelColors.current.content,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                    MapRoundButton(Icons.Filled.CenterFocusStrong, "Recadrer sur le parcours", onClick = onRefit)
                }
            }
            colors?.let { SpeedLegend(it, Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) }
        }
    }
}

@Composable
fun PoiRow(p: Poi, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(14.dp).clip(RoundedCornerShape(7.dp)).padding(0.dp)) {
            Surface(color = Color(android.graphics.Color.parseColor(p.category.color)), modifier = Modifier.fillMaxSize()) {}
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(p.name, fontWeight = FontWeight.SemiBold)
            Text(
                p.category.label + (p.altitudeM?.let { " · ${Format.meters(it)}" } ?: "") + (if (p.description.isNotBlank()) " · ${p.description}" else ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
        }
    }
    HorizontalDivider()
}
