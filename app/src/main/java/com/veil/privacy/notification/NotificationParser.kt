package com.veil.privacy.notification

import android.app.Notification
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import com.veil.privacy.model.ParsedNotificationInfo

/**
 * Robust extractor of sender identity and message body from Android notifications.
 *
 * Designed to handle all permutations of Android notification structures:
 * - MessagingStyle with Person objects
 * - Legacy BigTextStyle / InboxStyle
 * - Bundle extras: EXTRA_TITLE, EXTRA_TEXT, EXTRA_BIG_TEXT, EXTRA_CONVERSATION_TITLE
 * - Call categories and grouped summaries
 * - Safe null-checking to guarantee zero crashes on unexpected OEM formats.
 */
class NotificationParser {

    companion object {
        const val PKG_WHATSAPP = com.veil.privacy.model.SupportedApp.PKG_WHATSAPP
        const val PKG_WHATSAPP_BUSINESS = com.veil.privacy.model.SupportedApp.PKG_WHATSAPP_BUSINESS
        const val PKG_SIMULATION = com.veil.privacy.model.SupportedApp.PKG_SIMULATION

        // System packages that should never be intercepted
        private val SYSTEM_IGNORED_PACKAGES = setOf(
            "android",
            "com.android.systemui",
            "com.android.settings",
            "com.google.android.gms",
            "com.google.android.googlequicksearchbox"
        )

        /**
         * Checks if the package is a supported messaging app.
         */
        fun isSupportedPackage(packageName: String?, allowAnyApp: Boolean = false): Boolean {
            if (packageName == null) return false
            if (SYSTEM_IGNORED_PACKAGES.contains(packageName)) return false
            if (allowAnyApp) return true
            return com.veil.privacy.model.SupportedApp.isPackageRecognized(packageName)
        }
    }

    /**
     * Parses a StatusBarNotification safely.
     * Returns null if not from supported app, or if from VEIL itself, or if content is insufficient.
     */
    fun parse(
        sbn: StatusBarNotification,
        ownPackageName: String,
        allowAnyApp: Boolean = false
    ): ParsedNotificationInfo? {
        val packageName = sbn.packageName ?: return null
        val notification = sbn.notification ?: return null
        return parseNotification(
            notification = notification,
            packageName = packageName,
            notificationKey = sbn.key,
            postTime = sbn.postTime,
            ownPackageName = ownPackageName,
            allowAnyApp = allowAnyApp
        )
    }

    /**
     * Core parsing method that works directly with Notification objects.
     */
    fun parseNotification(
        notification: Notification?,
        packageName: String?,
        notificationKey: String?,
        postTime: Long,
        ownPackageName: String,
        allowAnyApp: Boolean = false
    ): ParsedNotificationInfo? {
        if (packageName == null || notification == null) {
            return null
        }

        // Rule: Never process VEIL's own notifications
        if (packageName == ownPackageName) {
            return null
        }

        // Rule: Verify package eligibility
        if (!isSupportedPackage(packageName, allowAnyApp)) {
            return null
        }

        val extras = notification.extras ?: return null

        // 1. Detect call notifications
        val isCall = isCallNotification(notification, extras)

        // 2. Extract sender and message
        val extracted = extractSenderAndMessage(notification, extras) ?: return null

        val (rawSender, rawMessage) = extracted
        val cleanSender = rawSender.trim()
        val cleanMessage = rawMessage.trim()

        if (cleanSender.isBlank()) {
            return null
        }

        val isSnapchat = packageName == com.veil.privacy.model.SupportedApp.PKG_SNAPCHAT
        val snapEvent = if (isSnapchat) classifySnapchatEvent(cleanMessage) else null
        val isMedia = isMediaNotification(notification, extras, cleanMessage) || snapEvent != null

        return ParsedNotificationInfo(
            sender = cleanSender,
            // Notification access cannot expose/view the Snap media itself. Label known Snap
            // lifecycle events explicitly so the Chat UI never presents them as ordinary text.
            message = snapEvent ?: if (cleanMessage.isBlank()) "New message" else cleanMessage,
            isCall = isCall,
            packageName = packageName,
            notificationKey = notificationKey,
            timestamp = postTime,
            isMediaOrAttachment = isMedia
        )
    }

    private fun classifySnapchatEvent(message: String): String? {
        val text = message.trim()
        val lower = text.lowercase()
        if (text.isBlank()) return "Snap event received — media is not available inside VEIL"
        return when {
            lower.contains("sent you a snap") || lower.contains("sent a snap") ||
                lower.contains("received a snap") || lower.contains("snap received") ->
                "Snap received — open Snapchat to view the media"
            lower.contains("replayed your snap") || lower.contains("replayed a snap") ->
                "Snap replay event — view status in Snapchat"
            lower.contains("opened your snap") || lower.contains("opened a snap") ->
                "Snap opened event — status reported by Snapchat"
            lower.contains("screenshot") && lower.contains("snap") ->
                "Snap screenshot event — details reported by Snapchat"
            else -> null
        }
    }

