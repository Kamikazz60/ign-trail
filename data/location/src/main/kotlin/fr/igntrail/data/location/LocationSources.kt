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

    /**
     * Source de l'enregistrement et du guidage : satellites uniquement. Le mode « fused »
     * complète les trous de réception par des positions Wi-Fi / antennes, décalées de
     * 30 à 50 m (tracé à travers les maisons, retour terrain du 15/10/2026). Sans satellite,
     * mieux vaut aucune position : le moteur coupe alors le tracé sans rien inventer.
     */
    fun createSatellite(context: Context): LocationSource {
        val hasGps = context.getSystemService<LocationManager>()?.allProviders?.contains(LocationManager.GPS_PROVIDER) == true
        return if (hasGps) PlatformLocationSource(context) else create(context)
    }

    /** Vrai si la localisation est activée dans les réglages système. */
    fun isLocationEnabled(context: Context): Boolean =
        context.getSystemService<LocationManager>()?.isLocationEnabled ?: false
}
