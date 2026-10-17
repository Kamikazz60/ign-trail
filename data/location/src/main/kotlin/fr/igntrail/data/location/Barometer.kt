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
 * Baromètre du téléphone (§11) : pression atmosphérique en hPa, une valeur par seconde,
 * moyenne des ~10 mesures de la seconde. Une mesure isolée varie de 0,2 hPa (≈ 2 m)
 * d'une seconde à l'autre téléphone en poche (retour terrain du 17/10/2026) ; la moyenne
 * réduit ce bruit à la source. Flux vide si le téléphone n'en a pas.
 */
class Barometer(context: Context) {
    private val manager: SensorManager? = context.getSystemService()
    private val sensor: Sensor? = manager?.getDefaultSensor(Sensor.TYPE_PRESSURE)

    val available: Boolean get() = sensor != null

    fun pressures(): Flow<Float> {
        val m = manager ?: return emptyFlow()
        val s = sensor ?: return emptyFlow()
        return callbackFlow {
            var sum = 0.0
            var count = 0
            var windowStartNs = 0L
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    if (count == 0) windowStartNs = event.timestamp
                    sum += event.values[0]
                    count++
                    if (event.timestamp - windowStartNs >= AVERAGE_WINDOW_NS) {
                        trySend((sum / count).toFloat())
                        sum = 0.0
                        count = 0
                    }
                }

                override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
            }
            // 10 mesures/s : coût négligeable (capteur de quelques µA), bruit divisé par ~3.
            m.registerListener(listener, s, SAMPLING_PERIOD_US)
            awaitClose { m.unregisterListener(listener) }
        }
    }

    private companion object {
        const val SAMPLING_PERIOD_US = 100_000
        const val AVERAGE_WINDOW_NS = 1_000_000_000L
    }
}
