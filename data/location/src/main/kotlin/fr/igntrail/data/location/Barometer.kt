package fr.igntrail.data.location

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.core.content.getSystemService
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Baromètre du téléphone (§11) : pression atmosphérique en hPa, environ une fois par
 * seconde. Flux vide si le téléphone n'en a pas.
 */
class Barometer(context: Context) {
    private val manager: SensorManager? = context.getSystemService()
    private val sensor: Sensor? = manager?.getDefaultSensor(Sensor.TYPE_PRESSURE)

    val available: Boolean get() = sensor != null

    fun pressures(): Flow<Float> {
        val m = manager ?: return emptyFlow()
        val s = sensor ?: return emptyFlow()
        return callbackFlow {
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    trySend(event.values[0])
                }

                override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
            }
            // Une mesure par seconde suffit et ménage la batterie (§17).
            m.registerListener(listener, s, 1_000_000)
            awaitClose { m.unregisterListener(listener) }
        }
    }
}
