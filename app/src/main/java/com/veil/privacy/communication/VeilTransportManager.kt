package com.veil.privacy.communication

import android.content.Context
import com.veil.privacy.model.SupportedApp
import com.veil.privacy.model.ProtectedEvent
import com.veil.privacy.model.TransportMessage
import com.veil.privacy.notification.NotificationReplyRegistry
import com.veil.privacy.storage.VeilDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Routes VEIL chat messages through existing app notification reply actions.
 *
 * Incoming transport is discovered from NotificationListenerService. Outgoing transport
 * is possible only while the selected app exposes a live RemoteInput reply PendingIntent.
 * This class never claims delivery confirmation and never uses app messaging APIs.
 */
class VeilTransportManager(
    private val context: Context,
    private val codec: VeilLanguageCodec = DictionaryPendingCodec()
) {
    private val database = VeilDatabase.getInstance(context)
    private val dao = database.transportMessageDao()

    data class AppCapability(
        val packageName: String,
        val displayName: String,
        val canReplyNow: Boolean
    )

    data class SendResult(
        val messageId: String,
        val status: String,
        val detail: String
    )

    fun supportedTransports(): List<Pair<String, String>> = listOf(
        SupportedApp.PKG_WHATSAPP to "WhatsApp",
        SupportedApp.PKG_INSTAGRAM to "Instagram",
        SupportedApp.PKG_SNAPCHAT to "Snapchat"
    )

    fun availableTransports(contactName: String): List<AppCapability> =
        supportedTransports().map { (pkg, label) ->
            AppCapability(
                packageName = pkg,
                displayName = label,
                canReplyNow = NotificationReplyRegistry.getLiveReplyAction(contactName, pkg) != null
            )
        }

    /**
     * Queues a message. Until the official dictionary is loaded, it remains local and is
     * not sent as plaintext or as a fabricated code.
     */
    suspend fun queueOutgoing(
        conversationId: String,
        contactName: String,
        sourcePackage: String,
        plainText: String
    ): SendResult {
        val id = UUID.randomUUID().toString()
        if (sourcePackage !in supportedTransports().map { it.first }) {
            return SendResult(id, TransportMessage.FAILED, "Unsupported transport application")
        }
        if (plainText.isBlank()) {
            return SendResult(id, TransportMessage.FAILED, "Message is empty")
        }

        if (!codec.isReady) {
            dao.upsert(
                TransportMessage(
                    id = id,
                    conversationId = conversationId,
                    contactName = contactName,
                    sourcePackage = sourcePackage,
                    direction = TransportMessage.DIRECTION_OUTGOING,
                    originalText = plainText,
                    status = TransportMessage.WAITING_FOR_DICTIONARY,
                    lastError = "Waiting for VEIL dictionary file"
                )
            )
            return SendResult(id, TransportMessage.WAITING_FOR_DICTIONARY, "Saved locally; dictionary required")
            saveChatEvent("VEIL_TRANSPORT:$id", conversationId, contactName, sourcePackage, plainText, true, TransportMessage.WAITING_FOR_DICTIONARY)
        }

        return encodeAndDispatch(id, conversationId, contactName, sourcePackage, plainText)
    }

    /** Retry queued messages after the official dictionary implementation is installed. */
    suspend fun processDictionaryQueue(): List<SendResult> {
        if (!codec.isReady) return emptyList()
        return dao.getByStatus(TransportMessage.WAITING_FOR_DICTIONARY)
            .filter { it.direction == TransportMessage.DIRECTION_OUTGOING }
            .map {
                encodeAndDispatch(it.id, it.conversationId, it.contactName, it.sourcePackage, it.originalText)
            }
    }

    private suspend fun encodeAndDispatch(
        id: String,
        conversationId: String,
        contactName: String,
        sourcePackage: String,
        plainText: String
    ): SendResult {
        return try {
            val encoded = codec.encode(plainText)
            val packet = VeilTransportPacket.encode(id, codec.dictionaryVersion, encoded)
            val action = NotificationReplyRegistry.getLiveReplyAction(contactName, sourcePackage)
            if (action == null) {
                dao.upsert(
                    TransportMessage(
                        id = id,
                        conversationId = conversationId,
                        contactName = contactName,
                        sourcePackage = sourcePackage,
                        direction = TransportMessage.DIRECTION_OUTGOING,
                        originalText = plainText,
                        encodedPayload = encoded,
                        packet = packet,
                        dictionaryVersion = codec.dictionaryVersion,
                        status = TransportMessage.WAITING_FOR_REPLY_ACTION,
                        lastError = "No active reply action from selected app"
                    )
                )
                return SendResult(id, TransportMessage.WAITING_FOR_REPLY_ACTION, "Open the selected app conversation and wait for its reply-enabled notification")
            }

            val accepted = NotificationReplyRegistry.sendReply(context, contactName, sourcePackage, packet)
            val status = if (accepted) TransportMessage.DISPATCHED_NOT_CONFIRMED else TransportMessage.FAILED
            dao.upsert(
                TransportMessage(
                    id = id,
                    conversationId = conversationId,
                    contactName = contactName,
                    sourcePackage = sourcePackage,
                    direction = TransportMessage.DIRECTION_OUTGOING,
                    originalText = plainText,
                    encodedPayload = encoded,
                    packet = packet,
                    dictionaryVersion = codec.dictionaryVersion,
                    status = status,
                    lastError = if (accepted) "" else "Android rejected the active reply PendingIntent"
                )
            )
            saveChatEvent("VEIL_TRANSPORT:$id", conversationId, contactName, sourcePackage, plainText, true, status)
            SendResult(id, status, if (accepted) "Handed to Android; delivery is not confirmed" else "Reply dispatch failed")
        } catch (error: Exception) {
            dao.upsert(
                TransportMessage(
                    id = id,
                    conversationId = conversationId,
                    contactName = contactName,
                    sourcePackage = sourcePackage,
                    direction = TransportMessage.DIRECTION_OUTGOING,
                    originalText = plainText,
                    status = TransportMessage.FAILED,
                    lastError = error.message ?: error.javaClass.simpleName
                )
            )
            SendResult(id, TransportMessage.FAILED, error.message ?: "Encoding or dispatch failed")
        }
    }

    /** Retry encoded outgoing messages when a fresh notification reply action appears. */
    suspend fun retryWaitingForReplyActions(): List<SendResult> {
        return dao.getByStatus(TransportMessage.WAITING_FOR_REPLY_ACTION)
            .filter { it.direction == TransportMessage.DIRECTION_OUTGOING }
            .map { queued ->
                val accepted = NotificationReplyRegistry.sendReply(
                    context, queued.contactName, queued.sourcePackage, queued.packet
                )
                val nextStatus = if (accepted) {
                    TransportMessage.DISPATCHED_NOT_CONFIRMED
                } else {
                    TransportMessage.WAITING_FOR_REPLY_ACTION
                }
                dao.upsert(queued.copy(
                    status = nextStatus,
                    updatedAt = System.currentTimeMillis(),
                    lastError = if (accepted) "" else "No active reply action from selected app"
                ))
                SendResult(
                    queued.id,
                    nextStatus,
                    if (accepted) "Handed to Android; delivery is not confirmed" else "Still waiting for a reply-enabled notification"
                )
            }
    }

    /** Decode stored incoming packets after the matching dictionary version is installed. */
    suspend fun decodePendingIncoming(): Int {
        if (!codec.isReady) return 0
        var decodedCount = 0
        dao.getByStatus(TransportMessage.WAITING_FOR_DICTIONARY)
            .filter { it.direction == TransportMessage.DIRECTION_INCOMING }
            .forEach { queued ->
                if (queued.dictionaryVersion == codec.dictionaryVersion) {
                    val plainText = runCatching { codec.decode(queued.encodedPayload) }.getOrNull()
                    if (plainText != null) {
                        dao.upsert(queued.copy(
                            originalText = plainText,
                            status = TransportMessage.RECEIVED,
                            updatedAt = System.currentTimeMillis(),
                            lastError = ""
                        ))
                        decodedCount++
                    } else {
                        dao.upsert(queued.copy(
                            status = TransportMessage.DECODE_FAILED,
                            updatedAt = System.currentTimeMillis(),
                            lastError = "Dictionary rejected the encoded payload"
                        ))
                    }
                }
            }
        return decodedCount
    }

    /** Called by the notification listener for every supported app notification. */
    suspend fun captureIncoming(sourcePackage: String, sender: String, notificationText: String): Boolean {
        val packet = VeilTransportPacket.extract(notificationText) ?: return false
        val conversationId = sender.lowercase().trim() + "@" + sourcePackage
        val existing = dao.getById(packet.messageId)
        if (existing != null) return true

        val decoded = if (codec.isReady && codec.dictionaryVersion == packet.dictionaryVersion) {
            runCatching { codec.decode(packet.encodedPayload) }.getOrNull()
        } else null

        dao.upsert(
            TransportMessage(
                id = packet.messageId,
                conversationId = conversationId,
                contactName = sender,
                sourcePackage = sourcePackage,
                direction = TransportMessage.DIRECTION_INCOMING,
                originalText = decoded.orEmpty(),
                encodedPayload = packet.encodedPayload,
                packet = "VUPL1 packet received",
                dictionaryVersion = packet.dictionaryVersion,
                status = if (decoded != null) TransportMessage.RECEIVED else TransportMessage.WAITING_FOR_DICTIONARY,
                lastError = if (decoded == null) "Dictionary missing or version mismatch" else ""
            )
        )
        saveChatEvent(
            "VEIL_TRANSPORT:${packet.messageId}",
            sender.lowercase().trim(),
            sender,
            sourcePackage,
            decoded ?: "VEIL message received - waiting for dictionary",
            false,
            if (decoded != null) TransportMessage.RECEIVED else TransportMessage.WAITING_FOR_DICTIONARY
        )
        return true
    }

    private suspend fun saveChatEvent(
        packetKey: String,
        conversationId: String,
        contactName: String,
        sourcePackage: String,
        text: String,
        outgoing: Boolean,
        status: String
    ) {
        withContext(Dispatchers.IO) {
            val events = database.protectedEventDao()
            val existing = events.getTransportEventByKey(packetKey)
            if (existing == null) {
                events.insert(
                    ProtectedEvent(
                        sourcePackage = sourcePackage,
                        realSender = if (outgoing) "You" else contactName,
                        realMessage = text,
                        originalNotificationKey = packetKey,
                        isOutgoing = outgoing,
                        replyStatus = status,
                        conversationId = conversationId
                    )
                )
            } else {
                events.updateTransportEvent(packetKey, text, status)
            }
        }
    }
}
