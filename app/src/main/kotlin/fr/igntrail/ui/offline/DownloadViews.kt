package fr.igntrail.ui.offline

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.igntrail.offline.Availability
import fr.igntrail.offline.DetailLevel
import fr.igntrail.offline.NetworkState
import fr.igntrail.offline.OfflineEstimate
import fr.igntrail.offline.OfflineRequest
import fr.igntrail.ui.format.Format
import fr.igntrail.ui.theme.TrailColors

@Composable
fun DetailChips(selected: DetailLevel, onSelect: (DetailLevel) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        DetailLevel.entries.forEach { level ->
            FilterChip(selected = selected == level, onClick = { onSelect(level) }, label = { Text(level.label) })
        }
    }
    Text(selected.hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Estimation de la taille et avertissements avant téléchargement (§4). */
@Composable
fun EstimateText(
    estimate: OfflineEstimate?,
    network: NetworkState,
    freeBytes: Long,
    color: Color = MaterialTheme.colorScheme.onSurface,
    /** Part déjà téléchargée : seul le reste sera téléchargé (MapLibre réutilise les tuiles présentes). */
    alreadyRatio: Double = 0.0,
) {
    if (estimate == null) {
        Text("Calcul de la taille…", color = color, style = MaterialTheme.typography.bodyMedium)
        return
    }
    val toDownload = (estimate.bytes * (1.0 - alreadyRatio)).toLong()
    Text(
        "≈ ${Format.bytes(toDownload)} à télécharger · %.0f km²".format(estimate.areaKm2),
        color = color,
        style = MaterialTheme.typography.bodyMedium,
    )
    if (alreadyRatio > 0.005) {
        Text(
            "Déjà disponible : ${(alreadyRatio * 100).toInt()} % — seule la partie manquante sera téléchargée.",
            color = color,
            style = MaterialTheme.typography.bodySmall,
        )
    }
    val warning = when {
        estimate.tiles > OfflineRequest.MAX_TILES -> "Zone trop grande : réduisez-la ou baissez le niveau de détail."
        estimate.bytes * 12 / 10 > freeBytes -> "Espace insuffisant sur le téléphone."
        !network.online -> "Pas de connexion : le téléchargement démarrera au retour du réseau."
        network.metered && estimate.bytes > 50L * 1024 * 1024 -> "Connexion mobile : ${Format.bytes(estimate.bytes)} de données seront utilisées."
        estimate.bytes > OfflineRequest.WARN_BYTES -> "Téléchargement volumineux : comptez plusieurs minutes."
        else -> null
    }
    if (warning != null) Text(warning, color = TrailColors.Orange, style = MaterialTheme.typography.bodySmall)
}

fun OfflineEstimate.blocking(freeBytes: Long): Boolean = tiles > OfflineRequest.MAX_TILES || bytes * 12 / 10 > freeBytes

/** Pastille « disponible / partielle / indisponible hors ligne » (§4). */
@Composable
fun AvailabilityLine(availability: Availability?, modifier: Modifier = Modifier, color: Color? = null) {
    if (availability == null) return
    val (icon, tint) = when (availability) {
        Availability.AVAILABLE -> Icons.Filled.CheckCircle to TrailColors.Green
        Availability.PARTIAL -> Icons.Filled.CloudQueue to TrailColors.Orange
        Availability.UNAVAILABLE -> Icons.Filled.CloudOff to (color ?: MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(availability.label, color = color ?: MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
    }
}

@Composable
fun DownloadButtonContent() {
    Icon(Icons.Filled.CloudDownload, contentDescription = null, modifier = Modifier.size(20.dp))
    Spacer(Modifier.width(8.dp))
    Text("Télécharger")
}

@Composable
fun SectionTitle(text: String) {
    Column {
        Spacer(Modifier.size(4.dp))
        Text(text, style = MaterialTheme.typography.titleSmall)
    }
}
