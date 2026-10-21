package com.veil.privacy

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import com.veil.privacy.notification.NotificationReplyRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NotificationReplyRegistryTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        NotificationReplyRegistry.clearAll()
    }

    @Test
    fun testFindReplyAction_returnsActionWhenPresent() {
        val remoteInput = RemoteInput.Builder("key_text_reply")
            .setLabel("Reply")
            .build()

        val intent = Intent("com.whatsapp.REPLY_ACTION")
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val action = Notification.Action.Builder(
            null,
            "Reply",
            pendingIntent
        ).addRemoteInput(remoteInput).build()

        val notification = Notification.Builder(context, "test_channel")
            .setContentTitle("Rahul")
            .setContentText("Bhai kaha hai?")
            .addAction(action)
            .build()

        val pair = NotificationReplyRegistry.findReplyAction(notification)
        assertNotNull("Should locate reply action with RemoteInput", pair)
        assertEquals("key_text_reply", pair?.second?.resultKey)
    }

    @Test
    fun testFindReplyAction_returnsNullWhenNoRemoteInput() {
        val intent = Intent("com.whatsapp.MARK_READ")
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            2,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val action = Notification.Action.Builder(null, "Mark as Read", pendingIntent).build()

        val notification = Notification.Builder(context, "test_channel")
            .setContentTitle("Rahul")
            .setContentText("Hello")
            .addAction(action)
            .build()

        val pair = NotificationReplyRegistry.findReplyAction(notification)
        assertNull("Should return null when action has no RemoteInput", pair)
    }

    @Test
    fun removingCallNotificationClearsCallControlsButKeepsReplyToken() {
        val remoteInput = RemoteInput.Builder("reply_key_stale_test").setLabel("Reply").build()
        val replyIntent = PendingIntent.getBroadcast(
            context, 51, Intent("com.whatsapp.REPLY_STALE_TEST"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val callIntent = PendingIntent.getBroadcast(
            context, 52, Intent("com.whatsapp.CALL_STALE_TEST"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val replyAction = Notification.Action.Builder(null, "Reply", replyIntent)
            .addRemoteInput(remoteInput).build()

        NotificationReplyRegistry.register("Rahul", "same_notification_key", replyAction, remoteInput)
        NotificationReplyRegistry.registerCall(
            "Rahul", "same_notification_key", callIntent, callIntent
        )

        assertNotNull(NotificationReplyRegistry.getLiveCallAction("Rahul"))
        assertNotNull(NotificationReplyRegistry.getLiveReplyAction("Rahul"))

        NotificationReplyRegistry.removeCallByKey("same_notification_key")

        assertNull(NotificationReplyRegistry.getLiveCallAction("Rahul"))
        assertNotNull("Reply token should survive VEIL dismissing the source notification",
            NotificationReplyRegistry.getLiveReplyAction("Rahul"))
    }

    @Test
    fun testRegisterAndLifecycle() {
        val remoteInput = RemoteInput.Builder("reply_key")
            .setLabel("Reply")
            .build()

        val intent = Intent("com.whatsapp.REPLY")
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            3,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val action = Notification.Action.Builder(null, "Reply", pendingIntent)
            .addRemoteInput(remoteInput)
            .build()

        assertFalse(NotificationReplyRegistry.isReplyActionLive("Rahul"))

        // Register
        NotificationReplyRegistry.register("Rahul", "key_12345", action, remoteInput)

        assertTrue("Should report live reply available for Rahul", NotificationReplyRegistry.isReplyActionLive("Rahul"))
        assertTrue("Should match case-insensitively", NotificationReplyRegistry.isReplyActionLive("rahul"))

        // Dispatch reply
        val sent = NotificationReplyRegistry.sendReply(context, "Rahul", "Bas nikal raha hu")
        assertTrue("Dispatch should succeed via RemoteInput PendingIntent", sent)

        // Notification dismissed by Android
        NotificationReplyRegistry.removeByKey("key_12345")
        assertFalse("Should report false once notification removed", NotificationReplyRegistry.isReplyActionLive("Rahul"))
    }

    @Test
    fun sameContactNameOnDifferentPlatformsNeverSelectsWrongReplyAction() {
        val remoteInput = RemoteInput.Builder("platform_reply_key").setLabel("Reply").build()

        fun register(platform: String, requestCode: Int) {
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                Intent("com.veil.test.REPLY.$requestCode"),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val action = Notification.Action.Builder(null, "Reply", pendingIntent)
                .addRemoteInput(remoteInput)
                .build()
            NotificationReplyRegistry.register(
                contactName = "Alex",
                notificationKey = "notification-$requestCode",
                action = action,
                remoteInput = remoteInput,
                sourcePackage = platform
            )
        }

        register("com.whatsapp", 61)
        register("org.telegram.messenger", 62)

        assertEquals(
            "com.whatsapp",
            NotificationReplyRegistry.getLiveReplyAction("Alex", "com.whatsapp")?.sourcePackage
        )
        assertEquals(
            "org.telegram.messenger",
            NotificationReplyRegistry.getLiveReplyAction("Alex", "org.telegram.messenger")?.sourcePackage
        )
        assertNull(
            "A caller without platform identity must fail closed when names collide",
            NotificationReplyRegistry.getLiveReplyAction("Alex")
        )
        assertFalse(
            "A platform-ambiguous reply must not be dispatched",
            NotificationReplyRegistry.sendReply(context, "Alex", "Hello")
        )
    }
}
