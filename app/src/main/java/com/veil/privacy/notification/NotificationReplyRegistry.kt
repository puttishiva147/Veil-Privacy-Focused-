package com.veil.privacy.notification

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Bundle
import java.util.concurrent.ConcurrentHashMap

/**
 * Stores only live notification actions. Every lookup without an explicit app identity
 * fails closed when the same contact name exists in more than one messaging application.
 */
object NotificationReplyRegistry {
    data class LiveReplyAction(
        val notificationKey: String,
        val contactName: String,
        val sourcePackage: String,
        val actionIntent: PendingIntent,
        val remoteInput: RemoteInput,
        val resultKey: String,
        val capturedAt: Long = System.currentTimeMillis()
    )

    data class LiveCallAction(
        val notificationKey: String,
        val contactName: String,
        val sourcePackage: String,
        val answerIntent: PendingIntent?,
        val declineIntent: PendingIntent?,
        val capturedAt: Long = System.currentTimeMillis()
    )

    private val replyActions = ConcurrentHashMap<String, LiveReplyAction>()
    private val callActions = ConcurrentHashMap<String, LiveCallAction>()

    fun findReplyAction(notification: Notification?): Pair<Notification.Action, RemoteInput>? {
        if (notification == null) return null
        for (action in notification.actions ?: return null) {
            for (remoteInput in action.remoteInputs ?: continue) {
                if (!remoteInput.resultKey.isNullOrBlank()) return action to remoteInput
            }
        }
        return null
    }

    fun findCallActions(notification: Notification?): Pair<PendingIntent?, PendingIntent?>? {
        if (notification == null) return null
        var answer: PendingIntent? = null
        var decline: PendingIntent? = null
        for (action in notification.actions ?: return null) {
            val title = action.title?.toString()?.lowercase().orEmpty()
            if (title.contains("answer") || title.contains("accept") || title.contains("call back")) {
                answer = action.actionIntent
            } else if (title.contains("decline") || title.contains("reject") || title.contains("dismiss")) {
                decline = action.actionIntent
            }
        }
        return if (answer != null || decline != null) answer to decline else null
    }

    fun registerCall(
        contactName: String,
        notificationKey: String,
        answerIntent: PendingIntent?,
        declineIntent: PendingIntent?,
        sourcePackage: String = com.veil.privacy.model.SupportedApp.PKG_WHATSAPP
    ) {
        val normalized = normalize(contactName)
        callActions[key(normalized, sourcePackage)] = LiveCallAction(
            notificationKey = notificationKey,
            contactName = contactName,
            sourcePackage = sourcePackage,
            answerIntent = answerIntent,
            declineIntent = declineIntent
        )
    }

    fun getLiveCallAction(contactName: String): LiveCallAction? =
        unambiguous(callActions, contactName) { it.contactName }.also { if (it != null && expired(it.capturedAt, 5 * 60_000L)) removeCallByKey(it.notificationKey) }

    fun getLiveCallAction(contactName: String, sourcePackage: String): LiveCallAction? {
        val action = callActions[key(normalize(contactName), sourcePackage)] ?: return null
        if (expired(action.capturedAt, 5 * 60_000L)) {
            removeCallByKey(action.notificationKey)
            return null
        }
        return action
    }

    fun register(
        contactName: String,
        notificationKey: String,
        action: Notification.Action,
        remoteInput: RemoteInput,
        sourcePackage: String = com.veil.privacy.model.SupportedApp.PKG_WHATSAPP
    ) {
        val normalized = normalize(contactName)
        replyActions[key(normalized, sourcePackage)] = LiveReplyAction(
            notificationKey = notificationKey,
            contactName = contactName,
            sourcePackage = sourcePackage,
            actionIntent = action.actionIntent,
            remoteInput = remoteInput,
            resultKey = remoteInput.resultKey
        )
    }

    fun getLiveReplyAction(contactName: String, sourcePackage: String): LiveReplyAction? {
        val action = replyActions[key(normalize(contactName), sourcePackage)] ?: return null
        if (expired(action.capturedAt, 2 * 60 * 60_000L)) {
            replyActions.remove(key(normalize(contactName), sourcePackage), action)
            return null
        }
        return action
    }

    /** Legacy lookup is safe only when exactly one app has a live action for this contact. */
    fun getLiveReplyAction(contactName: String): LiveReplyAction? =
        unambiguous(replyActions, contactName) { it.contactName }.also {
            if (it != null && expired(it.capturedAt, 2 * 60 * 60_000L)) {
                replyActions.remove(key(normalize(it.contactName), it.sourcePackage), it)
            }
        }

    fun isReplyActionLive(contactName: String): Boolean = getLiveReplyAction(contactName) != null
    fun isReplyActionLive(contactName: String, sourcePackage: String): Boolean =
        getLiveReplyAction(contactName, sourcePackage) != null

    fun sendReply(context: Context, contactName: String, replyText: String): Boolean {
        val action = getLiveReplyAction(contactName) ?: return false
        return dispatch(context, action, replyText)
    }

    fun sendReply(context: Context, contactName: String, sourcePackage: String, replyText: String): Boolean {
        val action = getLiveReplyAction(contactName, sourcePackage) ?: return false
        return dispatch(context, action, replyText)
    }

    private fun dispatch(context: Context, action: LiveReplyAction, replyText: String): Boolean {
        if (replyText.isBlank()) return false
        return try {
            val intent = Intent()
            val results = Bundle().apply { putCharSequence(action.resultKey, replyText.trim()) }
            RemoteInput.addResultsToIntent(arrayOf(action.remoteInput), intent, results)
            action.actionIntent.send(context, 0, intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun removeByKey(notificationKey: String) {
        replyActions.entries.removeIf { it.value.notificationKey == notificationKey }
        callActions.entries.removeIf { it.value.notificationKey == notificationKey }
    }

    /** Call lifecycle cleanup must not discard a message reply action with the same notification key. */
    fun removeCallByKey(notificationKey: String) {
        callActions.entries.removeIf { it.value.notificationKey == notificationKey }
    }

    fun clearAll() {
        replyActions.clear()
        callActions.clear()
    }

    private fun <T> unambiguous(
        actions: ConcurrentHashMap<String, T>,
        contactName: String,
        contactOf: (T) -> String
    ): T? {
        val normalized = normalize(contactName)
        val matches = actions.entries.filter { normalize(contactOf(it.value)) == normalized }
        val live = matches.filterNot { entry ->
            val capturedAt = when (val value = entry.value) {
                is LiveReplyAction -> value.capturedAt
                is LiveCallAction -> value.capturedAt
                else -> 0L
            }
            val lifetime = if (entry.value is LiveCallAction) 5 * 60_000L else 2 * 60 * 60_000L
            val isExpired = expired(capturedAt, lifetime)
            if (isExpired) actions.remove(entry.key, entry.value)
            isExpired
        }
        return live.singleOrNull()?.value
    }

    private fun key(normalizedName: String, sourcePackage: String): String =
        "${sourcePackage.trim().lowercase()}::$normalizedName"

    private fun normalize(name: String): String = name.trim().lowercase()
    private fun expired(capturedAt: Long, lifetimeMs: Long): Boolean =
        System.currentTimeMillis() - capturedAt > lifetimeMs
}
