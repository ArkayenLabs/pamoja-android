package com.pamoja.app

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.*

/**
 * Smoke test that the instrumentation harness reaches the app under test.
 *
 * It asserted `com.pamoja.app`, the **namespace**, against a package name that
 * is the **applicationId**, `com.arkayenlabs.pamoja`. Those two differ on
 * purpose, so this had failed since the scaffold commit and took
 * `connectedAndroidTest` down with it: the suite was red before a single real
 * test existed, which is exactly how a red suite stops meaning anything.
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @Test
    fun useAppContext() {
        // Context of the app under test.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.arkayenlabs.pamoja", appContext.packageName)
    }
}