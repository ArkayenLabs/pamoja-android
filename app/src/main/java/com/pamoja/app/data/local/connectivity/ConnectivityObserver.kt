package com.pamoja.app.data.local.connectivity

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whether the device currently has usable internet, as a Flow.
 *
 * Checks NET_CAPABILITY_VALIDATED, not just "a network exists". Without that,
 * captive portals and hotel wifi report as online while nothing can actually
 * reach Firestore, which produces the worst possible state: no offline banner
 * and no working requests.
 *
 * distinctUntilChanged matters because the system fires several callbacks
 * during a single handover between wifi and mobile data, and a banner that
 * flickers three times on every walk out of the house reads as a bug.
 */
@Singleton
open class ConnectivityObserver @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val manager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    /**
     * Open, and the class with it, so a test can supply a connection state.
     * Same reason [com.pamoja.app.data.local.health.HealthConnectReader] is
     * open: the real one reports what the device is actually doing, which a
     * test cannot drive. No behaviour change.
     */
    open val isOnline: Flow<Boolean> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {

            override fun onCapabilitiesChanged(
                network: Network,
                capabilities: NetworkCapabilities,
            ) {
                trySend(capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))
            }

            override fun onLost(network: Network) {
                trySend(false)
            }

            override fun onUnavailable() {
                trySend(false)
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        manager.registerNetworkCallback(request, callback)

        // Emitted immediately so the first frame is not blank. Callbacks only
        // fire on change, so without this a screen opened while already offline
        // would show no banner until the connection changed.
        trySend(currentlyOnline())

        awaitClose { manager.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()

    fun currentlyOnline(): Boolean {
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
