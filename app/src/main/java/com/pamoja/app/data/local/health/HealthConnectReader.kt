package com.pamoja.app.data.local.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads today's cumulative step count from Health Connect.
 *
 * Health Connect is a system-level data store managed by Google, it works
 * reliably in the background without a foreground service. It works on any
 * app installed from the Play Store (any track: internal, closed, production).
 *
 * Why Health Connect instead of Sensor.TYPE_STEP_COUNTER:
 *  - Battery-optimized: no background sensor registration
 *  - Reliable across OEM battery-killers (Samsung, Xiaomi, OnePlus)
 *  - Data persists even when the app is not running
 *  - Standard Google permission dialog, users trust it more
 */
@Singleton
class HealthConnectReader @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        /** The single permission we require, read daily steps. */
        val REQUIRED_PERMISSIONS = setOf(
            HealthPermission.getReadPermission(StepsRecord::class)
        )
    }

    /**
     * Returns true if Health Connect is available on this device/Android version.
     * On Android 14+ it is built-in. On Android 9-13 the user must have the
     * Health Connect app installed from the Play Store.
     */
    fun isAvailable(): Boolean =
        HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    /**
     * Returns true if the user has already granted the Steps read permission.
     */
    suspend fun hasPermission(): Boolean {
        if (!isAvailable()) return false
        val client = HealthConnectClient.getOrCreate(context)
        val granted = client.permissionController.getGrantedPermissions()
        return granted.containsAll(REQUIRED_PERMISSIONS)
    }

    /**
     * Reads the total steps recorded in Health Connect for today (midnight → now).
     * Returns null if Health Connect is unavailable or permission is not granted.
     */
    suspend fun readTodaySteps(): Long? {
        return try {
            if (!isAvailable()) return null
            val client = HealthConnectClient.getOrCreate(context)
            if (!client.permissionController.getGrantedPermissions()
                    .containsAll(REQUIRED_PERMISSIONS)) return null

            val today     = LocalDate.now()
            val startTime = today.atStartOfDay(ZoneId.systemDefault()).toInstant()
            val endTime   = today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()

            // aggregate() deduplicates overlapping records from multiple apps
            // (e.g. Google Fit writes both raw records AND a merged total, readRecords()
            // would sum both and double the count; aggregate() returns one correct total)
            val response = client.aggregate(
                AggregateRequest(
                    metrics         = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                )
            )
            response[StepsRecord.COUNT_TOTAL] ?: 0L
        } catch (e: Exception) {
            null
        }
    }
}
