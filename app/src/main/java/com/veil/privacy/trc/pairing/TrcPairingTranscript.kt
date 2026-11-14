package com.veil.privacy.trc.pairing

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.charset.StandardCharsets

/**
 * Canonical, domain-separated bytes that the identity signature must authenticate.
 *
 * This defines serialization only; it does not implement signing, key agreement, or
 * any cryptographic primitive. The selected audited provider supplies those operations.
 */
object TrcPairingTranscript {
    private val domain = "VEIL-TRC-PAIRING-V1".toByteArray(StandardCharsets.US_ASCII)
    private const val VERSION = 1
    private const val MAX_FIELD_BYTES = 2_048

    fun encode(
        peerId: String,
        sessionId: String,
        nonce: ByteArray,
        identityPublicKey: ByteArray,
        ephemeralPublicKey: ByteArray,
        expiresAtEpochMillis: Long
    ): ByteArray {
        require(peerId.isNotBlank() && peerId.length <= 128)
        require(sessionId.isNotBlank() && sessionId.length <= 64)
        require(nonce.size == 32)
        require(identityPublicKey.isNotEmpty() && identityPublicKey.size <= MAX_FIELD_BYTES)
        require(ephemeralPublicKey.isNotEmpty() && ephemeralPublicKey.size <= MAX_FIELD_BYTES)
        require(expiresAtEpochMillis > 0L)

        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { data ->
            data.write(domain)
            data.writeByte(0)
            data.writeInt(VERSION)
            writeLengthPrefixed(data, peerId.toByteArray(StandardCharsets.UTF_8))
            writeLengthPrefixed(data, sessionId.toByteArray(StandardCharsets.US_ASCII))
            writeLengthPrefixed(data, nonce)
            writeLengthPrefixed(data, identityPublicKey)
            writeLengthPrefixed(data, ephemeralPublicKey)
            data.writeLong(expiresAtEpochMillis)
        }
        return output.toByteArray()
    }

    private fun writeLengthPrefixed(data: DataOutputStream, value: ByteArray) {
        require(value.isNotEmpty() && value.size <= MAX_FIELD_BYTES)
        data.writeShort(value.size)
        data.write(value)
    }
}
