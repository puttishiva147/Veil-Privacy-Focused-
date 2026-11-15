package com.veil.privacy.trc.expiry

import com.veil.privacy.trc.model.TrcCiphertextEnvelope

/** Default lifetime is measured from send time, not from when the recipient reads. */
object TrcExpiryPolicy {
    const val DEFAULT_TTL_MILLIS: Long = 24L * 60L * 60L * 1000L

    fun expiryFor(createdAtEpochMillis: Long): Long =
        Math.addExact(createdAtEpochMillis, DEFAULT_TTL_MILLIS)

    fun shouldDiscard(
        envelope: TrcCiphertextEnvelope,
        nowEpochMillis: Long = System.currentTimeMillis()
    ): Boolean = envelope.isExpired(nowEpochMillis)
}
