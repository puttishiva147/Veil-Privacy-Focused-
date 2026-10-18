package com.veil.privacy

import com.veil.privacy.util.PanicTriggerSequence
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PanicTriggerSequenceTest {

    @Test
    fun triggersOnlyAfterConfiguredPressCount() {
        val sequence = PanicTriggerSequence()
        assertFalse(sequence.onPress(100L, 3))
        assertFalse(sequence.onPress(500L, 3))
        assertTrue(sequence.onPress(900L, 3))
    }

    @Test
    fun resetsWhenPressesExceedWindow() {
        val sequence = PanicTriggerSequence()
        assertFalse(sequence.onPress(100L, 3))
        assertFalse(sequence.onPress(1_601L, 3))
        assertFalse(sequence.onPress(1_900L, 3))
        assertTrue(sequence.onPress(2_100L, 3))
    }

    @Test
    fun supportsTwoPressConfigurationAndResetsAfterTrigger() {
        val sequence = PanicTriggerSequence()
        assertFalse(sequence.onPress(10L, 2))
        assertTrue(sequence.onPress(20L, 2))
        assertFalse(sequence.onPress(30L, 2))
    }

    @Test
    fun resetCancelsPartialSequence() {
        val sequence = PanicTriggerSequence()
        assertFalse(sequence.onPress(10L, 2))
        sequence.reset()
        assertFalse(sequence.onPress(20L, 2))
        assertTrue(sequence.onPress(30L, 2))
    }
}
