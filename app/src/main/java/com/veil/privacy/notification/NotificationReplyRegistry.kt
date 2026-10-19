package com.veil.privacy.notification

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Bundle
import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe registry that stores active Android notification reply tokens (PendingIntent + RemoteInput)
 * exposed by WhatsApp notifications.
 *
 * This allows VEIL's Protected Chats UI to transmit user replies legitimately through
 * Android's official Notification RemoteInput inline-reply mechanism.
 *
 * As required by Android security architecture:
 * - A reply can only be dispatched when WhatsApp currently exposes an active notification action.
 * - If the notification has been cancelled or cleared by the OS, this registry immediately flags the
 *   action as unavailable so VEIL does not pretend the message can be sent.
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

    // Keyed by normalized contact name (lowercase trimmed)
    private val liveActionsByContact = ConcurrentHashMap<String, LiveReplyAction>()
    private val liveCallsByContact = ConcurrentHashMap<String, LiveCallAction>()
    // Keyed by notificationKey to allow swift removal when notification is cleared
    private val keyToContactMap = ConcurrentHashMap<String, String>()

    /**
     * Inspects a status notification's actions to locate an inline-reply action with RemoteInput.
     */
    fun findReplyAction(notification: Notification?): Pair<Notification.Action, RemoteInput>? {
        if (notification == null) return null
        val actions = notification.actions ?: return null

        for (action in actions) {
            val remoteInputs = action.remoteInputs ?: continue
            for (remoteInput in remoteInputs) {
                // Free-form reply text input
                if (!remoteInput.resultKey.isNullOrBlank()) {
                    return Pair(action, remoteInput)
                }
            }
        }
        return null
    }

    /**
     * Inspects a status notification's actions to locate call Answer / Decline PendingIntents.
     */
    fun findCallActions(notification: Notification?): Pair<PendingIntent?, PendingIntent?>? {
        if (notification == null) return null
        val actions = notification.actions ?: return null
        var answerIntent: PendingIntent? = null
        var declineIntent: PendingIntent? = null

        for (action in actions) {
            val title = action.title?.toString()?.lowercase() ?: ""
            if (title.contains("answer") || title.contains("accept") || title.contains("call back")) {
                answerIntent = action.actionIntent
            } else if (title.contains("decline") || title.contains("reject") || title.contains("dismiss")) {
                declineIntent = action.actionIntent
            }
        }

        if (answerIntent != null || declineIntent != null) {
            return Pair(answerIntent, declineIntent)
        }
        return null
    }

    /**
     * Registers an intercepted live notification call action for a protected contact.
     */
    fun registerCall(
        contactName: String,
        notificationKey: String,
        answerIntent: PendingIntent?,
        declineIntent: PendingIntent?,
        sourcePackage: String = com.veil.privacy.model.SupportedApp.PKG_WHATSAPP
    ) {
        val normalizedName = normalize(contactName, sourcePackage)
        val liveCall = LiveCallAction(
            notificationKey = notificationKey,
            contactName = contactName,
            sourcePackage = sourcePackage,
            answerIntent = answerIntent,
            declineIntent = declineIntent
        )
        liveCallsByContact[normalizedName] = liveCall
        keyToContactMap[notificationKey] = normalizedName

    }

    fun getLiveCallAction(contactName: String, sourcePackage: String? = null): LiveCallAction? {
        val normalized = if (sourcePackage == null) normalize(contactName) else normalize(contactName, sourcePackage)
        val call = liveCallsByContact[normalized] ?: return null
        // Call actions expire after 5 minutes
        if (System.currentTimeMillis() - call.capturedAt > 5 * 60 * 1000L) {
            liveCallsByContact.remove(normalized)
            return null
        }
        return call
    }

    /**
     * Registers an intercepted live notification reply action for a protected contact.
     */
    fun register(
        contactName: String,
        notificationKey: String,
        action: Notification.Action,
        remoteInput: RemoteInput,
        sourcePackage: String = com.veil.privacy.model.SupportedApp.PKG_WHATSAPP
    ) {
        val normalizedName = normalize(contactName, sourcePackage)
        val liveAction = LiveReplyAction(
            notificationKey = notificationKey,
            contactName = contactName,
            sourcePackage = sourcePackage,
            actionIntent = action.actionIntent,
            remoteInput = remoteInput,
            resultKey = remoteInput.resultKey
        )
        liveActionsByContact[normalizedName] = liveAction
        keyToContactMap[notificationKey] = normalizedName

    }

    /**
     * Checks if a live notification reply action is currently available for this contact.
     */
    fun isReplyActionLive(contactName: String, sourcePackage: String? = null): Boolean {
        val normalized = if (sourcePackage == null) normalize(contactName) else normalize(contactName, sourcePackage)
        val action = liveActionsByContact[normalized]
            ?: (if (sourcePackage == null) liveActionsByContact.values.filter { it.contactName.equals(contactName, ignoreCase = true) }.maxByOrNull { it.capturedAt } else null)
            ?: return false
        // Actions older than 2 hours are discarded to avoid stale PendingIntents
        if (System.currentTimeMillis() - action.capturedAt > 2 * 3600 * 1000L) {
            liveActionsByContact.remove(normalized)
            keyToContactMap.remove(action.notificationKey)
            return false
        }
        return true
    }

    /**
     * Gets the live action details if available.
     */
    fun getLiveReplyAction(contactName: String, sourcePackage: String? = null): LiveReplyAction? {
        val normalized = if (sourcePackage == null) normalize(contactName) else normalize(contactName, sourcePackage)
        return if (isReplyActionLive(contactName, sourcePackage)) liveActionsByContact[normalized]
            ?: liveActionsByContact.values.filter { it.contactName.equals(contactName, ignoreCase = true) }.maxByOrNull { it.capturedAt }
        else null
    }

    /**
     * Dispatches an inline reply using Android's standard RemoteInput bundle through WhatsApp's PendingIntent.
     *
     * @return true if the reply was successfully handed over to the Android OS for delivery, false if token is expired/missing.
     */
    fun sendReply(context: Context, contactName: String, replyText: String, sourcePackage: String? = null): Boolean {
        val action = getLiveReplyAction(contactName, sourcePackage) ?: run {

            return false
        }

        return try {
            val intent = Intent()
            val bundle = Bundle()
            bundle.putCharSequence(action.resultKey, replyText.trim())
            RemoteInput.addResultsToIntent(arrayOf(action.remoteInput), intent, bundle)

            // Send intent via PendingIntent
            action.actionIntent.send(context, 0, intent)

            true
        } catch (e: Exception) {
            // A cancelled PendingIntent is no longer a live reply route. Remove it now so
            // VEIL does not keep advertising an action that repeatedly fails.
            removeByKey(action.notificationKey)
            false
        }
    }

    /**
     * Removes cached action when Android cancels or removes the underlying notification.
     */
    fun removeByKey(notificationKey: String) {
        keyToContactMap.remove(notificationKey)
        // A single notification can be registered under both its parsed sender and a configured
        // rule name. Remove every matching entry, but preserve newer tokens for the same contact.
        liveActionsByContact.entries.removeIf { it.value.notificationKey == notificationKey }
        liveCallsByContact.entries.removeIf { it.value.notificationKey == notificationKey }

    }

    /**
     * Clears call controls when a call notification ends. Reply tokens are kept separately because
     * VEIL may itself dismiss the original message notification after extracting its RemoteInput.
     */
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

    private fun normalize(name: String, sourcePackage: String): String = "${sourcePackage.trim().lowercase()}::${normalize(name)}"
}
