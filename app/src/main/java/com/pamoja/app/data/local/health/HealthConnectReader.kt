package com.pamoja.app.data.local.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.ReadRecordsRequest
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
open class HealthConnectReader @Inject constructor(
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
    open suspend fun readTodaySteps(): Long? {
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

    /**
     * Today's steps broken down by the app that wrote them.
     *
     * Diagnostic, for the debug build only. It exists because Pamoja and Google
     * Fit can show different totals for the same day and there is no way to tell
     * from the app which explanation is true:
     *
     *  - **Two writers.** Health Connect is a shared store. If a watch, an OEM
     *    health app and Fit all record the same walk, [readTodaySteps] returns
     *    the sum across them while Fit's own screen shows only Fit's share.
     *    Nothing is broken; the two numbers are measuring different things.
     *  - **A miscount.** Something genuinely inflates the total, in which case
     *    the aggregate will not reconcile against the per-record sum.
     *
     * Both possibilities look identical from a single number, so this returns
     * the aggregate *and* the raw records side by side. If they disagree, that
     * gap is the bug. If they agree but several origins appear, the difference
     * against Fit is expected.
     *
     * This is also the groundwork for trusting only recognised writers and
     * rejecting typed-in steps, which any competitive or paid feature needs
     * before its leaderboard can be believed.
     */
    suspend fun readTodayStepsBySource(): StepSourceBreakdown? {
        return try {
            if (!isAvailable()) return null
            val client = HealthConnectClient.getOrCreate(context)
            if (!client.permissionController.getGrantedPermissions()
                    .containsAll(REQUIRED_PERMISSIONS)) return null

            val today     = LocalDate.now()
            val startTime = today.atStartOfDay(ZoneId.systemDefault()).toInstant()
            val endTime   = today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()
            val range     = TimeRangeFilter.between(startTime, endTime)

            val aggregate = client.aggregate(
                AggregateRequest(
                    metrics         = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = range,
                )
            )[StepsRecord.COUNT_TOTAL] ?: 0L

            val records = client.readRecords(
                ReadRecordsRequest(
                    recordType      = StepsRecord::class,
                    timeRangeFilter = range,
                )
            ).records

            val sources = records
                .groupBy { it.metadata.dataOrigin.packageName }
                .map { (packageName, rows) ->
                    StepSource(
                        packageName = packageName,
                        steps       = rows.sumOf { it.count },
                        recordCount = rows.size,
                        manualCount = rows.count {
                            it.metadata.recordingMethod == Metadata.RECORDING_METHOD_MANUAL_ENTRY
                        },
                    )
                }
                .sortedByDescending { it.steps }

            StepSourceBreakdown(
                aggregateTotal = aggregate,
                rawRecordTotal = records.sumOf { it.count },
                sources        = sources,
            )
        } catch (e: Exception) {
            null
        }
    }
}

/** One app's contribution to today's steps. See [HealthConnectReader.readTodayStepsBySource]. */
data class StepSource(
    val packageName: String,
    val steps: Long,
    val recordCount: Int,
    /** Records the user typed in by hand rather than a device recording them. */
    val manualCount: Int,
)

/**
 * Today's steps, as the aggregate and as the raw records behind it.
 *
 * [aggregateTotal] is what the app actually syncs. [rawRecordTotal] is the plain
 * sum of every record. They are expected to differ when writers overlap, because
 * aggregation de-duplicates and a plain sum does not, so the gap between them is
 * the measurement worth looking at rather than either number alone.
 */
data class StepSourceBreakdown(
    val aggregateTotal: Long,
    val rawRecordTotal: Long,
    val sources: List<StepSource>,
)
