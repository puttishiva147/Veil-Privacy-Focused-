package com.veil.privacy

import android.app.Notification
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import com.veil.privacy.notification.NotificationParser
import org.junit.Assert.assertEquals
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
class NotificationParserTest {

    private lateinit var parser: NotificationParser
    private lateinit var context: Context

    @Before
    fun setup() {
        parser = NotificationParser()
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun testRejectsNonWhatsAppPackage() {
        val notification = NotificationCompat.Builder(context, "test")
            .setContentTitle("Jane Doe")
            .setContentText("Hey")
            .build()

        val result = parser.parseNotification(
            notification = notification,
            packageName = "com.twitter.android",
            notificationKey = "key_1",
            postTime = 1000L,
            ownPackageName = "com.veil.privacy"
        )
        assertNull(result)
    }

    @Test
    fun testRejectsVeilOwnNotifications() {
        val notification = NotificationCompat.Builder(context, "test")
            .setContentTitle("Decoy Sender")
            .setContentText("Decoy text")
            .build()

        val result = parser.parseNotification(
            notification = notification,
            packageName = "com.veil.privacy",
            notificationKey = "key_1",
            postTime = 1000L,
            ownPackageName = "com.veil.privacy"
        )
        assertNull(result)
    }

    @Test
    fun testParsesWhatsAppStandardNotification() {
        val notification = NotificationCompat.Builder(context, "test")
            .setContentTitle("Jane Doe")
            .setContentText("Hello there")
            .build()

        val result = parser.parseNotification(
            notification = notification,
            packageName = "com.whatsapp",
            notificationKey = "key_1",
            postTime = 5000L,
            ownPackageName = "com.veil.privacy"
        )

        assertNotNull(result)
        assertEquals("Jane Doe", result?.sender)
        assertEquals("Hello there", result?.message)
        assertEquals(false, result?.isCall)
        assertEquals("com.whatsapp", result?.packageName)
    }

    @Test
    fun testCleansGroupedNotificationSenderName() {
        val notification = NotificationCompat.Builder(context, "test")
            .setContentTitle("Jane Doe (3 messages)")
            .setContentText("Latest message")
            .build()

        val result = parser.parseNotification(
            notification = notification,
            packageName = "com.whatsapp",
            notificationKey = "key_1",
            postTime = 5000L,
            ownPackageName = "com.veil.privacy"
        )

        assertNotNull(result)
        assertEquals("Jane Doe", result?.sender)
    }

    @Test
    fun testDetectsWhatsAppIncomingCall() {
        val notification = NotificationCompat.Builder(context, "test")
            .setContentTitle("Jane Doe")
            .setContentText("Incoming voice call")
            .setCategory(Notification.CATEGORY_CALL)
            .build()

        val result = parser.parseNotification(
            notification = notification,
            packageName = "com.whatsapp",
            notificationKey = "key_call",
            postTime = 5000L,
            ownPackageName = "com.veil.privacy"
        )

        assertNotNull(result)
        assertTrue(result?.isCall == true)
        assertEquals("Jane Doe", result?.sender)
    }

    @Test
    fun testSafelyHandlesEmptyOrMissingNotificationData() {
        val emptyNotification = NotificationCompat.Builder(context, "test").build()

        val result = parser.parseNotification(
            notification = emptyNotification,
            packageName = "com.whatsapp",
            notificationKey = "key_empty",
            postTime = 5000L,
            ownPackageName = "com.veil.privacy"
        )

        assertNull(result)
    }

    @Test
    fun testParsesTelegramAndSignalNotifications() {
        val telegramNotif = NotificationCompat.Builder(context, "test")
            .setContentTitle("Alice Telegram")
            .setContentText("Meeting at 5")
            .build()

        val tgResult = parser.parseNotification(
            notification = telegramNotif,
            packageName = "org.telegram.messenger",
            notificationKey = "tg_1",
            postTime = 6000L,
            ownPackageName = "com.veil.privacy"
        )
        assertNotNull(tgResult)
        assertEquals("Alice Telegram", tgResult?.sender)
        assertEquals("Meeting at 5", tgResult?.message)

        val signalNotif = NotificationCompat.Builder(context, "test")
            .setContentTitle("Bob Signal")
            .setContentText("Secret update")
            .build()

        val signalResult = parser.parseNotification(
            notification = signalNotif,
            packageName = "org.thoughtcrime.securesms",
            notificationKey = "sig_1",
            postTime = 7000L,
            ownPackageName = "com.veil.privacy"
        )
        assertNotNull(signalResult)
        assertEquals("Bob Signal", signalResult?.sender)
        assertEquals("Secret update", signalResult?.message)
    }

    @Test
    fun testParsesSimulationNotifications() {
        val simNotif = NotificationCompat.Builder(context, "test")
            .setContentTitle("Test Sender")
            .setContentText("Test simulation alert")
            .build()

        val simResult = parser.parseNotification(
            notification = simNotif,
            packageName = com.veil.privacy.model.SupportedApp.PKG_SIMULATION,
            notificationKey = "sim_1",
            postTime = 8000L,
            ownPackageName = "com.veil.privacy"
        )
        assertNotNull(simResult)
        assertEquals("Test Sender", simResult?.sender)
    }
}
