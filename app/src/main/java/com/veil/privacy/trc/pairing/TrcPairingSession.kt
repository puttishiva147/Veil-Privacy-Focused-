package com.veil.privacy.trc.pairing

/** In-memory pairing lifecycle. Do not persist QR payloads or pending pairing sessions. */
enum class TrcPairingState {
    OFFERED,
    SCANNED,
    IDENTITY_VERIFIED,
    CHANNEL_ESTABLISHED,
    EXPIRED,
    REJECTED
}

class TrcPairingSession(
    val localOffer: TrcPairingOffer,
    private val createdAtEpochMillis: Long = System.currentTimeMillis()
) {
    var state: TrcPairingState = TrcPairingState.OFFERED
        private set

    fun acceptScannedOffer(
        validation: TrcPairingValidation,
        nowEpochMillis: Long = System.currentTimeMillis()
    ): Boolean {
        if (isExpired(nowEpochMillis) || state != TrcPairingState.OFFERED) {
            state = TrcPairingState.EXPIRED
            return false
        }
        if (validation !is TrcPairingValidation.Valid) {
            state = TrcPairingState.REJECTED
            return false
        }
        state = TrcPairingState.IDENTITY_VERIFIED
        return true
    }

    /** Call only after an authenticated E2EE session has actually been established. */
    fun markSecureChannelEstablished(nowEpochMillis: Long = System.currentTimeMillis()): Boolean {
        if (isExpired(nowEpochMillis) || state != TrcPairingState.IDENTITY_VERIFIED) {
            state = TrcPairingState.EXPIRED
            return false
        }
        state = TrcPairingState.CHANNEL_ESTABLISHED
        return true
    }

    private fun isExpired(nowEpochMillis: Long): Boolean =
        nowEpochMillis >= localOffer.expiresAtEpochMillis ||
            nowEpochMillis - createdAtEpochMillis >= 120_000L
}
