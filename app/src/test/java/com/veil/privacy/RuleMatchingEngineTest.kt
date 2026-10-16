package com.veil.privacy

import android.app.Notification
import android.content.Context
import android.os.Bundle
import android.os.Process
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import com.veil.privacy.model.ParsedNotificationInfo
import com.veil.privacy.model.ProtectionRule
import com.veil.privacy.privacy.RuleMatchingEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RuleMatchingEngineTest {

    private lateinit var context: Context
    private lateinit var engine: RuleMatchingEngine

    private val ruleA = ProtectionRule(
        id = 1,
        contactName = "Jane Doe",
        contactPhoneNumber = "+1 555-019-2834",
        decoyIdentity = "Work",
        decoyMessage = "New message",
        isEnabled = true
    )

    private val ruleB = ProtectionRule(
        id = 2,
        contactName = "Carlos Mendez",
        contactPhoneNumber = "+34 612 345 678",
        decoyIdentity = "Calendar",
        decoyMessage = "Task reminder",
        isEnabled = true
    )

    private val ruleC = ProtectionRule(
        id = 3,
        contactName = "Dr. Yuki Tanaka",
        contactPhoneNumber = "+81 90-1234-5678",
        decoyIdentity = "Cloud Service",
        decoyMessage = "Sync completed",
        isEnabled = true
    )

    private val allRules = listOf(ruleA, ruleB, ruleC)

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        engine = RuleMatchingEngine()
    }

    private fun createSbn(
        title: String,
        text: String,
        isGroup: Boolean = false,
        conversationTitle: String? = null
    ): StatusBarNotification {
        val builder = NotificationCompat.Builder(context, "test_channel")
            .setContentTitle(title)
            .setContentText(text)

        if (isGroup || conversationTitle != null) {
            val extras = Bundle().apply {
                if (isGroup) putBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION, true)
                if (conversationTitle != null) putCharSequence(Notification.EXTRA_CONVERSATION_TITLE, conversationTitle)
            }
            builder.addExtras(extras)
        }

        val notification = builder.build()

        return StatusBarNotification(
            "com.whatsapp",
            "com.whatsapp",
            101,
            "tag_1",
            1000,
            1000,
            0,
            notification,
            Process.myUserHandle(),
            System.currentTimeMillis()
        )
    }

    @Test
    fun testIndependentMultiRuleMatching_ContactA() {
        val sbn = createSbn(title = "Jane Doe", text = "Are you free for lunch?")
        val parsed = ParsedNotificationInfo(
            sender = "Jane Doe",
            message = "Are you free for lunch?",
            isCall = false,
            packageName = "com.whatsapp",
            notificationKey = "key_1"
        )

        val matched = engine.evaluateMatch(sbn, parsed, allRules)
        assertNotNull(matched)
        assertEquals("Jane Doe", matched?.contactName)
        assertEquals("Work", matched?.decoyIdentity)
        assertEquals("New message", matched?.decoyMessage)
    }

    @Test
    fun testIndependentMultiRuleMatching_ContactB() {
        val sbn = createSbn(title = "Carlos Mendez", text = "Meeting rescheduled to 3pm")
        val parsed = ParsedNotificationInfo(
            sender = "Carlos Mendez",
            message = "Meeting rescheduled to 3pm",
            isCall = false,
            packageName = "com.whatsapp",
            notificationKey = "key_2"
        )

        val matched = engine.evaluateMatch(sbn, parsed, allRules)
        assertNotNull(matched)
        assertEquals("Carlos Mendez", matched?.contactName)
        assertEquals("Calendar", matched?.decoyIdentity)
        assertEquals("Task reminder", matched?.decoyMessage)
    }

    @Test
    fun testIndependentMultiRuleMatching_ContactC() {
        val sbn = createSbn(title = "+81 90-1234-5678", text = "Reports are uploaded")
        val parsed = ParsedNotificationInfo(
            sender = "+81 90-1234-5678",
            message = "Reports are uploaded",
            isCall = false,
            packageName = "com.whatsapp",
            notificationKey = "key_3"
        )

        val matched = engine.evaluateMatch(sbn, parsed, allRules)
        assertNotNull(matched)
        assertEquals("Dr. Yuki Tanaka", matched?.contactName)
        assertEquals("Cloud Service", matched?.decoyIdentity)
        assertEquals("Sync completed", matched?.decoyMessage)
    }

    @Test
    fun testSafety_UnrelatedSenderFailsOpen() {
        val sbn = createSbn(title = "Random Person", text = "Hello there")
        val parsed = ParsedNotificationInfo(
            sender = "Random Person",
            message = "Hello there",
            isCall = false,
            packageName = "com.whatsapp",
            notificationKey = "key_4"
        )

        val matched = engine.evaluateMatch(sbn, parsed, allRules)
        assertNull("Unrelated contact must not be disguised", matched)
    }

    @Test
    fun testSafety_GroupChatMerelyMentioningNameFailsOpen() {
        // In a group chat "Project Team", someone mentions Jane Doe: "Did you ask Jane Doe?"
        val sbn = createSbn(
            title = "Project Team",
            text = "Bob: Did you ask Jane Doe?",
            isGroup = true,
            conversationTitle = "Project Team"
        )
        val parsed = ParsedNotificationInfo(
            sender = "Project Team",
            message = "Bob: Did you ask Jane Doe?",
            isCall = false,
            packageName = "com.whatsapp",
            notificationKey = "key_group"
        )

        val matched = engine.evaluateMatch(sbn, parsed, allRules)
        assertNull("Group chat merely mentioning contact must fail open", matched)
    }

    @Test
    fun testSafety_WhatsAppSystemAlertFailsOpen() {
        val sbn = createSbn(title = "WhatsApp", text = "Checking for new messages")
        val parsed = ParsedNotificationInfo(
            sender = "WhatsApp",
            message = "Checking for new messages",
            isCall = false,
            packageName = "com.whatsapp",
            notificationKey = "key_system"
        )

        val matched = engine.evaluateMatch(sbn, parsed, allRules)
        assertNull("WhatsApp system alert must never be disguised", matched)
    }

    @Test
    fun testSafety_DisabledRuleIsNotDisguised() {
        val disabledRule = ruleA.copy(isEnabled = false)
        val sbn = createSbn(title = "Jane Doe", text = "Hey")
        val parsed = ParsedNotificationInfo(
            sender = "Jane Doe",
            message = "Hey",
            isCall = false,
            packageName = "com.whatsapp",
            notificationKey = "key_disabled"
        )

        val matched = engine.evaluateMatch(sbn, parsed, listOf(disabledRule))
        assertNull("Disabled rule must fail open", matched)
    }

    @Test
    fun testPhoneNumberMatching_LocalSuffixVariation() {
        // Rule has +1 555-019-2834, incoming notification exposes digits without dial code "5550192834"
        val sbn = createSbn(title = "5550192834", text = "Hey")
        val parsed = ParsedNotificationInfo(
            sender = "5550192834",
            message = "Hey",
            isCall = false,
            packageName = "com.whatsapp",
            notificationKey = "key_digits"
        )

        val matched = engine.evaluateMatch(sbn, parsed, allRules)
        assertNotNull(matched)
        assertEquals("Jane Doe", matched?.contactName)
    }

    @Test
    fun testMultiContact_ThreeSimultaneousContactsWithIndependentDecoys() {
        val contact1 = ProtectionRule(
            id = 10,
            contactName = "Alice Anderson",
            contactPhoneNumber = "+1 555-101-1001",
            decoyIdentity = "Amazon Logistics",
            decoyMessage = "Package out for delivery",
            isEnabled = true
        )
        val contact2 = ProtectionRule(
            id = 20,
            contactName = "Bob Builder",
            contactPhoneNumber = "+1 555-202-2002",
            decoyIdentity = "Google Calendar",
            decoyMessage = "Team Standup in 10 mins",
            isEnabled = true
        )
        val contact3 = ProtectionRule(
            id = 30,
            contactName = "Charlie Chaplin",
            contactPhoneNumber = "+1 555-303-3003",
            decoyIdentity = "System Alert",
            decoyMessage = "Cloud backup complete",
            isEnabled = true
        )

        val activeList = listOf(contact1, contact2, contact3)

        // Test Contact 1
        val parsed1 = ParsedNotificationInfo(
            sender = "Alice Anderson",
            message = "Hey Alice here",
            isCall = false,
            packageName = "com.whatsapp",
            notificationKey = "k1"
        )
        val match1 = engine.evaluateMatch(createSbn("Alice Anderson", "Hey Alice here"), parsed1, activeList)
        assertNotNull(match1)
        assertEquals("Alice Anderson", match1?.contactName)
        assertEquals("Amazon Logistics", match1?.decoyIdentity)
        assertEquals("Package out for delivery", match1?.decoyMessage)

        // Test Contact 2
        val parsed2 = ParsedNotificationInfo(
            sender = "+1 555-202-2002",
            message = "Can we fix it?",
            isCall = false,
            packageName = "com.whatsapp",
            notificationKey = "k2"
        )
        val match2 = engine.evaluateMatch(createSbn("+1 555-202-2002", "Can we fix it?"), parsed2, activeList)
        assertNotNull(match2)
        assertEquals("Bob Builder", match2?.contactName)
        assertEquals("Google Calendar", match2?.decoyIdentity)
        assertEquals("Team Standup in 10 mins", match2?.decoyMessage)

        // Test Contact 3
        val parsed3 = ParsedNotificationInfo(
            sender = "Charlie Chaplin",
            message = "Cinema show tonight",
            isCall = false,
            packageName = "com.whatsapp",
            notificationKey = "k3"
        )
        val match3 = engine.evaluateMatch(createSbn("Charlie Chaplin", "Cinema show tonight"), parsed3, activeList)
        assertNotNull(match3)
        assertEquals("Charlie Chaplin", match3?.contactName)
        assertEquals("System Alert", match3?.decoyIdentity)
        assertEquals("Cloud backup complete", match3?.decoyMessage)
    }

    @Test
    fun testMultiNumberContact_MatchesExactSelectedNumber() {
        // User selected work number for Michael, not his personal number
        val michaelRule = ProtectionRule(
            id = 40,
            contactName = "Michael Scott",
            contactPhoneNumber = "+1 555-111-2222",
            decoyIdentity = "Dunder Mifflin",
            decoyMessage = "Paper order processed",
            isEnabled = true
        )

        // Message from the selected work number
        val parsedWork = ParsedNotificationInfo(
            sender = "5551112222",
            message = "That's what she said",
            isCall = false,
            packageName = "com.whatsapp",
            notificationKey = "k_work"
        )
        val matchWork = engine.evaluateMatch(createSbn("5551112222", "That's what she said"), parsedWork, listOf(michaelRule))
        assertNotNull(matchWork)
        assertEquals("Michael Scott", matchWork?.contactName)
        assertEquals("Dunder Mifflin", matchWork?.decoyIdentity)

        // Message from an unselected second number (e.g. personal number +1 555-999-8888)
        val parsedOther = ParsedNotificationInfo(
            sender = "5559998888",
            message = "Hey from personal phone",
            isCall = false,
            packageName = "com.whatsapp",
            notificationKey = "k_other"
        )
        val matchOther = engine.evaluateMatch(createSbn("5559998888", "Hey from personal phone"), parsedOther, listOf(michaelRule))
        assertNull("Unselected phone number must not match the protected rule", matchOther)
    }

    @Test
    fun testCommonTokenCollisionPrevention_JohnSmithVsJohnDoe() {
        val johnSmith = ProtectionRule(
            id = 51,
            contactName = "John Smith",
            contactPhoneNumber = "+1 555-111-1111",
            decoyIdentity = "Amazon",
            decoyMessage = "Order shipped",
            isEnabled = true
        )
        val johnDoe = ProtectionRule(
            id = 52,
            contactName = "John Doe",
            contactPhoneNumber = "+1 555-222-2222",
            decoyIdentity = "Google Calendar",
            decoyMessage = "Meeting at 2pm",
            isEnabled = true
        )

        val rules = listOf(johnSmith, johnDoe)

        // Notification explicitly from "John Doe"
        val parsedDoe = ParsedNotificationInfo(
            sender = "John Doe",
            message = "See you at 2",
            isCall = false,
            packageName = "com.whatsapp",
            notificationKey = "k_doe"
        )
        val matchDoe = engine.evaluateMatch(createSbn("John Doe", "See you at 2"), parsedDoe, rules)
        assertNotNull(matchDoe)
        assertEquals("Must match John Doe, not John Smith", "John Doe", matchDoe?.contactName)
        assertEquals("Google Calendar", matchDoe?.decoyIdentity)

        // Notification explicitly from "John Smith"
        val parsedSmith = ParsedNotificationInfo(
            sender = "John Smith",
            message = "Report ready",
            isCall = false,
            packageName = "com.whatsapp",
            notificationKey = "k_smith"
        )
        val matchSmith = engine.evaluateMatch(createSbn("John Smith", "Report ready"), parsedSmith, rules)
        assertNotNull(matchSmith)
        assertEquals("Must match John Smith", "John Smith", matchSmith?.contactName)
        assertEquals("Amazon", matchSmith?.decoyIdentity)
    }
}
