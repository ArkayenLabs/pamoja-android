package com.pamoja.app.data.remote.firebase

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.pamoja.app.domain.error.AppError
import com.pamoja.app.domain.model.GroupAccess
import com.pamoja.app.domain.model.GroupAccessSnapshot
import com.pamoja.app.domain.model.GroupFeature
import com.pamoja.app.domain.model.GroupPreview
import com.pamoja.app.domain.repository.GroupAccessRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads the minimal group access projection and never writes billing state.
 *
 * Firestore listeners first answer from the Android cache. Cached Premium is
 * not authority: both membership and access must be confirmed by a server
 * snapshot before this repository returns it. Metadata changes remain enabled
 * so losing that confirmation moves the app to an unavailable, non-Premium
 * state instead of silently extending a stale lease.
 */
@Singleton
@OptIn(ExperimentalCoroutinesApi::class)
class FirebaseGroupAccessRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
) : GroupAccessRepository {

    override fun observeForCurrentMember(
        groupId: String,
        userId: String,
    ): Flow<Result<GroupAccessSnapshot>> {
        require(groupId.isNotBlank()) { "groupId must not be blank" }
        require(userId.isNotBlank()) { "userId must not be blank" }

        return observeOwnMembership(groupId, userId).flatMapLatest { membership ->
            when (membership) {
                MembershipConfirmation.Current -> observeAccess(groupId)
                MembershipConfirmation.Absent ->
                    flowOf(Result.success(GroupAccessSnapshot()))
                is MembershipConfirmation.Unavailable ->
                    flowOf(Result.failure(membership.error))
            }
        }
    }

    private fun observeOwnMembership(
        groupId: String,
        userId: String,
    ): Flow<MembershipConfirmation> = callbackFlow {
        val registration = firestore
            .collection(MEMBERSHIPS_COLLECTION)
            .document("${userId}_$groupId")
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) {
                    trySend(MembershipConfirmation.Unavailable(error.toFirebaseAppError()))
                    close()
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener

                if (snapshot.metadata.isFromCache) {
                    trySend(MembershipConfirmation.Unavailable(AppError.Offline()))
                } else if (snapshot.exists()) {
                    trySend(MembershipConfirmation.Current)
                } else {
                    trySend(MembershipConfirmation.Absent)
                }
            }
        awaitClose { registration.remove() }
    }

    private fun observeAccess(groupId: String): Flow<Result<GroupAccessSnapshot>> = combine(
        observePaidAccess(groupId),
        observePreview(groupId),
    ) { paidResult, previewResult ->
        val paid = paidResult.getOrElse { return@combine Result.failure(it) }
        val preview = previewResult.getOrElse { return@combine Result.failure(it) }
        Result.success(
            GroupAccessSnapshot(
                premiumAccess = paid,
                preview = preview,
            ),
        )
    }

    private fun observePaidAccess(groupId: String): Flow<Result<GroupAccess?>> = callbackFlow {
        val registration = firestore
            .collection(GROUP_ACCESS_COLLECTION)
            .document(groupId)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) {
                    val mapped = error.toFirebaseAppError()
                    // Removal can revoke this listener before the membership
                    // listener's deletion snapshot arrives. Clear access now.
                    if (mapped is AppError.PermissionDenied) {
                        trySend(Result.success(null))
                    } else {
                        trySend(Result.failure(mapped))
                    }
                    close()
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener

                when {
                    snapshot.metadata.isFromCache ->
                        trySend(Result.failure(AppError.Offline()))

                    !snapshot.exists() ->
                        trySend(Result.success(null))

                    else ->
                        trySend(Result.success(parseGroupAccess(groupId, snapshot.data)))
                }
            }
        awaitClose { registration.remove() }
    }

    private fun observePreview(groupId: String): Flow<Result<GroupPreview?>> = callbackFlow {
        val registration = firestore
            .collection(GROUP_PREVIEWS_COLLECTION)
            .document(groupId)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) {
                    val mapped = error.toFirebaseAppError()
                    if (mapped is AppError.PermissionDenied) {
                        trySend(Result.success(null))
                    } else {
                        trySend(Result.failure(mapped))
                    }
                    close()
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener

                when {
                    snapshot.metadata.isFromCache ->
                        trySend(Result.failure(AppError.Offline()))

                    !snapshot.exists() ->
                        trySend(Result.success(null))

                    else ->
                        trySend(Result.success(parseGroupPreview(groupId, snapshot.data)))
                }
            }
        awaitClose { registration.remove() }
    }

    private sealed interface MembershipConfirmation {
        data object Current : MembershipConfirmation
        data object Absent : MembershipConfirmation
        data class Unavailable(val error: AppError) : MembershipConfirmation
    }

    private companion object {
        const val MEMBERSHIPS_COLLECTION = "memberships"
        const val GROUP_ACCESS_COLLECTION = "groupAccess"
        const val GROUP_PREVIEWS_COLLECTION = "groupPreviews"
    }
}

