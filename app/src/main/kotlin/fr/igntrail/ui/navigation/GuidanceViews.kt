package fr.igntrail.ui.navigation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.igntrail.core.model.Route
import fr.igntrail.data.settings.GuidanceSettings
import fr.igntrail.navigation.GuidanceKind
import fr.igntrail.navigation.NavigationStatus
import fr.igntrail.ui.format.Format
import fr.igntrail.ui.theme.LocalPanelColors
import fr.igntrail.ui.theme.TrailColors

private val GuidanceViolet = Color(0xFF7B3FE4)

/**
 * Bandeau de guidage (§9, §10) : flèche directionnelle, distance restante le long
 * du parcours, distance au tracé et alerte de sortie de parcours.
 *
 * @param headingDeg orientation du téléphone (boussole) ; la flèche indique alors la
 * direction à prendre par rapport à soi. Sans boussole, elle est orientée nord en haut,
 * comme la carte.
 */
@Composable
fun GuidanceCard(
    status: NavigationStatus,
    headingDeg: Float?,
    onOptions: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val panel = LocalPanelColors.current
    val (title, line1, line2, bearing, alert) = when (status) {
        is NavigationStatus.Following -> {
            val s = status.state
            val label = when {
                status.kind == GuidanceKind.TRACK_BACK -> "Retour sur mes pas"
                status.reversed -> "Sens inverse · ${status.name}"
                else -> status.name
            }
            when {
                s == null -> Row5(label, "Recherche de la position…", null, null, false)
                s.arrived -> Row5(label, "Arrivée !", "${Format.distance(s.totalM)} parcourus", null, false)
                s.offRoute -> Row5(label, "Hors parcours", "Tracé à ${Format.distance(s.crossTrackM)} · suivez la flèche", s.targetBearingDeg, true)
                else -> Row5(label, "Reste ${Format.distance(s.remainingM)}", "Sur le parcours · à ${Format.distance(s.crossTrackM)} du tracé", s.targetBearingDeg, false)
            }
        }
        is NavigationStatus.DirectionToStart -> {
            val d = status.direction
            when {
                status.arrived -> Row5("Direction du départ", "Point de départ atteint", null, null, false)
                d == null -> Row5("Direction du départ", "Recherche de la position…", null, null, false)
                else -> Row5(
                    "Direction du départ",
                    "${Format.distance(d.distanceM)} · cap ${d.bearingDeg.toInt()}°",
                    "À vol d'oiseau : ni relief ni obstacles pris en compte.",
                    d.bearingDeg,
                    false,
                )
            }
        }
        NavigationStatus.Idle -> return
    }
    val progress = (status as? NavigationStatus.Following)?.state?.progress

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = if (alert) Color(0xF2B3261E) else panel.container,
        shadowElevation = 4.dp,
    ) {
        Column(Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (bearing != null) {
                    // Flèche relative à l'orientation du téléphone si la boussole est disponible.
                    val rotation = (bearing - (headingDeg ?: 0f)).toFloat()
                    GuidanceArrow(rotation, if (alert) Color.White else GuidanceViolet)
                    Spacer(Modifier.width(10.dp))
                }
                val textColor = if (alert) Color.White else panel.content
                Column(Modifier.weight(1f)) {
                    Text(title, color = if (alert) Color.White else panel.secondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(line1, color = textColor, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    if (line2 != null) Text(line2, color = textColor, fontSize = 12.sp)
                }
                IconButton(onClick = onOptions) { Icon(Icons.Filled.Tune, contentDescription = "Réglages du guidage", tint = if (alert) Color.White else panel.content) }
                IconButton(onClick = onStop) { Icon(Icons.Filled.Close, contentDescription = "Arrêter le guidage", tint = if (alert) Color.White else panel.content) }
            }
            if (progress != null) {
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { progress.toFloat() },
                    color = GuidanceViolet,
                    modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
                )
            }
        }
    }
}

private data class Row5(val title: String, val line1: String, val line2: String?, val bearing: Double?, val alert: Boolean)

@Composable
private fun GuidanceArrow(rotationDeg: Float, color: Color) {
    Canvas(Modifier.size(52.dp).rotate(rotationDeg)) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w / 2, h * 0.05f)
            lineTo(w * 0.85f, h * 0.9f)
            lineTo(w / 2, h * 0.7f)
            lineTo(w * 0.15f, h * 0.9f)
            close()
        }
        drawPath(path, color)
    }
}

/** Réglages du guidage (§9, §16) : tolérance de sortie, vibration, son. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuidanceOptionsSheet(settings: GuidanceSettings, onChange: (GuidanceSettings) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Réglages du guidage", style = MaterialTheme.typography.titleLarge)
            Text("Alerte de sortie de parcours au-delà de :", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                GuidanceSettings.TOLERANCE_CHOICES_M.forEach { m ->
                    FilterChip(selected = settings.toleranceM == m, onClick = { onChange(settings.copy(toleranceM = m)) }, label = { Text("$m m") })
                }
            }
            Text(
                "Une tolérance faible prévient plus tôt mais peut se déclencher sous les arbres ou dans les gorges, où le GPS est moins précis.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SwitchRow("Vibration", settings.vibrate) { onChange(settings.copy(vibrate = it)) }
            SwitchRow("Son", settings.sound) { onChange(settings.copy(sound = it)) }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** Aperçu d'un itinéraire sur la carte avant de le suivre. */
@Composable
fun RoutePreviewCard(
    name: String,
    route: Route,
    distanceM: Double,
    onFollow: (reversed: Boolean) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val panel = LocalPanelColors.current
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp), color = panel.container) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(name, color = panel.content, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${Format.distance(distanceM)} · ${route.points.size} points" +
                            if (route.waypoints.isNotEmpty()) " · ${route.waypoints.size} repères" else "",
                        color = panel.secondary,
                        fontSize = 13.sp,
                    )
                }
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Fermer l'aperçu", tint = panel.content) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { onFollow(true) }, modifier = Modifier.weight(1f).height(48.dp)) {
                    Text("Sens inverse", color = panel.content)
                }
                Button(onClick = { onFollow(false) }, modifier = Modifier.weight(1.3f).height(48.dp)) { Text("Suivre ce parcours") }
            }
        }
    }
}
