package com.pamoja.app.data.local.health

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager

class StepCounterManager(private val context: Context) {
    
    /**
     * Checks if the device has a built-in step counter sensor.
     * We check both the SensorManager and the PackageManager features to be safe.
     */
    fun isStepCounterAvailable(): Boolean {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val hasSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null
        val hasFeature = context.packageManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_STEP_COUNTER)
        
        return hasSensor || hasFeature
    }
}
