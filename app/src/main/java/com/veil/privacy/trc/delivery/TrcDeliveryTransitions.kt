package com.veil.privacy.trc.delivery

/**
 * Volatile delivery lifecycle. Persisting this state is a separate product decision.
 *
 * DELIVERED must only be reached after an authenticated recipient acknowledgement.
 * A transport write/connection success is not proof that the recipient received it.
 */
enum class TrcDeliveryState {
    CREATED,
    QUEUED,
    SENDING,
    SENT_TO_TRANSPORT,
    DELIVERED,
    FAILED,
    EXPIRED
}

object TrcDeliveryTransitions {
    private val allowed = mapOf(
        TrcDeliveryState.CREATED to setOf(TrcDeliveryState.QUEUED, TrcDeliveryState.SENDING, TrcDeliveryState.EXPIRED),
        TrcDeliveryState.QUEUED to setOf(TrcDeliveryState.SENDING, TrcDeliveryState.EXPIRED, TrcDeliveryState.FAILED),
        TrcDeliveryState.SENDING to setOf(TrcDeliveryState.SENT_TO_TRANSPORT, TrcDeliveryState.QUEUED, TrcDeliveryState.FAILED, TrcDeliveryState.EXPIRED),
        TrcDeliveryState.SENT_TO_TRANSPORT to setOf(TrcDeliveryState.DELIVERED, TrcDeliveryState.QUEUED, TrcDeliveryState.FAILED, TrcDeliveryState.EXPIRED),
        TrcDeliveryState.DELIVERED to emptySet(),
        TrcDeliveryState.FAILED to setOf(TrcDeliveryState.QUEUED, TrcDeliveryState.EXPIRED),
        TrcDeliveryState.EXPIRED to emptySet()
    )

    fun canTransition(from: TrcDeliveryState, to: TrcDeliveryState): Boolean =
        to in (allowed[from] ?: emptySet())
}
