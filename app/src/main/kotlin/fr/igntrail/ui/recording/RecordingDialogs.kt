package fr.igntrail.ui.recording

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.igntrail.core.tracking.RecordingStats
import fr.igntrail.tracking.RecordingStatus
import fr.igntrail.ui.format.Format
import fr.igntrail.ui.theme.LocalPanelColors
import fr.igntrail.ui.theme.TrailColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Confirmation avant de terminer ou de supprimer la randonnée active (§15.3). */
@Composable
fun StopRecordingDialog(stats: RecordingStats, onFinish: () -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    var confirmDelete by remember { mutableStateOf(false) }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Supprimer la sortie ?") },
            text = { Text("La trace de ${Format.distance(stats.distanceM)} sera définitivement perdue.") },
            confirmButton = {
                TextButton(onClick = onDelete) { Text("Supprimer", color = TrailColors.Red, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annuler") } },
        )
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Terminer la sortie ?") },
        text = {
            Column {
                Text("${Format.distance(stats.distanceM)} en ${Format.duration(stats.totalMs)}, D+ ${Format.meters(stats.elevationGainM)}.")
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = { confirmDelete = true }, contentPadding = ButtonDefaults.TextButtonContentPadding) {
                    Text("Supprimer sans enregistrer…", color = TrailColors.Red)
                }
            }
        },
        confirmButton = {
            Button(onClick = onFinish, colors = ButtonDefaults.buttonColors(containerColor = TrailColors.Red, contentColor = Color.White)) {
                Text("Terminer et enregistrer")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Continuer") } },
    )
}

/** Session non terminée retrouvée au démarrage (§5.4, test H). */
@Composable
fun InterruptedDialog(status: RecordingStatus.Interrupted, onResume: () -> Unit, onFinish: () -> Unit) {
    val last = SimpleDateFormat("EEEE d MMMM 'à' HH:mm", Locale.FRANCE).format(Date(status.lastDataMillis))
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Randonnée interrompue") },
        text = {
            Text(
                "L'enregistrement de « ${status.summary.name} » s'est arrêté de façon inattendue " +
                    "(dernière donnée $last, ${Format.distance(status.summary.distanceM)} environ).\n\n" +
                    "La trace déjà enregistrée est conservée. Si vous reprenez, la coupure sera indiquée " +
                    "et ne sera pas reliée par une ligne droite.",
            )
        },
        confirmButton = {
            Button(onClick = onResume, colors = ButtonDefaults.buttonColors(containerColor = TrailColors.Green, contentColor = Color.White)) {
                Text("Reprendre l'enregistrement")
            }
        },
        dismissButton = { TextButton(onClick = onFinish) { Text("Terminer") } },
    )
}

/** Résumé après la fin de la randonnée (maquette, écran 8 — version Phase 2). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinishedSheet(status: RecordingStatus.Finished, onRename: (String) -> Unit, onClose: () -> Unit) {
    val panel = LocalPanelColors.current
    var name by remember(status.summary.id) { mutableStateOf(status.summary.name) }
    val close = {
        if (name.isNotBlank() && name != status.summary.name) onRename(name.trim())
        onClose()
    }
    ModalBottomSheet(onDismissRequest = close, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState()),
        ) {
            Text("Sortie enregistrée", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                "${status.summary.activityName} · ${SimpleDateFormat("d MMMM yyyy, HH:mm", Locale.FRANCE).format(Date(status.summary.startTime))}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nom") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            Surface(color = panel.container, shape = MaterialTheme.shapes.large) {
                FullStatsGrid(status.stats, Modifier.padding(8.dp), live = false)
            }
            Spacer(Modifier.height(16.dp))
            Button(onClick = close, modifier = Modifier.fillMaxWidth()) { Text("Fermer") }
            Spacer(Modifier.height(16.dp))
        }
    }
}
