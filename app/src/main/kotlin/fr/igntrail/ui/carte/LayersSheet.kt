package fr.igntrail.ui.carte

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fr.igntrail.data.settings.BaseMap
import fr.igntrail.data.settings.MapDisplaySettings
import fr.igntrail.map.IgnLayers

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LayersSheet(
    settings: MapDisplaySettings,
    onBaseMap: (BaseMap) -> Unit,
    onHillshade: (Boolean) -> Unit,
    onShowOfflineZones: (Boolean) -> Unit,
    onOpenOfflineMaps: () -> Unit,
    onImportGpx: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding()) {
            Text("Fond de carte", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.padding(4.dp))
            BaseMap.entries.forEach { map ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onBaseMap(map) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = settings.baseMap == map, onClick = { onBaseMap(map) })
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(IgnLayers.label(map))
                        if (map != BaseMap.PLAN_IGN_CLASSIQUE) {
                            Text(
                                "Nécessite Internet (non téléchargeable)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            val hillshadeAvailable = settings.baseMap != BaseMap.ORTHOPHOTOS
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Ombrage du relief")
                    Text(
                        if (hillshadeAvailable) "Estompage IGN (RGE ALTI)" else "Non disponible sur les photographies",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = settings.hillshade && hillshadeAvailable,
                    enabled = hillshadeAvailable,
                    onCheckedChange = onHillshade,
                )
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Zones téléchargées")
                    Text(
                        "Vert : disponible hors ligne · orange : incomplet",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = settings.showOfflineZones, onCheckedChange = onShowOfflineZones)
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            FilledTonalButton(onClick = onOpenOfflineMaps, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.DownloadForOffline, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Cartes hors ligne…")
            }
            FilledTonalButton(onClick = onImportGpx, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.FileOpen, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Importer un parcours (GPX)…")
            }
            Text(
                IgnLayers.ATTRIBUTION,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 16.dp),
            )
        }
    }
}
