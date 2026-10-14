package fr.igntrail.ui.recording

import androidx.compose.animation.AnimatedVisibility
import fr.igntrail.data.settings.SpeedUnit
import fr.igntrail.data.settings.DisplaySettings
import fr.igntrail.appContainer
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalContext
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.igntrail.core.tracking.RecordingState
import fr.igntrail.tracking.RecordingStatus
import fr.igntrail.ui.format.Format
import fr.igntrail.ui.theme.LocalPanelColors
import fr.igntrail.ui.theme.TrailColors

/**
 * Panneau de la randonnée en cours (maquette, écrans 2 et 5) :
 * compact par défaut (distance, durée, vitesse, D+), déployable pour tout voir.
 */
@Composable
fun RecordingPanel(
    status: RecordingStatus.Active,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onFullscreen: () -> Unit,
    onTrackBack: () -> Unit,
    onDirectionToStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val panel = LocalPanelColors.current
    var expanded by rememberSaveable { mutableStateOf(false) }
    val stats = status.stats.at(rememberTicker())
    val (dist, distUnit) = Format.distanceParts(stats.distanceM)
    val (speedLabel, speedValue, speedUnit) = speedTile(status.session.activity, stats, rememberSpeedUnit())

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
        color = panel.container,
        shadowElevation = 8.dp,
    ) {
        Column(Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
            // Poignée : un appui déploie ou replie le panneau.
            Box(
                Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button) { expanded = !expanded }
                    .semantics { contentDescription = if (expanded) "Réduire les statistiques" else "Afficher toutes les statistiques" }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.width(40.dp).height(4.dp).background(panel.secondary, RoundedCornerShape(2.dp)))
            }
            StateBanner(stats.state, stats.gpsSignalOk)
            Row(Modifier.fillMaxWidth()) {
                StatTile("Distance", dist, distUnit, Modifier.weight(1f))
                StatTile("Durée", Format.duration(stats.totalMs), "", Modifier.weight(1.1f))
                StatTile(speedLabel, speedValue, speedUnit, Modifier.weight(1f))
                StatTile("D+", Format.meters(stats.elevationGainM).removeSuffix(" m"), "m", Modifier.weight(0.8f))
            }
            AnimatedVisibility(visible = expanded, enter = expandVertically(), exit = shrinkVertically()) {
                Column {
                    HorizontalDivider(color = panel.divider, modifier = Modifier.padding(vertical = 6.dp))
                    // Retour au point de départ (§10).
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onTrackBack, modifier = Modifier.weight(1f)) {
                            Text("Retour sur mes pas", color = panel.content, fontSize = 13.sp)
                        }
                        OutlinedButton(onClick = onDirectionToStart, modifier = Modifier.weight(1f)) {
                            Text("Direction du départ", color = panel.content, fontSize = 13.sp)
                        }
                    }
                    FullStatsGrid(
                        stats,
                        Modifier
                            .heightIn(max = 380.dp)
                            .verticalScroll(rememberScrollState()),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RoundAction(Icons.Filled.Fullscreen, "Plein écran", panel.content, Color.Transparent, 52.dp, onFullscreen)
                if (stats.state == RecordingState.MANUAL_PAUSED) {
                    RoundAction(Icons.Filled.PlayArrow, "Reprendre", Color.White, TrailColors.Green, 68.dp, onResume)
                } else {
                    RoundAction(Icons.Filled.Pause, "Pause", Color.White, TrailColors.Orange, 68.dp, onPause)
                }
                RoundAction(Icons.Filled.Stop, "Terminer", Color.White, TrailColors.Red, 68.dp, onStop)
            }
        }
    }
}

@Composable
private fun StateBanner(state: RecordingState, signalOk: Boolean) {
    val (text, color) = when {
        state == RecordingState.MANUAL_PAUSED -> "En pause — appuyez sur Reprendre pour continuer" to TrailColors.Orange
        state == RecordingState.AUTO_PAUSED -> "Pause automatique — reprise dès que vous repartez" to TrailColors.Orange
        !signalOk -> "Signal GPS faible — enregistrement maintenu" to TrailColors.Red
        else -> return
    }
    Surface(shape = RoundedCornerShape(10.dp), color = color.copy(alpha = 0.18f), modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        Text(text, color = color, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
    }
}

@Composable
private fun RoundAction(icon: ImageVector, label: String, tint: Color, background: Color, size: Dp, onClick: () -> Unit) {
    val panel = LocalPanelColors.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = if (background == Color.Transparent) panel.divider else background,
            modifier = Modifier.size(size),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(size * 0.5f))
            }
        }
        Text(label, color = panel.secondary, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
    }
}

/** Mode plein écran (maquette, écran 4) : bandeau minimal en haut. */
@Composable
fun FullscreenStatsBar(status: RecordingStatus.Active, modifier: Modifier = Modifier) {
    val panel = LocalPanelColors.current
    val stats = status.stats.at(rememberTicker())
    val (dist, distUnit) = Format.distanceParts(stats.distanceM)
    val (speedLabel, speedValue, speedUnit) = speedTile(status.session.activity, stats, rememberSpeedUnit())
    Surface(modifier = modifier, shape = RoundedCornerShape(16.dp), color = panel.container, shadowElevation = 4.dp) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 2.dp)) {
            StatTile("Distance", dist, distUnit, Modifier.weight(1f), valueSize = 20)
            StatTile("Durée", Format.duration(stats.totalMs), "", Modifier.weight(1.1f), valueSize = 20)
            StatTile(speedLabel, speedValue, speedUnit, Modifier.weight(1f), valueSize = 20)
        }
    }
}

@Composable
private fun rememberSpeedUnit(): SpeedUnit {
    val prefs = LocalContext.current.appContainer.displayPreferences
    val settings by prefs.settings.collectAsStateWithLifecycle(initialValue = DisplaySettings())
    return settings.speedUnit
}
