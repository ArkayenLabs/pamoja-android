package com.pamoja.app

import com.google.firebase.appcheck.FirebaseAppCheck

/** Offline preview never initializes Firebase; satisfies main source linkage. */
fun FirebaseAppCheck.installPamojaProvider() = Unit