    /**
     * Detects if the notification contains media, photo, sticker, audio, or file attachments.
     */
    private fun isMediaNotification(notification: Notification, extras: android.os.Bundle, message: String): Boolean {
        if (extras.containsKey(Notification.EXTRA_PICTURE) || extras.containsKey(NotificationCompat.EXTRA_PICTURE)) {
            return true
        }
        val lower = message.lowercase()
        return lower.contains("photo") ||
                lower.contains("image") ||
                lower.contains("sticker") ||
                lower.contains("gif") ||
                lower.contains("video") ||
                lower.contains("audio") ||
                lower.contains("voice message") ||
                lower.contains("document") ||
                lower.contains("file") ||
                lower.contains("attachment") ||
                lower.contains("📷") ||
                lower.contains("📹") ||
                lower.contains("🎙️") ||
                lower.contains("🎤") ||
                lower.contains("📄")
    }

    /**
     * Detects if the notification is a call.
     */
    private fun isCallNotification(notification: Notification, extras: android.os.Bundle): Boolean {
        if (notification.category == Notification.CATEGORY_CALL) {
            return true
        }
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.lowercase() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.lowercase() ?: ""
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()?.lowercase() ?: ""

        return title.contains("incoming call") ||
                title.contains("voice call") ||
                title.contains("video call") ||
                text.contains("incoming call") ||
                text.contains("voice call") ||
                text.contains("video call") ||
                subText.contains("call")
    }

    /**
     * Attempts multi-tiered extraction:
     * Tier 1: NotificationCompat.MessagingStyle (standard modern Android messaging)
     * Tier 2: Standard Notification bundle extras (EXTRA_TITLE, EXTRA_TEXT, EXTRA_BIG_TEXT)
     * Tier 3: Stacked / grouped text lines (EXTRA_TEXT_LINES)
     */
    private fun extractSenderAndMessage(
        notification: Notification,
        extras: android.os.Bundle
    ): Pair<String, String>? {
        // Tier 1: Try MessagingStyle
        try {
            val messagingStyle = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)
            if (messagingStyle != null && messagingStyle.messages.isNotEmpty()) {
                val lastMessage = messagingStyle.messages.last()
                val person = lastMessage.person
                val senderName = person?.name?.toString()
                    ?: messagingStyle.conversationTitle?.toString()
                    ?: extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()

                val messageText = lastMessage.text?.toString()

                if (!senderName.isNullOrBlank()) {
                    return Pair(senderName, messageText ?: "")
                }
            }
        } catch (_: Exception) {
            // Safe fallback to Bundle extras
        }

        // Tier 2: Check conversation title vs title
        val conversationTitle = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()

        val primarySender = title ?: conversationTitle
        val primaryMessage = bigText ?: text ?: ""

        if (!primarySender.isNullOrBlank()) {
            // Check for grouped header pattern like "Sender Name (3 messages)" or "Sender Name: Message text"
            val cleanSender = cleanGroupedSender(primarySender)
            return Pair(cleanSender, primaryMessage)
        }

        // Tier 3: Text lines array (InboxStyle fallback)
        val textLines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
        if (!textLines.isNullOrEmpty()) {
            val lastLine = textLines.lastOrNull()?.toString()
            if (!lastLine.isNullOrBlank() && lastLine.contains(":")) {
                val parts = lastLine.split(":", limit = 2)
                val lineSender = parts[0].trim()
                val lineMsg = parts[1].trim()
                if (lineSender.isNotBlank()) {
                    return Pair(lineSender, lineMsg)
                }
            }
        }

        return null
    }

    /**
     * Cleans sender names from formats like "Sender Name (2 messages)" -> "Sender Name"
     */
    private fun cleanGroupedSender(sender: String): String {
        var cleaned = sender.replace("\\s*\\(\\d+\\s+(?:new\\s+)?messages?\\)".toRegex(RegexOption.IGNORE_CASE), "").trim()
        val fromRegex = "^\\d+\\s+(?:new\\s+)?messages?\\s+from\\s+(.+)$".toRegex(RegexOption.IGNORE_CASE)
        val match = fromRegex.find(cleaned)
        if (match != null) {
            cleaned = match.groupValues[1].trim()
        }

        // Also check if sender string ends with a colon "Sender:"
        return if (cleaned.endsWith(":")) {
            cleaned.dropLast(1).trim()
        } else {
            cleaned
        }
    }
}
