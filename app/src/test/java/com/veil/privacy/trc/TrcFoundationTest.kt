package com.veil.privacy.trc

import com.veil.privacy.trc.expiry.TrcExpiryPolicy
import com.veil.privacy.trc.location.TrcLocationFix
import com.veil.privacy.trc.location.TrcLocationPrivacy
import com.veil.privacy.trc.model.TrcTransport
import com.veil.privacy.trc.model.TrcTransportAvailability
import com.veil.privacy.trc.transport.TrcRouteSelector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrcFoundationTest {
    @Test
    fun defaultMessageLifetimeIs24HoursFromSendTime() {
        val sentAt = 1_000L
        assertEquals(sentAt + 86_400_000L, TrcExpiryPolicy.expiryFor(sentAt))
    }

    @Test
    fun distanceEstimateIsPositiveAndCarriesAccuracyUncertainty() {
        val first = TrcLocationFix(19.0760, 72.8777, 8f, 1L)
        val second = TrcLocationFix(19.0770, 72.8777, 12f, 1L)

        val estimate = TrcLocationPrivacy.estimateDistance(first, second)

        assertTrue(estimate.meters > 100.0)
        assertTrue(estimate.meters < 120.0)
        assertEquals(20.0, estimate.estimatedUncertaintyMeters, 0.001)
    }

    @Test
    fun coarseLocationRoundsCoordinatesBeforeSharing() {
        val fix = TrcLocationFix(19.076012, 72.877712, 10f, 1L)

        val coarse = TrcLocationPrivacy.coarseForMutualSharing(fix)

        assertEquals(19.076, coarse.latitude, 0.000001)
        assertEquals(72.878, coarse.longitude, 0.000001)
    }

    @Test
    fun routeSelectorDoesNotInventReachability() {
        assertNull(TrcRouteSelector.select(TrcTransportAvailability()))
    }

    @Test
    fun routeSelectorPrefersDirectWifiWhenReachable() {
        val route = TrcRouteSelector.select(
            TrcTransportAvailability(
                bluetoothLePeerReachable = true,
                wifiDirectPeerReachable = true,
                internetAvailable = true
            )
        )

        assertEquals(TrcTransport.WIFI_DIRECT, route)
    }
}
