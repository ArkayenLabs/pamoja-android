package com.pamoja.app.data.remote.firebase

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pamoja.app.domain.model.StepGoal
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * Pins the shared-goal contract against the real Firestore emulator.
 *
 * A group owns one pooled weekly target. Member count is capacity only: it must
 * never multiply the target or create an individual quota. Presets are quick
 * choices, while deliberate custom totals support differently sized groups.
 * Only the organizer may move the official group promise.
 */
@RunWith(AndroidJUnit4::class)
class GroupGoalRulesEmulatorTest {

    private val clients = mutableListOf<FirebaseEmulator.Client>()

    @Before
    fun wipe() = runBlocking { FirebaseEmulator.reset() }

    @After
    fun closeClients() {
        clients.forEach { it.close() }
        clients.clear()
    }

    private suspend fun newUser(): FirebaseEmulator.Client =
        FirebaseEmulator.signIn().also { clients += it }

    private suspend fun createGroup(
        admin: FirebaseEmulator.Client,
        weeklyTarget: Int = StepGoal.DEFAULT_WEEKLY_TOTAL,
        dailyPerPerson: Int = 0,
        cap: Int = StepGoal.MAX_GROUP_MEMBERS,
        canMembersEditTarget: Boolean = false,
    ): String {
        val groupId = UUID.randomUUID().toString()
        admin.firestore.collection("groups").document(groupId).set(
            mapOf(
                "groupId" to groupId,
                "name" to "Shared goal group",
                "adminId" to admin.uid,
                "weeklyTarget" to weeklyTarget,
                "dailyPerPersonTarget" to dailyPerPerson,
                "maxMemberCap" to cap,
                "memberCount" to 1,
                "canMembersEditTarget" to canMembersEditTarget,
                "inviteLink" to "pamoja://join/$groupId",
                "inviteLinkActive" to true,
                "createdAt" to System.currentTimeMillis(),
                "weekStartDay" to "MONDAY",
            ),
        ).await()
        return groupId
    }

    @Test
    fun everySupportedSharedGoalCanBeCreatedWithoutAPerPersonQuota() = runBlocking {
        val admin = newUser()

        StepGoal.PRESETS_WEEKLY_TOTAL.forEach { target ->
            val groupId = createGroup(admin, weeklyTarget = target)
            val stored = admin.firestore.collection("groups").document(groupId).get().await()

            assertEquals(target.toLong(), stored.getLong("weeklyTarget"))
            assertEquals(0L, stored.getLong("dailyPerPersonTarget"))
        }
    }

    @Test
    fun aCustomSharedGoalCanBeCreated() = runBlocking {
        val admin = newUser()

        val groupId = createGroup(admin, weeklyTarget = 300_000)
        val stored = admin.firestore.collection("groups").document(groupId).get().await()

        assertEquals(300_000L, stored.getLong("weeklyTarget"))
    }

    @Test
    fun aGoalBelowTheProductMinimumCannotBeCreated() = runBlocking {
        val admin = newUser()

        val failed = runCatching { createGroup(admin, weeklyTarget = 9_999) }.isFailure

        assertTrue("A meaningless group total must be rejected", failed)
    }

    @Test
    fun aNewGroupCannotRestoreTheRetiredPerPersonQuota() = runBlocking {
        val admin = newUser()

        val failed = runCatching {
            createGroup(admin, dailyPerPerson = 8_000)
        }.isFailure

        assertTrue("New groups must not store an individual daily quota", failed)
    }

    @Test
    fun aNewGroupCannotGiveEveryMemberGoalEditingAccess() = runBlocking {
        val admin = newUser()

        val failed = runCatching {
            createGroup(admin, canMembersEditTarget = true)
        }.isFailure

        assertTrue("Only the organizer controls the official group goal", failed)
    }

    @Test
    fun changingTheMemberCapDoesNotChangeTheSharedGoal() = runBlocking {
        val admin = newUser()
        val groupId = createGroup(admin, weeklyTarget = 70_000, cap = 6)

        admin.firestore.collection("groups").document(groupId).update(
            mapOf(
                "name" to "Bigger family",
                "weeklyTarget" to 70_000,
                "dailyPerPersonTarget" to 0,
                "maxMemberCap" to 12,
                "canMembersEditTarget" to false,
                "weekStartDay" to "MONDAY",
            ),
        ).await()

        val stored = admin.firestore.collection("groups").document(groupId).get().await()
        assertEquals(12L, stored.getLong("maxMemberCap"))
        assertEquals(70_000L, stored.getLong("weeklyTarget"))
    }

