package com.pamoja.app.data.local.health

import androidx.health.connect.client.HealthConnectClient
import org.junit.Assert.assertEquals
import org.junit.Test

class HealthConnectAvailabilityTest {

    @Test
    fun availableIsAvailable() = assertEquals(
        HealthConnectAvailability.Available,
        healthConnectAvailabilityOf(HealthConnectClient.SDK_AVAILABLE),
    )

    /** The case that used to read as "not installed" and send people to install it. */
    @Test
    fun anOutdatedProviderNeedsAnUpdate() = assertEquals(
        HealthConnectAvailability.NeedsUpdate,
        healthConnectAvailabilityOf(HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED),
    )

    @Test
    fun unavailableIsNotInstalled() = assertEquals(
        HealthConnectAvailability.NotInstalled,
        healthConnectAvailabilityOf(HealthConnectClient.SDK_UNAVAILABLE),
    )
}
