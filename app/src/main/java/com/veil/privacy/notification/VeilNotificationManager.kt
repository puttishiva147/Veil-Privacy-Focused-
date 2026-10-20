package com.veil.privacy.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.veil.privacy.R
import com.veil.privacy.model.ProtectedEvent
import com.veil.privacy.model.ProtectionRule
import com.veil.privacy.storage.VeilPrefs
import com.veil.privacy.ui.ProtectedChatConversationActivity

/**
 * Manages posting of privacy-safe VEIL notifications.
 *
 * Implements core requirement:
 * Creates privacy-safe notifications such as "🔒 VEIL — Protected message"
 * without exposing the protected person's real name or message content in the normal
 * notification presentation.
 */
class VeilNotificationManager(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "veil_privacy_channel"
        const val CHANNEL_SILENT_ID = "veil_privacy_silent_channel"
        const val CHANNEL_SERVICE_ID = "veil_privacy_service_channel"
        const val CHANNEL_SIMULATION_ID = "veil_simulation_channel"
        const val NOTIFICATION_GROUP_KEY = "com.veil.privacy.DISGUISED_GROUP"
        const val SIMULATION_NOTIFICATION_ID = 88888

        @Volatile
        private var INSTANCE: VeilNotificationManager? = null

        fun getInstance(context: Context): VeilNotificationManager {
            return INSTANCE ?: synchronized(this) {
                val instance = VeilNotificationManager(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }

    init {
        createNotificationChannels()
    }

    fun createNotificationChannel() {
        createNotificationChannels()
    }

    fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Primary Channel (High Priority with sound/vibration)
            val name = context.getString(R.string.channel_veil_name)
            val descriptionText = context.getString(R.string.channel_veil_desc)
            val primaryChannel = NotificationChannel(CHANNEL_ID, name, NotificationManager.IMPORTANCE_HIGH).apply {
                description = descriptionText
                enableLights(true)
                enableVibration(true)
                setShowBadge(true)
            }
            notificationManager.createNotificationChannel(primaryChannel)

            // Silent Channel (for rules with sound & vibration disabled)
            val silentChannel = NotificationChannel(CHANNEL_SILENT_ID, "$name (Silent)", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Silent disguised VEIL notifications"
                enableLights(false)
                enableVibration(false)
                setShowBadge(true)
            }
            notificationManager.createNotificationChannel(silentChannel)

            // Persistent Foreground Shield Channel
            val serviceChannel = NotificationChannel(CHANNEL_SERVICE_ID, "VEIL Protection Shield", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Ongoing notification keeping VEIL real-time protection active"
                enableLights(false)
                enableVibration(false)
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(serviceChannel)

            // Simulation Alert Channel (High Priority)
            val simulationChannel = NotificationChannel(CHANNEL_SIMULATION_ID, "Test & Simulation Alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Simulated incoming messaging notifications for real-time testing"
                enableLights(true)
                enableVibration(true)
                setShowBadge(true)
            }
            notificationManager.createNotificationChannel(simulationChannel)
        }
    }

    /**
     * Posts a simulated incoming messaging notification with real RemoteInput reply action.
     * This triggers the entire real-time pipeline through VeilNotificationListener.
     */
    fun postSimulationNotification(
        sender: String,
        message: String,
        isCall: Boolean = false
    ) {
        val notificationManager = NotificationManagerCompat.from(context)

        // RemoteInput for live reply simulation
        val replyBroadcastIntent = Intent(context, VeilNotificationReplyReceiver::class.java).apply {
            action = VeilNotificationReplyReceiver.ACTION_REPLY
            putExtra(VeilNotificationReplyReceiver.EXTRA_CONTACT_NAME, sender)
            putExtra(VeilNotificationReplyReceiver.EXTRA_CONVERSATION_ID, sender.lowercase().trim())
            putExtra(VeilNotificationReplyReceiver.EXTRA_NOTIFICATION_ID, SIMULATION_NOTIFICATION_ID)
        }
        val replyPendingIntent = PendingIntent.getBroadcast(
            context,
            SIMULATION_NOTIFICATION_ID,
            replyBroadcastIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        val remoteInput = androidx.core.app.RemoteInput.Builder(VeilNotificationReplyReceiver.KEY_TEXT_REPLY)
            .setLabel("Reply to $sender...")
            .build()

        val replyAction = NotificationCompat.Action.Builder(
            R.drawable.ic_veil_notification,
            "Reply",
            replyPendingIntent
        ).addRemoteInput(remoteInput)
            .setAllowGeneratedReplies(true)
            .build()

        val intent = Intent(context, com.veil.privacy.ui.ProtectedChatConversationActivity::class.java).apply {
            putExtra(com.veil.privacy.ui.ProtectedChatConversationActivity.EXTRA_CONTACT_NAME, sender)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            SIMULATION_NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_SIMULATION_ID)
            .setSmallIcon(R.drawable.ic_veil_notification)
            .setContentTitle(sender)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(if (isCall) NotificationCompat.CATEGORY_CALL else NotificationCompat.CATEGORY_MESSAGE)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .addAction(replyAction)
            .setWhen(System.currentTimeMillis())
            .setShowWhen(true)

        try {
            notificationManager.notify("VEIL_SIMULATION", SIMULATION_NOTIFICATION_ID, builder.build())
        } catch (_: SecurityException) {}
    }

    /**
     * Builds and posts a privacy-safe VEIL replacement notification tailored to the matched [ProtectionRule].
     * Tapping launches ProtectedChatConversationActivity directly.
     */
    fun postRuleDecoyNotification(event: ProtectedEvent, rule: ProtectionRule): Boolean {
        val notificationManager = NotificationManagerCompat.from(context)

        // PendingIntent to launch ProtectedChatConversationActivity
        val intent = Intent(context, ProtectedChatConversationActivity::class.java).apply {
            putExtra(ProtectedChatConversationActivity.EXTRA_CONTACT_NAME, event.realSender)
            putExtra(ProtectedChatConversationActivity.EXTRA_EVENT_ID, event.id)
            putExtra(ProtectedChatConversationActivity.EXTRA_SOURCE_PACKAGE, event.sourcePackage)
            putExtra(ProtectedChatConversationActivity.EXTRA_CONVERSATION_ID, event.conversationId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            event.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationId = (event.id % 50000 + 1000).toInt()
        val channelId = if (rule.soundEnabled || rule.vibrationEnabled) CHANNEL_ID else CHANNEL_SILENT_ID

        val visibility = when (rule.lockscreenVisibility) {
            1 -> NotificationCompat.VISIBILITY_PUBLIC
            -1 -> NotificationCompat.VISIBILITY_SECRET
            else -> NotificationCompat.VISIBILITY_PRIVATE
        }

        val resolvedSender = event.decoySender.trim().ifBlank {
            rule.decoyIdentity.trim().ifBlank {
                VeilPrefs.getInstance(context).defaultDecoySender.ifBlank { "Notification" }
            }
        }
        val resolvedMessage = event.decoyMessage.trim().ifBlank {
            rule.decoyMessage.trim().ifBlank {
                VeilPrefs.getInstance(context).defaultDecoyMessage.ifBlank { "New message received" }
            }
        }

        val (notifTitle, notifText) = when (rule.disguiseMode) {
            ProtectionRule.MODE_SILENT -> Pair("VEIL", "Notification received")
            else -> Pair(resolvedSender, resolvedMessage)
        }

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_veil_notification)
            .setContentTitle(notifTitle)
            .setContentText(notifText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notifText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(if (event.isCall) NotificationCompat.CATEGORY_CALL else NotificationCompat.CATEGORY_MESSAGE)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setWhen(event.timestamp)
            .setShowWhen(true)
            .setVisibility(visibility)
            .setGroup(NOTIFICATION_GROUP_KEY)
            .setColor(context.getColor(R.color.veil_accent_secure))

        // Attach Android RemoteInput inline reply action if live WhatsApp reply token is active
        if (!event.isCall && NotificationReplyRegistry.isReplyActionLive(event.realSender, event.sourcePackage)) {
            val replyBroadcastIntent = Intent(context, VeilNotificationReplyReceiver::class.java).apply {
                action = VeilNotificationReplyReceiver.ACTION_REPLY
                putExtra(VeilNotificationReplyReceiver.EXTRA_CONTACT_NAME, event.realSender)
                putExtra(VeilNotificationReplyReceiver.EXTRA_CONVERSATION_ID, event.conversationId)
                putExtra(VeilNotificationReplyReceiver.EXTRA_SOURCE_PACKAGE, event.sourcePackage)
                putExtra(VeilNotificationReplyReceiver.EXTRA_NOTIFICATION_ID, notificationId)
            }
            val replyPendingIntent = PendingIntent.getBroadcast(
                context,
                notificationId,
                replyBroadcastIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            val remoteInput = androidx.core.app.RemoteInput.Builder(VeilNotificationReplyReceiver.KEY_TEXT_REPLY)
                .setLabel("Reply anonymously...")
                .build()

            val replyAction = NotificationCompat.Action.Builder(
                R.drawable.ic_veil_notification,
                "Reply",
                replyPendingIntent
            ).addRemoteInput(remoteInput)
                .setAllowGeneratedReplies(true)
                .build()

            builder.addAction(replyAction)
        }

        if (!rule.soundEnabled) {
            builder.setSilent(true)
        }

        if (!rule.vibrationEnabled) {
            builder.setVibrate(longArrayOf(0))
        }

        return try {
            // This confirms that Android accepted the notify request, not that the OS
            // displayed it to the user (channels/OEM policy may still suppress it).
            if (!notificationManager.areNotificationsEnabled()) {
                false
            } else {
                notificationManager.notify(notificationId, builder.build())
                true
            }
        } catch (_: SecurityException) {
            // Do not let the listener cancel the source notification if VEIL cannot post.
            false
        } catch (_: RuntimeException) {
            false
        }
    }

    /**
     * Compatibility overload for legacy event posting.
     */
    fun postDecoyNotification(event: ProtectedEvent) {
        val defaultRule = ProtectionRule(
            contactName = event.realSender,
            decoyIdentity = event.decoySender,
            decoyMessage = event.decoyMessage
        )
        postRuleDecoyNotification(event, defaultRule)
    }
}
