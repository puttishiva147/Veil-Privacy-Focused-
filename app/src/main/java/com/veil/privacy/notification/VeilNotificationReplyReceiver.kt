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
import kotlinx.coroutines.launch

/**
 * Handles inline replies dispatched directly from VEIL replacement notifications.
 *
 * Forwards the reply via Android's RemoteInput PendingIntent directly to WhatsApp,
 * updates the local encrypted vault, and refreshes the notification in place.
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

        val contactName = intent.getStringExtra(EXTRA_CONTACT_NAME) ?: return
        val conversationId = intent.getStringExtra(EXTRA_CONVERSATION_ID) ?: contactName.lowercase().trim()
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)

        // Capture the source package before dispatch so outgoing history preserves the
        // actual app (not ProtectedEvent's WhatsApp default) for future source-app routing.
        val liveAction = NotificationReplyRegistry.getLiveReplyAction(contactName)
        val dispatched = NotificationReplyRegistry.sendReply(context, contactName, replyText)
        Log.d(TAG, "Inline reply dispatch attempted; acceptedByPendingIntent=$dispatched (delivery is not confirmed)")

        // 2. Persist outgoing message in local encrypted Room database
        val db = VeilDatabase.getInstance(context)
        CoroutineScope(Dispatchers.IO).launch {
            val outgoingEvent = ProtectedEvent(
                sourcePackage = liveAction?.sourcePackage
                    ?: com.veil.privacy.model.SupportedApp.PKG_WHATSAPP,
                realSender = "You",
                realMessage = replyText,
                isOutgoing = true,
                replyStatus = if (dispatched) "DISPATCHED_NOT_CONFIRMED" else "FAILED_DISPATCH",
                conversationId = conversationId,
                timestamp = System.currentTimeMillis()
            )
            db.protectedEventDao().insert(outgoingEvent)
        }

        // 3. Update the notification in-place to give immediate feedback to user
        if (notificationId != 0) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            val statusText = if (dispatched) "Reply handed to Android; delivery not confirmed" else "Reply could not be dispatched; reopen VEIL to retry"

            val updatedNotification = NotificationCompat.Builder(context, VeilNotificationManager.CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_veil_notification)
                .setContentTitle("🔒 VEIL")
                .setContentText(statusText)
                .setStyle(NotificationCompat.BigTextStyle().bigText(statusText))
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setTimeoutAfter(8000)
                .setAutoCancel(true)
                .build()

            notificationManager?.notify(notificationId, updatedNotification)
        }
    }
}
