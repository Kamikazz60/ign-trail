package fr.igntrail.data.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.getSystemService
import fr.igntrail.core.model.LocationFix
import fr.igntrail.core.model.LocationSourceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** Récepteur GNSS natif, utilisé lorsque les Google Play Services sont absents. */
@SuppressLint("MissingPermission")
class PlatformLocationSource(private val context: Context) : LocationSource {
    private val manager: LocationManager = requireNotNull(context.getSystemService())

    override val type = "GPS natif"

    override fun updates(intervalMs: Long): Flow<LocationFix> = callbackFlow<Location> {
        val listener = LocationListener { trySend(it) }
        manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, intervalMs, 0f, listener, Looper.getMainLooper())
        awaitClose { manager.removeUpdates(listener) }
    }.map { it.toFix(context, LocationSourceType.PLATFORM_GPS) }.flowOn(Dispatchers.Default)

    override suspend fun lastKnown(): LocationFix? =
        listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
            ?.let { withContext(Dispatchers.Default) { it.toFix(context, LocationSourceType.PLATFORM_GPS) } }
}
