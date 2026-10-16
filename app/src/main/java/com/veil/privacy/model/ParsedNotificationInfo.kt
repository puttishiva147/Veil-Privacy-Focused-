package com.veil.privacy.model

/**
 * Encapsulates the extracted information from an incoming notification
 * before privacy evaluation.
 */
data class ParsedNotificationInfo(
    val sender: String,
    val message: String,
    val isCall: Boolean,
    val packageName: String,
    val notificationKey: String?,
    val timestamp: Long = System.currentTimeMillis(),
    val isMediaOrAttachment: Boolean = false
) {
    fun getNotificationType(): NotificationType {
        return when {
            isCall -> NotificationType.CALL
            isMediaOrAttachment -> NotificationType.MEDIA_ATTACHMENT
            else -> NotificationType.MESSAGE
        }
    }
}
