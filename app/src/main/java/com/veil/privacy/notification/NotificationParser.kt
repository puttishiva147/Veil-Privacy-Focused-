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
        val cleanSender = normalizeSenderForSource(rawSender, packageName)
        val cleanMessage = rawMessage.trim()

        if (cleanSender.isBlank()) {
            return null
        }

        val isMedia = isMediaNotification(notification, extras, cleanMessage)

        return ParsedNotificationInfo(
            sender = cleanSender,
            message = if (cleanMessage.isBlank()) "New message" else cleanMessage,
            isCall = isCall,
            packageName = packageName,
            notificationKey = notificationKey,
            timestamp = postTime,
            isMediaOrAttachment = isMedia
        )
    }

    /**
     * Normalize sender labels that social apps append to notification titles.
     * Keep parsing source-aware: never treat an Instagram "sent you a message" suffix,
     * or a Telegram group title, as part of the contact identity used for rule matching.
     */
    private fun normalizeSenderForSource(rawSender: String, packageName: String): String {
        var sender = rawSender.trim()
        when {
            packageName == com.veil.privacy.model.SupportedApp.PKG_INSTAGRAM -> {
                sender = sender
                    .replace(Regex("\\s*[•·|:]\\s*(?:sent you a message|sent a message|new message).*?$", RegexOption.IGNORE_CASE), "")
                    .replace(Regex("^Instagram\\s*[:|-]\\s*", RegexOption.IGNORE_CASE), "")
            }
            packageName.startsWith("org.telegram.") -> {
                sender = sender
                    .replace(Regex("\\s*\\(\\d+\\s+(?:new\\s+)?messages?\\)$", RegexOption.IGNORE_CASE), "")
                    .replace(Regex("^Telegram\\s*[:|-]\\s*", RegexOption.IGNORE_CASE), "")
            }
            packageName == com.veil.privacy.model.SupportedApp.PKG_MESSENGER -> {
                sender = sender
                    .replace(Regex("\\s*[•·|:]\\s*(?:sent you a message|sent a message|new message).*?$", RegexOption.IGNORE_CASE), "")
            }
        }
        return sender.trim().trimEnd(':', '•', '·', '|').trim()
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
