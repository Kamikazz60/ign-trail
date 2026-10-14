package fr.igntrail.ui.parcours

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.igntrail.appContainer
import fr.igntrail.data.storage.Poi
import fr.igntrail.data.storage.RouteSummary
import fr.igntrail.data.storage.SessionSummary
import fr.igntrail.navigation.GpxImport
import fr.igntrail.navigation.ImportOutcome
import fr.igntrail.ui.format.Format
import fr.igntrail.ui.poi.PoiEditorSheet
import fr.igntrail.ui.theme.TrailColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

/** Actions proposées depuis la liste : aperçu, guidage, carte hors ligne. */
interface ParcoursActions {
    fun openSession(id: Long)
    fun previewSession(id: Long)
    fun previewRoute(id: Long)
    fun followSession(id: Long)
    fun followRoute(id: Long, reversed: Boolean)
    fun offlineAroundSession(id: Long)
    fun offlineAroundRoute(id: Long)
    fun shareRoute(id: Long)
}

/** Tri de l'historique (§13). */
enum class SessionSort(val label: String) {
    DATE("Date"),
    DISTANCE("Distance"),
    GAIN("Dénivelé"),
    DURATION("Durée"),
}

/**
 * Onglet Parcours : randonnées enregistrées et itinéraires (importés, puis créés en Phase 5).
 * Fiches détaillées, tri et recherche : Phase 6.
 */
@Composable
fun ParcoursScreen(actions: ParcoursActions, importResult: ImportOutcome?, onImportResultShown: () -> Unit) {
    val context = LocalContext.current
    val container = context.appContainer
    val sessionsFlow = remember { container.trackRepository.finishedSessions() }
    val routesFlow = remember { container.routeRepository.routes() }
    val sessions by sessionsFlow.collectAsStateWithLifecycle(initialValue = null)
    val routes by routesFlow.collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableStateOf(0) }
    var outcome by remember { mutableStateOf<ImportOutcome?>(null) }
    var toDelete by remember { mutableStateOf<RouteSummary?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf(SessionSort.DATE) }
    val poisFlow = remember { container.poiRepository.all() }
    val pois by poisFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    var editingPoi by remember { mutableStateOf<Poi?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch { outcome = GpxImport(context, container.routeRepository).import(uri) }
    }
    val shown = outcome ?: importResult

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.statusBarsPadding()) {
                Text("Mes parcours", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    listOf("Sorties", "Itinéraires", "Points").forEachIndexed { i, label ->
                        SegmentedButton(selected = tab == i, onClick = { tab = i }, shape = SegmentedButtonDefaults.itemShape(i, 3)) { Text(label) }
                    }
                }
                if (tab == 0) {
                    val all = sessions
                    when {
                        all == null -> Unit
                        all.isEmpty() -> EmptyState("Aucune randonnée enregistrée.\nAppuyez sur « Démarrer ma randonnée » depuis la carte.")
                        else -> {
                            // Recherche par nom et tri (§13).
                            val list = all.filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
                                .let { l ->
                                    when (sort) {
                                        SessionSort.DATE -> l.sortedByDescending { it.startTime }
                                        SessionSort.DISTANCE -> l.sortedByDescending { it.distanceM }
                                        SessionSort.GAIN -> l.sortedByDescending { it.elevationGainM }
                                        SessionSort.DURATION -> l.sortedByDescending { it.movingMs + it.pausedMs + it.interruptedMs }
                                    }
                                }
                            Column(Modifier.padding(horizontal = 16.dp).padding(top = 10.dp)) {
                                OutlinedTextField(
                                    value = query,
                                    onValueChange = { query = it },
                                    singleLine = true,
                                    placeholder = { Text("Rechercher par nom") },
                                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    SessionSort.entries.forEach { s ->
                                        FilterChip(selected = sort == s, onClick = { sort = s }, label = { Text(s.label) })
                                    }
                                }
                            }
                            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(list, key = { it.id }) { SessionCard(it, actions) }
                                if (list.isEmpty()) item { Text("Aucune randonnée ne correspond à « $query ».") }
                            }
                        }
                    }
                } else if (tab == 2) {
                    if (pois.isEmpty()) {
                        EmptyState("Aucun point d'intérêt.\nAppui long sur la carte, ou bouton « Ajouter un point » pendant une sortie.")
                    } else {
                        LazyColumn(contentPadding = PaddingValues(16.dp)) {
                            items(pois, key = { it.id }) { p -> PoiRow(p) { editingPoi = p } }
                        }
                    }
                } else {
                    val list = routes
                    when {
                        list == null -> Unit
                        list.isEmpty() -> EmptyState("Aucun itinéraire.\nImportez un fichier GPX, ou ouvrez-en un depuis une autre application.")
                        else -> LazyColumn(
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(list, key = { it.id }) { RouteCard(it, actions, onDelete = { toDelete = it }) }
                        }
                    }
                }
            }
            if (tab == 1) {
                ExtendedFloatingActionButton(
                    onClick = { picker.launch(arrayOf("application/gpx+xml", "application/octet-stream", "text/xml", "application/xml", "*/*")) },
                    icon = { Icon(Icons.Filled.FileOpen, contentDescription = null) },
                    text = { Text("Importer un GPX") },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                )
            }
        }
    }

    shown?.let { result ->
        val close = {
            outcome = null
            onImportResultShown()
        }
        when (result) {
            is ImportOutcome.Imported -> AlertDialog(
                onDismissRequest = close,
                title = { Text("Itinéraire importé") },
                text = { Text("« ${result.name} » est disponible dans vos itinéraires." + result.warnings.joinToString("") { "\n• $it" }) },
                confirmButton = {
                    TextButton(onClick = {
                        close()
                        tab = 1
                        actions.previewRoute(result.routeId)
                    }) { Text("Voir sur la carte") }
                },
                dismissButton = { TextButton(onClick = close) { Text("Fermer") } },
            )
            is ImportOutcome.Error -> AlertDialog(
                onDismissRequest = close,
                title = { Text("Importation impossible") },
                text = { Text(result.message) },
                confirmButton = { TextButton(onClick = close) { Text("OK") } },
            )
        }
    }

    editingPoi?.let { PoiEditorSheet(it, onDone = { editingPoi = null }) }

    toDelete?.let { route ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Supprimer « ${route.name} » ?") },
            text = { Text("L'itinéraire sera retiré de l'application.") },
            confirmButton = {
                TextButton(onClick = {
                    toDelete = null
                    scope.launch { container.routeRepository.delete(route.id) }
                }) { Text("Supprimer", color = TrailColors.Red) }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Annuler") } },
        )
    }
}

