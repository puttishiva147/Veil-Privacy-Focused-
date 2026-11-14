package com.veil.privacy.trc.transport

import com.veil.privacy.trc.model.TrcTransport
import com.veil.privacy.trc.model.TrcTransportAvailability

/** Selects only routes already confirmed reachable by a platform adapter. */
object TrcRouteSelector {
    fun select(availability: TrcTransportAvailability): TrcTransport? = when {
        availability.wifiDirectPeerReachable -> TrcTransport.WIFI_DIRECT
        availability.bluetoothLePeerReachable -> TrcTransport.BLUETOOTH_LE
        availability.sameLanPeerReachable -> TrcTransport.LOCAL_WIFI
        availability.meshRouteAvailable -> TrcTransport.MESH_RELAY
        availability.internetAvailable -> TrcTransport.INTERNET_RELAY
        else -> null
    }
}
