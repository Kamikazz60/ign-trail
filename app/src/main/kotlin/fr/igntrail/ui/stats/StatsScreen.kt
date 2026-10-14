package fr.igntrail.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.igntrail.appContainer
import fr.igntrail.core.model.ActivityType
import fr.igntrail.core.stats.Dashboard
import fr.igntrail.core.stats.MonthTotals
import fr.igntrail.core.stats.OutingSummary
import fr.igntrail.core.stats.Totals
import fr.igntrail.ui.format.Format
import java.time.format.TextStyle
import java.util.Locale

private enum class Metric(val label: String) { DISTANCE("Distance"), GAIN("Dénivelé +"), DURATION("Durée") }

/**
 * Tableau de bord (§13) : cumuls, évolution sur 12 mois et records, par activité.
 * Tout est recalculé à partir des sorties enregistrées.
 */
@Composable
fun StatsScreen(onOpenSettings: () -> Unit, onOpenSession: (Long) -> Unit) {
    val container = LocalContext.current.appContainer
    val flow = remember { container.trackRepository.finishedSessions() }
    val sessions by flow.collectAsStateWithLifecycle(initialValue = emptyList())
    var activity by rememberSaveable { mutableStateOf<ActivityType?>(null) }
    // Activités personnalisées (« Autre » nommée : ski, roller…), filtrables séparément.
    var label by rememberSaveable { mutableStateOf<String?>(null) }
    val labels = remember(sessions) { sessions.mapNotNull { it.activityLabel }.distinct() }
    var metric by rememberSaveable { mutableStateOf(Metric.DISTANCE) }

    val outings = remember(sessions, label) {
        sessions.filter { label == null || it.activityLabel == label }
            .map { OutingSummary(it.startTime, it.activity, it.distanceM, it.movingMs, it.movingMs + it.pausedMs + it.interruptedMs, it.elevationGainM) }
    }
    val filtered = Dashboard.filter(outings, activity)
    val totals = Totals.of(filtered)
    val months = remember(filtered) { Dashboard.monthly(filtered, 12, System.currentTimeMillis()) }
    val records = Dashboard.records(filtered)
    fun idOf(o: OutingSummary?) = o?.let { r -> sessions.firstOrNull { it.startTime == r.startMillis }?.id }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
                Text("Statistiques", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                IconButton(onClick = onOpenSettings) { Icon(Icons.Filled.Settings, contentDescription = "Paramètres") }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = activity == null && label == null, onClick = { activity = null; label = null }, label = { Text("Toutes") })
                ActivityType.entries.forEach { a ->
                    FilterChip(selected = activity == a && label == null, onClick = { activity = a; label = null }, label = { Text(a.label) })
                }
                labels.forEach { l ->
                    FilterChip(selected = label == l, onClick = { activity = null; label = l }, label = { Text(l) })
                }
            }
            if (filtered.isEmpty()) {
                Text("Aucune sortie enregistrée" + ((label ?: activity?.label)?.let { " en ${it.lowercase()}" } ?: "") + ".", style = MaterialTheme.typography.bodyLarge)
                return@Column
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TotalCard("Sorties", "${totals.count}", Modifier.weight(1f))
                TotalCard("Distance", Format.distance(totals.distanceM), Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TotalCard("En mouvement", hours(totals.movingMs), Modifier.weight(1f))
                TotalCard("Dénivelé +", Format.meters(totals.gainM), Modifier.weight(1f))
            }
            TotalCard("Vitesse moyenne en mouvement", "${Format.speedKmh(totals.avgMovingSpeedMps)} km/h", Modifier.fillMaxWidth())

            Text("Évolution sur 12 mois", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Metric.entries.forEach { m -> FilterChip(selected = metric == m, onClick = { metric = m }, label = { Text(m.label) }) }
            }
            MonthlyBars(months, metric)
            SpeedTrend(months)

            Text("Records", style = MaterialTheme.typography.titleMedium)
            RecordRow("Plus longue", records.longest?.let { Format.distance(it.distanceM) }) { idOf(records.longest)?.let(onOpenSession) }
            RecordRow("Plus grand dénivelé", records.highestGain?.let { Format.meters(it.gainM) }) { idOf(records.highestGain)?.let(onOpenSession) }
            RecordRow("Plus rapide (> 3 km)", records.fastest?.let { "${Format.speedKmh(it.distanceM / (it.movingMs / 1000.0))} km/h" }) {
                idOf(records.fastest)?.let(onOpenSession)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun hours(ms: Long): String {
    val h = ms / 3_600_000
    val m = (ms / 60_000) % 60
    return "$h h %02d".format(m)
}

@Composable
private fun TotalCard(label: String, value: String, modifier: Modifier) {
    Card(modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun RecordRow(label: String, value: String?, onClick: () -> Unit) {
    if (value == null) return
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp)) {
            Text(label, modifier = Modifier.weight(1f))
            Text(value, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Histogramme mensuel de la mesure choisie. */
@Composable
private fun MonthlyBars(months: List<MonthTotals>, metric: Metric) {
    val values = months.map {
        when (metric) {
            Metric.DISTANCE -> it.totals.distanceM / 1000.0
            Metric.GAIN -> it.totals.gainM
            Metric.DURATION -> it.totals.movingMs / 3_600_000.0
        }
    }
    val max = values.maxOrNull()?.takeIf { it > 0 } ?: 1.0
    val unit = when (metric) {
        Metric.DISTANCE -> "km"
        Metric.GAIN -> "m"
        Metric.DURATION -> "h"
    }
    val color = MaterialTheme.colorScheme.primary
    Card {
        Column(Modifier.padding(12.dp)) {
            Text("Maximum : ${"%.0f".format(max)} $unit par mois", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Canvas(Modifier.fillMaxWidth().height(120.dp)) {
                val slot = size.width / values.size
                values.forEachIndexed { i, v ->
                    val h = (v / max * size.height).toFloat()
                    drawRoundRect(color, Offset(i * slot + slot * 0.15f, size.height - h), Size(slot * 0.7f, h), CornerRadius(4f, 4f))
                }
            }
            Row {
                months.forEach { m ->
                    Text(
                        m.month.month.getDisplayName(TextStyle.NARROW, Locale.FRANCE),
                        fontSize = 10.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** Évolution de la vitesse moyenne en mouvement (mois avec sorties). */
@Composable
private fun SpeedTrend(months: List<MonthTotals>) {
    val points = months.mapIndexedNotNull { i, m -> if (m.totals.movingMs > 0) i to m.totals.avgMovingSpeedMps * 3.6 else null }
    if (points.size < 2) return
    val min = points.minOf { it.second } - 0.5
    val max = points.maxOf { it.second } + 0.5
    val color = Color(0xFF1E6FE8)
    Card {
        Column(Modifier.padding(12.dp)) {
            Text(
                "Vitesse moyenne en mouvement : ${"%.1f".format(Locale.FRANCE, points.first().second)} → ${"%.1f".format(Locale.FRANCE, points.last().second)} km/h",
                style = MaterialTheme.typography.labelMedium,
            )
            Box(Modifier.fillMaxWidth().height(70.dp)) {
                Canvas(Modifier.fillMaxSize()) {
                    val slot = size.width / months.size
                    fun at(p: Pair<Int, Double>) = Offset(p.first * slot + slot / 2, (size.height - (p.second - min) / (max - min) * size.height).toFloat())
                    points.zipWithNext { a, b -> drawLine(color, at(a), at(b), strokeWidth = 3.dp.toPx()) }
                    points.forEach { drawCircle(color, 4.dp.toPx(), at(it)) }
                }
            }
        }
    }
}
