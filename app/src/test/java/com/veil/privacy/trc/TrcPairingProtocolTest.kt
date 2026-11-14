package com.veil.privacy.trc

import com.veil.privacy.trc.pairing.TrcPairingCrypto
import com.veil.privacy.trc.pairing.TrcPairingIdentity
import com.veil.privacy.trc.pairing.TrcPairingProtocol
import com.veil.privacy.trc.pairing.TrcPairingReplayGuard
import com.veil.privacy.trc.pairing.TrcPairingValidation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TrcPairingProtocolTest {
    private class TestCrypto : TrcPairingCrypto {
        override fun createSignedEphemeralIdentity(
            sessionId: String,
            nonce: ByteArray,
            expiresAtEpochMillis: Long
        ) = TrcPairingIdentity(
            peerId = "test-peer",
            identityPublicKey = byteArrayOf(1, 2, 3),
            ephemeralPublicKey = byteArrayOf(4, 5, 6),
            signature = byteArrayOf(7, 8, 9)
        )

        override fun verifySignedEphemeralIdentity(
            peerId: String,
            identityPublicKey: ByteArray,
            ephemeralPublicKey: ByteArray,
            sessionId: String,
            nonce: ByteArray,
            expiresAtEpochMillis: Long,
            signature: ByteArray
        ): Boolean = peerId == "test-peer" &&
            identityPublicKey.contentEquals(byteArrayOf(1, 2, 3)) &&
            ephemeralPublicKey.contentEquals(byteArrayOf(4, 5, 6)) &&
            signature.contentEquals(byteArrayOf(7, 8, 9))
    }

    @Test
    fun generatedQrOfferRoundTripsThroughSignatureValidation() {
        val crypto = TestCrypto()
        val offer = TrcPairingProtocol.createOffer(crypto, nowEpochMillis = 10_000L)
        val qr = TrcPairingProtocol.encodeForQr(offer)

        val result = TrcPairingProtocol.validateScannedQr(qr, crypto, TrcPairingReplayGuard(), nowEpochMillis = 10_001L)

        assertTrue(result is TrcPairingValidation.Valid)
        assertEquals("test-peer", (result as TrcPairingValidation.Valid).offer.peerId)
    }

    @Test
    fun expiredOfferIsRejected() {
        val crypto = TestCrypto()
        val offer = TrcPairingProtocol.createOffer(crypto, nowEpochMillis = 10_000L)
        val qr = TrcPairingProtocol.encodeForQr(offer)

        val result = TrcPairingProtocol.validateScannedQr(
            qr, crypto, TrcPairingReplayGuard(), nowEpochMillis = offer.expiresAtEpochMillis
        )

        assertEquals(
            TrcPairingValidation.Reason.EXPIRED,
            (result as TrcPairingValidation.Invalid).reason
        )
    }

    @Test
    fun sameQrOfferCannotBeAcceptedTwiceInOneProcess() {
        val crypto = TestCrypto()
        val guard = TrcPairingReplayGuard()
        val offer = TrcPairingProtocol.createOffer(crypto, nowEpochMillis = 10_000L)
        val qr = TrcPairingProtocol.encodeForQr(offer)

        val first = TrcPairingProtocol.validateScannedQr(qr, crypto, guard, nowEpochMillis = 10_001L)
        val second = TrcPairingProtocol.validateScannedQr(qr, crypto, guard, nowEpochMillis = 10_002L)

        assertTrue(first is TrcPairingValidation.Valid)
        assertEquals(
            TrcPairingValidation.Reason.REPLAYED,
            (second as TrcPairingValidation.Invalid).reason
        )
    }

    @Test
    fun malformedQrIsRejected() {
        val result = TrcPairingProtocol.validateScannedQr("not-a-veil-pairing-code", TestCrypto(), TrcPairingReplayGuard())
        assertEquals(
            TrcPairingValidation.Reason.MALFORMED,
            (result as TrcPairingValidation.Invalid).reason
        )
    }
}
