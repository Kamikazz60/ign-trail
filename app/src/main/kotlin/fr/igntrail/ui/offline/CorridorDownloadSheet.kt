package fr.igntrail.ui.offline

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.igntrail.appContainer
import fr.igntrail.core.geo.Corridor
import fr.igntrail.core.model.GeoPoint
import fr.igntrail.offline.Availability
import fr.igntrail.offline.DetailLevel
import fr.igntrail.offline.OfflineKind
import fr.igntrail.offline.OfflineRequest
import fr.igntrail.ui.format.Format
import fr.igntrail.ui.theme.TrailColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val MARGINS_KM = listOf(1, 2, 5)

/**
 * Téléchargement autour d'un parcours (§4) : une randonnée enregistrée, et à partir de
 * la Phase 4 les parcours importés ou créés. Marge par défaut : 2 km.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CorridorDownloadSheet(initialSessionId: Long?, initialRouteId: Long?, onDismiss: () -> Unit, onStarted: () -> Unit) {
    val context = LocalContext.current
    val container = context.appContainer
    val sessionsFlow = remember { container.trackRepository.finishedSessions() }
    val sessionsOnly by sessionsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val routesFlow = remember { container.routeRepository.routes() }
    val routes by routesFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    // Randonnées enregistrées et itinéraires (importés, puis créés en Phase 5) dans une même liste.
    val sessions = remember(sessionsOnly, routes) {
        routes.map { CorridorSource(-it.id, it.name, it.distanceM, routeId = it.id) } +
            sessionsOnly.map { CorridorSource(it.id, it.name, it.distanceM, routeId = null) }
    }
    val network by container.network.state.collectAsStateWithLifecycle()
    val regions by container.offlineMaps.regions.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var sessionId by remember { mutableStateOf(initialRouteId?.let { -it } ?: initialSessionId) }
    var marginKm by remember { mutableStateOf(2) }
    var detail by remember { mutableStateOf(DetailLevel.DETAILED) }
    var request by remember { mutableStateOf<OfflineRequest?>(null) }
    var computing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val freeBytes = remember { context.filesDir.usableSpace }

    LaunchedEffect(sessions) { if (sessionId == null) sessionId = sessions.firstOrNull()?.id }
    // La liste des randonnées arrive après la première composition : on recalcule à son arrivée.
    LaunchedEffect(sessionId, marginKm, detail, sessions.isNotEmpty()) {
        val id = sessionId ?: return@LaunchedEffect
        val summary = sessions.firstOrNull { it.id == id } ?: return@LaunchedEffect
        computing = true
        request = withContext(Dispatchers.Default) {
            val segments = if (summary.routeId != null) {
                listOfNotNull(container.routeRepository.load(summary.routeId)?.points?.map { GeoPoint(it.latitude, it.longitude) })
            } else {
                container.recordingManager.segmentsOf(id)
            }
            val quads = Corridor.build(segments, marginKm * 1000.0)
            val bounds = Corridor.bounds(quads) ?: return@withContext null
            OfflineRequest(
                name = "Autour de « ${summary.name} »",
                kind = OfflineKind.CORRIDOR,
                detail = detail,
                bounds = bounds,
                corridor = quads,
                sessionId = id.takeIf { it > 0 },
            ).also { it.estimate }
        }
        computing = false
    }
    val availability = remember(request, regions) {
        request?.let { container.offlineMaps.availability(it.coverageTiles()) }
    }
    val already = remember(request, regions) { request?.let { container.offlineMaps.coverageRatio(it.coverageTiles()) } ?: 0.0 }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Carte autour d'un parcours", style = MaterialTheme.typography.titleLarge)
            if (sessions.isEmpty()) {
                Text(
                    "Aucun parcours pour l'instant : enregistrez une randonnée ou importez un fichier GPX (onglet Parcours).",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(24.dp))
                return@ModalBottomSheet
            }
            SectionTitle("Parcours")
            LazyColumn(Modifier.heightIn(max = 220.dp)) {
                items(sessions, key = { it.id }) { s ->
                    Row(
                        Modifier.fillMaxWidth().clickable { sessionId = s.id }.padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = sessionId == s.id, onClick = { sessionId = s.id })
                        Column {
                            Text(s.name, style = MaterialTheme.typography.bodyLarge)
                            Text(Format.distance(s.distanceM), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            SectionTitle("Marge autour du tracé")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MARGINS_KM.forEach { km ->
                    FilterChip(selected = marginKm == km, onClick = { marginKm = km }, label = { Text("$km km") })
                }
            }
            SectionTitle("Niveau de détail")
            DetailChips(detail) { detail = it }
            Spacer(Modifier.height(4.dp))
            EstimateText(if (computing) null else request?.estimate, network, freeBytes, alreadyRatio = already)
            AvailabilityLine(availability)
            error?.let { Text(it, color = TrailColors.Red) }
            val req = request
            Button(
                onClick = {
                    if (req == null) return@Button
                    scope.launch {
                        error = container.offlineMaps.download(req)
                        if (error == null) onStarted()
                    }
                },
                enabled = req != null && !computing && !req.estimate.blocking(freeBytes) && availability != Availability.AVAILABLE,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { DownloadButtonContent() }
            Spacer(Modifier.height(12.dp))
        }
    }
}

/** Élément de la liste : randonnée (id positif) ou itinéraire (id négatif, [routeId] renseigné). */
private data class CorridorSource(val id: Long, val name: String, val distanceM: Double, val routeId: Long?)
