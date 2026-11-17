package com.veil.privacy

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import com.veil.privacy.vupl.VuplEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VuplEngineTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun pairedDevicesRoundTripEncryptedMultilingualText() {
        val secret = VuplEngine.createPairSecret()
        assertEquals(64, secret.length)
        val phoneA = VuplEngine.openPair(context, secret)
        val phoneB = VuplEngine.openPair(context, secret)
        val original = "Hello नमस्ते तू कसा आहेस? తెలుగు 🔥 🇮🇳 007"
        val packet = phoneA.encode(original)
        assertEquals(original, phoneB.decode(packet).text)
        val envelope = phoneA.sendMessage(original)
        assertEquals(original, phoneB.receiveMessage(envelope))
        assertNotEquals(original, envelope)
    }

    @Test
    fun wrongPairSecretCannotDecrypt() {
        val phoneA = VuplEngine.openPair(context, VuplEngine.createPairSecret())
        val phoneB = VuplEngine.openPair(context, VuplEngine.createPairSecret())
        val envelope = phoneA.sendMessage("private message")
        assertThrows(Exception::class.java) { phoneB.receiveMessage(envelope) }
    }

    @Test
    fun corruptedPacketIsRejected() {
        val pair = VuplEngine.openPair(context, VuplEngine.createPairSecret())
        val packet = pair.encode("Integrity check")
        assertThrows(IllegalArgumentException::class.java) {
            pair.decode(packet.replace("ck=", "ck=0"))
        }
    }
}
