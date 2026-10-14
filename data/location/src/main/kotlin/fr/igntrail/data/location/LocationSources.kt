package fr.igntrail.data.location

import android.content.Context
import android.location.LocationManager
import androidx.core.content.getSystemService
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability

object LocationSources {
    /** Fused si les Google Play Services sont disponibles, sinon GNSS natif. */
    fun create(context: Context): LocationSource {
        val gms = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context)
        return if (gms == ConnectionResult.SUCCESS) FusedLocationSource(context) else PlatformLocationSource(context)
    }

    /** Vrai si la localisation est activée dans les réglages système. */
    fun isLocationEnabled(context: Context): Boolean =
        context.getSystemService<LocationManager>()?.isLocationEnabled ?: false
}
