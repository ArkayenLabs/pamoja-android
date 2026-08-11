buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        // No plugin marker is published for this one, so it cannot be declared
        // in the plugins block by id like every other plugin here.
        classpath(libs.oss.licenses.plugin)
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.hilt.android) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
}
