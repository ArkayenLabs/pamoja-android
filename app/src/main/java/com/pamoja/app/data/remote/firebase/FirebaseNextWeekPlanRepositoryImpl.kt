package com.pamoja.app.data.remote.firebase

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.pamoja.app.domain.error.AppError
import com.pamoja.app.domain.model.PlanningWindow
import com.pamoja.app.domain.model.ScheduledGoal
import kotlinx.coroutines.CancellationException
import com.pamoja.app.data.remote.model.NextWeekPlanDto
import com.pamoja.app.data.remote.model.NextWeekPlanResponseDto
import com.pamoja.app.domain.model.GroupWeekSummary
import com.pamoja.app.domain.model.NextWeekPlan
import com.pamoja.app.domain.model.NextWeekPlanChoice
import com.pamoja.app.domain.model.NextWeekPlanResponse
import com.pamoja.app.domain.model.NextWeekPlanStatus
import com.pamoja.app.domain.model.NextWeekResponse
import com.pamoja.app.domain.repository.NextWeekPlanRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class FirebaseNextWeekPlanRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance("asia-south1"),
) : NextWeekPlanRepository {

    override suspend fun planningWindow(groupId: String, timeZone: String): Result<PlanningWindow> = try {
        val data = functions.getHttpsCallable("getNextWeekPlanningWindow")
            .call(mapOf("groupId" to groupId, "timeZone" to timeZone)).await().data as Map<*, *>
        val week = data["weekStart"] as String
        val zone = data["timeZone"] as String
        require(java.time.LocalDate.parse(week).toString() == week)
        java.time.ZoneId.of(zone)
        val remaining = (data["appliesAtMillis"] as Number).toLong() -
            (data["serverNowMillis"] as Number).toLong()
        require(remaining > 0)
        val target = (data["currentTargetSteps"] as Number).toInt()
        require(target in 10_000..2_800_000)
        val startDay = java.time.DayOfWeek.valueOf(data["startDay"] as String).name
        Result.success(PlanningWindow(week, zone, remaining, target, startDay))
    } catch (cancelled: CancellationException) { throw cancelled
    } catch (error: Exception) { Result.failure(error.toPlanningAppError()) }

    override suspend fun saveScheduledPlan(groupId: String, weekStart: String, targetSteps: Int,
        choice: NextWeekPlanChoice, sourceWeekStart: String?, basisTargetSteps: Int): Result<Unit> = try {
        functions.getHttpsCallable("saveNextWeekPlan").call(mapOf(
            "groupId" to groupId, "weekStart" to weekStart, "targetSteps" to targetSteps,
            "choice" to choice.wireName, "sourceWeekStart" to sourceWeekStart,
            "basisTargetSteps" to basisTargetSteps,
        )).await()
        Result.success(Unit)
    } catch (cancelled: CancellationException) { throw cancelled
    } catch (error: Exception) { Result.failure(error.toPlanningAppError()) }

    override fun observeScheduledGoal(groupId: String): Flow<Result<ScheduledGoal?>> = callbackFlow {
        val listener = firestore.collection("groups").document(groupId)
            .collection("planningSummary").document("current").addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) trySend(Result.failure(error.toFirebaseAppError()))
                else trySend(runCatching {
                    // Confirm the current membership on the server before
                    // showing a projection cached by an earlier signed-in user.
                    if (snapshot == null || snapshot.metadata.isFromCache || !snapshot.exists()) null else {
                        val week = requireNotNull(snapshot.getString("weekStart"))
                        require(java.time.LocalDate.parse(week).toString() == week)
                        val target = requireNotNull(snapshot.getLong("targetSteps"))
                        require(target in 10_000..2_800_000)
                        val zone = requireNotNull(snapshot.getString("timeZone"))
                        java.time.ZoneId.of(zone)
                        val status = requireNotNull(snapshot.getString("status"))
                        require(status in setOf("scheduled", "applied", "missed"))
                        ScheduledGoal(week, target.toInt(), zone, status)
                    }
                })
            }
        awaitClose { listener.remove() }
    }

    override fun observePlan(groupId: String, weekStart: String): Flow<Result<NextWeekPlan?>> =
        callbackFlow {
            val listener = planDocument(groupId, weekStart).addSnapshotListener { snapshot, error ->
                when {
                    error != null -> trySend(Result.failure(error.toFirebaseAppError()))
                    snapshot == null || !snapshot.exists() -> trySend(Result.success(null))
                    else -> {
                        val plan = snapshot.toObject(NextWeekPlanDto::class.java)?.toDomain()
                        if (plan == null || plan.groupId != groupId || plan.weekStart != weekStart) {
                            trySend(Result.failure(IllegalStateException("Invalid next-week plan")))
                        } else {
                            trySend(Result.success(plan))
                        }
                    }
                }
            }
            awaitClose { listener.remove() }
        }

    override fun observeMyResponse(
        groupId: String,
        weekStart: String,
        userId: String,
    ): Flow<Result<NextWeekPlanResponse?>> = callbackFlow {
        val listener = planDocument(groupId, weekStart)
            .collection(RESPONSES)
            .document(userId)
            .addSnapshotListener { snapshot, error ->
                when {
                    error != null -> trySend(Result.failure(error.toFirebaseAppError()))
                    snapshot == null || !snapshot.exists() -> trySend(Result.success(null))
                    else -> trySend(Result.success(
                        snapshot.toObject(NextWeekPlanResponseDto::class.java)?.toDomain()
                            ?.takeIf { it.userId == userId },
                    ))
                }
            }
        awaitClose { listener.remove() }
    }

    override suspend fun savePlan(
        groupId: String,
        weekStart: String,
        targetSteps: Int,
        choice: NextWeekPlanChoice,
        source: GroupWeekSummary?,
        userId: String,
    ): Result<Unit> = runCatching {
        val reference = planDocument(groupId, weekStart)
        firestore.runTransaction { transaction ->
            val existing = transaction.get(reference)
            val currentGroup = transaction.get(firestore.collection("groups").document(groupId))
            val currentTarget = requireNotNull(currentGroup.getLong("weeklyTarget"))
            if (source == null) {
                val expected = com.pamoja.app.domain.usecase.targetForChoice(
                    currentTarget.toInt(), choice, targetSteps,
                )
                check(expected == targetSteps) { "The current group goal changed. Reload before planning." }
            }
            val dayOne = source == null ||
                existing.getLong("schemaVersion") == NextWeekPlanDto.DAY_ONE_SCHEMA_VERSION.toLong()
            val mutable = mapOf<String, Any?>(
                "schemaVersion" to if (dayOne) NextWeekPlanDto.DAY_ONE_SCHEMA_VERSION
                    else NextWeekPlanDto.SCHEMA_VERSION,
                "groupId" to groupId,
                "weekStart" to weekStart,
                "targetSteps" to targetSteps,
                "choice" to choice.wireName,
                "sourceWeekStart" to source?.weekStart,
                "basisTotalSteps" to source?.totalSteps,
                "basisTargetSteps" to (source?.targetSteps ?: currentTarget),
                "status" to NextWeekPlanStatus.Scheduled.wireName,
                "updatedAt" to FieldValue.serverTimestamp(),
            )
            if (existing.exists()) {
                transaction.update(reference, mutable)
            } else {
                transaction.set(
                    reference,
                    mutable + mapOf(
                        "createdBy" to userId,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "inCount" to 0,
                        "preferGentlerCount" to 0,
                        "restingCount" to 0,
                    ),
                )
            }
        }.await()
        Unit
    }.mapFailure()

    override suspend fun saveResponse(
        groupId: String,
        weekStart: String,
        userId: String,
        response: NextWeekResponse,
    ): Result<Unit> = runCatching {
        val reference = planDocument(groupId, weekStart).collection(RESPONSES).document(userId)
        firestore.runTransaction { transaction ->
            val existing = transaction.get(reference)
            val mutable = mapOf<String, Any>(
                "schemaVersion" to NextWeekPlanDto.SCHEMA_VERSION,
                "groupId" to groupId,
                "weekStart" to weekStart,
                "userId" to userId,
                "response" to response.wireName,
                "updatedAt" to FieldValue.serverTimestamp(),
            )
            if (existing.exists()) {
                transaction.update(reference, mutable)
            } else {
                transaction.set(
                    reference,
                    mutable + ("createdAt" to FieldValue.serverTimestamp()),
                )
            }
        }.await()
        Unit
    }.mapFailure()

    private fun planDocument(groupId: String, weekStart: String) = firestore
        .collection("groups")
        .document(groupId)
        .collection(PLANS)
        .document(weekStart)

    private companion object {
        const val PLANS = "nextWeekPlans"
        const val RESPONSES = "responses"
    }
}

internal fun Throwable.toPlanningAppError(): AppError {
    if (this is FirebaseFunctionsException && code == FirebaseFunctionsException.Code.FAILED_PRECONDITION) {
        val reason = (details as? Map<*, *>)?.get("reason")
        return AppError.Planning(when (reason) {
            "planning_not_enabled" -> AppError.PlanningReason.NotEnabled
            "organizer_setup_required" -> AppError.PlanningReason.OrganizerSetup
            "premium_required" -> AppError.PlanningReason.PremiumRequired
            "existing_plan_pending" -> AppError.PlanningReason.ExistingPlan
            else -> AppError.PlanningReason.RefreshRequired
        }, this)
    }
    return toFirebaseAppError()
}

private fun <T> Result<T>.mapFailure(): Result<T> = fold(
    onSuccess = { Result.success(it) },
    onFailure = { Result.failure((it as? Exception)?.toFirebaseAppError() ?: it) },
)
