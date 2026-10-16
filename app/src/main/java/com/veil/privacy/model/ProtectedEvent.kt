package com.veil.privacy.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Stores intercepted notification events and legitimate user replies for VEIL Protected Chats.
 *
 * Incoming messages originate from Android notification access.
 * Outgoing replies are dispatched via Android RemoteInput inline-reply mechanism.
 */
@Entity(tableName = "protected_events")
data class ProtectedEvent(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val sourcePackage: String = "com.whatsapp",
    val realSender: String,
    val realMessage: String,
    val decoySender: String = "VEIL",
    val decoyMessage: String = "Protected message",
    val isCall: Boolean = false,
    val originalNotificationKey: String? = null,
    val isOutgoing: Boolean = false,
    val replyStatus: String = "RECEIVED", // "RECEIVED", "SENT_VIA_REMOTE_INPUT", "FAILED_NO_ACTION"
    val conversationId: String = realSender.lowercase().trim()
)
