package com.veil.privacy.communication

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.MessageDigest
import java.util.Base64

/**
 * Transport-safe framing for VEIL language tokens.
 *
 * The envelope is framing, not encryption. Confidentiality must be provided by a
 * separate authenticated-encryption layer when that layer is implemented.
 */
data class VeilTransportPacket(
    val messageId: String,
    val dictionaryVersion: Int,
    val encodedPayload: String
) {
    companion object {
        private const val MAGIC = "VEIL-TRANSPORT"
        private const val FORMAT_VERSION = 1
        private const val PREFIX = "VUPL1:"
        private const val MAX_PACKET_BYTES = 48 * 1024

        fun encode(messageId: String, dictionaryVersion: Int, encodedPayload: String): String {
            require(messageId.isNotBlank()) { "Message ID is required" }
            require(dictionaryVersion >= 1) { "Dictionary version must be positive" }
            require(encodedPayload.isNotEmpty()) { "Encoded payload is empty" }

            val bodyBytes = ByteArrayOutputStream().use { bytes ->
                DataOutputStream(bytes).use { out ->
                    out.writeUTF(MAGIC)
                    out.writeInt(FORMAT_VERSION)
                    out.writeUTF(messageId)
                    out.writeInt(dictionaryVersion)
                    out.writeUTF(encodedPayload)
                }
                bytes.toByteArray()
            }
            val digest = MessageDigest.getInstance("SHA-256").digest(bodyBytes)
            val packetBytes = bodyBytes + digest
            require(packetBytes.size <= MAX_PACKET_BYTES) { "VEIL packet exceeds transport size limit" }
            return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(packetBytes)
        }

        /** Extracts one complete VEIL packet from notification text or an app message body. */
        fun extract(text: String): VeilTransportPacket? {
            val match = Regex("VUPL1:([A-Za-z0-9_-]+)").find(text) ?: return null
            return decode(match.value)
        }

        fun decode(packet: String): VeilTransportPacket? {
            if (!packet.startsWith(PREFIX)) return null
            return try {
                val bytes = Base64.getUrlDecoder().decode(packet.removePrefix(PREFIX))
                if (bytes.size < 33 || bytes.size > MAX_PACKET_BYTES) return null
                val body = bytes.copyOfRange(0, bytes.size - 32)
                val suppliedDigest = bytes.copyOfRange(bytes.size - 32, bytes.size)
                val expectedDigest = MessageDigest.getInstance("SHA-256").digest(body)
                if (!MessageDigest.isEqual(suppliedDigest, expectedDigest)) return null

                DataInputStream(ByteArrayInputStream(body)).use { input ->
                    if (input.readUTF() != MAGIC) return null
                    if (input.readInt() != FORMAT_VERSION) return null
                    val id = input.readUTF()
                    val dictionaryVersion = input.readInt()
                    val payload = input.readUTF()
                    if (id.isBlank() || dictionaryVersion < 1 || payload.isEmpty() || input.available() != 0) return null
                    VeilTransportPacket(id, dictionaryVersion, payload)
                }
            } catch (_: Exception) {
                null
            }
        }
    }
}
