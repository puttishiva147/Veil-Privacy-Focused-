package com.veil.privacy.trc.model

/**
 * Structural validation for untrusted ciphertext envelopes.
 *
 * This is not cryptographic authentication. The E2EE adapter must authenticate the
 * canonical envelope metadata as associated data before the payload is accepted.
 */
object TrcEnvelopeValidator {
    const val MAX_MESSAGE_TTL_MILLIS: Long = 24L * 60L * 60L * 1000L
    const val MAX_CIPHERTEXT_BYTES: Int = 64 * 1024
    private const val MAX_IDENTIFIER_LENGTH = 128

    enum class Error {
        EMPTY_MESSAGE_ID,
        INVALID_PEER_ID,
        SELF_RECIPIENT,
        INVALID_TIMESTAMP,
        INVALID_EXPIRY,
        TTL_TOO_LONG,
        EMPTY_CIPHERTEXT,
        CIPHERTEXT_TOO_LARGE
    }

    fun validate(envelope: TrcCiphertextEnvelope): Error? {
        if (envelope.messageId.isBlank() || envelope.messageId.length > MAX_IDENTIFIER_LENGTH) {
            return Error.EMPTY_MESSAGE_ID
        }
        if (!isValidPeerId(envelope.sender) || !isValidPeerId(envelope.recipient)) {
            return Error.INVALID_PEER_ID
        }
        if (envelope.sender == envelope.recipient) return Error.SELF_RECIPIENT
        if (envelope.createdAtEpochMillis <= 0L) return Error.INVALID_TIMESTAMP
        if (envelope.expiresAtEpochMillis <= envelope.createdAtEpochMillis) {
            return Error.INVALID_EXPIRY
        }
        val ttl = envelope.expiresAtEpochMillis - envelope.createdAtEpochMillis
        if (ttl > MAX_MESSAGE_TTL_MILLIS) return Error.TTL_TOO_LONG
        if (envelope.ciphertext.isEmpty()) return Error.EMPTY_CIPHERTEXT
        if (envelope.ciphertext.size > MAX_CIPHERTEXT_BYTES) return Error.CIPHERTEXT_TOO_LARGE
        return null
    }

    private fun isValidPeerId(peer: TrcPeerId): Boolean =
        peer.value.isNotBlank() && peer.value.length <= MAX_IDENTIFIER_LENGTH
}
