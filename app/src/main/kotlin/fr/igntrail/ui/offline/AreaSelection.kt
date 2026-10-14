package fr.igntrail.ui.offline

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.igntrail.appContainer
import fr.igntrail.core.geo.GeoBounds
import fr.igntrail.offline.Availability
import fr.igntrail.offline.DetailLevel
import fr.igntrail.offline.OfflineKind
import fr.igntrail.offline.OfflineRequest
import fr.igntrail.ui.theme.LocalPanelColors
import fr.igntrail.ui.theme.TrailColors
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Cadre de sélection d'une zone libre : l'utilisateur déplace et zoome la carte
 * pour placer la zone dans le cadre (§4, « sélection d'une zone sur la carte »).
 * [onFrame] reçoit la position du cadre à l'écran.
 */
@Composable
fun AreaSelectionFrame(onFrame: (Rect) -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 330.dp)
            .border(3.dp, TrailColors.Blue, RoundedCornerShape(12.dp))
            .onGloballyPositioned { onFrame(it.boundsInRoot()) },
    )
}

/** Panneau du bas pendant la sélection : nom, détail, estimation, Télécharger. */
@Composable
fun AreaDownloadCard(bounds: GeoBounds?, onCancel: () -> Unit, onStarted: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val container = context.appContainer
    val panel = LocalPanelColors.current
    val network by container.network.state.collectAsStateWithLifecycle()
    val regions by container.offlineMaps.regions.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("Zone du ${SimpleDateFormat("d MMMM", Locale.FRANCE).format(Date())}") }
    var detail by remember { mutableStateOf(DetailLevel.DETAILED) }
    var error by remember { mutableStateOf<String?>(null) }
    val freeBytes = remember { context.filesDir.usableSpace }

    val request = remember(bounds, detail, name) {
        bounds?.let { OfflineRequest(name.ifBlank { "Zone" }, OfflineKind.AREA, detail, it) }
    }
    val availability = remember(request, regions) { request?.let { container.offlineMaps.availability(it.coverageTiles()) } }
    val already = remember(request, regions) { request?.let { container.offlineMaps.coverageRatio(it.coverageTiles()) } ?: 0.0 }

    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp), color = panel.container) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Placez la zone dans le cadre bleu", color = panel.content, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text("Nom") },
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = panel.content, unfocusedTextColor = panel.content),
                modifier = Modifier.fillMaxWidth(),
            )
            DetailChipsOnPanel(detail) { detail = it }
            EstimateText(request?.estimate, network, freeBytes, color = panel.content, alreadyRatio = already)
            AvailabilityLine(availability, color = panel.content)
            error?.let { Text(it, color = TrailColors.Red) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f).height(48.dp)) { Text("Annuler", color = panel.content) }
                Button(
                    onClick = {
                        val req = request ?: return@Button
                        scope.launch {
                            error = container.offlineMaps.download(req)
                            if (error == null) onStarted()
                        }
                    },
                    enabled = request != null && !request.estimate.blocking(freeBytes) && availability != Availability.AVAILABLE,
                    modifier = Modifier.weight(1.4f).height(48.dp),
                ) { DownloadButtonContent() }
            }
        }
    }
}

@Composable
private fun DetailChipsOnPanel(selected: DetailLevel, onSelect: (DetailLevel) -> Unit) {
    androidx.compose.material3.ProvideTextStyle(androidx.compose.ui.text.TextStyle(color = LocalPanelColors.current.content)) {
        DetailChips(selected, onSelect)
    }
}
