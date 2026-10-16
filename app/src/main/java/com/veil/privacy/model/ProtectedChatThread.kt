package com.veil.privacy.model

/**
 * Represents a summarized Protected Chat thread displayed in the Protected Chats tab.
 */
data class ProtectedChatThread(
    val contactName: String,
    val contactPhoneNumber: String = "",
    val lastMessage: String,
    val lastTimestamp: Long,
    val isLiveReplyAvailable: Boolean,
    val disguiseMode: String,
    val decoyIdentity: String,
    val unreadCount: Int = 0
)
