package com.pamoja.app.data.remote.firebase

import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.pamoja.app.domain.repository.AppConfigRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/**
 * Remote Config rather than a Firestore document: it needs no rules change
 * to be readable before sign-in, caches on the phone, and throttles its own
 * fetches. With the parameter unset the value is 0 and nothing is gated.
 *
 * To retire old versions, set `min_supported_version_code` in the Firebase
 * console. Only raise it once the release that replaces them is at 100% on
 * Play: the gate sends people to the store, and a staged rollout can leave
 * someone with no update to install.
 */
class FirebaseAppConfigRepositoryImpl @Inject constructor(
    private val remoteConfig: FirebaseRemoteConfig,
) : AppConfigRepository {

    override fun minimumSupportedVersionCode(): Flow<Long> = flow {
        // Whatever the last fetch left on the phone, activated first so the
        // launch never waits on the network.
        quietly { remoteConfig.activate().await() }
        emit(remoteConfig.getLong(KEY_MIN_VERSION_CODE))

        if (quietly { remoteConfig.fetchAndActivate().await() } != null) {
            emit(remoteConfig.getLong(KEY_MIN_VERSION_CODE))
        }
    }.distinctUntilChanged()

    /** Null on failure. Never swallows cancellation. */
    private suspend fun <T> quietly(block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    private companion object {
        const val KEY_MIN_VERSION_CODE = "min_supported_version_code"
    }
}