    @Test
    fun anExistingNonStandardGoalCanBePreservedDuringMigration() = runBlocking {
        val admin = newUser()
        val groupId = UUID.randomUUID().toString()
        FirebaseEmulator.seedServerDocument(
            collection = "groups",
            documentId = groupId,
            fields = mapOf(
                "groupId" to groupId,
                "name" to "Older group",
                "adminId" to admin.uid,
                "weeklyTarget" to 1_120_000,
                "dailyPerPersonTarget" to 8_000,
                "maxMemberCap" to 20,
                "memberCount" to 1,
                "canMembersEditTarget" to false,
                "inviteLinkActive" to true,
                "weekStartDay" to "MONDAY",
            ),
        )

        admin.firestore.collection("groups").document(groupId).update(
            mapOf(
                "name" to "Older group renamed",
                "weeklyTarget" to 1_120_000,
                "dailyPerPersonTarget" to 0,
                "maxMemberCap" to 10,
                "canMembersEditTarget" to false,
                "weekStartDay" to "MONDAY",
            ),
        ).await()

        val stored = admin.firestore.collection("groups").document(groupId).get().await()
        assertEquals(1_120_000L, stored.getLong("weeklyTarget"))
        assertEquals(0L, stored.getLong("dailyPerPersonTarget"))
    }

    @Test
    fun anExistingGoalCanBeChangedToACustomValue() = runBlocking {
        val admin = newUser()
        val groupId = createGroup(admin)

        admin.firestore.collection("groups").document(groupId)
            .update("weeklyTarget", 300_000)
            .await()

        val stored = admin.firestore.collection("groups").document(groupId).get().await()
        assertEquals(300_000L, stored.getLong("weeklyTarget"))
    }

    @Test
    fun aMemberCannotMoveTheGoalEvenOnALegacyPermissionDocument() = runBlocking {
        val admin = newUser()
        val member = newUser()
        val groupId = UUID.randomUUID().toString()
        FirebaseEmulator.seedServerDocument(
            collection = "groups",
            documentId = groupId,
            fields = mapOf(
                "groupId" to groupId,
                "name" to "Legacy shared goal group",
                "adminId" to admin.uid,
                "weeklyTarget" to 70_000,
                "dailyPerPersonTarget" to 0,
                "maxMemberCap" to 6,
                "memberCount" to 2,
                "canMembersEditTarget" to true,
                "inviteLinkActive" to true,
                "weekStartDay" to "MONDAY",
            ),
        )
        FirebaseEmulator.seedServerDocument(
            collection = "memberships",
            documentId = "${member.uid}_$groupId",
            fields = mapOf(
                "membershipId" to "${member.uid}_$groupId",
                "userId" to member.uid,
                "groupId" to groupId,
            ),
        )

        val failed = runCatching {
            member.firestore.collection("groups").document(groupId)
                .update("weeklyTarget", 100_000)
                .await()
        }.isFailure

        assertTrue("A member cannot move the organizer's group promise", failed)
    }

    @Test
    fun aStrangerCannotChangeTheSharedGoal() = runBlocking {
        val admin = newUser()
        val stranger = newUser()
        val groupId = createGroup(admin)

        val failed = runCatching {
            stranger.firestore.collection("groups").document(groupId)
                .update("weeklyTarget", 100_000)
                .await()
        }.isFailure

        assertTrue("An invite holder is not a group member", failed)
    }

    @Test
    fun theAdminCannotForgeTheStepCacheAlongsideTheGoal() = runBlocking {
        val admin = newUser()
        val groupId = createGroup(admin)

        val failed = runCatching {
            admin.firestore.collection("groups").document(groupId).update(
                mapOf(
                    "weeklyTarget" to 100_000,
                    "weeklySteps" to 9_999_999L,
                ),
            ).await()
        }.isFailure

        assertTrue("weeklySteps is not part of goal editing", failed)
    }
}
