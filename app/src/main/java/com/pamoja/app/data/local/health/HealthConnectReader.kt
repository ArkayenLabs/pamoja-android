package com.pamoja.app.data.local.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import kotlinx.coroutines.CancellationException
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId
import com.pamoja.app.domain.model.AdventureSourceWindow
import com.pamoja.app.domain.model.AdventureStepObservation
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads daily aggregated steps. Background reads require the optional Health
 * Connect permission on supported devices; Android may defer scheduled work.
 * Stored readings can be recovered when access resumes.
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
        val BACKGROUND_PERMISSIONS = setOf(HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND)
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
    fun supportsBackgroundRead(): Boolean = isAvailable() &&
        HealthConnectClient.getOrCreate(context).features.getFeatureStatus(
            HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_IN_BACKGROUND,
        ) == HealthConnectFeatures.FEATURE_STATUS_AVAILABLE

    suspend fun hasBackgroundPermission(): Boolean = supportsBackgroundRead() &&
        HealthConnectClient.getOrCreate(context).permissionController.getGrantedPermissions()
            .containsAll(BACKGROUND_PERMISSIONS)

    open suspend fun readTodaySteps(): Long? = readStepsForDate(LocalDate.now())

    /** Adventure-only UTC query. The caller uses the server-issued observation
     * cutoff; locale/travel must not move an already identified source window.
     * Shared by explicit foreground contribution and enrolled background sync. */
    open suspend fun readAdventureSteps(
        window: AdventureSourceWindow,
        observedThrough: Instant,
    ): AdventureStepObservation? {
        require(observedThrough > window.start && observedThrough <= window.endExclusive)
        return try {
            if (!isAvailable()) return null
            val client = HealthConnectClient.getOrCreate(context)
            if (!client.permissionController.getGrantedPermissions()
                    .containsAll(REQUIRED_PERMISSIONS)) return null
            val result = client.aggregate(
                AggregateRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(window.start, observedThrough),
                ),
            )
            // Unlike the ordinary display counter, an absent source must not
            // seed a zero baseline and later credit pre-join activity.
            val steps = result[StepsRecord.COUNT_TOTAL] ?: return null
            if (steps !in 0L..200_000L) return null
            AdventureStepObservation(window, observedThrough, steps)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }

    open suspend fun readStepsForDate(date: LocalDate): Long? {
        return try {
            if (!isAvailable()) return null
            val client = HealthConnectClient.getOrCreate(context)
            if (!client.permissionController.getGrantedPermissions()
                    .containsAll(REQUIRED_PERMISSIONS)) return null

            val today     = date
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
        } catch (cancelled: CancellationException) {
            throw cancelled
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
