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
 * The rules around the per-person goal, against the real emulator.
 *
 * Two rule changes are under test, and both are written in pairs as this
 * repository requires: one that the legitimate write still works, one that the
 * attacker is still blocked. A rule that denies everything passes the blocked
 * half on its own, and that mistake has been made here before.
 *
 * 1. **The ceiling moved from 1,000,000 to 2,800,000.** This was a correctness
 *    fix rather than a loosening: the recommended default at the premium member
 *    cap is 20 x 8,000 x 7 = **1,120,000**, so the old ceiling rejected the
 *    default. The new figure is `StepGoal.MAX_WEEKLY_TOTAL`, the largest value
 *    the model can produce, and anything above it must still be refused.
 * 2. **`dailyPerPersonTarget` joined the admin settings allowlist.** Without it
 *    every settings save is denied for naming a key the rule does not list.
 *
 * Requires the emulators, see [FirebaseEmulator].
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

    /** A group owned by [admin], written straight in as the create rule allows. */
    private suspend fun createGroup(
        admin: FirebaseEmulator.Client,
        weeklyTarget: Int,
        dailyPerPerson: Int = StepGoal.DEFAULT_DAILY_PER_PERSON,
        cap: Int = StepGoal.MAX_GROUP_MEMBERS,
    ): String {
        val groupId = UUID.randomUUID().toString()
        admin.firestore.collection("groups").document(groupId).set(
            mapOf(
                "groupId" to groupId,
                "name" to "Goal rules group",
                "adminId" to admin.uid,
                "weeklyTarget" to weeklyTarget,
                "dailyPerPersonTarget" to dailyPerPerson,
                "maxMemberCap" to cap,
                "memberCount" to 1,
                "canMembersEditTarget" to false,
                "inviteLink" to "pamoja://join/$groupId",
                "inviteLinkActive" to true,
                "createdAt" to System.currentTimeMillis(),
                "weekStartDay" to "MONDAY",
            )
        ).await()
        return groupId
    }

    // ── The ceiling ─────────────────────────────────────────────────────────

    /**
     * The legitimate half, and the reason the ceiling had to move at all. This
     * exact value was refused before the change.
     */
    @Test
    fun theDefaultGoalAtThePremiumCapCanBeCreated() = runBlocking {
        val admin = newUser()
        val target = StepGoal.weeklyTotalFor(
            StepGoal.DEFAULT_DAILY_PER_PERSON,
            StepGoal.MAX_GROUP_MEMBERS,
        )
        assertEquals("Guards the premise of this test", 1_120_000, target)

        val groupId = createGroup(admin, weeklyTarget = target)

        val stored = admin.firestore.collection("groups").document(groupId).get().await()
        assertEquals(target.toLong(), stored.getLong("weeklyTarget"))
    }

    /** The largest value the model can produce must be storable. */
    @Test
    fun theHighestGoalTheModelCanProduceCanBeCreated() = runBlocking {
        val admin = newUser()

        val groupId = createGroup(admin, weeklyTarget = StepGoal.MAX_WEEKLY_TOTAL)

        val stored = admin.firestore.collection("groups").document(groupId).get().await()
        assertEquals(StepGoal.MAX_WEEKLY_TOTAL.toLong(), stored.getLong("weeklyTarget"))
    }

    /** The blocked half. The ceiling moved, it did not disappear. */
    @Test
    fun aGoalAboveTheCeilingIsStillRefused() = runBlocking {
        val admin = newUser()

        val failed = runCatching {
            createGroup(admin, weeklyTarget = StepGoal.MAX_WEEKLY_TOTAL + 1)
        }.isFailure

        assertTrue("A target above the ceiling must be denied", failed)
    }

    @Test
    fun aZeroGoalIsStillRefused() = runBlocking {
        val admin = newUser()

        val failed = runCatching { createGroup(admin, weeklyTarget = 0) }.isFailure

        assertTrue("A zero target must be denied", failed)
    }

    // ── The new field on the settings allowlist ─────────────────────────────

    /**
     * The legitimate half. Without `dailyPerPersonTarget` on the allowlist the
     * admin settings save is denied outright, so this is the test that would
     * have caught shipping the model without touching the rules.
     */
    @Test
    fun theAdminCanSaveTheNewPerPersonField() = runBlocking {
        val admin = newUser()
        val groupId = createGroup(admin, weeklyTarget = 560_000)

        admin.firestore.collection("groups").document(groupId).update(
            mapOf(
                "name" to "Renamed",
                "weeklyTarget" to StepGoal.weeklyTotalFor(10_000, 20),
                "dailyPerPersonTarget" to 10_000,
                "maxMemberCap" to 20,
                "canMembersEditTarget" to false,
                "weekStartDay" to "MONDAY",
            )
        ).await()

        val stored = admin.firestore.collection("groups").document(groupId).get().await()
        assertEquals(10_000L, stored.getLong("dailyPerPersonTarget"))
        assertEquals(1_400_000L, stored.getLong("weeklyTarget"))
    }

    /** The blocked half: a stranger may not move another group's goal. */
    @Test
    fun aNonAdminCannotChangeThePerPersonField() = runBlocking {
        val admin = newUser()
        val stranger = newUser()
        val groupId = createGroup(admin, weeklyTarget = 560_000)

        val failed = runCatching {
            stranger.firestore.collection("groups").document(groupId)
                .update("dailyPerPersonTarget", 20_000)
                .await()
        }.isFailure

        assertTrue("Only the admin may move the goal", failed)
    }

    /**
     * The admin allowlist must not have become a way to write anything. The
     * step cache is written by every member's own device and must stay off it.
     */
    @Test
    fun theAdminStillCannotForgeTheStepCacheAlongsideTheGoal() = runBlocking {
        val admin = newUser()
        val groupId = createGroup(admin, weeklyTarget = 560_000)

        val failed = runCatching {
            admin.firestore.collection("groups").document(groupId).update(
                mapOf(
                    "dailyPerPersonTarget" to 6_000,
                    "weeklySteps" to 9_999_999L,
                )
            ).await()
        }.isFailure

        assertTrue("weeklySteps is not on the admin allowlist", failed)
    }
}
