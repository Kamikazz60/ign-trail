package fr.igntrail.data.location

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import android.view.WindowManager
import androidx.core.content.getSystemService
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Cap magnétique du téléphone (degrés, 0 = nord) à partir du capteur de rotation.
 * Émet null tant que la fiabilité du magnétomètre est insuffisante (§3.3).
 */
class CompassHeading(private val context: Context) {
    private val sensors: SensorManager? = context.getSystemService()

    fun headings(): Flow<Float?> {
        val manager = sensors ?: return emptyFlow()
        val sensor = manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) ?: return emptyFlow()
        return callbackFlow {
            val rotation = FloatArray(9)
            val remapped = FloatArray(9)
            val orientation = FloatArray(3)
            var reliable = true
            var smoothed: Float? = null
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    if (!reliable) {
                        trySend(null)
                        return
                    }
                    SensorManager.getRotationMatrixFromVector(rotation, event.values)
                    val (axisX, axisY) = when (displayRotation()) {
                        Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
                        Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
                        Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
                        else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
                    }
                    SensorManager.remapCoordinateSystem(rotation, axisX, axisY, remapped)
                    SensorManager.getOrientation(remapped, orientation)
                    val azimuth = ((Math.toDegrees(orientation[0].toDouble()) + 360.0) % 360.0).toFloat()
                    smoothed = smoothed?.let { lowPass(it, azimuth) } ?: azimuth
                    // Arrondi à 2° : évite de redessiner la carte pour des micro-variations.
                    trySend(((smoothed!! / 2f).roundToInt() * 2 % 360).toFloat())
                }

                override fun onAccuracyChanged(s: Sensor, accuracy: Int) {
                    reliable = accuracy >= SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM
                }
            }
            manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
            awaitClose { manager.unregisterListener(listener) }
        }.distinctUntilChanged()
    }

    @Suppress("DEPRECATION")
    private fun displayRotation(): Int =
        context.getSystemService<WindowManager>()?.defaultDisplay?.rotation ?: Surface.ROTATION_0

    private fun lowPass(previous: Float, current: Float): Float {
        var delta = current - previous
        if (delta > 180f) delta -= 360f
        if (delta < -180f) delta += 360f
        val alpha = if (abs(delta) > 30f) 0.5f else 0.2f
        return (previous + alpha * delta + 360f) % 360f
    }
}
