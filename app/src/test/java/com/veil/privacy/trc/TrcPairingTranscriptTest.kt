package com.veil.privacy.trc

import com.veil.privacy.trc.pairing.TrcPairingTranscript
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrcPairingTranscriptTest {
    private fun transcript(
        peer: String = "peer-a",
        session: String = "session-123456789",
        nonce: ByteArray = ByteArray(32) { it.toByte() },
        identity: ByteArray = byteArrayOf(1, 2, 3),
        ephemeral: ByteArray = byteArrayOf(4, 5, 6),
        expires: Long = 120_000L
    ) = TrcPairingTranscript.encode(peer, session, nonce, identity, ephemeral, expires)

    @Test fun encodingIsDeterministic() {
        assertArrayEquals(transcript(), transcript())
    }

    @Test fun transcriptBindsPeerIdentity() {
        assertFalse(transcript().contentEquals(transcript(peer = "peer-b")))
    }

    @Test fun transcriptBindsEphemeralKeyAndNonce() {
        assertFalse(transcript().contentEquals(transcript(ephemeral = byteArrayOf(7, 8, 9))))
        assertFalse(transcript().contentEquals(transcript(nonce = ByteArray(32) { (it + 1).toByte() })))
    }

    @Test fun transcriptBindsExpiry() {
        assertFalse(transcript().contentEquals(transcript(expires = 120_001L)))
    }

    @Test fun transcriptContainsDomainSeparationPrefix() {
        val bytes = transcript()
        val prefix = "VEIL-TRC-PAIRING-V1".toByteArray(Charsets.US_ASCII)
        assertTrue(bytes.copyOfRange(0, prefix.size).contentEquals(prefix))
    }
}
