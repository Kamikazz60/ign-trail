package fr.igntrail.data.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import fr.igntrail.core.model.LocationFix
import fr.igntrail.core.model.LocationSourceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

@SuppressLint("MissingPermission")
class FusedLocationSource(private val context: Context) : LocationSource {
    private val client = LocationServices.getFusedLocationProviderClient(context)

    override val type = "Fused"

    override fun updates(intervalMs: Long): Flow<LocationFix> = callbackFlow<Location> {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs)
            .setMinUpdateIntervalMillis(intervalMs / 2)
            .setWaitForAccurateLocation(false)
            .build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.locations.forEach { trySend(it) }
            }
        }
        client.requestLocationUpdates(request, callback, Looper.getMainLooper())
            .addOnFailureListener { close(it) }
        awaitClose { client.removeLocationUpdates(callback) }
    }.map { it.toFix(context, LocationSourceType.FUSED) }.flowOn(Dispatchers.Default)

    override suspend fun lastKnown(): LocationFix? =
        runCatching { client.lastLocation.await() }.getOrNull()
            ?.let { withContext(Dispatchers.Default) { it.toFix(context, LocationSourceType.FUSED) } }
}
