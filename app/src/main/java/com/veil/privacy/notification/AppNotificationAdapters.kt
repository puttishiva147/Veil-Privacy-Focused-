package com.veil.privacy.notification

import android.app.Notification
import android.os.Bundle
import androidx.core.app.NotificationCompat
import com.veil.privacy.model.SupportedApp

/**
 * Package-specific interpretation of the notification payload Android exposes.
 * This deliberately does not access private app databases or message history.
 */
internal interface AppNotificationAdapter {
    val packages: Set<String>
    fun extract(notification: Notification, extras: Bundle): Pair<String, String>?
    fun normalizeSender(raw: String): String
    val supportsRemoteInputReply: Boolean get() = true

    fun isGenericLabel(value: String): Boolean {
        val normalized = value.trim().lowercase()
        return normalized.isBlank() || normalized in setOf(
            "whatsapp", "whatsapp business", "instagram", "instagram direct",
            "telegram", "snapchat", "new message", "messages", "chat"
        )
    }
}

internal object AppNotificationAdapters {
    private val adapters: List<AppNotificationAdapter> = listOf(
        WhatsAppNotificationAdapter,
        InstagramNotificationAdapter,
        TelegramNotificationAdapter,
        SnapchatNotificationAdapter
    )

    fun forPackage(packageName: String): AppNotificationAdapter? =
        adapters.firstOrNull { packageName in it.packages }
}

private fun messagingStylePayload(notification: Notification): Pair<String, String>? {
    return try {
        val style = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)
            ?: return null
        val last = style.messages.lastOrNull() ?: return null
        val sender = last.person?.name?.toString()
            ?.takeIf { it.isNotBlank() }
            ?: style.conversationTitle?.toString()?.takeIf { it.isNotBlank() }
            ?: return null
        sender to (last.text?.toString().orEmpty())
    } catch (_: Exception) {
        null
    }
}

private fun extrasPayload(extras: Bundle): Pair<String, String>? {
    val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty().trim()
    val conversation = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString().orEmpty().trim()
    val text = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
        ?.takeIf { it.isNotBlank() }
        ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
    val lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
        ?.mapNotNull { it?.toString()?.takeIf(String::isNotBlank) }
    val body = if (!lines.isNullOrEmpty()) lines.last() else text
    val sender = when {
        title.isNotBlank() -> title
        conversation.isNotBlank() -> conversation
        else -> return null
    }
    return sender to body
}

private object WhatsAppNotificationAdapter : AppNotificationAdapter {
    override val packages = setOf(SupportedApp.PKG_WHATSAPP, SupportedApp.PKG_WHATSAPP_BUSINESS)
    override fun extract(notification: Notification, extras: Bundle): Pair<String, String>? =
        messagingStylePayload(notification) ?: extrasPayload(extras)
    override fun normalizeSender(raw: String): String =
        raw.replace(Regex("\\s*\\(\\d+\\s+(?:new\\s+)?messages?\\)$", RegexOption.IGNORE_CASE), "")
            .trim()
}

private object InstagramNotificationAdapter : AppNotificationAdapter {
    override val packages = setOf(SupportedApp.PKG_INSTAGRAM)
    override fun extract(notification: Notification, extras: Bundle): Pair<String, String>? {
        val payload = messagingStylePayload(notification) ?: extrasPayload(extras) ?: return null
        val title = payload.first.trim()
        if (isGenericLabel(title)) return null
        return payload
    }
    override fun normalizeSender(raw: String): String =
        raw.replace(Regex("\\s*[•·|:]\\s*(?:sent you a message|sent a message|new message|replied to your story).*?$", RegexOption.IGNORE_CASE), "")
            .replace(Regex("^Instagram(?: Direct)?\\s*[:|-]\\s*", RegexOption.IGNORE_CASE), "")
            .trim()
}

private object TelegramNotificationAdapter : AppNotificationAdapter {
    override val packages = setOf(
        SupportedApp.PKG_TELEGRAM,
        SupportedApp.PKG_TELEGRAM_PLUS,
        SupportedApp.PKG_TELEGRAM_CH
    )
    override fun extract(notification: Notification, extras: Bundle): Pair<String, String>? =
        messagingStylePayload(notification) ?: extrasPayload(extras)
    override fun normalizeSender(raw: String): String =
        raw.replace(Regex("\\s*\\(\\d+\\s+(?:new\\s+)?messages?\\)$", RegexOption.IGNORE_CASE), "")
            .replace(Regex("^Telegram\\s*[:|-]\\s*", RegexOption.IGNORE_CASE), "")
            .trim()
}

private object SnapchatNotificationAdapter : AppNotificationAdapter {
    override val packages = setOf(SupportedApp.PKG_SNAPCHAT)
    // Snapchat notification templates vary by OS/app version. Prefer MessagingStyle,
    // then title/body extras; do not infer a sender from unrelated generic text.
    override fun extract(notification: Notification, extras: Bundle): Pair<String, String>? {
        val payload = messagingStylePayload(notification) ?: extrasPayload(extras) ?: return null
        if (isGenericLabel(payload.first)) {
            val text = payload.second.trim()
            val fromPattern = Regex("^(?:new )?(?:snap|chat|message) from (.+)$", RegexOption.IGNORE_CASE)
            val match = fromPattern.find(text) ?: return null
            return match.groupValues[1].trim() to "New Snapchat notification"
        }
        return payload
    }
    override fun normalizeSender(raw: String): String =
        raw.replace(Regex("^Snapchat\\s*[:|-]\\s*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s+(?:sent you a snap|sent a chat|sent you a chat|sent a message)$", RegexOption.IGNORE_CASE), "")
            .trim()
}
