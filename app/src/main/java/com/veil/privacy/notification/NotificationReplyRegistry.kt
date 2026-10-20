package com.veil.privacy.notification

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Bundle
import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe registry for active Android notification reply tokens.
 * Reply delivery is supported only when the source app exposes a live inline-reply
 * PendingIntent and a free-form RemoteInput.
 */
object NotificationReplyRegistry {
    data class LiveReplyAction(
        val notificationKey: String,
        val contactName: String,
        val sourcePackage: String = com.veil.privacy.model.SupportedApp.PKG_WHATSAPP,
        val actionIntent: PendingIntent,
        val remoteInput: RemoteInput,
        val resultKey: String,
        val capturedAt: Long = System.currentTimeMillis()
    )

    data class LiveCallAction(
        val notificationKey: String,
        val contactName: String,
        val sourcePackage: String = com.veil.privacy.model.SupportedApp.PKG_WHATSAPP,
        val answerIntent: PendingIntent?,
        val declineIntent: PendingIntent?,
        val capturedAt: Long = System.currentTimeMillis()
    )

    private val liveActionsByContact = ConcurrentHashMap<String, LiveReplyAction>()
    private val liveCallsByContact = ConcurrentHashMap<String, LiveCallAction>()
    private val keyToContactMap = ConcurrentHashMap<String, String>()

    /** Return only a RemoteInput action that accepts typed free-form text. */
    fun findReplyAction(notification: Notification?): Pair<Notification.Action, RemoteInput>? {
        val actions = notification?.actions ?: return null
        for (action in actions) {
            val inputs = action.remoteInputs ?: continue
            val input = inputs.firstOrNull { !it.resultKey.isNullOrBlank() && it.allowFreeFormInput }
                ?: continue
            return action to input
        }
        return null
    }

    fun findCallActions(notification: Notification?): Pair<PendingIntent?, PendingIntent?>? {
        val actions = notification?.actions ?: return null
        var answerIntent: PendingIntent? = null
        var declineIntent: PendingIntent? = null
        for (action in actions) {
            val title = action.title?.toString()?.lowercase() ?: continue
            if (title.contains("answer") || title.contains("accept") || title.contains("call back")) {
                answerIntent = action.actionIntent
            } else if (title.contains("decline") || title.contains("reject") || title.contains("dismiss")) {
                declineIntent = action.actionIntent
            }
        }
        return if (answerIntent != null || declineIntent != null) answerIntent to declineIntent else null
    }

    fun registerCall(
        contactName: String,
        notificationKey: String,
        answerIntent: PendingIntent?,
        declineIntent: PendingIntent?,
        sourcePackage: String = com.veil.privacy.model.SupportedApp.PKG_WHATSAPP
    ) {
        val normalizedName = normalize(contactName, sourcePackage)
        liveCallsByContact[normalizedName] = LiveCallAction(
            notificationKey, contactName, sourcePackage, answerIntent, declineIntent
        )
        keyToContactMap[notificationKey] = normalizedName
    }

    fun getLiveCallAction(contactName: String, sourcePackage: String? = null): LiveCallAction? {
        val normalized = if (sourcePackage == null) normalize(contactName) else normalize(contactName, sourcePackage)
        val call = liveCallsByContact[normalized]
            ?: (if (sourcePackage == null) liveCallsByContact.values.filter {
                it.contactName.equals(contactName, ignoreCase = true)
            }.maxByOrNull { it.capturedAt } else null)
            ?: return null
        if (System.currentTimeMillis() - call.capturedAt > 5 * 60 * 1000L) {
            liveCallsByContact.remove(normalize(call.contactName, call.sourcePackage), call)
            return null
        }
        return call
    }

    fun register(
        contactName: String,
        notificationKey: String,
        action: Notification.Action,
        remoteInput: RemoteInput,
        sourcePackage: String = com.veil.privacy.model.SupportedApp.PKG_WHATSAPP
    ) {
        if (!remoteInput.allowFreeFormInput || remoteInput.resultKey.isBlank()) return
        val normalizedName = normalize(contactName, sourcePackage)
        liveActionsByContact[normalizedName] = LiveReplyAction(
            notificationKey, contactName, sourcePackage, action.actionIntent, remoteInput, remoteInput.resultKey
        )
        keyToContactMap[notificationKey] = normalizedName
    }

    fun isReplyActionLive(contactName: String, sourcePackage: String? = null): Boolean {
        val normalized = if (sourcePackage == null) normalize(contactName) else normalize(contactName, sourcePackage)
        val action = liveActionsByContact[normalized]
            ?: (if (sourcePackage == null) liveActionsByContact.values.filter {
                it.contactName.equals(contactName, ignoreCase = true)
            }.maxByOrNull { it.capturedAt } else null)
            ?: return false
        if (System.currentTimeMillis() - action.capturedAt > 2 * 3600 * 1000L) {
            liveActionsByContact.remove(normalize(action.contactName, action.sourcePackage), action)
            keyToContactMap.remove(action.notificationKey, normalize(action.contactName, action.sourcePackage))
            return false
        }
        return true
    }

    fun getLiveReplyAction(contactName: String, sourcePackage: String? = null): LiveReplyAction? {
        if (!isReplyActionLive(contactName, sourcePackage)) return null
        val normalized = if (sourcePackage == null) normalize(contactName) else normalize(contactName, sourcePackage)
        return liveActionsByContact[normalized]
            ?: if (sourcePackage == null) liveActionsByContact.values.filter {
                it.contactName.equals(contactName, ignoreCase = true)
            }.maxByOrNull { it.capturedAt } else null
    }

    fun sendReply(context: Context, contactName: String, replyText: String, sourcePackage: String? = null): Boolean {
        if (replyText.isBlank()) return false
        val action = getLiveReplyAction(contactName, sourcePackage) ?: return false
        return try {
            val intent = Intent()
            val results = Bundle().apply { putCharSequence(action.resultKey, replyText.trim()) }
            RemoteInput.addResultsToIntent(arrayOf(action.remoteInput), intent, results)
            action.actionIntent.send(context, 0, intent)
            true
        } catch (_: Exception) {
            removeByKey(action.notificationKey)
            false
        }
    }

    fun removeByKey(notificationKey: String) {
        keyToContactMap.remove(notificationKey)
        liveActionsByContact.entries.removeIf { it.value.notificationKey == notificationKey }
        liveCallsByContact.entries.removeIf { it.value.notificationKey == notificationKey }
    }

    fun removeCallByKey(notificationKey: String) {
        liveCallsByContact.entries.removeIf { it.value.notificationKey == notificationKey }
        keyToContactMap.remove(notificationKey)
    }

    fun clearAll() {
        liveActionsByContact.clear()
        liveCallsByContact.clear()
        keyToContactMap.clear()
    }

    private fun normalize(name: String): String = name.lowercase().trim()
    private fun normalize(name: String, sourcePackage: String): String =
        "${sourcePackage.trim().lowercase()}::${normalize(name)}"
}
