import java.util.Properties
import java.io.File
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
    // Classpath-applied, see the buildscript block in the root build file.
    id("com.google.android.gms.oss-licenses-plugin")
    kotlin("kapt")
}

// ── Load signing credentials from keystore.properties (never committed to Git) ──
val keystorePropertiesFile = rootProject.file(
    providers.gradleProperty("pamojaSigningProperties").orNull ?: "keystore.properties",
)
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) load(keystorePropertiesFile.inputStream())
}

// ── RevenueCat public SDK key, from local.properties (never committed) ──
//
// Defaults to empty on purpose. There is no key yet: it cannot exist until the
// Play merchant chain finishes, and a build that failed without one would block
// every developer and CI run for a reason unrelated to what they are building.
//
// Empty means the SDK is never configured, and RevenueCatSubscriptionRepository
// reports Free with nothing for sale, which is exactly the behaviour the app
// has today. Nothing silently grants Premium in its absence.
//
// This is RevenueCat's *public* key, which is designed to ship inside the APK.
// It is kept out of Git anyway, because a key in source is a key that gets
// copied into the wrong project and is awkward to rotate.
val localPropertiesFile = rootProject.file(
    providers.gradleProperty("pamojaBillingProperties").orNull ?: "local.properties",
)
val localProperties = Properties().apply {
    if (localPropertiesFile.exists()) load(localPropertiesFile.inputStream())
}
val revenueCatApiKey: String =
    (localProperties["revenueCatApiKey"] as String?)?.trim().orEmpty()
val subscriptionSalesEnabled: Boolean =
    (localProperties["subscriptionSalesEnabled"] as String?)
        ?.trim()
        ?.equals("true", ignoreCase = true)
        ?: false
val circlePreviewEnabled: Boolean =
    (localProperties["circlePreviewEnabled"] as String?)
        ?.trim()
        ?.equals("true", ignoreCase = true)
        ?: false

android {
    namespace = "com.pamoja.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.arkayenlabs.pamoja"
        minSdk = 26
        targetSdk = 36
        // Closed testing and the first upload have consumed earlier codes, and
        // Play refuses an upload that reuses one. Raise this again if the
        // Console says the code is taken; it only ever goes up.
        versionCode = providers.gradleProperty("pamojaVersionCode").orNull?.let {
            requireNotNull(it.toIntOrNull()?.takeIf { code -> code > 7 }) {
                "pamojaVersionCode must be an integer above 7, verified unused in Play Console"
            }
        } ?: 7

        // Three-part on purpose. The Settings screen used to print a hardcoded
        // "1.0.0" next to a versionName of "1.0", so the two disagreed; that
        // screen now reads BuildConfig.VERSION_NAME, and this is the value it
        // shows.
        versionName = providers.gradleProperty("pamojaVersionName").orNull ?: "1.0.4"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "REVENUECAT_API_KEY", "\"$revenueCatApiKey\"")
        buildConfigField("boolean", "TOGETHER_TRAIL_ENABLED",
            (providers.gradleProperty("togetherTrailEnabled").orNull == "true").toString())
        // Enable only after compatible planning rules and finalizer are deployed.
        buildConfigField(
            "boolean", "DAY_ONE_PLANNING_ENABLED",
            (providers.gradleProperty("dayOnePlanningEnabled").orNull == "true").toString(),
        )
        // Independent from the SDK key on purpose. A key is configuration; it
        // is not permission to expose products or accept real purchases.
        buildConfigField(
            "boolean",
            "SUBSCRIPTION_SALES_ENABLED",
            subscriptionSalesEnabled.toString(),
        )
        // Coordinated rollout guard. Keep false until the weekly finalizer and
        // preview-aware Firestore rules are both live and verified.
        buildConfigField(
            "boolean",
            "CIRCLE_PREVIEW_ENABLED",
            circlePreviewEnabled.toString(),
        )
    }

    // Only created when keystore.properties is present. Without this guard the
    // unsafe `as String` cast on a null throws while the android block is still
    // configuring, so EVERY task fails, including assembleDebug and test, which
    // need no signing at all. That broke CI and any fresh clone of this repo.
    // Same reasoning already applied to revenueCatApiKey above.
    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                // Relative paths in an external signing file retain the original
                // app project's base directory, rather than this worktree's.
                val configuredStore = File(keystoreProperties["storeFile"] as String)
                storeFile = if (configuredStore.isAbsolute) configuredStore else
                    File(keystorePropertiesFile.parentFile.resolve("app"), configuredStore.path)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias      = keystoreProperties["keyAlias"] as String
                keyPassword   = keystoreProperties["keyPassword"] as String
            }
        }
    }

    buildTypes {
        release {
            // Null where keystore.properties is absent, which yields an unsigned
            // release build rather than a failed configuration. A signed AAB
            // still requires the file, exactly as before.
            signingConfig   = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            // Debug keeps minify off for faster builds and readable stack traces
            isMinifyEnabled = false
        }
        create("preview") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".preview"
            matchingFallbacks += "debug"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    // All four picker choices must be available even when installed in another language.
    bundle { language { enableSplit = false } }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.1")
    // Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Hilt
    implementation(libs.hilt.android)
    kapt(libs.hilt.android.compiler)
    implementation(libs.hilt.navigation.compose)

    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.appcheck.playintegrity)
    debugImplementation(libs.firebase.appcheck.debug)
    implementation(libs.firebase.storage)
    implementation(libs.firebase.functions)

    // Health Connect
    implementation(libs.health.connect)

    // WorkManager
    implementation(libs.work.runtime.ktx)
    implementation(libs.hilt.work)
    kapt(libs.hilt.compiler)

    // DataStore
    implementation(libs.datastore.preferences)

    // Lifecycle ViewModel Compose
    implementation(libs.lifecycle.viewmodel.compose)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Google Sign-In through Credential Manager
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.googleid)

    // Avatars
    implementation(libs.coil.compose)

    // QR, for joining a group in person
    implementation(libs.zxing.core)
    implementation(libs.play.services.code.scanner)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui.text.google.fonts)

    // Billing. Pulls Play Billing in transitively; do not add it separately or
    // the two versions fight.
    implementation(libs.revenuecat.purchases)



    // Play In-App Update (seamless updates during closed testing)
    implementation("com.google.android.play:app-update-ktx:2.1.0")

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.work.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

kapt {
    correctErrorTypes = true
}

// Offline device preview has no Firebase project or network access.
tasks.matching { it.name == "processPreviewGoogleServices" }.configureEach { enabled = false }
