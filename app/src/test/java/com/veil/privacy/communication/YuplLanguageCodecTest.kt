package com.veil.privacy.communication

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class YuplLanguageCodecTest {
    private val codec = BuiltinYuplLanguageCodec()

    @Test
    fun codecIsReadyAndVersioned() {
        assertEquals(true, codec.isReady)
        assertEquals(1, codec.dictionaryVersion)
    }

    @Test
    fun roundTripsDictionaryPhrasesAndWords() {
        val samples = listOf(
            "how are you?", "good morning", "hello", "Hi", "YES", "Okay",
            "I love you", "call me", "meet me", "thanks friend"
        )
        samples.forEach { assertEquals(it, codec.decode(codec.encode(it))) }
    }

    @Test
    fun roundTripsUnknownWordsWhitespaceAndPunctuation() {
        val samples = listOf(
            "This is an uncommon sentence, with punctuation!",
            "two  spaces\tand a newline\n",
            "MiXeD-CaSe words don't change.",
            "1234567890 + = / ? #"
        )
        samples.forEach { assertEquals(it, codec.decode(codec.encode(it))) }
    }

    @Test
    fun roundTripsIndicScriptsAndEmojiLosslessly() {
        val samples = listOf(
            "नमस्ते दुनिया",
            "నమస్కారం",
            "வணக்கம்",
            "مرحبا بالعالم",
            "Hello 👋🏽 ❤️ 🚀"
        )
        samples.forEach { assertEquals(it, codec.decode(codec.encode(it))) }
    }

    @Test
    fun encodingIsDeterministicAndDoesNotReturnPlainTextForDictionaryWords() {
        val first = codec.encode("hello")
        assertEquals(first, codec.encode("hello"))
        assertNotEquals("hello", first)
    }

    @Test
    fun emptyTextAndInvalidVersionsAreHandled() {
        assertEquals("", codec.decode(codec.encode("")))
        assertThrows(IllegalArgumentException::class.java) { codec.decode("Y2;bad") }
        assertThrows(IllegalArgumentException::class.java) { codec.decode("Y1;unknown-token") }
    }
    @Test
    fun productionProviderStaysPendingUntilTheOwnerDictionaryIsInstalled() {
        VeilLanguageCodecProvider.resetToPending()
        val pending = VeilLanguageCodecProvider.get()
        assertFalse(pending.isReady)
        assertEquals(0, pending.dictionaryVersion)
        assertThrows(IllegalStateException::class.java) { pending.encode("hello") }
        assertThrows(IllegalStateException::class.java) { pending.decode("Y1;") }
    }
}
