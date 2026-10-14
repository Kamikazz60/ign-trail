package fr.igntrail.ui.recording

import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Hiking
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.getSystemService
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.igntrail.appContainer
import fr.igntrail.core.model.ActivityType
import fr.igntrail.core.tracking.TrackingParams
import fr.igntrail.offline.Availability
import fr.igntrail.tracking.RecordingManager
import fr.igntrail.ui.offline.AvailabilityLine
import fr.igntrail.ui.theme.TrailColors

private fun ActivityType.icon(): ImageVector = when (this) {
    ActivityType.WALK -> Icons.AutoMirrored.Filled.DirectionsWalk
    ActivityType.HIKE -> Icons.Filled.Hiking
    ActivityType.RUN -> Icons.AutoMirrored.Filled.DirectionsRun
    ActivityType.OTHER -> Icons.Filled.Explore
}

private fun autoPauseLabel(seconds: Int) = when {
    seconds == 0 -> "Désactivée"
    seconds < 60 -> "$seconds s"
    else -> "${seconds / 60} min"
}

/** Choix de l'activité avant le départ (§5.1) ; le dernier profil est présélectionné. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StartRecordingSheet(
    manager: RecordingManager,
    offlineAvailability: Availability?,
    onStart: (ActivityType, Int, String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var activity by remember { mutableStateOf<ActivityType?>(null) }
    var autoPause by remember { mutableStateOf<Int?>(null) }
    // « Autre » : nom libre (ski, roller…), les noms déjà utilisés sont proposés.
    var label by remember { mutableStateOf("") }
    val labelsFlow = remember { context.appContainer.trackRepository.activityLabels() }
    val knownLabels by labelsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    LaunchedEffect(Unit) {
        val last = manager.defaultActivity()
        activity = last
        autoPause = manager.autoPauseSeconds(last)
        label = manager.lastActivityLabel().orEmpty()
    }
    LaunchedEffect(activity) {
        activity?.let { autoPause = manager.autoPauseSeconds(it) }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding()) {
            Text("Quelle activité ?", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))
            // Grille 2 × 2 : libellés lisibles même sur un petit écran.
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ActivityType.entries.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        pair.forEach { type ->
                            ActivityCard(type, selected = activity == type, Modifier.weight(1f)) { activity = type }
                        }
                    }
                }
            }
            if (activity == ActivityType.OTHER) {
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it.take(30) },
                    singleLine = true,
                    label = { Text("Nom de l'activité") },
                    placeholder = { Text("Ski, roller, trottinette…") },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (knownLabels.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        knownLabels.forEach { known ->
                            FilterChip(selected = label.trim().equals(known, ignoreCase = true), onClick = { label = known }, label = { Text(known) })
                        }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            Text("Pause automatique à l'arrêt", style = MaterialTheme.typography.titleSmall)
            Text(
                "Le temps de déplacement s'arrête si vous restez immobile pendant :",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TrackingParams.AUTO_PAUSE_CHOICES_S.forEach { s ->
                    FilterChip(selected = autoPause == s, onClick = { autoPause = s }, label = { Text(autoPauseLabel(s), fontSize = 12.sp) })
                }
            }
            // Avant le départ : la carte est-elle disponible sans réseau autour de vous ? (§4)
            if (offlineAvailability != null) {
                Spacer(Modifier.height(12.dp))
                AvailabilityLine(offlineAvailability)
            }
            if (!context.isIgnoringBatteryOptimizations()) {
                Spacer(Modifier.height(12.dp))
                BatteryHint(context)
            }
            Spacer(Modifier.height(20.dp))
            val ready = activity != null && autoPause != null
            Button(
                onClick = { onStart(activity!!, autoPause!!, label.trim().takeIf { activity == ActivityType.OTHER && it.isNotEmpty() }) },
                enabled = ready,
                colors = ButtonDefaults.buttonColors(containerColor = TrailColors.Green, contentColor = Color.White),
                shape = RoundedCornerShape(50),
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(10.dp))
                Text("Démarrer", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ActivityCard(type: ActivityType, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val color = if (selected) TrailColors.Blue else MaterialTheme.colorScheme.outlineVariant
    Surface(
        onClick = onClick,
        modifier = modifier.height(76.dp),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(if (selected) 2.dp else 1.dp, color),
        color = if (selected) TrailColors.Blue.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surface,
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(type.icon(), contentDescription = null, tint = if (selected) TrailColors.Blue else MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(30.dp))
            Spacer(Modifier.width(12.dp))
            Text(type.label, textAlign = TextAlign.Start, fontSize = 15.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
        }
    }
}

/** Les économiseurs de batterie de certains constructeurs coupent l'enregistrement (§17). */
@Composable
private fun BatteryHint(context: Context) {
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(Modifier.padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.BatteryAlert, contentDescription = null, tint = TrailColors.Orange)
            Spacer(Modifier.width(10.dp))
            Text(
                "Pour un enregistrement fiable écran éteint, retirez IGN Trail de l'optimisation de batterie.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = {
                context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }) { Text("Réglages") }
        }
    }
}

private fun Context.isIgnoringBatteryOptimizations(): Boolean =
    getSystemService<PowerManager>()?.isIgnoringBatteryOptimizations(packageName) ?: true
