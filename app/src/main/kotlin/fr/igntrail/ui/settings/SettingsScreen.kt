package fr.igntrail.ui.settings

import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.igntrail.appContainer
import fr.igntrail.backup.BackupManager
import fr.igntrail.data.settings.DisplaySettings
import fr.igntrail.data.settings.GuidanceSettings
import fr.igntrail.data.settings.SpeedUnit
import fr.igntrail.diagnostic.CrashLog
import fr.igntrail.tracking.RecordingStatus
import fr.igntrail.ui.format.Format
import fr.igntrail.ui.theme.TrailColors
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.system.exitProcess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Paramètres (§16) et confidentialité (§18). */
@Composable
fun SettingsScreen(onBack: () -> Unit, onOpenOfflineMaps: () -> Unit) {
    val context = LocalContext.current
    val container = context.appContainer
    val scope = rememberCoroutineScope()
    BackHandler(onBack = onBack)

    val display by container.displayPreferences.settings.collectAsStateWithLifecycle(initialValue = DisplaySettings())
    val guidance by container.navigationManager.settings.collectAsStateWithLifecycle()
    val recording by container.recordingManager.status.collectAsStateWithLifecycle()
    var message by remember { mutableStateOf<String?>(null) }
    var restartNeeded by remember { mutableStateOf(false) }
    val backup = remember { BackupManager(context, container.trackRepository) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) scope.launch {
            message = runCatching { backup.export(uri) }.fold({ "Sauvegarde enregistrée." }, { "Échec de la sauvegarde : ${it.message}" })
        }
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val error = backup.prepareRestore(uri)
            if (error == null) restartNeeded = true else message = error
        }
    }
    val storage by produceState<Pair<Long, Long>?>(null) {
        value = withContext(Dispatchers.IO) {
            val db = context.getDatabasePath("igntrail.db").let { f -> listOf("", "-wal").sumOf { File(f.path + it).length() } }
            db to File(context.filesDir, "photos").listFiles().orEmpty().sumOf { it.length() }
        }
    }
    val offlineRegions by container.offlineMaps.regions.collectAsStateWithLifecycle()
    var lastCrash by remember { mutableStateOf(CrashLog.lastCrashMillis(context)) }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.statusBarsPadding()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour") }
                Text("Paramètres", style = MaterialTheme.typography.titleLarge)
            }
            Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Section("Affichage de la vitesse")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SpeedUnit.entries.forEach { u ->
                        FilterChip(selected = display.speedUnit == u, onClick = { scope.launch { container.displayPreferences.setSpeedUnit(u) } }, label = { Text(u.label) })
                    }
                }
                Hint("Automatique : allure (min/km) en course à pied, vitesse (km/h) pour les autres activités.")

                Section("Couleurs du tracé")
                Hint(
                    "Pendant la sortie, le tracé est d'une seule couleur. À la fin, chaque portion est colorée " +
                        "par rapport à la vitesse moyenne de la sortie : rouge = plus lent, jaune = la moyenne, " +
                        "vert = plus rapide. Ces couleurs sont ensuite enregistrées avec la sortie.",
                )

                Section("Guidage")
                Text("Alerte de sortie de parcours au-delà de :")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    GuidanceSettings.TOLERANCE_CHOICES_M.forEach { m ->
                        FilterChip(selected = guidance.toleranceM == m, onClick = { container.navigationManager.updateSettings(guidance.copy(toleranceM = m)) }, label = { Text("$m m") })
                    }
                }
                SwitchRow("Vibration", guidance.vibrate) { container.navigationManager.updateSettings(guidance.copy(vibrate = it)) }
                SwitchRow("Son", guidance.sound) { container.navigationManager.updateSettings(guidance.copy(sound = it)) }

                Section("Enregistrement")
                Hint(
                    "L'activité et le délai de pause automatique se choisissent au départ de chaque sortie ; " +
                        "le dernier choix est repris par défaut.",
                )
                Hint(if (container.barometer.available) "Baromètre détecté : dénivelés mesurés par la pression atmosphérique." else "Pas de baromètre : dénivelés calculés à partir du GPS.")

                Section("Stockage")
                storage?.let { (db, photos) ->
                    Text("Sorties et itinéraires : ${Format.bytes(db)} · Photos : ${Format.bytes(photos)}")
                }
                Text("Cartes hors ligne : ${Format.bytes(offlineRegions.sumOf { it.sizeBytes })} (${offlineRegions.size} zone(s))")
                OutlinedButton(onClick = onOpenOfflineMaps) { Text("Gérer les cartes hors ligne") }

                Section("Sauvegarde")
                Hint("Archive complète : randonnées (mesures brutes), itinéraires, points d'intérêt, photos et réglages. Les cartes hors ligne ne sont pas incluses.")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = {
                        exportLauncher.launch("IGN-Trail-sauvegarde-${SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date())}.zip")
                    }) { Text("Exporter") }
                    OutlinedButton(
                        onClick = { restoreLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) },
                        enabled = recording !is RecordingStatus.Active,
                    ) { Text("Restaurer…") }
                }
                if (recording is RecordingStatus.Active) Hint("Terminez la randonnée en cours pour restaurer une sauvegarde.")

                Section("Diagnostic")
                val crash = lastCrash
                if (crash == null) {
                    Hint("Aucun plantage enregistré.")
                } else {
                    Hint(
                        "L'application s'est fermée anormalement le " +
                            SimpleDateFormat("d MMMM yyyy à HH:mm", Locale.FRANCE).format(Date(crash)) +
                            ". Le rapport (sans position ni trace) reste sur le téléphone ; vous pouvez le partager pour aider à corriger le problème.",
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { CrashLog.share(context) }) { Text("Partager le rapport") }
                        TextButton(onClick = {
                            CrashLog.clear(context)
                            lastCrash = null
                        }) { Text("Effacer") }
                    }
                }
                Section("Confidentialité")
                Hint(
                    "Vos traces restent sur votre téléphone : aucun compte, aucune publicité, aucune statistique d'usage. " +
                        "Rien n'est envoyé sans action de votre part (export, partage).",
                )
                Hint(
                    "Services réseau utilisés (Géoplateforme IGN, data.geopf.fr) : tuiles de carte de la zone affichée ; " +
                        "calcul d'itinéraire (coordonnées des points placés) et altitudes (points du parcours créé, " +
                        "point d'intérêt posé sur la carte) lors de la création de parcours. Les traces enregistrées ne sont jamais transmises.",
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    message?.let { m ->
        AlertDialog(onDismissRequest = { message = null }, text = { Text(m) }, confirmButton = { TextButton(onClick = { message = null }) { Text("OK") } })
    }
    if (restartNeeded) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Restauration prête") },
            text = { Text("Vos données actuelles vont être remplacées par celles de la sauvegarde. L'application va redémarrer.") },
            confirmButton = { Button(onClick = { restart(context) }) { Text("Redémarrer") } },
        )
    }
}

/** Relance l'application pour appliquer la restauration avant l'ouverture de la base. */
private fun restart(context: Context) {
    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
    context.startActivity(Intent.makeRestartActivityTask(intent.component))
    exitProcess(0)
}

@Composable
private fun Section(title: String) {
    Spacer(Modifier.height(6.dp))
    Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    HorizontalDivider()
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
        Spacer(Modifier.width(4.dp))
    }
}
