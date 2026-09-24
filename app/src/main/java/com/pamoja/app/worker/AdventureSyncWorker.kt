package com.pamoja.app.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pamoja.app.data.local.health.HealthConnectReader
import com.pamoja.app.di.TogetherTrailEnabled
import com.pamoja.app.domain.model.AdventureFailure
import com.pamoja.app.domain.repository.AdventureRepository
import com.pamoja.app.domain.repository.AuthRepository
import com.pamoja.app.domain.usecase.GetUserGroupsUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/** Android may defer this work. It requires background health permission and
 * only syncs existing active enrollment; it can never establish consent. */
@HiltWorker
class AdventureSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted parameters: WorkerParameters,
    private val auth: AuthRepository,
    private val groups: GetUserGroupsUseCase,
    private val repository: AdventureRepository,
    private val health: HealthConnectReader,
    @TogetherTrailEnabled private val enabled: Boolean,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        if (!enabled) return Result.success()
        val uid = auth.getCurrentUser()?.userId ?: return Result.success()
        return try {
            if (!health.hasBackgroundPermission()) return Result.success()
            var retry = false
            for (group in groups(uid).first()) {
                if (auth.getCurrentUser()?.userId != uid) return Result.success()
                try {
                    val id = repository.entry(group.groupId).adventureId ?: continue
                    val adventure = repository.read(group.groupId, id)
                    if (adventure.completed || adventure.earnedFinish || adventure.participation?.active != true) continue
                    if (auth.getCurrentUser()?.userId != uid) return Result.success()
                    repository.sync(group.groupId, id, join = false)
                } catch (cancelled: CancellationException) { throw cancelled
                } catch (error: AdventureFailure) {
                    if (error.reason !in setOf("feature_disabled", "health_unavailable", "device_bound_elsewhere",
                            "access_denied", "signed_out", "join_required", "participation_changed", "adventure_not_active")) retry = true
                }
            }
            if (retry) Result.retry() else Result.success()
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (_: Exception) { Result.retry() }
    }
    companion object { const val WORK_NAME = "TogetherTrailSync" }
}