/** Refuses a reset, overlong or otherwise malformed Preview projection. */
internal fun parseGroupPreview(
    expectedGroupId: String,
    fields: Map<String, Any>?,
): GroupPreview? {
    if (fields == null || (fields["schemaVersion"] != 1L && fields["schemaVersion"] != 1)) {
        return null
    }
    val groupId = fields["groupId"] as? String
    if (groupId != expectedGroupId) return null

    val rawFeatures = fields["featureSet"] as? List<*> ?: return null
    if (rawFeatures.any { it !is String }) return null
    val features = rawFeatures
        .filterIsInstance<String>()
        .mapNotNull(GroupFeature::fromWireName)
        .toSet()
    if (GroupFeature.CircleV1 !in features) return null

    val eligibleWeekStart = fields["eligibleWeekStart"] as? String
        ?: return null
    if (!eligibleWeekStart.isIsoDate()) return null

    val startedAtMillis = (fields["startedAt"] as? Timestamp)
        ?.toSafeEpochMillis()
        ?: return null
    val validUntilMillis = (fields["validUntil"] as? Timestamp)
        ?.toSafeEpochMillis()
        ?: return null
    val duration = validUntilMillis - startedAtMillis
    if (duration <= 0L || duration > MAX_CIRCLE_PREVIEW_DURATION_MILLIS) return null

    return GroupPreview(
        groupId = groupId,
        featureSet = features,
        eligibleWeekStart = eligibleWeekStart,
        startedAtMillis = startedAtMillis,
        validUntilMillis = validUntilMillis,
    )
}

private fun Timestamp.toSafeEpochMillis(): Long? =
    runCatching { toDate().time }.getOrNull()?.takeIf { it > 0L }

private fun String.isIsoDate(): Boolean =
    runCatching { java.time.LocalDate.parse(this).toString() == this }.getOrDefault(false)

private const val MAX_CIRCLE_PREVIEW_DURATION_MILLIS = 14L * 24 * 60 * 60 * 1_000

/** Treats every malformed or unsupported server projection as free. */
internal fun parseGroupAccess(
    groupId: String,
    fields: Map<String, Any>?,
): GroupAccess? {
    if (fields == null || fields["isPremium"] != true) return null

    val rawFeatures = fields["featureSet"] as? List<*> ?: return null
    if (rawFeatures.any { it !is String }) return null
    val features = rawFeatures
        .filterIsInstance<String>()
        .mapNotNull(GroupFeature::fromWireName)
        .toSet()
    if (GroupFeature.CircleV1 !in features) return null

    val validUntil = fields["leaseValidUntil"] as? Timestamp ?: return null
    val validUntilMillis = runCatching { validUntil.toDate().time }.getOrNull()
        ?.takeIf { it > 0L }
        ?: return null

    return GroupAccess(
        groupId = groupId,
        featureSet = features,
        validUntilMillis = validUntilMillis,
    )
}
