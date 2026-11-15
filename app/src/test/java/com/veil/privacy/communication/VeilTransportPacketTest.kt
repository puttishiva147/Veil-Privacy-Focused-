package com.veil.privacy.communication

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VeilTransportPacketTest {
    @Test
    fun packetRoundTripsWithNotificationTextAroundIt() {
        val packet = VeilTransportPacket.encode(
            messageId = "msg-123",
            dictionaryVersion = 7,
            encodedPayload = "⟁mix-9-token"
        )

        val decoded = VeilTransportPacket.extract("A sender: $packet (tap to reply)")

        assertEquals("msg-123", decoded?.messageId)
        assertEquals(7, decoded?.dictionaryVersion)
        assertEquals("⟁mix-9-token", decoded?.encodedPayload)
    }

    @Test
    fun packetRejectsTamperedPayload() {
        val packet = VeilTransportPacket.encode("msg-456", 2, "encoded-token")
        val index = packet.indexOf(':') + 1
        val replacement = if (packet[index] == 'A') 'B' else 'A'
        val changed = packet.substring(0, index) + replacement + packet.substring(index + 1)

        assertNull(VeilTransportPacket.decode(changed))
    }

    @Test
    fun packetRejectsNonVeilText() {
        assertNull(VeilTransportPacket.extract("ordinary WhatsApp message"))
    }
}
