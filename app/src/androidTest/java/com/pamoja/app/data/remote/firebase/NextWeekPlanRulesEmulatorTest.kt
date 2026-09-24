package com.pamoja.app.data.remote.firebase

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.firestore.FieldValue
import com.pamoja.app.domain.model.Group
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.util.UUID

/** Pins the paid-only, group-scoped write boundary for Next Week Together. */
@RunWith(AndroidJUnit4::class)
class NextWeekPlanRulesEmulatorTest {

    private val clients = mutableListOf<FirebaseEmulator.Client>()

    @Before
    fun wipeEmulators() = FirebaseEmulator.reset()

    @After
    fun closeClients() {
        clients.forEach { it.close() }
        clients.clear()
    }

    private suspend fun newUser(): FirebaseEmulator.Client =
        FirebaseEmulator.signIn().also { clients += it }

    private suspend fun createGroup(admin: FirebaseEmulator.Client): Group {
        val group = Group(
            groupId = UUID.randomUUID().toString(),
            name = "Planning circle",
            adminId = admin.uid,
            weeklyTarget = 70_000,
            maxMemberCap = 10,
            memberCount = 1,
            createdAt = System.currentTimeMillis(),
        )
        FirebaseGroupRepositoryImpl(admin.firestore).createGroup(group).getOrThrow()
        return group
    }

    private fun seedPaidAccess(groupId: String) {
        // circle_v1 alone represents a valid lease issued before this feature.
        FirebaseEmulator.seedServerDocument(
            "groupAccess",
            groupId,
            mapOf(
                "isPremium" to true,
                "featureSet" to listOf("circle_v1"),
                "leaseValidUntil" to Instant.now().plusSeconds(3_600),
            ),
        )
    }

    private fun seedMembership(userId: String, groupId: String) {
        FirebaseEmulator.seedServerDocument(
            "memberships",
            "${userId}_$groupId",
            mapOf("userId" to userId, "groupId" to groupId, "role" to "member"),
        )
    }

    private fun planData(groupId: String, adminId: String) = mapOf(
        "schemaVersion" to 1,
        "groupId" to groupId,
        "weekStart" to NEXT_WEEK,
        "targetSteps" to 70_000,
        "choice" to "repeat",
        "sourceWeekStart" to "2026-09-07",
        "basisTotalSteps" to 58_000L,
        "basisTargetSteps" to 70_000L,
        "status" to "scheduled",
        "createdBy" to adminId,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
        "inCount" to 0,
        "preferGentlerCount" to 0,
        "restingCount" to 0,
    )

    @Test
    fun dayOneRepositorySupportsMemberResponseWithOldCircleLease() = runBlocking {
        val admin = newUser()
        val member = newUser()
        val group = createGroup(admin)
        seedMembership(member.uid, group.groupId)
        seedPaidAccess(group.groupId)
        FirebaseEmulator.seedServerDocument("featureRollouts", "dayOnePlanning", mapOf("enabled" to true))
        seedScheduledPlan(group.groupId, admin.uid)
        val memberRepository = FirebaseNextWeekPlanRepositoryImpl(member.firestore)
        memberRepository.saveResponse(group.groupId, NEXT_WEEK, member.uid,
            com.pamoja.app.domain.model.NextWeekResponse.In).getOrThrow()
        val plan = member.firestore.collection("groups").document(group.groupId)
            .collection("nextWeekPlans").document(NEXT_WEEK).get().await()
        assertEquals(null, plan.get("sourceWeekStart"))
        assertEquals(null, plan.get("basisTotalSteps"))
        assertEquals(70_000L, plan.getLong("basisTargetSteps"))
        assertTrue(memberRepository.savePlan(group.groupId, NEXT_WEEK, 90_000,
            com.pamoja.app.domain.model.NextWeekPlanChoice.Custom, null, member.uid).isFailure)
    }

    @Test
    fun dayOneNeedsRolloutAndPaidLeaseAndPreservesCurrentGoal() = runBlocking {
        val admin = newUser()
        val group = createGroup(admin)
        val plan = admin.firestore.collection("groups").document(group.groupId)
            .collection("nextWeekPlans").document(NEXT_WEEK)
        val dayOne = planData(group.groupId, admin.uid) + mapOf(
            "schemaVersion" to 2, "sourceWeekStart" to null, "basisTotalSteps" to null,
        )
        seedPaidAccess(group.groupId)
        assertTrue(runCatching { plan.set(dayOne).await() }.isFailure)
        FirebaseEmulator.seedServerDocument("featureRollouts", "dayOnePlanning", mapOf("enabled" to true))
        assertTrue(runCatching { plan.set(dayOne).await() }.isFailure)
        seedScheduledPlan(group.groupId, admin.uid)
        assertEquals(70_000L, admin.firestore.collection("groups").document(group.groupId)
            .get().await().getLong("weeklyTarget"))
        assertTrue(runCatching { plan.update("basisTotalSteps", 0L,
            "updatedAt", FieldValue.serverTimestamp()).await() }.isFailure)
        FirebaseEmulator.seedServerDocument("groupAccess", group.groupId, mapOf(
            "isPremium" to false, "featureSet" to listOf("circle_v1"),
            "leaseValidUntil" to Instant.now().minusSeconds(60),
        ))
        assertTrue(runCatching { plan.update("targetSteps", 80_000,
            "updatedAt", FieldValue.serverTimestamp()).await() }.isFailure)
    }

