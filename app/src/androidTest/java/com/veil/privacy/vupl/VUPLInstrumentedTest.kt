package com.veil.privacy.vupl

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.security.SecureRandom

@RunWith(AndroidJUnit4::class)
class VUPLInstrumentedTest {
    private fun keys(): VUPL.KeysFile {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val json = ctx.assets.open("veil_keys_v1.json").bufferedReader().use { it.readText() }
        return VUPL.parseKeysFile(json)
    }

    @Test fun pairDerivationAndCipherRoundTrip() {
        val keys = keys()
        val secret = VUPL.createPairSecret()
        val a = VUPL.openPair(keys, secret)
        val b = VUPL.openPair(keys, secret)
        val wrong = VUPL.openPair(keys, VUPL.createPairSecret())
        assertEquals(a.dict.size, b.dict.size)
        assertEquals(a.dict.size, a.dict.keyToCode.values.toSet().size)
        for ((k, v) in a.dict.keyToCode) assertEquals(v, b.dict.keyToCode[k])
        val messages = listOf(
            "Hello, how are you?", "नमस्ते, आप कैसे हैं?", "तू कसा आहेस?",
            "నువ్వు ఎలా ఉన్నావు?", "Hola, ¿cómo estás?", "مرحبا، كيف حالك؟",
            "こんにちは", "你好", "Привет 안녕하세요", "Hi there! ❤️",
            "👨‍👩‍👧‍👦 🇮🇳 👍🏽 1️⃣ 😂 🔥",
            "Total -12.50, 007, ₹1,000, 2026-11-15 10:30",
            "e\u0301 a\u0308 क्षत्रिय", "Unknownword xyzzy qwerty",
            "(a+b)*c ≠ d ∑ ∞ © ™", "\t\n  \r\n", "", "long text. ".repeat(500)
        )
        for (message in messages) {
            assertEquals(message, VUPL.decode(VUPL.encode(message, a.dict), b.dict).text)
            val envelope = VUPL.sendMessage(message, a)
            assertEquals(message, VUPL.receiveMessage(envelope, b))
            try {
                VUPL.receiveMessage(envelope, wrong)
                fail("Wrong pairing key must be rejected")
            } catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun malformedAndTamperedPacketsAreRejected() {
        val keys = keys()
        val secret = VUPL.createPairSecret()
        val a = VUPL.openPair(keys, secret)
        val b = VUPL.openPair(keys, VUPL.createPairSecret())
        val packet = VUPL.encode("secret", a.dict)
        for (bad in listOf(packet.replace("|t=", "|t=ΩΩ"), packet.dropLast(20), "X$packet")) {
            try { VUPL.decode(bad, a.dict); fail("Malformed packet accepted") }
            catch (_: IllegalArgumentException) { }
        }
        val envelope = VUPL.sendMessage("गुप्त संदेश 🔥 1,500", a)
        try { VUPL.receiveMessage(envelope, b); fail("Wrong key accepted") }
        catch (_: IllegalArgumentException) { }
        val damaged = envelope.dropLast(4) + "AAAA"
        try { VUPL.receiveMessage(damaged, a); fail("Tampered ciphertext accepted") }
        catch (_: IllegalArgumentException) { }
    }

    @Test fun everyDictionaryEntryRoundTrips() {
        val keys = keys()
        val dict = VUPL.openPair(keys, VUPL.createPairSecret()).dict
        var checked = 0
        for (key in keys.keys) {
            val namespace = key.substringBefore(':')
            if (namespace == "x" || namespace == "d") continue
            val value = key.substringAfter(':')
            assertEquals("dictionary entry $key", value, VUPL.decode(VUPL.encode(value, dict), dict).text)
            checked++
        }
        assertTrue("expected all public dictionary entries to be tested", checked > 150_000)
    }

    @Test fun randomUnicodeRoundTrips() {
        val dict = VUPL.openPair(keys(), VUPL.createPairSecret()).dict
        val random = SecureRandom()
        repeat(300) {
            val sb = StringBuilder()
            repeat(random.nextInt(40)) {
                val cp = random.nextInt(0x110000)
                sb.appendCodePoint(cp)
            }
            val value = sb.toString()
            assertEquals(value, VUPL.decode(VUPL.encode(value, dict), dict).text)
        }
    }
}
