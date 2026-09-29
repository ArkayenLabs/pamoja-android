# ─── Pamoja ProGuard Rules ────────────────────────────────────────────────────
# R8 full mode is enabled in release builds for smaller APK size.
# These rules preserve the classes that R8 cannot detect automatically.

# ─── Kotlin ──────────────────────────────────────────────────────────────────
-keep class kotlin.Metadata { *; }
-keepclassmembers class **$WhenMappings { <fields>; }

# ─── Crashlytics ─────────────────────────────────────────────────────────────
# Without these two attributes the uploaded mapping file still cannot produce a
# line number, so every release stack trace stops at the class. This is the
# difference between a readable crash and a useless one.
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*

# Custom exceptions keep their names so they are greppable in the dashboard.
-keep public class * extends java.lang.Exception

# ─── Libraries ───────────────────────────────────────────────────────────────
# Firebase, Play services, Hilt, Health Connect, Play Core and DataStore ship
# their own consumer rules. This file used to blanket-keep all of them, which
# stopped R8 shrinking most of the app (Play Console rated it ~33% on 1.0.12).
# Only what reflects on OUR classes belongs here: the DTOs and Workers below.
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# ─── Firestore data model DTOs (must survive serialization) ──────────────────
-keep class com.pamoja.app.data.remote.model.** { *; }
-keepclassmembers class com.pamoja.app.data.remote.model.** { *; }

# Domain models are never reflected on: every toObject() names a DTO, and the
# DTOs hold only primitives and other DTOs.

# ─── Hilt / Dagger ───────────────────────────────────────────────────────────
-dontwarn dagger.**

# ─── WorkManager ─────────────────────────────────────────────────────────────
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.CoroutineWorker
-keepclassmembers class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# ─── Health Connect ───────────────────────────────────────────────────────────
-dontwarn androidx.health.connect.**

# ─── Coroutines ───────────────────────────────────────────────────────────────
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn kotlinx.coroutines.**

# ─── Compose ─────────────────────────────────────────────────────────────────
-dontwarn androidx.compose.**

# ─── Play In-App Update ───────────────────────────────────────────────────────
-dontwarn com.google.android.play.core.**

# ─── Strip debug logging from release ────────────────────────────────────────
# R8 does NOT remove android.util.Log on its own, and neither does
# proguard-android-optimize.txt, so every Log.d/Log.v/Log.i in this app runs on
# users' devices and its arguments are still built. Removing them here is what
# makes the "never log the value" rule in StepSyncWorker true in production
# rather than only in intent.
#
# w and e survive on purpose: they carry the failures worth diagnosing from a
# bug report, and they are already written to say what failed rather than what
# the data was.
#
# -assumenosideeffects only takes effect when optimization is on, which it is
# for release via proguard-android-optimize.txt.
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

# ─── Suppress common harmless warnings ───────────────────────────────────────
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
-dontwarn com.google.firebase.storage.**
-dontwarn coil.**
