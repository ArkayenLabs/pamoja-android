package com.pamoja.app.domain.usecase

import com.pamoja.app.domain.repository.AppConfigRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Whether this install is too old to keep running (REBUILD_PLAN.md §2.4).
 *
 * The backend is shared by every installed version, so old clients have to
 * be retirable before anything they depend on can be removed. The minimum is
 * raised on the server; until it is, nothing is gated.
 *
 * Fails open by construction: the repository answers 0 when it knows nothing,
 * and no versionCode is below 0.
 */
class ObserveUpdateRequiredUseCase @Inject constructor(
    private val repository: AppConfigRepository,
) {
    operator fun invoke(installedVersionCode: Long): Flow<Boolean> =
        repository.minimumSupportedVersionCode()
            .map { minimum -> installedVersionCode < minimum }
            .distinctUntilChanged()
}
