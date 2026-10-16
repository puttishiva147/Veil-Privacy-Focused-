package com.veil.privacy.model

/**
 * Supported notification payload classifications.
 */
enum class NotificationType {
    MESSAGE,
    CALL,
    MEDIA_ATTACHMENT,
    OTHER
}

/**
 * Represents a cohesive disguise profile persona (Work, Calendar, Delivery, System, etc.)
 * pairing matching sender identities with contextually appropriate messages for each
 * notification type (messages, calls, media/attachments, and other alerts).
 */
data class DecoyProfile(
    val key: String,
    val displayName: String,
    val iconEmoji: String,
    val messageSenders: List<String>,
    val messageTexts: List<String>,
    val callSenders: List<String>,
    val callTexts: List<String>,
    val mediaTexts: List<String>,
    val otherTexts: List<String>
) {
    /**
     * Retrieves available senders for the specified notification type.
     */
    fun getSendersForType(type: NotificationType): List<String> {
        return when (type) {
            NotificationType.CALL -> if (callSenders.isNotEmpty()) callSenders else messageSenders
            else -> messageSenders
        }
    }

    /**
     * Retrieves available messages for the specified notification type.
     */
    fun getMessagesForType(type: NotificationType): List<String> {
        return when (type) {
            NotificationType.CALL -> callTexts
            NotificationType.MEDIA_ATTACHMENT -> if (mediaTexts.isNotEmpty()) mediaTexts else messageTexts
            NotificationType.OTHER -> if (otherTexts.isNotEmpty()) otherTexts else messageTexts
            NotificationType.MESSAGE -> messageTexts
        }
    }
}
