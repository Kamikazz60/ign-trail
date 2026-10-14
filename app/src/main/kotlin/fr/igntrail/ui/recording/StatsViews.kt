package fr.igntrail.ui.recording

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.igntrail.core.model.ActivityType
import fr.igntrail.core.tracking.RecordingStats
import fr.igntrail.data.settings.SpeedUnit
import fr.igntrail.ui.format.Format
import fr.igntrail.ui.theme.LocalPanelColors
import kotlinx.coroutines.delay

/** Heure courante rafraîchie chaque seconde, uniquement tant que l'écran est composé. */
@Composable
fun rememberTicker(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000L - System.currentTimeMillis() % 1_000L)
            now = System.currentTimeMillis()
        }
    }
    return now
}

/** Une statistique : libellé discret, grande valeur, unité plus petite. */
@Composable
fun StatTile(label: String, value: String, unit: String = "", modifier: Modifier = Modifier, valueSize: Int = 24) {
    val panel = LocalPanelColors.current
    Column(modifier.padding(horizontal = 6.dp, vertical = 6.dp)) {
        Text(label, color = panel.secondary, fontSize = 12.sp, maxLines = 1)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, color = panel.content, fontSize = valueSize.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            if (unit.isNotEmpty()) {
                Spacer(Modifier.width(3.dp))
                Text(unit, color = panel.content, fontSize = (valueSize * 0.6).toInt().sp, modifier = Modifier.padding(bottom = 2.dp))
            }
        }
    }
}

/** Vitesse ou allure selon le réglage (§16) ; en automatique, l'allure est la référence en course à pied. */
fun speedTile(activity: ActivityType, stats: RecordingStats, unit: SpeedUnit = SpeedUnit.AUTO): Triple<String, String, String> =
    if (unit == SpeedUnit.PACE || (unit == SpeedUnit.AUTO && activity == ActivityType.RUN)) {
        Triple("Allure", Format.paceFromSpeed(stats.currentSpeedMps), "/km")
    } else {
        Triple("Vitesse", Format.speedKmh(stats.currentSpeedMps), "km/h")
    }

/** Toutes les statistiques (§5.2), en grille de deux colonnes (maquette, écran 5). */
@Composable
fun FullStatsGrid(stats: RecordingStats, modifier: Modifier = Modifier, live: Boolean = true) {
    val panel = LocalPanelColors.current
    val (dist, distUnit) = Format.distanceParts(stats.distanceM)
    val rows = buildList {
        add(Triple("Distance", dist, distUnit) to Triple("Durée totale", Format.duration(stats.totalMs), ""))
        add(Triple("Temps de déplacement", Format.duration(stats.movingMs), "") to Triple("Temps de pause", Format.duration(stats.pausedMs), ""))
        if (stats.interruptedMs > 0) {
            add(Triple("Interruption", Format.duration(stats.interruptedMs), "") to null)
        }
        if (live) {
            add(Triple("Vitesse actuelle", Format.speedKmh(stats.currentSpeedMps), "km/h") to Triple("Allure actuelle", Format.paceFromSpeed(stats.currentSpeedMps), "/km"))
        }
        add(Triple("Vitesse moyenne", Format.speedKmh(stats.avgMovingSpeedMps), "km/h") to Triple("Vitesse max", Format.speedKmh(stats.maxSpeedMps), "km/h"))
        add(Triple("Moyenne totale", Format.speedKmh(stats.avgTotalSpeedMps), "km/h") to Triple("Allure moyenne", Format.pace(stats.paceSecondsPerKm), "/km"))
        add(Triple("Dénivelé +", Format.meters(stats.elevationGainM).removeSuffix(" m"), "m") to Triple("Dénivelé -", Format.meters(stats.elevationLossM).removeSuffix(" m"), "m"))
        if (live) add(Triple("Altitude actuelle", altitude(stats.currentAltitudeM), "m") to null)
        add(Triple("Altitude min", altitude(stats.minAltitudeM), "m") to Triple("Altitude max", altitude(stats.maxAltitudeM), "m"))
    }
    Column(modifier) {
        rows.forEachIndexed { index, (left, right) ->
            if (index > 0) HorizontalDivider(color = panel.divider)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(left.first, left.second, left.third, Modifier.weight(1f), valueSize = 22)
                if (right != null) {
                    StatTile(right.first, right.second, right.third, Modifier.weight(1f), valueSize = 22)
                } else {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
        if (stats.altitudeSamples == 0) {
            Text(
                "Altitude indisponible : le GPS ne la fournit pas encore.",
                color = Color(0xFFF5A623),
                fontSize = 12.sp,
                modifier = Modifier.padding(6.dp),
            )
        }
    }
}

private fun altitude(value: Double?): String = value?.let { "%.0f".format(it) } ?: "–"