    private fun seedScheduledPlan(groupId: String, adminId: String,
        appliesAt: Instant = Instant.now().plusSeconds(3600)) {
        FirebaseEmulator.seedServerDocument("groupPlanning", groupId, mapOf("timeZone" to "Asia/Kolkata"))
        FirebaseEmulator.seedServerDocumentPath("groups/$groupId/nextWeekPlans/$NEXT_WEEK",
            planData(groupId, adminId) + mapOf(
                "schemaVersion" to 2, "sourceWeekStart" to null, "basisTotalSteps" to null,
                "createdAt" to Instant.now(), "updatedAt" to Instant.now(),
                "appliesAt" to appliesAt, "endsAt" to appliesAt.plusSeconds(604800),
                "timeZone" to "Asia/Kolkata",
            ))
    }

    @Test
    fun expiredMemberCanReadOnlyPromiseAndOldClientCannotOverwriteNewCalendar() = runBlocking {
        val admin = newUser()
        val outsider = newUser()
        val group = createGroup(admin)
        seedPaidAccess(group.groupId)
        seedScheduledPlan(group.groupId, admin.uid)
        val summaryPath = "groups/${group.groupId}/planningSummary/current"
        FirebaseEmulator.seedServerDocumentPath(summaryPath, mapOf(
            "weekStart" to NEXT_WEEK, "targetSteps" to 70_000,
            "timeZone" to "Asia/Kolkata", "status" to "scheduled",
        ))
        val plan = admin.firestore.document("groups/${group.groupId}/nextWeekPlans/$NEXT_WEEK")
        assertTrue(runCatching { admin.firestore.document("groups/${group.groupId}")
            .update("weekStartDay", "SUNDAY").await() }.isFailure)
        assertTrue(runCatching { plan.set(planData(group.groupId, admin.uid)).await() }.isFailure)
        // An old app's new schema-1 plan is also refused, not just a downgrade.
        assertTrue(runCatching { admin.firestore.document("groups/${group.groupId}/nextWeekPlans/2026-09-21")
            .set(planData(group.groupId, admin.uid) + ("weekStart" to "2026-09-21")).await() }.isFailure)
        FirebaseEmulator.seedServerDocument("groupAccess", group.groupId, mapOf(
            "isPremium" to false, "featureSet" to listOf("circle_v1"),
            "leaseValidUntil" to Instant.now().minusSeconds(1),
        ))
        assertEquals(70_000L, admin.firestore.document(summaryPath).get(com.google.firebase.firestore.Source.SERVER).await().getLong("targetSteps"))
        assertTrue(runCatching { plan.get(com.google.firebase.firestore.Source.SERVER).await() }.isFailure)
        assertTrue(runCatching { outsider.firestore.document(summaryPath).get().await() }.isFailure)
        assertTrue(runCatching { admin.firestore.document(summaryPath).update("targetSteps", 90_000).await() }.isFailure)
        // Existing basic group reads remain available after expiry.
        assertEquals(group.name, admin.firestore.document("groups/${group.groupId}").get().await().getString("name"))
    }

    @Test
    fun responsesCloseAtServerBoundaryEvenBeforeSchedulerApplies() = runBlocking {
        val admin = newUser()
        val group = createGroup(admin)
        seedPaidAccess(group.groupId)
        seedScheduledPlan(group.groupId, admin.uid, Instant.now().minusSeconds(1))
        val repository = FirebaseNextWeekPlanRepositoryImpl(admin.firestore, admin.functions)
        assertTrue(repository.saveResponse(group.groupId, NEXT_WEEK, admin.uid,
            com.pamoja.app.domain.model.NextWeekResponse.In).isFailure)
    }

