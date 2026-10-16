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
}
