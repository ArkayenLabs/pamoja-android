package com.pamoja.app.data.local.health

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.pamoja.app.R
import com.pamoja.app.data.local.preferences.UserPreferences
import com.pamoja.app.domain.model.StepEntry
import com.pamoja.app.domain.repository.StepRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@AndroidEntryPoint
class StepCounterService : Service(), SensorEventListener {

    @Inject lateinit var stepRepository: StepRepository
    @Inject lateinit var userPreferences: UserPreferences

    private lateinit var sensorManager: SensorManager
    private var stepCounterSensor: Sensor? = null
    private var initialStepCount: Long = -1L
    private var currentStepCount: Long = 0L

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        const val CHANNEL_ID = "pamoja_step_counter"
        const val NOTIFICATION_ID = 1

        fun start(context: Context) {
            val intent = Intent(context, StepCounterService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, StepCounterService::class.java)
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        stepCounterSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        stepCounterSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        return START_STICKY
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_STEP_COUNTER) return

        val totalStepsSinceReboot = event.values[0].toLong()

        if (initialStepCount == -1L) {
            initialStepCount = totalStepsSinceReboot
        }

        currentStepCount = totalStepsSinceReboot - initialStepCount

        serviceScope.launch {
            val userId = userPreferences.userId.first() ?: return@launch
            val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
            val stepEntry = StepEntry(
                userId = userId,
                stepCount = currentStepCount,
                date = today
            )
            stepRepository.saveStepEntry(stepEntry)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Step Counter",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Pamoja is counting your steps"
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("Pamoja")
        .setContentText("Counting your steps")
        .setSmallIcon(R.mipmap.ic_launcher)
        .setOngoing(true)
        .setSilent(true)
        .build()
}