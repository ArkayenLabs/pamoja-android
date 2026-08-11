package com.pamoja.app

import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

/**
 * Real attestation, backed by Play Integrity. See the debug source set for the
 * development variant and why this is split by source set rather than by an
 * `if (BuildConfig.DEBUG)`.
 */
fun FirebaseAppCheck.installPamojaProvider() {
    installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance())
}
