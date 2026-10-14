package fr.igntrail.ui.creer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.igntrail.core.model.ActivityType
import fr.igntrail.core.planning.LinkMode
import fr.igntrail.core.planning.PlanPoint
import fr.igntrail.routing.ElevationStatus
import fr.igntrail.routing.PlannerUiState
import fr.igntrail.routing.PlannerViewModel
import fr.igntrail.ui.components.ElevationChart
import fr.igntrail.ui.format.Format
import fr.igntrail.ui.theme.LocalPanelColors
import fr.igntrail.ui.theme.TrailColors

/** Panneau bas de l'onglet Créer (§8) : estimations, mode, actions. */
@Composable
fun CreatePanel(
    state: PlannerUiState,
    vm: PlannerViewModel,
    onShowList: () -> Unit,
    onSave: () -> Unit,
    onConfirmMove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val panel = LocalPanelColors.current
    var confirmClear by remember { mutableStateOf(false) }
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp), color = panel.container, shadowElevation = 8.dp) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            when {
                state.movingId != null -> MoveBar(onConfirm = onConfirmMove, onCancel = vm::cancelMove)
                state.points.isEmpty() -> Text("Touchez la carte pour placer le départ.", color = panel.content, fontWeight = FontWeight.SemiBold)
                state.points.size == 1 -> Text("Touchez la carte pour placer la prochaine étape ou l'arrivée.", color = panel.content, fontWeight = FontWeight.SemiBold)
                else -> EstimateBlock(state)
            }
            if (state.movingId == null) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = state.mode == LinkMode.FOLLOW_PATHS,
                        onClick = { vm.setMode(LinkMode.FOLLOW_PATHS) },
                        label = { Text("Suivre les chemins") },
                    )
                    FilterChip(
                        selected = state.mode == LinkMode.STRAIGHT,
                        onClick = { vm.setMode(LinkMode.STRAIGHT) },
                        label = { Text("Ligne droite") },
                    )
                }
                val selected = state.points.firstOrNull { it.id == state.selectedId }
                if (selected != null) SelectedBar(state, selected, vm)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ToolButton(Icons.AutoMirrored.Filled.Undo, "Annuler", state.canUndo, vm::undo)
                    ToolButton(Icons.AutoMirrored.Filled.Redo, "Rétablir", state.canRedo, vm::redo)
                    ToolButton(Icons.Filled.FormatListNumbered, "Liste des points", state.points.isNotEmpty(), onShowList)
                    ToolButton(Icons.Filled.SwapVert, "Inverser le sens", state.points.size >= 2, vm::reverse)
                    // Tout effacer : confirmation obligatoire (retour terrain : appui accidentel).
                    ToolButton(Icons.Filled.DeleteSweep, "Effacer tout le parcours", state.points.isNotEmpty()) { confirmClear = true }
                    Spacer(Modifier.weight(1f))
                    Button(onClick = onSave, enabled = state.points.size >= 2 && !state.computing) {
                        Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Enregistrer")
                    }
                }
            }
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Effacer tout le parcours ?") },
            text = { Text("Les ${state.points.size} points placés seront supprimés. Vous pourrez encore annuler avec la flèche ↶.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    vm.clear()
                }) { Text("Tout effacer", color = TrailColors.Red) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Garder") } },
        )
    }
}

@Composable
private fun EstimateBlock(state: PlannerUiState) {
    val panel = LocalPanelColors.current
    val e = state.estimate
    // Profil replié par défaut : le panneau doit laisser la carte visible pour placer les points.
    var showProfile by rememberSaveable { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Estimation", color = panel.secondary, fontSize = 12.sp, modifier = Modifier.weight(1f))
        if (state.elevationStatus == ElevationStatus.READY) {
            TextButton(onClick = { showProfile = !showProfile }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                Text(if (showProfile) "Masquer le profil" else "Profil")
            }
        }
        if (state.computing || state.elevationStatus == ElevationStatus.LOADING) {
            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(6.dp))
            Text(if (state.computing) "Calcul de l'itinéraire…" else "Altitudes…", color = panel.secondary, fontSize = 12.sp)
        }
    }
    if (e != null) {
        val hours = e.durationMs / 3_600_000
        val minutes = (e.durationMs / 60_000) % 60
        Text(
            "${Format.distance(e.distanceM)} · D+ ${e.gainM?.let { Format.meters(it) } ?: "–"} · D- ${e.lossM?.let { Format.meters(it) } ?: "–"} · ≈ ${hours} h %02d".format(minutes),
            color = panel.content,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            "Durée ${state.activity.label.lowercase()}" + if (e.personalSpeed) " d'après vos sorties" else " (rythme standard)",
            color = panel.secondary,
            fontSize = 12.sp,
        )
        if (e.straightM >= 10) {
            val failed = state.geometry.failedLegs
            Text(
                "Dont ${Format.distance(e.straightM)} en ligne directe (orange) : non vérifié sur le terrain." +
                    if (failed > 0) " Calcul impossible sur $failed liaison(s), connexion requise." else "",
                color = TrailColors.Orange,
                fontSize = 12.sp,
            )
        }
    }
    when (state.elevationStatus) {
        ElevationStatus.READY -> if (showProfile) state.profile?.let { ElevationChart(it, Modifier.fillMaxWidth()) }
        ElevationStatus.UNAVAILABLE -> Text("Profil altimétrique indisponible (connexion requise, ou zone hors de France).", color = panel.secondary, fontSize = 12.sp)
        else -> Unit
    }
}

