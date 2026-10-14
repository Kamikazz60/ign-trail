package fr.igntrail.ui.poi

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import fr.igntrail.appContainer
import fr.igntrail.core.model.GeoPoint
import fr.igntrail.data.storage.Photo
import fr.igntrail.data.storage.Poi
import fr.igntrail.data.storage.PoiCategory
import fr.igntrail.routing.IgnElevationProvider
import fr.igntrail.ui.format.Format
import fr.igntrail.ui.theme.TrailColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Nouveau point d'intérêt, à la position donnée (§12). */
fun newPoi(position: GeoPoint, altitudeM: Double?, sessionId: Long?) = Poi(
    id = 0,
    sessionId = sessionId,
    name = "",
    category = PoiCategory.OTHER,
    position = position,
    altitudeM = altitudeM,
    description = "",
    createdTime = System.currentTimeMillis(),
)

/** Fichiers photo dans le dossier privé de l'application. */
object PhotoFiles {
    fun newFile(dir: File): File =
        File(dir, "IMG_${SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.ROOT).format(Date())}.jpg")

    fun uriFor(context: Context, file: File): Uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)

    /** Copie une image choisie dans la galerie (l'original n'est pas modifié). */
    suspend fun importFrom(context: Context, uri: Uri, dir: File): File? = withContext(Dispatchers.IO) {
        runCatching {
            val file = newFile(dir)
            context.contentResolver.openInputStream(uri)?.use { input -> file.outputStream().use { input.copyTo(it) } } ?: return@runCatching null
            file
        }.getOrNull()
    }
}

/**
 * Création ou modification d'un point d'intérêt : nom, catégorie, description,
 * photos (appareil photo ou galerie), altitude. [onDone] est appelé à la fermeture.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PoiEditorSheet(initial: Poi, onDone: () -> Unit) {
    val context = LocalContext.current
    val repo = context.appContainer.poiRepository
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(initial.name) }
    var category by remember { mutableStateOf(initial.category) }
    var description by remember { mutableStateOf(initial.description) }
    var altitude by remember { mutableStateOf(initial.altitudeM) }
    val pending = remember { mutableStateListOf<File>() }
    val photosFlow = remember(initial.id) { if (initial.id != 0L) repo.photosOfPoi(initial.id) else flowOf(emptyList()) }
    val saved by photosFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    var confirmDelete by remember { mutableStateOf(false) }
    var viewing by remember { mutableStateOf<File?>(null) }
    var captureFile by remember { mutableStateOf<File?>(null) }

    // Altitude d'un point posé sur la carte : modèle de terrain IGN si connexion (§12 : « si disponible »).
    LaunchedEffect(initial.position) {
        if (altitude == null) {
            altitude = runCatching { IgnElevationProvider().elevations(listOf(initial.position)).firstOrNull() }.getOrNull()
        }
    }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val file = captureFile
        if (ok && file != null && file.length() > 0) pending += file else file?.delete()
        captureFile = null
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { PhotoFiles.importFrom(context, uri, repo.photoDir)?.let { pending += it } }
    }

    val cancel = {
        pending.forEach { it.delete() }
        onDone()
    }
    val save: () -> Unit = {
        scope.launch {
            val id = repo.save(
                initial.copy(
                    name = name.trim().ifEmpty { category.label },
                    category = category,
                    description = description.trim(),
                    altitudeM = altitude,
                ),
            )
            pending.forEach { repo.addPhoto(it, poiId = id, sessionId = initial.sessionId) }
            onDone()
        }
    }

    ModalBottomSheet(onDismissRequest = cancel, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.padding(horizontal = 20.dp).navigationBarsPadding().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (initial.id == 0L) "Nouveau point d'intérêt" else "Point d'intérêt", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                if (initial.id != 0L) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, contentDescription = "Supprimer", tint = TrailColors.Red) }
            }
            Text(
                "%.5f, %.5f".format(Locale.ROOT, initial.position.latitude, initial.position.longitude) +
                    (altitude?.let { " · alt. ${Format.meters(it)}" } ?: "") +
                    (if (initial.sessionId != null) " · lié à la randonnée" else ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PoiCategory.entries.forEach { c ->
                    val color = Color(android.graphics.Color.parseColor(c.color))
                    FilterChip(
                        selected = category == c,
                        onClick = { category = c },
                        label = { Text(c.label) },
                        leadingIcon = { Box(Modifier.size(10.dp).background(color, RoundedCornerShape(5.dp))) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = color.copy(alpha = 0.18f)),
                    )
                }
            }
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nom") }, placeholder = { Text(category.label) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Description (facultatif)") }, minLines = 2, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = {
                    val file = PhotoFiles.newFile(repo.photoDir)
                    captureFile = file
                    runCatching { camera.launch(PhotoFiles.uriFor(context, file)) }.onFailure { captureFile = null }
                }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Photo")
                }
                OutlinedButton(onClick = {
                    gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Galerie")
                }
            }
            val files = saved.map { it.file } + pending
            if (files.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(files, key = { it.name }) { file ->
                        Box {
                            AsyncImage(
                                model = file,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(86.dp).clip(RoundedCornerShape(10.dp)).clickable { viewing = file },
                            )
                            IconButton(
                                onClick = {
                                    if (file in pending) {
                                        pending -= file
                                        file.delete()
                                    } else {
                                        saved.firstOrNull { it.file == file }?.let { scope.launch { repo.deletePhoto(it) } }
                                    }
                                },
                                modifier = Modifier.align(Alignment.TopEnd).size(28.dp).background(Color(0x99000000), RoundedCornerShape(14.dp)),
                            ) { Icon(Icons.Filled.Close, contentDescription = "Retirer la photo", tint = Color.White, modifier = Modifier.size(16.dp)) }
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextButton(onClick = cancel, modifier = Modifier.weight(1f)) { Text("Annuler") }
                Button(onClick = save, modifier = Modifier.weight(1.4f)) { Text("Enregistrer") }
            }
            Spacer(Modifier.height(12.dp))
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Supprimer ce point ?") },
            text = { Text("Le point et ses photos seront supprimés.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        repo.delete(initial.id)
                        pending.forEach { it.delete() }
                        onDone()
                    }
                }) { Text("Supprimer", color = TrailColors.Red) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annuler") } },
        )
    }
    viewing?.let { PhotoViewer(it) { viewing = null } }
}

/** Photo en plein écran. */
@Composable
fun PhotoViewer(file: File, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black).clickable(onClick = onClose)) {
            AsyncImage(model = file, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)) {
                Icon(Icons.Filled.Close, contentDescription = "Fermer", tint = Color.White)
            }
        }
    }
}

/** Vignettes cliquables (fiche de randonnée). */
@Composable
fun PhotoStrip(photos: List<Photo>, modifier: Modifier = Modifier) {
    var viewing by remember { mutableStateOf<File?>(null) }
    LazyRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(photos, key = { it.id }) { p ->
            AsyncImage(
                model = p.file,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(96.dp).clip(RoundedCornerShape(10.dp)).clickable { viewing = p.file },
            )
        }
    }
    viewing?.let { PhotoViewer(it) { viewing = null } }
}
