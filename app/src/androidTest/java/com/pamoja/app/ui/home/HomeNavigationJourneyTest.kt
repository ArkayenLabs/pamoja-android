package com.pamoja.app.ui.home

import android.content.ContentValues
import android.graphics.Bitmap
import android.provider.MediaStore
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import com.pamoja.app.MainActivity
import com.pamoja.app.data.local.preferences.UserPreferences
import com.pamoja.app.data.remote.firebase.FirebaseEmulator
import com.pamoja.app.data.remote.firebase.FirebaseGroupRepositoryImpl
import com.pamoja.app.domain.model.Group
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.util.UUID

/**
 * Visual smoke journey for the actual app shell and navigation graph.
 *
 * The outer rule points the production Firebase singletons at local emulators
 * before Hilt creates [MainActivity]. That gives the real Home and Group
 * screens an authenticated, representative group without a debug bypass or a
 * production read. Screenshots are test artifacts; release builds contain
 * neither the seed data nor this class.
 */
@RunWith(AndroidJUnit4::class)
class HomeNavigationJourneyTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val preferences = UserPreferences(context)
    private lateinit var group: Group

    private val localState = object : ExternalResource() {
        override fun before() = runBlocking {
            FirebaseAnalytics.getInstance(context).setAnalyticsCollectionEnabled(false)
            auth.signOut()
            configureFirebaseOnce()
            FirebaseEmulator.reset()

            val created = auth.createUserWithEmailAndPassword(
                "home-journey-${System.nanoTime()}@pamoja.test",
                "test-password-123",
            ).await()
            val userId = requireNotNull(created.user?.uid)
            firestore.collection("users").document(userId).set(
                mapOf(
                    "userId" to userId,
                    "name" to "Journey",
                    "showPhotoInGroups" to false,
                )
            ).await()

            val monday = LocalDate.now()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .toString()
            group = Group(
                groupId = UUID.randomUUID().toString(),
                name = "Journey walkers",
                adminId = userId,
                weeklyTarget = 70_000,
                memberCount = 1,
                weeklySteps = 0,
                weekStart = monday,
                weekStartDay = DayOfWeek.MONDAY.name,
                createdAt = System.currentTimeMillis(),
            )
            val groupRepository = FirebaseGroupRepositoryImpl(firestore)
            groupRepository.createGroup(group).getOrThrow()
            groupRepository.publishMyWeeklySteps(
                groupId = group.groupId,
                userId = userId,
                steps = 42_300,
                weekStart = monday,
                todaySteps = 8_500,
                todayDate = LocalDate.now().toString(),
            ).getOrThrow()
            groupRepository.publishWeeklyTotal(
                groupId = group.groupId,
                weeklySteps = 42_300,
                weekStart = monday,
            ).getOrThrow()

            // Personal dashboard fixture. These are intentionally the user's
            // own daily documents, not the group's cached total: the two cards
            // have different sources in production and the visual journey must
            // exercise both.
            listOf(
                LocalDate.parse(monday) to 8_500L,
                LocalDate.parse(monday).plusDays(1) to 10_200L,
                LocalDate.parse(monday).plusDays(2) to 15_100L,
                LocalDate.now() to 8_500L,
            ).distinctBy { it.first }.forEach { (date, steps) ->
                firestore.collection("steps")
                    .document("${userId}_$date")
                    .set(
                        mapOf(
                            "userId" to userId,
                            "stepCount" to steps,
                            "date" to date.toString(),
                        )
                    )
                    .await()
            }

            preferences.clearAll()
            preferences.saveUserId(userId)
            preferences.saveUserName("Journey")
            preferences.setIntroSeen(true)
            preferences.setOnboarded(true)
            preferences.setNotificationPrimerShown()
            preferences.saveLastSyncTime(System.currentTimeMillis())
        }

        override fun after() {
            runBlocking {
                auth.signOut()
                preferences.clearAll()
                runCatching { FirebaseEmulator.reset() }
            }
        }
    }

    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(localState).around(compose)

    @Test
    fun homeToGroupAndBackUsesTheRealNavigationGraph() {
        compose.waitUntil(timeoutMillis = 15_000) {
            compose.onAllNodesWithText("Journey walkers").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Journey walkers").assertIsDisplayed()
        compose.waitUntil(timeoutMillis = 15_000) {
            compose.onAllNodesWithText("42,300", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onAllNodesWithText("42,300", substring = true)[0].assertIsDisplayed()
        compose.onNodeWithText("Every step counts. Let's go!").assertDoesNotExist()
        compose.onNodeWithText("Pamoja").assertIsDisplayed()
        compose.onNodeWithText("Good morning, Journey").assertDoesNotExist()
        compose.onNodeWithText("Good afternoon, Journey").assertDoesNotExist()
        compose.onNodeWithText("Good evening, Journey").assertDoesNotExist()
        compose.onNodeWithText("Today").assertIsDisplayed()
        // The selected design represents the normal connected state. The test
        // emulator has no Health Connect provider, so dismiss its recovery row
        // before capturing the like-for-like visual target.
        compose.onNodeWithContentDescription("Dismiss").performClick()
        saveScreenshot("home-weekly-dashboard.png")

        compose.onNodeWithText("Groups").performClick()
        compose.waitUntil(timeoutMillis = 15_000) {
            compose.onAllNodesWithText("Groups").fetchSemanticsNodes().size >= 2
        }
        saveScreenshot("groups-trail-dock.png")

        compose.onNodeWithText("Journey walkers").performClick()
        compose.waitUntil(timeoutMillis = 15_000) {
            compose.onAllNodesWithText("Weekly review").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Weekly review").assertIsDisplayed()
        compose.onNodeWithText("42,300").assertIsDisplayed()
        saveScreenshot("group-after-forward-navigation.png")

        pressBack()
        compose.waitUntil(timeoutMillis = 15_000) {
            compose.onAllNodesWithText("Journey walkers").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Journey walkers").assertIsDisplayed()
        compose.waitUntil(timeoutMillis = 15_000) {
            compose.onNodeWithText("42,300 · 60%").isDisplayed()
        }
        compose.onNodeWithText("42,300 · 60%").assertIsDisplayed()
        saveScreenshot("home-after-back-navigation.png")
    }

    @Test
    fun notificationSettingsShowsRealAndroidChannelState() {
        compose.waitUntil(timeoutMillis = 15_000) {
            compose.onAllNodesWithText("Journey walkers").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.waitUntil(timeoutMillis = 15_000) {
            compose.onAllNodesWithText("Notifications").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Notifications").performClick()

        val channelHint = "These are your Android notification settings. Tap one to change it."
        compose.waitUntil(timeoutMillis = 15_000) {
            compose.onAllNodesWithText(channelHint).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(channelHint).assertIsDisplayed()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("On").fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithText("Off").fetchSemanticsNodes().isNotEmpty()
        }
        saveScreenshot("notification-settings-status-rows.png")
    }

    @Test
    fun previewEnabledArtifactShowsPendingReviewAndKeepsCurrentWeek() {
        assumeTrue(
            "This artifact journey requires CIRCLE_PREVIEW_ENABLED=true",
            installedCirclePreviewEnabled(),
        )

        compose.waitUntil(timeoutMillis = 15_000) {
            compose.onAllNodesWithText("Journey walkers").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Journey walkers").performClick()
        compose.waitUntil(timeoutMillis = 15_000) {
            compose.onAllNodesWithText("Weekly review").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Weekly review").performClick()

        val pendingTitle = "Your Circle Preview starts after an active week"
        compose.waitUntil(timeoutMillis = 15_000) {
            compose.onAllNodesWithText(pendingTitle).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(pendingTitle).assertIsDisplayed()
        compose.onNodeWithText(
            "When at least two members record steps in the same completed week, " +
                "everyone in this group gets 14 days of Weekly Review."
        ).assertIsDisplayed()
        saveScreenshot("preview-pending-from-current-apk.png")

        pressBack()
        compose.waitUntil(timeoutMillis = 15_000) {
            compose.onAllNodesWithText("42,300").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("42,300").assertIsDisplayed()
    }

    private fun installedCirclePreviewEnabled(): Boolean = runCatching {
        Class.forName("com.pamoja.app.BuildConfig")
            .getField("CIRCLE_PREVIEW_ENABLED")
            .getBoolean(null)
    }.getOrDefault(false)

    private fun saveScreenshot(name: String) {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/PamojaJourneys")
        }
        val uri = requireNotNull(
            context.contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                values,
            )
        ) { "Could not create MediaStore entry for $name" }
        requireNotNull(context.contentResolver.openOutputStream(uri)).use { output ->
            check(compose.onRoot().captureToImage().asAndroidBitmap().compress(
                Bitmap.CompressFormat.PNG,
                100,
                output,
            )) { "Could not write $name" }
        }
    }

    private fun configureFirebaseOnce() {
        if (firebaseEmulatorsConfigured) return
        synchronized(HomeNavigationJourneyTest::class.java) {
            if (firebaseEmulatorsConfigured) return
            auth.useEmulator(FirebaseEmulator.HOST, FirebaseEmulator.AUTH_PORT)
            firestore.useEmulator(FirebaseEmulator.HOST, FirebaseEmulator.FIRESTORE_PORT)
            firestore.firestoreSettings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
                .build()
            firebaseEmulatorsConfigured = true
        }
    }

    private companion object {
        @Volatile
        var firebaseEmulatorsConfigured = false
    }
}
