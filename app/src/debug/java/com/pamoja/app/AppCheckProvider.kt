package com.pamoja.app

import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

/**
 * Debug attestation. See the release source set for the shipping variant.
 *
 * This lives in a variant source set because `firebase-appcheck-debug` is a
 * `debugImplementation` dependency, so `DebugAppCheckProviderFactory` is not on
 * the release compile classpath at all. Selecting between the two with an
 * `if (BuildConfig.DEBUG)` looked like it worked but could never compile for
 * release: that check runs at runtime, and the class still has to resolve.
 *
 * The dependency stays debug-only deliberately. The debug provider hands out
 * tokens to anything that asks, so shipping it would quietly turn off the
 * attestation the release build depends on.
 *
 * Prints a token to logcat on first run. Register it under App Check, Apps,
 * Manage debug tokens, before enforcement is switched on.
 */
fun FirebaseAppCheck.installPamojaProvider() {
    installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
}
