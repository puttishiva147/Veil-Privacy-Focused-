package com.veil.privacy

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.veil.privacy.model.ParsedNotificationInfo
import com.veil.privacy.model.ProtectedContact
import com.veil.privacy.privacy.DecoyGenerator
import com.veil.privacy.privacy.PrivacyEngine
import com.veil.privacy.storage.VeilPrefs
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
class PrivacyEngineTest {

    private lateinit var privacyEngine: PrivacyEngine
    private lateinit var prefs: VeilPrefs
    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        prefs = VeilPrefs(context)
        prefs.isVeilEnabled = true
        prefs.isProtectedCallsEnabled = true
        privacyEngine = PrivacyEngine(DecoyGenerator())
    }

    @Test
    fun testExactProtectedContactMatching() {
        val contacts = listOf(
            ProtectedContact(id = 1, name = "Jane Doe", phoneNumber = "+15550192834"),
            ProtectedContact(id = 2, name = "Alex Rivera", phoneNumber = "+15550199999")
        )

        val matched = privacyEngine.findMatchingContact("Jane Doe", contacts)
        assertNotNull(matched)
        assertEquals("Jane Doe", matched?.displayName)
    }

    @Test
    fun testPhoneNumberMatchingWithFormattingVariations() {
        val contacts = listOf(
            ProtectedContact(id = 1, name = "Dr. Smith", phoneNumber = "+1 (555) 234-5678")
        )

        // Matches formatted number from notification
        val matchFormatted = privacyEngine.findMatchingContact("+1 555 234 5678", contacts)
        assertNotNull(matchFormatted)
        assertEquals("Dr. Smith", matchFormatted?.displayName)

        // Matches unformatted digit string from notification
        val matchDigits = privacyEngine.findMatchingContact("15552345678", contacts)
        assertNotNull(matchDigits)
        assertEquals("Dr. Smith", matchDigits?.displayName)

        // Matches local 10-digit suffix without international prefix
        val matchLocal = privacyEngine.findMatchingContact("5552345678", contacts)
        assertNotNull(matchLocal)
        assertEquals("Dr. Smith", matchLocal?.displayName)
    }

    @Test
    fun testCaseInsensitiveMatching() {
        val contacts = listOf(
            ProtectedContact(id = 1, name = "Sarah Connor")
        )

        val matchedLower = privacyEngine.findMatchingContact("sarah connor", contacts)
        val matchedUpper = privacyEngine.findMatchingContact("SARAH CONNOR", contacts)
        val matchedMixed = privacyEngine.findMatchingContact("sArAh CoNnOr", contacts)

        assertNotNull(matchedLower)
        assertNotNull(matchedUpper)
        assertNotNull(matchedMixed)
        assertEquals("Sarah Connor", matchedLower?.displayName)
    }

    @Test
    fun testWhitespaceNormalization() {
        val contacts = listOf(
            ProtectedContact(id = 1, name = "Marcus Wright")
        )

        val matchedWithLeadingTrailing = privacyEngine.findMatchingContact("  Marcus Wright  ", contacts)
        val matchedWithMultipleSpaces = privacyEngine.findMatchingContact("Marcus    Wright", contacts)

        assertNotNull(matchedWithLeadingTrailing)
        assertNotNull(matchedWithMultipleSpaces)
        assertEquals("Marcus Wright", matchedWithLeadingTrailing?.displayName)
    }

    @Test
    fun testMultipleProtectedContacts() {
        val contacts = listOf(
            ProtectedContact(id = 1, name = "Elena Rostova", phoneNumber = "+447911123456"),
            ProtectedContact(id = 2, name = "Carlos Mendez", phoneNumber = "+34612345678"),
            ProtectedContact(id = 3, name = "Yuki Tanaka", phoneNumber = "+819012345678")
        )

        val matched1 = privacyEngine.findMatchingContact("carlos mendez", contacts)
        val matched2 = privacyEngine.findMatchingContact("+819012345678", contacts)

        assertEquals("Carlos Mendez", matched1?.displayName)
        assertEquals("Yuki Tanaka", matched2?.displayName)
    }

    @Test
    fun testNonProtectedContactReturnsNull() {
        val contacts = listOf(
            ProtectedContact(id = 1, name = "Known Contact", phoneNumber = "+15551112233")
        )

        val matched = privacyEngine.findMatchingContact("Unknown Stranger", contacts)
        assertNull(matched)

        val matchedWrongNumber = privacyEngine.findMatchingContact("+15559998877", contacts)
        assertNull(matchedWrongNumber)
    }

    @Test
    fun testDisabledContactIsNotDisguised() {
        val contacts = listOf(
            ProtectedContact(id = 1, name = "Paused Target", phoneNumber = "+15550001111", isEnabled = false)
        )

        val incoming = ParsedNotificationInfo(
            sender = "Paused Target",
            message = "Confidential notification",
            isCall = false,
            packageName = "com.whatsapp",
            notificationKey = "key_1"
        )

        val event = privacyEngine.evaluateAndDisguise(incoming, contacts, prefs)
        assertNull(event)
    }

    @Test
    fun testVeilDisabledMasterSwitch() {
        prefs.isVeilEnabled = false
        val contacts = listOf(ProtectedContact(id = 1, name = "Target User", phoneNumber = "+15553334444"))

        val incoming = ParsedNotificationInfo(
            sender = "Target User",
            message = "Confidential notification",
            isCall = false,
            packageName = "com.whatsapp",
            notificationKey = "key_1"
        )

        val event = privacyEngine.evaluateAndDisguise(incoming, contacts, prefs)
        assertNull(event)
    }

    @Test
    fun testProtectedEventCreation() {
        val contacts = listOf(ProtectedContact(id = 1, name = "Confidential Source", phoneNumber = "+15557778888"))

        val incoming = ParsedNotificationInfo(
            sender = "+15557778888",
            message = "Critical update ready",
            isCall = false,
            packageName = "com.whatsapp",
            notificationKey = "key_1"
        )

        val event = privacyEngine.evaluateAndDisguise(incoming, contacts, prefs)
        assertNotNull(event)
        assertEquals("+15557778888", event?.realSender)
        assertEquals("Critical update ready", event?.realMessage)
        assertTrue(event?.decoySender?.isNotBlank() == true)
        assertTrue(event?.decoyMessage?.isNotBlank() == true)
        assertEquals("com.whatsapp", event?.sourcePackage)
    }

    @Test
    fun testCallDisguiseWhenCallsEnabled() {
        prefs.isProtectedCallsEnabled = true
        val contacts = listOf(ProtectedContact(id = 1, name = "Call Target", phoneNumber = "+15554445555"))

        val incoming = ParsedNotificationInfo(
            sender = "Call Target",
            message = "Incoming voice call",
            isCall = true,
            packageName = "com.whatsapp",
            notificationKey = "key_call"
        )

        val event = privacyEngine.evaluateAndDisguise(incoming, contacts, prefs)
        assertNotNull(event)
        assertTrue(event?.isCall == true)
    }
}
