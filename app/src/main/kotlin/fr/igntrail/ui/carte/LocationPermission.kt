package fr.igntrail.ui.carte

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import fr.igntrail.data.location.LocationSources

enum class LocationAccess { NONE, APPROXIMATE, PRECISE }

class LocationPermissionState(
    val access: LocationAccess,
    val permanentlyDenied: Boolean,
    val locationEnabled: Boolean,
    val request: () -> Unit,
)

private val PERMISSIONS = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

private fun Context.locationAccess(): LocationAccess = when {
    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED -> LocationAccess.PRECISE
    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED -> LocationAccess.APPROXIMATE
    else -> LocationAccess.NONE
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Demande l'autorisation au premier affichage (§3.2), puis revérifie
 * l'autorisation et l'état de la localisation à chaque retour au premier plan.
 */
@Composable
fun rememberLocationPermissionState(): LocationPermissionState {
    val context = LocalContext.current
    var access by remember { mutableStateOf(context.locationAccess()) }
    var permanentlyDenied by remember { mutableStateOf(false) }
    var locationEnabled by remember { mutableStateOf(LocationSources.isLocationEnabled(context)) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        access = context.locationAccess()
        val activity = context.findActivity()
        permanentlyDenied = access == LocationAccess.NONE && activity != null &&
            !activity.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    LaunchedEffect(Unit) {
        if (access == LocationAccess.NONE) launcher.launch(PERMISSIONS)
    }

    LifecycleResumeEffect(Unit) {
        access = context.locationAccess()
        locationEnabled = LocationSources.isLocationEnabled(context)
        onPauseOrDispose { }
    }

    return LocationPermissionState(access, permanentlyDenied, locationEnabled) {
        if (permanentlyDenied) {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } else {
            launcher.launch(PERMISSIONS)
        }
    }
}

fun Context.openLocationSettings() {
    startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