@Composable
private fun SelectedBar(state: PlannerUiState, point: PlanPoint, vm: PlannerViewModel) {
    val panel = LocalPanelColors.current
    val index = state.points.indexOf(point)
    val label = when (index) {
        0 -> "Départ"
        state.points.lastIndex -> "Arrivée"
        else -> "Étape $index"
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Point : $label", color = panel.content, fontSize = 14.sp, modifier = Modifier.weight(1f))
        TextButton(onClick = { vm.startMove(point.id) }) {
            Icon(Icons.Filled.OpenWith, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text("Déplacer")
        }
        TextButton(onClick = { vm.delete(point.id) }) {
            Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp), tint = TrailColors.Red)
            Spacer(Modifier.width(4.dp))
            Text("Supprimer", color = TrailColors.Red)
        }
    }
}

@Composable
private fun MoveBar(onConfirm: () -> Unit, onCancel: () -> Unit) {
    val panel = LocalPanelColors.current
    Text("Déplacez la carte pour placer la cible sur le nouvel emplacement.", color = panel.content, fontWeight = FontWeight.SemiBold)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Annuler", color = panel.content) }
        Button(onClick = onConfirm, modifier = Modifier.weight(1.3f)) { Text("Valider la position") }
    }
}

@Composable
private fun ToolButton(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    val panel = LocalPanelColors.current
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(icon, contentDescription = label, tint = if (enabled) panel.content else panel.secondary.copy(alpha = 0.4f))
    }
}

/** Liste des points (§8.1, §8.3) : réorganisation et suppression. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanPointsSheet(state: PlannerUiState, vm: PlannerViewModel, onDismiss: () -> Unit) {
    // Distance cumulée à chaque point, le long de l'itinéraire calculé.
    val cumulative = remember(state.geometry) {
        var acc = 0.0
        listOf(0.0) + state.geometry.legs.map { acc += it.lengthM; acc }
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp).navigationBarsPadding()) {
            Text("Points de passage", style = MaterialTheme.typography.titleLarge)
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                itemsIndexed(state.points, key = { _, p -> p.id }) { i, p ->
                    val label = when (i) {
                        0 -> "Départ"
                        state.points.lastIndex -> "Arrivée"
                        else -> "Étape $i"
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(label, fontWeight = FontWeight.SemiBold)
                            Text(
                                if (i == 0) "km 0" else "à ${Format.distance(cumulative.getOrElse(i) { 0.0 })} du départ",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { vm.shift(p.id, -1) }, enabled = i > 0) { Icon(Icons.Filled.ArrowUpward, contentDescription = "Monter") }
                        IconButton(onClick = { vm.shift(p.id, 1) }, enabled = i < state.points.lastIndex) { Icon(Icons.Filled.ArrowDownward, contentDescription = "Descendre") }
                        IconButton(onClick = { vm.delete(p.id) }) { Icon(Icons.Filled.Delete, contentDescription = "Supprimer", tint = TrailColors.Red) }
                    }
                    HorizontalDivider()
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** Nom et activité, puis enregistrement local (§8.3). */
@Composable
fun SavePlanDialog(state: PlannerUiState, vm: PlannerViewModel, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enregistrer le parcours") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text("Nom") }, placeholder = { Text("Mon parcours") })
                Text("Activité (pour la durée estimée)", style = MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ActivityType.entries.forEach { a ->
                        FilterChip(selected = state.activity == a, onClick = { vm.setActivity(a) }, label = { Text(a.label) })
                    }
                }
                if ((state.estimate?.straightM ?: 0.0) >= 10) {
                    Text(
                        "Le parcours contient ${Format.distance(state.estimate!!.straightM)} de liaisons directes non vérifiées.",
                        color = TrailColors.Orange,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = { Button(onClick = { onSave(name) }) { Text("Enregistrer") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

/** Après l'enregistrement : suivre, télécharger la carte, ou continuer. */
@Composable
fun SavedPlanDialog(name: String, onFollow: () -> Unit, onOffline: () -> Unit, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Parcours enregistré") },
        text = { Text("« $name » est disponible dans Parcours › Itinéraires.") },
        confirmButton = { Button(onClick = onFollow, colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = TrailColors.Green, contentColor = Color.White)) { Text("Suivre maintenant") } },
        dismissButton = {
            Row {
                TextButton(onClick = onOffline) { Text("Carte hors ligne") }
                TextButton(onClick = onClose) { Text("Fermer") }
            }
        },
    )
}
