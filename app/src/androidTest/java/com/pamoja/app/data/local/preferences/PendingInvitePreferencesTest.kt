package com.pamoja.app.data.local.preferences

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PendingInvitePreferencesTest {

    private lateinit var preferences: UserPreferences

    @Before
    fun resetPreferences() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        preferences = UserPreferences(context)
        preferences.clearAll()
    }

    @Test
    fun codeAndCaptureTimeAreSavedAtomically() = runBlocking {
        preferences.savePendingInviteCode("group-code", savedAt = 123_456L)

        val stored = preferences.pendingInvite.first()

        assertEquals("group-code", stored?.code)
        assertEquals(123_456L, stored?.savedAt)
    }

    @Test
    fun dismissingRemovesBothPartsOfThePendingInvite() = runBlocking {
        preferences.savePendingInviteCode("group-code", savedAt = 123_456L)

        preferences.clearPendingInviteCode()

        assertNull(preferences.pendingInvite.first())
    }
}
