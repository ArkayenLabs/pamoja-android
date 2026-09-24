package com.pamoja.app.domain.usecase

import com.pamoja.app.domain.error.AppError
import com.pamoja.app.domain.error.SponsorshipFailure
import com.pamoja.app.domain.error.toAppError
import com.pamoja.app.domain.model.Group
import com.pamoja.app.domain.model.PamojaGroupId
import com.pamoja.app.domain.repository.AuthRepository
import com.pamoja.app.domain.repository.GroupRepository
import com.pamoja.app.domain.repository.GroupSponsorship
import com.pamoja.app.domain.repository.GroupSponsorshipRepository
import com.pamoja.app.domain.repository.PurchaseContext
import kotlinx.coroutines.delay
import javax.inject.Inject

class ActivateGroupSponsorshipUseCase @Inject constructor(
    private val sponsorshipRepository: GroupSponsorshipRepository,
) {
    suspend fun purchaseContext(groupId: String) = sponsorshipRepository.purchaseContext(groupId)
    suspend operator fun invoke(
        groupId: String,
        replaceExisting: Boolean = false,
        context: PurchaseContext? = null,
    ): Result<GroupSponsorship> {
        val normalizedGroupId = groupId.trim()
        if (!PamojaGroupId.isValid(normalizedGroupId)) {
            return Result.failure(
                AppError.Sponsorship(SponsorshipFailure.InvalidGroup)
            )
        }
        if (context != null) {
            if (context.groupId != normalizedGroupId) return Result.failure(AppError.SessionExpired())
            return sponsorshipRepository.activateChecked(context, replaceExisting)
        }
        return sponsorshipRepository.activate(normalizedGroupId, replaceExisting)
    }
}

/**
 * Confirms a just-completed store purchase against the server-owned group.
 *
 * RevenueCat's Android SDK can return updated CustomerInfo before the server
 * API exposes the same entitlement. Only that one known propagation result is
 * retried, and only for the same group. Every authorization or ownership
 * conflict returns immediately.
 */
class ConfirmPurchasedGroupAccessUseCase @Inject constructor(
    private val activateGroupSponsorshipUseCase: ActivateGroupSponsorshipUseCase,
) {
    suspend fun purchaseContext(groupId: String) = activateGroupSponsorshipUseCase.purchaseContext(groupId)
    suspend operator fun invoke(
        groupId: String,
        replaceExisting: Boolean = false,
        context: PurchaseContext? = null,
        retryCapacityIncrease: Boolean = false,
    ): Result<GroupSponsorship> {
        var result = activateGroupSponsorshipUseCase(groupId, replaceExisting, context)
        SPONSORSHIP_RETRY_DELAYS_MILLIS.forEach { delayMillis ->
            if (!result.isWaitingForRevenueCat(retryCapacityIncrease)) return result
            delay(delayMillis)
            result = activateGroupSponsorshipUseCase(groupId, replaceExisting, context)
        }
        return result
    }
}

internal val SPONSORSHIP_RETRY_DELAYS_MILLIS = listOf(1_000L, 2_000L, 4_000L)

private fun Result<GroupSponsorship>.isWaitingForRevenueCat(retryCapacityIncrease: Boolean): Boolean {
    val error = exceptionOrNull() as? AppError.Sponsorship ?: return false
    return error.reason == SponsorshipFailure.NoActiveSubscription ||
        (retryCapacityIncrease && error.reason == SponsorshipFailure.SubscriptionAssignedElsewhere)
}

/**
 * Resolves the one real group a paywall is allowed to name.
 *
 * Navigation contributes only an ID. Group details and current membership are
 * read again from the data layer, so process recreation or a stale screen can
 * never turn route text into authority to sell a group subscription.
 */
class ResolveSponsorshipGroupUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val groupRepository: GroupRepository,
) {
    suspend operator fun invoke(groupId: String): Result<Group> {
        val normalizedGroupId = groupId.trim()
        if (!PamojaGroupId.isValid(normalizedGroupId)) {
            return Result.failure(
                AppError.Sponsorship(SponsorshipFailure.InvalidGroup)
            )
        }

        val currentUser = runCatching { authRepository.getCurrentUser() }
            .getOrElse { error -> return Result.failure(error.toAppError()) }
            ?.takeIf { user -> user.userId.isNotBlank() }
            ?: return Result.failure(AppError.SessionExpired())

        val group = groupRepository.getGroup(normalizedGroupId).getOrElse { error ->
            return Result.failure(
                error.toSponsorshipContextError(SponsorshipFailure.GroupUnavailable)
            )
        }
        if (group.groupId != normalizedGroupId || group.name.isBlank()) {
            return Result.failure(
                AppError.Sponsorship(SponsorshipFailure.GroupUnavailable)
            )
        }

        val membership = groupRepository
            .getMembership(currentUser.userId, normalizedGroupId)
            .getOrElse { error ->
                return Result.failure(
                    error.toSponsorshipContextError(SponsorshipFailure.NotCurrentMember)
                )
            }
        if (
            membership.userId != currentUser.userId ||
            membership.groupId != normalizedGroupId
        ) {
            return Result.failure(
                AppError.Sponsorship(SponsorshipFailure.NotCurrentMember)
            )
        }

        return Result.success(group)
    }
}

private fun Throwable.toSponsorshipContextError(
    missingReason: SponsorshipFailure,
): AppError {
    val appError = toAppError()
    return when (appError) {
        is AppError.NotFound,
        is AppError.PermissionDenied -> AppError.Sponsorship(
            reason = missingReason,
            detail = appError.technicalMessage,
            cause = appError,
        )

        else -> appError
    }
}
