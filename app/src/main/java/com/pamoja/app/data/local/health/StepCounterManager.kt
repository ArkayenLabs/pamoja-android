package com.pamoja.app.data.local.health

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import com.pamoja.app.BuildConfig

class StepCounterManager(private val context: Context) {

    /**
     * Checks if the device has a built-in step counter sensor.
     * In debug builds (emulator/dev testing), we always return true so the
     * onboarding flow is testable end-to-end without physical hardware.
     * Release builds perform the real hardware check.
     */
    fun isStepCounterAvailable(): Boolean {
        if (BuildConfig.DEBUG) return true

        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val hasSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null
        val hasFeature = context.packageManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_STEP_COUNTER)

        return hasSensor || hasFeature
    }
}
