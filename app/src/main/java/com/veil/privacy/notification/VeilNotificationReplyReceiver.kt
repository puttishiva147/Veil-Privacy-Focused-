package com.veil.privacy.notification

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import com.veil.privacy.R
import com.veil.privacy.model.ProtectedEvent
import com.veil.privacy.storage.VeilDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Handles inline replies dispatched from VEIL replacement notifications.
 *
 * A BroadcastReceiver's process may be reclaimed after onReceive returns. goAsync()
 * keeps the receiver alive while the outgoing event is persisted, and status wording
 * deliberately distinguishes dispatch acceptance from confirmed message delivery.
 */
class VeilNotificationReplyReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_REPLY = "com.veil.privacy.ACTION_NOTIFICATION_REPLY"
        const val EXTRA_CONTACT_NAME = "extra_contact_name"
        const val EXTRA_CONVERSATION_ID = "extra_conversation_id"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
        const val KEY_TEXT_REPLY = "key_text_reply"
        private const val TAG = "VeilReplyReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_REPLY) return

        val results = RemoteInput.getResultsFromIntent(intent) ?: return
        val replyText = results.getCharSequence(KEY_TEXT_REPLY)?.toString()?.trim().orEmpty()
        if (replyText.isBlank()) return

        val contactName = intent.getStringExtra(EXTRA_CONTACT_NAME)?.takeIf { it.isNotBlank() } ?: return
        val conversationId = intent.getStringExtra(EXTRA_CONVERSATION_ID)
            ?.takeIf { it.isNotBlank() } ?: contactName.lowercase().trim()
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)

        // This confirms only that Android accepted the PendingIntent dispatch, not delivery.
        val dispatched = NotificationReplyRegistry.sendReply(context, contactName, replyText)
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        scope.launch {
            var persisted = false
            try {
                val outgoingEvent = ProtectedEvent(
                    realSender = "You",
                    realMessage = replyText,
                    isOutgoing = true,
                    replyStatus = if (dispatched) "DISPATCHED_NOT_CONFIRMED" else "FAILED_DISPATCH",
                    conversationId = conversationId,
                    timestamp = System.currentTimeMillis()
                )
                VeilDatabase.getInstance(appContext).protectedEventDao().insert(outgoingEvent)
                persisted = true
            } catch (e: Exception) {
                // Never log reply text, contact identity, or conversation identifiers.
                Log.e(TAG, "Unable to persist outgoing reply status (${e.javaClass.simpleName})")
            } finally {
                try {
                    if (notificationId != 0) {
                        val statusText = when {
                            !dispatched -> "Reply could not be handed to Android. Reopen VEIL to retry."
                            persisted -> "Reply handed to Android; delivery is not confirmed."
                            else -> "Reply was handed to Android, but VEIL could not save its status."
                        }
                        val updatedNotification = NotificationCompat.Builder(
                            appContext,
                            VeilNotificationManager.CHANNEL_ID
                        )
                            .setSmallIcon(R.drawable.ic_veil_notification)
                            .setContentTitle("VEIL")
                            .setContentText(statusText)
                            .setStyle(NotificationCompat.BigTextStyle().bigText(statusText))
                            .setPriority(NotificationCompat.PRIORITY_LOW)
                            .setTimeoutAfter(8000)
                            .setAutoCancel(true)
                            .build()
                        (appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)
                            ?.notify(notificationId, updatedNotification)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Unable to update reply status notification (${e.javaClass.simpleName})")
                } finally {
                    pendingResult.finish()
                    scope.cancel()
                }
            }
        }
    }
}
