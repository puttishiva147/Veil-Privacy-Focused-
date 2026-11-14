package com.veil.privacy.trc.pairing

import android.util.Base64
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.SecureRandom

/**
 * QR pairing protocol primitives.
 *
 * This file does not implement cryptography. A reviewed E2EE adapter must provide
 * identity signatures and ephemeral key agreement. Never replace it with custom crypto.
 */
data class TrcPairingIdentity(
    val peerId: String,
    val identityPublicKey: ByteArray,
    val ephemeralPublicKey: ByteArray,
    val signature: ByteArray
)

interface TrcPairingCrypto {
    /** Creates a fresh, single-use ephemeral key agreement key and signs its transcript
     * using the user's established identity key. The implementation must be audited. */
    fun createSignedEphemeralIdentity(sessionId: String, nonce: ByteArray, expiresAtEpochMillis: Long): TrcPairingIdentity

    /** Verifies the peer's identity signature over the canonical pairing transcript. */
    fun verifySignedEphemeralIdentity(
        peerId: String,
        identityPublicKey: ByteArray,
        ephemeralPublicKey: ByteArray,
        sessionId: String,
        nonce: ByteArray,
        expiresAtEpochMillis: Long,
        signature: ByteArray
    ): Boolean
}

data class TrcPairingOffer(
    val peerId: String,
    val sessionId: String,
    val nonce: ByteArray,
    val identityPublicKey: ByteArray,
    val ephemeralPublicKey: ByteArray,
    val expiresAtEpochMillis: Long,
    val signature: ByteArray
)

sealed interface TrcPairingValidation {
    data class Valid(val offer: TrcPairingOffer) : TrcPairingValidation
    data class Invalid(val reason: Reason) : TrcPairingValidation

    enum class Reason {
        MALFORMED,
        UNSUPPORTED_VERSION,
        EXPIRED,
        INVALID_FIELDS,
        INVALID_SIGNATURE
    }
}

object TrcPairingProtocol {
    private const val VERSION = 1
    private const val PREFIX = "veil-trc:"
    private const val OFFER_TTL_MILLIS = 120_000L
    private const val MAX_QR_LENGTH = 4_096
    private const val NONCE_BYTES = 32
    private const val ID_BYTES = 16
    private val random = SecureRandom()

    fun createOffer(
        crypto: TrcPairingCrypto,
        nowEpochMillis: Long = System.currentTimeMillis()
    ): TrcPairingOffer {
        val sessionId = randomBytes(ID_BYTES).toBase64()
        val nonce = randomBytes(NONCE_BYTES)
        val expiresAt = Math.addExact(nowEpochMillis, OFFER_TTL_MILLIS)
        val identity = crypto.createSignedEphemeralIdentity(sessionId, nonce, expiresAt)
        require(identity.peerId.isNotBlank() && identity.peerId.length <= 128)
        require(identity.identityPublicKey.isNotEmpty())
        require(identity.ephemeralPublicKey.isNotEmpty())
        require(identity.signature.isNotEmpty())
        return TrcPairingOffer(
            peerId = identity.peerId,
            sessionId = sessionId,
            nonce = nonce,
            identityPublicKey = identity.identityPublicKey,
            ephemeralPublicKey = identity.ephemeralPublicKey,
            expiresAtEpochMillis = expiresAt,
            signature = identity.signature
        )
    }

    /** QR carries pairing material only: never coordinates, phone numbers, or IPs. */
    fun encodeForQr(offer: TrcPairingOffer): String {
        require(offer.peerId.isNotBlank() && offer.peerId.length <= 128)
        val json = JSONObject()
            .put("v", VERSION)
            .put("peer", offer.peerId)
            .put("sid", offer.sessionId)
            .put("nonce", offer.nonce.toBase64())
            .put("identityKey", offer.identityPublicKey.toBase64())
            .put("ephemeralKey", offer.ephemeralPublicKey.toBase64())
            .put("expires", offer.expiresAtEpochMillis)
            .put("signature", offer.signature.toBase64())
        return PREFIX + json.toString()
    }

    fun validateScannedQr(
        raw: String,
        crypto: TrcPairingCrypto,
        nowEpochMillis: Long = System.currentTimeMillis()
    ): TrcPairingValidation {
        if (raw.length > MAX_QR_LENGTH || !raw.startsWith(PREFIX)) {
            return TrcPairingValidation.Invalid(TrcPairingValidation.Reason.MALFORMED)
        }
        return try {
            val json = JSONObject(raw.removePrefix(PREFIX))
            if (json.optInt("v", -1) != VERSION) {
                return TrcPairingValidation.Invalid(TrcPairingValidation.Reason.UNSUPPORTED_VERSION)
            }
            val peer = json.getString("peer")
            val sessionId = json.getString("sid")
            val nonce = json.getString("nonce").decodeBase64()
            val identityKey = json.getString("identityKey").decodeBase64()
            val ephemeralKey = json.getString("ephemeralKey").decodeBase64()
            val expires = json.getLong("expires")
            val signature = json.getString("signature").decodeBase64()

            if (peer.isBlank() || peer.length > 128 || sessionId.length !in 20..32 ||
                nonce.size != NONCE_BYTES || identityKey.isEmpty() ||
                ephemeralKey.isEmpty() || signature.isEmpty() || expires <= 0L
            ) {
                return TrcPairingValidation.Invalid(TrcPairingValidation.Reason.INVALID_FIELDS)
            }
            if (expires <= nowEpochMillis || expires > nowEpochMillis + OFFER_TTL_MILLIS) {
                return TrcPairingValidation.Invalid(TrcPairingValidation.Reason.EXPIRED)
            }
            if (!crypto.verifySignedEphemeralIdentity(
                    peer, identityKey, ephemeralKey, sessionId, nonce, expires, signature
                )
            ) {
                return TrcPairingValidation.Invalid(TrcPairingValidation.Reason.INVALID_SIGNATURE)
            }
            TrcPairingValidation.Valid(
                TrcPairingOffer(peer, sessionId, nonce, identityKey, ephemeralKey, expires, signature)
            )
        } catch (_: Exception) {
            TrcPairingValidation.Invalid(TrcPairingValidation.Reason.MALFORMED)
        }
    }

    private fun randomBytes(size: Int): ByteArray = ByteArray(size).also(random::nextBytes)

    private fun ByteArray.toBase64(): String =
        Base64.encodeToString(this, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

    private fun String.decodeBase64(): ByteArray =
        Base64.decode(this, Base64.URL_SAFE or Base64.NO_WRAP)
}
