package com.pamoja.app.domain.model

/** One Android installation currently registered to receive Pamoja pushes. */
data class PushRegistration(
    val installationId: String,
    val platform: String,
    val appVersion: String,
    /** Epoch millis of the last successful registration refresh, when known. */
    val updatedAt: Long?,
)
