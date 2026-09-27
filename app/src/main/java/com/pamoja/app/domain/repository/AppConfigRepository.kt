package com.pamoja.app.domain.repository

import kotlinx.coroutines.flow.Flow

/** Server-set configuration that every installed version reads. */
interface AppConfigRepository {
    /**
     * The lowest versionCode still allowed to run, 0 when none is set.
     *
     * Emits what is already on the phone first, then again if a fresh fetch
     * changed it, so reading it never delays a launch. Never fails: when the
     * server cannot be reached the answer is the last known one, or 0.
     */
    fun minimumSupportedVersionCode(): Flow<Long>
}
