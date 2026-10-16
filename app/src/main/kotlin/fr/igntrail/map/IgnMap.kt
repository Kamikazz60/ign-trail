package fr.igntrail.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import fr.igntrail.core.model.GeoPoint
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView

/** Cadrage courant, pour l'échelle graphique et la sauvegarde. */
/** [bearing] : orientation de la carte en degrés (0 = nord en haut). */
data class CameraSnapshot(val latitude: Double, val longitude: Double, val zoom: Double, val bearing: Double = 0.0)

/**
 * Vue MapLibre intégrée à Compose. Les gestes de l'utilisateur sont signalés via
 * [onUserGesture] ; les déplacements programmés (suivi GPS) ne le sont pas.
 */
@Composable
fun IgnMap(
    modifier: Modifier = Modifier,
    onMapReady: (IgnMapController) -> Unit,
    onUserGesture: () -> Unit,
    onCameraMove: (CameraSnapshot) -> Unit,
    onCameraIdle: (CameraSnapshot) -> Unit,
    /** Appui simple sur la carte ; retourne vrai s'il a été traité. */
    onMapClick: (GeoPoint) -> Boolean = { false },
    /** Appui long sur la carte (ajout d'un point d'intérêt, §12). */
    onMapLongClick: (GeoPoint) -> Boolean = { false },
) {
    val context = LocalContext.current
    val currentOnUserGesture = rememberUpdatedState(onUserGesture)
    val currentOnCameraMove = rememberUpdatedState(onCameraMove)
    val currentOnCameraIdle = rememberUpdatedState(onCameraIdle)
    val currentOnMapReady = rememberUpdatedState(onMapReady)
    val currentOnMapClick = rememberUpdatedState(onMapClick)
    val currentOnMapLongClick = rememberUpdatedState(onMapLongClick)

    val mapView = remember {
        MapView(context).apply {
            onCreate(null)
            getMapAsync { map ->
                val controller = IgnMapController(map)
                map.addOnCameraMoveStartedListener { reason ->
                    if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) {
                        currentOnUserGesture.value()
                    }
                }
                map.addOnCameraMoveListener {
                    controller.onCameraMoved()
                    currentOnCameraMove.value(map.snapshot())
                }
                map.addOnCameraIdleListener {
                    controller.onCameraMoved()
                    val snapshot = map.snapshot()
                    currentOnCameraMove.value(snapshot)
                    currentOnCameraIdle.value(snapshot)
                }
                map.addOnMapClickListener { latLng -> currentOnMapClick.value(GeoPoint(latLng.latitude, latLng.longitude)) }
                map.addOnMapLongClickListener { latLng -> currentOnMapLongClick.value(GeoPoint(latLng.latitude, latLng.longitude)) }
                currentOnMapReady.value(controller)
            }
        }
    }

    MapViewLifecycle(mapView)
    AndroidView(factory = { mapView }, modifier = modifier)
}

private fun MapLibreMap.snapshot(): CameraSnapshot {
    val position = cameraPosition
    val target = position.target
    return CameraSnapshot(target?.latitude ?: 0.0, target?.longitude ?: 0.0, position.zoom, position.bearing)
}

/** Relaye le cycle de vie Android vers la MapView (obligatoire pour MapLibre). */
@Composable
private fun MapViewLifecycle(mapView: MapView) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, mapView) {
        var started = false
        var resumed = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> { mapView.onStart(); started = true }
                Lifecycle.Event.ON_RESUME -> { mapView.onResume(); resumed = true }
                Lifecycle.Event.ON_PAUSE -> { mapView.onPause(); resumed = false }
                Lifecycle.Event.ON_STOP -> { mapView.onStop(); started = false }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            if (resumed) mapView.onPause()
            if (started) mapView.onStop()
            mapView.onDestroy()
        }
    }
}
