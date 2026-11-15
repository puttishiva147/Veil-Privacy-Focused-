package com.veil.privacy.notification

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import com.veil.privacy.R
import com.veil.privacy.communication.VeilTransportManager
import com.veil.privacy.model.SupportedApp
import com.veil.privacy.model.TransportMessage
import com.veil.privacy.storage.VeilDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Handles inline replies dispatched directly from VEIL replacement notifications.
 *
 * Queues the reply through VEIL's coded transport pipeline and refreshes the notification in place.
 */
class VeilNotificationReplyReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_REPLY = "com.veil.privacy.ACTION_NOTIFICATION_REPLY"
        const val EXTRA_CONTACT_NAME = "extra_contact_name"
        const val EXTRA_CONVERSATION_ID = "extra_conversation_id"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
        const val KEY_TEXT_REPLY = "key_text_reply"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_REPLY) return

        val results = RemoteInput.getResultsFromIntent(intent) ?: return
        val replyText = results.getCharSequence(KEY_TEXT_REPLY)?.toString()?.trim().orEmpty()
        if (replyText.isBlank()) return

        val contactName = intent.getStringExtra(EXTRA_CONTACT_NAME) ?: return
        val conversationId = intent.getStringExtra(EXTRA_CONVERSATION_ID) ?: contactName.lowercase().trim()
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)

        // Queue through VEIL's language/transport pipeline. Never send this reply as plaintext.
        val pendingResult = goAsync()
        val transportManager = VeilTransportManager(context)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val sourcePackage = transportManager.selectAvailableTransport(contactName)
                    ?: VeilDatabase.getInstance(context).protectedEventDao()
                        .getMessagesForConversationSync(conversationId)
                        .lastOrNull { it.sourcePackage in listOf(
                            SupportedApp.PKG_WHATSAPP,
                            SupportedApp.PKG_WHATSAPP_BUSINESS,
                            SupportedApp.PKG_INSTAGRAM,
                            SupportedApp.PKG_SNAPCHAT
                        ) }?.sourcePackage
                    ?: SupportedApp.PKG_WHATSAPP

                val result = transportManager.queueOutgoing(
                    conversationId = conversationId,
                    contactName = contactName,
                    sourcePackage = sourcePackage,
                    plainText = replyText
                )

                val statusText = when (result.status) {
                    TransportMessage.WAITING_FOR_DICTIONARY ->
                        "Saved in VEIL; waiting for language dictionary. Not sent yet."
                    TransportMessage.WAITING_FOR_REPLY_ACTION ->
                        "Encoded message queued; waiting for a reply-enabled notification."
                    TransportMessage.DISPATCHED_NOT_CONFIRMED ->
                        "Encoded message handed to Android; delivery is not confirmed."
                    else -> result.detail
                }

                if (notificationId != 0) {
                    withContext(Dispatchers.Main) {
                        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
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
            } catch (error: Exception) {
                android.util.Log.e("VeilReplyReceiver", "Could not queue transport reply", error)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