    @Test
    fun planningCalendarFieldsCanOnlyBeAssignedByServer() = runBlocking {
        val admin = newUser()
        val group = createGroup(admin)
        val ref = admin.firestore.document("groups/${group.groupId}")
        assertTrue(runCatching { ref.update("planningTimeZone", "UTC").await() }.isFailure)
        assertTrue(runCatching { ref.update("plannedWeekStart", NEXT_WEEK).await() }.isFailure)
        val forged = group.copy(groupId = UUID.randomUUID().toString(), planningTimeZone = "UTC")
        assertTrue(FirebaseGroupRepositoryImpl(admin.firestore).createGroup(forged).isFailure)
        FirebaseEmulator.seedServerDocumentPath("groups/${group.groupId}",
            ref.get().await().data!! + mapOf("planningTimeZone" to "Asia/Kolkata", "plannedWeekStart" to NEXT_WEEK))
        val reloaded = FirebaseGroupRepositoryImpl(admin.firestore).getGroup(group.groupId).getOrThrow()
        assertEquals("Asia/Kolkata", reloaded.planningTimeZone)
        assertEquals(NEXT_WEEK, reloaded.plannedWeekStart)
        // Existing settings updates used by old apps preserve server metadata.
        ref.update("name", "Updated name").await()
        assertEquals("Asia/Kolkata", ref.get().await().getString("planningTimeZone"))
    }

    @Test
    fun paidAdminCanScheduleAndPaidMemberCanAnswerOwnPoll() = runBlocking {
        val admin = newUser()
        val member = newUser()
        val group = createGroup(admin)
        seedMembership(member.uid, group.groupId)
        seedPaidAccess(group.groupId)
        val plan = admin.firestore.collection("groups").document(group.groupId)
            .collection("nextWeekPlans").document(NEXT_WEEK)
        val memberPlan = member.firestore.collection("groups").document(group.groupId)
            .collection("nextWeekPlans").document(NEXT_WEEK)

        plan.set(planData(group.groupId, admin.uid)).await()
        memberPlan.collection("responses").document(member.uid).set(
            mapOf(
                "schemaVersion" to 1,
                "groupId" to group.groupId,
                "weekStart" to NEXT_WEEK,
                "userId" to member.uid,
                "response" to "prefer_gentler",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
        ).await()

        assertEquals("prefer_gentler", memberPlan.collection("responses")
            .document(member.uid).get().await().getString("response"))
        val individualAnswers = runCatching {
            member.firestore.collection("groups").document(group.groupId)
                .collection("nextWeekPlans").document(NEXT_WEEK)
                .collection("responses").get().await()
        }
        assertTrue(individualAnswers.isFailure)
    }

    @Test
    fun memberCannotChooseTheGroupTargetOrWriteForAnotherMember() = runBlocking {
        val admin = newUser()
        val member = newUser()
        val group = createGroup(admin)
        seedMembership(member.uid, group.groupId)
        seedPaidAccess(group.groupId)
        val plan = admin.firestore.collection("groups").document(group.groupId)
            .collection("nextWeekPlans").document(NEXT_WEEK)
        plan.set(planData(group.groupId, admin.uid)).await()

        val targetWrite = runCatching {
            member.firestore.collection("groups").document(group.groupId)
                .collection("nextWeekPlans").document(NEXT_WEEK)
                .update("targetSteps", 100_000, "updatedAt", FieldValue.serverTimestamp())
                .await()
        }
        val someoneElsesResponse = runCatching {
            member.firestore.collection("groups").document(group.groupId)
                .collection("nextWeekPlans").document(NEXT_WEEK)
                .collection("responses").document(admin.uid)
                .set(mapOf("response" to "in"))
                .await()
        }

        assertTrue(targetWrite.isFailure)
        assertTrue(someoneElsesResponse.isFailure)
    }

    @Test
    fun previewDoesNotGrantPlanningAndClientCannotMarkPlanApplied() = runBlocking {
        val admin = newUser()
        val group = createGroup(admin)
        FirebaseEmulator.seedServerDocument(
            "groupPreviews",
            group.groupId,
            mapOf(
                "schemaVersion" to 1,
                "groupId" to group.groupId,
                "featureSet" to listOf("circle_v1"),
                "eligibleWeekStart" to "2026-09-07",
                "startedAt" to Instant.now().minusSeconds(60),
                "validUntil" to Instant.now().plusSeconds(3_600),
            ),
        )
        val plan = admin.firestore.collection("groups").document(group.groupId)
            .collection("nextWeekPlans").document(NEXT_WEEK)
        val previewWrite = runCatching { plan.set(planData(group.groupId, admin.uid)).await() }
        assertTrue(previewWrite.isFailure)

        seedPaidAccess(group.groupId)
        plan.set(planData(group.groupId, admin.uid)).await()
        val appliedWrite = runCatching {
            plan.update("status", "applied", "updatedAt", FieldValue.serverTimestamp()).await()
        }
        assertTrue(appliedWrite.isFailure)
    }

    private companion object {
        const val NEXT_WEEK = "2026-09-14"
    }
}
