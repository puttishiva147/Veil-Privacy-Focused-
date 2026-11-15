package com.veil.privacy.trc

import com.veil.privacy.trc.delivery.TrcDeliveryState
import com.veil.privacy.trc.delivery.TrcDeliveryTransitions
import com.veil.privacy.trc.model.TrcCiphertextEnvelope
import com.veil.privacy.trc.model.TrcEnvelopeValidator
import com.veil.privacy.trc.model.TrcPayloadType
import com.veil.privacy.trc.model.TrcPeerId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrcMessageBoundaryTest {
    private fun envelope(
        createdAt: Long = 1_000L,
        expiresAt: Long = createdAt + TrcEnvelopeValidator.MAX_MESSAGE_TTL_MILLIS,
        ciphertext: ByteArray = byteArrayOf(1, 2, 3),
        sender: String = "peer-a",
        recipient: String = "peer-b"
    ) = TrcCiphertextEnvelope(
        messageId = "message-1",
        sender = TrcPeerId(sender),
        recipient = TrcPeerId(recipient),
        createdAtEpochMillis = createdAt,
        expiresAtEpochMillis = expiresAt,
        payloadType = TrcPayloadType.TEXT,
        ciphertext = ciphertext
    )

    @Test fun acceptsWellFormedCiphertextEnvelope() {
        assertNull(TrcEnvelopeValidator.validate(envelope()))
    }

    @Test fun rejectsSelfAddressedEnvelope() {
        assertEquals(TrcEnvelopeValidator.Error.SELF_RECIPIENT,
            TrcEnvelopeValidator.validate(envelope(sender = "same", recipient = "same")))
    }

    @Test fun rejectsExpiryBeforeCreation() {
        assertEquals(TrcEnvelopeValidator.Error.INVALID_EXPIRY,
            TrcEnvelopeValidator.validate(envelope(expiresAt = 999L)))
    }

    @Test fun rejectsTtlLongerThanTwentyFourHours() {
        assertEquals(TrcEnvelopeValidator.Error.TTL_TOO_LONG,
            TrcEnvelopeValidator.validate(envelope(
                expiresAt = 1_000L + TrcEnvelopeValidator.MAX_MESSAGE_TTL_MILLIS + 1L
            )))
    }

    @Test fun rejectsEmptyCiphertext() {
        assertEquals(TrcEnvelopeValidator.Error.EMPTY_CIPHERTEXT,
            TrcEnvelopeValidator.validate(envelope(ciphertext = byteArrayOf())))
    }

    @Test fun rejectsOversizedCiphertextChunk() {
        assertEquals(TrcEnvelopeValidator.Error.CIPHERTEXT_TOO_LARGE,
            TrcEnvelopeValidator.validate(envelope(
                ciphertext = ByteArray(TrcEnvelopeValidator.MAX_CIPHERTEXT_BYTES + 1)
            )))
    }

    @Test fun deliveryRequiresAuthenticatedRecipientAcknowledgement() {
        assertFalse(TrcDeliveryTransitions.canTransition(
            TrcDeliveryState.SENT_TO_TRANSPORT, TrcDeliveryState.DELIVERED
        ))
        assertTrue(TrcDeliveryTransitions.canTransition(
            TrcDeliveryState.SENT_TO_TRANSPORT,
            TrcDeliveryState.DELIVERED,
            authenticatedRecipientAck = true
        ))
    }

    @Test fun deliveredStateIsTerminal() {
        assertFalse(TrcDeliveryTransitions.canTransition(
            TrcDeliveryState.DELIVERED, TrcDeliveryState.QUEUED
        ))
    }

    @Test fun failedMessageCanBeRetriedButExpiredCannot() {
        assertTrue(TrcDeliveryTransitions.canTransition(
            TrcDeliveryState.FAILED, TrcDeliveryState.QUEUED
        ))
        assertFalse(TrcDeliveryTransitions.canTransition(
            TrcDeliveryState.EXPIRED, TrcDeliveryState.QUEUED
        ))
    }
}
