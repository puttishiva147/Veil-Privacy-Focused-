package com.veil.privacy.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Durable VEIL transport queue item.
 *
 * The transport stores the original text only while waiting for the language dictionary;
 * after encoding, encodedPayload and packet are used for dispatch.
 */
@Entity(tableName = "transport_messages")
data class TransportMessage(
    @PrimaryKey val id: String,
    val conversationId: String,
    val contactName: String,
    val sourcePackage: String,
    val direction: String,
    val originalText: String = "",
    val encodedPayload: String = "",
    val packet: String = "",
    val dictionaryVersion: Int = 0,
    val status: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val lastError: String = ""
) {
    companion object {
        const val DIRECTION_OUTGOING = "OUTGOING"
        const val DIRECTION_INCOMING = "INCOMING"
        const val WAITING_FOR_DICTIONARY = "WAITING_FOR_DICTIONARY"
        const val WAITING_FOR_REPLY_ACTION = "WAITING_FOR_REPLY_ACTION"
        const val DISPATCHED_NOT_CONFIRMED = "DISPATCHED_NOT_CONFIRMED"
        const val RECEIVED = "RECEIVED"
        const val DECODE_FAILED = "DECODE_FAILED"
        const val FAILED = "FAILED"
    }
}
