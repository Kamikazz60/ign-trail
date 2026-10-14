package fr.igntrail.ui.offline

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.igntrail.appContainer
import fr.igntrail.core.geo.GeoBounds
import fr.igntrail.offline.DownloadState
import fr.igntrail.offline.OfflineKind
import fr.igntrail.offline.OfflineRegionInfo
import fr.igntrail.ui.format.Format
import fr.igntrail.ui.theme.TrailColors
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Gestion des cartes hors ligne (§4, §16) : zones téléchargées, état,
 * espace occupé, reprise, suppression.
 */
@Composable
fun OfflineMapsScreen(
    onBack: () -> Unit,
    onSelectArea: () -> Unit,
    onAroundHike: () -> Unit,
    onShow: (GeoBounds) -> Unit,
) {
    val context = LocalContext.current
    val container = context.appContainer
    val offline = container.offlineMaps
    val regions by offline.regions.collectAsStateWithLifecycle()
    val network by container.network.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var toDelete by remember { mutableStateOf<OfflineRegionInfo?>(null) }
    val freeBytes = remember(regions) { context.filesDir.usableSpace }

    LaunchedEffect(Unit) { offline.refresh() }
    BackHandler(onBack = onBack)

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.statusBarsPadding()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour") }
                Text("Cartes hors ligne", style = MaterialTheme.typography.titleLarge)
            }
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Text(
                        "Téléchargez la carte IGN (style classique, avec le relief) avant de partir : " +
                            "elle reste consultable sans réseau. L'enregistrement GPS fonctionne de toute façon sans carte.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Utilisé : ${Format.bytes(regions.sumOf { it.sizeBytes })} · Libre sur le téléphone : ${Format.bytes(freeBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (!network.online) {
                        Text("Hors connexion : téléchargements en attente du réseau.", color = TrailColors.Orange, style = MaterialTheme.typography.bodySmall)
                    } else if (network.metered) {
                        Text("Connexion mobile : préférez le Wi-Fi pour les grandes zones.", color = TrailColors.Orange, style = MaterialTheme.typography.bodySmall)
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FilledTonalButton(onClick = onSelectArea, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.CropFree, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Une zone")
                        }
                        FilledTonalButton(onClick = onAroundHike, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.Route, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Autour d'un parcours")
                        }
                    }
                }
                if (regions.isEmpty()) {
                    item {
                        Text(
                            "Aucune carte téléchargée.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 24.dp),
                        )
                    }
                }
                items(regions, key = { it.id }) { region ->
                    RegionCard(
                        region,
                        onPause = { offline.pause(region.id) },
                        onResume = { offline.resume(region.id) },
                        onShow = { onShow(region.bounds) },
                        onDelete = { toDelete = region },
                    )
                }
                item {
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    TextButton(onClick = { scope.launch { offline.clearTemporaryCache() } }) {
                        Text("Vider le cache temporaire (cartes vues en ligne)")
                    }
                    Text(
                        "Les zones téléchargées ne sont jamais effacées automatiquement. © IGN – Licence Ouverte Etalab 2.0.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    toDelete?.let { region ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Supprimer « ${region.name} » ?") },
            text = { Text("${Format.bytes(region.sizeBytes)} seront libérés. Cette zone ne sera plus disponible hors ligne.") },
            confirmButton = {
                TextButton(onClick = {
                    toDelete = null
                    scope.launch { offline.delete(region.id) }
                }) { Text("Supprimer", color = TrailColors.Red) }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Annuler") } },
        )
    }
}

@Composable
private fun RegionCard(
    region: OfflineRegionInfo,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onShow: () -> Unit,
    onDelete: () -> Unit,
) {
    val date = SimpleDateFormat("d MMM yyyy", Locale.FRANCE).format(Date(region.createdMillis))
    val kind = if (region.kind == OfflineKind.AREA) "Zone" else "Autour d'un parcours"
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(region.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "$kind · ${region.detail.label} · %.0f km² · $date".format(region.bounds.areaKm2),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onShow) { Icon(Icons.Outlined.Map, contentDescription = "Voir sur la carte") }
                IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Supprimer") }
            }
            Spacer(Modifier.height(6.dp))
            when (val state = region.state) {
                DownloadState.Complete -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = TrailColors.Green, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Disponible hors ligne · ${Format.bytes(region.sizeBytes)}", color = TrailColors.Green)
                }
                is DownloadState.Downloading -> ProgressRow(state.progress, "Téléchargement", region, Icons.Filled.Pause, "Pause", onPause)
                is DownloadState.Incomplete -> ProgressRow(state.progress, "Incomplète", region, Icons.Filled.PlayArrow, "Reprendre", onResume)
                is DownloadState.Failed -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = TrailColors.Red, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Erreur : ${state.message}", color = TrailColors.Red, modifier = Modifier.weight(1f))
                    TextButton(onClick = onResume) { Text("Réessayer") }
                }
            }
        }
    }
}

@Composable
private fun ProgressRow(
    progress: Float,
    label: String,
    region: OfflineRegionInfo,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    action: String,
    onAction: () -> Unit,
) {
    Column {
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "$label · ${(progress * 100).toInt()} % · ${Format.bytes(region.sizeBytes)}",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onAction) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(action)
            }
        }
    }
}
