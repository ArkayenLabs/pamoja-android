package com.pamoja.app.data.local.health

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Reads the hardware step counter ONCE and returns immediately.
 *
 * TYPE_STEP_COUNTER is a dedicated low-power hardware chip that counts steps
 * continuously since the last device reboot — even when the app is not running.
 * Querying it requires only a brief sensor registration (typically <100 ms on modern
 * hardware) and then immediately unregistering. Zero foreground service, zero persistent
 * notification, negligible battery impact.
 *
 * Returns null if:
 *  - The device has no step counter sensor
 *  - The sensor doesn't deliver an event within 3 seconds (timeout guard)
 *  - ACTIVITY_RECOGNITION permission is not granted
 */
@Singleton
class StepReader @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /**
     * Suspends briefly, registers the sensor listener, gets one reading, then unregisters.
     * Safe to call from any coroutine (IO or Main). Typically completes in <200 ms.
     */
    suspend fun readCurrentStepCount(): Long? {
        val sensorManager =
            context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
            ?: return null   // Device has no step counter hardware

        return withTimeoutOrNull(3_000L) {
            suspendCancellableCoroutine { cont ->
                val listener = object : SensorEventListener {
                    override fun onSensorChanged(event: SensorEvent?) {
                        if (event?.sensor?.type == Sensor.TYPE_STEP_COUNTER) {
                            val hardwareCount = event.values[0].toLong()
                            sensorManager.unregisterListener(this)
                            if (cont.isActive) cont.resume(hardwareCount)
                        }
                    }
                    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
                }
                sensorManager.registerListener(
                    listener, sensor, SensorManager.SENSOR_DELAY_FASTEST
                )
                cont.invokeOnCancellation { sensorManager.unregisterListener(listener) }
            }
        }
    }
}
