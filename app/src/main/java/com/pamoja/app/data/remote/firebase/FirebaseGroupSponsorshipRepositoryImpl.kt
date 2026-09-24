package com.pamoja.app.data.remote.firebase

import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctionsException
import com.pamoja.app.domain.error.AppError
import com.pamoja.app.domain.error.SponsorshipFailure
import com.pamoja.app.domain.repository.GroupSponsorship
import com.pamoja.app.domain.repository.GroupSponsorshipRepository
import com.pamoja.app.domain.repository.PurchaseContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/** Calls the server-owned sponsorship boundary; it never writes billing state. */
@Singleton
class FirebaseGroupSponsorshipRepositoryImpl @Inject constructor(
    private val functions: FirebaseFunctions,
) : GroupSponsorshipRepository {

    override suspend fun purchaseContext(groupId: String): Result<PurchaseContext> = try {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: throw AppError.SessionExpired()
        val fields = functions.getHttpsCallable("getCirclePurchaseContext")
            .call(mapOf("groupId" to groupId)).await().data as? Map<*, *>
            ?: throw AppError.Unknown("Malformed purchase context")
        if (FirebaseAuth.getInstance().currentUser?.uid != uid || fields["payerUid"] != uid) {
            throw AppError.SessionExpired()
        }
        require(fields["groupId"] == groupId)
        val previous = fields["sponsoredGroupId"] as? String
        require(previous == null || com.pamoja.app.domain.model.PamojaGroupId.isValid(previous))
        val sponsoredGroupIds = (fields["sponsoredGroupIds"] as? List<*>)
            ?.filterIsInstance<String>()
            ?.onEach { require(com.pamoja.app.domain.model.PamojaGroupId.isValid(it)) }
            ?.distinct()
            ?: listOfNotNull(previous)
        val groupCapacity = (fields["groupCapacity"] as? Number)?.toInt() ?: 0
        require(groupCapacity in 0..50)
        Result.success(PurchaseContext(groupId, uid,
            fields["groupIsPremium"] as Boolean, fields["isGroupSponsor"] as Boolean,
            fields["subscriptionIsActive"] as Boolean, previous, fields["sponsoredGroupName"] as? String,
            fields["planningAvailable"] == true, fields["trailAvailable"] == true,
            groupCapacity, sponsoredGroupIds))
    } catch (cancelled: CancellationException) { throw cancelled
    } catch (error: Exception) { Result.failure(error.toFirebaseAppError()) }

    override suspend fun activateChecked(context: PurchaseContext, replaceExisting: Boolean): Result<GroupSponsorship> {
        if (FirebaseAuth.getInstance().currentUser?.uid != context.payerUid) {
            return Result.failure(AppError.SessionExpired())
        }
        return activatePayload(context.groupId, mapOf("groupId" to context.groupId,
            "replaceExisting" to replaceExisting, "expectedSponsoredGroupId" to context.sponsoredGroupId,
            "expectedPayerUid" to context.payerUid))
    }

    override suspend fun activate(
        groupId: String,
        replaceExisting: Boolean,
    ): Result<GroupSponsorship> {
        return activatePayload(groupId, mapOf("groupId" to groupId, "replaceExisting" to replaceExisting))
    }

    private suspend fun activatePayload(groupId: String, data: Map<String, Any?>): Result<GroupSponsorship> {
        return try {
            val payload = functions
                .getHttpsCallable(ACTIVATE_SPONSORSHIP_FUNCTION)
                .call(data)
                .await()
                .data
            Result.success(parseSponsorship(payload, expectedGroupId = groupId))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: FirebaseFunctionsException) {
            Result.failure(error.toSponsorshipAppError())
        } catch (error: Exception) {
            Result.failure(error.toFirebaseAppError())
        }
    }

    private companion object {
        const val ACTIVATE_SPONSORSHIP_FUNCTION = "activateCircleSponsorship"
    }
}

/**
 * Treats the callable response as untrusted network data.
 *
 * Success is accepted only when the backend confirms the same group, Premium
 * is true and the server lease is a positive whole millisecond value.
 */
internal fun parseSponsorship(
    payload: Any?,
    expectedGroupId: String,
): GroupSponsorship {
    val fields = payload as? Map<*, *>
        ?: throw AppError.Unknown("Malformed sponsorship response")
    val groupId = fields["groupId"] as? String
        ?: throw AppError.Unknown("Sponsorship response has no group ID")
    if (groupId != expectedGroupId) {
        throw AppError.Unknown("Sponsorship response named a different group")
    }
    if (fields["isPremium"] != true) {
        throw AppError.Unknown("Sponsorship response did not confirm access")
    }

    val rawLease = fields["leaseValidUntilMs"] as? Number
        ?: throw AppError.Unknown("Sponsorship response has no access lease")
    val leaseAsDouble = rawLease.toDouble()
    val leaseValidUntilMillis = rawLease.toLong()
    if (
        !leaseAsDouble.isFinite() ||
        leaseAsDouble % 1.0 != 0.0 ||
        leaseValidUntilMillis <= 0L ||
        leaseValidUntilMillis.toDouble() != leaseAsDouble
    ) {
        throw AppError.Unknown("Sponsorship response has an invalid access lease")
    }

    return GroupSponsorship(
        groupId = groupId,
        leaseValidUntilMillis = leaseValidUntilMillis,
    )
}

private fun FirebaseFunctionsException.toSponsorshipAppError(): AppError {
    val reason = (details as? Map<*, *>)?.get("reason") as? String
    return mapSponsorshipFunctionError(
        statusName = code.name,
        wireReason = reason,
        detail = message ?: "Sponsorship function failed",
        cause = this,
    )
}

/** Maps status plus stable server reason; never branches on human-readable copy. */
internal fun mapSponsorshipFunctionError(
    statusName: String,
    wireReason: String?,
    detail: String,
    cause: Throwable? = null,
): AppError {
    val knownFailure = when (statusName to wireReason) {
        "INVALID_ARGUMENT" to "invalid_group" ->
            SponsorshipFailure.InvalidGroup

        "NOT_FOUND" to "group_unavailable" ->
            SponsorshipFailure.GroupUnavailable

        "PERMISSION_DENIED" to "not_current_member" ->
            SponsorshipFailure.NotCurrentMember

        "FAILED_PRECONDITION" to "no_active_subscription" ->
            SponsorshipFailure.NoActiveSubscription

        "FAILED_PRECONDITION" to "subscription_assigned_elsewhere" ->
            SponsorshipFailure.SubscriptionAssignedElsewhere

        "FAILED_PRECONDITION" to "sponsorship_changed" ->
            SponsorshipFailure.AssignmentChanged

        "ALREADY_EXISTS" to "group_already_sponsored" ->
            SponsorshipFailure.GroupAlreadySponsored

        else -> null
    }
    if (knownFailure != null) {
        return AppError.Sponsorship(knownFailure, detail, cause)
    }

    return when (statusName) {
        "UNAUTHENTICATED" ->
            AppError.SessionExpired(detail, cause)

        "PERMISSION_DENIED" ->
            AppError.PermissionDenied(detail, cause)

        "NOT_FOUND" ->
            AppError.NotFound(detail, cause)

        "ALREADY_EXISTS",
        "FAILED_PRECONDITION",
        "ABORTED" ->
            AppError.Conflict(detail, cause)

        "DEADLINE_EXCEEDED",
        "UNAVAILABLE",
        "CANCELLED" ->
            AppError.Network(detail, cause)

        "RESOURCE_EXHAUSTED" ->
            AppError.RateLimited(detail, cause)

        else -> AppError.Unknown(detail, cause)
    }
}
