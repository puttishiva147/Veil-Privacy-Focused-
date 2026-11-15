package com.veil.privacy.trc.model

/** Storage-agnostic TRC domain types. Never persist plaintext, location fixes, or session keys. */
@JvmInline
value class TrcPeerId(val value: String)

enum class TrcLocationSharingMode { OFF, DISTANCE_ONLY, MUTUAL_LIVE }
enum class TrcTransport { BLUETOOTH_LE, WIFI_DIRECT, LOCAL_WIFI, MESH_RELAY, INTERNET_RELAY }
enum class TrcPayloadType { TEXT, EMOJI, REACTION, GIF_REFERENCE, ENCRYPTED_MEDIA }

/** Ciphertext only: this envelope deliberately has no plaintext field. */
data class TrcCiphertextEnvelope(
    val messageId: String,
    val sender: TrcPeerId,
    val recipient: TrcPeerId,
    val createdAtEpochMillis: Long,
    val expiresAtEpochMillis: Long,
    val payloadType: TrcPayloadType,
    val ciphertext: ByteArray
) {
    fun isExpired(nowEpochMillis: Long = System.currentTimeMillis()): Boolean =
        nowEpochMillis >= expiresAtEpochMillis
}

/** Transport observations; reachability must come from a real platform adapter. */
data class TrcTransportAvailability(
    val bluetoothLePeerReachable: Boolean = false,
    val wifiDirectPeerReachable: Boolean = false,
    val sameLanPeerReachable: Boolean = false,
    val meshRouteAvailable: Boolean = false,
    val internetAvailable: Boolean = false
)
