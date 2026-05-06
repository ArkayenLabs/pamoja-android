# ─── Pamoja ProGuard Rules ────────────────────────────────────────────────────
# R8 full mode is enabled in release builds for smaller APK size.
# These rules preserve the classes that R8 cannot detect automatically.

# ─── Kotlin ──────────────────────────────────────────────────────────────────
-keep class kotlin.Metadata { *; }
-keepclassmembers class **$WhenMappings { <fields>; }

# ─── Firebase Auth & Firestore ────────────────────────────────────────────────
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# ─── Firestore data model DTOs (must survive serialization) ──────────────────
-keep class com.pamoja.app.data.remote.model.** { *; }
-keepclassmembers class com.pamoja.app.data.remote.model.** { *; }

# ─── Domain models (used in Firestore toDomain() mappings) ───────────────────
-keep class com.pamoja.app.domain.model.** { *; }

# ─── Hilt / Dagger ───────────────────────────────────────────────────────────
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-dontwarn dagger.**

# ─── WorkManager ─────────────────────────────────────────────────────────────
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.CoroutineWorker
-keepclassmembers class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# ─── Health Connect ───────────────────────────────────────────────────────────
-keep class androidx.health.connect.** { *; }
-dontwarn androidx.health.connect.**

# ─── Coroutines ───────────────────────────────────────────────────────────────
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn kotlinx.coroutines.**

# ─── Compose ─────────────────────────────────────────────────────────────────
-dontwarn androidx.compose.**

# ─── Play In-App Update ───────────────────────────────────────────────────────
-keep class com.google.android.play.core.** { *; }
-dontwarn com.google.android.play.core.**

# ─── DataStore ───────────────────────────────────────────────────────────────
-keep class androidx.datastore.** { *; }

# ─── Suppress common harmless warnings ───────────────────────────────────────
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**