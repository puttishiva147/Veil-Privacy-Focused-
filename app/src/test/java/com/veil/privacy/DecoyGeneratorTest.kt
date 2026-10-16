package com.veil.privacy

import com.veil.privacy.model.NotificationType
import com.veil.privacy.privacy.DecoyGenerator
import com.veil.privacy.storage.VeilPrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DecoyGeneratorTest {

    private lateinit var generator: DecoyGenerator

    @Before
    fun setup() {
        generator = DecoyGenerator()
        generator.resetHistory()
    }

    @Test
    fun testDecoySelectionFromNonEmptyLists() {
        val senders = listOf("Ayush", "Kiran", "Rahul")
        val messages = listOf("Kya kar raha hai bhai?", "Bhai kaha hai?", "Kal milte hai?")

        val (sender, message) = generator.generateDecoy(senders, messages)

        assertTrue(senders.contains(sender))
        assertTrue(messages.contains(message))
    }

    @Test
    fun testEmptyDecoyListsFallbackGracefully() {
        val (sender, message) = generator.generateDecoy(emptyList(), emptyList())

        assertNotNull(sender)
        assertNotNull(message)
        assertEquals(DecoyGenerator.FALLBACK_DECOY_SENDER, sender)
        assertEquals(DecoyGenerator.FALLBACK_DECOY_MESSAGE, message)
    }

    @Test
    fun testSingleItemListsAlwaysReturnThatItem() {
        val senders = listOf("OnlySender")
        val messages = listOf("OnlyMessage")

        val (sender, message) = generator.generateDecoy(senders, messages)

        assertEquals("OnlySender", sender)
        assertEquals("OnlyMessage", message)
    }

    @Test
    fun testAvoidsImmediateRepetitionWhenMultipleChoicesExist() {
        val senders = listOf("SenderA", "SenderB")
        val messages = listOf("MsgA", "MsgB")

        val (firstSender, firstMsg) = generator.generateDecoy(senders, messages)
        val (secondSender, secondMsg) = generator.generateDecoy(senders, messages)

        // When only 2 items exist, the anti-repetition mechanism should pick the alternate
        assertNotEquals(firstSender, secondSender)
        assertNotEquals(firstMsg, secondMsg)
    }

    @Test
    fun testIndependentSelection() {
        val senders = listOf("Ayush", "Kiran", "Rahul", "Rohan")
        val messages = listOf("Msg1", "Msg2", "Msg3", "Msg4")

        // Over 20 iterations, verify that decoys are varied and not hardcoded to pairs
        val generatedPairs = mutableSetOf<Pair<String, String>>()
        for (i in 0 until 20) {
            val pair = generator.generateDecoy(senders, messages)
            generatedPairs.add(pair)
        }

        // Multiple distinct pairs should be generated
        assertTrue(generatedPairs.size > 1)
    }

    @Test
    fun testDynamicArrivalSelectionRotatesAcrossProfiles() {
        // Over multiple calls, verify that decoys are generated from multiple distinct profiles
        val allPossibleSenders = VeilPrefs.WORK_DECOY_NAMES +
                VeilPrefs.CALENDAR_DECOY_NAMES +
                VeilPrefs.DELIVERY_DECOY_NAMES +
                VeilPrefs.SYSTEM_DECOY_NAMES

        val distinctSenders = mutableSetOf<String>()
        val distinctMessages = mutableSetOf<String>()

        for (i in 0 until 30) {
            val (sender, message) = generator.generateDynamicDecoy(NotificationType.MESSAGE)
            assertTrue(allPossibleSenders.contains(sender))
            distinctSenders.add(sender)
            distinctMessages.add(message)
        }

        // Should have drawn from a broad diversity of profiles and identities
        assertTrue(distinctSenders.size > 3)
        assertTrue(distinctMessages.size > 3)
    }

    @Test
    fun testDynamicArrivalAvoidsRepeatingImmediatePreviousCombination() {
        var previousPair: Pair<String, String>? = null

        for (i in 0 until 25) {
            val currentPair = generator.generateDynamicDecoy(NotificationType.MESSAGE)
            if (previousPair != null) {
                // Must never repeat the immediately previous exact combination
                assertNotEquals(previousPair, currentPair)
            }
            previousPair = currentPair
        }
    }

    @Test
    fun testSeparateDecoyCombinationsForCalls() {
        val allCallSenders = VeilPrefs.WORK_CALL_NAMES +
                VeilPrefs.CALENDAR_CALL_NAMES +
                VeilPrefs.DELIVERY_CALL_NAMES +
                VeilPrefs.SYSTEM_CALL_NAMES

        val allCallMessages = VeilPrefs.WORK_CALL_MESSAGES +
                VeilPrefs.CALENDAR_CALL_MESSAGES +
                VeilPrefs.DELIVERY_CALL_MESSAGES +
                VeilPrefs.SYSTEM_CALL_MESSAGES

        for (i in 0 until 15) {
            val (sender, message) = generator.generateDynamicDecoy(NotificationType.CALL)
            assertTrue("Call sender should come from call pool: $sender", allCallSenders.contains(sender))
            assertTrue("Call message should come from call pool: $message", allCallMessages.contains(message))
        }
    }

    @Test
    fun testSeparateDecoyCombinationsForMediaAttachments() {
        val allMediaMessages = VeilPrefs.WORK_MEDIA_MESSAGES +
                VeilPrefs.CALENDAR_MEDIA_MESSAGES +
                VeilPrefs.DELIVERY_MEDIA_MESSAGES +
                VeilPrefs.SYSTEM_MEDIA_MESSAGES

        for (i in 0 until 15) {
            val (sender, message) = generator.generateDynamicDecoy(NotificationType.MEDIA_ATTACHMENT)
            assertTrue("Media message should come from media pool: $message", allMediaMessages.contains(message))
        }
    }

    @Test
    fun testCoherentMatchingWithinConstrainedProfile() {
        // Constrain to Work profile
        for (i in 0 until 10) {
            val (sender, message) = generator.generateDynamicDecoy(
                type = NotificationType.MESSAGE,
                preferredProfile = VeilPrefs.THEME_WORK
            )
            assertTrue("Sender should be Work sender: $sender", VeilPrefs.WORK_DECOY_NAMES.contains(sender))
            assertTrue("Message should be Work message: $message", VeilPrefs.WORK_DECOY_MESSAGES.contains(message))
        }

        // Constrain to Delivery profile
        for (i in 0 until 10) {
            val (sender, message) = generator.generateDynamicDecoy(
                type = NotificationType.MESSAGE,
                preferredProfile = VeilPrefs.THEME_DELIVERY
            )
            assertTrue("Sender should be Delivery sender: $sender", VeilPrefs.DELIVERY_DECOY_NAMES.contains(sender))
            assertTrue("Message should be Delivery message: $message", VeilPrefs.DELIVERY_DECOY_MESSAGES.contains(message))
        }

        // Constrain to Calendar profile
        for (i in 0 until 10) {
            val (sender, message) = generator.generateDynamicDecoy(
                type = NotificationType.MESSAGE,
                preferredProfile = VeilPrefs.THEME_CALENDAR
            )
            assertTrue("Sender should be Calendar sender: $sender", VeilPrefs.CALENDAR_DECOY_NAMES.contains(sender))
            assertTrue("Message should be Calendar message: $message", VeilPrefs.CALENDAR_DECOY_MESSAGES.contains(message))
        }
    }
}