@Composable
private fun SessionCard(s: SessionSummary, actions: ParcoursActions) {
    val date = SimpleDateFormat("EEE d MMM yyyy, HH:mm", Locale.FRANCE).format(Date(s.startTime))
    Card(onClick = { actions.openSession(s.id) }, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text(s.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("${s.activityName} · $date", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Metric("Distance", Format.distance(s.distanceM))
                Metric("Durée", Format.duration(s.movingMs + s.pausedMs + s.interruptedMs))
                Metric("Déplacement", Format.duration(s.movingMs))
                Metric("D+", Format.meters(s.elevationGainM))
            }
            FlowRow {
                Action(Icons.Outlined.Info, "Fiche") { actions.openSession(s.id) }
                Action(Icons.Outlined.Map, "Voir") { actions.previewSession(s.id) }
                Action(Icons.Filled.Navigation, "Suivre") { actions.followSession(s.id) }
                Action(Icons.Filled.DownloadForOffline, "Carte hors ligne") { actions.offlineAroundSession(s.id) }
            }
        }
    }
}

@Composable
private fun RouteCard(r: RouteSummary, actions: ParcoursActions, onDelete: () -> Unit) {
    val date = SimpleDateFormat("d MMM yyyy", Locale.FRANCE).format(Date(r.createdTime))
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(r.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("${r.source.label} · $date", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Supprimer") }
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Metric("Distance", Format.distance(r.distanceM))
                // Valeurs prévisionnelles issues du fichier (§8.3 : distinguées des mesures).
                Metric("D+ prévu", r.elevationGainM?.let { Format.meters(it) } ?: "–")
                Metric("D- prévu", r.elevationLossM?.let { Format.meters(it) } ?: "–")
            }
            FlowRow {
                Action(Icons.Outlined.Map, "Voir") { actions.previewRoute(r.id) }
                Action(Icons.Filled.Navigation, "Suivre") { actions.followRoute(r.id, reversed = false) }
                Action(Icons.Filled.Navigation, "Sens inverse") { actions.followRoute(r.id, reversed = true) }
                Action(Icons.Filled.DownloadForOffline, "Carte hors ligne") { actions.offlineAroundRoute(r.id) }
                Action(Icons.Filled.Share, "Partager") { actions.shareRoute(r.id) }
            }
        }
    }
}

@Composable
private fun Action(icon: ImageVector, label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 8.dp)) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label)
    }
}

@Composable
private fun Metric(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun EmptyState(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Outlined.Folder, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(12.dp))
            Text(text, textAlign = TextAlign.Center, modifier = Modifier.width(300.dp))
        }
    }
}
